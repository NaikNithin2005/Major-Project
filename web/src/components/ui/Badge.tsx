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
      return (
        <span
          className={`inline-flex items-center font-bold tracking-wide rounded-full bg-rose-500/15 text-rose-300 border border-rose-500/30 ${sizeClasses}`}
          role="status"
          aria-label="High Risk Level"
        >
          <span className="w-1.5 h-1.5 rounded-full bg-rose-500 mr-1.5 animate-pulse" />
          HIGH RISK
        </span>
      );
    case 'MEDIUM':
      return (
        <span
          className={`inline-flex items-center font-bold tracking-wide rounded-full bg-amber-500/15 text-amber-300 border border-amber-500/30 ${sizeClasses}`}
          role="status"
          aria-label="Medium Risk Level"
        >
          <span className="w-1.5 h-1.5 rounded-full bg-amber-500 mr-1.5" />
          MEDIUM RISK
        </span>
      );
    case 'LOW':
      return (
        <span
          className={`inline-flex items-center font-bold tracking-wide rounded-full bg-sky-500/15 text-sky-300 border border-sky-500/30 ${sizeClasses}`}
          role="status"
          aria-label="Low Risk Level"
        >
          <span className="w-1.5 h-1.5 rounded-full bg-sky-500 mr-1.5" />
          LOW RISK
        </span>
      );
    case 'SAFE':
    default:
      return (
        <span
          className={`inline-flex items-center font-bold tracking-wide rounded-full bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 ${sizeClasses}`}
          role="status"
          aria-label="Safe Risk Level"
        >
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 mr-1.5" />
          SAFE
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
        QR QUISHING
      </span>
    );
  }
  return (
    <span className="inline-flex items-center text-[10px] font-bold tracking-wide px-2 py-0.5 rounded bg-cyan-500/15 text-cyan-300 border border-cyan-500/30">
      SMS SMISHING
    </span>
  );
}

interface StatusBadgeProps {
  status: ThreatStatus | string;
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const s = (status || 'REPORTED').toUpperCase();

  const styles: Record<string, string> = {
    NEW: 'bg-blue-500/15 text-blue-300 border-blue-500/30',
    REPORTED: 'bg-slate-500/15 text-slate-300 border-slate-500/30',
    REVIEWED: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
    RESOLVED: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
    FALSE_POSITIVE: 'bg-indigo-500/15 text-indigo-300 border-indigo-500/30',
    BLOCKED: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
  };

  const style = styles[s] || 'bg-slate-500/15 text-slate-400 border-slate-500/30';

  return (
    <span className={`inline-flex items-center text-[10px] font-bold px-2 py-0.5 rounded-full border ${style}`}>
      {s.replace('_', ' ')}
    </span>
  );
}

interface IncidentSeverityBadgeProps {
  severity: string;
  size?: 'sm' | 'md' | 'lg';
}

export function IncidentSeverityBadge({ severity, size = 'md' }: IncidentSeverityBadgeProps) {
  const sev = (severity || 'LOW').toUpperCase();
  const sizeClasses = {
    sm: 'text-[10px] px-2 py-0.5',
    md: 'text-xs px-2.5 py-0.5',
    lg: 'text-xs px-3 py-1',
  }[size];

  switch (sev) {
    case 'CRITICAL':
      return (
        <span className={`inline-flex items-center font-bold tracking-wide rounded-full bg-red-500/20 text-red-300 border border-red-500/40 ${sizeClasses}`}>
          <span className="w-1.5 h-1.5 rounded-full bg-red-400 mr-1.5 animate-ping" />
          CRITICAL
        </span>
      );
    case 'HIGH':
      return (
        <span className={`inline-flex items-center font-bold tracking-wide rounded-full bg-rose-500/15 text-rose-300 border border-rose-500/30 ${sizeClasses}`}>
          <span className="w-1.5 h-1.5 rounded-full bg-rose-500 mr-1.5" />
          HIGH
        </span>
      );
    case 'MEDIUM':
      return (
        <span className={`inline-flex items-center font-bold tracking-wide rounded-full bg-amber-500/15 text-amber-300 border border-amber-500/30 ${sizeClasses}`}>
          <span className="w-1.5 h-1.5 rounded-full bg-amber-500 mr-1.5" />
          MEDIUM
        </span>
      );
    case 'LOW':
    default:
      return (
        <span className={`inline-flex items-center font-bold tracking-wide rounded-full bg-sky-500/15 text-sky-300 border border-sky-500/30 ${sizeClasses}`}>
          <span className="w-1.5 h-1.5 rounded-full bg-sky-500 mr-1.5" />
          LOW
        </span>
      );
  }
}

interface IncidentStatusBadgeProps {
  status: string;
}

export function IncidentStatusBadge({ status }: IncidentStatusBadgeProps) {
  const s = (status || 'NEW').toUpperCase();

  const styles: Record<string, string> = {
    NEW: 'bg-blue-500/15 text-blue-300 border-blue-500/30',
    TRIAGED: 'bg-cyan-500/15 text-cyan-300 border-cyan-500/30',
    INVESTIGATING: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
    CONTAINED: 'bg-purple-500/15 text-purple-300 border-purple-500/30',
    RESOLVED: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
    CLOSED: 'bg-slate-500/15 text-slate-400 border-slate-600/30',
  };

  const style = styles[s] || 'bg-slate-500/15 text-slate-400 border-slate-500/30';

  return (
    <span className={`inline-flex items-center text-[10px] font-bold px-2.5 py-0.5 rounded-full border ${style}`}>
      {s}
    </span>
  );
}
