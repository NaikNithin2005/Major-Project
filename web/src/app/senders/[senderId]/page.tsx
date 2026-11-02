'use client';

import React, { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { UserShell } from '@/components/layout/UserShell';
import { api } from '@/services/api';
import { SenderDetailsData } from '@/types';
import {
  ArrowLeft,
  ShieldCheck,
  ShieldAlert,
  AlertTriangle,
  Ban,
  Unlock,
  ExternalLink,
  History,
  GitCommit,
  RefreshCw,
  Mail,
} from 'lucide-react';
import { RiskBadge, StatusBadge } from '@/components/ui/Badge';

export default function SenderDetailsPage() {
  const params = useParams();
  const router = useRouter();
  const senderId = decodeURIComponent(params.senderId as string);

  const [data, setData] = useState<SenderDetailsData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  const fetchSenderDetails = async () => {
    try {
      setIsLoading(true);
      const res = await api.security.getSenderDetails(senderId);
      setData(res);
    } catch (err) {
      console.error('Failed to load sender details:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (senderId) {
      fetchSenderDetails();
    }
  }, [senderId]);

  const handleTrust = async () => {
    const reason = prompt('Reason for trusting this sender:', 'Verified legitimate by user');
    if (!reason) return;
    try {
      setActionLoading(true);
      await api.security.trustSender({ sender_id: senderId, reason });
      await fetchSenderDetails();
    } catch (err: any) {
      alert(`Action failed: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleBlock = async () => {
    const reason = prompt('Reason for blocking this sender:', 'Sender blocked by user');
    if (!reason) return;
    try {
      setActionLoading(true);
      await api.security.blockSender({ sender_id: senderId, reason });
      await fetchSenderDetails();
    } catch (err: any) {
      alert(`Action failed: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleUnblock = async () => {
    if (!confirm(`Are you sure you want to unblock ${senderId}?`)) return;
    try {
      setActionLoading(true);
      await api.security.unblockSender(senderId);
      await fetchSenderDetails();
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
          <p className="text-sm">Loading sender dossier...</p>
        </div>
      </UserShell>
    );
  }

  if (!data || !data.sender) {
    return (
      <UserShell>
        <div className="max-w-4xl mx-auto px-4 py-16 text-center text-slate-400">
          <p className="text-base font-semibold text-white">Sender Record Not Found</p>
          <button
            onClick={() => router.back()}
            className="text-cyan-400 text-xs font-semibold hover:underline mt-2 inline-block"
          >
            Go Back
          </button>
        </div>
      </UserShell>
    );
  }

  const { sender, message_history, threat_history, feedback_history, audit_trail } = data;

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

        {/* Sender Dossier Card */}
        <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center space-x-3">
              <div className="w-12 h-12 rounded-xl bg-slate-800 border border-slate-700 flex items-center justify-center text-cyan-400 font-bold text-lg font-mono">
                {sender.sender_id.slice(0, 2).toUpperCase()}
              </div>
              <div>
                <h1 className="text-lg font-bold text-white font-mono">{sender.sender_id}</h1>
                <p className="text-xs text-slate-400">
                  {sender.display_name && sender.display_name !== sender.sender_id ? sender.display_name : 'Sender Entity Profile'}
                </p>
              </div>
            </div>

            <div className="flex items-center space-x-2 font-mono">
              <RiskBadge category={sender.trust_status} size="md" />
              <span className="text-xs text-slate-400 font-semibold">{sender.verification_status}</span>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2 text-xs border-t border-slate-800">
            <div className="p-3 bg-slate-950/60 rounded-lg">
              <div className="text-slate-500 font-medium">Total Messages</div>
              <div className="text-base font-bold text-white font-mono">{message_history.length}</div>
            </div>
            <div className="p-3 bg-slate-950/60 rounded-lg">
              <div className="text-slate-500 font-medium">Risk Score</div>
              <div className="text-base font-bold text-amber-400 font-mono">{sender.risk_score}/100</div>
            </div>
            <div className="p-3 bg-slate-950/60 rounded-lg">
              <div className="text-slate-500 font-medium">Trust Marked By</div>
              <div className="text-sm font-semibold text-slate-200">{sender.marked_by}</div>
            </div>
            <div className="p-3 bg-slate-950/60 rounded-lg">
              <div className="text-slate-500 font-medium">Status</div>
              <div className="text-sm font-semibold text-slate-200">
                {sender.is_blocked ? 'BLOCKED' : sender.trust_status}
              </div>
            </div>
          </div>

          {/* Quick Actions */}
          <div className="pt-2 flex flex-wrap gap-2">
            {sender.is_blocked ? (
              <button
                onClick={handleUnblock}
                disabled={actionLoading}
                className="inline-flex items-center space-x-1.5 px-3.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-sky-400 text-xs font-semibold transition-colors"
              >
                <Unlock className="w-3.5 h-3.5" />
                <span>Unblock Sender</span>
              </button>
            ) : (
              <button
                onClick={handleBlock}
                disabled={actionLoading}
                className="inline-flex items-center space-x-1.5 px-3.5 py-1.5 rounded-lg bg-rose-950/40 hover:bg-rose-900/60 text-rose-400 border border-rose-800/40 text-xs font-semibold transition-colors"
              >
                <Ban className="w-3.5 h-3.5" />
                <span>Block Sender</span>
              </button>
            )}

            {sender.trust_status !== 'TRUSTED' && (
              <button
                onClick={handleTrust}
                disabled={actionLoading}
                className="inline-flex items-center space-x-1.5 px-3.5 py-1.5 rounded-lg bg-emerald-950/40 hover:bg-emerald-900/60 text-emerald-400 border border-emerald-800/40 text-xs font-semibold transition-colors"
              >
                <ShieldCheck className="w-3.5 h-3.5" />
                <span>Mark as Trusted Sender</span>
              </button>
            )}
          </div>
        </div>

        {/* Message History */}
        <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
          <h2 className="text-sm font-bold text-white flex items-center space-x-2">
            <Mail className="w-4 h-4 text-cyan-400" />
            <span>Associated Messages ({message_history.length})</span>
          </h2>

          {message_history.length === 0 ? (
            <p className="text-xs text-slate-500 italic">No messages stored from this sender.</p>
          ) : (
            <div className="divide-y divide-slate-800">
              {message_history.map((m) => (
                <div key={m.id} className="py-3 flex items-center justify-between gap-3 text-xs">
                  <div className="space-y-1 max-w-lg">
                    <div className="text-slate-300 font-mono text-[11px] truncate">
                      {m.content_preview}
                    </div>
                    <div className="text-[10px] text-slate-500">
                      {m.timestamp ? new Date(m.timestamp).toLocaleString() : ''}
                    </div>
                  </div>
                  <div className="flex items-center space-x-2 shrink-0">
                    <RiskBadge category={m.threat_category} size="sm" />
                    <Link
                      href={`/messages/${m.id}`}
                      className="p-1 rounded bg-slate-800 text-cyan-400 hover:bg-slate-700"
                      title="Inspect SMS"
                    >
                      <ExternalLink className="w-3.5 h-3.5" />
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Audit Trail */}
        <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
          <h2 className="text-sm font-bold text-white flex items-center space-x-2">
            <History className="w-4 h-4 text-cyan-400" />
            <span>Sender Security Audit Trail</span>
          </h2>

          {audit_trail.length === 0 ? (
            <p className="text-xs text-slate-500 italic">No security modifications logged for this sender.</p>
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
        </div>
      </div>
    </UserShell>
  );
}

