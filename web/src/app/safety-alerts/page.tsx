'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { UserShell } from '@/components/layout/UserShell';
import { api } from '@/services/api';
import { SafetyAlertItem } from '@/types';
import {
  AlertTriangle,
  ShieldCheck,
  ShieldAlert,
  CheckCircle,
  Clock,
  ExternalLink,
  Ban,
  Search,
  RefreshCw,
  Eye,
} from 'lucide-react';
import { RiskBadge, StatusBadge } from '@/components/ui/Badge';
import { takeUserThreatAction } from '@/services/threatFirestore';
import { useAuth } from '@/context/AuthContext';

export default function SafetyAlertsPage() {
  const { user } = useAuth();
  const [alerts, setAlerts] = useState<SafetyAlertItem[]>([]);
  const [selectedTab, setSelectedTab] = useState<'ALL' | 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED'>('ALL');
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const fetchAlerts = async () => {
    try {
      setIsLoading(true);
      const data = await api.alerts.getAlerts(selectedTab === 'ALL' ? undefined : selectedTab);
      setAlerts(data);
    } catch (err) {
      console.error('Failed to load safety alerts:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchAlerts();
  }, [selectedTab]);

  const handleResolve = async (alertItem: SafetyAlertItem) => {
    const notes = prompt('Resolution notes (e.g., Verified legitimate banking alert):', 'Resolved by user review');
    if (notes === null) return;
    try {
      setActionLoading(true);
      await api.alerts.resolveAlert(alertItem.id, notes);
      // Synchronize to Firestore if threat_event_id is linked and user is authenticated
      const userUid = user?.firebase_uid || user?.id;
      if (userUid && alertItem.threat_event_id) {
        try {
          await takeUserThreatAction(userUid, alertItem.threat_event_id, 'RESOLVED');
        } catch (fErr) {
          console.warn('Firestore sync notice:', fErr);
        }
      }
      await fetchAlerts();
    } catch (err: any) {
      window.alert(`Error resolving alert: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleAcknowledge = async (alertId: string) => {
    try {
      setActionLoading(true);
      await api.alerts.acknowledgeAlert(alertId);
      await fetchAlerts();
    } catch (err: any) {
      alert(`Error acknowledging alert: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleBlockSender = async (sender: string) => {
    const reason = prompt(`Reason for blocking ${sender}:`, 'Malicious SMS sender flagged in safety alert');
    if (!reason) return;
    try {
      setActionLoading(true);
      await api.security.blockSender({ sender_id: sender, reason });
      alert(`Sender ${sender} blocked successfully.`);
      await fetchAlerts();
    } catch (err: any) {
      alert(`Error blocking sender: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const filtered = alerts.filter(
    (a) =>
      (a.title && a.title.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (a.sender && a.sender.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (a.message_preview && a.message_preview.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (a.detection_reason && a.detection_reason.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  return (
    <UserShell>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">Important Safety Alerts</h1>
          <p className="text-sm text-slate-400 mt-1">
            Active threat advisories, critical scam warnings, and urgent security notifications requiring action.
          </p>
        </div>

        {/* Tabs Row */}
        <div className="flex border-b border-slate-800 space-x-2">
          {(['ALL', 'ACTIVE', 'ACKNOWLEDGED', 'RESOLVED'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setSelectedTab(tab)}
              className={`px-4 py-2.5 text-xs sm:text-sm font-semibold rounded-t-lg transition-all border-b-2 ${
                selectedTab === tab
                  ? 'border-cyan-400 text-cyan-400 bg-slate-900/60'
                  : 'border-transparent text-slate-400 hover:text-slate-200 hover:bg-slate-900/30'
              }`}
            >
              {tab === 'ALL' ? 'All Alerts' : tab.charAt(0) + tab.slice(1).toLowerCase()}
            </button>
          ))}
        </div>

        {/* Controls */}
        <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
          <div className="relative flex-1 max-w-md">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search alerts by sender, keyword, or advisory..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 bg-slate-900 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-cyan-500"
            />
          </div>

          <button
            onClick={fetchAlerts}
            disabled={isLoading}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors self-end sm:self-auto"
            title="Refresh"
          >
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>

        {/* Alerts List */}
        {isLoading ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/50 rounded-xl border border-slate-800">
            <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-cyan-400" />
            <p className="text-sm">Loading safety alerts...</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="p-12 text-center text-slate-400 bg-slate-900/40 rounded-xl border border-slate-800">
            <ShieldCheck className="w-10 h-10 mx-auto mb-3 text-emerald-500" />
            <p className="text-base font-semibold text-white">No Safety Alerts in this Category</p>
            <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
              Your system has no pending advisories for the selected filter. Resolved alerts can be viewed in the Resolved tab.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4">
            {filtered.map((alert) => (
              <div
                key={alert.id}
                className={`p-5 rounded-xl border transition-all space-y-3 ${
                  alert.status === 'RESOLVED'
                    ? 'bg-slate-900/40 border-slate-800/80 opacity-80'
                    : alert.status === 'ACKNOWLEDGED'
                    ? 'bg-slate-900/70 border-sky-900/40'
                    : 'bg-slate-900/90 border-rose-900/40 shadow-sm'
                }`}
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center space-x-2">
                    <RiskBadge category={alert.threat_category} size="sm" />
                    <span className="text-sm font-bold text-white">{alert.title}</span>
                  </div>
                  <div className="flex items-center space-x-2 font-mono text-xs">
                    <span className="text-rose-400 font-bold">Severity: {alert.severity}</span>
                    <span className="text-slate-500 font-normal">|</span>
                    <span className="text-amber-400 font-bold">Risk: {alert.risk_score}/100</span>
                    <StatusBadge status={alert.status} />
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-400">
                  {alert.sender && (
                    <div>
                      <span className="text-slate-500 font-medium">Sender: </span>
                      <span className="text-slate-200 font-mono font-semibold">{alert.sender}</span>
                    </div>
                  )}
                  <div>
                    <span className="text-slate-500 font-medium">Triggered: </span>
                    <span>{new Date(alert.created_at).toLocaleString()}</span>
                  </div>
                  {alert.resolved_at && (
                    <div className="text-emerald-400">
                      <span className="text-slate-500 font-medium">Resolved: </span>
                      <span>{new Date(alert.resolved_at).toLocaleString()}</span>
                    </div>
                  )}
                </div>

                {alert.message_preview && (
                  <div className="p-3 bg-slate-950/80 rounded-lg border border-slate-800 text-xs text-slate-300 font-mono whitespace-pre-wrap break-words">
                    {alert.message_preview}
                  </div>
                )}

                <div className="text-xs text-slate-400">
                  <span className="text-slate-500 font-medium">Detection Context: </span>
                  {alert.detection_reason || 'AI identified deceptive smishing or quishing patterns'}
                </div>

                {alert.resolution_notes && (
                  <div className="text-xs text-emerald-400 bg-emerald-950/20 p-2 rounded border border-emerald-900/40">
                    <span className="font-semibold text-emerald-300">Resolution Note: </span>
                    {alert.resolution_notes}
                  </div>
                )}

                <div className="pt-2 border-t border-slate-800 flex flex-wrap items-center justify-between gap-2">
                  {alert.threat_event_id ? (
                    <Link
                      href={`/messages/${alert.threat_event_id}`}
                      className="inline-flex items-center space-x-1 text-xs text-cyan-400 hover:text-cyan-300 font-semibold"
                    >
                      <span>View Message Details</span>
                      <ExternalLink className="w-3 h-3" />
                    </Link>
                  ) : <div />}

                  <div className="flex items-center space-x-2">
                    {alert.sender && (
                      <button
                        onClick={() => handleBlockSender(alert.sender!)}
                        disabled={actionLoading}
                        className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-lg bg-rose-950/40 hover:bg-rose-900/60 text-rose-400 text-xs font-semibold transition-colors"
                      >
                        <Ban className="w-3.5 h-3.5" />
                        <span>Block Sender</span>
                      </button>
                    )}
                    {alert.status === 'ACTIVE' && (
                      <button
                        onClick={() => handleAcknowledge(alert.id)}
                        disabled={actionLoading}
                        className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-sky-400 text-xs font-semibold transition-colors"
                      >
                        <Eye className="w-3.5 h-3.5" />
                        <span>Acknowledge</span>
                      </button>
                    )}
                    {alert.status !== 'RESOLVED' && (
                      <button
                        onClick={() => handleResolve(alert)}
                        disabled={actionLoading}
                        className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-lg bg-emerald-950/50 hover:bg-emerald-900/70 text-emerald-400 border border-emerald-800/40 text-xs font-bold transition-colors"
                      >
                        <CheckCircle className="w-3.5 h-3.5" />
                        <span>Resolve Alert</span>
                      </button>
                    )}
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
