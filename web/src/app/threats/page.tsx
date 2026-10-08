'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { UserShell } from '@/components/layout/UserShell';
import { Card } from '@/components/ui/Card';
import { RiskBadge, EventTypeBadge, StatusBadge } from '@/components/ui/Badge';
import { TableSkeleton } from '@/components/ui/LoadingState';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { api } from '@/services/api';
import { ThreatEvent, ThreatFilterParams } from '@/types';
import {
  ShieldAlert,
  Search,
  Filter,
  ArrowRight,
  RefreshCw,
  Clock,
  ChevronLeft,
  ChevronRight,
  Trash2,
  CheckCircle2,
  XCircle,
  FileSearch,
  ExternalLink,
  Ban,
  Eye,
} from 'lucide-react';

export default function UserThreatHistoryPage() {
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
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);

  const fetchUserThreats = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const params: ThreatFilterParams = {
        page,
        page_size: pageSize,
      };
      if (eventTypeFilter !== 'ALL') params.event_type = eventTypeFilter;
      if (categoryFilter !== 'ALL') params.threat_category = categoryFilter;

      const res = await api.userThreats.list(params);
      setThreats(res.items || []);
      setTotalCount(res.total || 0);
      setTotalPages(res.pages || 1);
    } catch (err: any) {
      setError(err.message || 'Failed to retrieve your threat history.');
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize, eventTypeFilter, categoryFilter]);

  useEffect(() => {
    fetchUserThreats();
  }, [fetchUserThreats]);

  const handleTakeAction = async (threatId: string, action: 'BLOCK' | 'MARK_SAFE' | 'IGNORE') => {
    setActionInProgress(threatId);
    try {
      const updated = await api.userThreats.takeAction(threatId, action);
      setThreats((prev) =>
        prev.map((t) =>
          t.id === threatId
            ? { ...t, status: updated.status, user_action: updated.user_action, updated_at: updated.updated_at }
            : t
        )
      );
    } catch (err: any) {
      alert(`Action failed: ${err.message}`);
    } finally {
      setActionInProgress(null);
    }
  };

  const handleDeleteDetection = async (threatId: string) => {
    if (!confirm('Are you sure you want to delete this threat record from your history?')) return;
    setActionInProgress(threatId);
    try {
      await api.userThreats.delete(threatId);
      setThreats((prev) => prev.filter((t) => t.id !== threatId));
      setTotalCount((c) => Math.max(0, c - 1));
    } catch (err: any) {
      alert(`Delete failed: ${err.message}`);
    } finally {
      setActionInProgress(null);
    }
  };

  // Client search filtering
  const filteredThreats = threats.filter((t) => {
    if (!searchTerm.trim()) return true;
    const term = searchTerm.toLowerCase();
    const domain = t.url_metadata?.domain?.toLowerCase() || '';
    const reasons = (t.reasons || []).join(' ').toLowerCase();
    const id = t.id.toLowerCase();
    return domain.includes(term) || reasons.includes(term) || id.includes(term);
  });

  return (
    <UserShell>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2">
              <ShieldAlert className="w-6 h-6 text-cyan-400" />
              <span>Your Threat History & Decisions</span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Inspect detected quishing and smishing signals, take immediate protection actions, and manage your isolated history.
            </p>
          </div>

          <button
            onClick={() => fetchUserThreats()}
            disabled={isLoading}
            className="inline-flex items-center space-x-2 px-3 py-2 bg-slate-900 hover:bg-slate-800 text-slate-300 text-xs font-semibold rounded-lg border border-slate-700/60 shadow-sm transition-all"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-cyan-400' : ''}`} />
            <span>Refresh Threats</span>
          </button>
        </div>

        {/* Filter Controls Bar */}
        <Card className="p-4 bg-slate-900/90 border-slate-800">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {/* Search Input */}
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-500">
                <Search className="w-3.5 h-3.5" />
              </div>
              <input
                type="text"
                placeholder="Search domain, URL, or indicator..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-9 pr-3 py-2 bg-slate-950/70 border border-slate-800 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500/50"
              />
            </div>

            {/* Vector Filter */}
            <div>
              <select
                value={eventTypeFilter}
                onChange={(e) => {
                  setEventTypeFilter(e.target.value);
                  setPage(1);
                }}
                className="w-full px-3 py-2 bg-slate-950/70 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-cyan-500/50 font-sans"
              >
                <option value="ALL">All Sources (SMS & QR)</option>
                <option value="QR">QR Code Scans Only</option>
                <option value="SMS">SMS Messages Only</option>
              </select>
            </div>

            {/* Category Filter */}
            <div>
              <select
                value={categoryFilter}
                onChange={(e) => {
                  setCategoryFilter(e.target.value);
                  setPage(1);
                }}
                className="w-full px-3 py-2 bg-slate-950/70 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-cyan-500/50 font-sans"
              >
                <option value="ALL">All Risk Levels</option>
                <option value="HIGH">High Severity (Malicious)</option>
                <option value="MEDIUM">Medium Severity (Suspicious)</option>
                <option value="LOW">Low Severity (Uncertain)</option>
                <option value="SAFE">Safe (Benign)</option>
              </select>
            </div>
          </div>
        </Card>

        {/* Threats Table Card */}
        <Card className="overflow-hidden border-slate-800 bg-slate-900/90">
          {error && (
            <div className="p-6">
              <ErrorState
                title="Error Loading Threats"
                message={error}
                onRetry={fetchUserThreats}
              />
            </div>
          )}

          {isLoading ? (
            <TableSkeleton rows={8} cols={6} />
          ) : filteredThreats.length === 0 ? (
            <EmptyState
              icon={FileSearch}
              title="No Threat Detections Found"
              description="No quishing or smishing events match your criteria. You are clean and protected!"
              actionLabel="Clear Filters"
              onAction={() => {
                setEventTypeFilter('ALL');
                setCategoryFilter('ALL');
                setSearchTerm('');
                setPage(1);
              }}
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-800 bg-slate-950/50 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                    <th className="py-3 px-4">Channel</th>
                    <th className="py-3 px-4">Risk & Verdict</th>
                    <th className="py-3 px-4">Detected Target / Indicators</th>
                    <th className="py-3 px-4">Your Decision</th>
                    <th className="py-3 px-4">Detection Time</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-xs">
                  {filteredThreats.map((threat) => {
                    const isActing = actionInProgress === threat.id;
                    const domain = threat.url_metadata?.domain || threat.qr_metadata?.scheme;
                    const topReason = threat.reasons && threat.reasons.length > 0 ? threat.reasons[0] : null;

                    return (
                      <tr key={threat.id} className="hover:bg-slate-800/40 transition-colors group">
                        {/* Channel */}
                        <td className="py-3.5 px-4">
                          <EventTypeBadge type={threat.event_type} />
                        </td>

                        {/* Risk & Verdict */}
                        <td className="py-3.5 px-4">
                          <div className="flex items-center space-x-2">
                            <RiskBadge category={threat.threat_category} score={threat.risk_score} />
                            <span className="font-mono font-bold text-slate-200">
                              {threat.risk_score}/100
                            </span>
                          </div>
                        </td>

                        {/* Indicators */}
                        <td className="py-3.5 px-4 max-w-sm">
                          {domain ? (
                            <div className="font-mono text-cyan-400 font-semibold truncate text-[11px]">
                              {domain}
                            </div>
                          ) : (
                            <div className="text-[11px] text-slate-400 font-mono">
                              ID: {threat.id.substring(0, 10)}...
                            </div>
                          )}
                          {topReason ? (
                            <div className="text-[11px] text-slate-400 truncate mt-0.5">
                              {topReason}
                            </div>
                          ) : (
                            <div className="text-[11px] text-slate-500 italic">Policy: {threat.recommended_action}</div>
                          )}
                        </td>

                        {/* User Action */}
                        <td className="py-3.5 px-4">
                          {threat.user_action ? (
                            <span
                              className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold border ${
                                threat.user_action === 'BLOCK'
                                  ? 'bg-rose-500/10 text-rose-300 border-rose-500/20'
                                  : threat.user_action === 'MARK_SAFE'
                                  ? 'bg-emerald-500/10 text-emerald-300 border-emerald-500/20'
                                  : 'bg-slate-800 text-slate-300 border-slate-700'
                              }`}
                            >
                              {threat.user_action}
                            </span>
                          ) : (
                            <StatusBadge status={threat.status} />
                          )}
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

                        {/* User Actions Buttons */}
                        <td className="py-3.5 px-4 text-right">
                          <div className="flex items-center justify-end space-x-1.5">
                            {threat.user_action !== 'BLOCK' && threat.threat_category !== 'SAFE' && (
                              <button
                                onClick={() => handleTakeAction(threat.id, 'BLOCK')}
                                disabled={isActing}
                                className="px-2 py-1 rounded bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 text-[10px] font-semibold border border-rose-500/30 transition-all disabled:opacity-50"
                                title="Block sender and URL domain"
                              >
                                Block
                              </button>
                            )}

                            {threat.user_action !== 'MARK_SAFE' && threat.status !== 'RESOLVED' && (
                              <button
                                onClick={() => handleTakeAction(threat.id, 'MARK_SAFE')}
                                disabled={isActing}
                                className="px-2 py-1 rounded bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-300 text-[10px] font-semibold border border-emerald-500/30 transition-all disabled:opacity-50"
                                title="Mark false alarm as safe"
                              >
                                Safe
                              </button>
                            )}

                            <Link
                              href={`/threats/${threat.id}`}
                              className="p-1 rounded text-slate-400 hover:text-cyan-400 hover:bg-slate-800 transition-colors"
                              title="Inspect AI Forensics"
                            >
                              <Eye className="w-3.5 h-3.5" />
                            </Link>

                            <button
                              onClick={() => handleDeleteDetection(threat.id)}
                              disabled={isActing}
                              className="p-1 rounded text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 transition-colors disabled:opacity-50"
                              title="Delete record from your history"
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
    </UserShell>
  );
}
