'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { AdminShell } from '@/components/layout/AdminShell';
import { Card } from '@/components/ui/Card';
import {
  IncidentSeverityBadge,
  IncidentStatusBadge,
  RiskBadge,
  EventTypeBadge,
} from '@/components/ui/Badge';
import { LoadingSpinner } from '@/components/ui/LoadingState';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { adminApi } from '@/services/api';
import {
  IncidentDetail,
  CorrelationSuggestion,
  IncidentSeverity,
  IncidentStatus,
  IncidentEvent,
  IncidentNote,
  LinkedThreatSummary,
  User,
} from '@/types';
import {
  AlertOctagon,
  ShieldAlert,
  ShieldCheck,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Clock,
  ArrowLeft,
  UserCheck,
  FolderGit2,
  FileText,
  PlusCircle,
  Trash2,
  Link2,
  RefreshCw,
  Send,
  History,
  Copy,
  Check,
  Sparkles,
  ExternalLink,
  Tag,
  Calendar,
  Layers,
  ArrowRight,
} from 'lucide-react';

export default function IncidentDetailPage() {
  const params = useParams();
  const router = useRouter();
  const incidentId = params?.id as string;

  const [incident, setIncident] = useState<IncidentDetail | null>(null);
  const [suggestions, setSuggestions] = useState<CorrelationSuggestion[]>([]);
  const [adminUsers, setAdminUsers] = useState<User[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<boolean>(false);

  // Note State
  const [noteContent, setNoteContent] = useState<string>('');
  const [noteType, setNoteType] = useState<string>('OBSERVATION');
  const [isSubmittingNote, setIsSubmittingNote] = useState<boolean>(false);

  // Status Modal State
  const [showStatusModal, setShowStatusModal] = useState<boolean>(false);
  const [targetStatus, setTargetStatus] = useState<IncidentStatus>('INVESTIGATING');
  const [statusReason, setStatusReason] = useState<string>('');
  const [resolutionSummary, setResolutionSummary] = useState<string>('');
  const [isUpdatingStatus, setIsUpdatingStatus] = useState<boolean>(false);

  // Severity Modal State
  const [showSeverityModal, setShowSeverityModal] = useState<boolean>(false);
  const [newSeverity, setNewSeverity] = useState<IncidentSeverity>('HIGH');
  const [severityReason, setSeverityReason] = useState<string>('');
  const [isUpdatingSeverity, setIsUpdatingSeverity] = useState<boolean>(false);

  // Assign Modal State
  const [showAssignModal, setShowAssignModal] = useState<boolean>(false);
  const [selectedAdminId, setSelectedAdminId] = useState<string>('');
  const [assignmentNote, setAssignmentNote] = useState<string>('');
  const [isAssigning, setIsAssigning] = useState<boolean>(false);

  // Linking State
  const [isLinkingId, setIsLinkingId] = useState<string | null>(null);
  const [isUnlinkingId, setIsUnlinkingId] = useState<string | null>(null);

  const fetchIncidentData = useCallback(async () => {
    if (!incidentId) return;
    setError(null);
    try {
      const [detailRes, suggRes] = await Promise.all([
        adminApi.incidents.get(incidentId),
        adminApi.incidents.getCorrelationSuggestions(incidentId),
      ]);
      setIncident(detailRes);
      setSuggestions(suggRes.suggestions || []);
    } catch (err: any) {
      setError(err.message || 'Failed to load incident investigation details.');
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [incidentId]);

  const fetchAdmins = useCallback(async () => {
    try {
      const res = await adminApi.users.list(1, 100);
      const admins = (res.items || []).filter((u: User) => u.role === 'ADMIN');
      setAdminUsers(admins);
    } catch (e) {
      // Non-critical, fallback to manual input or empty list
    }
  }, []);

  useEffect(() => {
    fetchIncidentData();
    fetchAdmins();
  }, [fetchIncidentData, fetchAdmins]);

  const handleRefresh = () => {
    setIsRefreshing(true);
    setActionSuccess(null);
    fetchIncidentData();
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(true);
    setTimeout(() => setCopiedId(false), 2000);
  };

  // 1. Status Update Handler
  const handleStatusChange = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!incident) return;
    if (targetStatus === 'RESOLVED' && !resolutionSummary.trim()) {
      setError('Resolution summary is mandatory when resolving an incident.');
      return;
    }
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await adminApi.incidents.updateStatus(incident.id, {
        status: targetStatus,
        resolution_summary: resolutionSummary.trim() || undefined,
        reason: statusReason.trim() || undefined,
      });
      setIncident(updated);
      setShowStatusModal(false);
      setStatusReason('');
      setResolutionSummary('');
      setActionSuccess(`Incident status transitioned to ${targetStatus}.`);
    } catch (err: any) {
      setError(err.message || 'Failed to transition incident status.');
    } finally {
      setIsUpdatingStatus(false);
    }
  };

  // 2. Severity Update Handler
  const handleSeverityChange = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!incident) return;
    setIsUpdatingSeverity(true);
    setError(null);
    try {
      const updated = await adminApi.incidents.updateSeverity(incident.id, {
        severity: newSeverity,
        reason: severityReason.trim() || undefined,
      });
      setIncident(updated);
      setShowSeverityModal(false);
      setSeverityReason('');
      setActionSuccess(`Incident severity adjusted to ${newSeverity}.`);
    } catch (err: any) {
      setError(err.message || 'Failed to update severity.');
    } finally {
      setIsUpdatingSeverity(false);
    }
  };

  // 3. Assignment Handler
  const handleAssignAdmin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!incident) return;
    setIsAssigning(true);
    setError(null);
    try {
      const updated = await adminApi.incidents.assign(incident.id, {
        assigned_admin_id: selectedAdminId || null,
        note: assignmentNote.trim() || undefined,
      });
      setIncident(updated);
      setShowAssignModal(false);
      setAssignmentNote('');
      setActionSuccess(
        selectedAdminId
          ? 'Incident assigned to operator.'
          : 'Incident unassigned.'
      );
    } catch (err: any) {
      setError(err.message || 'Failed to update incident assignment.');
    } finally {
      setIsAssigning(false);
    }
  };

  // 4. Add Investigation Note Handler
  const handleAddNote = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!incident || !noteContent.trim()) return;
    setIsSubmittingNote(true);
    setError(null);
    try {
      const updated = await adminApi.incidents.addNote(incident.id, {
        content: noteContent.trim(),
        note_type: noteType,
      });
      setIncident(updated);
      setNoteContent('');
      setActionSuccess('Investigation note recorded in audit log.');
    } catch (err: any) {
      setError(err.message || 'Failed to save note.');
    } finally {
      setIsSubmittingNote(false);
    }
  };

  // 5. Link Suggested Threat
  const handleLinkThreat = async (
    threatEventId: string,
    signal: string,
    confidence: string
  ) => {
    if (!incident) return;
    setIsLinkingId(threatEventId);
    setError(null);
    try {
      const relType = confidence === 'CONFIRMED' ? 'CONFIRMED' : 'LIKELY';
      const updated = await adminApi.incidents.linkThreat(incident.id, {
        threat_event_id: threatEventId,
        relationship_type: relType,
        correlation_signal: signal,
      });
      setIncident(updated);
      // Remove from suggestions list
      setSuggestions((prev) => prev.filter((s) => s.threat_event_id !== threatEventId));
      setActionSuccess('Threat event linked to incident investigation.');
    } catch (err: any) {
      setError(err.message || 'Failed to link threat event.');
    } finally {
      setIsLinkingId(null);
    }
  };

  // 6. Unlink Threat
  const handleUnlinkThreat = async (threatEventId: string) => {
    if (!incident) return;
    if (!window.confirm('Are you sure you want to decouple this threat event from the incident?')) {
      return;
    }
    setIsUnlinkingId(threatEventId);
    setError(null);
    try {
      const updated = await adminApi.incidents.unlinkThreat(incident.id, threatEventId);
      setIncident(updated);
      setActionSuccess('Threat event decoupled from incident.');
      // Refresh suggestions
      const suggRes = await adminApi.incidents.getCorrelationSuggestions(incident.id);
      setSuggestions(suggRes.suggestions || []);
    } catch (err: any) {
      setError(err.message || 'Failed to unlink threat event.');
    } finally {
      setIsUnlinkingId(null);
    }
  };

  // Legal transitions map according to backend rule
  const legalTransitions: Record<string, IncidentStatus[]> = {
    NEW: ['TRIAGED', 'INVESTIGATING', 'CLOSED'],
    TRIAGED: ['INVESTIGATING', 'CLOSED'],
    INVESTIGATING: ['CONTAINED', 'RESOLVED', 'CLOSED'],
    CONTAINED: ['RESOLVED', 'INVESTIGATING'],
    RESOLVED: ['CLOSED', 'INVESTIGATING'],
    CLOSED: ['INVESTIGATING'],
  };

  const allowedNextStatuses = incident
    ? legalTransitions[incident.status] || []
    : [];

  return (
    <AdminShell>
      <div className="space-y-6">
        {/* Navigation Breadcrumb & Actions */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div className="flex items-center space-x-3">
            <Link
              href="/admin/incidents"
              className="p-1.5 rounded-lg bg-slate-900 border border-slate-800 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
            </Link>
            <div>
              <div className="flex items-center space-x-2">
                <span className="text-[11px] font-mono text-slate-500 uppercase tracking-wider">
                  Incident Dossier
                </span>
                <span className="text-slate-600">/</span>
                <span className="font-mono text-xs text-amber-400 font-semibold">
                  {incident ? incident.id.substring(0, 8) : 'Loading...'}
                </span>
                <button
                  onClick={() => incident && copyToClipboard(incident.id)}
                  title="Copy full Incident UUID"
                  className="p-1 text-slate-500 hover:text-slate-300 transition-colors"
                >
                  {copiedId ? (
                    <Check className="w-3 h-3 text-emerald-400" />
                  ) : (
                    <Copy className="w-3 h-3" />
                  )}
                </button>
              </div>
              <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 mt-0.5">
                {incident?.title || 'Loading Incident...'}
              </h1>
            </div>
          </div>

          <div className="flex items-center space-x-2">
            <button
              onClick={handleRefresh}
              disabled={isRefreshing || isLoading}
              className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-medium text-slate-300 hover:text-white hover:bg-slate-800 transition-colors disabled:opacity-50"
            >
              <RefreshCw
                className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin text-amber-400' : ''}`}
              />
              <span>{isRefreshing ? 'Refreshing...' : 'Refresh'}</span>
            </button>
          </div>
        </div>

        {/* Global Feedback Banners */}
        {error && (
          <ErrorState
            title="Investigation Action Failed"
            message={error}
            onRetry={fetchIncidentData}
          />
        )}

        {actionSuccess && (
          <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-xs text-emerald-300 flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-400" />
              <span>{actionSuccess}</span>
            </div>
            <button
              onClick={() => setActionSuccess(null)}
              className="text-slate-400 hover:text-white text-xs"
            >
              Dismiss
            </button>
          </div>
        )}

        {isLoading ? (
          <div className="py-20 flex justify-center">
            <LoadingSpinner message="Retrieving secure incident dossier and threat intelligence correlation data..." />
          </div>
        ) : !incident ? (
          <EmptyState
            icon={AlertOctagon}
            title="Incident Dossier Not Found"
            description="The requested security incident identifier does not exist or has been archived."
          />
        ) : (
          <>
            {/* Top Overview & Lifecycle Status Card */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              {/* Primary Metadata Dossier */}
              <Card className="lg:col-span-2 bg-slate-900/90 border-slate-800 p-5 space-y-4">
                <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-800">
                  <div className="flex flex-wrap items-center gap-2">
                    <IncidentSeverityBadge severity={incident.severity} size="md" />
                    <IncidentStatusBadge status={incident.status} />
                    <span className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-mono bg-slate-800 text-cyan-300 border border-slate-700">
                      {incident.category}
                    </span>
                  </div>

                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => {
                        setNewSeverity(incident.severity);
                        setShowSeverityModal(true);
                      }}
                      className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-xs font-medium transition-colors"
                    >
                      Change Severity
                    </button>
                    <button
                      onClick={() => {
                        setSelectedAdminId(incident.assigned_admin_id || '');
                        setShowAssignModal(true);
                      }}
                      className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-xs font-medium transition-colors"
                    >
                      {incident.assigned_admin_id ? 'Reassign' : 'Assign Lead'}
                    </button>
                  </div>
                </div>

                {/* Description */}
                <div>
                  <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    Investigation Hypothesis & Context
                  </h3>
                  <p className="text-sm text-slate-200 mt-1 whitespace-pre-wrap leading-relaxed">
                    {incident.description || 'No initial hypothesis recorded at time of creation.'}
                  </p>
                </div>

                {/* Resolution Summary (If Present) */}
                {incident.resolution_summary && (
                  <div className="p-3 bg-emerald-950/30 border border-emerald-500/30 rounded-lg">
                    <div className="text-xs font-semibold text-emerald-400 uppercase tracking-wider flex items-center space-x-1.5">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>Resolution Summary</span>
                    </div>
                    <p className="text-xs text-slate-200 mt-1 whitespace-pre-wrap">
                      {incident.resolution_summary}
                    </p>
                    {incident.resolved_at && (
                      <div className="text-[10px] text-slate-400 mt-1 font-mono">
                        Resolved on {new Date(incident.resolved_at).toLocaleString()}
                      </div>
                    )}
                  </div>
                )}

                {/* Tags Metadata */}
                {incident.metadata_json &&
                  Array.isArray(incident.metadata_json.tags) &&
                  incident.metadata_json.tags.length > 0 && (
                    <div>
                      <div className="text-[11px] font-semibold text-slate-400 mb-1.5 flex items-center space-x-1">
                        <Tag className="w-3 h-3" />
                        <span>Campaign Tags</span>
                      </div>
                      <div className="flex flex-wrap gap-1.5">
                        {(incident.metadata_json.tags as string[]).map((tag, idx) => (
                          <span
                            key={idx}
                            className="inline-flex items-center px-2 py-0.5 rounded text-xs font-mono bg-slate-800 text-amber-300 border border-slate-700"
                          >
                            #{tag}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}

                {/* Key Lifecycle Timestamps */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-3 border-t border-slate-800 text-xs">
                  <div>
                    <span className="text-[10px] text-slate-500 block uppercase font-mono">Created</span>
                    <span className="text-slate-300 font-mono text-[11px]">
                      {new Date(incident.created_at).toLocaleString()}
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-500 block uppercase font-mono">Triaged</span>
                    <span className="text-slate-300 font-mono text-[11px]">
                      {incident.triaged_at ? new Date(incident.triaged_at).toLocaleString() : 'Pending'}
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-500 block uppercase font-mono">Contained</span>
                    <span className="text-slate-300 font-mono text-[11px]">
                      {incident.contained_at ? new Date(incident.contained_at).toLocaleString() : 'Pending'}
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-500 block uppercase font-mono">Closed</span>
                    <span className="text-slate-300 font-mono text-[11px]">
                      {incident.closed_at ? new Date(incident.closed_at).toLocaleString() : 'Open'}
                    </span>
                  </div>
                </div>
              </Card>

              {/* Lifecycle Actions & Operator Status */}
              <Card className="bg-slate-900/90 border-slate-800 p-5 space-y-4">
                <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider pb-2 border-b border-slate-800">
                  Response Workflow & Ownership
                </h3>

                {/* Operator Assignment Display */}
                <div className="p-3 bg-slate-950 border border-slate-800 rounded-lg">
                  <div className="text-[10px] text-slate-500 uppercase font-mono">Assigned Lead</div>
                  <div className="flex items-center space-x-2 mt-1">
                    <UserCheck className="w-4 h-4 text-emerald-400" />
                    <div>
                      <div className="text-xs font-semibold text-slate-200">
                        {incident.assigned_admin_name || incident.assigned_admin_email || 'Unassigned'}
                      </div>
                      {incident.assigned_admin_email && (
                        <div className="text-[10px] text-slate-500 font-mono">
                          {incident.assigned_admin_email}
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {/* Legal Lifecycle State Actions */}
                <div className="space-y-2">
                  <div className="text-[10px] text-slate-500 uppercase font-mono">
                    Authorized Status Transitions
                  </div>
                  {allowedNextStatuses.length === 0 ? (
                    <div className="text-xs text-slate-500 italic">
                      No further status transitions permitted from {incident.status}.
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 gap-2">
                      {allowedNextStatuses.map((st) => (
                        <button
                          key={st}
                          onClick={() => {
                            setTargetStatus(st);
                            setShowStatusModal(true);
                          }}
                          className={`w-full py-2 px-3 rounded-lg text-xs font-semibold flex items-center justify-between border transition-all ${
                            st === 'RESOLVED'
                              ? 'bg-emerald-600/20 border-emerald-500/30 text-emerald-300 hover:bg-emerald-600/30'
                              : st === 'CONTAINED'
                              ? 'bg-purple-600/20 border-purple-500/30 text-purple-300 hover:bg-purple-600/30'
                              : st === 'INVESTIGATING'
                              ? 'bg-amber-600/20 border-amber-500/30 text-amber-300 hover:bg-amber-600/30'
                              : st === 'CLOSED'
                              ? 'bg-slate-800 border-slate-700 text-slate-300 hover:bg-slate-700'
                              : 'bg-blue-600/20 border-blue-500/30 text-blue-300 hover:bg-blue-600/30'
                          }`}
                        >
                          <span>Move to {st}</span>
                          <ArrowRight className="w-3.5 h-3.5" />
                        </button>
                      ))}
                    </div>
                  )}
                </div>

                {/* Correlation Metrics Quick Summary */}
                <div className="pt-3 border-t border-slate-800 space-y-1.5 text-xs text-slate-400">
                  <div className="flex justify-between">
                    <span>Correlated Threats:</span>
                    <span className="font-mono text-slate-200 font-semibold">
                      {incident.threat_links?.length || 0}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span>Investigation Notes:</span>
                    <span className="font-mono text-slate-200 font-semibold">
                      {incident.notes?.length || 0}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span>Audit Events:</span>
                    <span className="font-mono text-slate-200 font-semibold">
                      {incident.events?.length || 0}
                    </span>
                  </div>
                </div>
              </Card>
            </div>

            {/* Middle Section: Linked Threats & Automated Correlation Candidates */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              {/* Linked Threat Detections (Evidence) */}
              <div className="lg:col-span-2 space-y-4">
                <Card
                  title={`Linked Threat Records (${incident.threat_links?.length || 0})`}
                  subtitle="Smishing and quishing telemetry events providing evidence for this investigation"
                  action={
                    <span className="text-[11px] font-mono text-slate-500">
                      Decoupled from Android detection
                    </span>
                  }
                >
                  {(!incident.threat_links || incident.threat_links.length === 0) ? (
                    <div className="py-8 text-center text-xs text-slate-500">
                      No threat detections linked yet. Review suggestions below to correlate related evidence.
                    </div>
                  ) : (
                    <div className="divide-y divide-slate-800/80">
                      {incident.threat_links.map((link) => (
                        <div key={link.id} className="py-3.5 first:pt-0 last:pb-0 space-y-2">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <div className="flex items-center space-x-2">
                              <EventTypeBadge type={link.event_type} />
                              <RiskBadge
                                category={link.threat_category}
                                score={link.risk_score}
                                size="sm"
                              />
                              <span className="font-mono text-xs text-slate-300 font-semibold">
                                Score: {link.risk_score}/100
                              </span>
                              <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono bg-slate-800 text-amber-300 border border-slate-700">
                                {link.relationship_type}
                              </span>
                            </div>

                            <button
                              onClick={() => handleUnlinkThreat(link.id)}
                              disabled={isUnlinkingId === link.id}
                              title="Unlink threat event from this incident"
                              className="inline-flex items-center space-x-1 px-2 py-0.5 rounded bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 border border-rose-500/20 text-[11px] transition-colors disabled:opacity-50"
                            >
                              <Trash2 className="w-3 h-3" />
                              <span>{isUnlinkingId === link.id ? 'Unlinking...' : 'Unlink'}</span>
                            </button>
                          </div>

                          {/* Indicator & Domain Evidence */}
                          <div className="text-xs space-y-1">
                            {link.domain && (
                              <div className="flex items-center space-x-1.5 text-slate-300">
                                <span className="text-slate-500 font-mono text-[10px]">DOMAIN:</span>
                                <span className="font-mono text-cyan-300">{link.domain}</span>
                              </div>
                            )}
                            {link.url && (
                              <div className="flex items-start space-x-1.5 text-slate-400">
                                <span className="text-slate-500 font-mono text-[10px] mt-0.5">URL:</span>
                                <span className="font-mono text-[11px] text-slate-300 break-all select-all">
                                  {link.url}
                                </span>
                              </div>
                            )}
                          </div>

                          {/* Reasons & Recommended Action */}
                          {link.reasons && link.reasons.length > 0 && (
                            <div className="flex flex-wrap gap-1 mt-1">
                              {link.reasons.map((r, idx) => (
                                <span
                                  key={idx}
                                  className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] bg-slate-950 text-slate-400 border border-slate-800"
                                >
                                  {r}
                                </span>
                              ))}
                            </div>
                          )}

                          <div className="flex items-center justify-between text-[10px] text-slate-500 font-mono pt-1">
                            <span>
                              Correlation Signal:{' '}
                              <strong className="text-slate-400">
                                {link.correlation_signal || 'MANUAL_LINK'}
                              </strong>
                            </span>
                            <span>
                              Detected {new Date(link.timestamp).toLocaleString()}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </Card>

                {/* Conservative Threat Correlation Suggestions */}
                <Card
                  title={`Correlation Candidates (${suggestions.length})`}
                  subtitle="Unlinked threat detections sharing matching URLs, malicious domains, or campaign signatures"
                  action={
                    <span className="inline-flex items-center space-x-1 text-xs text-amber-400 font-medium">
                      <Sparkles className="w-3.5 h-3.5" />
                      <span>Heuristic Provenance</span>
                    </span>
                  }
                >
                  {suggestions.length === 0 ? (
                    <div className="py-6 text-center text-xs text-slate-500">
                      No unlinked threats currently match indicators from this incident&apos;s evidence set.
                    </div>
                  ) : (
                    <div className="divide-y divide-slate-800/80">
                      {suggestions.map((sugg) => (
                        <div
                          key={sugg.threat_event_id}
                          className="py-3 first:pt-0 last:pb-0 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                        >
                          <div className="space-y-1">
                            <div className="flex items-center space-x-2">
                              <EventTypeBadge type={sugg.event_type} />
                              <RiskBadge
                                category={sugg.threat_category}
                                score={sugg.risk_score}
                                size="sm"
                              />
                              <span className="font-mono text-xs font-semibold text-slate-300">
                                {sugg.risk_score}/100
                              </span>
                              <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono bg-cyan-950 text-cyan-300 border border-cyan-800">
                                {sugg.signal}
                              </span>
                              <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono bg-slate-800 text-slate-400">
                                {sugg.confidence}
                              </span>
                            </div>

                            <div className="text-xs text-slate-300 flex items-center space-x-1">
                              <span className="text-slate-500 font-mono text-[10px]">MATCHED:</span>
                              <span className="font-mono text-amber-300 break-all">
                                {sugg.matched_indicator}
                              </span>
                            </div>

                            <div className="text-[10px] text-slate-500 font-mono">
                              Detected on {new Date(sugg.timestamp).toLocaleString()}
                            </div>
                          </div>

                          <button
                            onClick={() =>
                              handleLinkThreat(
                                sugg.threat_event_id,
                                sugg.signal,
                                sugg.confidence
                              )
                            }
                            disabled={isLinkingId === sugg.threat_event_id}
                            className="inline-flex items-center justify-center space-x-1.5 px-3 py-1.5 rounded-lg bg-amber-500/10 hover:bg-amber-500/20 text-amber-300 border border-amber-500/30 text-xs font-semibold transition-colors disabled:opacity-50 whitespace-nowrap self-start sm:self-auto"
                          >
                            <Link2 className="w-3.5 h-3.5" />
                            <span>
                              {isLinkingId === sugg.threat_event_id
                                ? 'Correlating...'
                                : 'Correlate Threat'}
                            </span>
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </Card>
              </div>

              {/* Right Column: Investigation Notes */}
              <div className="space-y-4">
                <Card
                  title={`Investigation Notes (${incident.notes?.length || 0})`}
                  subtitle="Append-only operator notes and analytical observations"
                >
                  {/* Add Note Form */}
                  <form onSubmit={handleAddNote} className="space-y-3 pb-4 border-b border-slate-800">
                    <div className="flex items-center space-x-2">
                      <select
                        value={noteType}
                        onChange={(e) => setNoteType(e.target.value)}
                        className="py-1 px-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-amber-500/50"
                      >
                        <option value="OBSERVATION">Observation</option>
                        <option value="ANALYSIS">Analysis</option>
                        <option value="REMEDIATION">Remediation Action</option>
                        <option value="EVIDENCE_NOTE">Evidence Finding</option>
                      </select>
                      <span className="text-[10px] text-slate-500 font-mono uppercase">
                        Audit Logged
                      </span>
                    </div>

                    <textarea
                      rows={3}
                      placeholder="Add investigation finding, external OSINT note, or remediation step..."
                      value={noteContent}
                      onChange={(e) => setNoteContent(e.target.value)}
                      className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-amber-500/50"
                    />

                    <button
                      type="submit"
                      disabled={isSubmittingNote || !noteContent.trim()}
                      className="w-full py-1.5 px-3 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold border border-slate-700 flex items-center justify-center space-x-1.5 transition-colors disabled:opacity-40"
                    >
                      <Send className="w-3 h-3 text-amber-400" />
                      <span>{isSubmittingNote ? 'Saving Note...' : 'Record Note'}</span>
                    </button>
                  </form>

                  {/* Notes Feed */}
                  {(!incident.notes || incident.notes.length === 0) ? (
                    <div className="py-6 text-center text-xs text-slate-500">
                      No investigation notes recorded yet.
                    </div>
                  ) : (
                    <div className="space-y-3 pt-3 max-h-[460px] overflow-y-auto pr-1">
                      {incident.notes.map((note) => (
                        <div
                          key={note.id}
                          className="p-3 bg-slate-950/70 border border-slate-800/80 rounded-lg space-y-1.5"
                        >
                          <div className="flex items-center justify-between text-[11px]">
                            <span className="font-semibold text-slate-300">
                              {note.author_name || 'SOC Operator'}
                            </span>
                            <span className="inline-flex items-center px-1.5 py-0.2 rounded text-[9px] font-mono bg-slate-800 text-cyan-300 border border-slate-700">
                              {note.note_type}
                            </span>
                          </div>
                          <p className="text-xs text-slate-200 whitespace-pre-wrap leading-relaxed">
                            {note.content}
                          </p>
                          <div className="text-[10px] text-slate-500 font-mono text-right">
                            {new Date(note.created_at).toLocaleString()}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </Card>
              </div>
            </div>

            {/* Bottom Section: Audit Trail & Timeline */}
            <Card
              title={`Chronological Audit Timeline (${incident.events?.length || 0})`}
              subtitle="Tamper-evident audit trail of state transitions, threat linkages, assignments, and notes"
              action={
                <span className="inline-flex items-center space-x-1 text-xs text-slate-400">
                  <History className="w-3.5 h-3.5 text-cyan-400" />
                  <span>Immutable Ledger</span>
                </span>
              }
            >
              {(!incident.events || incident.events.length === 0) ? (
                <div className="py-8 text-center text-xs text-slate-500">
                  No chronological events recorded yet.
                </div>
              ) : (
                <div className="relative pl-6 space-y-4 before:absolute before:left-2.5 before:top-2 before:bottom-2 before:w-0.5 before:bg-slate-800">
                  {incident.events.map((evt) => (
                    <div key={evt.id} className="relative">
                      <div className="absolute -left-6 top-1.5 w-3 h-3 rounded-full bg-slate-900 border-2 border-cyan-400" />
                      <div className="p-3 bg-slate-950/80 border border-slate-800/80 rounded-lg space-y-1">
                        <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                          <div className="flex items-center space-x-2">
                            <span className="font-mono font-bold text-slate-200">
                              {evt.event_type}
                            </span>
                            {evt.previous_state && evt.new_state && (
                              <span className="text-[10px] font-mono text-slate-400">
                                ({evt.previous_state} → <strong className="text-amber-300">{evt.new_state}</strong>)
                              </span>
                            )}
                          </div>
                          <span className="text-[10px] font-mono text-slate-500">
                            {new Date(evt.timestamp).toLocaleString()}
                          </span>
                        </div>
                        <p className="text-xs text-slate-300">{evt.summary}</p>
                        <div className="text-[10px] text-slate-500 font-mono">
                          Operator: {evt.actor_name || 'System / Automated Rule'}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Card>
          </>
        )}

        {/* MODAL 1: Status Transition Modal */}
        {showStatusModal && (
          <div className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-md w-full p-6 space-y-4 shadow-2xl">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <h3 className="text-base font-bold text-slate-100 flex items-center space-x-2">
                  <AlertOctagon className="w-5 h-5 text-amber-400" />
                  <span>Transition Status to {targetStatus}</span>
                </h3>
                <button
                  onClick={() => setShowStatusModal(false)}
                  className="text-slate-400 hover:text-white text-sm"
                >
                  ✕
                </button>
              </div>

              <form onSubmit={handleStatusChange} className="space-y-4">
                {targetStatus === 'RESOLVED' && (
                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">
                      Resolution Summary <span className="text-rose-400">*</span>
                    </label>
                    <textarea
                      rows={3}
                      required
                      placeholder="Document containment steps taken, threat indicators blocklisted, and impact assessment..."
                      value={resolutionSummary}
                      onChange={(e) => setResolutionSummary(e.target.value)}
                      className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-emerald-500"
                    />
                  </div>
                )}

                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">
                    Transition Rationale & Notes
                  </label>
                  <textarea
                    rows={2}
                    placeholder="Rationale for this state transition (e.g. initial triage complete, phishing kit verified)..."
                    value={statusReason}
                    onChange={(e) => setStatusReason(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-amber-500"
                  />
                </div>

                <div className="flex items-center justify-end space-x-2 pt-3 border-t border-slate-800">
                  <button
                    type="button"
                    onClick={() => setShowStatusModal(false)}
                    className="px-3.5 py-1.5 rounded-lg bg-slate-800 text-slate-300 text-xs font-medium hover:bg-slate-700"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isUpdatingStatus}
                    className="px-4 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold transition-colors disabled:opacity-50"
                  >
                    {isUpdatingStatus ? 'Applying...' : `Confirm ${targetStatus}`}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* MODAL 2: Change Severity Modal */}
        {showSeverityModal && (
          <div className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-md w-full p-6 space-y-4 shadow-2xl">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <h3 className="text-base font-bold text-slate-100 flex items-center space-x-2">
                  <ShieldAlert className="w-5 h-5 text-rose-400" />
                  <span>Update Incident Severity</span>
                </h3>
                <button
                  onClick={() => setShowSeverityModal(false)}
                  className="text-slate-400 hover:text-white text-sm"
                >
                  ✕
                </button>
              </div>

              <form onSubmit={handleSeverityChange} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">
                    Severity Level
                  </label>
                  <select
                    value={newSeverity}
                    onChange={(e) => setNewSeverity(e.target.value as IncidentSeverity)}
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-amber-500"
                  >
                    <option value="LOW">LOW — Minor heuristic anomaly</option>
                    <option value="MEDIUM">MEDIUM — Suspicious SMS/QR detection</option>
                    <option value="HIGH">HIGH — Confirmed credential phishing attack</option>
                    <option value="CRITICAL">CRITICAL — Active campaign targeting multiple users</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">
                    Adjustment Justification
                  </label>
                  <textarea
                    rows={2}
                    placeholder="Document evidence justifying the severity elevation or downgrade..."
                    value={severityReason}
                    onChange={(e) => setSeverityReason(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-amber-500"
                  />
                </div>

                <div className="flex items-center justify-end space-x-2 pt-3 border-t border-slate-800">
                  <button
                    type="button"
                    onClick={() => setShowSeverityModal(false)}
                    className="px-3.5 py-1.5 rounded-lg bg-slate-800 text-slate-300 text-xs font-medium hover:bg-slate-700"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isUpdatingSeverity}
                    className="px-4 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold transition-colors disabled:opacity-50"
                  >
                    {isUpdatingSeverity ? 'Updating...' : 'Update Severity'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* MODAL 3: Assign Administrator Modal */}
        {showAssignModal && (
          <div className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-md w-full p-6 space-y-4 shadow-2xl">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <h3 className="text-base font-bold text-slate-100 flex items-center space-x-2">
                  <UserCheck className="w-5 h-5 text-emerald-400" />
                  <span>Assign Investigation Lead</span>
                </h3>
                <button
                  onClick={() => setShowAssignModal(false)}
                  className="text-slate-400 hover:text-white text-sm"
                >
                  ✕
                </button>
              </div>

              <form onSubmit={handleAssignAdmin} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">
                    Designated Administrator
                  </label>
                  <select
                    value={selectedAdminId}
                    onChange={(e) => setSelectedAdminId(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-amber-500"
                  >
                    <option value="">-- Unassigned --</option>
                    {adminUsers.map((u) => (
                      <option key={u.id} value={u.id}>
                        {u.display_name || u.email} ({u.email})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">
                    Assignment Guidance Note
                  </label>
                  <textarea
                    rows={2}
                    placeholder="Instructions, expected triage window, or priority focus..."
                    value={assignmentNote}
                    onChange={(e) => setAssignmentNote(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-amber-500"
                  />
                </div>

                <div className="flex items-center justify-end space-x-2 pt-3 border-t border-slate-800">
                  <button
                    type="button"
                    onClick={() => setShowAssignModal(false)}
                    className="px-3.5 py-1.5 rounded-lg bg-slate-800 text-slate-300 text-xs font-medium hover:bg-slate-700"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isAssigning}
                    className="px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold transition-colors disabled:opacity-50"
                  >
                    {isAssigning ? 'Saving...' : 'Confirm Assignment'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </AdminShell>
  );
}
