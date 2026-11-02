'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { UserShell } from '@/components/layout/UserShell';
import { SecurityTabs } from '@/components/security/SecurityTabs';
import { api } from '@/services/api';
import { SenderSecurityItem } from '@/types';
import {
  Ban,
  UserCheck,
  ExternalLink,
  Search,
  RefreshCw,
  Unlock,
  ShieldCheck,
} from 'lucide-react';
import { RiskBadge } from '@/components/ui/Badge';

export default function BlockedUsersPage() {
  const [senders, setSenders] = useState<SenderSecurityItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const fetchBlockedSenders = async () => {
    try {
      setIsLoading(true);
      const data = await api.security.getBlockedSenders();
      setSenders(data);
    } catch (err) {
      console.error('Failed to load blocked senders:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchBlockedSenders();
  }, []);

  const handleUnblock = async (senderId: string) => {
    if (!confirm(`Are you sure you want to unblock ${senderId}? Incoming messages will be analyzed normally.`)) return;
    try {
      setActionLoading(true);
      await api.security.unblockSender(senderId);
      await fetchBlockedSenders();
    } catch (err: any) {
      alert(`Error unblocking sender: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const filtered = senders.filter(
    (s) =>
      s.sender_id.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (s.display_name && s.display_name.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (s.block_reason && s.block_reason.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  return (
    <UserShell>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">Blocked Senders</h1>
          <p className="text-sm text-slate-400 mt-1">
            Senders you have restricted from your inbox. All future communications are rejected or flagged.
          </p>
        </div>

        <SecurityTabs />

        {/* Controls */}
        <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
          <div className="relative flex-1 max-w-md">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search blocked senders..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 bg-slate-900 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-cyan-500"
            />
          </div>

          <button
            onClick={fetchBlockedSenders}
            disabled={isLoading}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors self-end sm:self-auto"
            title="Refresh"
          >
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>

        {/* Blocked Senders Table */}
        {isLoading ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/50 rounded-xl border border-slate-800">
            <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-cyan-400" />
            <p className="text-sm">Loading blocked senders...</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/40 rounded-xl border border-slate-800">
            <Ban className="w-10 h-10 mx-auto mb-3 text-slate-500" />
            <p className="text-base font-semibold text-white">No Senders Currently Blocked</p>
            <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
              You haven&apos;t added any senders to your block list. You can block fraudulent or harassment senders anytime from message details or safety alerts.
            </p>
          </div>
        ) : (
          <div className="bg-slate-900/60 rounded-xl border border-slate-800 overflow-hidden shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-950/60 border-b border-slate-800 text-slate-400 uppercase font-semibold">
                  <tr>
                    <th className="px-4 py-3">Blocked Sender</th>
                    <th className="px-4 py-3">Date Blocked</th>
                    <th className="px-4 py-3">Reason</th>
                    <th className="px-4 py-3">Associated SMS</th>
                    <th className="px-4 py-3">Blocked By</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-slate-300">
                  {filtered.map((s) => (
                    <tr key={s.id} className="hover:bg-slate-800/30 transition-colors">
                      <td className="px-4 py-3">
                        <div className="font-semibold text-white">{s.display_name || s.sender_id}</div>
                        <div className="text-[11px] text-slate-400 font-mono">{s.sender_id}</div>
                      </td>
                      <td className="px-4 py-3 text-slate-400">
                        {s.blocked_at ? new Date(s.blocked_at).toLocaleDateString() : 'N/A'}
                      </td>
                      <td className="px-4 py-3 text-slate-300 max-w-xs truncate" title={s.block_reason || ''}>
                        {s.block_reason || 'User block'}
                      </td>
                      <td className="px-4 py-3 font-mono text-slate-400">
                        {s.total_messages_count} messages
                      </td>
                      <td className="px-4 py-3 text-slate-400">
                        {s.blocked_by || 'USER'}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end space-x-2">
                          <Link
                            href={`/senders/${encodeURIComponent(s.sender_id)}`}
                            className="p-1.5 rounded bg-slate-800 text-slate-300 hover:text-cyan-400 hover:bg-slate-700 transition-colors"
                            title="View Sender History & Details"
                          >
                            <ExternalLink className="w-3.5 h-3.5" />
                          </Link>
                          <button
                            onClick={() => handleUnblock(s.sender_id)}
                            disabled={actionLoading}
                            className="inline-flex items-center space-x-1 px-2.5 py-1.5 rounded bg-slate-800 hover:bg-slate-700 text-sky-400 text-xs font-semibold transition-colors"
                            title="Unblock Sender"
                          >
                            <Unlock className="w-3.5 h-3.5" />
                            <span>Unblock</span>
                          </button>
                        </div>
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

