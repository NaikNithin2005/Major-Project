'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { UserShell } from '@/components/layout/UserShell';
import { Card } from '@/components/ui/Card';
import { RiskBadge, EventTypeBadge, StatusBadge } from '@/components/ui/Badge';
import { LoadingSpinner } from '@/components/ui/LoadingState';
import { ErrorState } from '@/components/ui/ErrorState';
import { api } from '@/services/api';
import { ThreatEvent, UserStats } from '@/types';
import { useAuth } from '@/context/AuthContext';
import {
  Shield,
  ShieldAlert,
  ShieldCheck,
  QrCode,
  Smartphone,
  AlertTriangle,
  ArrowRight,
  RefreshCw,
  Clock,
  CheckCircle2,
  XCircle,
  FileSearch,
  ExternalLink,
  Ban,
  Activity,
  Sparkles,
  Radio,
  Wifi,
} from 'lucide-react';
import {
  subscribeToUserThreats,
  takeUserThreatAction,
  calculateUserStatsFromThreats,
} from '@/services/threatFirestore';

export default function UserDashboardPage() {
  const { user } = useAuth();
  const [stats, setStats] = useState<UserStats | null>(null);
  const [recentThreats, setRecentThreats] = useState<ThreatEvent[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);
  const [isLiveConnected, setIsLiveConnected] = useState<boolean>(false);

  // Real-time Firestore subscription scoped to authenticated user UID
  useEffect(() => {
    if (!user?.firebase_uid) {
      setStats(null);
      setRecentThreats([]);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);

    // Initial fallback from REST API to ensure immediate data if Firestore index is provisioning
    api.userThreats.getStats().then(setStats).catch(() => {});
    api.userThreats.list({ page: 1, page_size: 5 }).then((res) => {
      if (res.items && res.items.length > 0) {
        setRecentThreats(res.items);
      }
    }).catch(() => {});

    // Establish real-time Firestore listener on users/{uid}/threats
    const unsubscribe = subscribeToUserThreats(
      user.firebase_uid,
      (liveThreats) => {
        setRecentThreats(liveThreats.slice(0, 5));
        const liveStats = calculateUserStatsFromThreats(liveThreats);
        setStats(liveStats);
        setIsLiveConnected(true);
        setIsLoading(false);
        setIsRefreshing(false);
      },
      (err) => {
        console.warn('Real-time Firestore stream fallback notice:', err.message);
        setIsLiveConnected(false);
        setIsLoading(false);
        setIsRefreshing(false);
      }
    );

    return () => {
      unsubscribe();
      setIsLiveConnected(false);
    };
  }, [user?.firebase_uid]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    try {
      const [statsRes, threatsRes] = await Promise.all([
        api.userThreats.getStats(),
        api.userThreats.list({ page: 1, page_size: 5 }),
      ]);
      setStats(statsRes);
      setRecentThreats(threatsRes.items || []);
    } catch (err: any) {
      console.warn('Manual refresh notice:', err);
    } finally {
      setIsRefreshing(false);
    }
  };

  const handleUserAction = async (threatId: string, action: 'BLOCK' | 'MARK_SAFE') => {
    setActionInProgress(threatId);
    try {
      // 1. Update Firestore in real time if user UID is available
      if (user?.firebase_uid) {
        await takeUserThreatAction(user.firebase_uid, threatId, action);
      }
      // 2. Also notify backend API
      try {
        await api.userThreats.takeAction(threatId, action);
      } catch {}

      setRecentThreats((prev) =>
        prev.map((t) => (t.id === threatId ? { ...t, status: action === 'BLOCK' ? 'BLOCKED' : 'RESOLVED', user_action: action } : t))
      );
    } catch (err: any) {
      alert(`Action failed: ${err.message}`);
    } finally {
      setActionInProgress(null);
    }
  };

  // Derive protection health score
  const totalScans = stats?.total_scans ?? 0;
  const highThreats = stats?.high_risk_count ?? 0;
  const blockedThreats = stats?.threats_blocked ?? 0;

  let healthStatus = 'Optimal Protection';
  let healthColor = 'text-emerald-400';
  let healthScore = 98;

  if (highThreats > 0 && highThreats > blockedThreats) {
    healthStatus = 'Action Required';
    healthColor = 'text-rose-400';
    healthScore = Math.max(45, 100 - (highThreats - blockedThreats) * 20);
  } else if ((stats?.medium_risk_count ?? 0) > 0) {
    healthStatus = 'Moderate Watch';
    healthColor = 'text-amber-400';
    healthScore = 85;
  }

  return (
    <UserShell>
      <div className="space-y-6">
        {/* Welcome Banner & System Status */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-slate-900/80 border border-slate-800 rounded-xl p-5 sm:p-6 shadow-xl backdrop-blur-sm">
          <div className="flex items-start space-x-4">
            <div className="w-12 h-12 rounded-xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400 shrink-0">
              <ShieldCheck className="w-7 h-7" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h1 className="text-lg sm:text-xl font-bold text-slate-100">
                  Welcome back, {user?.display_name || user?.email?.split('@')[0]}
                </h1>
                <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  Active Shield
                </span>
                {isLiveConnected ? (
                  <span className="inline-flex items-center space-x-1.5 px-2 py-0.5 rounded text-[10px] font-semibold bg-cyan-500/10 text-cyan-400 border border-cyan-500/30">
                    <span className="relative flex h-2 w-2">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75"></span>
                      <span className="relative inline-flex rounded-full h-2 w-2 bg-cyan-500"></span>
                    </span>
                    <span>Live Telemetry</span>
                  </span>
                ) : (
                  <span className="inline-flex items-center space-x-1 px-2 py-0.5 rounded text-[10px] font-semibold bg-slate-800 text-slate-400 border border-slate-700">
                    <span>Synchronized</span>
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-400 mt-1 max-w-xl">
                Your mobile device and web account are protected by Squish Shield offline-first AI detection. All scans and threat verdicts synchronize automatically.
              </p>
            </div>
          </div>

          <div className="flex items-center space-x-3 self-start md:self-auto">
            <button
              onClick={handleRefresh}
              disabled={isRefreshing || isLoading}
              className="inline-flex items-center space-x-1.5 px-3 py-2 rounded-lg bg-slate-800/80 hover:bg-slate-800 border border-slate-700/80 text-xs font-semibold text-slate-200 transition-colors disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin text-cyan-400' : ''}`} />
              <span>{isRefreshing ? 'Syncing...' : 'Sync Telemetry'}</span>
            </button>
            <Link
              href="/threats"
              className="inline-flex items-center space-x-1.5 px-3.5 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/25 transition-all"
            >
              <span>Threat History</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </div>

        {error && (
          <ErrorState
            title="Failed to Load Protection Overview"
            message={error}
            onRetry={handleRefresh}
          />
        )}

        {isLoading ? (
          <div className="py-20 flex justify-center">
            <LoadingSpinner message="Fetching your personalized security telemetry..." />
          </div>
        ) : (
          <>
            {/* Personal Telemetry Counters */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {/* Protection Health Score */}
              <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-5 shadow-lg relative overflow-hidden">
                <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
                  <span>Protection Rating</span>
                  <Activity className="w-4 h-4 text-cyan-400" />
                </div>
                <div className="flex items-baseline space-x-2">
                  <span className={`text-3xl font-extrabold font-mono ${healthColor}`}>
                    {healthScore}
                  </span>
                  <span className="text-xs text-slate-500 font-mono">/ 100</span>
                </div>
                <div className="mt-2 text-xs font-semibold flex items-center space-x-1.5 text-slate-300">
                  <span className={`w-2 h-2 rounded-full ${healthColor.replace('text-', 'bg-')}`}></span>
                  <span>{healthStatus}</span>
                </div>
              </div>

              {/* Total Scans Processed */}
              <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-5 shadow-lg">
                <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
                  <span>Total Scans Analyzed</span>
                  <FileSearch className="w-4 h-4 text-slate-400" />
                </div>
                <div className="text-3xl font-extrabold font-mono text-slate-100">
                  {stats?.total_scans ?? 0}
                </div>
                <div className="mt-2 text-[11px] text-slate-400 flex items-center space-x-2">
                  <span className="text-cyan-400 font-mono">{stats?.recent_qr_count ?? 0} QR</span>
                  <span>•</span>
                  <span className="text-blue-400 font-mono">{stats?.recent_sms_count ?? 0} SMS</span>
                </div>
              </div>

              {/* Threats Intercepted */}
              <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-5 shadow-lg">
                <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
                  <span>Threats Intercepted</span>
                  <ShieldAlert className="w-4 h-4 text-rose-400" />
                </div>
                <div className="text-3xl font-extrabold font-mono text-rose-400">
                  {stats?.threats_detected ?? 0}
                </div>
                <div className="mt-2 text-[11px] text-slate-400">
                  <span className="text-emerald-400 font-semibold">{stats?.threats_blocked ?? 0}</span> confirmed blocked
                </div>
              </div>

              {/* Safe Verifications */}
              <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-5 shadow-lg">
                <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
                  <span>Safe Verifications</span>
                  <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                </div>
                <div className="text-3xl font-extrabold font-mono text-emerald-400">
                  {stats?.safe_detections ?? 0}
                </div>
                <div className="mt-2 text-[11px] text-slate-400">
                  Verified benign links & messages
                </div>
              </div>
            </div>

            {/* Quick Actions & Security Recommendations */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              {/* Recent Detections Feed (Col Span 2) */}
              <div className="lg:col-span-2">
                <Card
                  title="Recent Threat Detections"
                  subtitle="Latest telemetry events analyzed by your on-device models"
                  action={
                    <Link
                      href="/threats"
                      className="text-xs font-semibold text-cyan-400 hover:text-cyan-300 inline-flex items-center space-x-1"
                    >
                      <span>View All Threats</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </Link>
                  }
                >
                  {recentThreats.length === 0 ? (
                    <div className="py-12 text-center">
                      <div className="w-10 h-10 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center mx-auto mb-3">
                        <CheckCircle2 className="w-5 h-5" />
                      </div>
                      <h4 className="text-sm font-semibold text-slate-200">No Threats Detected</h4>
                      <p className="text-xs text-slate-400 max-w-sm mx-auto mt-1">
                        Your device hasn&apos;t encountered any suspicious QR codes or smishing messages recently.
                      </p>
                    </div>
                  ) : (
                    <div className="divide-y divide-slate-800/60">
                      {recentThreats.map((threat) => {
                        const isActing = actionInProgress === threat.id;
                        const domain = threat.url_metadata?.domain || threat.qr_metadata?.scheme;
                        const topReason = threat.reasons && threat.reasons.length > 0 ? threat.reasons[0] : null;

                        return (
                          <div
                            key={threat.id}
                            className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 hover:bg-slate-800/30 px-3 -mx-3 rounded-lg transition-colors"
                          >
                            <div className="flex items-start space-x-3 min-w-0">
                              <div className="pt-0.5 shrink-0">
                                <EventTypeBadge type={threat.event_type} />
                              </div>
                              <div className="min-w-0">
                                <div className="flex items-center space-x-2">
                                  <RiskBadge category={threat.threat_category} score={threat.risk_score} />
                                  <span className="font-mono text-xs font-bold text-slate-200">
                                    Risk {threat.risk_score}/100
                                  </span>
                                  {threat.user_action && (
                                    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-cyan-500/15 text-cyan-300 border border-cyan-500/30">
                                      {threat.user_action}
                                    </span>
                                  )}
                                </div>
                                {domain && (
                                  <div className="font-mono text-xs text-cyan-400 font-semibold truncate mt-1">
                                    {domain}
                                  </div>
                                )}
                                {topReason && (
                                  <div className="text-xs text-slate-400 truncate mt-0.5">
                                    {topReason}
                                  </div>
                                )}
                                <div className="text-[10px] text-slate-500 mt-1 flex items-center space-x-2">
                                  <Clock className="w-3 h-3" />
                                  <span>
                                    {new Date(threat.timestamp).toLocaleDateString([], {
                                      month: 'short',
                                      day: 'numeric',
                                    })}{' '}
                                    at{' '}
                                    {new Date(threat.timestamp).toLocaleTimeString([], {
                                      hour: '2-digit',
                                      minute: '2-digit',
                                    })}
                                  </span>
                                </div>
                              </div>
                            </div>

                            {/* User Action Buttons */}
                            <div className="flex items-center space-x-2 shrink-0 self-end sm:self-center">
                              {threat.threat_category !== 'SAFE' && threat.user_action !== 'BLOCK' && (
                                <button
                                  onClick={() => handleUserAction(threat.id, 'BLOCK')}
                                  disabled={isActing}
                                  className="px-2.5 py-1 rounded bg-rose-500/15 hover:bg-rose-500/25 text-rose-300 text-xs font-semibold border border-rose-500/30 transition-all disabled:opacity-50"
                                >
                                  Block
                                </button>
                              )}
                              {threat.user_action !== 'MARK_SAFE' && threat.status !== 'RESOLVED' && (
                                <button
                                  onClick={() => handleUserAction(threat.id, 'MARK_SAFE')}
                                  disabled={isActing}
                                  className="px-2.5 py-1 rounded bg-emerald-500/15 hover:bg-emerald-500/25 text-emerald-300 text-xs font-semibold border border-emerald-500/30 transition-all disabled:opacity-50"
                                >
                                  Mark Safe
                                </button>
                              )}
                              <Link
                                href={`/threats/${threat.id}`}
                                className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium border border-slate-700 transition-all"
                              >
                                Forensics
                              </Link>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </Card>
              </div>

              {/* Security Recommendations & Heuristics */}
              <div className="space-y-6">
                <Card title="Security Recommendations" subtitle="Actionable hygiene from your threat telemetry">
                  <div className="space-y-3">
                    <div className="p-3 rounded-lg bg-slate-950/60 border border-slate-800 flex items-start space-x-3">
                      <div className="p-2 rounded bg-amber-500/10 text-amber-400 shrink-0">
                        <QrCode className="w-4 h-4" />
                      </div>
                      <div>
                        <div className="text-xs font-semibold text-slate-200">
                          Verify Physical QR Codes
                        </div>
                        <p className="text-[11px] text-slate-400 mt-0.5 leading-relaxed">
                          Attackers frequently overlay stickers with malicious URLs onto authentic parking or restaurant payment QR codes.
                        </p>
                      </div>
                    </div>

                    <div className="p-3 rounded-lg bg-slate-950/60 border border-slate-800 flex items-start space-x-3">
                      <div className="p-2 rounded bg-blue-500/10 text-blue-400 shrink-0">
                        <Smartphone className="w-4 h-4" />
                      </div>
                      <div>
                        <div className="text-xs font-semibold text-slate-200">
                          Urgent SMS Demands
                        </div>
                        <p className="text-[11px] text-slate-400 mt-0.5 leading-relaxed">
                          TinyBERT flags messages threatening account suspension or immediate tax payment. Banks never demand password verification over SMS.
                        </p>
                      </div>
                    </div>

                    <div className="p-3 rounded-lg bg-slate-950/60 border border-slate-800 flex items-start space-x-3">
                      <div className="p-2 rounded bg-cyan-500/10 text-cyan-400 shrink-0">
                        <Shield className="w-4 h-4" />
                      </div>
                      <div>
                        <div className="text-xs font-semibold text-slate-200">
                          Multi-Device Synchronization
                        </div>
                        <p className="text-[11px] text-slate-400 mt-0.5 leading-relaxed">
                          When you block an indicator here, your decision is tied to your account across mobile and web.
                        </p>
                      </div>
                    </div>
                  </div>
                </Card>

                {/* Account Details Box */}
                <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-4">
                  <div className="text-xs font-semibold text-slate-300 mb-2">Account Isolation</div>
                  <div className="text-[11px] text-slate-400 space-y-1">
                    <div className="flex justify-between">
                      <span>Owner UID:</span>
                      <span className="font-mono text-cyan-400">{user?.firebase_uid.substring(0, 12)}...</span>
                    </div>
                    <div className="flex justify-between">
                      <span>Sync Partition:</span>
                      <span className="text-emerald-400 font-medium">Private Tenant</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </>
        )}
      </div>
    </UserShell>
  );
}
