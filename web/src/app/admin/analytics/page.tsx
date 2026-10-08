'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { AdminShell } from '@/components/layout/AdminShell';
import { Card, StatCard } from '@/components/ui/Card';
import { ThreatTrendChart } from '@/components/charts/ThreatTrendChart';
import { CategoryDoughnut } from '@/components/charts/CategoryDoughnut';
import { RiskDistributionBar } from '@/components/charts/RiskDistributionBar';
import { LoadingSpinner } from '@/components/ui/LoadingState';
import { ErrorState } from '@/components/ui/ErrorState';
import { api } from '@/services/api';
import { AnalyticsSummary, ThreatTrendsResponse } from '@/types';
import { BarChart3, RefreshCw, TrendingUp, ShieldAlert, Cpu } from 'lucide-react';

export default function AdminAnalyticsPage() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [trends, setTrends] = useState<ThreatTrendsResponse | null>(null);
  const [days, setDays] = useState<number>(7);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAnalytics = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [sumRes, trendRes] = await Promise.all([
        api.analytics.getSummary(),
        api.analytics.getTrends(days),
      ]);
      setSummary(sumRes);
      setTrends(trendRes);
    } catch (err: any) {
      setError(err.message || 'Failed to aggregate security analytics.');
    } finally {
      setIsLoading(false);
    }
  }, [days]);

  useEffect(() => {
    fetchAnalytics();
  }, [fetchAnalytics]);

  const total = summary ? summary.total_detections : 0;
  const smsPct = total > 0 && summary ? Math.round((summary.sms_count / total) * 100) : 0;
  const qrPct = total > 0 && summary ? Math.round((summary.qr_count / total) * 100) : 0;

  const safePct = total > 0 && summary ? Math.round((summary.safe_count / total) * 100) : 0;
  const lowPct = total > 0 && summary ? Math.round((summary.low_count / total) * 100) : 0;
  const medPct = total > 0 && summary ? Math.round((summary.medium_count / total) * 100) : 0;
  const highPct = total > 0 && summary ? Math.round((summary.high_count / total) * 100) : 0;

  return (
    <AdminShell>
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2">
              <BarChart3 className="w-6 h-6 text-amber-400" />
              <span>Advanced Threat Analytics & Heuristics</span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Time-series velocity curves, channel infection distributions, and risk score frequency bands across the entire user base.
            </p>
          </div>

          <div className="flex items-center space-x-3">
            <div className="flex rounded-lg bg-slate-900 border border-slate-800 p-0.5 text-xs font-semibold">
              {[7, 14, 30].map((d) => (
                <button
                  key={d}
                  onClick={() => setDays(d)}
                  className={`px-3 py-1.5 rounded-md transition-all ${
                    days === d
                      ? 'bg-amber-500/20 text-amber-300 font-bold shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {d}D
                </button>
              ))}
            </div>

            <button
              onClick={() => fetchAnalytics()}
              disabled={isLoading}
              className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors disabled:opacity-50"
              title="Refresh"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-amber-400' : ''}`} />
            </button>
          </div>
        </div>

        {error && (
          <ErrorState
            title="Analytics Retrieval Error"
            message={error}
            onRetry={fetchAnalytics}
          />
        )}

        {isLoading ? (
          <div className="py-20 flex justify-center">
            <LoadingSpinner message="Calculating time-series trends & risk distributions..." />
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <StatCard
                title="Analyzed Events"
                value={total}
                icon={BarChart3}
                description={`Over the past ${days} days`}
              />
              <StatCard
                title="Average Threat Score"
                value={`${Math.round(summary?.average_risk_score ?? 0)}/100`}
                icon={ShieldAlert}
                description="Cross-vector composite"
                accentColor="text-amber-400"
              />
              <StatCard
                title="Smishing Attack Vector"
                value={`${smsPct}%`}
                icon={TrendingUp}
                description={`${summary?.sms_count ?? 0} SMS events`}
                accentColor="text-blue-400"
              />
              <StatCard
                title="Quishing Attack Vector"
                value={`${qrPct}%`}
                icon={Cpu}
                description={`${summary?.qr_count ?? 0} QR events`}
                accentColor="text-cyan-400"
              />
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              <div className="lg:col-span-2">
                <Card
                  title={`Threat Velocity (${days}-Day Trajectory)`}
                  subtitle="Moving daily detection frequency correlated with average event risk"
                >
                  <ThreatTrendChart dataPoints={trends?.data_points || []} />
                </Card>
              </div>

              <div>
                <Card
                  title="Severity Partition"
                  subtitle="Ratio of benign vs critical indicators"
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

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <Card
                title="Risk Score Dispersion"
                subtitle="Frequency breakdown across 0-100 risk score quartiles"
              >
                <RiskDistributionBar
                  safeCount={summary?.safe_count || 0}
                  lowCount={summary?.low_count || 0}
                  mediumCount={summary?.medium_count || 0}
                  highCount={summary?.high_count || 0}
                />
              </Card>

              <Card
                title="Detection Vector Breakdown"
                subtitle="Telemetry channels transmitting AI assessments"
              >
                <div className="space-y-4 pt-2">
                  <div>
                    <div className="flex justify-between text-xs mb-1.5 font-medium">
                      <span className="text-slate-300">SMS Smishing Detections</span>
                      <span className="text-slate-400 font-mono">{summary?.sms_count ?? 0} ({smsPct}%)</span>
                    </div>
                    <div className="w-full h-2 rounded-full bg-slate-950 overflow-hidden border border-slate-800">
                      <div className="h-full bg-blue-500 rounded-full transition-all duration-500" style={{ width: `${smsPct}%` }} />
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-xs mb-1.5 font-medium">
                      <span className="text-slate-300">QR Quishing Detections</span>
                      <span className="text-slate-400 font-mono">{summary?.qr_count ?? 0} ({qrPct}%)</span>
                    </div>
                    <div className="w-full h-2 rounded-full bg-slate-950 overflow-hidden border border-slate-800">
                      <div className="h-full bg-cyan-500 rounded-full transition-all duration-500" style={{ width: `${qrPct}%` }} />
                    </div>
                  </div>

                  <div className="pt-4 border-t border-slate-800/80 grid grid-cols-2 gap-3 text-center">
                    <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800">
                      <div className="text-[11px] text-slate-400">Total False Positives</div>
                      <div className="text-base font-bold text-slate-200 mt-0.5">
                        {summary?.false_positive_count ?? 0}
                      </div>
                    </div>
                    <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800">
                      <div className="text-[11px] text-slate-400">Active Operators</div>
                      <div className="text-base font-bold text-slate-200 mt-0.5">
                        {summary?.active_users ?? 0}
                      </div>
                    </div>
                  </div>
                </div>
              </Card>
            </div>
          </>
        )}
      </div>
    </AdminShell>
  );
}
