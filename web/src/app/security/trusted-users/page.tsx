'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { UserShell } from '@/components/layout/UserShell';
import { SecurityTabs } from '@/components/security/SecurityTabs';
import { api } from '@/services/api';
import { useAuth } from '@/context/AuthContext';
import { setSenderTrustStatus, setSenderBlockStatus } from '@/services/threatFirestore';
import { SenderSecurityItem } from '@/types';
import {
  ShieldCheck,
  ShieldAlert,
  AlertTriangle,
  UserCheck,
  ExternalLink,
  Trash2,
  Ban,
  Search,
  Plus,
  RefreshCw,
} from 'lucide-react';
import { RiskBadge, StatusBadge } from '@/components/ui/Badge';

export default function TrustedUsersPage() {
  const { user } = useAuth();
  const currentUid = user?.firebase_uid || user?.id;
  const [senders, setSenders] = useState<SenderSecurityItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [showAddModal, setShowAddModal] = useState(false);
  const [newSenderId, setNewSenderId] = useState('');
  const [newDisplayName, setNewDisplayName] = useState('');
  const [newReason, setNewReason] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const fetchTrustedSenders = async () => {
    try {
      setIsLoading(true);
      const data = await api.security.getTrustedSenders();
      setSenders(data);
    } catch (err) {
      console.error('Failed to load trusted senders:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchTrustedSenders();
  }, []);

  const handleRemove = async (senderId: string) => {
    if (!confirm(`Are you sure you want to remove ${senderId} from your trusted list?`)) return;
    try {
      setActionLoading(true);
      await api.security.removeTrustedSender(senderId);
      if (currentUid) {
        await setSenderTrustStatus(currentUid, senderId, false).catch(() => {});
      }
      await fetchTrustedSenders();
    } catch (err: any) {
      alert(`Error removing sender: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleBlock = async (senderId: string) => {
    const reason = prompt(`Reason for blocking ${senderId}:`, 'User blocked this sender');
    if (!reason) return;
    try {
      setActionLoading(true);
      await api.security.blockSender({ sender_id: senderId, reason });
      if (currentUid) {
        await setSenderBlockStatus(currentUid, senderId, true, reason).catch(() => {});
      }
      await fetchTrustedSenders();
    } catch (err: any) {
      alert(`Error blocking sender: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleAddSender = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newSenderId.trim()) return;
    try {
      setActionLoading(true);
      await api.security.trustSender({
        sender_id: newSenderId.trim(),
        display_name: newDisplayName.trim() || undefined,
        reason: newReason.trim() || 'Manually added by user',
      });
      setShowAddModal(false);
      setNewSenderId('');
      setNewDisplayName('');
      setNewReason('');
      await fetchTrustedSenders();
    } catch (err: any) {
      alert(`Error adding trusted sender: ${err.message}`);
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
          <h1 className="text-2xl font-bold text-white tracking-tight">Security & Trust Management</h1>
          <p className="text-sm text-slate-400 mt-1">
            Manage your verified senders, analyze threat reputations, and configure isolation preferences.
          </p>
        </div>

        <SecurityTabs />

        {/* Security Invariant Banner */}
        <div className="p-4 rounded-xl bg-cyan-950/30 border border-cyan-800/40 flex items-start space-x-3.5">
          <ShieldCheck className="w-5 h-5 text-cyan-400 shrink-0 mt-0.5" />
          <div className="text-xs text-slate-300 space-y-1">
            <p className="font-semibold text-cyan-300">Continuous AI Protection Guarantee</p>
            <p className="text-slate-400 leading-relaxed">
              Marking a sender as trusted verifies sender authenticity, but <strong>does not bypass message safety scans</strong>.
              Squish continuously inspects every individual SMS for spoofing indicators, fraudulent links, and OTP harvesting requests.
            </p>
          </div>
        </div>

        {/* Action Controls */}
        <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
          <div className="relative flex-1 max-w-md">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search trusted senders..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 bg-slate-900 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-cyan-500"
            />
          </div>

          <div className="flex items-center space-x-2">
            <button
              onClick={fetchTrustedSenders}
              disabled={isLoading}
              className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors"
              title="Refresh"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            </button>
            <button
              onClick={() => setShowAddModal(true)}
              className="inline-flex items-center space-x-1.5 px-3.5 py-2 rounded-lg bg-cyan-500 hover:bg-cyan-400 text-slate-950 text-xs font-bold transition-colors shadow-sm"
            >
              <Plus className="w-4 h-4" />
              <span>Trust New Sender</span>
            </button>
          </div>
        </div>

        {/* Trusted Senders Table / Cards */}
        {isLoading ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/50 rounded-xl border border-slate-800">
            <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-cyan-400" />
            <p className="text-sm">Loading trusted senders...</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/40 rounded-xl border border-slate-800">
            <ShieldCheck className="w-10 h-10 mx-auto mb-3 text-slate-500" />
            <p className="text-base font-semibold text-white">No Trusted Senders Found</p>
            <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
              You haven&apos;t marked any senders as trusted yet. Use the button above or mark messages from trusted banks as safe in your history.
            </p>
          </div>
        ) : (
          <div className="bg-slate-900/60 rounded-xl border border-slate-800 overflow-hidden shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-950/60 border-b border-slate-800 text-slate-400 uppercase font-semibold">
                  <tr>
                    <th className="px-4 py-3">Sender Details</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Messages</th>
                    <th className="px-4 py-3">Date Added</th>
                    <th className="px-4 py-3">Reason</th>
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
                        <span className="text-emerald-400 font-bold">{s.legitimate_messages_count}</span>
                        <span className="text-slate-500"> / {s.total_messages_count} Total</span>
                      </td>
                      <td className="px-4 py-3 text-slate-400">
                        {s.trusted_at ? new Date(s.trusted_at).toLocaleDateString() : 'N/A'}
                        <div className="text-[10px] text-slate-500">by {s.marked_by}</div>
                      </td>
                      <td className="px-4 py-3 text-slate-400 max-w-xs truncate" title={s.reason || ''}>
                        {s.reason || 'Verified'}
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
                            onClick={() => handleBlock(s.sender_id)}
                            disabled={actionLoading}
                            className="p-1.5 rounded bg-rose-950/40 text-rose-400 hover:bg-rose-900/60 transition-colors"
                            title="Block Sender"
                          >
                            <Ban className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => handleRemove(s.sender_id)}
                            disabled={actionLoading}
                            className="p-1.5 rounded bg-slate-800 text-slate-400 hover:text-rose-400 hover:bg-slate-700 transition-colors"
                            title="Remove from Trusted List"
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
          </div>
        )}

        {/* Add Modal */}
        {showAddModal && (
          <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-md w-full p-6 space-y-4 shadow-xl">
              <h2 className="text-lg font-bold text-white">Trust a Sender</h2>
              <form onSubmit={handleAddSender} className="space-y-4 text-xs">
                <div>
                  <label className="block text-slate-300 font-semibold mb-1">Sender ID / Phone Number *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. HDFCBK or +919876543210"
                    value={newSenderId}
                    onChange={(e) => setNewSenderId(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-white focus:outline-none focus:border-cyan-500"
                  />
                </div>
                <div>
                  <label className="block text-slate-300 font-semibold mb-1">Display Name (Optional)</label>
                  <input
                    type="text"
                    placeholder="e.g. HDFC Bank Alerts"
                    value={newDisplayName}
                    onChange={(e) => setNewDisplayName(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-white focus:outline-none focus:border-cyan-500"
                  />
                </div>
                <div>
                  <label className="block text-slate-300 font-semibold mb-1">Verification Reason</label>
                  <input
                    type="text"
                    placeholder="e.g. Official bank transaction notifications"
                    value={newReason}
                    onChange={(e) => setNewReason(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-white focus:outline-none focus:border-cyan-500"
                  />
                </div>
                <div className="flex justify-end space-x-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setShowAddModal(false)}
                    className="px-4 py-2 rounded-lg bg-slate-800 text-slate-300 hover:bg-slate-700"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={actionLoading}
                    className="px-4 py-2 rounded-lg bg-cyan-500 text-slate-950 font-bold hover:bg-cyan-400"
                  >
                    {actionLoading ? 'Saving...' : 'Add to Trusted'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </UserShell>
  );
}

