'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { UserShell } from '@/components/layout/UserShell';
import { api } from '@/services/api';
import { FeedbackItem } from '@/types';
import {
  MessageSquareQuote,
  ShieldCheck,
  ShieldAlert,
  Clock,
  ExternalLink,
  Search,
  RefreshCw,
  GitCommit,
  CheckCircle,
} from 'lucide-react';
import { RiskBadge, StatusBadge } from '@/components/ui/Badge';

export default function FeedbackHistoryPage() {
  const [feedbacks, setFeedbacks] = useState<FeedbackItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');

  const fetchFeedbackHistory = async () => {
    try {
      setIsLoading(true);
      const res = await api.feedback.list(1, 50);
      setFeedbacks(res.items);
    } catch (err) {
      console.error('Failed to load feedback history:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchFeedbackHistory();
  }, []);

  const filtered = feedbacks.filter(
    (f) =>
      (f.comment && f.comment.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (f.original_prediction && f.original_prediction.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (f.corrected_label && f.corrected_label.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (f.threat_event_id && f.threat_event_id.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  return (
    <UserShell>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">User Reclassification & Feedback History</h1>
          <p className="text-sm text-slate-400 mt-1">
            Audit log of all your AI label corrections. Original model predictions remain immutable for calibration tracking.
          </p>
        </div>

        {/* Immutability Invariant Alert */}
        <div className="p-4 rounded-xl bg-slate-900/80 border border-slate-800 flex items-start space-x-3.5">
          <GitCommit className="w-5 h-5 text-cyan-400 shrink-0 mt-0.5" />
          <div className="text-xs text-slate-300 space-y-1">
            <p className="font-semibold text-cyan-300">Immutable Audit Trail Invariant</p>
            <p className="text-slate-400 leading-relaxed">
              When you reclassify a message, Squish adapts your local personal preferences, but <strong>the original AI model prediction is preserved permanently</strong> to ensure rigorous ML retraining and false-positive evaluation.
            </p>
          </div>
        </div>

        {/* Search & Refresh */}
        <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
          <div className="relative flex-1 max-w-md">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search feedback history..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 bg-slate-900 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-cyan-500"
            />
          </div>

          <button
            onClick={fetchFeedbackHistory}
            disabled={isLoading}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors self-end sm:self-auto"
            title="Refresh"
          >
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>

        {/* Feedback Audit Timeline */}
        {isLoading ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/50 rounded-xl border border-slate-800">
            <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-cyan-400" />
            <p className="text-sm">Loading audit trail...</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/40 rounded-xl border border-slate-800">
            <MessageSquareQuote className="w-10 h-10 mx-auto mb-3 text-slate-500" />
            <p className="text-base font-semibold text-white">No Reclassification History</p>
            <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
              You haven&apos;t corrected or submitted feedback on any detections yet. When you override a classification, the audit trail will appear here.
            </p>
          </div>
        ) : (
          <div className="bg-slate-900/60 rounded-xl border border-slate-800 overflow-hidden shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-950/60 border-b border-slate-800 text-slate-400 uppercase font-semibold">
                  <tr>
                    <th className="px-4 py-3">Timestamp</th>
                    <th className="px-4 py-3">Original AI Prediction</th>
                    <th className="px-4 py-3">Your Correction</th>
                    <th className="px-4 py-3">Review Status</th>
                    <th className="px-4 py-3">User Feedback Notes</th>
                    <th className="px-4 py-3 text-right">Target Threat</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-slate-300">
                  {filtered.map((fb) => (
                    <tr key={fb.id} className="hover:bg-slate-800/30 transition-colors">
                      <td className="px-4 py-3 text-slate-400 whitespace-nowrap">
                        {fb.created_at ? new Date(fb.created_at).toLocaleString() : 'N/A'}
                      </td>
                      <td className="px-4 py-3">
                        <RiskBadge category={fb.original_prediction || 'SAFE'} size="sm" />
                        {fb.original_confidence !== undefined && fb.original_confidence !== null && (
                          <span className="text-[10px] text-slate-500 ml-1.5 font-mono">
                            {(fb.original_confidence * 100).toFixed(0)}%
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <RiskBadge category={fb.corrected_label || 'SAFE'} size="sm" />
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={fb.review_status || 'PENDING'} />
                      </td>
                      <td className="px-4 py-3 text-slate-300 max-w-xs truncate" title={fb.comment || ''}>
                        {fb.comment || 'No comment provided'}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {fb.threat_event_id ? (
                          <Link
                            href={`/messages/${fb.threat_event_id}`}
                            className="inline-flex items-center space-x-1 text-cyan-400 hover:text-cyan-300 font-semibold"
                          >
                            <span>Inspect</span>
                            <ExternalLink className="w-3 h-3" />
                          </Link>
                        ) : (
                          <span className="text-slate-500 font-mono text-[10px]">Direct Feed</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </UserShell>
  );
}
