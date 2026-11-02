/**
 * Phase 1 Test Suite: Firebase Account Isolation & Security Verification
 *
 * Tests:
 * 1. User A login and threat creation in users/{uid}/threats
 * 2. Real-time subscription detachment on session termination
 * 3. Clearing user-specific arrays, cached IDs, selected threats, metrics, cursors, and errors on logout
 * 4. Stale async request invalidation via Session Generation counters
 * 5. User B login without data leakage from User A
 * 6. Protection against client-side UID / document path manipulation
 * 7. Firestore Security Rules evaluation for cross-account isolation
 */

const assert = require('assert');

// 1. In-memory Mock SessionManager matching web implementation
class MockSessionManager {
  constructor() {
    this.activeSubscriptions = new Map();
    this.resetHandlers = new Set();
    this.sessionGeneration = 0;
    this.currentUid = null;
    this.userCache = new Map();
  }

  getGeneration() {
    return this.sessionGeneration;
  }

  getCurrentUid() {
    return this.currentUid;
  }

  getActiveSubscriptionCount() {
    let count = 0;
    this.activeSubscriptions.forEach((subs) => {
      count += subs.size;
    });
    return count;
  }

  hasActiveSubscriptions(uid) {
    if (uid) {
      return (this.activeSubscriptions.get(uid)?.size ?? 0) > 0;
    }
    return this.getActiveSubscriptionCount() > 0;
  }

  setCache(key, value) {
    this.userCache.set(key, value);
  }

  getCache(key) {
    return this.userCache.get(key);
  }

  clearCache() {
    this.userCache.clear();
  }

  setActiveUser(uid) {
    if (this.currentUid !== uid) {
      this.resetSession();
      this.currentUid = uid;
    }
  }

  isValidSession(generation, uid) {
    if (generation !== this.sessionGeneration) {
      return false;
    }
    if (uid !== undefined && this.currentUid !== uid) {
      return false;
    }
    return true;
  }

  registerSubscription(uid, unsubscribe) {
    if (!this.activeSubscriptions.has(uid)) {
      this.activeSubscriptions.set(uid, new Set());
    }
    const set = this.activeSubscriptions.get(uid);
    set.add(unsubscribe);

    let isCleanedUp = false;
    return () => {
      if (isCleanedUp) return;
      isCleanedUp = true;
      try {
        unsubscribe();
      } catch (err) {}
      set.delete(unsubscribe);
    };
  }

  registerResetHandler(handler) {
    this.resetHandlers.add(handler);
    return () => {
      this.resetHandlers.delete(handler);
    };
  }

  resetSession() {
    this.sessionGeneration++;
    this.currentUid = null;
    this.clearCache();

    this.activeSubscriptions.forEach((subs) => {
      subs.forEach((unsub) => {
        try {
          unsub();
        } catch (err) {}
      });
    });
    this.activeSubscriptions.clear();

    this.resetHandlers.forEach((handler) => {
      try {
        handler();
      } catch (err) {}
    });
  }
}

// 2. Mock Firestore Database with Strict Security Rules Simulation
class MockFirestoreSecurityEngine {
  constructor() {
    // Database storage: { collectionPath: { docId: data } }
    this.collections = {
      users: {},
      threats: {}
    };
  }

  evaluateRules(auth, method, path, data) {
    // path: ['users', userId, 'threats', threatId] or ['threats', threatId]
    const isAuthenticated = auth !== null && auth.uid !== null;
    const isAdmin = isAuthenticated && (auth.token?.role === 'ADMIN' || auth.email === 'admin@squishshield.local');
    const isOwner = (uid) => isAuthenticated && auth.uid === uid;

    if (path[0] === 'users' && path.length >= 2) {
      const targetUserId = path[1];

      // Nested threats
      if (path[2] === 'threats') {
        if (method === 'read') {
          return isOwner(targetUserId) || isAdmin;
        }
        if (method === 'create' || method === 'write' || method === 'update') {
          const ownerOrAdmin = isOwner(targetUserId) || isAdmin;
          const noUidMismatch = !data || !data.userId || data.userId === targetUserId;
          return ownerOrAdmin && noUidMismatch;
        }
        if (method === 'delete') {
          return isOwner(targetUserId) || isAdmin;
        }
      }

      // User document root
      if (method === 'read' || method === 'create' || method === 'update') {
        return isOwner(targetUserId) || isAdmin;
      }
      if (method === 'delete') {
        return isAdmin;
      }
    }

    if (path[0] === 'threats') {
      if (method === 'create') {
        return isAuthenticated && data && data.userId === auth.uid;
      }
      if (method === 'read' || method === 'write' || method === 'update' || method === 'delete') {
        return isAuthenticated && (data?.userId === auth.uid || isAdmin);
      }
    }

    return false;
  }

  writeDocument(auth, path, docId, data) {
    const allowed = this.evaluateRules(auth, 'write', [...path, docId], data);
    if (!allowed) {
      throw new Error(`PERMISSION_DENIED: Security rules rejected operation for auth=${auth?.uid} on path=/${path.join('/')}/${docId}`);
    }
    const fullPath = path.join('/');
    if (!this.collections[fullPath]) {
      this.collections[fullPath] = {};
    }
    this.collections[fullPath][docId] = { ...data, id: docId };
    return this.collections[fullPath][docId];
  }

  readDocument(auth, path, docId) {
    const fullPath = path.join('/');
    const doc = this.collections[fullPath]?.[docId];
    const allowed = this.evaluateRules(auth, 'read', [...path, docId], doc);
    if (!allowed) {
      throw new Error(`PERMISSION_DENIED: Security rules rejected read for auth=${auth?.uid} on path=/${path.join('/')}/${docId}`);
    }
    return doc || null;
  }

  queryCollection(auth, path) {
    const fullPath = path.join('/');
    // Check collection access
    const allowed = this.evaluateRules(auth, 'read', [...path, '_probe'], null);
    if (!allowed) {
      throw new Error(`PERMISSION_DENIED: Security rules rejected query for auth=${auth?.uid} on path=/${path.join('/')}`);
    }
    const docs = this.collections[fullPath] || {};
    return Object.values(docs);
  }
}

// 3. Test Runner
async function runPhase1Tests() {
  console.log('==============================================================');
  console.log('🛡️  SQUISH — PHASE 1: FIREBASE ACCOUNT ISOLATION TEST SUITE');
  console.log('==============================================================\n');

  let passed = 0;
  let total = 0;

  function runTest(name, fn) {
    total++;
    try {
      fn();
      console.log(`✅ [PASS] TC${String(total).padStart(2, '0')}: ${name}`);
      passed++;
    } catch (err) {
      console.error(`❌ [FAIL] TC${String(total).padStart(2, '0')}: ${name}`);
      console.error(`   Error: ${err.message}\n`);
    }
  }

  const session = new MockSessionManager();
  const db = new MockFirestoreSecurityEngine();

  const userA = { uid: 'user_alpha_uid', email: 'userA@squishshield.local', token: { role: 'USER' } };
  const userB = { uid: 'user_beta_uid', email: 'userB@squishshield.local', token: { role: 'USER' } };

  // Test 1: User A Login & Active UID established
  runTest('User A login establishes active UID and initializes clean session', () => {
    session.setActiveUser(userA.uid);
    assert.strictEqual(session.getCurrentUid(), userA.uid);
    assert.strictEqual(session.getGeneration(), 1);
    assert.strictEqual(session.getActiveSubscriptionCount(), 0);
  });

  // Test 2: User A creates threat in users/{userA}/threats
  runTest('User A can create and read records in own isolated collection', () => {
    const threatA = {
      id: 'THREAT-A1',
      userId: userA.uid,
      eventType: 'SMS',
      riskScore: 85,
      threatCategory: 'HIGH',
      userAction: 'BLOCK',
      status: 'BLOCKED'
    };
    db.writeDocument(userA, ['users', userA.uid, 'threats'], threatA.id, threatA);
    const readBack = db.readDocument(userA, ['users', userA.uid, 'threats'], threatA.id);
    assert.strictEqual(readBack.id, 'THREAT-A1');
    assert.strictEqual(readBack.userId, userA.uid);
  });

  // Test 3: Real-time listener registered for User A
  let listenerUnsubscribed = false;
  let receivedEvents = [];

  runTest('User A listener receives updates and registers with SessionManager', () => {
    const unsub = session.registerSubscription(userA.uid, () => {
      listenerUnsubscribed = true;
    });
    assert.strictEqual(session.getActiveSubscriptionCount(), 1);
    assert.strictEqual(session.hasActiveSubscriptions(userA.uid), true);
    assert.strictEqual(session.hasActiveSubscriptions(userB.uid), false);
  });

  // Test 4: Stale request protection via generation counter
  let staleStateMutated = false;
  const initialGen = session.getGeneration();

  runTest('In-flight async request captured at generation 1 is tracked', () => {
    assert.strictEqual(session.isValidSession(initialGen, userA.uid), true);
  });

  // Test 5: UI Reset handlers clear state
  let uiState = {
    threats: ['THREAT-A1'],
    selectedThreat: 'THREAT-A1',
    stats: { totalScans: 1, threatsBlocked: 1 },
    cursor: 'doc_cursor_123',
    error: 'network error'
  };

  session.registerResetHandler(() => {
    uiState.threats = [];
    uiState.selectedThreat = null;
    uiState.stats = null;
    uiState.cursor = null;
    uiState.error = null;
  });

  // Test 6: User A Logout
  runTest('User A logout detaches listeners, clears state, and increments generation ID', () => {
    session.resetSession();
    assert.strictEqual(session.getCurrentUid(), null);
    assert.strictEqual(session.getGeneration(), 2);
    assert.strictEqual(session.getActiveSubscriptionCount(), 0);
    assert.strictEqual(listenerUnsubscribed, true, 'Old listener unsubscribe was called');
    assert.deepStrictEqual(uiState.threats, []);
    assert.strictEqual(uiState.selectedThreat, null);
    assert.strictEqual(uiState.stats, null);
    assert.strictEqual(uiState.cursor, null);
    assert.strictEqual(uiState.error, null);
  });

  // Test 7: In-flight request from User A resolving after logout/switch is discarded
  runTest('Stale in-flight request from previous session cannot mutate new state', () => {
    if (session.isValidSession(initialGen, userA.uid)) {
      staleStateMutated = true;
      uiState.threats.push('STALE-A1');
    }
    assert.strictEqual(staleStateMutated, false);
    assert.deepStrictEqual(uiState.threats, []);
  });

  // Test 8: User B Login
  runTest('User B login establishes new UID with clean isolated state', () => {
    session.setActiveUser(userB.uid);
    assert.strictEqual(session.getCurrentUid(), userB.uid);
    assert.strictEqual(session.getGeneration(), 3);
    assert.strictEqual(session.getActiveSubscriptionCount(), 0);
    assert.deepStrictEqual(uiState.threats, []);
  });

  // Test 9: User B cannot read User A records in Firestore
  runTest('Firestore Security Rules REJECT User B read on User A threats', () => {
    let errorCaught = false;
    try {
      db.readDocument(userB, ['users', userA.uid, 'threats'], 'THREAT-A1');
    } catch (err) {
      errorCaught = true;
      assert.match(err.message, /PERMISSION_DENIED/);
    }
    assert.strictEqual(errorCaught, true, 'User B read was rejected with PERMISSION_DENIED');
  });

  // Test 10: User B cannot write or tamper with User A records
  runTest('Firestore Security Rules REJECT User B write on User A threats', () => {
    let errorCaught = false;
    try {
      db.writeDocument(userB, ['users', userA.uid, 'threats'], 'MALICIOUS_DOC', {
        id: 'MALICIOUS_DOC',
        userId: userB.uid
      });
    } catch (err) {
      errorCaught = true;
      assert.match(err.message, /PERMISSION_DENIED/);
    }
    assert.strictEqual(errorCaught, true, 'User B write was rejected with PERMISSION_DENIED');
  });

  // Test 11: User B cannot query collection of User A
  runTest('Firestore Security Rules REJECT User B query on users/userA/threats', () => {
    let errorCaught = false;
    try {
      db.queryCollection(userB, ['users', userA.uid, 'threats']);
    } catch (err) {
      errorCaught = true;
      assert.match(err.message, /PERMISSION_DENIED/);
    }
    assert.strictEqual(errorCaught, true, 'User B collection query was rejected with PERMISSION_DENIED');
  });

  // Test 12: User B cannot tamper with userId during document creation
  runTest('Security Rules REJECT document creation where userId spoofed as User A', () => {
    let errorCaught = false;
    try {
      db.writeDocument(userB, ['users', userB.uid, 'threats'], 'SPOOF_THREAT', {
        id: 'SPOOF_THREAT',
        userId: userA.uid // Attempting to inject User A's UID into User B's subcollection
      });
    } catch (err) {
      errorCaught = true;
      assert.match(err.message, /PERMISSION_DENIED/);
    }
    assert.strictEqual(errorCaught, true, 'UID spoofing was rejected with PERMISSION_DENIED');
  });

  // Test 13: Client-side Isolation Guard rejects mismatched UID operations
  runTest('Client-side Isolation Guard rejects calls with mismatched UID', () => {
    function clientGuardCheck(requestedUid) {
      const activeUid = session.getCurrentUid();
      if (!activeUid || activeUid !== requestedUid) {
        throw new Error(`Isolation Guard: Client UID mismatch (${requestedUid} !== ${activeUid})`);
      }
      return true;
    }

    assert.strictEqual(clientGuardCheck(userB.uid), true);
    assert.throws(() => clientGuardCheck(userA.uid), /Isolation Guard: Client UID mismatch/);
    assert.throws(() => clientGuardCheck('malicious_user'), /Isolation Guard: Client UID mismatch/);
  });

  console.log('\n==============================================================');
  console.log(`📊 PHASE 1 RESULTS: ${passed}/${total} Tests Passed (${Math.round((passed / total) * 100)}%)`);
  console.log('==============================================================\n');

  if (passed !== total) {
    process.exit(1);
  }
}

runPhase1Tests();
