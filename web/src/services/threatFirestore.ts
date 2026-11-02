import {

  collection,

  query,

  orderBy,

  onSnapshot,

  doc,

  getDoc,

  getDocs,

  updateDoc,

  deleteDoc,

  serverTimestamp,

  Timestamp,

  Unsubscribe,

  limit,

  startAfter,

  QueryDocumentSnapshot,

  DocumentData,

} from 'firebase/firestore';

import { db, getFirebaseDb } from './auth';

import { ThreatEvent, ThreatCategory, ThreatEventType, UserStats } from '@/types';

import { sessionManager } from './sessionManager';

function getActiveDb() {

  return db || (typeof window !== 'undefined' ? getFirebaseDb() : null);

}

/**

 * Normalizes Firestore timestamp, string, or number to ISO string.

 */

function toIsoString(val: any): string {

  if (!val) return new Date().toISOString();

  if (val instanceof Timestamp) {

    return val.toDate().toISOString();

  }

  if (typeof val?.toDate === 'function') {

    return val.toDate().toISOString();

  }

  if (typeof val === 'number') {

    return new Date(val).toISOString();

  }

  if (typeof val === 'string') {

    const parsed = new Date(val);

    if (!isNaN(parsed.getTime())) {

      return parsed.toISOString();

    }

  }

  return new Date().toISOString();

}

/**

 * Normalizes a raw Firestore document to a strongly-typed ThreatEvent.

 */

export function mapDocToThreatEvent(id: string, data: Record<string, any>, uid: string): ThreatEvent {

  const timestamp = toIsoString(data.detectedAt || data.timestamp || data.createdAt);

  const createdAt = toIsoString(data.createdAt || data.detectedAt || data.timestamp);

  const eventType: ThreatEventType =

    (data.eventType || '').toUpperCase() === 'SMS' ? 'SMS' : 'QR';

  const riskScore = typeof data.riskScore === 'number' ? data.riskScore : (data.risk_score ?? 0);

  let category: ThreatCategory = 'SAFE';

  const rawSeverity = (data.severity || data.threatCategory || '').toUpperCase();

  if (rawSeverity === 'HIGH' || rawSeverity === 'CRITICAL' || riskScore >= 70) {

    category = 'HIGH';

  } else if (rawSeverity === 'MEDIUM' || riskScore >= 50) {

    category = 'MEDIUM';

  } else if (rawSeverity === 'LOW') {

    category = 'LOW';

  }

  const reasons: string[] = Array.isArray(data.detectionIndicators)

    ? data.detectionIndicators.map((i: any) => (typeof i === 'string' ? i : JSON.stringify(i)))

    : Array.isArray(data.reasons)

    ? data.reasons

    : data.sanitizedDescription

    ? [data.sanitizedDescription]

    : [];

  return {

    id,

    client_event_id: data.id || id,

    user_id: uid,

    device_id: data.deviceId || null,

    event_type: eventType,

    risk_score: riskScore,

    threat_category: category,

    recommended_action: data.actionTaken || data.recommended_action || 'PROCEED_SAFELY',

    timestamp,

    model_versions: data.modelVersion || data.model_versions || null,

    model_results: data.modelResults || data.model_results || null,

    rule_results: data.ruleResults || data.rule_results || null,

    context_results: data.contextResults || null,

    reasons,

    url_metadata: data.urlMetadata || (data.domain ? { domain: data.domain } : null),

    qr_metadata: data.qrMetadata || null,

    related_threat_id: data.relatedThreatId || null,

    campaign_id: data.campaignId || null,

    status: data.reviewStatus || data.status || 'NEW',

    user_action: data.userAction || data.user_action || null,

    sanitized_preview: data.sanitizedPreview || data.sanitized_preview || data.sanitizedDescription || null,

    content_retention_mode: data.contentRetentionMode || data.content_retention_mode || 'LOCAL_SAVED',

    decoded_url: data.decodedUrl || data.decoded_url || null,

    created_at: createdAt,

    updated_at: data.updatedAt ? toIsoString(data.updatedAt) : null,

    sender: data.sender || data.contextResults?.sender || null,

    raw_content: data.rawContent || data.raw_content || data.sanitizedPreview || null,

    explanation: data.explanation || reasons.join('; ') || null,

  };

}

/**

 * Asserts that the requested UID matches the active authenticated session.

 * Prevents client-side UID manipulation or cross-user leakage.

 */

function assertSessionMatch(uid: string, operationName: string): boolean {

  const currentUid = sessionManager.getCurrentUid();

  if (!currentUid) {

    console.warn(`[Isolation Guard] Blocked ${operationName}: No active session.`);

    return false;

  }

  if (currentUid !== uid) {

    console.warn(`[Isolation Guard] Blocked ${operationName}: UID mismatch (${uid} !== ${currentUid}).`);

    return false;

  }

  return true;

}

/**

 * Subscribes in real-time to the authenticated user's isolated threat collection:

 * Path: users/{uid}/threats

 *

 * Scoped strictly to the session lifecycle via sessionManager.

 */

export function subscribeToUserThreats(

  uid: string,

  onUpdate: (threats: ThreatEvent[]) => void,

  onError: (error: Error) => void,

  maxLimit: number = 100

): Unsubscribe {

  const activeDb = getActiveDb();

  if (!activeDb) {

    onError(new Error('Cloud Firestore is not initialized in this browser environment.'));

    return () => {};

  }

  if (!uid || uid.trim() === '') {

    onError(new Error('Cannot subscribe to threats: Unauthenticated session.'));

    return () => {};

  }

  if (!assertSessionMatch(uid, 'subscribeToUserThreats')) {

    onError(new Error(`Permission denied: Session mismatch for user ${uid}. Isolation enforced.`));

    return () => {};

  }

  const currentGeneration = sessionManager.getGeneration();

  try {

    const threatsCollectionRef = collection(activeDb, 'users', uid, 'threats');

    const q = query(threatsCollectionRef, orderBy('detectedAt', 'desc'), limit(maxLimit));

    let fallbackUnsub: Unsubscribe | null = null;

    const rawUnsubscribe = onSnapshot(

      q,

      (snapshot) => {

        // Cross-account guard: ignore if session has changed or unmounted

        if (!sessionManager.isValidSession(currentGeneration, uid)) {

          return;

        }

        const events: ThreatEvent[] = [];

        snapshot.forEach((docSnap) => {

          events.push(mapDocToThreatEvent(docSnap.id, docSnap.data(), uid));

        });

        onUpdate(events);

      },

      (err: any) => {

        if (!sessionManager.isValidSession(currentGeneration, uid)) {

          return;

        }

        console.warn(`Firestore listener notice for users/${uid}/threats:`, err.message);

        // Security rule rejection: do not attempt fallback query on forbidden path

        if (err.code === 'permission-denied') {

          onError(err);

          return;

        }

        // Fallback query without composite orderBy in case index is creating

        try {

          const fallbackQuery = query(threatsCollectionRef, limit(maxLimit));

          fallbackUnsub = onSnapshot(

            fallbackQuery,

            (snap) => {

              if (!sessionManager.isValidSession(currentGeneration, uid)) {

                return;

              }

              const events: ThreatEvent[] = [];

              snap.forEach((docSnap) => {

                events.push(mapDocToThreatEvent(docSnap.id, docSnap.data(), uid));

              });

              events.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());

              onUpdate(events);

            },

            (fallbackErr) => {

              if (sessionManager.isValidSession(currentGeneration, uid)) {

                onError(fallbackErr);

              }

            }

          );

        } catch (innerErr: any) {

          onError(innerErr);

        }

      }

    );

    const masterUnsubscribe = () => {

      try {

        rawUnsubscribe();

      } catch {}

      if (fallbackUnsub) {

        try {

          fallbackUnsub();

        } catch {}

      }

    };

    // Register with centralized session manager for automatic cleanup on logout/switch

    return sessionManager.registerSubscription(uid, masterUnsubscribe);

  } catch (err: any) {

    onError(err);

    return () => {};

  }

}

export interface PaginatedThreatsResult {

  items: ThreatEvent[];

  lastVisible: QueryDocumentSnapshot<DocumentData> | null;

  hasMore: boolean;

}

/**

 * Fetches a paginated page of threats using Firestore cursor pagination (startAfter).

 * Scoped strictly to users/{uid}/threats.

 */

export async function fetchPaginatedUserThreats(options: {

  uid: string;

  pageSize?: number;

  lastDoc?: QueryDocumentSnapshot<DocumentData> | null;

}): Promise<PaginatedThreatsResult> {

  const { uid, pageSize = 20, lastDoc = null } = options;

  if (!assertSessionMatch(uid, 'fetchPaginatedUserThreats')) {

    return { items: [], lastVisible: null, hasMore: false };

  }

  const currentGeneration = sessionManager.getGeneration();

  const activeDb = getActiveDb();

  if (!activeDb || !uid) {

    return { items: [], lastVisible: null, hasMore: false };

  }

  try {

    const threatsRef = collection(activeDb, 'users', uid, 'threats');

    let q = query(threatsRef, orderBy('detectedAt', 'desc'), limit(pageSize + 1));

    if (lastDoc) {

      q = query(threatsRef, orderBy('detectedAt', 'desc'), startAfter(lastDoc), limit(pageSize + 1));

    }

    const snap = await getDocs(q);

    // Stale session validation

    if (!sessionManager.isValidSession(currentGeneration, uid)) {

      return { items: [], lastVisible: null, hasMore: false };

    }

    const docs = snap.docs;

    const hasMore = docs.length > pageSize;

    const resultDocs = hasMore ? docs.slice(0, pageSize) : docs;

    const items = resultDocs.map((d) => mapDocToThreatEvent(d.id, d.data(), uid));

    const lastVisible = resultDocs.length > 0 ? resultDocs[resultDocs.length - 1] : null;

    return {

      items,

      lastVisible,

      hasMore,

    };

  } catch (err: any) {

    console.warn(`Firestore paginated query notice for users/${uid}:`, err);

    if (err.code === 'permission-denied') {

      return { items: [], lastVisible: null, hasMore: false };

    }

    // Fallback if orderBy('detectedAt') requires index

    try {

      const threatsRef = collection(activeDb, 'users', uid, 'threats');

      let fallbackQ = query(threatsRef, limit(pageSize + 1));

      if (lastDoc) {

        fallbackQ = query(threatsRef, startAfter(lastDoc), limit(pageSize + 1));

      }

      const snap = await getDocs(fallbackQ);

      if (!sessionManager.isValidSession(currentGeneration, uid)) {

        return { items: [], lastVisible: null, hasMore: false };

      }

      const docs = snap.docs;

      const hasMore = docs.length > pageSize;

      const resultDocs = hasMore ? docs.slice(0, pageSize) : docs;

      const items = resultDocs.map((d) => mapDocToThreatEvent(d.id, d.data(), uid));

      items.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());

      const lastVisible = resultDocs.length > 0 ? resultDocs[resultDocs.length - 1] : null;

      return { items, lastVisible, hasMore };

    } catch (fallbackErr) {

      console.error('Fallback query error:', fallbackErr);

      return { items: [], lastVisible: null, hasMore: false };

    }

  }

}

/**

 * Fetches all user threats for computing accurate, non-truncated all-time statistics.

 */

export async function fetchFullUserStats(uid: string): Promise<UserStats | null> {

  if (!assertSessionMatch(uid, 'fetchFullUserStats')) {

    return null;

  }

  const activeDb = getActiveDb();

  if (!activeDb) return null;

  const currentGeneration = sessionManager.getGeneration();

  try {

    const threatsRef = collection(activeDb, 'users', uid, 'threats');

    const snap = await getDocs(threatsRef);

    if (!sessionManager.isValidSession(currentGeneration, uid)) {

      return null;

    }

    const allThreats: ThreatEvent[] = [];

    snap.forEach((d) => {

      allThreats.push(mapDocToThreatEvent(d.id, d.data(), uid));

    });

    return calculateUserStatsFromThreats(allThreats);

  } catch (err) {

    console.warn(`Firestore stats calculation fallback notice for ${uid}:`, err);

    return null;

  }

}

/**

 * Updates a threat document when user takes an action (BLOCK / MARK_SAFE / IGNORE)

 */

export async function takeUserThreatAction(

  uid: string,

  threatId: string,

  action: 'BLOCK' | 'MARK_SAFE' | 'IGNORE' | 'RESOLVED'

): Promise<void> {

  if (!assertSessionMatch(uid, 'takeUserThreatAction')) {

    throw new Error('Access denied: Cannot take action on threats belonging to another session.');

  }

  const activeDb = getActiveDb();

  if (!activeDb) throw new Error('Firestore not initialized');

  const threatDocRef = doc(activeDb, 'users', uid, 'threats', threatId);

  const reviewStatus = action === 'BLOCK' ? 'BLOCKED' : (action === 'MARK_SAFE' || action === 'RESOLVED') ? 'RESOLVED' : 'REVIEWED';

  const status = (action === 'RESOLVED' || action === 'MARK_SAFE') ? 'RESOLVED' : (action === 'BLOCK') ? 'BLOCKED' : 'REVIEWED';

  await updateDoc(threatDocRef, {

    actionTaken: action,

    userAction: action,

    reviewStatus,

    status,

    updatedAt: serverTimestamp(),

  });

}

/**

 * Deletes a threat document from the user's isolated collection

 */

export async function deleteUserThreat(uid: string, threatId: string): Promise<void> {

  if (!assertSessionMatch(uid, 'deleteUserThreat')) {

    throw new Error('Access denied: Cannot delete threats belonging to another session.');

  }

  const activeDb = getActiveDb();

  if (!activeDb) throw new Error('Firestore not initialized');

  const threatDocRef = doc(activeDb, 'users', uid, 'threats', threatId);

  await deleteDoc(threatDocRef);

}

/**

 * Computes UserStats in real-time from threat records.

 * Note: Only actual blocking actions (user_action === 'BLOCK' or status === 'BLOCKED')

 * are counted as blocked threats, not merely recommended_action.

 */

export function calculateUserStatsFromThreats(threats: ThreatEvent[]): UserStats {

  const total = threats.length;

  let threatsDetected = 0;

  let threatsBlocked = 0;

  let safeDetections = 0;

  let highRisk = 0;

  let mediumRisk = 0;

  let lowRisk = 0;

  let safeCount = 0;

  let qrCount = 0;

  let smsCount = 0;

  let totalScore = 0;

  let falsePositives = 0;

  let unresolvedHighRisk = 0;

  let smsSafe = 0;

  let smsAttention = 0;

  let smsDangerous = 0;

  let qrSafe = 0;

  let qrAttention = 0;

  let qrDangerous = 0;

  for (const t of threats) {

    totalScore += t.risk_score;

    const isSms = (t.event_type || '').toUpperCase() === 'SMS';

    const isQr = (t.event_type || '').toUpperCase() === 'QR';

    if (isQr) qrCount++;

    if (isSms) smsCount++;

    const rawCat = (t.threat_category || '').toUpperCase();
    const isHigh =
      rawCat === 'HIGH' ||
      rawCat === 'MALICIOUS' ||
      rawCat === 'SMISHING' ||
      rawCat === 'QUISHING' ||
      t.risk_score >= 70;
    const isMed =
      !isHigh &&
      (rawCat === 'MEDIUM' ||
        rawCat === 'SUSPICIOUS' ||
        rawCat === 'LOW' ||
        rawCat === 'ATTENTION' ||
        (t.risk_score >= 40 && t.risk_score < 70));
    const isLow = !isHigh && !isMed && (rawCat === 'LOW' || (t.risk_score >= 20 && t.risk_score < 40));

    if (isHigh) {

      highRisk++;

      threatsDetected++;

      if (isSms) smsDangerous++;

      if (isQr) qrDangerous++;

    } else if (isMed || isLow) {

      mediumRisk++;

      threatsDetected++;

      if (isSms) smsAttention++;

      if (isQr) qrAttention++;

    } else {

      safeCount++;

      safeDetections++;

      if (isSms) smsSafe++;

      if (isQr) qrSafe++;

    }

    const isBlocked =

      t.user_action === 'BLOCK' ||

      t.status === 'BLOCKED' ||

      (t as any).actionTaken === 'BLOCK' ||

      (t as any).actionTaken === 'QUARANTINED';

    if (isBlocked) {

      threatsBlocked++;

    }

    if (

      (t.user_action === 'MARK_SAFE' || t.status === 'RESOLVED' || t.status === 'FALSE_POSITIVE') &&

      t.risk_score >= 40

    ) {

      falsePositives++;

    }

    if (isHigh && !isBlocked && t.status !== 'RESOLVED' && t.user_action !== 'MARK_SAFE') {

      unresolvedHighRisk++;

    }

  }

  const avgRisk = total > 0 ? Math.round(totalScore / total) : 0;

  return {

    total_scans: total,

    threats_detected: threatsDetected,

    threats_blocked: threatsBlocked,

    safe_detections: safeDetections,

    avg_risk_score: avgRisk,

    high_risk_count: highRisk,

    medium_risk_count: mediumRisk,

    low_risk_count: lowRisk,

    safe_count: safeCount,

    recent_qr_count: qrCount,

    recent_sms_count: smsCount,

    false_positives_reported: falsePositives,

    unresolved_high_risk: unresolvedHighRisk,

    sms_total: smsCount,

    sms_safe: smsSafe,

    sms_attention: smsAttention,

    sms_dangerous: smsDangerous,

    qr_total: qrCount,

    qr_safe: qrSafe,

    qr_attention: qrAttention,

    qr_dangerous: qrDangerous,

  };

}

/**

 * Retrieves a single threat document by ID from users/{uid}/threats/{threatId}

 */

export async function getUserThreatById(uid: string, threatId: string): Promise<ThreatEvent | null> {

  if (!assertSessionMatch(uid, 'getUserThreatById')) {

    return null;

  }

  const activeDb = getActiveDb();

  if (!activeDb || !uid || !threatId) return null;

  try {

    const snap = await getDoc(doc(activeDb, 'users', uid, 'threats', threatId));

    if (snap.exists()) {

      return mapDocToThreatEvent(snap.id, snap.data(), uid);

    }

  } catch (err) {

    console.warn(`Firestore getDoc notice for users/${uid}/threats/${threatId}:`, err);

  }

  return null;

}
