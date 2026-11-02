'use client';



import React, { useState, useEffect, useCallback, useMemo } from 'react';

import Link from 'next/link';

import { UserShell } from '@/components/layout/UserShell';

import { RiskBadge, EventTypeBadge, StatusBadge } from '@/components/ui/Badge';

import { LoadingSpinner, TableSkeleton } from '@/components/ui/LoadingState';

import { ErrorState } from '@/components/ui/ErrorState';

import { EmptyState } from '@/components/ui/EmptyState';

import { ThreatDetailPanel } from '@/components/ui/ThreatDetailPanel';

import { api } from '@/services/api';

import { ThreatEvent, ThreatCategory, ThreatEventType, UserStats } from '@/types';

import { useAuth } from '@/context/AuthContext';

import { sessionManager } from '@/services/sessionManager';

import {

  ShieldCheck,

  ShieldAlert,

  AlertTriangle,

  QrCode,

  Smartphone,

  ArrowRight,

  RefreshCw,

  Clock,

  CheckCircle2,

  FileSearch,

  ExternalLink,

  Ban,

  Activity,

  Sparkles,

  Radio,

  Eye,

  Search,

  Filter,

  X,

  ChevronDown,

  Trash2,

  Flame,
  Zap,
  BookOpen,
  MessageSquareQuote,

} from 'lucide-react';

import {

  subscribeToUserThreats,

  fetchPaginatedUserThreats,

  fetchFullUserStats,

  takeUserThreatAction,

  deleteUserThreat,

  calculateUserStatsFromThreats,

} from '@/services/threatFirestore';

import { QueryDocumentSnapshot, DocumentData } from 'firebase/firestore';



export default function UserDashboardPage() {

  const { user } = useAuth();



  // Metric states (authoritative, all-time)

  const [stats, setStats] = useState<UserStats | null>(null);



  // Paginated threat list & pagination cursor

  const [threats, setThreats] = useState<ThreatEvent[]>([]);

  const [lastDoc, setLastDoc] = useState<QueryDocumentSnapshot<DocumentData> | null>(null);

  const [hasMore, setHasMore] = useState<boolean>(false);

  const [isLoading, setIsLoading] = useState<boolean>(true);

  const [isLoadingMore, setIsLoadingMore] = useState<boolean>(false);

  const [error, setError] = useState<string | null>(null);

  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);

  const [isLiveConnected, setIsLiveConnected] = useState<boolean>(false);



  // Interaction & detail states

  const [selectedThreat, setSelectedThreat] = useState<ThreatEvent | null>(null);

  const [actionInProgress, setActionInProgress] = useState<string | null>(null);

  const [newThreatIds, setNewThreatIds] = useState<Set<string>>(new Set());



  // Search & Filtering states

  const [searchTerm, setSearchTerm] = useState<string>('');

  const [channelFilter, setChannelFilter] = useState<string>('ALL');

  const [severityFilter, setSeverityFilter] = useState<string>('ALL');

  const [statusFilter, setStatusFilter] = useState<string>('ALL');



  // Centralized session reset registration

  useEffect(() => {

    const deregister = sessionManager.registerResetHandler(() => {

      setStats(null);

      setThreats([]);

      setLastDoc(null);

      setHasMore(false);

      setIsLoading(false);

      setIsLoadingMore(false);

      setIsLiveConnected(false);

      setSelectedThreat(null);

      setNewThreatIds(new Set());

      setError(null);

      setSearchTerm('');

      setChannelFilter('ALL');

      setSeverityFilter('ALL');

      setStatusFilter('ALL');

    });

    return () => deregister();

  }, []);



  // Fetch full all-time user stats (never fabricated, never truncated to 25 records)

  const loadStats = useCallback(async (uid: string, generation: number) => {

    try {

      // 1. Try Firestore full collection calculation

      const fsStats = await fetchFullUserStats(uid);

      if (sessionManager.isValidSession(generation, uid) && fsStats) {

        setStats(fsStats);

        return;

      }

    } catch {}



    // 2. Fallback to authoritative backend API aggregated stats

    try {

      const apiStats = await api.userThreats.getStats();

      if (sessionManager.isValidSession(generation, uid) && apiStats) {

        setStats(apiStats);

      }

    } catch (err) {

      console.warn('Backend stats retrieval deferred:', err);

    }

  }, []);



  // Load initial page of ~25 threats

  const loadInitialPage = useCallback(async () => {

    const currentUid = user?.firebase_uid;

    if (!currentUid) {

      setThreats([]);

      setStats(null);

      setIsLoading(false);

      return;

    }



    const currentGeneration = sessionManager.getGeneration();

    setIsLoading(true);

    setError(null);



    // Concurrently load full stats and initial paginated records

    loadStats(currentUid, currentGeneration);



    try {

      const res = await fetchPaginatedUserThreats({

        uid: currentUid,

        pageSize: 25,

        lastDoc: null,

      });



      if (!sessionManager.isValidSession(currentGeneration, currentUid)) return;



      if (res.items.length > 0) {

        setThreats(res.items);

        setLastDoc(res.lastVisible);

        setHasMore(res.hasMore);

      } else {

        // Fallback to backend API if Firestore returns empty on new user

        try {

          const apiRes = await api.userThreats.list({ page: 1, page_size: 25 });

          if (sessionManager.isValidSession(currentGeneration, currentUid)) {

            setThreats(apiRes.items || []);

            setHasMore(false);

          }

        } catch {

          setThreats([]);

          setHasMore(false);

        }

      }

    } catch (err: any) {

      if (sessionManager.isValidSession(currentGeneration, currentUid)) {

        console.warn('Initial threat load notice:', err);

        setError(err.message || 'Failed to load security telemetry.');

      }

    } finally {

      if (sessionManager.isValidSession(currentGeneration, currentUid)) {

        setIsLoading(false);

      }

    }

  }, [user?.firebase_uid, loadStats]);



  // Trigger initial page load on UID change or when filters reset cursor

  useEffect(() => {

    loadInitialPage();

  }, [loadInitialPage]);



  // Load next page using startAfter(lastDoc)

  const handleLoadMore = async () => {

    const currentUid = user?.firebase_uid;

    if (!currentUid || !lastDoc || isLoadingMore || !hasMore) return;



    const currentGeneration = sessionManager.getGeneration();

    setIsLoadingMore(true);



    try {

      const res = await fetchPaginatedUserThreats({

        uid: currentUid,

        pageSize: 25,

        lastDoc,

      });



      if (!sessionManager.isValidSession(currentGeneration, currentUid)) return;



      if (res.items.length > 0) {

        setThreats((prev) => {

          // Deduplicate against existing events

          const existingIds = new Set(prev.map((t) => t.id));

          const newUnique = res.items.filter((t) => !existingIds.has(t.id));

          return [...prev, ...newUnique];

        });

        setLastDoc(res.lastVisible);

        setHasMore(res.hasMore);

      } else {

        setHasMore(false);

      }

    } catch (err: any) {

      console.warn('Load more notice:', err);

    } finally {

      if (sessionManager.isValidSession(currentGeneration, currentUid)) {

        setIsLoadingMore(false);

      }

    }

  };



  // Real-time Firestore subscription scoped to authenticated UID

  useEffect(() => {

    const currentUid = user?.firebase_uid;

    if (!currentUid) {

      setIsLiveConnected(false);

      return;

    }



    const currentGeneration = sessionManager.getGeneration();



    const unsubscribe = subscribeToUserThreats(

      currentUid,

      (liveThreats) => {

        if (!sessionManager.isValidSession(currentGeneration, currentUid)) return;



        // Deduplicate and merge live threats at top of the list

        setThreats((prev) => {

          const liveMap = new Map<string, ThreatEvent>();

          liveThreats.forEach((t) => liveMap.set(t.id, t));



          // Merge: replace existing updated documents or prepend new incoming events

          const updatedPrev = prev.map((t) => (liveMap.has(t.id) ? liveMap.get(t.id)! : t));

          const existingIds = new Set(prev.map((t) => t.id));

          const brandNew = liveThreats.filter((t) => !existingIds.has(t.id));



          if (brandNew.length > 0) {

            setNewThreatIds((curr) => {

              const next = new Set(curr);

              brandNew.forEach((t) => next.add(t.id));

              return next;

            });

            setTimeout(() => {

              setNewThreatIds((curr) => {

                const next = new Set(curr);

                brandNew.forEach((t) => next.delete(t.id));

                return next;

              });

            }, 4000);

          }



          return [...brandNew, ...updatedPrev];

        });



        // Recompute stats seamlessly if live threats change

        loadStats(currentUid, currentGeneration);

        setIsLiveConnected(true);

      },

      (err) => {

        if (!sessionManager.isValidSession(currentGeneration, currentUid)) return;

        console.warn('Real-time telemetry notice:', err.message);

        setIsLiveConnected(false);
      },
      250
    );



    return () => {

      unsubscribe();

      setIsLiveConnected(false);

    };

  }, [user?.firebase_uid, loadStats]);



  // Manual refresh action

  const handleRefresh = async () => {

    const currentUid = user?.firebase_uid;

    if (!currentUid) return;

    setIsRefreshing(true);

    await loadInitialPage();

    setIsRefreshing(false);

  };



  // User Actions: Block or Mark as Safe (False positive)

  const handleUserAction = async (threatId: string, action: 'BLOCK' | 'MARK_SAFE') => {

    const currentUid = user?.firebase_uid;

    if (!currentUid) return;

    setActionInProgress(threatId);



    try {

      await takeUserThreatAction(currentUid, threatId, action);

      try {

        await api.userThreats.takeAction(threatId, action);

      } catch {}



      // Optimistically update list

      setThreats((prev) =>

        prev.map((t) =>

          t.id === threatId

            ? { ...t, status: action === 'BLOCK' ? 'BLOCKED' : 'RESOLVED', user_action: action }

            : t

        )

      );



      if (selectedThreat?.id === threatId) {

        setSelectedThreat((prev) =>

          prev ? { ...prev, status: action === 'BLOCK' ? 'BLOCKED' : 'RESOLVED', user_action: action } : null

        );

      }



      // Re-fetch accurate metrics

      loadStats(currentUid, sessionManager.getGeneration());

    } catch (err: any) {

      alert(`Action failed: ${err.message}`);

    } finally {

      setActionInProgress(null);

    }

  };



  // User Action: Delete threat event

  const handleDeleteThreat = async (threatId: string) => {

    const currentUid = user?.firebase_uid;

    if (!currentUid) return;

    if (!confirm('Are you sure you want to permanently delete this threat event?')) return;



    setActionInProgress(threatId);

    try {

      await deleteUserThreat(currentUid, threatId);

      try {

        await api.userThreats.delete(threatId);

      } catch {}



      setThreats((prev) => prev.filter((t) => t.id !== threatId));

      if (selectedThreat?.id === threatId) {

        setSelectedThreat(null);

      }

      loadStats(currentUid, sessionManager.getGeneration());

    } catch (err: any) {

      alert(`Delete failed: ${err.message}`);

    } finally {

      setActionInProgress(null);

    }

  };



  // Filtered threats based on Search & Filter options

  const filteredThreats = useMemo(() => {

    return threats.filter((item) => {

      // Channel Filter

      if (channelFilter !== 'ALL' && item.event_type !== channelFilter) {

        return false;

      }



      // Severity Filter

      if (severityFilter !== 'ALL') {

        if (severityFilter === 'HIGH') {

          if (item.threat_category !== 'HIGH' && item.risk_score < 70) return false;

        } else if (severityFilter === 'MEDIUM') {

          if (item.threat_category !== 'MEDIUM' && (item.risk_score < 50 || item.risk_score >= 70)) return false;

        } else if (severityFilter === 'LOW') {

          if (item.threat_category !== 'LOW' && (item.risk_score < 20 || item.risk_score >= 50)) return false;

        } else if (severityFilter === 'SAFE') {

          if (item.threat_category !== 'SAFE' && item.risk_score >= 20) return false;

        }

      }



      // Status Filter

      if (statusFilter !== 'ALL') {

        const itemStatus = (item.status || 'NEW').toUpperCase();

        if (statusFilter === 'BLOCKED' && itemStatus !== 'BLOCKED' && item.user_action !== 'BLOCK') return false;

        if (statusFilter === 'RESOLVED' && itemStatus !== 'RESOLVED' && item.user_action !== 'MARK_SAFE') return false;

        if (statusFilter === 'NEW' && itemStatus !== 'NEW' && itemStatus !== 'ANALYZED') return false;

      }



      // Search Term Matching

      if (searchTerm.trim() !== '') {

        const term = searchTerm.toLowerCase();

        const url = (item.decoded_url || '').toLowerCase();

        const domain = (item.url_metadata?.domain || '').toLowerCase();

        const preview = (item.sanitized_preview || '').toLowerCase();

        const reasons = (item.reasons || []).join(' ').toLowerCase();

        const id = item.id.toLowerCase();

        const sender = (item.device_id || '').toLowerCase();



        return (

          url.includes(term) ||

          domain.includes(term) ||

          preview.includes(term) ||

          reasons.includes(term) ||

          id.includes(term) ||

          sender.includes(term)

        );

      }



      return true;

    });

  }, [threats, channelFilter, severityFilter, statusFilter, searchTerm]);



  // Separate SMS and QR Items (Authoritative real database records)

  const smsItems = useMemo(() => threats.filter((t) => t.event_type === 'SMS'), [threats]);

  const qrItems = useMemo(() => threats.filter((t) => t.event_type === 'QR'), [threats]);



  const isDangerous = (t: ThreatEvent) => {
    const cat = (t.threat_category || '').toUpperCase();
    return t.risk_score >= 70 || cat === 'HIGH' || cat === 'MALICIOUS' || cat === 'SMISHING' || cat === 'QUISHING';
  };
  const isAttention = (t: ThreatEvent) => {
    if (isDangerous(t)) return false;
    const cat = (t.threat_category || '').toUpperCase();
    return t.risk_score >= 40 || cat === 'MEDIUM' || cat === 'SUSPICIOUS' || cat === 'LOW' || cat === 'ATTENTION';
  };
  const isSafe = (t: ThreatEvent) => !isDangerous(t) && !isAttention(t);

  // 4 Primary SMS Statistics (Authoritative real account database metrics)
  const smsTotal = stats?.sms_total ?? smsItems.length;

  const smsSafe = stats?.sms_safe ?? smsItems.filter(isSafe).length;

  const smsAttention = stats?.sms_attention ?? smsItems.filter(isAttention).length;

  const smsDangerous = stats?.sms_dangerous ?? smsItems.filter(isDangerous).length;



  // 4 Separate QR Statistics (Authoritative real account database metrics)

  const qrTotal = stats?.qr_total ?? qrItems.length;

  const qrSafe = stats?.qr_safe ?? qrItems.filter(isSafe).length;

  const qrAttention = stats?.qr_attention ?? qrItems.filter(isAttention).length;

  const qrDangerous = stats?.qr_dangerous ?? qrItems.filter(isDangerous).length;

  const totalScans = stats?.total_scans ?? (smsTotal + qrTotal);



  // Important Safety Alerts (Dangerous or unresolved items needing user action)

  const safetyAlerts = useMemo(

    () => threats.filter((t) => (isDangerous(t) || isAttention(t)) && t.status !== 'RESOLVED' && t.status !== 'FALSE_POSITIVE'),

    [threats]

  );



  // Recent messages and QR scans

  const recentMessages = useMemo(() => smsItems.slice(0, 4), [smsItems]);

  const recentQrScans = useMemo(() => qrItems.slice(0, 4), [qrItems]);



  const hasActiveFilters =

    channelFilter !== 'ALL' ||

    severityFilter !== 'ALL' ||

    statusFilter !== 'ALL' ||

    searchTerm.trim() !== '';



  const clearAllFilters = () => {

    setChannelFilter('ALL');

    setSeverityFilter('ALL');

    setStatusFilter('ALL');

    setSearchTerm('');

  };



  return (

    <UserShell>

      <div className="space-y-6">

        {/* Top Header & Telemetry Status Banner */}

        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 glass-panel rounded-2xl p-6 shadow-2xl relative overflow-hidden">

          <div className="flex items-start space-x-4">

            <div className="w-12 h-12 rounded-xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400 shrink-0 shadow-lg shadow-cyan-500/10">

              <ShieldCheck className="w-7 h-7" />

            </div>

            <div>

              <div className="flex flex-wrap items-center gap-2">

                <h1 className="text-xl font-bold text-slate-100 tracking-tight">

                  Your Safety Overview

                </h1>

                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] font-semibold border bg-cyan-500/10 text-cyan-400 border-cyan-500/30">

                  Real-Time Activity

                </span>

                {isLiveConnected ? (

                  <span className="inline-flex items-center space-x-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-cyan-500/10 text-cyan-400 border border-cyan-500/30">

                    <span className="relative flex h-2 w-2">

                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75"></span>

                      <span className="relative inline-flex rounded-full h-2 w-2 bg-cyan-500"></span>

                    </span>

                    <span>Live Updates</span>

                  </span>

                ) : (

                  <span className="inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-slate-800 text-slate-400 border border-slate-700">

                    <span>Synchronized</span>

                  </span>

                )}

              </div>

              <p className="text-xs text-slate-400 mt-1 max-w-2xl leading-relaxed">

                Logged in as <strong className="text-slate-200">{user?.display_name || user?.email}</strong>. Data is strictly isolated to your authenticated account.

              </p>

            </div>

          </div>



          <div className="flex items-center space-x-3 self-start md:self-auto">

            <button

              onClick={handleRefresh}

              disabled={isRefreshing || isLoading}

              className="inline-flex items-center space-x-1.5 px-3.5 py-2 rounded-xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700 text-xs font-semibold text-slate-200 transition-all hover:border-slate-600 disabled:opacity-50"

            >

              <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin text-cyan-400' : ''}`} />

              <span>{isRefreshing ? 'Syncing...' : 'Sync Telemetry'}</span>

            </button>

            <Link

              href="/threats"

              className="inline-flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/25 transition-all"

            >

              <span>All Checks & History</span>

              <ArrowRight className="w-3.5 h-3.5" />

            </Link>

          </div>

        </div>



        {error && (

          <ErrorState

            title="Failed to Load Security Overview"

            message={error}

            onRetry={handleRefresh}

          />

        )}



        {/* SECTION 1: 4 PRIMARY MESSAGE STATISTICS (PHASE 1 REQUIREMENT) */}

        <div>

          <div className="flex items-center justify-between mb-3">

            <h2 className="text-sm font-bold text-slate-100 flex items-center gap-2">

              <Smartphone className="w-4 h-4 text-cyan-400" />

              <span>Message Safety Activity</span>

            </h2>

            <span className="text-xs text-slate-400 font-mono">{smsTotal} Total Scanned</span>

          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">

            <div className="glass-card rounded-2xl p-5 border-slate-800 hover:border-cyan-500/40 transition-all">

              <div className="text-xs text-slate-400 font-semibold mb-1">Total Messages Scanned</div>

              <div className="text-2xl font-black font-mono text-slate-100">{smsTotal}</div>

            </div>

            <div className="glass-card rounded-2xl p-5 border-slate-800 hover:border-emerald-500/40 transition-all">

              <div className="text-xs text-slate-400 font-semibold mb-1">Messages Looking Safe</div>

              <div className="text-2xl font-black font-mono text-emerald-400">{smsSafe}</div>

            </div>

            <div className="glass-card rounded-2xl p-5 border-slate-800 hover:border-amber-500/40 transition-all">

              <div className="text-xs text-slate-400 font-semibold mb-1">Messages Needing Attention</div>

              <div className="text-2xl font-black font-mono text-amber-400">{smsAttention}</div>

            </div>

            <div className="glass-card rounded-2xl p-5 border-slate-800 hover:border-rose-500/40 transition-all">

              <div className="text-xs text-slate-400 font-semibold mb-1">Messages Identified as Dangerous</div>

              <div className="text-2xl font-black font-mono text-rose-400">{smsDangerous}</div>

            </div>

          </div>

        </div>



        {/* SECTION 2: SEPARATE QR CODE ACTIVITY (PHASE 1 REQUIREMENT) */}

        <div>

          <div className="flex items-center justify-between mb-3">

            <h2 className="text-sm font-bold text-slate-100 flex items-center gap-2">

              <QrCode className="w-4 h-4 text-purple-400" />

              <span>QR Code Safety Activity</span>

            </h2>

            <span className="text-xs text-slate-400 font-mono">{qrTotal} Total Scanned</span>

          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">

            <div className="glass-card rounded-2xl p-5 border-slate-800 hover:border-purple-500/40 transition-all">

              <div className="text-xs text-slate-400 font-semibold mb-1">Total QR Codes Scanned</div>

              <div className="text-2xl font-black font-mono text-slate-100">{qrTotal}</div>

            </div>

            <div className="glass-card rounded-2xl p-5 border-slate-800 hover:border-emerald-500/40 transition-all">

              <div className="text-xs text-slate-400 font-semibold mb-1">QR Codes Looking Safe</div>

              <div className="text-2xl font-black font-mono text-emerald-400">{qrSafe}</div>

            </div>

            <div className="glass-card rounded-2xl p-5 border-slate-800 hover:border-amber-500/40 transition-all">

              <div className="text-xs text-slate-400 font-semibold mb-1">QR Codes Needing Attention</div>

              <div className="text-2xl font-black font-mono text-amber-400">{qrAttention}</div>

            </div>

            <div className="glass-card rounded-2xl p-5 border-slate-800 hover:border-rose-500/40 transition-all">

              <div className="text-xs text-slate-400 font-semibold mb-1">QR Codes Identified as Dangerous</div>

              <div className="text-2xl font-black font-mono text-rose-400">{qrDangerous}</div>

            </div>

          </div>

        </div>



        {/* SECTION 3: QUICK ACTIONS (PHASE 9 REQUIREMENT) */}
        <div>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-bold text-slate-100 flex items-center gap-2">
              <Zap className="w-4 h-4 text-cyan-400" />
              <span>Quick Actions</span>
            </h2>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <Link
              href="/threats"
              className="glass-card rounded-xl p-4 border-slate-800 hover:border-cyan-500/40 hover:bg-slate-800/40 transition-all flex flex-col items-center text-center space-y-2 group"
            >
              <div className="w-10 h-10 rounded-xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400 group-hover:scale-105 transition-transform">
                <Search className="w-5 h-5" />
              </div>
              <div>
                <div className="text-xs font-bold text-white">Scan Threat</div>
                <div className="text-[10px] text-slate-400">Scan SMS text or URL</div>
              </div>
            </Link>

            <Link
              href="/security/trusted-users"
              className="glass-card rounded-xl p-4 border-slate-800 hover:border-emerald-500/40 hover:bg-slate-800/40 transition-all flex flex-col items-center text-center space-y-2 group"
            >
              <div className="w-10 h-10 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center text-emerald-400 group-hover:scale-105 transition-transform">
                <ShieldCheck className="w-5 h-5" />
              </div>
              <div>
                <div className="text-xs font-bold text-white">Security Center</div>
                <div className="text-[10px] text-slate-400">Trusted & Blocked lists</div>
              </div>
            </Link>

            <Link
              href="/safety-alerts"
              className="glass-card rounded-xl p-4 border-slate-800 hover:border-rose-500/40 hover:bg-slate-800/40 transition-all flex flex-col items-center text-center space-y-2 group"
            >
              <div className="w-10 h-10 rounded-xl bg-rose-500/10 border border-rose-500/30 flex items-center justify-center text-rose-400 group-hover:scale-105 transition-transform">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div>
                <div className="text-xs font-bold text-white">Safety Alerts</div>
                <div className="text-[10px] text-slate-400">
                  {safetyAlerts.length > 0 ? `${safetyAlerts.length} Active` : '0 Active'}
                </div>
              </div>
            </Link>

            <Link
              href="/feedback-history"
              className="glass-card rounded-xl p-4 border-slate-800 hover:border-purple-500/40 hover:bg-slate-800/40 transition-all flex flex-col items-center text-center space-y-2 group"
            >
              <div className="w-10 h-10 rounded-xl bg-purple-500/10 border border-purple-500/30 flex items-center justify-center text-purple-400 group-hover:scale-105 transition-transform">
                <MessageSquareQuote className="w-5 h-5" />
              </div>
              <div>
                <div className="text-xs font-bold text-white">Feedback Log</div>
                <div className="text-[10px] text-slate-400">AI reclassification trail</div>
              </div>
            </Link>
          </div>
        </div>

        {/* SECTION 4: IMPORTANT SAFETY ALERTS (PHASE 9 REQUIREMENT) */}

        {safetyAlerts.length > 0 && (

          <div className="p-5 rounded-2xl bg-rose-950/20 border border-rose-500/30">

            <div className="flex items-center justify-between mb-3">

              <div className="flex items-center space-x-2">

                <span className="w-2.5 h-2.5 rounded-full bg-rose-500 animate-pulse" />

                <h3 className="text-sm font-bold text-rose-300">

                  Important Safety Alerts ({safetyAlerts.length})

                </h3>

              </div>

              <Link
                href="/safety-alerts"
                className="text-xs font-bold text-cyan-400 hover:text-cyan-300 flex items-center gap-1"
              >
                <span>View All Alerts</span>
                <ExternalLink className="w-3 h-3" />
              </Link>

            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">

              {safetyAlerts.slice(0, 2).map((alert) => (

                <div

                  key={alert.id}

                  onClick={() => setSelectedThreat(alert)}

                  className="p-3.5 rounded-xl bg-slate-900/80 border border-rose-500/20 hover:border-rose-500/40 cursor-pointer transition-all flex items-start space-x-3"

                >

                  <div className="w-8 h-8 rounded-lg bg-rose-500/10 border border-rose-500/30 flex items-center justify-center text-rose-400 shrink-0">

                    <AlertTriangle className="w-4 h-4" />

                  </div>

                  <div className="flex-1 min-w-0">

                    <div className="flex items-center justify-between">

                      <span className="text-xs font-bold text-slate-200 truncate">{alert.sender || alert.device_id || 'Security Alert'}</span>

                      <span className="text-[10px] text-rose-400 font-mono font-semibold">Dangerous</span>

                    </div>

                    <p className="text-xs text-slate-400 truncate mt-0.5">{alert.raw_content || alert.sanitized_preview || alert.decoded_url}</p>

                  </div>

                </div>

              ))}

            </div>

          </div>

        )}



        {/* SECTION 5: SAFETY TIP RECOMMENDATION (PHASE 9 REQUIREMENT) */}
        <div className="p-4 rounded-xl bg-cyan-950/25 border border-cyan-800/40 flex items-start space-x-3.5">
          <ShieldAlert className="w-5 h-5 text-cyan-400 shrink-0 mt-0.5" />
          <div className="text-xs text-slate-300 space-y-1">
            <p className="font-semibold text-cyan-300">Safety Tip Recommendation: Banking SMS Verification</p>
            <p className="text-slate-400 leading-relaxed">
              Legitimate Indian bank notifications always originate from registered 6-character TRAI headers (e.g. <code className="text-cyan-300">VK-HDFCBK</code> or <code className="text-cyan-300">AX-SBIBNK</code>).
              Legitimate transaction debit SMS will <strong>never ask you to click a link to cancel a transfer or claim a refund</strong>.
            </p>
          </div>
        </div>

        {/* SECTION 6: DAILY TIP (PHASE 9 REQUIREMENT) */}
        <div className="p-4 rounded-xl bg-slate-900/80 border border-slate-800 flex items-start space-x-3.5">
          <Zap className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
          <div className="text-xs text-slate-300 space-y-1">
            <p className="font-semibold text-amber-300">Daily Tip: QR Code Safety in Public Places</p>
            <p className="text-slate-400 leading-relaxed">
              When scanning QR codes at parking meters, restaurants, or payment counters, visually check that no fraudulent sticker has been placed over the original code before confirming payment.
            </p>
          </div>
        </div>

        {/* SECTION 7: ACADEMY / EDUCATIONAL RESOURCES (PHASE 9 REQUIREMENT) */}
        <div className="p-5 rounded-2xl bg-slate-900/60 border border-slate-800 space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-white flex items-center gap-2">
              <BookOpen className="w-4 h-4 text-cyan-400" />
              <span>Squish Safety Academy & Defense Guides</span>
            </h3>
            <span className="text-xs text-slate-500 font-mono">3 Interactive Modules</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-1">
            <div className="p-3.5 bg-slate-950/70 rounded-xl border border-slate-800/80 space-y-1.5 hover:border-cyan-500/40 transition-colors">
              <div className="text-xs font-bold text-cyan-300">1. Banking SMS vs Smishing</div>
              <p className="text-[11px] text-slate-400 leading-snug">
                Learn how Squish&apos;s TinyBERT differentiates authentic debit/credit notifications from fake KYC expiration traps.
              </p>
            </div>
            <div className="p-3.5 bg-slate-950/70 rounded-xl border border-slate-800/80 space-y-1.5 hover:border-cyan-500/40 transition-colors">
              <div className="text-xs font-bold text-purple-300">2. Quishing Attack Vectors</div>
              <p className="text-[11px] text-slate-400 leading-snug">
                Understand how malicious actors craft disguised QR codes to hijack UPI sessions and harvest credentials.
              </p>
            </div>
            <div className="p-3.5 bg-slate-950/70 rounded-xl border border-slate-800/80 space-y-1.5 hover:border-cyan-500/40 transition-colors">
              <div className="text-xs font-bold text-amber-300">3. OTP & Urgency Tactics</div>
              <p className="text-[11px] text-slate-400 leading-snug">
                Master how psychological pressure and fake countdown timers are used by cybercriminals to bypass 2FA.
              </p>
            </div>
          </div>
        </div>

        {/* SECTION 8: REMAINING SECTIONS - OVERVIEW VISUALIZATIONS & THREAT HISTORY */}
        {/* Security Overview Visualizations */}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

          {/* Channel Distribution Card */}

          <div className="glass-card rounded-2xl p-6 flex flex-col justify-between">

            <div>

              <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">

                <Activity className="w-4 h-4 text-cyan-400" />

                Detection Channels

              </h3>

              <p className="text-xs text-slate-400 mt-1">

                Source breakdown between QR Quishing and SMS Smishing.

              </p>

            </div>

            <div className="space-y-4 my-6">

              <div>

                <div className="flex justify-between text-xs font-semibold mb-1">

                  <span className="text-slate-300 flex items-center gap-1.5">

                    <QrCode className="w-3.5 h-3.5 text-cyan-400" /> QR Quishing

                  </span>

                  <span className="font-mono text-cyan-400">{stats?.recent_qr_count ?? 0}</span>

                </div>

                <div className="w-full bg-slate-800 rounded-full h-2 overflow-hidden">

                  <div

                    className="bg-cyan-500 h-2 rounded-full transition-all duration-500"

                    style={{

                      width: `${totalScans > 0 ? ((stats?.recent_qr_count ?? 0) / totalScans) * 100 : 0}%`,

                    }}

                  />

                </div>

              </div>



              <div>

                <div className="flex justify-between text-xs font-semibold mb-1">

                  <span className="text-slate-300 flex items-center gap-1.5">

                    <Smartphone className="w-3.5 h-3.5 text-blue-400" /> SMS Smishing

                  </span>

                  <span className="font-mono text-blue-400">{stats?.recent_sms_count ?? 0}</span>

                </div>

                <div className="w-full bg-slate-800 rounded-full h-2 overflow-hidden">

                  <div

                    className="bg-blue-500 h-2 rounded-full transition-all duration-500"

                    style={{

                      width: `${totalScans > 0 ? ((stats?.recent_sms_count ?? 0) / totalScans) * 100 : 0}%`,

                    }}

                  />

                </div>

              </div>

            </div>



            <div className="pt-4 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-400">

              <span>Dual Engine: TinyBERT + XGBoost</span>

              <span className="text-emerald-400 font-semibold">Active</span>

            </div>

          </div>



          {/* Severity Breakdown Card */}

          <div className="glass-card rounded-2xl p-6 flex flex-col justify-between">

            <div>

              <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">

                <ShieldAlert className="w-4 h-4 text-rose-400" />

                Severity Breakdown

              </h3>

              <p className="text-xs text-slate-400 mt-1">

                Risk classification of user-scoped events.

              </p>

            </div>

            <div className="grid grid-cols-4 gap-2 my-6">

              <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-3 text-center">

                <span className="text-[10px] uppercase font-bold text-rose-400 block mb-1">High</span>

                <span className="text-xl font-bold font-mono text-rose-400">{stats?.high_risk_count ?? (smsDangerous + qrDangerous)}</span>

              </div>

              <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-3 text-center">

                <span className="text-[10px] uppercase font-bold text-amber-400 block mb-1">Med</span>

                <span className="text-xl font-bold font-mono text-amber-400">{stats?.medium_risk_count ?? 0}</span>

              </div>

              <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-3 text-center">

                <span className="text-[10px] uppercase font-bold text-blue-400 block mb-1">Low</span>

                <span className="text-xl font-bold font-mono text-blue-400">{stats?.low_risk_count ?? 0}</span>

              </div>

              <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-3 text-center">

                <span className="text-[10px] uppercase font-bold text-emerald-400 block mb-1">Safe</span>

                <span className="text-xl font-bold font-mono text-emerald-400">{stats?.safe_count ?? 0}</span>

              </div>

            </div>



            <div className="pt-4 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-400">

              <span>Average Risk Score</span>

              <span className="font-mono font-bold text-slate-200">{stats?.avg_risk_score ?? 0} / 100</span>

            </div>

          </div>



          {/* Protection Health Index Card */}

          <div className="glass-card rounded-2xl p-6 flex flex-col justify-between">

            <div>

              <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">

                <Sparkles className="w-4 h-4 text-cyan-400" />

                Active Protection Status

              </h3>

              <p className="text-xs text-slate-400 mt-1">

                Real-time security posture evaluation.

              </p>

            </div>



            <div className="my-6 text-center">

              <div className="my-2">

                <ShieldCheck className="w-10 h-10 text-cyan-400 mx-auto" />

              </div>

              <span className="text-xs uppercase font-bold tracking-wider text-slate-400 mt-1 block">

                On-Device Protection Active

              </span>

              {safetyAlerts.length > 0 ? (

                <div className="mt-3 inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs font-semibold">

                  <AlertTriangle className="w-3.5 h-3.5" />

                  <span>{safetyAlerts.length} Attention Alert{safetyAlerts.length > 1 ? 's' : ''} Pending</span>

                </div>

              ) : (

                <div className="mt-3 inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-semibold">

                  <CheckCircle2 className="w-3.5 h-3.5" />

                  <span>All Detections Mitigated</span>

                </div>

              )}

            </div>



            <div className="pt-4 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-400">

              <span>Account Boundary</span>

              <span className="text-cyan-400 font-mono text-xs">Security Rules Enforced</span>

            </div>

          </div>

        </div>



        {/* PAGINATED THREAT HISTORY SECTION (Phase 2 Requirement) */}

        <div className="glass-panel rounded-2xl p-6 space-y-5">

          <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">

            <div>

              <h2 className="text-base font-bold text-slate-100 flex items-center gap-2">

                <Radio className="w-4 h-4 text-cyan-400 animate-pulse" />

                Security Threat History & Telemetry Feed

              </h2>

              <p className="text-xs text-slate-400 mt-0.5">

                Paginated on-device and synchronized detections scoped to your account ({threats.length} loaded).

              </p>

            </div>



            {hasActiveFilters && (

              <button

                onClick={clearAllFilters}

                className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs text-cyan-400 transition-colors self-start md:self-auto"

              >

                <X className="w-3.5 h-3.5" />

                <span>Reset Filters</span>

              </button>

            )}

          </div>



          {/* Search Bar & Filter Controls */}

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-2">

            {/* Search Input */}

            <div className="relative">

              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />

              <input

                type="text"

                placeholder="Search URL, domain, content..."

                value={searchTerm}

                onChange={(e) => setSearchTerm(e.target.value)}

                className="w-full pl-9 pr-8 py-2 bg-slate-900/80 border border-slate-700 rounded-xl text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-cyan-500 transition-colors"

              />

              {searchTerm && (

                <button

                  onClick={() => setSearchTerm('')}

                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-200"

                >

                  <X className="w-3.5 h-3.5" />

                </button>

              )}

            </div>



            {/* Channel Filter */}

            <div>

              <select

                value={channelFilter}

                onChange={(e) => setChannelFilter(e.target.value)}

                className="w-full px-3 py-2 bg-slate-900/80 border border-slate-700 rounded-xl text-xs text-slate-200 focus:outline-none focus:border-cyan-500 transition-colors"

              >

                <option value="ALL">All Channels (QR & SMS)</option>

                <option value="QR">QR Quishing Only</option>

                <option value="SMS">SMS Smishing Only</option>

              </select>

            </div>



            {/* Severity Filter */}

            <div>

              <select

                value={severityFilter}

                onChange={(e) => setSeverityFilter(e.target.value)}

                className="w-full px-3 py-2 bg-slate-900/80 border border-slate-700 rounded-xl text-xs text-slate-200 focus:outline-none focus:border-cyan-500 transition-colors"

              >

                <option value="ALL">All Results</option>

                <option value="HIGH">High & Critical Threats</option>

                <option value="MEDIUM">Medium Suspicious</option>

                <option value="LOW">Minor Caution</option>

                <option value="SAFE">Verified Safe</option>

              </select>

            </div>



            {/* Status Filter */}

            <div>

              <select

                value={statusFilter}

                onChange={(e) => setStatusFilter(e.target.value)}

                className="w-full px-3 py-2 bg-slate-900/80 border border-slate-700 rounded-xl text-xs text-slate-200 focus:outline-none focus:border-cyan-500 transition-colors"

              >

                <option value="ALL">All Statuses</option>

                <option value="BLOCKED">Blocked</option>

                <option value="RESOLVED">Resolved / Marked Safe</option>

                <option value="NEW">New / Unreviewed</option>

              </select>

            </div>

          </div>



          {/* Threat Records Table / List */}

          {isLoading ? (

            <div className="py-12">

              <TableSkeleton rows={5} />

            </div>

          ) : filteredThreats.length === 0 ? (

            <div className="py-12 text-center text-slate-400 border border-dashed border-slate-800 rounded-xl">

              <ShieldCheck className="w-10 h-10 mx-auto text-slate-600 mb-2" />

              <p className="text-sm font-semibold text-slate-300">

                {hasActiveFilters ? 'No Records Matching Filters' : 'No Security Events Recorded'}

              </p>

              <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">

                {hasActiveFilters

                  ? 'Try adjusting your search query or filter criteria to see more events.'

                  : 'Scans performed in the Android app will synchronize into this isolated view.'}

              </p>

              {hasActiveFilters && (

                <button

                  onClick={clearAllFilters}

                  className="mt-4 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-cyan-400 transition-colors"

                >

                  Clear Filters

                </button>

              )}

            </div>

          ) : (

            <div className="space-y-4">

              <div className="overflow-x-auto">

                <table className="w-full text-left text-xs">

                  <thead>

                    <tr className="border-b border-slate-800 text-slate-400 uppercase text-[10px] tracking-wider font-semibold">

                      <th className="pb-3 pl-3">Channel</th>

                      <th className="pb-3">Classification</th>

                      <th className="pb-3">Risk Index</th>

                      <th className="pb-3">Scanned Payload Preview</th>

                      <th className="pb-3">Timestamp</th>

                      <th className="pb-3">Status</th>

                      <th className="pb-3 pr-3 text-right">Actions</th>

                    </tr>

                  </thead>

                  <tbody className="divide-y divide-slate-800/60 font-sans">

                    {filteredThreats.map((threat) => {

                      const isNewLive = newThreatIds.has(threat.id);

                      return (

                      <tr

                        key={threat.id}

                        className={`transition-all duration-300 group cursor-pointer ${

                          isNewLive

                            ? 'bg-cyan-950/30 border-l-2 border-cyan-400 animate-fade-in-up'

                            : 'hover:bg-slate-800/40'

                        }`}

                        onClick={() => setSelectedThreat(threat)}

                      >

                        <td className="py-3.5 pl-3 font-mono">

                          <div className="flex items-center gap-1.5">

                            <EventTypeBadge type={threat.event_type} />

                            {isNewLive && (

                              <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 animate-pulse">

                                Live

                              </span>

                            )}

                          </div>

                        </td>

                        <td className="py-3.5 font-semibold text-slate-200">

                          {threat.threat_category === 'HIGH'

                            ? threat.event_type === 'SMS'

                              ? 'Smishing'

                              : 'Quishing'

                            : threat.threat_category === 'MEDIUM'

                            ? 'Suspicious'

                            : threat.threat_category === 'SAFE'

                            ? 'Safe Scan'

                            : 'Low Risk'}

                        </td>

                        <td className="py-3.5">

                          <RiskBadge category={threat.threat_category} score={threat.risk_score} />

                        </td>

                        <td className="py-3.5 max-w-xs truncate font-mono text-slate-300">

                          {threat.decoded_url || threat.sanitized_preview || 'Scanned Payload'}

                        </td>

                        <td className="py-3.5 text-slate-400">

                          {new Date(threat.timestamp).toLocaleDateString([], {

                            month: 'short',

                            day: 'numeric',

                          })}{' '}

                          {new Date(threat.timestamp).toLocaleTimeString([], {

                            hour: '2-digit',

                            minute: '2-digit',

                          })}

                        </td>

                        <td className="py-3.5">

                          <StatusBadge status={threat.status} />

                        </td>

                        <td className="py-3.5 pr-3 text-right" onClick={(e) => e.stopPropagation()}>

                          <div className="flex items-center justify-end gap-1.5">

                            <button

                              onClick={() => setSelectedThreat(threat)}

                              className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors"

                              title="Inspect Details"

                            >

                              <Eye className="w-3.5 h-3.5" />

                            </button>

                            {threat.status !== 'BLOCKED' && (

                              <button

                                onClick={() => handleUserAction(threat.id, 'BLOCK')}

                                disabled={actionInProgress === threat.id}

                                className="p-1.5 rounded-lg bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 transition-colors disabled:opacity-50"

                                title="Block Threat"

                              >

                                <Ban className="w-3.5 h-3.5" />

                              </button>

                            )}

                            {threat.status !== 'RESOLVED' && threat.risk_score >= 40 && (

                              <button

                                onClick={() => handleUserAction(threat.id, 'MARK_SAFE')}

                                disabled={actionInProgress === threat.id}

                                className="p-1.5 rounded-lg bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 transition-colors disabled:opacity-50"

                                title="Mark as Safe (False Positive)"

                              >

                                <CheckCircle2 className="w-3.5 h-3.5" />

                              </button>

                            )}

                            <button

                              onClick={() => handleDeleteThreat(threat.id)}

                              disabled={actionInProgress === threat.id}

                              className="p-1.5 rounded-lg bg-slate-800/80 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 transition-colors disabled:opacity-50"

                              title="Delete Record"

                            >

                              <Trash2 className="w-3.5 h-3.5" />

                            </button>

                          </div>

                        </td>

                      </tr>

                    );

                  })}

                  </tbody>

                </table>

              </div>



              {/* Load More Pagination Action */}

              {hasMore && (

                <div className="pt-4 flex flex-col sm:flex-row items-center justify-between gap-3 border-t border-slate-800 text-xs text-slate-400">

                  <span>

                    Showing {filteredThreats.length} of {totalScans} total recorded scans

                  </span>

                  <button

                    onClick={handleLoadMore}

                    disabled={isLoadingMore}

                    className="inline-flex items-center space-x-2 px-5 py-2.5 rounded-xl bg-slate-800/90 hover:bg-slate-800 border border-slate-700 hover:border-slate-600 font-semibold text-slate-200 transition-all shadow-md disabled:opacity-50"

                  >

                    {isLoadingMore ? (

                      <>

                        <RefreshCw className="w-3.5 h-3.5 animate-spin text-cyan-400" />

                        <span>Loading next batch...</span>

                      </>

                    ) : (

                      <>

                        <span>Load More Records</span>

                        <ChevronDown className="w-3.5 h-3.5 text-cyan-400" />

                      </>

                    )}

                  </button>

                </div>

              )}

            </div>

          )}

        </div>



        {/* Responsive Threat Detail Side Panel */}

        <ThreatDetailPanel

          threat={selectedThreat}

          isOpen={!!selectedThreat}

          onClose={() => setSelectedThreat(null)}

          onAction={handleUserAction}

          onDelete={handleDeleteThreat}

          isActionInProgress={actionInProgress === selectedThreat?.id}

        />

      </div>

    </UserShell>

  );

}

