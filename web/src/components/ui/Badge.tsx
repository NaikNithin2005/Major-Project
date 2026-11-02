import React from 'react';
import { ThreatCategory, ThreatEventType, ThreatStatus } from '@/types';

interface RiskBadgeProps {
  category: ThreatCategory | string;
  score?: number;
  size?: 'sm' | 'md' | 'lg';
}

export function RiskBadge({ category, score, size = 'md' }: RiskBadgeProps) {
  const cat = (category || 'SAFE').toUpperCase();

  const sizeClasses = {
    sm: 'text-[10px] px-2 py-0.5',
    md: 'text-xs px-2.5 py-0.5',
    lg: 'text-xs px-3 py-1',
  }[size];

  switch (cat) {
    case 'HIGH':
    case 'CRITICAL':
    case 'MALICIOUS':
      return (
        <span
          className={`inline-flex items-center font-bold tracking-wide rounded-full bg-rose-500/15 text-rose-300 border border-rose-500/30 ${sizeClasses}`}
          role="status"
          aria-label="Dangerous Scam Warning"
        >
          <span className="w-1.5 h-1.5 rounded-full bg-rose-500 mr-1.5 animate-pulse" />
          Dangerous
        </span>
      );
    case 'MEDIUM':
    case 'SUSPICIOUS':
      return (
        <span
          className={`inline-flex items-center font-bold tracking-wide rounded-full bg-amber-500/15 text-amber-300 border border-amber-500/30 ${sizeClasses}`}
          role="status"
          aria-label="Needs Attention"
        >
          <span className="w-1.5 h-1.5 rounded-full bg-amber-500 mr-1.5" />
          Needs Attention
        </span>
      );
    case 'LOW':
      return (
        <span
          className={`inline-flex items-center font-bold tracking-wide rounded-full bg-sky-500/15 text-sky-300 border border-sky-500/30 ${sizeClasses}`}
          role="status"
          aria-label="Minor Caution"
        >
          <span className="w-1.5 h-1.5 rounded-full bg-sky-500 mr-1.5" />
          Minor Caution
        </span>
      );
    case 'SAFE':
    default:
      return (
        <span
          className={`inline-flex items-center font-bold tracking-wide rounded-full bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 ${sizeClasses}`}
          role="status"
          aria-label="Looks Safe"
        >
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 mr-1.5" />
          Looks Safe
        </span>
      );
  }
}

interface EventTypeBadgeProps {
  type: ThreatEventType | string;
}

export function EventTypeBadge({ type }: EventTypeBadgeProps) {
  const t = (type || 'SMS').toUpperCase();
  if (t === 'QR') {
    return (
      <span className="inline-flex items-center text-[10px] font-bold tracking-wide px-2 py-0.5 rounded bg-purple-500/15 text-purple-300 border border-purple-500/30">
        QR Code
      </span>
    );
  }
  return (
    <span className="inline-flex items-center text-[10px] font-bold tracking-wide px-2 py-0.5 rounded bg-cyan-500/15 text-cyan-300 border border-cyan-500/30">
      Text Message
    </span>
  );
}

interface StatusBadgeProps {
  status: ThreatStatus | string;
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const s = (status || 'NEW').toUpperCase();

  const labels: Record<string, string> = {
    NEW: 'New',
    REPORTED: 'Checked',
    REVIEWED: 'Reviewed',
    RESOLVED: 'Resolved',
    FALSE_POSITIVE: 'Safe Override',
    BLOCKED: 'Blocked',
  };

  const label = labels[s] || s;

  const styles: Record<string, string> = {
    NEW: 'bg-blue-500/15 text-blue-300 border-blue-500/30',
    REPORTED: 'bg-slate-500/15 text-slate-300 border-slate-500/30',
    REVIEWED: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
    RESOLVED: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
    FALSE_POSITIVE: 'bg-indigo-500/15 text-indigo-300 border-indigo-500/30',
    BLOCKED: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
  };

  const style = styles[s] || 'bg-slate-500/15 text-slate-300 border-slate-500/30';

  return (
    <span className={`inline-flex items-center text-[10px] font-semibold px-2 py-0.5 rounded border ${style}`}>
      {label}
    </span>
  );
}

export function IncidentSeverityBadge({ severity, size = 'md' }: { severity?: string; size?: 'sm' | 'md' | 'lg' | string }) {
  return <RiskBadge category={severity || 'SAFE'} size={size as any} />;
}

export function IncidentStatusBadge({ status }: { status?: string }) {
  return <StatusBadge status={status || 'NEW'} />;
}
