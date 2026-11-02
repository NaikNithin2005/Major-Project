'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { UserShell } from '@/components/layout/UserShell';
import { SecurityTabs } from '@/components/security/SecurityTabs';
import { api } from '@/services/api';
import { QuarantinedSmsItem } from '@/types';
import {
  AlertTriangle,
  ShieldCheck,
  ShieldAlert,
  Ban,
  Unlock,
  ExternalLink,
  Search,
  RefreshCw,
  Lock,
} from 'lucide-react';
import { RiskBadge, StatusBadge } from '@/components/ui/Badge';

export default function QuarantinedSmsPage() {
  const [messages, setMessages] = useState<QuarantinedSmsItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const fetchQuarantined = async () => {
    try {
      setIsLoading(true);
      const data = await api.security.getQuarantinedSms();
      setMessages(data);
    } catch (err) {
      console.error('Failed to load quarantined messages:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchQuarantined();
  }, []);

  const handleAction = async (threatId: string, action: string, promptReason: boolean = false) => {
    let reason = '';
    if (promptReason) {
      reason = prompt('Optional reason / security note:') || '';
    }
    try {
      setActionLoading(true);
      await api.security.quarantineAction(threatId, { action, reason });
      await fetchQuarantined();
    } catch (err: any) {
      alert(`Action failed: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const filtered = messages.filter(
    (m) =>
      (m.sender && m.sender.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (m.raw_content && m.raw_content.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (m.quarantine_reason && m.quarantine_reason.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  return (
    <UserShell>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">Quarantined SMS Messages</h1>
          <p className="text-sm text-slate-400 mt-1">
            Suspicious incoming messages isolated by TinyBERT and threat scoring engines before delivery.
          </p>
        </div>

        <SecurityTabs />

        {/* Security Invariant Alert */}
        <div className="p-4 rounded-xl bg-amber-950/30 border border-amber-800/40 flex items-start space-x-3.5">
          <Lock className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
          <div className="text-xs text-slate-300 space-y-1">
            <p className="font-semibold text-amber-300">Quarantine Isolation Policy</p>
            <p className="text-slate-400 leading-relaxed">
              Releasing an individual message from quarantine removes the isolation lock for viewing, but <strong>never automatically whitelists the sender</strong>.
              Sender trust requires explicit user review.
            </p>
          </div>
        </div>

        {/* Search & Refresh */}
        <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
          <div className="relative flex-1 max-w-md">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search quarantined SMS..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 bg-slate-900 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-cyan-500"
            />
          </div>

          <button
            onClick={fetchQuarantined}
            disabled={isLoading}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors self-end sm:self-auto"
            title="Refresh"
          >
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>

        {/* Quarantined List */}
        {isLoading ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/50 rounded-xl border border-slate-800">
            <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-cyan-400" />
            <p className="text-sm">Loading quarantined messages...</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/40 rounded-xl border border-slate-800">
            <AlertTriangle className="w-10 h-10 mx-auto mb-3 text-emerald-500" />
            <p className="text-base font-semibold text-white">No Quarantined Messages</p>
            <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
              Your message quarantine vault is empty. All incoming messages are passing safety heuristics or confirmed clear.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4">
            {filtered.map((msg) => (
              <div
                key={msg.id}
                className="p-5 rounded-xl bg-slate-900/60 border border-slate-800 hover:border-slate-700 transition-all space-y-3"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center space-x-2">
                    <RiskBadge category={msg.threat_category} size="sm" />
                    <span className="text-xs font-semibold text-white">From: {msg.sender || 'Unknown'}</span>
                    <span className="text-[11px] text-slate-500">
                      {msg.timestamp ? new Date(msg.timestamp).toLocaleString() : ''}
                    </span>
                  </div>
                  <div className="flex items-center space-x-2 font-mono text-xs">
                    <span className="text-amber-400 font-bold">Risk: {msg.risk_score}/100</span>
                    <StatusBadge status={msg.status} />
                  </div>
                </div>

                <div className="p-3 bg-slate-950/80 rounded-lg border border-slate-800 text-xs text-slate-200 font-mono whitespace-pre-wrap break-words">
                  {msg.raw_content || 'No content retained'}
                </div>

                {msg.extracted_url && (
                  <div className="text-[11px] text-cyan-400 flex items-center space-x-1.5 truncate">
                    <span className="text-slate-400">Target URL:</span>
                    <span className="underline">{msg.extracted_url}</span>
                  </div>
                )}

                <div className="text-[11px] text-slate-400">
                  <span className="text-slate-500 font-medium">Isolation Reason: </span>
                  {msg.quarantine_reason || (msg.reasons && msg.reasons.join('; ')) || 'High risk indicators detected'}
                </div>

                <div className="pt-2 border-t border-slate-800/80 flex flex-wrap items-center justify-between gap-2">
                  <Link
                    href={`/messages/${msg.id}`}
                    className="inline-flex items-center space-x-1 text-xs text-cyan-400 hover:text-cyan-300 font-semibold"
                  >
                    <span>View Full Forensics & Timeline</span>
                    <ExternalLink className="w-3 h-3" />
                  </Link>

                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => handleAction(msg.id, 'RELEASE')}
                      disabled={actionLoading}
                      className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition-colors"
                      title="Release message to inbox without trusting sender"
                    >
                      <Unlock className="w-3.5 h-3.5 text-sky-400" />
                      <span>Release</span>
                    </button>
                    <button
                      onClick={() => handleAction(msg.id, 'MARK_SAFE', true)}
                      disabled={actionLoading}
                      className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-lg bg-emerald-950/40 hover:bg-emerald-900/60 text-emerald-400 text-xs font-semibold transition-colors"
                    >
                      <ShieldCheck className="w-3.5 h-3.5" />
                      <span>Mark Safe</span>
                    </button>
                    <button
                      onClick={() => handleAction(msg.id, 'BLOCK_SENDER', true)}
                      disabled={actionLoading}
                      className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-lg bg-rose-950/40 hover:bg-rose-900/60 text-rose-400 text-xs font-semibold transition-colors"
                    >
                      <Ban className="w-3.5 h-3.5" />
                      <span>Block Sender</span>
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </UserShell>
  );
}

