'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { DashboardShell } from '@/components/layout/DashboardShell';
import { Card } from '@/components/ui/Card';
import { TableSkeleton } from '@/components/ui/LoadingState';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { api } from '@/services/api';
import { FeedbackItem } from '@/types';
import {
  MessageSquareQuote,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  ArrowRight,
  Clock,
} from 'lucide-react';

export default function FeedbackPage() {
  const [feedbackList, setFeedbackList] = useState<FeedbackItem[]>([]);
  const [totalCount, setTotalCount] = useState<number>(0);
  const [page, setPage] = useState<number>(1);
  const [pageSize] = useState<number>(20);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchFeedback = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await api.feedback.list(page, pageSize);
      setFeedbackList(res.items || []);
      setTotalCount(res.total || 0);
      setTotalPages(res.pages || 1);
    } catch (err: any) {
      setError(err.message || 'Failed to retrieve feedback submissions.');
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchFeedback();
  }, [fetchFeedback]);

  return (
    <DashboardShell>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2">
              <MessageSquareQuote className="w-6 h-6 text-indigo-400" />
              <span>User Detection Feedback</span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              User submissions and false positive reports for model performance evaluation.
            </p>
          </div>

          <button
            onClick={fetchFeedback}
            disabled={isLoading}
            className="inline-flex items-center space-x-2 px-3 py-2 bg-slate-900 hover:bg-slate-800 text-slate-300 text-xs font-semibold rounded-lg border border-slate-700/60 shadow-sm transition-all"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-cyan-400' : ''}`} />
            <span>Refresh Feedback</span>
          </button>
        </div>

        {error && (
          <ErrorState
            title="Feedback Engine Error"
            message={error}
            onRetry={fetchFeedback}
          />
        )}

        {isLoading ? (
          <TableSkeleton rows={8} />
        ) : feedbackList.length === 0 ? (
          <EmptyState
            title="No Feedback Submissions"
            description="Users have not submitted any detection reports or false positive reviews yet."
          />
        ) : (
          <Card className="p-0 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="bg-slate-900/90 border-b border-slate-800 text-slate-400 uppercase tracking-wider font-semibold text-[11px]">
                    <th className="py-3.5 px-4">Feedback ID</th>
                    <th className="py-3.5 px-4">Linked Threat</th>
                    <th className="py-3.5 px-4">User Classification</th>
                    <th className="py-3.5 px-4">User Comment</th>
                    <th className="py-3.5 px-4">Submitted At</th>
                    <th className="py-3.5 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-sans">
                  {feedbackList.map((item) => (
                    <tr key={item.id} className="hover:bg-slate-800/30 transition-colors">
                      <td className="py-3.5 px-4 font-mono font-medium text-slate-300">
                        {item.id.slice(0, 8)}...
                      </td>

                      <td className="py-3.5 px-4 font-mono text-cyan-400">
                        <Link
                          href={`/threats/${item.threat_event_id}`}
                          className="hover:underline flex items-center space-x-1"
                        >
                          <span>{item.threat_event_id.slice(0, 8)}...</span>
                        </Link>
                      </td>

                      <td className="py-3.5 px-4">
                        {item.classification === 'FALSE_POSITIVE' ? (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-indigo-500/15 text-indigo-300 border border-indigo-500/30">
                            <AlertTriangle className="w-3 h-3 mr-1 text-indigo-400" />
                            False Positive
                          </span>
                        ) : (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                            <CheckCircle2 className="w-3 h-3 mr-1 text-emerald-400" />
                            True Positive
                          </span>
                        )}
                      </td>

                      <td className="py-3.5 px-4 text-slate-300 max-w-xs truncate">
                        {item.comment || <span className="text-slate-500 italic">No comment provided</span>}
                      </td>

                      <td className="py-3.5 px-4 text-slate-400">
                        <div className="flex items-center space-x-1.5">
                          <Clock className="w-3.5 h-3.5 text-slate-500" />
                          <span>{new Date(item.created_at).toLocaleString()}</span>
                        </div>
                      </td>

                      <td className="py-3.5 px-4 text-right">
                        <Link
                          href={`/threats/${item.threat_event_id}`}
                          className="text-[11px] font-semibold text-cyan-400 hover:text-cyan-300 px-2 py-1 rounded bg-cyan-500/10 hover:bg-cyan-500/20 border border-cyan-500/25 inline-flex items-center space-x-1"
                        >
                          <span>Inspect Event</span>
                          <ArrowRight className="w-3 h-3" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="p-4 border-t border-slate-800 bg-slate-900/60 flex items-center justify-between">
              <span className="text-xs text-slate-400">
                Total <span className="font-semibold text-slate-200">{totalCount}</span> feedback reviews
              </span>

              <div className="flex items-center space-x-2">
                <button
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={page <= 1 || isLoading}
                  className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 disabled:opacity-40 disabled:pointer-events-none transition-colors"
                  aria-label="Previous Page"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <span className="text-xs font-semibold px-2 text-slate-300">
                  Page {page} of {totalPages}
                </span>
                <button
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  disabled={page >= totalPages || isLoading}
                  className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 disabled:opacity-40 disabled:pointer-events-none transition-colors"
                  aria-label="Next Page"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          </Card>
        )}
      </div>
    </DashboardShell>
  );
}
