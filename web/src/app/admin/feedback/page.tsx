'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { AdminShell } from '@/components/layout/AdminShell';
import { Card } from '@/components/ui/Card';
import { TableSkeleton } from '@/components/ui/LoadingState';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { api } from '@/services/api';
import { FeedbackItem, FeedbackStats } from '@/types';
import {
  MessageSquareQuote,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  ShieldCheck,
  XCircle,
  Play,
  Filter,
  Check,
  X,
  Clock,
  Sparkles,
  Database
} from 'lucide-react';

export default function AdminFeedbackPage() {
  const [feedbackList, setFeedbackList] = useState<FeedbackItem[]>([]);
  const [stats, setStats] = useState<FeedbackStats | null>(null);
  const [totalCount, setTotalCount] = useState<number>(0);
  const [page, setPage] = useState<number>(1);
  const [pageSize] = useState<number>(20);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [isRetraining, setIsRetraining] = useState<boolean>(false);

  const fetchStats = useCallback(async () => {
    try {
      const data = await api.feedback.stats();
      setStats(data);
    } catch {
      // Graceful fallback
    }
  }, []);

  const fetchFeedback = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const filter = statusFilter === 'ALL' ? undefined : statusFilter;
      const res = await api.feedback.list(page, pageSize, filter);
      setFeedbackList(res.items || []);
      setTotalCount(res.total || 0);
      setTotalPages(res.pages || 1);
    } catch (err: any) {
      setError(err.message || 'Failed to retrieve feedback submissions.');
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize, statusFilter]);

  useEffect(() => {
    fetchStats();
    fetchFeedback();
  }, [fetchStats, fetchFeedback]);

  const handleReview = async (id: string, reviewStatus: 'APPROVED' | 'REJECTED') => {
    try {
      await api.feedback.review(id, reviewStatus);
      setActionMessage(`Feedback ${id.substring(0, 8)} marked as ${reviewStatus}`);
      setTimeout(() => setActionMessage(null), 4000);
      fetchFeedback();
      fetchStats();
    } catch (err: any) {
      alert(`Error updating feedback: ${err.message}`);
    }
  };

  const handleTriggerRetraining = async () => {
    if (!confirm('Start controlled incremental retraining job on candidate TinyBERT model with approved feedback samples?')) return;
    setIsRetraining(true);
    try {
      const res = await api.feedback.retrainCandidate();
      setActionMessage(`Success: ${res.message} Candidate version: ${res.model_candidate_version || 'N/A'}`);
      fetchStats();
      fetchFeedback();
    } catch (err: any) {
      alert(`Retraining initiation failed: ${err.message}`);
    } finally {
      setIsRetraining(false);
    }
  };

  return (
    <AdminShell>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2">
              <MessageSquareQuote className="w-6 h-6 text-amber-400" />
              <span>User Detection Feedback & Retraining Hub</span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Field reports and corrections submitted across Android and Web for false-positive resolution and controlled model learning.
            </p>
          </div>

          <div className="flex items-center space-x-2">
            <button
              onClick={handleTriggerRetraining}
              disabled={isRetraining}
              className="inline-flex items-center space-x-1.5 px-3 py-2 bg-purple-600 hover:bg-purple-500 text-white text-xs font-semibold rounded-lg shadow-sm transition-all disabled:opacity-50"
            >
              <Sparkles className={`w-3.5 h-3.5 ${isRetraining ? 'animate-spin' : ''}`} />
              <span>{isRetraining ? 'Retraining...' : 'Trigger Retraining'}</span>
            </button>

            <button
              onClick={() => { fetchStats(); fetchFeedback(); }}
              disabled={isLoading}
              className="inline-flex items-center space-x-1.5 px-3 py-2 bg-slate-900 hover:bg-slate-800 text-slate-300 text-xs font-semibold rounded-lg border border-slate-700/60 shadow-sm transition-all"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-amber-400' : ''}`} />
              <span>Refresh</span>
            </button>
          </div>
        </div>

        {actionMessage && (
          <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs rounded-lg flex items-center space-x-2">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            <span>{actionMessage}</span>
          </div>
        )}

        {/* Phase 9: Feedback Management Section Stats Cards */}
        {stats && (
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-3">
            <div className="bg-slate-900/80 border border-slate-800 p-3 rounded-lg">
              <span className="text-[10px] text-slate-400 block font-medium">Total Feedback</span>
              <span className="text-lg font-bold text-slate-100">{stats.total_submissions}</span>
            </div>
            <div className="bg-slate-900/80 border border-slate-800 p-3 rounded-lg">
              <span className="text-[10px] text-emerald-400 block font-medium">Safe Corrections</span>
              <span className="text-lg font-bold text-emerald-400">{stats.safe_corrections}</span>
            </div>
            <div className="bg-slate-900/80 border border-slate-800 p-3 rounded-lg">
              <span className="text-[10px] text-rose-400 block font-medium">Smishing Scams</span>
              <span className="text-lg font-bold text-rose-400">{stats.smishing_corrections}</span>
            </div>
            <div className="bg-slate-900/80 border border-slate-800 p-3 rounded-lg">
              <span className="text-[10px] text-amber-400 block font-medium">Suspicious</span>
              <span className="text-lg font-bold text-amber-400">{stats.suspicious_corrections}</span>
            </div>
            <div className="bg-slate-900/80 border border-slate-800 p-3 rounded-lg">
              <span className="text-[10px] text-sky-400 block font-medium">Pending Review</span>
              <span className="text-lg font-bold text-sky-400">{stats.pending_reviews}</span>
            </div>
            <div className="bg-slate-900/80 border border-slate-800 p-3 rounded-lg">
              <span className="text-[10px] text-purple-400 block font-medium">Approved Samples</span>
              <span className="text-lg font-bold text-purple-400">{stats.approved_training_samples}</span>
            </div>
            <div className="bg-slate-900/80 border border-slate-800 p-3 rounded-lg">
              <span className="text-[10px] text-slate-400 block font-medium">Last Retrain Date</span>
              <span className="text-xs font-semibold text-slate-300 truncate block mt-1">
                {stats.last_retraining_date ? stats.last_retraining_date.split(' ')[0] : 'None'}
              </span>
            </div>
          </div>
        )}

        {/* Filters */}
        <div className="flex items-center space-x-2">
          <Filter className="w-3.5 h-3.5 text-slate-400" />
          <span className="text-xs text-slate-400">Review Filter:</span>
          {['ALL', 'PENDING', 'APPROVED', 'REJECTED'].map((st) => (
            <button
              key={st}
              onClick={() => { setStatusFilter(st); setPage(1); }}
              className={`px-2.5 py-1 text-[11px] font-semibold rounded-md border transition-all ${
                statusFilter === st
                  ? 'bg-amber-500/20 text-amber-300 border-amber-500/40'
                  : 'bg-slate-900 text-slate-400 border-slate-800 hover:bg-slate-800'
              }`}
            >
              {st}
            </button>
          ))}
        </div>

        {/* Feedback Table */}
        <Card className="overflow-hidden border-slate-800 bg-slate-900/90">
          {error && (
            <div className="p-6">
              <ErrorState title="Error Loading Feedback" message={error} onRetry={fetchFeedback} />
            </div>
          )}

          {isLoading ? (
            <TableSkeleton rows={8} cols={6} />
          ) : feedbackList.length === 0 ? (
            <EmptyState
              icon={MessageSquareQuote}
              title="No Feedback Found"
              description="No feedback submissions match the current filter."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-800 bg-slate-950/50 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                    <th className="py-3 px-4">Origin / ID</th>
                    <th className="py-3 px-4">Original Pred.</th>
                    <th className="py-3 px-4">User Correction</th>
                    <th className="py-3 px-4">Privacy Sender Hash</th>
                    <th className="py-3 px-4">Review Status</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-xs">
                  {feedbackList.map((item) => (
                    <tr key={item.id} className="hover:bg-slate-800/40 transition-colors">
                      <td className="py-3 px-4">
                        <div className="font-mono text-[11px] text-cyan-400">
                          {item.id.substring(0, 8)}...
                        </div>
                        <span className="text-[10px] text-slate-400 block">
                          Src: {item.feedback_source || 'ANDROID'}
                        </span>
                      </td>

                      <td className="py-3 px-4 text-slate-300 font-mono text-[11px]">
                        {item.original_prediction || 'N/A'}
                        {item.original_confidence && (
                          <span className="text-[10px] text-slate-400 block">
                            {(item.original_confidence * 100).toFixed(1)}% conf
                          </span>
                        )}
                      </td>

                      <td className="py-3 px-4">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold border ${
                            item.corrected_label?.includes('BANKING') || item.corrected_label?.includes('SAFE')
                              ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                              : item.corrected_label?.includes('SMISH')
                              ? 'bg-rose-500/10 text-rose-400 border-rose-500/20'
                              : 'bg-amber-500/10 text-amber-400 border-amber-500/20'
                          }`}
                        >
                          {item.corrected_label || item.classification}
                        </span>
                        {item.comment && (
                          <p className="text-[11px] text-slate-400 mt-1 line-clamp-1 italic max-w-xs">
                            &quot;{item.comment}&quot;
                          </p>
                        )}
                      </td>

                      <td className="py-3 px-4 font-mono text-[11px] text-slate-400">
                        {item.sender_hash ? `${item.sender_hash.substring(0, 10)}...` : 'Unspecified'}
                        {item.sender_reputation_score !== undefined && item.sender_reputation_score !== null && (
                          <span className="text-[10px] text-cyan-400 block font-sans">
                            Rep: {(item.sender_reputation_score * 100).toFixed(0)}%
                          </span>
                        )}
                      </td>

                      <td className="py-3 px-4">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold border ${
                            item.review_status === 'APPROVED'
                              ? 'bg-purple-500/10 text-purple-400 border-purple-500/20'
                              : item.review_status === 'REJECTED'
                              ? 'bg-slate-800 text-slate-400 border-slate-700'
                              : 'bg-sky-500/10 text-sky-400 border-sky-500/20'
                          }`}
                        >
                          {item.review_status || 'PENDING'}
                        </span>
                      </td>

                      <td className="py-3 px-4 text-right space-x-1 whitespace-nowrap">
                        <button
                          onClick={() => handleReview(item.id, 'APPROVED')}
                          disabled={item.review_status === 'APPROVED'}
                          title="Approve for Training"
                          className="p-1 px-2 text-[10px] font-semibold rounded bg-emerald-950 text-emerald-400 border border-emerald-800/80 hover:bg-emerald-900 disabled:opacity-40 transition-colors"
                        >
                          <Check className="w-3 h-3 inline mr-0.5" /> Approve
                        </button>
                        <button
                          onClick={() => handleReview(item.id, 'REJECTED')}
                          disabled={item.review_status === 'REJECTED'}
                          title="Reject Feedback"
                          className="p-1 px-2 text-[10px] font-semibold rounded bg-slate-900 text-rose-400 border border-rose-900/60 hover:bg-rose-950/60 disabled:opacity-40 transition-colors"
                        >
                          <X className="w-3 h-3 inline mr-0.5" /> Reject
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination */}
          {!isLoading && feedbackList.length > 0 && (
            <div className="px-6 py-4 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
              <div>
                Showing <span className="font-semibold text-slate-200">{(page - 1) * pageSize + 1}</span> to{' '}
                <span className="font-semibold text-slate-200">
                  {Math.min(page * pageSize, totalCount)}
                </span>{' '}
                of <span className="font-semibold text-slate-200">{totalCount}</span> submissions
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
