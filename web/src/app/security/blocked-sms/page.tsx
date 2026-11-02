'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { UserShell } from '@/components/layout/UserShell';
import { SecurityTabs } from '@/components/security/SecurityTabs';
import { api } from '@/services/api';
import { BlockedSmsItem } from '@/types';
import {
  FileX,
  ShieldCheck,
  ShieldAlert,
  Unlock,
  ExternalLink,
  Search,
  RefreshCw,
  Info,
} from 'lucide-react';
import { RiskBadge, StatusBadge } from '@/components/ui/Badge';

export default function BlockedSmsPage() {
  const [messages, setMessages] = useState<BlockedSmsItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const fetchBlockedSms = async () => {
    try {
      setIsLoading(true);
      const data = await api.security.getBlockedSms();
      setMessages(data);
    } catch (err) {
      console.error('Failed to load blocked messages:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchBlockedSms();
  }, []);

  const handleAction = async (threatId: string, action: string) => {
    try {
      setActionLoading(true);
      await api.security.blockedSmsAction(threatId, { action });
      await fetchBlockedSms();
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
      (m.block_reason && m.block_reason.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  return (
    <UserShell>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">Blocked Messages</h1>
          <p className="text-sm text-slate-400 mt-1">
            Individual SMS messages blocked and suppressed from your messaging inbox.
          </p>
        </div>

        <SecurityTabs />

        {/* Informational Guidance */}
        <div className="p-4 rounded-xl bg-slate-900/80 border border-slate-800 flex items-start space-x-3.5">
          <Info className="w-5 h-5 text-sky-400 shrink-0 mt-0.5" />
          <div className="text-xs text-slate-300 space-y-1">
            <p className="font-semibold text-sky-300">Targeted Message Suppression</p>
            <p className="text-slate-400 leading-relaxed">
              Blocking an individual message suppresses only that specific message without blacklisting the entire sender.
              Conversely, unblocking a message restores its visibility without altering the sender&apos;s global status.
            </p>
          </div>
        </div>

        {/* Controls */}
        <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
          <div className="relative flex-1 max-w-md">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search blocked messages..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 bg-slate-900 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-cyan-500"
            />
          </div>

          <button
            onClick={fetchBlockedSms}
            disabled={isLoading}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors self-end sm:self-auto"
            title="Refresh"
          >
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>

        {/* Blocked SMS List */}
        {isLoading ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/50 rounded-xl border border-slate-800">
            <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-cyan-400" />
            <p className="text-sm">Loading blocked messages...</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/40 rounded-xl border border-slate-800">
            <FileX className="w-10 h-10 mx-auto mb-3 text-slate-500" />
            <p className="text-base font-semibold text-white">No Blocked Messages</p>
            <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
              You haven&apos;t blocked any individual messages. Blocked scam or harassment messages will appear here for audit and restoration.
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
                      {msg.blocked_at ? new Date(msg.blocked_at).toLocaleString() : ''}
                    </span>
                  </div>
                  <div className="flex items-center space-x-2 font-mono text-xs">
                    <span className="text-rose-400 font-bold">Risk: {msg.risk_score}/100</span>
                    <span className="px-2 py-0.5 rounded bg-rose-950/40 text-rose-300 border border-rose-800/50 text-[10px] font-bold">
                      BLOCKED
                    </span>
                  </div>
                </div>

                <div className="p-3 bg-slate-950/80 rounded-lg border border-slate-800 text-xs text-slate-300 font-mono whitespace-pre-wrap break-words">
                  {msg.raw_content || 'No content retained'}
                </div>

                <div className="text-[11px] text-slate-400">
                  <span className="text-slate-500 font-medium">Block Reason: </span>
                  {msg.block_reason || 'Blocked by user'}
                </div>

                <div className="pt-2 border-t border-slate-800/80 flex flex-wrap items-center justify-between gap-2">
                  <Link
                    href={`/messages/${msg.id}`}
                    className="inline-flex items-center space-x-1 text-xs text-cyan-400 hover:text-cyan-300 font-semibold"
                  >
                    <span>View Inspection Forensics</span>
                    <ExternalLink className="w-3 h-3" />
                  </Link>

                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => handleAction(msg.id, 'RESTORE')}
                      disabled={actionLoading}
                      className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-sky-400 text-xs font-semibold transition-colors"
                      title="Restore message to history"
                    >
                      <Unlock className="w-3.5 h-3.5" />
                      <span>Restore Message</span>
                    </button>
                    <button
                      onClick={() => handleAction(msg.id, 'MARK_SAFE')}
                      disabled={actionLoading}
                      className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-lg bg-emerald-950/40 hover:bg-emerald-900/60 text-emerald-400 text-xs font-semibold transition-colors"
                    >
                      <ShieldCheck className="w-3.5 h-3.5" />
                      <span>Mark Safe</span>
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

