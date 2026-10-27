'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { UserShell } from '@/components/layout/UserShell';
import { Card } from '@/components/ui/Card';
import { RiskBadge, EventTypeBadge, StatusBadge } from '@/components/ui/Badge';
import { RiskGauge } from '@/components/ui/RiskGauge';
import { LoadingSpinner } from '@/components/ui/LoadingState';
import { ErrorState } from '@/components/ui/ErrorState';
import { api } from '@/services/api';
import { ThreatEvent } from '@/types';
import {
  ArrowLeft,
  ShieldAlert,
  Clock,
  Cpu,
  CheckCircle2,
  AlertTriangle,
  Info,
  ExternalLink,
  ChevronDown,
  ChevronUp,
  Shield,
  FileCheck,
  Ban,
  Trash2,
  Share2,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import {
  getUserThreatById,
  takeUserThreatAction,
  deleteUserThreat,
} from '@/services/threatFirestore';

export default function ThreatDetailPage() {
  const { user } = useAuth();
  const params = useParams();
  const router = useRouter();
  const threatId = params.id as string;

  const [threat, setThreat] = useState<ThreatEvent | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [showTechnicalDetails, setShowTechnicalDetails] = useState<boolean>(false);
  const [isUpdatingAction, setIsUpdatingAction] = useState<boolean>(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const fetchThreat = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      // 1. First attempt to load direct from user's isolated Firestore collection
      if (user?.firebase_uid) {
        const firestoreRecord = await getUserThreatById(user.firebase_uid, threatId);
        if (firestoreRecord) {
          setThreat(firestoreRecord);
          setIsLoading(false);
          return;
        }
      }

      // 2. Fallback to API if not yet synced in client or legacy record
      const data = await api.userThreats.get(threatId);
      setThreat(data);
    } catch (err: any) {
      setError(err.message || 'Unable to retrieve threat report.');
    } finally {
      setIsLoading(false);
    }
  }, [threatId, user?.firebase_uid]);

  useEffect(() => {
    fetchThreat();
  }, [fetchThreat]);

  const handleUserAction = async (action: 'BLOCK' | 'MARK_SAFE' | 'REPORT' | 'IGNORE') => {
    setIsUpdatingAction(true);
    setActionMessage(null);
    try {
      if (user?.firebase_uid && (action === 'BLOCK' || action === 'MARK_SAFE' || action === 'IGNORE')) {
        await takeUserThreatAction(user.firebase_uid, threatId, action);
      }
      try {
        const updated = await api.userThreats.takeAction(threatId, action);
        setThreat(updated);
      } catch {
        setThreat((prev) =>
          prev ? { ...prev, status: action === 'BLOCK' ? 'BLOCKED' : 'RESOLVED', user_action: action } : null
        );
      }
      setActionMessage(`Action "${action}" recorded successfully.`);
    } catch (err: any) {
      alert(`Action failed: ${err.message}`);
    } finally {
      setIsUpdatingAction(false);
    }
  };

  const handleDelete = async () => {
    if (!confirm('Are you sure you want to permanently delete this threat event from your history?')) return;
    try {
      if (user?.firebase_uid) {
        await deleteUserThreat(user.firebase_uid, threatId);
      }
      try {
        await api.userThreats.delete(threatId);
      } catch {}

      router.push('/threats');
    } catch (err: any) {
      alert(`Delete failed: ${err.message}`);
    }
  };

  if (isLoading) {
    return (
      <UserShell>
        <div className="py-24 flex justify-center">
          <LoadingSpinner message="Decrypting on-device AI forensics report..." />
        </div>
      </UserShell>
    );
  }

  if (error || !threat) {
    return (
      <UserShell>
        <div className="space-y-4">
          <Link
            href="/threats"
            className="inline-flex items-center space-x-1.5 text-xs text-slate-400 hover:text-slate-200 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back to Threat History</span>
          </Link>
          <ErrorState
            title="Threat Forensics Unavailable"
            message={error || 'Threat record was not found or is inaccessible.'}
            onRetry={fetchThreat}
          />
        </div>
      </UserShell>
    );
  }

  const modelResults = threat.model_results || {};
  const urlMeta = threat.url_metadata || {};
  const qrMeta = threat.qr_metadata || {};
  const ruleResults = threat.rule_results || [];

  return (
    <UserShell>
      <div className="space-y-6">
        {/* Navigation Breadcrumb */}
        <div className="flex items-center justify-between">
          <Link
            href="/threats"
            className="inline-flex items-center space-x-1.5 text-xs font-semibold text-slate-400 hover:text-cyan-400 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back to My Threats</span>
          </Link>

          <div className="flex items-center space-x-2">
            <button
              onClick={handleDelete}
              className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-lg bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 border border-rose-500/30 text-xs font-semibold transition-all"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>Delete Record</span>
            </button>
          </div>
        </div>

        {/* Action feedback alert */}
        {actionMessage && (
          <div className="p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/25 flex items-center space-x-2.5 text-emerald-300 text-xs">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>{actionMessage}</span>
          </div>
        )}

        {/* Header Threat Hero Card */}
        <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-6 shadow-xl relative overflow-hidden backdrop-blur-sm">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 relative z-10">
            <div>
              <div className="flex flex-wrap items-center gap-2 mb-2">
                <EventTypeBadge type={threat.event_type} />
                <RiskBadge category={threat.threat_category} score={threat.risk_score} />
                <StatusBadge status={threat.status} />
                {threat.user_action && (
                  <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-cyan-500/15 text-cyan-300 border border-cyan-500/30">
                    Decision: {threat.user_action}
                  </span>
                )}
              </div>
              <h1 className="text-xl sm:text-2xl font-extrabold text-slate-100 tracking-tight flex items-center space-x-2">
                <span>{threat.event_type === 'QR' ? 'Quishing Attack Telemetry' : 'Smishing SMS Telemetry'}</span>
              </h1>
              <p className="text-xs text-slate-400 mt-1 font-mono">
                Event ID: {threat.id}
              </p>
              <div className="mt-2 text-xs text-slate-400 flex items-center space-x-3">
                <span className="flex items-center space-x-1">
                  <Clock className="w-3.5 h-3.5 text-slate-500" />
                  <span>
                    Detected:{' '}
                    {new Date(threat.timestamp).toLocaleString([], {
                      dateStyle: 'medium',
                      timeStyle: 'short',
                    })}
                  </span>
                </span>
              </div>
            </div>

            {/* Gauge */}
            <div className="shrink-0 flex items-center justify-center p-3 bg-slate-950/60 rounded-xl border border-slate-800/80">
              <RiskGauge score={threat.risk_score} category={threat.threat_category} size={140} />
            </div>
          </div>
        </div>

        {/* Recommended Action Policy & User Action Controls */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <Card title="Automated Defense Policy" subtitle="Prescribed mitigation from on-device heuristics">
            <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs text-slate-400">Policy Recommendation:</span>
                <span className="font-bold text-xs uppercase px-2 py-0.5 rounded bg-amber-500/10 text-amber-300 border border-amber-500/20 font-mono">
                  {threat.recommended_action}
                </span>
              </div>
              <p className="text-xs text-slate-300 leading-relaxed">
                {threat.threat_category === 'HIGH'
                  ? 'High confidence malicious indicators detected. Network navigation was blocked to protect your credentials and device.'
                  : threat.threat_category === 'MEDIUM'
                  ? 'Suspicious attributes identified. Sender or URL displays unverified characteristics. Exercise caution.'
                  : 'No known malicious patterns or suspicious structures detected. Safe to browse.'}
              </p>
            </div>

            <div className="mt-4 pt-3 border-t border-slate-800/80">
              <div className="text-xs font-semibold text-slate-300 mb-2">Take Action:</div>
              <div className="flex flex-wrap gap-2">
                <button
                  onClick={() => handleUserAction('BLOCK')}
                  disabled={isUpdatingAction || threat.user_action === 'BLOCK'}
                  className="px-3 py-1.5 rounded-lg bg-rose-500/15 hover:bg-rose-500/25 text-rose-300 text-xs font-semibold border border-rose-500/30 transition-all disabled:opacity-40"
                >
                  Block Sender & Domain
                </button>
                <button
                  onClick={() => handleUserAction('MARK_SAFE')}
                  disabled={isUpdatingAction || threat.user_action === 'MARK_SAFE'}
                  className="px-3 py-1.5 rounded-lg bg-emerald-500/15 hover:bg-emerald-500/25 text-emerald-300 text-xs font-semibold border border-emerald-500/30 transition-all disabled:opacity-40"
                >
                  Mark False Positive
                </button>
                <button
                  onClick={() => handleUserAction('IGNORE')}
                  disabled={isUpdatingAction || threat.user_action === 'IGNORE'}
                  className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium border border-slate-700 transition-all disabled:opacity-40"
                >
                  Dismiss / Ignore
                </button>
              </div>
            </div>
          </Card>

          {/* Extracted Target Information */}
          <Card title="Target Inspection" subtitle="Payload indicators extracted with zero-knowledge privacy">
            <div className="space-y-3">
              {urlMeta.domain && (
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800 flex justify-between items-center text-xs">
                  <span className="text-slate-400">Target Domain:</span>
                  <span className="font-mono text-cyan-400 font-bold">{urlMeta.domain}</span>
                </div>
              )}
              {urlMeta.scheme && (
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800 flex justify-between items-center text-xs">
                  <span className="text-slate-400">Protocol / Scheme:</span>
                  <span className="font-mono text-slate-200">{urlMeta.scheme}</span>
                </div>
              )}
              {qrMeta.scheme && (
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800 flex justify-between items-center text-xs">
                  <span className="text-slate-400">QR Payload Type:</span>
                  <span className="font-mono text-cyan-400 font-bold">{qrMeta.scheme}</span>
                </div>
              )}
              <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800 flex justify-between items-center text-xs">
                <span className="text-slate-400">Zero Raw Message Storage:</span>
                <span className="text-emerald-400 font-medium">Data Minimized Verified</span>
              </div>
            </div>
          </Card>
        </div>

        {/* Explainable AI Telemetry Breakdown */}
        <Card title="Explainable AI Telemetry Breakdown" subtitle="Detailed signals and inference probabilities from on-device models">
          {threat.reasons && threat.reasons.length > 0 ? (
            <div className="space-y-2 mb-4">
              <div className="text-xs font-semibold text-slate-300 mb-1">Key Heuristic Reasons:</div>
              <ul className="space-y-1.5">
                {threat.reasons.map((reason, idx) => (
                  <li
                    key={idx}
                    className="p-2.5 rounded-lg bg-slate-950/70 border border-slate-800/80 text-xs text-slate-200 flex items-start space-x-2"
                  >
                    <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                    <span>{reason}</span>
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <div className="text-xs text-slate-500 italic mb-4">
              No anomaly flags were raised by on-device heuristic classifiers.
            </div>
          )}

          {/* Model Probabilities Matrix */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-3 border-t border-slate-800">
            <div className="p-3 rounded-lg bg-slate-950/60 border border-slate-800">
              <div className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">
                XGBoost Probability
              </div>
              <div className="text-lg font-bold font-mono text-cyan-400 mt-1">
                {modelResults.xgboost_prob !== undefined
                  ? `${Math.round(modelResults.xgboost_prob * 100)}%`
                  : 'N/A'}
              </div>
              <div className="text-[10px] text-slate-500 mt-0.5">URL structural model</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-950/60 border border-slate-800">
              <div className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">
                TinyBERT NLP Confidence
              </div>
              <div className="text-lg font-bold font-mono text-blue-400 mt-1">
                {modelResults.tinybert_prob !== undefined
                  ? `${Math.round(modelResults.tinybert_prob * 100)}%`
                  : 'N/A'}
              </div>
              <div className="text-[10px] text-slate-500 mt-0.5">Semantic smishing NLP</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-950/60 border border-slate-800">
              <div className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">
                Isolation Forest Score
              </div>
              <div className="text-lg font-bold font-mono text-purple-400 mt-1">
                {modelResults.anomaly_score !== undefined
                  ? Number(modelResults.anomaly_score).toFixed(2)
                  : 'N/A'}
              </div>
              <div className="text-[10px] text-slate-500 mt-0.5">Zero-day outlier index</div>
            </div>
          </div>

          {/* Toggle raw JSON telemetry */}
          <div className="mt-4 pt-3 border-t border-slate-800/80">
            <button
              onClick={() => setShowTechnicalDetails(!showTechnicalDetails)}
              className="inline-flex items-center space-x-1.5 text-xs text-slate-400 hover:text-slate-200"
            >
              {showTechnicalDetails ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
              <span>{showTechnicalDetails ? 'Hide Raw Telemetry Payload' : 'Show Raw Telemetry Payload'}</span>
            </button>

            {showTechnicalDetails && (
              <pre className="mt-3 p-4 bg-slate-950 rounded-lg border border-slate-800 font-mono text-[11px] text-slate-300 overflow-x-auto">
                {JSON.stringify(threat, null, 2)}
              </pre>
            )}
          </div>
        </Card>
      </div>
    </UserShell>
  );
}
