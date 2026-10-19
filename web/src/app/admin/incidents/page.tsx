'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { AdminShell } from '@/components/layout/AdminShell';
import { Card, StatCard } from '@/components/ui/Card';
import { IncidentSeverityBadge, IncidentStatusBadge } from '@/components/ui/Badge';
import { LoadingSpinner, TableSkeleton } from '@/components/ui/LoadingState';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { adminApi } from '@/services/api';
import {
  Incident,
  IncidentMetrics,
  IncidentFilterParams,
  IncidentSeverity,
  IncidentStatus,
  IncidentCategory,
} from '@/types';
import {
  AlertOctagon,
  ShieldAlert,
  Search,
  Filter,
  ArrowRight,
  RefreshCw,
  Clock,
  ChevronLeft,
  ChevronRight,
  PlusCircle,
  FolderGit2,
  Timer,
  CheckCircle2,
  XCircle,
  UserCheck,
  Tag,
  X,
} from 'lucide-react';

export default function AdminIncidentsPage() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [metrics, setMetrics] = useState<IncidentMetrics | null>(null);
  const [totalCount, setTotalCount] = useState<number>(0);
  const [page, setPage] = useState<number>(1);
  const [pageSize] = useState<number>(15);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [severityFilter, setSeverityFilter] = useState<string>('ALL');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');

  // Create Modal
  const [showCreateModal, setShowCreateModal] = useState<boolean>(false);
  const [newTitle, setNewTitle] = useState<string>('');
  const [newDescription, setNewDescription] = useState<string>('');
  const [newSeverity, setNewSeverity] = useState<IncidentSeverity>('MEDIUM');
  const [newCategory, setNewCategory] = useState<IncidentCategory>('SMISHING_SURGE');
  const [newTags, setNewTags] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setError(null);
    try {
      const params: IncidentFilterParams = {
        page,
        page_size: pageSize,
      };
      if (statusFilter !== 'ALL') params.status = statusFilter;
      if (severityFilter !== 'ALL') params.severity = severityFilter;
      if (categoryFilter !== 'ALL') params.category = categoryFilter;
      if (searchTerm.trim()) params.search = searchTerm.trim();

      const [incRes, metRes] = await Promise.all([
        adminApi.incidents.list(params),
        adminApi.incidents.getMetrics(),
      ]);

      setIncidents(incRes.items || []);
      setTotalCount(incRes.total || 0);
      setTotalPages(incRes.pages || 1);
      setMetrics(metRes);
    } catch (err: any) {
      setError(err.message || 'Failed to retrieve incidents from security operations center backend.');
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [page, pageSize, statusFilter, severityFilter, categoryFilter, searchTerm]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleRefresh = () => {
    setIsRefreshing(true);
    fetchData();
  };

  const handleCreateIncident = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim()) {
      setCreateError('Incident title is required.');
      return;
    }
    setCreateError(null);
    setIsSubmitting(true);

    try {
      const tagsArray = newTags
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean);

      await adminApi.incidents.create({
        title: newTitle.trim(),
        description: newDescription.trim() || undefined,
        severity: newSeverity,
        category: newCategory,
        metadata_json: tagsArray.length > 0 ? { tags: tagsArray } : undefined,
      });

      setShowCreateModal(false);
      setNewTitle('');
      setNewDescription('');
      setNewSeverity('MEDIUM');
      setNewCategory('SMISHING_SURGE');
      setNewTags('');
      fetchData();
    } catch (err: any) {
      setCreateError(err.message || 'Failed to initialize security incident.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AdminShell>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2.5">
              <div className="p-2 rounded-lg bg-red-500/10 border border-red-500/20 text-red-400">
                <AlertOctagon className="w-5 h-5" />
              </div>
              <span>SOC Incident Investigation & Response</span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Correlated multi-threat incident investigations, risk prioritization, legal lifecycle workflows, and response tracking.
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
              onClick={() => setShowCreateModal(true)}
              className="inline-flex items-center space-x-1.5 px-3.5 py-1.5 rounded-lg bg-amber-500/15 border border-amber-500/30 text-amber-300 hover:bg-amber-500/25 text-xs font-semibold shadow-sm transition-all"
            >
              <PlusCircle className="w-4 h-4 text-amber-400" />
              <span>Create Incident</span>
            </button>
          </div>
        </div>

        {/* SOC Metrics KPI Cards */}
        {metrics && (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            <div className="bg-slate-900/80 border border-slate-800/80 rounded-xl p-3.5">
              <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Total</div>
              <div className="text-xl font-bold text-slate-100 mt-1 font-mono">{metrics.total_incidents}</div>
              <div className="text-[10px] text-slate-500 mt-0.5">Recorded incidents</div>
            </div>

            <div className="bg-slate-900/80 border border-amber-500/30 rounded-xl p-3.5">
              <div className="text-[11px] font-semibold text-amber-400 uppercase tracking-wider">Open Cases</div>
              <div className="text-xl font-bold text-amber-300 mt-1 font-mono">{metrics.open_incidents}</div>
              <div className="text-[10px] text-amber-500/80 mt-0.5">Active triage/investigation</div>
            </div>

            <div className="bg-slate-900/80 border border-red-500/30 rounded-xl p-3.5">
              <div className="text-[11px] font-semibold text-red-400 uppercase tracking-wider">Critical / High</div>
              <div className="text-xl font-bold text-red-300 mt-1 font-mono">
                {metrics.critical_incidents + metrics.high_incidents}
              </div>
              <div className="text-[10px] text-red-500/80 mt-0.5">High operational risk</div>
            </div>

            <div className="bg-slate-900/80 border border-blue-500/30 rounded-xl p-3.5">
              <div className="text-[11px] font-semibold text-blue-400 uppercase tracking-wider">Awaiting Triage</div>
              <div className="text-xl font-bold text-blue-300 mt-1 font-mono">
                {metrics.awaiting_triage_incidents}
              </div>
              <div className="text-[10px] text-blue-500/80 mt-0.5">New, unreviewed status</div>
            </div>

            <div className="bg-slate-900/80 border border-slate-800/80 rounded-xl p-3.5">
              <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Mean Triage (MTTT)</div>
              <div className="text-xl font-bold text-cyan-300 mt-1 font-mono">
                {metrics.mean_time_to_triage_hours !== null && metrics.mean_time_to_triage_hours !== undefined
                  ? `${metrics.mean_time_to_triage_hours.toFixed(1)}h`
                  : 'N/A'}
              </div>
              <div className="text-[10px] text-slate-500 mt-0.5">Time to initial triage</div>
            </div>

            <div className="bg-slate-900/80 border border-emerald-500/30 rounded-xl p-3.5">
              <div className="text-[11px] font-semibold text-emerald-400 uppercase tracking-wider">Mean Resolve (MTTR)</div>
              <div className="text-xl font-bold text-emerald-300 mt-1 font-mono">
                {metrics.mean_time_to_resolve_hours !== null && metrics.mean_time_to_resolve_hours !== undefined
                  ? `${metrics.mean_time_to_resolve_hours.toFixed(1)}h`
                  : 'N/A'}
              </div>
              <div className="text-[10px] text-emerald-500/80 mt-0.5">Time to resolution</div>
            </div>
          </div>
        )}

        {/* Filters and Search Bar */}
        <Card className="p-4 bg-slate-900/90 border-slate-800">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {/* Search */}
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Search by title, description, ID..."
                value={searchTerm}
                onChange={(e) => {
                  setSearchTerm(e.target.value);
                  setPage(1);
                }}
                className="w-full pl-9 pr-3 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-amber-500/50"
              />
            </div>

            {/* Status Filter */}
            <div className="flex items-center space-x-2">
              <span className="text-[11px] text-slate-400 whitespace-nowrap">Status:</span>
              <select
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value);
                  setPage(1);
                }}
                className="w-full py-1.5 px-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-amber-500/50"
              >
                <option value="ALL">All Statuses</option>
                <option value="NEW">NEW (Awaiting Triage)</option>
                <option value="TRIAGED">TRIAGED</option>
                <option value="INVESTIGATING">INVESTIGATING</option>
                <option value="CONTAINED">CONTAINED</option>
                <option value="RESOLVED">RESOLVED</option>
                <option value="CLOSED">CLOSED</option>
              </select>
            </div>

            {/* Severity Filter */}
            <div className="flex items-center space-x-2">
              <span className="text-[11px] text-slate-400 whitespace-nowrap">Severity:</span>
              <select
                value={severityFilter}
                onChange={(e) => {
                  setSeverityFilter(e.target.value);
                  setPage(1);
                }}
                className="w-full py-1.5 px-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-amber-500/50"
              >
                <option value="ALL">All Severities</option>
                <option value="CRITICAL">CRITICAL</option>
                <option value="HIGH">HIGH</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="LOW">LOW</option>
              </select>
            </div>

            {/* Category Filter */}
            <div className="flex items-center space-x-2">
              <span className="text-[11px] text-slate-400 whitespace-nowrap">Category:</span>
              <select
                value={categoryFilter}
                onChange={(e) => {
                  setCategoryFilter(e.target.value);
                  setPage(1);
                }}
                className="w-full py-1.5 px-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-amber-500/50"
              >
                <option value="ALL">All Categories</option>
                <option value="QUISHING_CAMPAIGN">Quishing Campaign</option>
                <option value="SMISHING_SURGE">Smishing Surge</option>
                <option value="BRAND_IMPERSONATION">Brand Impersonation</option>
                <option value="MALICIOUS_INFRASTRUCTURE">Malicious Infrastructure</option>
                <option value="ACCOUNT_TAKEOVER_ATTEMPT">Account Takeover</option>
                <option value="UNKNOWN">Unknown</option>
              </select>
            </div>
          </div>
        </Card>

        {error && (
          <ErrorState
            title="Failed to Load Security Incidents"
            message={error}
            onRetry={fetchData}
          />
        )}

        {/* Incidents Table */}
        <Card className="bg-slate-900/90 border-slate-800 overflow-hidden">
          {isLoading ? (
            <div className="p-6">
              <TableSkeleton rows={8} />
            </div>
          ) : incidents.length === 0 ? (
            <div className="p-12 text-center">
              <EmptyState
                icon={AlertOctagon}
                title="No Security Incidents Found"
                description={
                  searchTerm || statusFilter !== 'ALL' || severityFilter !== 'ALL'
                    ? 'No incidents matched your active filter criteria. Try resetting filters or search term.'
                    : 'No incidents recorded yet. Detections remain evidence until grouped into an investigation.'
                }
              />
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-slate-800 bg-slate-950/60 text-slate-400 uppercase tracking-wider text-[10px]">
                    <th className="py-3 px-4 font-semibold">Incident Identifier & Title</th>
                    <th className="py-3 px-3 font-semibold">Severity</th>
                    <th className="py-3 px-3 font-semibold">Status</th>
                    <th className="py-3 px-3 font-semibold">Category</th>
                    <th className="py-3 px-3 font-semibold">Assigned Admin</th>
                    <th className="py-3 px-3 font-semibold">Linked Threats</th>
                    <th className="py-3 px-4 font-semibold">Created / Updated</th>
                    <th className="py-3 px-4 font-semibold text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-slate-300">
                  {incidents.map((inc) => (
                    <tr
                      key={inc.id}
                      className="hover:bg-slate-800/40 transition-colors group"
                    >
                      <td className="py-3 px-4">
                        <div className="flex items-start space-x-2.5">
                          <div className="mt-0.5">
                            <AlertOctagon className="w-4 h-4 text-amber-400/80" />
                          </div>
                          <div>
                            <Link
                              href={`/admin/incidents/${inc.id}`}
                              className="font-semibold text-slate-100 hover:text-amber-300 transition-colors block"
                            >
                              {inc.title}
                            </Link>
                            <span className="font-mono text-[10px] text-slate-500 block mt-0.5">
                              ID: {inc.id.substring(0, 8)}...
                            </span>
                            {inc.metadata_json && Array.isArray(inc.metadata_json.tags) && inc.metadata_json.tags.length > 0 && (
                              <div className="flex flex-wrap gap-1 mt-1">
                                {(inc.metadata_json.tags as string[]).map((tag: string, idx: number) => (
                                  <span
                                    key={idx}
                                    className="inline-flex items-center px-1.5 py-0.2 rounded text-[9px] font-mono bg-slate-800 text-slate-400"
                                  >
                                    #{tag}
                                  </span>
                                ))}
                              </div>
                            )}
                          </div>
                        </div>
                      </td>

                      <td className="py-3 px-3 whitespace-nowrap">
                        <IncidentSeverityBadge severity={inc.severity} size="sm" />
                      </td>

                      <td className="py-3 px-3 whitespace-nowrap">
                        <IncidentStatusBadge status={inc.status} />
                      </td>

                      <td className="py-3 px-3 whitespace-nowrap">
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-mono bg-slate-800/80 text-cyan-300 border border-slate-700">
                          {inc.category}
                        </span>
                      </td>

                      <td className="py-3 px-3 whitespace-nowrap text-slate-400">
                        {inc.assigned_admin_email ? (
                          <span className="inline-flex items-center space-x-1 text-slate-300 font-mono text-[11px]">
                            <UserCheck className="w-3.5 h-3.5 text-emerald-400" />
                            <span className="truncate max-w-[130px]">{inc.assigned_admin_email}</span>
                          </span>
                        ) : (
                          <span className="text-slate-500 italic text-[11px]">Unassigned</span>
                        )}
                      </td>

                      <td className="py-3 px-3 whitespace-nowrap">
                        <span className="inline-flex items-center space-x-1 px-2 py-0.5 rounded bg-slate-800/80 text-slate-300 border border-slate-700 font-mono text-[11px]">
                          <FolderGit2 className="w-3 h-3 text-amber-400" />
                          <span>{inc.linked_threats_count ?? 0} threats</span>
                        </span>
                      </td>

                      <td className="py-3 px-4 whitespace-nowrap text-slate-400 text-[11px]">
                        <div>{new Date(inc.created_at).toLocaleDateString()}</div>
                        <div className="text-[10px] text-slate-500 font-mono">
                          {new Date(inc.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </div>
                      </td>

                      <td className="py-3 px-4 whitespace-nowrap text-right">
                        <Link
                          href={`/admin/incidents/${inc.id}`}
                          className="inline-flex items-center space-x-1 px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 hover:text-white border border-slate-700 text-[11px] font-medium transition-all"
                        >
                          <span>Investigate</span>
                          <ArrowRight className="w-3 h-3" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination Bar */}
          {!isLoading && totalPages > 1 && (
            <div className="p-3 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
              <div>
                Showing page <span className="font-semibold text-slate-200">{page}</span> of{' '}
                <span className="font-semibold text-slate-200">{totalPages}</span> ({totalCount} total)
              </div>
              <div className="flex items-center space-x-2">
                <button
                  onClick={() => setPage((p) => Math.max(p - 1, 1))}
                  disabled={page <= 1}
                  className="p-1 rounded bg-slate-800 border border-slate-700 disabled:opacity-40 disabled:cursor-not-allowed text-slate-300 hover:text-white"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setPage((p) => Math.min(p + 1, totalPages))}
                  disabled={page >= totalPages}
                  className="p-1 rounded bg-slate-800 border border-slate-700 disabled:opacity-40 disabled:cursor-not-allowed text-slate-300 hover:text-white"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}
        </Card>

        {/* Modal: Create Incident */}
        {showCreateModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in">
            <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-lg w-full p-6 shadow-2xl space-y-4">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <h3 className="text-base font-bold text-slate-100 flex items-center space-x-2">
                  <AlertOctagon className="w-5 h-5 text-amber-400" />
                  <span>Initiate Security Incident Dossier</span>
                </h3>
                <button
                  onClick={() => setShowCreateModal(false)}
                  className="text-slate-400 hover:text-slate-200"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {createError && (
                <div className="p-3 bg-red-500/10 border border-red-500/30 rounded-lg text-xs text-red-300">
                  {createError}
                </div>
              )}

              <form onSubmit={handleCreateIncident} className="space-y-4 text-xs">
                <div>
                  <label className="block text-slate-300 font-semibold mb-1">
                    Incident Title <span className="text-red-400">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Coordinated Smishing Campaign targeting Banking Credentials"
                    value={newTitle}
                    onChange={(e) => setNewTitle(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-200 focus:outline-none focus:border-amber-500/50"
                  />
                </div>

                <div>
                  <label className="block text-slate-300 font-semibold mb-1">
                    Investigation Hypothesis & Summary
                  </label>
                  <textarea
                    rows={3}
                    placeholder="Describe the initial findings, observed indicators, or affected users..."
                    value={newDescription}
                    onChange={(e) => setNewDescription(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-200 focus:outline-none focus:border-amber-500/50"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-slate-300 font-semibold mb-1">
                      Initial Severity Level
                    </label>
                    <select
                      value={newSeverity}
                      onChange={(e) => setNewSeverity(e.target.value as IncidentSeverity)}
                      className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-200 focus:outline-none focus:border-amber-500/50"
                    >
                      <option value="LOW">LOW</option>
                      <option value="MEDIUM">MEDIUM</option>
                      <option value="HIGH">HIGH</option>
                      <option value="CRITICAL">CRITICAL</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-slate-300 font-semibold mb-1">
                      Threat Category
                    </label>
                    <select
                      value={newCategory}
                      onChange={(e) => setNewCategory(e.target.value as IncidentCategory)}
                      className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-200 focus:outline-none focus:border-amber-500/50"
                    >
                      <option value="QUISHING_CAMPAIGN">Quishing Campaign</option>
                      <option value="SMISHING_SURGE">Smishing Surge</option>
                      <option value="BRAND_IMPERSONATION">Brand Impersonation</option>
                      <option value="MALICIOUS_INFRASTRUCTURE">Malicious Infrastructure</option>
                      <option value="ACCOUNT_TAKEOVER_ATTEMPT">Account Takeover</option>
                      <option value="UNKNOWN">Unknown / Other</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block text-slate-300 font-semibold mb-1">
                    Tags (Comma-separated)
                  </label>
                  <input
                    type="text"
                    placeholder="e.g. otp-bypass, fake-bank, campaign-alpha"
                    value={newTags}
                    onChange={(e) => setNewTags(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-200 focus:outline-none focus:border-amber-500/50"
                  />
                </div>

                <div className="flex items-center justify-end space-x-2 pt-3 border-t border-slate-800">
                  <button
                    type="button"
                    onClick={() => setShowCreateModal(false)}
                    className="px-3.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className="px-4 py-1.5 rounded-lg bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold shadow-md transition-colors disabled:opacity-50"
                  >
                    {isSubmitting ? 'Initializing...' : 'Create Incident'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </AdminShell>
  );
}
