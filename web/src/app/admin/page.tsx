'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { AdminShell } from '@/components/layout/AdminShell';
import { StatCard, Card } from '@/components/ui/Card';
import { RiskBadge, EventTypeBadge, StatusBadge } from '@/components/ui/Badge';
import { ThreatTrendChart } from '@/components/charts/ThreatTrendChart';
import { CategoryDoughnut } from '@/components/charts/CategoryDoughnut';
import { RiskDistributionBar } from '@/components/charts/RiskDistributionBar';
import { LoadingSpinner } from '@/components/ui/LoadingState';
import { ErrorState } from '@/components/ui/ErrorState';
import { api } from '@/services/api';
import { AnalyticsSummary, ThreatTrendsResponse, ThreatEvent } from '@/types';
import {
  ShieldAlert,
  Users,
  Smartphone,
  QrCode,
  Flame,
  Activity,
  ArrowRight,
  RefreshCw,
  Clock,
  ShieldCheck,
} from 'lucide-react';

export default function AdminOverviewDashboardPage() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [trends, setTrends] = useState<ThreatTrendsResponse | null>(null);
  const [recentThreats, setRecentThreats] = useState<ThreatEvent[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);

  const fetchData = useCallback(async () => {
    setError(null);
    try {
      const [sumRes, trendRes, threatsRes] = await Promise.all([
        api.analytics.getSummary(),
        api.analytics.getTrends(7),
        api.threats.list({ page: 1, page_size: 5 }),
      ]);
      setSummary(sumRes);
      setTrends(trendRes);
      setRecentThreats(threatsRes.items || []);
    } catch (err: any) {
      setError(err.message || 'Unable to retrieve telemetry overview from backend.');
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleRefresh = () => {
    setIsRefreshing(true);
    fetchData();
  };

  return (
    <AdminShell>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold text-slate-100 tracking-tight">
              Administrative Security Command
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Global system monitoring, multi-user telemetry, and AI detection pipeline health
            </p>
          </div>
          <div className="flex items-center space-x-2">
            <button
              onClick={handleRefresh}
              disabled={isRefreshing || isLoading}
              className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-medium text-slate-300 hover:text-white hover:bg-slate-800 transition-colors disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin text-cyan-400' : ''}`} />
              <span>{isRefreshing ? 'Updating...' : 'Refresh'}</span>
            </button>
          </div>
        </div>

        {error && (
          <ErrorState
            title="Failed to Load System Telemetry"
            message={error}
            onRetry={fetchData}
          />
        )}

        {isLoading ? (
          <div className="py-20 flex justify-center">
            <LoadingSpinner message="Aggregating multi-user security metrics..." />
          </div>
        ) : (
          <>
            {/* Stat Cards Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <StatCard
                title="Total Telemetry Events"
                value={summary?.total_detections ?? 0}
                icon={Activity}
                description="Cumulative QR & SMS scans"
                change={{ value: '+12% from yesterday', positive: true }}
              />
              <StatCard
                title="High Severity Threats"
                value={summary?.high_count ?? 0}
                icon={Flame}
                description="Malicious Quishing & Smishing"
                accentColor="text-rose-400"
              />
              <StatCard
                title="Registered Users"
                value={summary?.total_users ?? 0}
                icon={Users}
                description={`${summary?.active_users ?? 0} active in last 30d`}
              />
              <StatCard
                title="System Average Risk"
                value={`${Math.round(summary?.average_risk_score ?? 0)}/100`}
                icon={ShieldAlert}
                description="Overall heuristic posture"
                accentColor={
                  (summary?.average_risk_score ?? 0) > 60
                    ? 'text-rose-400'
                    : (summary?.average_risk_score ?? 0) > 30
                    ? 'text-amber-400'
                    : 'text-emerald-400'
                }
              />
            </div>

            {/* Channels & Verification Sub-metrics */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              <div className="bg-slate-900/60 border border-slate-800/80 rounded-xl p-4 flex items-center space-x-3">
                <div className="p-2.5 rounded-lg bg-cyan-500/10 border border-cyan-500/20 text-cyan-400">
                  <QrCode className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-lg font-bold text-slate-100">{summary?.qr_count ?? 0}</div>
                  <div className="text-[11px] text-slate-400">QR Detections</div>
                </div>
              </div>

              <div className="bg-slate-900/60 border border-slate-800/80 rounded-xl p-4 flex items-center space-x-3">
                <div className="p-2.5 rounded-lg bg-blue-500/10 border border-blue-500/20 text-blue-400">
                  <Smartphone className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-lg font-bold text-slate-100">{summary?.sms_count ?? 0}</div>
                  <div className="text-[11px] text-slate-400">SMS Detections</div>
                </div>
              </div>

              <div className="bg-slate-900/60 border border-slate-800/80 rounded-xl p-4 flex items-center space-x-3">
                <div className="p-2.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400">
                  <ShieldCheck className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-lg font-bold text-slate-100">{summary?.safe_count ?? 0}</div>
                  <div className="text-[11px] text-slate-400">Safe Detections</div>
                </div>
              </div>

              <div className="bg-slate-900/60 border border-slate-800/80 rounded-xl p-4 flex items-center space-x-3">
                <div className="p-2.5 rounded-lg bg-purple-500/10 border border-purple-500/20 text-purple-400">
                  <Activity className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-lg font-bold text-slate-100">{summary?.total_feedback_count ?? 0}</div>
                  <div className="text-[11px] text-slate-400">User Feedbacks</div>
                </div>
              </div>
            </div>

            {/* Charts Row */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              <div className="lg:col-span-2">
                <Card
                  title="Global Detection Velocity (7 Days)"
                  subtitle="Volume and risk trends aggregated across all registered devices"
                >
                  <ThreatTrendChart dataPoints={trends?.data_points || []} />
                </Card>
              </div>

              <div>
                <Card
                  title="Threat Severity Mix"
                  subtitle="Distribution by category"
                >
                  <CategoryDoughnut
                    distribution={
                      trends?.category_distribution || {
                        SAFE: summary?.safe_count || 0,
                        LOW: summary?.low_count || 0,
                        MEDIUM: summary?.medium_count || 0,
                        HIGH: summary?.high_count || 0,
                      }
                    }
                  />
                </Card>
              </div>
            </div>

            {/* Recent Global Threats & Triage Table */}
            <Card
              title="Recent Global Threats"
              subtitle="Latest high-priority quishing and smishing signals awaiting operator triage"
              action={
                <Link
                  href="/admin/threats"
                  className="inline-flex items-center space-x-1 text-xs font-semibold text-cyan-400 hover:text-cyan-300 transition-colors"
                >
                  <span>Open Triage Queue</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </Link>
              }
            >
              {recentThreats.length === 0 ? (
                <div className="py-8 text-center text-xs text-slate-500">
                  No threat events detected in the system yet.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-slate-950/60 text-slate-400 uppercase text-[10px] tracking-wider border-b border-slate-800">
                      <tr>
                        <th className="py-2.5 px-3">Event ID</th>
                        <th className="py-2.5 px-3">Channel</th>
                        <th className="py-2.5 px-3">Category</th>
                        <th className="py-2.5 px-3">Risk Score</th>
                        <th className="py-2.5 px-3">Recommended Policy</th>
                        <th className="py-2.5 px-3">Status</th>
                        <th className="py-2.5 px-3">Detected</th>
                        <th className="py-2.5 px-3 text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60">
                      {recentThreats.map((threat) => (
                        <tr key={threat.id} className="hover:bg-slate-800/40 transition-colors">
                          <td className="py-3 px-3 font-mono text-[11px] text-slate-300">
                            {threat.id.substring(0, 8)}...
                          </td>
                          <td className="py-3 px-3">
                            <EventTypeBadge type={threat.event_type} />
                          </td>
                          <td className="py-3 px-3">
                            <RiskBadge category={threat.threat_category} score={threat.risk_score} />
                          </td>
                          <td className="py-3 px-3 font-mono font-bold text-slate-200">
                            {threat.risk_score} / 100
                          </td>
                          <td className="py-3 px-3 text-slate-300">
                            {threat.recommended_action}
                          </td>
                          <td className="py-3 px-3">
                            <StatusBadge status={threat.status} />
                          </td>
                          <td className="py-3 px-3 text-slate-400 text-[11px]">
                            {new Date(threat.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </td>
                          <td className="py-3 px-3 text-right">
                            <Link
                              href="/admin/threats"
                              className="text-cyan-400 hover:text-cyan-300 font-semibold text-xs"
                            >
                              Triage
                            </Link>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
          </>
        )}
      </div>
    </AdminShell>
  );
}
