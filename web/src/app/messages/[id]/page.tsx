'use client';

import React, { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { UserShell } from '@/components/layout/UserShell';
import { api } from '@/services/api';
import { SmsDetailsData } from '@/types';
import {
  ArrowLeft,
  ShieldCheck,
  ShieldAlert,
  AlertTriangle,
  Ban,
  Unlock,
  ExternalLink,
  Clock,
  User,
  History,
  GitCommit,
  CheckCircle,
  RefreshCw,
} from 'lucide-react';
import { RiskBadge, StatusBadge } from '@/components/ui/Badge';

export default function SmsDetailsPage() {
  const params = useParams();
  const router = useRouter();
  const threatId = params.id as string;

  const [data, setData] = useState<SmsDetailsData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  const fetchDetails = async () => {
    try {
      setIsLoading(true);
      const res = await api.security.getSmsDetails(threatId);
      setData(res);
    } catch (err) {
      console.error('Failed to load SMS details:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (threatId) {
      fetchDetails();
    }
  }, [threatId]);

  const handleAction = async (action: string) => {
    try {
      setActionLoading(true);
      if (action === 'BLOCK_SENDER' && data?.threat_event?.sender) {
        await api.security.blockSender({ sender_id: data.threat_event.sender, reason: 'Sender blocked from SMS details' });
      } else if (action === 'MARK_SAFE') {
        await api.security.quarantineAction(threatId, { action: 'MARK_SAFE' });
      } else if (action === 'BLOCK_SMS') {
        await api.security.blockedSmsAction(threatId, { action: 'BLOCK' });
      } else if (action === 'UNBLOCK_SMS') {
        await api.security.blockedSmsAction(threatId, { action: 'RESTORE' });
      }
      await fetchDetails();
    } catch (err: any) {
      alert(`Action failed: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  if (isLoading) {
    return (
      <UserShell>
        <div className="max-w-4xl mx-auto px-4 py-16 text-center text-slate-400">
          <RefreshCw className="w-8 h-8 animate-spin mx-auto mb-3 text-cyan-400" />
          <p className="text-sm">Loading message forensics...</p>
        </div>
      </UserShell>
    );
  }

  if (!data || !data.threat_event) {
    return (
      <UserShell>
        <div className="max-w-4xl mx-auto px-4 py-16 text-center text-slate-400">
          <p className="text-base font-semibold text-white">Message Record Not Found</p>
          <Link href="/threats" className="text-cyan-400 text-xs font-semibold hover:underline mt-2 inline-block">
            Return to Messages History
          </Link>
        </div>
      </UserShell>
    );
  }

  const { threat_event, sender_info, audit_trail, feedback_history } = data;

  return (
    <UserShell>
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        {/* Back Link */}
        <button
          onClick={() => router.back()}
          className="inline-flex items-center space-x-1.5 text-xs text-slate-400 hover:text-white transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back</span>
        </button>

        {/* Header Summary */}
        <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center space-x-3">
              <RiskBadge category={threat_event.threat_category} size="lg" />
              <div>
                <h1 className="text-lg font-bold text-white">
                  SMS Forensics from {threat_event.sender || 'Unknown Sender'}
                </h1>
                <p className="text-xs text-slate-400">
                  Detected on {threat_event.timestamp ? new Date(threat_event.timestamp).toLocaleString() : 'N/A'}
                </p>
              </div>
            </div>
            <div className="flex items-center space-x-2 font-mono">
              <span className="text-sm font-bold text-amber-400">Risk Score: {threat_event.risk_score}/100</span>
              <StatusBadge status={threat_event.status || 'REPORTED'} />
            </div>
          </div>

          {/* Quick Actions Bar */}
          <div className="pt-3 border-t border-slate-800 flex flex-wrap gap-2">
            <button
              onClick={() => handleAction('MARK_SAFE')}
              disabled={actionLoading}
              className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-emerald-950/40 text-emerald-400 border border-emerald-800/40 text-xs font-semibold hover:bg-emerald-900/60"
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>Mark Safe</span>
            </button>
            {threat_event.is_blocked ? (
              <button
                onClick={() => handleAction('UNBLOCK_SMS')}
                disabled={actionLoading}
                className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-800 text-sky-400 text-xs font-semibold hover:bg-slate-700"
              >
                <Unlock className="w-3.5 h-3.5" />
                <span>Unblock Message</span>
              </button>
            ) : (
              <button
                onClick={() => handleAction('BLOCK_SMS')}
                disabled={actionLoading}
                className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-rose-950/40 text-rose-400 border border-rose-800/40 text-xs font-semibold hover:bg-rose-900/60"
              >
                <Ban className="w-3.5 h-3.5" />
                <span>Block Message</span>
              </button>
            )}
            {threat_event.sender && (
              <button
                onClick={() => handleAction('BLOCK_SENDER')}
                disabled={actionLoading}
                className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-800 text-slate-300 text-xs font-semibold hover:bg-slate-700 hover:text-white"
              >
                <Ban className="w-3.5 h-3.5 text-rose-500" />
                <span>Block Entire Sender</span>
              </button>
            )}
          </div>
        </div>

        {/* Message Content & Sender Info */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="md:col-span-2 space-y-4">
            <div className="p-5 rounded-xl bg-slate-900/60 border border-slate-800 space-y-3">
              <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400">Full Message Payload</h2>
              <div className="p-4 bg-slate-950 rounded-lg border border-slate-800/80 font-mono text-xs text-slate-200 whitespace-pre-wrap leading-relaxed break-words">
                {threat_event.raw_content || 'No message content available.'}
              </div>
              {threat_event.extracted_url && (
                <div className="p-3 bg-cyan-950/30 rounded-lg border border-cyan-900/40 text-xs">
                  <span className="text-slate-400 font-semibold">Extracted Target URL: </span>
                  <a
                    href={threat_event.extracted_url}
                    target="_blank"
                    rel="noreferrer"
                    className="text-cyan-400 underline font-mono break-all ml-1"
                  >
                    {threat_event.extracted_url}
                  </a>
                </div>
              )}
            </div>

            {/* AI Explanation & Heuristics */}
            <div className="p-5 rounded-xl bg-slate-900/60 border border-slate-800 space-y-3">
              <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400">AI Detection Analysis</h2>
              <div className="space-y-2 text-xs text-slate-300">
                <p>
                  <strong className="text-slate-400">Recommended Action: </strong>
                  {threat_event.recommended_action || 'Review sender authenticity'}
                </p>
                {threat_event.reasons && threat_event.reasons.length > 0 && (
                  <div>
                    <strong className="text-slate-400">Suspicion Reasons: </strong>
                    <ul className="list-disc list-inside mt-1 space-y-1 text-slate-300">
                      {threat_event.reasons.map((r: string, idx: number) => (
                        <li key={idx}>{r}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Sender Profile Box */}
          <div className="space-y-4">
            <div className="p-5 rounded-xl bg-slate-900/60 border border-slate-800 space-y-3">
              <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400">Sender Profile</h2>
              <div className="space-y-2 text-xs">
                <div>
                  <span className="text-slate-500 font-medium">Identifier: </span>
                  <span className="text-white font-mono font-bold">{threat_event.sender || 'Unknown'}</span>
                </div>
                {sender_info && (
                  <>
                    <div>
                      <span className="text-slate-500 font-medium">Display Name: </span>
                      <span className="text-slate-200">{sender_info.display_name || 'N/A'}</span>
                    </div>
                    <div>
                      <span className="text-slate-500 font-medium">Trust Status: </span>
                      <RiskBadge category={sender_info.trust_status} size="sm" />
                    </div>
                    <div>
                      <span className="text-slate-500 font-medium">Verification: </span>
                      <span className="text-slate-300 font-semibold">{sender_info.verification_status}</span>
                    </div>
                    <div className="font-mono text-[11px] text-slate-400 pt-1">
                      <div>Total Messages: {sender_info.total_messages_count}</div>
                      <div>Legitimate: {sender_info.legitimate_messages_count}</div>
                      <div>Dangerous: {sender_info.dangerous_messages_count}</div>
                    </div>
                  </>
                )}
                {threat_event.sender && (
                  <Link
                    href={`/senders/${encodeURIComponent(threat_event.sender)}`}
                    className="inline-flex items-center space-x-1 text-cyan-400 hover:text-cyan-300 font-semibold pt-2 text-xs"
                  >
                    <span>Inspect Sender Dossier</span>
                    <ExternalLink className="w-3 h-3" />
                  </Link>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Chronological Audit Trail & Feedback Timeline */}
        <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
          <h2 className="text-sm font-bold text-white flex items-center space-x-2">
            <History className="w-4 h-4 text-cyan-400" />
            <span>Chronological Audit Trail & Actions</span>
          </h2>

          {audit_trail.length === 0 ? (
            <p className="text-xs text-slate-500 italic">No historical audit modifications recorded.</p>
          ) : (
            <div className="space-y-3">
              {audit_trail.map((entry, idx) => (
                <div key={idx} className="flex items-start space-x-3 text-xs border-l-2 border-cyan-500/40 pl-3">
                  <div className="w-2 h-2 rounded-full bg-cyan-400 mt-1.5 -ml-[17px]" />
                  <div>
                    <div className="font-semibold text-slate-200">{entry.action}</div>
                    <div className="text-[10px] text-slate-500">
                      {entry.timestamp ? new Date(entry.timestamp).toLocaleString() : ''}
                    </div>
                    {entry.details && (
                      <div className="text-slate-400 text-[11px] mt-0.5">
                        {typeof entry.details === 'object' ? JSON.stringify(entry.details) : entry.details}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Feedback Reclassifications */}
          {feedback_history.length > 0 && (
            <div className="pt-4 border-t border-slate-800 space-y-3">
              <h3 className="text-xs font-bold text-slate-300 flex items-center space-x-1.5">
                <GitCommit className="w-3.5 h-3.5 text-amber-400" />
                <span>User Reclassifications (AI Immutable Baseline)</span>
              </h3>
              {feedback_history.map((fb, idx) => (
                <div key={idx} className="p-3 bg-slate-950/60 rounded-lg border border-slate-800 text-xs space-y-1">
                  <div className="flex items-center space-x-2">
                    <span className="text-slate-400">Original AI:</span>
                    <RiskBadge category={fb.original_prediction} size="sm" />
                    <span className="text-slate-500">→ User Classified:</span>
                    <RiskBadge category={fb.user_classification} size="sm" />
                  </div>
                  {fb.comments && <p className="text-slate-300 italic">&quot;{fb.comments}&quot;</p>}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </UserShell>
  );
}

