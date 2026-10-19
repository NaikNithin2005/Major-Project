'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { AdminShell } from '@/components/layout/AdminShell';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingState';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { adminApi } from '@/services/api';
import {
  ThreatIndicator,
  CommunityThreatReport,
  ThreatIntelHealth,
  FeedStatusItem,
} from '@/types';
import {
  Radio,
  ShieldAlert,
  ShieldCheck,
  Search,
  Filter,
  RefreshCw,
  PlusCircle,
  CheckCircle2,
  XCircle,
  Clock,
  ExternalLink,
  ChevronLeft,
  ChevronRight,
  Database,
  Users,
  AlertTriangle,
  Lock,
  Layers,
  FileText,
  Activity,
  X,
  Share2
} from 'lucide-react';

export default function AdminThreatIntelPage() {
  const [health, setHealth] = useState<ThreatIntelHealth | null>(null);
  const [activeTab, setActiveTab] = useState<'indicators' | 'community' | 'privacy'>('indicators');

  // Indicators state
  const [indicators, setIndicators] = useState<ThreatIndicator[]>([]);
  const [indicatorsTotal, setIndicatorsTotal] = useState<number>(0);
  const [indPage, setIndPage] = useState<number>(1);
  const [indSearch, setIndSearch] = useState<string>('');
  const [indStatusFilter, setIndStatusFilter] = useState<string>('');
  const [indTypeFilter, setIndTypeFilter] = useState<string>('');

  // Community Reports state
  const [communityReports, setCommunityReports] = useState<CommunityThreatReport[]>([]);
  const [communityTotal, setCommunityTotal] = useState<number>(0);
  const [commPage, setCommPage] = useState<number>(1);
  const [commSearch, setCommSearch] = useState<string>('');
  const [commStatusFilter, setCommStatusFilter] = useState<string>('PENDING_REVIEW');

  // General UI states
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Modals
  const [showAddModal, setShowAddModal] = useState<boolean>(false);
  const [newIndicator, setNewIndicator] = useState({
    indicator_type: 'URL',
    indicator_value: '',
    threat_type: 'PHISHING',
    confidence: 0.9,
    source: 'INTERNAL',
    expires_in_days: 30,
  });

  const [lifecycleModal, setLifecycleModal] = useState<{
    isOpen: boolean;
    indicator: ThreatIndicator | null;
    targetStatus: string;
    reason: string;
  }>({
    isOpen: false,
    indicator: null,
    targetStatus: 'REVOKED',
    reason: '',
  });

  const [triageModal, setTriageModal] = useState<{
    isOpen: boolean;
    report: CommunityThreatReport | null;
    action: 'APPROVE_AND_PROMOTE' | 'REJECT' | 'MARK_DUPLICATE';
    notes: string;
    assignedConfidence: number;
    assignedThreatType: string;
    expiresInDays: number;
  }>({
    isOpen: false,
    report: null,
    action: 'APPROVE_AND_PROMOTE',
    notes: '',
    assignedConfidence: 0.85,
    assignedThreatType: 'PHISHING',
    expiresInDays: 30,
  });

  // Fetch Health Overview
  const fetchHealth = useCallback(async () => {
    try {
      const data = await adminApi.threatIntel.getHealth();
      setHealth(data);
    } catch (err: any) {
      console.error('Failed to fetch threat intel health:', err);
    }
  }, []);

  // Fetch Indicators
  const fetchIndicators = useCallback(async () => {
    try {
      const res = await adminApi.threatIntel.getIndicators({
        page: indPage,
        page_size: 20,
        search: indSearch || undefined,
        status: indStatusFilter || undefined,
        indicator_type: indTypeFilter || undefined,
      });
      setIndicators(res.items || []);
      setIndicatorsTotal(res.total || 0);
    } catch (err: any) {
      console.error('Failed to fetch indicators:', err);
      setError(err?.message || 'Failed to load threat indicators');
    }
  }, [indPage, indSearch, indStatusFilter, indTypeFilter]);

  // Fetch Community Reports
  const fetchCommunityReports = useCallback(async () => {
    try {
      const res = await adminApi.threatIntel.getCommunityReports({
        page: commPage,
        page_size: 20,
        status: commStatusFilter || undefined,
        search: commSearch || undefined,
      });
      setCommunityReports(res.items || []);
      setCommunityTotal(res.total || 0);
    } catch (err: any) {
      console.error('Failed to fetch community reports:', err);
      setError(err?.message || 'Failed to load community threat reports');
    }
  }, [commPage, commStatusFilter, commSearch]);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      await Promise.all([fetchHealth(), fetchIndicators(), fetchCommunityReports()]);
    } finally {
      setIsLoading(false);
    }
  }, [fetchHealth, fetchIndicators, fetchCommunityReports]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await loadData();
    setIsRefreshing(false);
  };

  // Add Manual Indicator Submit
  const handleAddIndicatorSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newIndicator.indicator_value.trim()) return;

    try {
      await adminApi.threatIntel.addIndicator({
        indicator_type: newIndicator.indicator_type,
        indicator_value: newIndicator.indicator_value.trim(),
        threat_type: newIndicator.threat_type,
        confidence: Number(newIndicator.confidence),
        source: newIndicator.source,
      });
      setShowAddModal(false);
      setNewIndicator({
        indicator_type: 'URL',
        indicator_value: '',
        threat_type: 'PHISHING',
        confidence: 0.9,
        source: 'INTERNAL',
        expires_in_days: 30,
      });
      setSuccessMessage('Threat indicator added successfully');
      setTimeout(() => setSuccessMessage(null), 4000);
      await Promise.all([fetchHealth(), fetchIndicators()]);
    } catch (err: any) {
      setError(err?.message || 'Failed to create indicator');
    }
  };

  // Update Lifecycle Status Submit
  const handleLifecycleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!lifecycleModal.indicator || !lifecycleModal.reason.trim()) return;

    try {
      await adminApi.threatIntel.updateIndicatorStatus(lifecycleModal.indicator.id, {
        status: lifecycleModal.targetStatus,
        reason: lifecycleModal.reason.trim(),
      });
      setLifecycleModal({ isOpen: false, indicator: null, targetStatus: 'REVOKED', reason: '' });
      setSuccessMessage(`Indicator updated to ${lifecycleModal.targetStatus}`);
      setTimeout(() => setSuccessMessage(null), 4000);
      await Promise.all([fetchHealth(), fetchIndicators()]);
    } catch (err: any) {
      setError(err?.message || 'Failed to update indicator status');
    }
  };

  // Triage Community Report Submit
  const handleTriageSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!triageModal.report) return;

    try {
      await adminApi.threatIntel.triageCommunityReport(triageModal.report.id, {
        action: triageModal.action,
        notes: triageModal.notes.trim() || undefined,
        assigned_confidence: triageModal.assignedConfidence,
        assigned_threat_type: triageModal.assignedThreatType,
        expires_in_days: triageModal.expiresInDays,
      });
      setTriageModal({
        isOpen: false,
        report: null,
        action: 'APPROVE_AND_PROMOTE',
        notes: '',
        assignedConfidence: 0.85,
        assignedThreatType: 'PHISHING',
        expiresInDays: 30,
      });
      setSuccessMessage(`Report processed: ${triageModal.action}`);
      setTimeout(() => setSuccessMessage(null), 4000);
      await Promise.all([fetchHealth(), fetchIndicators(), fetchCommunityReports()]);
    } catch (err: any) {
      setError(err?.message || 'Failed to triage community report');
    }
  };

  return (
    <AdminShell>
      <div className="space-y-6">
        {/* Page Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2.5">
              <div className="p-2 rounded-lg bg-cyan-500/10 border border-cyan-500/20 text-cyan-400">
                <Radio className="w-5 h-5" />
              </div>
              <span>Threat Intelligence & Collaborative Sharing</span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Federated threat feed management, privacy-preserving community triage, and indicator lifecycles.
            </p>
          </div>

          <div className="flex items-center space-x-2.5">
            <button
              onClick={handleRefresh}
              disabled={isRefreshing || isLoading}
              className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-medium text-slate-300 hover:text-white hover:bg-slate-800 transition-colors disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin text-cyan-400' : ''}`} />
              <span>{isRefreshing ? 'Refreshing...' : 'Refresh'}</span>
            </button>
            <button
              onClick={() => setShowAddModal(true)}
              className="inline-flex items-center space-x-1.5 px-3.5 py-1.5 rounded-lg bg-cyan-500/15 border border-cyan-500/30 text-cyan-300 hover:bg-cyan-500/25 text-xs font-semibold shadow-sm transition-all"
            >
              <PlusCircle className="w-4 h-4 text-cyan-400" />
              <span>Add Indicator</span>
            </button>
          </div>
        </div>
        {/* Banner Messages */}
        {successMessage && (
          <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-sm flex items-center justify-between">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
              <span>{successMessage}</span>
            </div>
            <button onClick={() => setSuccessMessage(null)} className="text-emerald-400 hover:text-emerald-300">
              <X className="w-4 h-4" />
            </button>
          </div>
        )}

        {error && (
          <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-sm flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AlertTriangle className="w-5 h-5 flex-shrink-0" />
              <span>{error}</span>
            </div>
            <button onClick={() => setError(null)} className="text-rose-400 hover:text-rose-300">
              <X className="w-4 h-4" />
            </button>
          </div>
        )}

        {/* Top Operational Strip: Feeds & Synchronizations */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <Card className="p-5 border-slate-800 bg-slate-900/60 backdrop-blur-sm">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">Feed Health</span>
              <span
                className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                  health?.status === 'HEALTHY'
                    ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                    : 'bg-amber-500/10 text-amber-400 border border-amber-500/30'
                }`}
              >
                <Activity className="w-3.5 h-3.5 animate-pulse" />
                {health?.status || 'HEALTHY'}
              </span>
            </div>
            <div className="mt-3">
              <div className="text-2xl font-bold text-white">{health?.active_indicators || 0}</div>
              <p className="text-xs text-slate-400 mt-1">Active verified indicators across feeds</p>
            </div>
          </Card>

          <Card className="p-5 border-slate-800 bg-slate-900/60 backdrop-blur-sm">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">Indicators Total</span>
              <Database className="w-4 h-4 text-cyan-400" />
            </div>
            <div className="mt-3">
              <div className="text-2xl font-bold text-white">{health?.total_verified_indicators || 0}</div>
              <div className="flex items-center gap-2 mt-1 text-xs text-slate-400">
                <span className="text-rose-400">{health?.revoked_indicators || 0} revoked</span>
                <span>•</span>
                <span className="text-amber-400">{health?.expired_indicators || 0} expired</span>
              </div>
            </div>
          </Card>

          <Card className="p-5 border-slate-800 bg-slate-900/60 backdrop-blur-sm">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">Community Reports</span>
              <Share2 className="w-4 h-4 text-indigo-400" />
            </div>
            <div className="mt-3">
              <div className="text-2xl font-bold text-white">{health?.pending_community_reports || 0}</div>
              <div className="flex items-center gap-2 mt-1 text-xs text-slate-400">
                <span className="text-amber-400 font-medium">Pending Triage</span>
                <span>•</span>
                <span className="text-emerald-400">{health?.approved_community_reports || 0} promoted</span>
              </div>
            </div>
          </Card>

          <Card className="p-5 border-slate-800 bg-slate-900/60 backdrop-blur-sm">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">Privacy & Consent</span>
              <Lock className="w-4 h-4 text-emerald-400" />
            </div>
            <div className="mt-3">
              <div className="text-sm font-semibold text-emerald-400 flex items-center gap-1.5">
                <ShieldCheck className="w-4 h-4" /> Zero-PII Enforced
              </div>
              <p className="text-xs text-slate-400 mt-1">Raw SMS bodies filtered at device edge</p>
            </div>
          </Card>
        </div>

        {/* Registered Intelligence Feed Sources */}
        {health?.feeds && health.feeds.length > 0 && (
          <div className="bg-slate-900/40 rounded-xl p-4 border border-slate-800/80">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-2">
                <Radio className="w-3.5 h-3.5 text-cyan-400" /> Configured External Feeds & Sources
              </h3>
              <button
                onClick={handleRefresh}
                disabled={isRefreshing}
                className="text-xs text-slate-400 hover:text-white flex items-center gap-1.5 transition-colors"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin' : ''}`} />
                Refresh
              </button>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
              {health.feeds.map((feed) => (
                <div
                  key={feed.feed_name}
                  className="p-3 rounded-lg bg-slate-950/60 border border-slate-800 flex flex-col justify-between"
                >
                  <div>
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-slate-200">{feed.feed_name}</span>
                      <span
                        className={`w-2 h-2 rounded-full ${feed.is_active ? 'bg-emerald-400 ring-2 ring-emerald-400/20' : 'bg-slate-600'}`}
                      />
                    </div>
                    <p className="text-[11px] text-slate-400 mt-1 truncate">{feed.provider_type}</p>
                  </div>
                  <div className="mt-2 pt-2 border-t border-slate-800/60 flex items-center justify-between text-[11px]">
                    <span className="text-slate-400">{feed.indicator_count} indicators</span>
                    <span className="text-slate-400 truncate max-w-[100px]">{feed.terms.split(' ')[0]}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Tab Navigation */}
        <div className="flex border-b border-slate-800 gap-6">
          <button
            onClick={() => setActiveTab('indicators')}
            className={`pb-3 text-sm font-semibold flex items-center gap-2 border-b-2 transition-all ${
              activeTab === 'indicators'
                ? 'border-cyan-500 text-cyan-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Database className="w-4 h-4" />
            Verified Threat Indicators ({indicatorsTotal})
          </button>
          <button
            onClick={() => setActiveTab('community')}
            className={`pb-3 text-sm font-semibold flex items-center gap-2 border-b-2 transition-all ${
              activeTab === 'community'
                ? 'border-cyan-500 text-cyan-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Share2 className="w-4 h-4" />
            Community Triage Queue ({communityTotal})
          </button>
          <button
            onClick={() => setActiveTab('privacy')}
            className={`pb-3 text-sm font-semibold flex items-center gap-2 border-b-2 transition-all ${
              activeTab === 'privacy'
                ? 'border-cyan-500 text-cyan-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Lock className="w-4 h-4" />
            Privacy & Data Governance
          </button>
        </div>

        {/* TAB 1: VERIFIED THREAT INDICATORS */}
        {activeTab === 'indicators' && (
          <div className="space-y-4">
            {/* Filter and Action Bar */}
            <div className="flex flex-col md:flex-row gap-3 justify-between items-stretch md:items-center">
              <div className="flex flex-wrap items-center gap-2 flex-1">
                <div className="relative flex-1 min-w-[200px] max-w-md">
                  <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                  <input
                    type="text"
                    placeholder="Search by URL, domain or hash..."
                    value={indSearch}
                    onChange={(e) => setIndSearch(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-800 rounded-lg pl-9 pr-4 py-2 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-cyan-500"
                  />
                </div>

                <select
                  value={indTypeFilter}
                  onChange={(e) => setIndTypeFilter(e.target.value)}
                  className="bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-300 focus:outline-none focus:border-cyan-500"
                >
                  <option value="">All Types</option>
                  <option value="URL">URL</option>
                  <option value="DOMAIN">Domain</option>
                  <option value="IP">IP</option>
                  <option value="CAMPAIGN">Campaign</option>
                </select>

                <select
                  value={indStatusFilter}
                  onChange={(e) => setIndStatusFilter(e.target.value)}
                  className="bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-300 focus:outline-none focus:border-cyan-500"
                >
                  <option value="">All Statuses</option>
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="VERIFIED">VERIFIED</option>
                  <option value="EXPIRED">EXPIRED</option>
                  <option value="REVOKED">REVOKED</option>
                </select>
              </div>

              <button
                onClick={() => setShowAddModal(true)}
                className="px-4 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white font-medium text-sm flex items-center justify-center gap-2 transition-colors shadow-lg shadow-cyan-600/20"
              >
                <PlusCircle className="w-4 h-4" />
                Add Indicator
              </button>
            </div>

            {/* Indicators Table */}
            <Card className="border-slate-800 bg-slate-900/60 overflow-hidden">
              {isLoading ? (
                <div className="p-12 flex justify-center">
                  <LoadingSpinner />
                </div>
              ) : indicators.length === 0 ? (
                <div className="p-12 text-center">
                  <EmptyState
                    title="No threat indicators found"
                    description="No indicators match the current filters. Adjust your search parameters or ingest feeds."
                  />
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm text-slate-300">
                    <thead className="bg-slate-950/80 text-xs uppercase font-semibold text-slate-400 border-b border-slate-800">
                      <tr>
                        <th className="px-4 py-3">Indicator Value</th>
                        <th className="px-4 py-3">Type & Category</th>
                        <th className="px-4 py-3">Confidence</th>
                        <th className="px-4 py-3">Source</th>
                        <th className="px-4 py-3">Status</th>
                        <th className="px-4 py-3">Last Seen</th>
                        <th className="px-4 py-3 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60">
                      {indicators.map((ind) => (
                        <tr key={ind.id} className="hover:bg-slate-800/30 transition-colors">
                          <td className="px-4 py-3 font-mono text-xs max-w-xs truncate text-cyan-300">
                            {ind.indicator_value}
                          </td>
                          <td className="px-4 py-3">
                            <span className="inline-block px-2 py-0.5 rounded text-[11px] font-semibold bg-slate-800 text-slate-300 mr-1.5">
                              {ind.indicator_type}
                            </span>
                            <span className="text-xs font-medium text-slate-400">{ind.threat_type}</span>
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-2">
                              <div className="w-16 bg-slate-800 rounded-full h-1.5 overflow-hidden">
                                <div
                                  className="bg-cyan-400 h-full rounded-full"
                                  style={{ width: `${Math.round(ind.confidence * 100)}%` }}
                                />
                              </div>
                              <span className="text-xs font-mono text-slate-400">
                                {Math.round(ind.confidence * 100)}%
                              </span>
                            </div>
                          </td>
                          <td className="px-4 py-3 text-xs">
                            <span
                              className={`px-2 py-0.5 rounded text-[11px] font-semibold ${
                                ind.source === 'COMMUNITY'
                                  ? 'bg-indigo-500/10 text-indigo-400 border border-indigo-500/30'
                                  : 'bg-slate-800 text-slate-300'
                              }`}
                            >
                              {ind.source}
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            <span
                              className={`inline-block px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                                ind.status === 'ACTIVE' || ind.verification_status === 'VERIFIED'
                                  ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                                  : ind.status === 'REVOKED' || ind.verification_status === 'REVOKED'
                                  ? 'bg-rose-500/10 text-rose-400 border border-rose-500/30'
                                  : 'bg-amber-500/10 text-amber-400 border border-amber-500/30'
                              }`}
                            >
                              {ind.verification_status || ind.status}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-xs text-slate-400">
                            {new Date(ind.last_seen).toLocaleDateString()}
                          </td>
                          <td className="px-4 py-3 text-right">
                            <div className="flex items-center justify-end gap-1.5">
                              {ind.status !== 'REVOKED' && ind.verification_status !== 'REVOKED' ? (
                                <button
                                  onClick={() =>
                                    setLifecycleModal({
                                      isOpen: true,
                                      indicator: ind,
                                      targetStatus: 'REVOKED',
                                      reason: '',
                                    })
                                  }
                                  className="px-2 py-1 rounded text-xs font-medium bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 transition-colors"
                                >
                                  Revoke
                                </button>
                              ) : (
                                <button
                                  onClick={() =>
                                    setLifecycleModal({
                                      isOpen: true,
                                      indicator: ind,
                                      targetStatus: 'VERIFIED',
                                      reason: 'Re-verified upon SOC investigation',
                                    })
                                  }
                                  className="px-2 py-1 rounded text-xs font-medium bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 transition-colors"
                                >
                                  Re-verify
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {/* Pagination */}
              {indicatorsTotal > 20 && (
                <div className="p-4 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
                  <div>
                    Showing {(indPage - 1) * 20 + 1} to {Math.min(indPage * 20, indicatorsTotal)} of {indicatorsTotal}
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setIndPage((p) => Math.max(1, p - 1))}
                      disabled={indPage === 1}
                      className="p-1.5 rounded bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-200"
                    >
                      <ChevronLeft className="w-4 h-4" />
                    </button>
                    <span>Page {indPage}</span>
                    <button
                      onClick={() => setIndPage((p) => p + 1)}
                      disabled={indPage * 20 >= indicatorsTotal}
                      className="p-1.5 rounded bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-200"
                    >
                      <ChevronRight className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              )}
            </Card>
          </div>
        )}

        {/* TAB 2: COMMUNITY TRIAGE QUEUE */}
        {activeTab === 'community' && (
          <div className="space-y-4">
            {/* Filter Bar */}
            <div className="flex flex-col md:flex-row gap-3 justify-between items-stretch md:items-center">
              <div className="flex flex-wrap items-center gap-2 flex-1">
                <div className="relative flex-1 min-w-[200px] max-w-md">
                  <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                  <input
                    type="text"
                    placeholder="Search community submissions..."
                    value={commSearch}
                    onChange={(e) => setCommSearch(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-800 rounded-lg pl-9 pr-4 py-2 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-cyan-500"
                  />
                </div>

                <select
                  value={commStatusFilter}
                  onChange={(e) => setCommStatusFilter(e.target.value)}
                  className="bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-300 focus:outline-none focus:border-cyan-500"
                >
                  <option value="">All Submissions</option>
                  <option value="PENDING_REVIEW">Pending Review</option>
                  <option value="PROMOTED">Promoted (Approved)</option>
                  <option value="REJECTED">Rejected</option>
                </select>
              </div>
            </div>

            {/* Community Reports Table */}
            <Card className="border-slate-800 bg-slate-900/60 overflow-hidden">
              {isLoading ? (
                <div className="p-12 flex justify-center">
                  <LoadingSpinner />
                </div>
              ) : communityReports.length === 0 ? (
                <div className="p-12 text-center">
                  <EmptyState
                    title="No community reports in queue"
                    description="No opt-in threat submissions require review right now."
                  />
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm text-slate-300">
                    <thead className="bg-slate-950/80 text-xs uppercase font-semibold text-slate-400 border-b border-slate-800">
                      <tr>
                        <th className="px-4 py-3">Anonymous ID</th>
                        <th className="px-4 py-3">Sanitized Indicator</th>
                        <th className="px-4 py-3">Threat Category</th>
                        <th className="px-4 py-3">Consensus Score</th>
                        <th className="px-4 py-3">Corroboration</th>
                        <th className="px-4 py-3">Status</th>
                        <th className="px-4 py-3 text-right">Triage Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60">
                      {communityReports.map((rep) => (
                        <tr key={rep.id} className="hover:bg-slate-800/30 transition-colors">
                          <td className="px-4 py-3 font-mono text-xs text-slate-400">
                            {rep.anonymous_submission_id.substring(0, 8)}...
                          </td>
                          <td className="px-4 py-3 font-mono text-xs max-w-xs truncate text-indigo-300">
                            {rep.indicator_value}
                          </td>
                          <td className="px-4 py-3">
                            <span className="inline-block px-2 py-0.5 rounded text-[11px] font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                              {rep.threat_category}
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-2">
                              <span className="text-xs font-mono font-bold text-white">
                                {Math.round(rep.consensus_score * 100)}%
                              </span>
                            </div>
                          </td>
                          <td className="px-4 py-3 text-xs">
                            <span className="inline-flex items-center gap-1 text-slate-300">
                              <Users className="w-3.5 h-3.5 text-cyan-400" />
                              {rep.corroboration_count} user(s)
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            <span
                              className={`inline-block px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                                rep.status === 'PROMOTED'
                                  ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                                  : rep.status === 'REJECTED'
                                  ? 'bg-rose-500/10 text-rose-400 border border-rose-500/30'
                                  : 'bg-amber-500/10 text-amber-400 border border-amber-500/30'
                              }`}
                            >
                              {rep.status}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-right">
                            {rep.status === 'PENDING_REVIEW' ? (
                              <div className="flex items-center justify-end gap-1.5">
                                <button
                                  onClick={() =>
                                    setTriageModal({
                                      isOpen: true,
                                      report: rep,
                                      action: 'APPROVE_AND_PROMOTE',
                                      notes: '',
                                      assignedConfidence: Math.max(0.8, rep.consensus_score),
                                      assignedThreatType: rep.threat_category,
                                      expiresInDays: 30,
                                    })
                                  }
                                  className="px-2.5 py-1 rounded text-xs font-medium bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 transition-colors"
                                >
                                  Approve & Promote
                                </button>
                                <button
                                  onClick={() =>
                                    setTriageModal({
                                      isOpen: true,
                                      report: rep,
                                      action: 'REJECT',
                                      notes: 'Insufficient threat evidence',
                                      assignedConfidence: 0.8,
                                      assignedThreatType: rep.threat_category,
                                      expiresInDays: 30,
                                    })
                                  }
                                  className="px-2.5 py-1 rounded text-xs font-medium bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 transition-colors"
                                >
                                  Reject
                                </button>
                              </div>
                            ) : (
                              <span className="text-xs text-slate-500">Processed</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
          </div>
        )}

        {/* TAB 3: PRIVACY & DATA GOVERNANCE */}
        {activeTab === 'privacy' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Card className="p-6 border-slate-800 bg-slate-900/60 space-y-4">
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <Lock className="w-5 h-5 text-emerald-400" />
                Zero-PII Smishing/Quishing Guard
              </h3>
              <p className="text-sm text-slate-300 leading-relaxed">
                Under Phase 18, edge devices run TinyBERT and XGBoost locally. Raw SMS message text never leaves the user’s
                device. Only normalized destination URLs, cryptographic SHA-256 canonical fingerprints, and local inference
                risk scores are voluntarily transmitted with explicit opt-in consent.
              </p>
              <div className="p-4 rounded-lg bg-slate-950/80 border border-slate-800 space-y-2 text-xs font-mono text-slate-400">
                <div className="text-emerald-400 font-semibold">{'// Client Normalization Protocol'}</div>
                <div>1. URL parsed & Punycode decoded (RFC 3986)</div>
                <div>2. Sensitive tokens (auth, token, pwd, session) stripped</div>
                <div>3. SSRF / RFC 1918 private loopback ranges blocked</div>
                <div>4. Deterministic SHA-256 fingerprint generated</div>
              </div>
            </Card>

            <Card className="p-6 border-slate-800 bg-slate-900/60 space-y-4">
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <ShieldCheck className="w-5 h-5 text-cyan-400" />
                Consensus & Anti-Poisoning Architecture
              </h3>
              <p className="text-sm text-slate-300 leading-relaxed">
                An unverified user submission is <strong>never</strong> treated as confirmed malicious intelligence. Single reports
                remain in pending status. Multi-user corroboration within a 72-hour sliding window dynamically elevates consensus
                scores, triggering SOC review for promotion into the authoritative threat feed.
              </p>
              <div className="p-4 rounded-lg bg-slate-950/80 border border-slate-800 space-y-2 text-xs font-mono text-slate-400">
                <div className="text-cyan-400 font-semibold">{'// Collaborative Lifecycle States'}</div>
                <div>• PENDING_REVIEW: Under evaluation with dynamic consensus</div>
                <div>• VERIFIED: Promoted to authoritative feed by SOC analyst</div>
                <div>• EXPIRED: Auto-deactivated after TTL window (default 30d)</div>
                <div>• REVOKED: Revoked with mandatory audit reasoning</div>
              </div>
            </Card>
          </div>
        )}
      </div>

      {/* MODAL: ADD MANUAL INDICATOR */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-lg w-full p-6 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h3 className="text-lg font-bold text-white flex items-center gap-2">
                <PlusCircle className="w-5 h-5 text-cyan-400" /> Add Verified Threat Indicator
              </h3>
              <button onClick={() => setShowAddModal(false)} className="text-slate-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleAddIndicatorSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                  Indicator Type
                </label>
                <select
                  value={newIndicator.indicator_type}
                  onChange={(e) => setNewIndicator({ ...newIndicator, indicator_type: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-cyan-500"
                >
                  <option value="URL">URL</option>
                  <option value="DOMAIN">Domain</option>
                  <option value="IP">IP Address</option>
                  <option value="CAMPAIGN">Campaign Identifier</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                  Indicator Value
                </label>
                <input
                  type="text"
                  required
                  placeholder="https://evil-phish.example.com/login"
                  value={newIndicator.indicator_value}
                  onChange={(e) => setNewIndicator({ ...newIndicator, indicator_value: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-cyan-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                    Threat Type
                  </label>
                  <select
                    value={newIndicator.threat_type}
                    onChange={(e) => setNewIndicator({ ...newIndicator, threat_type: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-cyan-500"
                  >
                    <option value="PHISHING">Phishing</option>
                    <option value="QUISHING">Quishing (QR Phish)</option>
                    <option value="SMISHING">Smishing (SMS Phish)</option>
                    <option value="MALWARE">Malware</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                    Confidence ({Math.round(newIndicator.confidence * 100)}%)
                  </label>
                  <input
                    type="range"
                    min="0.5"
                    max="1.0"
                    step="0.05"
                    value={newIndicator.confidence}
                    onChange={(e) => setNewIndicator({ ...newIndicator, confidence: parseFloat(e.target.value) })}
                    className="w-full mt-2 accent-cyan-500"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-4 py-2 rounded-lg bg-slate-800 text-slate-300 text-sm font-medium hover:bg-slate-700"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-sm font-medium shadow-lg shadow-cyan-600/20"
                >
                  Save Indicator
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: LIFECYCLE STATUS (REVOKE / EXPIRE / RE-VERIFY) */}
      {lifecycleModal.isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-md w-full p-6 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h3 className="text-lg font-bold text-white flex items-center gap-2">
                <AlertTriangle className="w-5 h-5 text-amber-400" /> Update Indicator Lifecycle
              </h3>
              <button
                onClick={() => setLifecycleModal({ ...lifecycleModal, isOpen: false })}
                className="text-slate-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleLifecycleSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                  Target Status
                </label>
                <select
                  value={lifecycleModal.targetStatus}
                  onChange={(e) => setLifecycleModal({ ...lifecycleModal, targetStatus: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-cyan-500"
                >
                  <option value="REVOKED">REVOKED (Dispute / False Positive)</option>
                  <option value="EXPIRED">EXPIRED (Threat Inactive)</option>
                  <option value="VERIFIED">VERIFIED (Active Malicious)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                  Mandatory Audit Reason
                </label>
                <textarea
                  required
                  rows={3}
                  placeholder="Explain why this status transition is being applied for the audit log..."
                  value={lifecycleModal.reason}
                  onChange={(e) => setLifecycleModal({ ...lifecycleModal, reason: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-cyan-500"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setLifecycleModal({ ...lifecycleModal, isOpen: false })}
                  className="px-4 py-2 rounded-lg bg-slate-800 text-slate-300 text-sm font-medium hover:bg-slate-700"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-sm font-medium"
                >
                  Confirm Transition
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: TRIAGE COMMUNITY REPORT */}
      {triageModal.isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-lg w-full p-6 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h3 className="text-lg font-bold text-white flex items-center gap-2">
                <Share2 className="w-5 h-5 text-indigo-400" /> Triage Community Report
              </h3>
              <button
                onClick={() => setTriageModal({ ...triageModal, isOpen: false })}
                className="text-slate-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleTriageSubmit} className="space-y-4">
              <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 space-y-1 text-xs">
                <div className="text-slate-400 font-semibold">Reported Target:</div>
                <div className="font-mono text-cyan-400 break-all">{triageModal.report?.indicator_value}</div>
                <div className="flex items-center gap-3 pt-2 text-slate-400">
                  <span>Corroboration: {triageModal.report?.corroboration_count} users</span>
                  <span>•</span>
                  <span>Consensus: {Math.round((triageModal.report?.consensus_score || 0) * 100)}%</span>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                  Triage Decision
                </label>
                <select
                  value={triageModal.action}
                  onChange={(e) => setTriageModal({ ...triageModal, action: e.target.value as any })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-cyan-500"
                >
                  <option value="APPROVE_AND_PROMOTE">Approve & Promote to Authoritative Feed</option>
                  <option value="REJECT">Reject (False Alarm / Inconclusive)</option>
                  <option value="MARK_DUPLICATE">Mark Duplicate</option>
                </select>
              </div>

              {triageModal.action === 'APPROVE_AND_PROMOTE' && (
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                      Threat Type
                    </label>
                    <select
                      value={triageModal.assignedThreatType}
                      onChange={(e) => setTriageModal({ ...triageModal, assignedThreatType: e.target.value })}
                      className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-cyan-500"
                    >
                      <option value="PHISHING">Phishing</option>
                      <option value="QUISHING">Quishing (QR)</option>
                      <option value="SMISHING">Smishing (SMS)</option>
                      <option value="MALWARE">Malware</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                      Assigned Confidence
                    </label>
                    <input
                      type="number"
                      step="0.05"
                      min="0.5"
                      max="1.0"
                      value={triageModal.assignedConfidence}
                      onChange={(e) => setTriageModal({ ...triageModal, assignedConfidence: parseFloat(e.target.value) })}
                      className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-cyan-500"
                    />
                  </div>
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                  Administrative Notes
                </label>
                <textarea
                  rows={2}
                  placeholder="Optional analyst notes..."
                  value={triageModal.notes}
                  onChange={(e) => setTriageModal({ ...triageModal, notes: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-cyan-500"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setTriageModal({ ...triageModal, isOpen: false })}
                  className="px-4 py-2 rounded-lg bg-slate-800 text-slate-300 text-sm font-medium hover:bg-slate-700"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium"
                >
                  Execute Triage
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </AdminShell>
  );
}
