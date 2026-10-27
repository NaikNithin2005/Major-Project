import {
  collection,
  query,
  orderBy,
  onSnapshot,
  doc,
  getDoc,
  updateDoc,
  deleteDoc,
  serverTimestamp,
  Timestamp,
  Unsubscribe,
  limit,
} from 'firebase/firestore';
import { db, getFirebaseDb } from './auth';
import { ThreatEvent, ThreatCategory, ThreatEventType, UserStats } from '@/types';

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
  };
}

/**
 * Subscribes in real-time to the authenticated user's isolated threat collection:
 * Path: users/{uid}/threats
 *
 * Orders by detectedAt descending.
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

  try {
    const threatsCollectionRef = collection(activeDb, 'users', uid, 'threats');
    // Order by detectedAt desc with a bounded query limit for fast loading
    const q = query(threatsCollectionRef, orderBy('detectedAt', 'desc'), limit(maxLimit));

    const unsubscribe = onSnapshot(
      q,
      (snapshot) => {
        const events: ThreatEvent[] = [];
        snapshot.forEach((docSnap) => {
          events.push(mapDocToThreatEvent(docSnap.id, docSnap.data(), uid));
        });
        onUpdate(events);
      },
      (err) => {
        console.warn(`Firestore listener notice for users/${uid}/threats:`, err.message);
        // Fallback query if composite index or detectedAt field is not present on older records
        try {
          const fallbackQuery = query(threatsCollectionRef, limit(maxLimit));
          const fallbackUnsub = onSnapshot(
            fallbackQuery,
            (snap) => {
              const events: ThreatEvent[] = [];
              snap.forEach((docSnap) => {
                events.push(mapDocToThreatEvent(docSnap.id, docSnap.data(), uid));
              });
              // Sort locally by timestamp descending
              events.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
              onUpdate(events);
            },
            (fallbackErr) => {
              onError(fallbackErr);
            }
          );
          return fallbackUnsub;
        } catch (innerErr: any) {
          onError(innerErr);
        }
      }
    );

    return unsubscribe;
  } catch (err: any) {
    onError(err);
    return () => {};
  }
}

/**
 * Updates a threat document when user takes an action (BLOCK / MARK_SAFE / IGNORE)
 */
export async function takeUserThreatAction(
  uid: string,
  threatId: string,
  action: 'BLOCK' | 'MARK_SAFE' | 'IGNORE'
): Promise<void> {
  const activeDb = getActiveDb();
  if (!activeDb) throw new Error('Firestore not initialized');
  const threatDocRef = doc(activeDb, 'users', uid, 'threats', threatId);

  const reviewStatus = action === 'BLOCK' ? 'BLOCKED' : action === 'MARK_SAFE' ? 'RESOLVED' : 'REVIEWED';
  await updateDoc(threatDocRef, {
    actionTaken: action,
    userAction: action,
    reviewStatus,
    updatedAt: serverTimestamp(),
  });
}

/**
 * Deletes a threat document from the user's isolated collection
 */
export async function deleteUserThreat(uid: string, threatId: string): Promise<void> {
  const activeDb = getActiveDb();
  if (!activeDb) throw new Error('Firestore not initialized');
  const threatDocRef = doc(activeDb, 'users', uid, 'threats', threatId);
  await deleteDoc(threatDocRef);
}

/**
 * Computes UserStats in real-time from the user's active threat collection.
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

  for (const t of threats) {
    totalScore += t.risk_score;
    if (t.event_type === 'QR') qrCount++;
    if (t.event_type === 'SMS') smsCount++;

    if (t.threat_category === 'HIGH' || t.risk_score >= 70) {
      highRisk++;
      threatsDetected++;
    } else if (t.threat_category === 'MEDIUM' || t.risk_score >= 50) {
      mediumRisk++;
      threatsDetected++;
    } else if (t.threat_category === 'LOW') {
      lowRisk++;
      threatsDetected++;
    } else {
      safeCount++;
      safeDetections++;
    }

    if (
      t.user_action === 'BLOCK' ||
      t.status === 'BLOCKED' ||
      t.recommended_action === 'BLOCK' ||
      t.recommended_action === 'BLOCKED'
    ) {
      threatsBlocked++;
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
  };
}

/**
 * Retrieves a single threat document by ID from users/{uid}/threats/{threatId}
 */
export async function getUserThreatById(uid: string, threatId: string): Promise<ThreatEvent | null> {
  if (!db || !uid || !threatId) return null;
  try {
    const snap = await getDoc(doc(db, 'users', uid, 'threats', threatId));
    if (snap.exists()) {
      return mapDocToThreatEvent(snap.id, snap.data(), uid);
    }
  } catch (err) {
    console.warn(`Firestore getDoc notice for users/${uid}/threats/${threatId}:`, err);
  }
  return null;
}

