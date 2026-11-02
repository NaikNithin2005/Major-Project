'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { UserShell } from '@/components/layout/UserShell';
import { SecurityTabs } from '@/components/security/SecurityTabs';
import { api } from '@/services/api';
import { SenderSecurityItem } from '@/types';
import {
  ShieldAlert,
  AlertTriangle,
  UserCheck,
  ExternalLink,
  Ban,
  ShieldCheck,
  Search,
  RefreshCw,
  HelpCircle,
} from 'lucide-react';
import { RiskBadge } from '@/components/ui/Badge';

export default function UntrustedUsersPage() {
  const [senders, setSenders] = useState<SenderSecurityItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const fetchUntrustedSenders = async () => {
    try {
      setIsLoading(true);
      const data = await api.security.getUntrustedSenders();
      setSenders(data);
    } catch (err) {
      console.error('Failed to load untrusted senders:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchUntrustedSenders();
  }, []);

  const handleTrust = async (senderId: string) => {
    const reason = prompt(`Reason for trusting ${senderId}:`, 'Verified legitimate sender by user');
    if (!reason) return;
    try {
      setActionLoading(true);
      await api.security.trustSender({ sender_id: senderId, reason });
      await fetchUntrustedSenders();
    } catch (err: any) {
      alert(`Error trusting sender: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleBlock = async (senderId: string) => {
    const reason = prompt(`Reason for blocking ${senderId}:`, 'User blocked this suspicious sender');
    if (!reason) return;
    try {
      setActionLoading(true);
      await api.security.blockSender({ sender_id: senderId, reason });
      await fetchUntrustedSenders();
    } catch (err: any) {
      alert(`Error blocking sender: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const filtered = senders.filter(
    (s) =>
      s.sender_id.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (s.display_name && s.display_name.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  return (
    <UserShell>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">Untrusted & Unverified Senders</h1>
          <p className="text-sm text-slate-400 mt-1">
            Review senders with suspicious activity, unverified identities, or pending security ratings.
          </p>
        </div>

        <SecurityTabs />

        {/* Guidance Note */}
        <div className="p-4 rounded-xl bg-amber-950/20 border border-amber-800/30 flex items-start space-x-3.5">
          <HelpCircle className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
          <div className="text-xs text-slate-300 space-y-1">
            <p className="font-semibold text-amber-300">Nuanced Threat Differentiation</p>
            <p className="text-slate-400 leading-relaxed">
              An unverified sender is <strong>not automatically malicious</strong>. Squish separates first-time senders from
              confirmed smishing campaigns to prevent false alerts on legitimate service messages.
            </p>
          </div>
        </div>

        {/* Search & Refresh */}
        <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
          <div className="relative flex-1 max-w-md">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search untrusted senders..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 bg-slate-900 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-cyan-500"
            />
          </div>

          <button
            onClick={fetchUntrustedSenders}
            disabled={isLoading}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors self-end sm:self-auto"
            title="Refresh"
          >
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>

        {/* Untrusted Senders Table */}
        {isLoading ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/50 rounded-xl border border-slate-800">
            <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-cyan-400" />
            <p className="text-sm">Loading senders...</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/40 rounded-xl border border-slate-800">
            <UserCheck className="w-10 h-10 mx-auto mb-3 text-slate-500" />
            <p className="text-base font-semibold text-white">No Untrusted Senders Detected</p>
            <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
              All active senders in your account have either been verified, trusted, or placed on your blocked list.
            </p>
          </div>
        ) : (
          <div className="bg-slate-900/60 rounded-xl border border-slate-800 overflow-hidden shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-950/60 border-b border-slate-800 text-slate-400 uppercase font-semibold">
                  <tr>
                    <th className="px-4 py-3">Sender</th>
                    <th className="px-4 py-3">Classification</th>
                    <th className="px-4 py-3">Threat Profile</th>
                    <th className="px-4 py-3">Detection Context</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-slate-300">
                  {filtered.map((s) => (
                    <tr key={s.id} className="hover:bg-slate-800/30 transition-colors">
                      <td className="px-4 py-3">
                        <div className="font-semibold text-white">{s.display_name || s.sender_id}</div>
                        <div className="text-[11px] text-slate-400 font-mono">{s.sender_id}</div>
                        {s.last_message_preview && (
                          <div className="text-[11px] text-slate-500 italic truncate max-w-xs mt-0.5">
                            &quot;{s.last_message_preview}&quot;
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <RiskBadge category={s.verification_status} size="sm" />
                      </td>
                      <td className="px-4 py-3 font-mono">
                        <div className="text-rose-400 font-semibold">{s.dangerous_messages_count} Dangerous</div>
                        <div className="text-amber-400">{s.suspicious_messages_count} Suspicious</div>
                        <div className="text-slate-500 text-[10px]">{s.total_messages_count} Total</div>
                      </td>
                      <td className="px-4 py-3 text-slate-400 max-w-xs truncate">
                        <div>{s.reason || 'Unverified sender detection'}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">
                          Risk: {s.risk_score}/100
                        </div>
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
                            onClick={() => handleTrust(s.sender_id)}
                            disabled={actionLoading}
                            className="p-1.5 rounded bg-emerald-950/40 text-emerald-400 hover:bg-emerald-900/60 transition-colors"
                            title="Mark as Trusted Sender"
                          >
                            <ShieldCheck className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => handleBlock(s.sender_id)}
                            disabled={actionLoading}
                            className="p-1.5 rounded bg-rose-950/40 text-rose-400 hover:bg-rose-900/60 transition-colors"
                            title="Block Sender"
                          >
                            <Ban className="w-3.5 h-3.5" />
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

