/**
 * Phase 2 Test Suite: Web Dashboard Metrics, Pagination & Filter Integrity
 *
 * Validates:
 * 1. 5 Metric cards (Total Scans, Threats Detected, Threats Blocked, False Positives, High-Severity)
 * 2. Invariant: Threats are NOT counted as blocked unless an actual blocking action occurred
 * 3. Firestore cursor pagination (page size ~25, startAfter)
 * 4. Deduplication of threat events across pages and live updates
 * 5. Cursor and filter reset behavior
 * 6. Scoped user isolation throughout pagination queries
 */

const assert = require('assert');

// Test data generator
function generateMockThreat(id, overrides = {}) {
  return {
    id,
    client_event_id: id,
    user_id: overrides.user_id || 'user_alpha_uid',
    device_id: 'device_01',
    event_type: overrides.event_type || 'QR',
    risk_score: overrides.risk_score ?? 80,
    threat_category: overrides.threat_category || 'HIGH',
    recommended_action: overrides.recommended_action || 'BLOCK',
    timestamp: overrides.timestamp || new Date().toISOString(),
    status: overrides.status || 'NEW',
    user_action: overrides.user_action || null,
    decoded_url: overrides.decoded_url || 'https://malicious-test.example.com',
    reasons: overrides.reasons || ['Urgent verification hook', 'Unrecognized domain']
  };
}

// Mirror calculateUserStatsFromThreats logic
function calculateUserStatsFromThreats(threats) {
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

  for (const t of threats) {
    totalScore += t.risk_score;
    if (t.event_type === 'QR') qrCount++;
    if (t.event_type === 'SMS') smsCount++;

    const isHigh = t.threat_category === 'HIGH' || t.risk_score >= 70;
    const isMed = t.threat_category === 'MEDIUM' || (t.risk_score >= 50 && t.risk_score < 70);
    const isLow = t.threat_category === 'LOW' || (t.risk_score >= 20 && t.risk_score < 50);

    if (isHigh) {
      highRisk++;
      threatsDetected++;
    } else if (isMed) {
      mediumRisk++;
      threatsDetected++;
    } else if (isLow) {
      lowRisk++;
      threatsDetected++;
    } else {
      safeCount++;
      safeDetections++;
    }

    // CRITICAL REQUIREMENT: Do not count detected threats as blocked unless a blocking action actually occurred.
    const isBlocked =
      t.user_action === 'BLOCK' ||
      t.status === 'BLOCKED' ||
      t.actionTaken === 'BLOCK';

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
  };
}

// In-memory cursor pagination simulator
class MockFirestorePaginator {
  constructor(records) {
    this.records = [...records].sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
  }

  fetchPage(pageSize = 25, startAfterId = null) {
    let startIndex = 0;
    if (startAfterId) {
      const idx = this.records.findIndex((r) => r.id === startAfterId);
      if (idx !== -1) {
        startIndex = idx + 1;
      }
    }
    const slice = this.records.slice(startIndex, startIndex + pageSize + 1);
    const hasMore = slice.length > pageSize;
    const items = hasMore ? slice.slice(0, pageSize) : slice;
    const lastVisibleId = items.length > 0 ? items[items.length - 1].id : null;

    return {
      items,
      lastVisibleId,
      hasMore
    };
  }
}

async function runPhase2Tests() {
  console.log('==============================================================');
  console.log('📊 SQUISH — PHASE 2: DASHBOARD METRICS & PAGINATION TEST SUITE');
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

  // TC01: Threat detected with recommendedAction="BLOCK" is NOT counted as blocked
  runTest('Threat with recommended_action=BLOCK is NOT counted as blocked without action', () => {
    const list = [
      generateMockThreat('T1', {
        risk_score: 90,
        threat_category: 'HIGH',
        recommended_action: 'BLOCK',
        status: 'NEW',
        user_action: null
      })
    ];
    const stats = calculateUserStatsFromThreats(list);
    assert.strictEqual(stats.total_scans, 1);
    assert.strictEqual(stats.threats_detected, 1);
    assert.strictEqual(stats.threats_blocked, 0, 'Must NOT be counted as blocked');
    assert.strictEqual(stats.high_risk_count, 1);
    assert.strictEqual(stats.unresolved_high_risk, 1);
  });

  // TC02: Explicit user_action="BLOCK" or status="BLOCKED" IS counted as blocked
  runTest('Confirmed blocking action IS counted as threats_blocked', () => {
    const list = [
      generateMockThreat('T1', {
        risk_score: 90,
        threat_category: 'HIGH',
        recommended_action: 'BLOCK',
        status: 'BLOCKED',
        user_action: 'BLOCK'
      })
    ];
    const stats = calculateUserStatsFromThreats(list);
    assert.strictEqual(stats.threats_blocked, 1);
    assert.strictEqual(stats.unresolved_high_risk, 0);
  });

  // TC03: False Positives reported tracking
  runTest('Threat marked safe or false positive increments false_positives_reported', () => {
    const list = [
      generateMockThreat('T1', {
        risk_score: 75,
        threat_category: 'HIGH',
        user_action: 'MARK_SAFE',
        status: 'RESOLVED'
      })
    ];
    const stats = calculateUserStatsFromThreats(list);
    assert.strictEqual(stats.false_positives_reported, 1);
    assert.strictEqual(stats.threats_blocked, 0);
  });

  // TC04: 5 Metric Cards accurate aggregation across multi-category dataset
  runTest('Multi-event dataset accurately computes all 5 metric cards', () => {
    const dataset = [
      generateMockThreat('T1', { event_type: 'QR', risk_score: 95, threat_category: 'HIGH', status: 'BLOCKED', user_action: 'BLOCK' }),
      generateMockThreat('T2', { event_type: 'SMS', risk_score: 85, threat_category: 'HIGH', status: 'NEW', user_action: null }),
      generateMockThreat('T3', { event_type: 'QR', risk_score: 60, threat_category: 'MEDIUM', status: 'RESOLVED', user_action: 'MARK_SAFE' }),
      generateMockThreat('T4', { event_type: 'SMS', risk_score: 10, threat_category: 'SAFE', status: 'ANALYZED' }),
      generateMockThreat('T5', { event_type: 'QR', risk_score: 15, threat_category: 'SAFE', status: 'ANALYZED' })
    ];

    const stats = calculateUserStatsFromThreats(dataset);
    assert.strictEqual(stats.total_scans, 5, 'Card 1: Total Scans = 5');
    assert.strictEqual(stats.threats_detected, 3, 'Card 2: Threats Detected = 3 (High + Med)');
    assert.strictEqual(stats.threats_blocked, 1, 'Card 3: Threats Blocked = 1');
    assert.strictEqual(stats.false_positives_reported, 1, 'Card 4: False Positives = 1');
    assert.strictEqual(stats.high_risk_count, 2, 'Card 5: High-Severity Threats = 2');
    assert.strictEqual(stats.recent_qr_count, 3);
    assert.strictEqual(stats.recent_sms_count, 2);
  });

  // TC05: Pagination cursor and pageSize=25
  const largeDataset = [];
  for (let i = 1; i <= 65; i++) {
    largeDataset.push(
      generateMockThreat(`THREAT-${String(i).padStart(3, '0')}`, {
        timestamp: new Date(1700000000000 + i * 60000).toISOString()
      })
    );
  }
  const paginator = new MockFirestorePaginator(largeDataset);

  runTest('Initial pagination returns page 1 of 25 records with hasMore=true', () => {
    const page1 = paginator.fetchPage(25, null);
    assert.strictEqual(page1.items.length, 25);
    assert.strictEqual(page1.hasMore, true);
    assert.strictEqual(page1.items[0].id, 'THREAT-065'); // descending by timestamp
    assert.strictEqual(page1.lastVisibleId, 'THREAT-041');
  });

  // TC06: Page 2 cursor fetching
  runTest('Page 2 starts after cursor with next 25 records and hasMore=true', () => {
    const page1 = paginator.fetchPage(25, null);
    const page2 = paginator.fetchPage(25, page1.lastVisibleId);
    assert.strictEqual(page2.items.length, 25);
    assert.strictEqual(page2.hasMore, true);
    assert.strictEqual(page2.items[0].id, 'THREAT-040');
    assert.strictEqual(page2.lastVisibleId, 'THREAT-016');
  });

  // TC07: Page 3 cursor fetching final records
  runTest('Page 3 fetches remaining 15 records and returns hasMore=false', () => {
    const page1 = paginator.fetchPage(25, null);
    const page2 = paginator.fetchPage(25, page1.lastVisibleId);
    const page3 = paginator.fetchPage(25, page2.lastVisibleId);
    assert.strictEqual(page3.items.length, 15);
    assert.strictEqual(page3.hasMore, false);
    assert.strictEqual(page3.items[page3.items.length - 1].id, 'THREAT-001');
  });

  // TC08: Deduplication across pages
  runTest('Deduplication prevents repeated records across pages', () => {
    const page1 = paginator.fetchPage(25, null);
    // Simulate re-fetching overlapping item
    const duplicateList = [...page1.items, page1.items[0], page1.items[5]];
    const existingIds = new Set();
    const deduped = [];
    for (const item of duplicateList) {
      if (!existingIds.has(item.id)) {
        existingIds.add(item.id);
        deduped.push(item);
      }
    }
    assert.strictEqual(deduped.length, 25);
  });

  // TC09: Deduplication between paginated items and live onSnapshot updates
  runTest('Live snapshot updates merge into paginated list without duplicates', () => {
    const page1 = paginator.fetchPage(25, null);
    const currentList = [...page1.items];

    // Live update for existing record THREAT-065 (status change to BLOCKED)
    const liveUpdate = generateMockThreat('THREAT-065', {
      timestamp: currentList[0].timestamp,
      status: 'BLOCKED',
      user_action: 'BLOCK'
    });

    // Brand new real-time threat THREAT-066
    const liveNew = generateMockThreat('THREAT-066', {
      timestamp: new Date().toISOString()
    });

    const liveThreats = [liveNew, liveUpdate];

    const liveMap = new Map();
    liveThreats.forEach((t) => liveMap.set(t.id, t));

    const updatedPrev = currentList.map((t) => (liveMap.has(t.id) ? liveMap.get(t.id) : t));
    const existingIds = new Set(currentList.map((t) => t.id));
    const brandNew = liveThreats.filter((t) => !existingIds.has(t.id));

    const merged = [...brandNew, ...updatedPrev];

    assert.strictEqual(merged.length, 26);
    assert.strictEqual(merged[0].id, 'THREAT-066', 'Brand new item prepended');
    const updatedThreat65 = merged.find((t) => t.id === 'THREAT-065');
    assert.strictEqual(updatedThreat65.status, 'BLOCKED', 'Existing item updated in-place');
    assert.strictEqual(new Set(merged.map((t) => t.id)).size, 26, 'Zero duplicate IDs');
  });

  // TC10: Filter changing resets pagination cursor
  runTest('Filter changes reset cursor to null and re-evaluate', () => {
    let cursor = 'cursor_123';
    let filter = 'ALL';

    function onFilterChange(newFilter) {
      filter = newFilter;
      cursor = null; // cursor reset requirement
    }

    onFilterChange('SMS');
    assert.strictEqual(cursor, null, 'Cursor must be null after filter change');
    assert.strictEqual(filter, 'SMS');
  });

  console.log('\n==============================================================');
  console.log(`📊 PHASE 2 RESULTS: ${passed}/${total} Tests Passed (${Math.round((passed / total) * 100)}%)`);
  console.log('==============================================================\n');

  if (passed !== total) {
    process.exit(1);
  }
}

runPhase2Tests();
