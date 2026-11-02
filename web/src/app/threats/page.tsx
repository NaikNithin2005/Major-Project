'use client';

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import Link from 'next/link';
import { UserShell } from '@/components/layout/UserShell';
import { RiskBadge, EventTypeBadge, StatusBadge } from '@/components/ui/Badge';
import { TableSkeleton } from '@/components/ui/LoadingState';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { ThreatDetailPanel } from '@/components/ui/ThreatDetailPanel';
import { api } from '@/services/api';
import { ThreatEvent } from '@/types';
import { sessionManager } from '@/services/sessionManager';
import {
  ShieldAlert,
  Search,
  Filter,
  ArrowRight,
  RefreshCw,
  Clock,
  ChevronDown,
  Trash2,
  CheckCircle2,
  FileSearch,
  ExternalLink,
  Ban,
  Eye,
  X,
  QrCode,
  Smartphone,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import {
  fetchPaginatedUserThreats,
  takeUserThreatAction,
  deleteUserThreat,
} from '@/services/threatFirestore';
import { QueryDocumentSnapshot, DocumentData } from 'firebase/firestore';

export default function UserThreatHistoryPage() {
  const { user } = useAuth();
  const [threats, setThreats] = useState<ThreatEvent[]>([]);
  const [lastDoc, setLastDoc] = useState<QueryDocumentSnapshot<DocumentData> | null>(null);
  const [hasMore, setHasMore] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isLoadingMore, setIsLoadingMore] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [eventTypeFilter, setEventTypeFilter] = useState<string>('ALL');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);
  const [selectedThreat, setSelectedThreat] = useState<ThreatEvent | null>(null);

  // Session reset listener
  useEffect(() => {
    const deregister = sessionManager.registerResetHandler(() => {
      setThreats([]);
      setLastDoc(null);
      setHasMore(false);
      setIsLoading(false);
      setSelectedThreat(null);
      setError(null);
    });
    return () => deregister();
  }, []);

  // Fetch initial page of threats
  const loadInitialPage = useCallback(async () => {
    const currentUid = user?.firebase_uid;
    if (!currentUid) {
      setThreats([]);
      setIsLoading(false);
      return;
    }

    const currentGeneration = sessionManager.getGeneration();
    setIsLoading(true);
    setError(null);

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
        // Fallback to backend API if Firestore returned empty or hasn't synced yet
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
        setError(err.message || 'Failed to load threat history');
      }
    } finally {
      if (sessionManager.isValidSession(currentGeneration, currentUid)) {
        setIsLoading(false);
      }
    }
  }, [user?.firebase_uid]);

  useEffect(() => {
    loadInitialPage();
  }, [loadInitialPage]);

  // Load next page using Firestore cursor
  const handleLoadMore = async () => {
    const currentUid = user?.firebase_uid;
    if (!currentUid || !hasMore || isLoadingMore) return;

    const currentGeneration = sessionManager.getGeneration();
    setIsLoadingMore(true);

    try {
      const res = await fetchPaginatedUserThreats({
        uid: currentUid,
        pageSize: 25,
        lastDoc: lastDoc,
      });

      if (!sessionManager.isValidSession(currentGeneration, currentUid)) return;

      // Deduplicate items to prevent duplicate rendering
      setThreats((prev) => {
        const existingIds = new Set(prev.map((t) => t.id));
        const newItems = res.items.filter((t) => !existingIds.has(t.id));
        return [...prev, ...newItems];
      });
      setLastDoc(res.lastVisible);
      setHasMore(res.hasMore);
    } catch (err: any) {
      console.warn('Load more error:', err);
    } finally {
      if (sessionManager.isValidSession(currentGeneration, currentUid)) {
        setIsLoadingMore(false);
      }
    }
  };

  const handleTakeAction = async (threatId: string, action: 'BLOCK' | 'MARK_SAFE') => {
    setActionInProgress(threatId);
    try {
      if (user?.firebase_uid) {
        await takeUserThreatAction(user.firebase_uid, threatId, action);
      }
      try {
        await api.userThreats.takeAction(threatId, action);
      } catch {}

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
    } catch (err: any) {
      alert(`Action failed: ${err.message}`);
    } finally {
      setActionInProgress(null);
    }
  };

  const handleDelete = async (threatId: string) => {
    if (!confirm('Are you sure you want to permanently delete this threat event?')) return;
    setActionInProgress(threatId);
    try {
      if (user?.firebase_uid) {
        await deleteUserThreat(user.firebase_uid, threatId);
      }
      setThreats((prev) => prev.filter((t) => t.id !== threatId));
      if (selectedThreat?.id === threatId) {
        setSelectedThreat(null);
      }
    } catch (err: any) {
      alert(`Failed to delete record: ${err.message}`);
    } finally {
      setActionInProgress(null);
    }
  };

  // Filter threats
  const filteredThreats = useMemo(() => {
    return threats.filter((threat) => {
      // Event type filter
      if (eventTypeFilter !== 'ALL' && threat.event_type !== eventTypeFilter) {
        return false;
      }
      // Category filter
      if (categoryFilter !== 'ALL' && threat.threat_category !== categoryFilter) {
        return false;
      }
      // Status filter
      if (statusFilter !== 'ALL' && threat.status !== statusFilter) {
        return false;
      }
      // Search term
      if (searchTerm.trim() !== '') {
        const query = searchTerm.toLowerCase();
        const matchesContent =
          (threat.sanitized_preview || '').toLowerCase().includes(query) ||
          (threat.decoded_url || '').toLowerCase().includes(query) ||
          (threat.id || '').toLowerCase().includes(query) ||
          (threat.reasons || []).some((r) => r.toLowerCase().includes(query));
        if (!matchesContent) return false;
      }
      return true;
    });
  }, [threats, eventTypeFilter, categoryFilter, statusFilter, searchTerm]);

  const clearFilters = () => {
    setEventTypeFilter('ALL');
    setCategoryFilter('ALL');
    setStatusFilter('ALL');
    setSearchTerm('');
  };

  const isFiltered =
    eventTypeFilter !== 'ALL' || categoryFilter !== 'ALL' || statusFilter !== 'ALL' || searchTerm.trim() !== '';

  return (
    <UserShell>
      <div className="space-y-6">
        {/* Header Banner */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 glass-panel rounded-2xl p-6 shadow-xl">
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="text-xl font-bold text-slate-100 tracking-tight">Messages & QR Code Safety History</h1>
              <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 font-mono">
                {filteredThreats.length} Recorded
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-1 max-w-2xl leading-relaxed">
              Complete history of text messages and QR codes checked by Squish on your devices, including safe items, warnings, and dangerous scam detections.
            </p>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={loadInitialPage}
              disabled={isLoading}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 border border-slate-700 text-xs font-semibold text-slate-200 transition-colors disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-cyan-400' : ''}`} />
              <span>Refresh</span>
            </button>
          </div>
        </div>

        {/* Filter and Search Bar */}
        <div className="glass-panel rounded-2xl p-4 flex flex-col md:flex-row items-center justify-between gap-3 shadow-lg">
          {/* Search Box */}
          <div className="relative w-full md:w-80">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search by URL, keyword, or event ID..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full bg-slate-900/80 border border-slate-700/80 rounded-xl pl-9 pr-8 py-2 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500 transition-colors"
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

          {/* Filter Dropdowns */}
          <div className="flex flex-wrap items-center gap-2 w-full md:w-auto">
            {/* Event Type Filter */}
            <select
              value={eventTypeFilter}
              onChange={(e) => setEventTypeFilter(e.target.value)}
              className="bg-slate-900/80 border border-slate-700/80 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-cyan-500 font-medium cursor-pointer"
            >
              <option value="ALL">All Checks (SMS & QR)</option>
              <option value="SMS">Text Messages (SMS)</option>
              <option value="QR">QR Codes</option>
            </select>

            {/* Severity Filter */}
            <select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              className="bg-slate-900/80 border border-slate-700/80 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-cyan-500 font-medium cursor-pointer"
            >
              <option value="ALL">All Results</option>
              <option value="HIGH">Dangerous Scams</option>
              <option value="MEDIUM">Needs Attention</option>
              <option value="LOW">Minor Caution</option>
              <option value="SAFE">Looks Safe</option>
            </select>

            {/* Status Filter */}
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="bg-slate-900/80 border border-slate-700/80 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-cyan-500 font-medium cursor-pointer"
            >
              <option value="ALL">All Statuses</option>
              <option value="NEW">New / Unresolved</option>
              <option value="BLOCKED">Blocked</option>
              <option value="RESOLVED">Resolved / Safe</option>
            </select>

            {isFiltered && (
              <button
                onClick={clearFilters}
                className="px-3 py-2 rounded-xl text-xs font-semibold text-slate-400 hover:text-slate-100 bg-slate-800/80 hover:bg-slate-800 border border-slate-700 transition-colors"
              >
                Clear
              </button>
            )}
          </div>
        </div>

        {error && (
          <ErrorState
            title="Failed to Load Threats"
            message={error}
            onRetry={loadInitialPage}
          />
        )}

        {/* Content Area */}
        {isLoading ? (
          <div className="py-20 flex justify-center">
            <TableSkeleton rows={8} />
          </div>
        ) : filteredThreats.length === 0 ? (
          <EmptyState
            title={isFiltered ? 'No Matches Found' : 'No Messages or Scans Yet'}
            description={
              isFiltered
                ? 'Try changing your search term or selecting All Results.'
                : 'Text messages and QR codes checked by your phone will appear here in real time.'
            }
            actionLabel={isFiltered ? 'Clear Filters' : 'Refresh Telemetry'}
            onAction={isFiltered ? clearFilters : loadInitialPage}
          />
        ) : (
          <div className="space-y-4">
            {/* Desktop Table View */}
            <div className="hidden md:block glass-panel rounded-2xl overflow-hidden shadow-xl">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-800 bg-slate-900/60 text-slate-400 uppercase text-[10px] tracking-wider font-semibold">
                    <th className="py-3.5 pl-4">Type</th>
                    <th className="py-3.5">Sender / Origin</th>
                    <th className="py-3.5">Result</th>
                    <th className="py-3.5">Safety Rating</th>
                    <th className="py-3.5">Message / Scanned Content</th>
                    <th className="py-3.5">Checked Date</th>
                    <th className="py-3.5 pr-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {filteredThreats.map((threat) => (
                    <tr
                      key={threat.id}
                      onClick={() => setSelectedThreat(threat)}
                      className="hover:bg-slate-800/40 transition-colors cursor-pointer group"
                    >
                      <td className="py-3.5 pl-4 font-mono">
                        <EventTypeBadge type={threat.event_type} />
                      </td>
                      <td className="py-3.5 font-medium text-slate-300">
                        {threat.sender || (threat.event_type === 'SMS' ? 'Text Message' : 'QR Scan')}
                      </td>
                      <td className="py-3.5">
                        <RiskBadge category={threat.threat_category} score={threat.risk_score} />
                      </td>
                      <td className="py-3.5 font-mono text-xs">
                        <span className={threat.threat_category === 'HIGH' ? 'text-rose-400 font-bold' : threat.threat_category === 'MEDIUM' ? 'text-amber-400 font-bold' : 'text-emerald-400 font-bold'}>
                          {threat.risk_score}/100
                        </span>
                      </td>
                      <td className="py-3.5 max-w-sm truncate text-slate-200">
                        {threat.raw_content || threat.decoded_url || threat.sanitized_preview || 'No content retained'}
                      </td>
                      <td className="py-3.5 text-slate-400 whitespace-nowrap">
                        {new Date(threat.timestamp).toLocaleDateString([], {
                          month: 'short',
                          day: 'numeric',
                        })}{' '}
                        {new Date(threat.timestamp).toLocaleTimeString([], {
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </td>
                      <td className="py-3.5 pr-4 text-right" onClick={(e) => e.stopPropagation()}>
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
                              onClick={() => handleTakeAction(threat.id, 'BLOCK')}
                              disabled={actionInProgress === threat.id}
                              className="p-1.5 rounded-lg bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 transition-colors disabled:opacity-50"
                              title="Block Threat"
                            >
                              <Ban className="w-3.5 h-3.5" />
                            </button>
                          )}
                          {threat.status !== 'RESOLVED' && threat.risk_score >= 40 && (
                            <button
                              onClick={() => handleTakeAction(threat.id, 'MARK_SAFE')}
                              disabled={actionInProgress === threat.id}
                              className="p-1.5 rounded-lg bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 transition-colors disabled:opacity-50"
                              title="Mark Safe (False Positive)"
                            >
                              <CheckCircle2 className="w-3.5 h-3.5" />
                            </button>
                          )}
                          <button
                            onClick={() => handleDelete(threat.id)}
                            disabled={actionInProgress === threat.id}
                            className="p-1.5 rounded-lg text-slate-500 hover:text-rose-400 hover:bg-slate-800 transition-colors disabled:opacity-50"
                            title="Delete"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Mobile Responsive Card View */}
            <div className="md:hidden space-y-3">
              {filteredThreats.map((threat) => (
                <div
                  key={threat.id}
                  onClick={() => setSelectedThreat(threat)}
                  className="glass-card rounded-xl p-4 space-y-3 cursor-pointer"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <EventTypeBadge type={threat.event_type} />
                      <span className="text-xs font-bold text-slate-200">
                        {threat.threat_category === 'HIGH' ? 'High Risk' : threat.threat_category}
                      </span>
                    </div>
                    <StatusBadge status={threat.status} />
                  </div>

                  <div className="bg-slate-950/80 rounded-lg p-2.5 font-mono text-xs text-slate-300 break-words line-clamp-2">
                    {threat.decoded_url || threat.sanitized_preview || 'Scanned Payload'}
                  </div>

                  <div className="flex items-center justify-between text-xs text-slate-400 pt-1">
                    <span className="flex items-center gap-1 font-mono">
                      <Clock className="w-3 h-3" />
                      {new Date(threat.timestamp).toLocaleTimeString([], {
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </span>
                    <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                      <button
                        onClick={() => setSelectedThreat(threat)}
                        className="px-2.5 py-1 rounded bg-slate-800 text-cyan-400 font-semibold text-[11px]"
                      >
                        Inspect
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {/* Pagination / Load More Button */}
            {hasMore && (
              <div className="text-center py-4">
                <button
                  onClick={handleLoadMore}
                  disabled={isLoadingMore}
                  className="inline-flex items-center gap-2 px-6 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-750 border border-slate-750 text-xs font-semibold text-slate-200 transition-all shadow-lg hover:border-slate-650 disabled:opacity-50"
                >
                  {isLoadingMore ? (
                    <>
                      <RefreshCw className="w-4 h-4 animate-spin text-cyan-400" />
                      <span>Loading Older Threats...</span>
                    </>
                  ) : (
                    <>
                      <ChevronDown className="w-4 h-4 text-cyan-400" />
                      <span>Load More Threats</span>
                    </>
                  )}
                </button>
              </div>
            )}
          </div>
        )}

        {/* Threat Details Side Panel */}
        <ThreatDetailPanel
          threat={selectedThreat}
          isOpen={!!selectedThreat}
          onClose={() => setSelectedThreat(null)}
          onAction={handleTakeAction}
          onDelete={handleDelete}
          isActionInProgress={actionInProgress === selectedThreat?.id}
        />
      </div>
    </UserShell>
  );
}
