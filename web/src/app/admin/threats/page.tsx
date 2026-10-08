'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { AdminShell } from '@/components/layout/AdminShell';
import { Card } from '@/components/ui/Card';
import { RiskBadge, EventTypeBadge, StatusBadge } from '@/components/ui/Badge';
import { LoadingSpinner, TableSkeleton } from '@/components/ui/LoadingState';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { api } from '@/services/api';
import { ThreatEvent, PaginatedResponse, ThreatFilterParams } from '@/types';
import {
  ShieldAlert,
  Search,
  Filter,
  ArrowRight,
  RefreshCw,
  Clock,
  ChevronLeft,
  ChevronRight,
  CheckCircle2,
  XCircle,
  FileSearch,
} from 'lucide-react';

export default function AdminThreatTriagePage() {
  const [threats, setThreats] = useState<ThreatEvent[]>([]);
  const [totalCount, setTotalCount] = useState<number>(0);
  const [page, setPage] = useState<number>(1);
  const [pageSize] = useState<number>(15);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [eventTypeFilter, setEventTypeFilter] = useState<string>('ALL');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);

  const fetchThreats = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const params: ThreatFilterParams = {
        page,
        page_size: pageSize,
      };
      if (eventTypeFilter !== 'ALL') params.event_type = eventTypeFilter;
      if (categoryFilter !== 'ALL') params.threat_category = categoryFilter;
      if (statusFilter !== 'ALL') params.status = statusFilter;
      if (searchTerm.trim()) params.search = searchTerm.trim();

      const res = await api.threats.list(params);
      setThreats(res.items || []);
      setTotalCount(res.total || 0);
      setTotalPages(res.pages || 1);
    } catch (err: any) {
      setError(err.message || 'Failed to retrieve telemetry events.');
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize, eventTypeFilter, categoryFilter, statusFilter, searchTerm]);

  useEffect(() => {
    fetchThreats();
  }, [fetchThreats]);

  const handleQuickStatusChange = async (threatId: string, newStatus: string) => {
    setActionInProgress(threatId);
    try {
      const updated = await api.threats.updateStatus(threatId, newStatus);
      setThreats((prev) =>
        prev.map((t) => (t.id === threatId ? { ...t, status: updated.status } : t))
      );
    } catch (err: any) {
      alert(`Action failed: ${err.message}`);
    } finally {
      setActionInProgress(null);
    }
  };

  return (
    <AdminShell>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2">
              <ShieldAlert className="w-6 h-6 text-rose-400" />
              <span>Global Threat Monitoring & Triage</span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Global telemetry stream of smishing and quishing events across all users detected by on-device AI.
            </p>
          </div>

          <button
            onClick={() => fetchThreats()}
            disabled={isLoading}
            className="inline-flex items-center space-x-2 px-3 py-2 bg-slate-900 hover:bg-slate-800 text-slate-300 text-xs font-semibold rounded-lg border border-slate-700/60 shadow-sm transition-all"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-cyan-400' : ''}`} />
            <span>Refresh Stream</span>
          </button>
        </div>

        {/* Filter Controls Bar */}
        <Card className="p-4 bg-slate-900/90 border-slate-800">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {/* Search Input */}
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-500">
                <Search className="w-3.5 h-3.5" />
              </div>
              <input
                type="text"
                placeholder="Search threat ID, URL, reason..."
                value={searchTerm}
                onChange={(e) => {
                  setSearchTerm(e.target.value);
                  setPage(1);
                }}
                className="w-full pl-9 pr-3 py-2 bg-slate-950/70 border border-slate-800 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-amber-500/50"
              />
            </div>

            {/* Threat Vector Filter */}
            <div>
              <select
                value={eventTypeFilter}
                onChange={(e) => {
                  setEventTypeFilter(e.target.value);
                  setPage(1);
                }}
                className="w-full px-3 py-2 bg-slate-950/70 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-amber-500/50 font-sans"
              >
                <option value="ALL">All Vectors (SMS & QR)</option>
                <option value="SMS">SMS Only (Smishing)</option>
                <option value="QR">QR Only (Quishing)</option>
              </select>
            </div>

            {/* Severity Category Filter */}
            <div>
              <select
                value={categoryFilter}
                onChange={(e) => {
                  setCategoryFilter(e.target.value);
                  setPage(1);
                }}
                className="w-full px-3 py-2 bg-slate-950/70 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-amber-500/50 font-sans"
              >
                <option value="ALL">All Categories</option>
                <option value="HIGH">High Severity (Malicious)</option>
                <option value="MEDIUM">Medium Severity (Suspicious)</option>
                <option value="LOW">Low Severity (Uncertain)</option>
                <option value="SAFE">Safe (Benign)</option>
              </select>
            </div>

            {/* Status Filter */}
            <div>
              <select
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value);
                  setPage(1);
                }}
                className="w-full px-3 py-2 bg-slate-950/70 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-amber-500/50 font-sans"
              >
                <option value="ALL">All Statuses</option>
                <option value="NEW">New</option>
                <option value="REPORTED">Reported</option>
                <option value="REVIEWED">Under Review</option>
                <option value="RESOLVED">Resolved</option>
                <option value="FALSE_POSITIVE">False Positive</option>
                <option value="BLOCKED">Blocked</option>
              </select>
            </div>
          </div>
        </Card>

        {/* Threats Data Grid */}
        <Card className="overflow-hidden border-slate-800 bg-slate-900/90">
          {error && (
            <div className="p-6">
              <ErrorState
                title="Error Loading Threat Stream"
                message={error}
                onRetry={fetchThreats}
              />
            </div>
          )}

          {isLoading ? (
            <TableSkeleton rows={8} cols={7} />
          ) : threats.length === 0 ? (
            <EmptyState
              icon={FileSearch}
              title="No Telemetry Events Found"
              description="No quishing or smishing events match your current filter criteria."
              actionLabel="Clear Filters"
              onAction={() => {
                setEventTypeFilter('ALL');
                setCategoryFilter('ALL');
                setStatusFilter('ALL');
                setSearchTerm('');
                setPage(1);
              }}
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-800 bg-slate-950/50 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                    <th className="py-3 px-4">Event ID / Client Key</th>
                    <th className="py-3 px-4">User ID</th>
                    <th className="py-3 px-4">Vector</th>
                    <th className="py-3 px-4">Risk & Score</th>
                    <th className="py-3 px-4">AI Telemetry Evidence</th>
                    <th className="py-3 px-4">Status</th>
                    <th className="py-3 px-4">Timestamp</th>
                    <th className="py-3 px-4 text-right">Quick Triage</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-xs">
                  {threats.map((threat) => {
                    const isActing = actionInProgress === threat.id;
                    const domain = threat.url_metadata?.domain || threat.qr_metadata?.scheme;
                    const topReason = threat.reasons && threat.reasons.length > 0 ? threat.reasons[0] : null;

                    return (
                      <tr
                        key={threat.id}
                        className="hover:bg-slate-800/40 transition-colors group"
                      >
                        {/* Event ID */}
                        <td className="py-3.5 px-4 font-mono text-slate-300">
                          <div className="font-semibold text-xs text-slate-200">
                            {threat.id.substring(0, 10)}...
                          </div>
                          {threat.client_event_id && (
                            <div className="text-[10px] text-slate-500 truncate max-w-[120px]">
                              {threat.client_event_id}
                            </div>
                          )}
                        </td>

                        {/* User ID */}
                        <td className="py-3.5 px-4 font-mono text-[11px] text-slate-400">
                          {threat.user_id ? (
                            <span className="truncate max-w-[100px] block" title={threat.user_id}>
                              {threat.user_id.substring(0, 8)}...
                            </span>
                          ) : (
                            <span className="text-slate-600">Anonymous</span>
                          )}
                        </td>

                        {/* Vector */}
                        <td className="py-3.5 px-4">
                          <EventTypeBadge type={threat.event_type} />
                        </td>

                        {/* Risk & Score */}
                        <td className="py-3.5 px-4">
                          <div className="flex items-center space-x-2">
                            <RiskBadge category={threat.threat_category} score={threat.risk_score} />
                            <span className="font-mono font-bold text-slate-200">
                              {threat.risk_score}
                            </span>
                          </div>
                        </td>

                        {/* AI Evidence */}
                        <td className="py-3.5 px-4 max-w-xs">
                          {domain && (
                            <div className="font-mono text-[11px] text-cyan-400 truncate">
                              {domain}
                            </div>
                          )}
                          {topReason ? (
                            <div className="text-[11px] text-slate-400 truncate">
                              {topReason}
                            </div>
                          ) : (
                            <div className="text-[11px] text-slate-500 italic">No heuristics triggered</div>
                          )}
                        </td>

                        {/* Status */}
                        <td className="py-3.5 px-4">
                          <StatusBadge status={threat.status} />
                        </td>

                        {/* Timestamp */}
                        <td className="py-3.5 px-4 text-slate-400 text-[11px] whitespace-nowrap">
                          {new Date(threat.timestamp).toLocaleDateString([], {
                            month: 'short',
                            day: 'numeric',
                          })}{' '}
                          {new Date(threat.timestamp).toLocaleTimeString([], {
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </td>

                        {/* Triage Actions */}
                        <td className="py-3.5 px-4 text-right">
                          <div className="flex items-center justify-end space-x-1.5">
                            {threat.status !== 'BLOCKED' && (
                              <button
                                onClick={() => handleQuickStatusChange(threat.id, 'BLOCKED')}
                                disabled={isActing}
                                className="px-2 py-1 rounded bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 text-[10px] font-semibold border border-rose-500/30 transition-all disabled:opacity-50"
                              >
                                Block
                              </button>
                            )}

                            {threat.status !== 'RESOLVED' && (
                              <button
                                onClick={() => handleQuickStatusChange(threat.id, 'RESOLVED')}
                                disabled={isActing}
                                className="px-2 py-1 rounded bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-300 text-[10px] font-semibold border border-emerald-500/30 transition-all disabled:opacity-50"
                              >
                                Resolve
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination Footer */}
          {!isLoading && threats.length > 0 && (
            <div className="px-6 py-4 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
              <div>
                Showing <span className="font-semibold text-slate-200">{(page - 1) * pageSize + 1}</span> to{' '}
                <span className="font-semibold text-slate-200">
                  {Math.min(page * pageSize, totalCount)}
                </span>{' '}
                of <span className="font-semibold text-slate-200">{totalCount}</span> threats
              </div>

              <div className="flex items-center space-x-2">
                <button
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={page <= 1}
                  className="p-1.5 rounded-lg border border-slate-800 bg-slate-950 text-slate-300 hover:bg-slate-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <span className="font-mono text-xs px-2 text-slate-300">
                  {page} / {totalPages}
                </span>
                <button
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  disabled={page >= totalPages}
                  className="p-1.5 rounded-lg border border-slate-800 bg-slate-950 text-slate-300 hover:bg-slate-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}
        </Card>
      </div>
    </AdminShell>
  );
}
