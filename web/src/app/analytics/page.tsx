'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { DashboardShell } from '@/components/layout/DashboardShell';
import { Card, StatCard } from '@/components/ui/Card';
import { ThreatTrendChart } from '@/components/charts/ThreatTrendChart';
import { CategoryDoughnut } from '@/components/charts/CategoryDoughnut';
import { RiskDistributionBar } from '@/components/charts/RiskDistributionBar';
import { LoadingSpinner } from '@/components/ui/LoadingState';
import { ErrorState } from '@/components/ui/ErrorState';
import { api } from '@/services/api';
import { AnalyticsSummary, ThreatTrendsResponse } from '@/types';
import { BarChart3, RefreshCw, TrendingUp, ShieldAlert, Cpu } from 'lucide-react';

export default function AnalyticsPage() {
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

  // Derived Statistics
  const total = summary ? summary.total_detections : 0;
  const smsPct = total > 0 && summary ? Math.round((summary.sms_count / total) * 100) : 0;
  const qrPct = total > 0 && summary ? Math.round((summary.qr_count / total) * 100) : 0;

  const safePct = total > 0 && summary ? Math.round((summary.safe_count / total) * 100) : 0;
  const lowPct = total > 0 && summary ? Math.round((summary.low_count / total) * 100) : 0;
  const medPct = total > 0 && summary ? Math.round((summary.medium_count / total) * 100) : 0;
  const highPct = total > 0 && summary ? Math.round((summary.high_count / total) * 100) : 0;

  return (
    <DashboardShell>
      <div className="space-y-6">
        {/* Header with Lookback Window Control */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2">
              <BarChart3 className="w-6 h-6 text-cyan-400" />
              <span>Threat Intelligence Analytics</span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Statistical aggregation, trend modeling, and vector distribution across registered mobile endpoints.
            </p>
          </div>

          <div className="flex items-center space-x-2">
            <span className="text-xs text-slate-400 font-semibold mr-1">Time Horizon:</span>
            {[7, 14, 30].map((d) => (
              <button
                key={d}
                onClick={() => setDays(d)}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  days === d
                    ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 shadow-sm'
                    : 'bg-slate-900 text-slate-400 hover:text-slate-200 border border-slate-800'
                }`}
              >
                {d} Days
              </button>
            ))}
            <button
              onClick={fetchAnalytics}
              disabled={isLoading}
              className="p-1.5 bg-slate-900 hover:bg-slate-800 text-slate-400 rounded-lg border border-slate-800 ml-2"
              title="Refresh Analytics"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-cyan-400' : ''}`} />
            </button>
          </div>
        </div>

        {error && (
          <ErrorState
            title="Analytics Engine Error"
            message={error}
            onRetry={fetchAnalytics}
          />
        )}

        {isLoading ? (
          <LoadingSpinner message="Calculating real-time telemetry distributions..." />
        ) : summary ? (
          <>
            {/* Top Level Metric Summary */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <StatCard
                label="Mean Risk Score"
                value={`${summary.average_risk_score} / 100`}
                subtext="System-wide normalized average"
                icon={<TrendingUp className="w-5 h-5 text-cyan-400" />}
              />
              <StatCard
                label="High-Risk Ratio"
                value={`${highPct}%`}
                subtext={`${summary.high_count} critical detections`}
                icon={<ShieldAlert className="w-5 h-5 text-rose-400" />}
              />
              <StatCard
                label="Dominant Threat Vector"
                value={summary.sms_count >= summary.qr_count ? 'SMS Smishing' : 'QR Quishing'}
                subtext={`${Math.max(smsPct, qrPct)}% of total telemetry`}
                icon={<Cpu className="w-5 h-5 text-purple-400" />}
              />
              <StatCard
                label="Feedback Accuracy"
                value={
                  summary.total_feedback_count > 0
                    ? `${Math.round(((summary.total_feedback_count - summary.false_positive_count) / summary.total_feedback_count) * 100)}%`
                    : '100%'
                }
                subtext={`${summary.false_positive_count} false positives reviewed`}
                icon={<TrendingUp className="w-5 h-5 text-emerald-400" />}
              />
            </div>

            {/* Time-Series Trend Chart */}
            <Card
              title={`Temporal Threat Trends (${days} Days)`}
              subtitle="Daily count of detected threats and high-severity occurrences"
            >
              <ThreatTrendChart dataPoints={trends?.data_points || []} />
            </Card>

            {/* Breakdown Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Threat Vector Distribution with Textual Summary */}
              <Card
                title="Threat Vector Proportion"
                subtitle="Comparative volume of SMS vs QR payload attacks"
              >
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-center">
                  <CategoryDoughnut smsCount={summary.sms_count} qrCount={summary.qr_count} />

                  <div className="space-y-4 p-4 rounded-xl bg-slate-950/40 border border-slate-800">
                    <h4 className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
                      Textual Vector Summary
                    </h4>
                    <div className="space-y-2 text-xs">
                      <div className="flex justify-between items-center pb-2 border-b border-slate-800">
                        <span className="text-slate-400">Smishing (SMS):</span>
                        <span className="font-bold text-cyan-400 font-mono">
                          {summary.sms_count} ({smsPct}%)
                        </span>
                      </div>
                      <div className="flex justify-between items-center pb-2 border-b border-slate-800">
                        <span className="text-slate-400">Quishing (QR):</span>
                        <span className="font-bold text-purple-400 font-mono">
                          {summary.qr_count} ({qrPct}%)
                        </span>
                      </div>
                      <div className="flex justify-between items-center pt-1 font-semibold">
                        <span className="text-slate-300">Total Analyzed:</span>
                        <span className="font-mono text-slate-100">{total}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </Card>

              {/* Severity Category Distribution with Textual Breakdown */}
              <Card
                title="Risk Category Breakdown"
                subtitle="Distribution across Safe, Low, Medium, and High risk buckets"
              >
                <div className="space-y-4">
                  <RiskDistributionBar
                    safe={summary.safe_count}
                    low={summary.low_count}
                    medium={summary.medium_count}
                    high={summary.high_count}
                  />

                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-2">
                    <div className="p-2.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-center">
                      <p className="text-[10px] uppercase font-semibold text-emerald-400">Safe</p>
                      <p className="text-base font-bold font-mono text-emerald-200 mt-0.5">{summary.safe_count}</p>
                      <p className="text-[10px] text-emerald-400/80">{safePct}%</p>
                    </div>

                    <div className="p-2.5 rounded-lg bg-sky-500/10 border border-sky-500/20 text-center">
                      <p className="text-[10px] uppercase font-semibold text-sky-400">Low</p>
                      <p className="text-base font-bold font-mono text-sky-200 mt-0.5">{summary.low_count}</p>
                      <p className="text-[10px] text-sky-400/80">{lowPct}%</p>
                    </div>

                    <div className="p-2.5 rounded-lg bg-amber-500/10 border border-amber-500/20 text-center">
                      <p className="text-[10px] uppercase font-semibold text-amber-400">Medium</p>
                      <p className="text-base font-bold font-mono text-amber-200 mt-0.5">{summary.medium_count}</p>
                      <p className="text-[10px] text-amber-400/80">{medPct}%</p>
                    </div>

                    <div className="p-2.5 rounded-lg bg-rose-500/10 border border-rose-500/20 text-center">
                      <p className="text-[10px] uppercase font-semibold text-rose-400">High</p>
                      <p className="text-base font-bold font-mono text-rose-200 mt-0.5">{summary.high_count}</p>
                      <p className="text-[10px] text-rose-400/80">{highPct}%</p>
                    </div>
                  </div>
                </div>
              </Card>
            </div>
          </>
        ) : null}
      </div>
    </DashboardShell>
  );
}
