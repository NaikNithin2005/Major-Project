import React from 'react';
import { RiskBadge } from './Badge';

interface RiskGaugeProps {
  score: number;
  category?: string;
  size?: 'sm' | 'md' | 'lg' | number;
}

export function RiskGauge({ score, category, size = 'md' }: RiskGaugeProps) {
  const normalizedScore = Math.max(0, Math.min(100, Math.round(score)));

  let resolvedSize: 'sm' | 'md' | 'lg' = 'md';
  if (typeof size === 'number') {
    if (size < 80) resolvedSize = 'sm';
    else if (size > 140) resolvedSize = 'lg';
    else resolvedSize = 'md';
  } else {
    resolvedSize = size;
  }

  const computedCategory =
    category ||
    (normalizedScore >= 76
      ? 'HIGH'
      : normalizedScore >= 51
      ? 'MEDIUM'
      : normalizedScore >= 26
      ? 'LOW'
      : 'SAFE');

  const getColor = (s: number) => {
    if (s >= 76) return 'from-rose-500 to-red-600';
    if (s >= 51) return 'from-amber-500 to-orange-600';
    if (s >= 26) return 'from-sky-500 to-blue-600';
    return 'from-emerald-500 to-teal-600';
  };

  const getTextColor = (s: number) => {
    if (s >= 76) return 'text-rose-400';
    if (s >= 51) return 'text-amber-400';
    if (s >= 26) return 'text-sky-400';
    return 'text-emerald-400';
  };

  if (resolvedSize === 'sm') {
    return (
      <div className="flex items-center space-x-2">
        <span className={`font-bold font-mono text-sm ${getTextColor(normalizedScore)}`}>
          {normalizedScore}
        </span>
        <div className="w-16 h-1.5 bg-slate-800 rounded-full overflow-hidden">
          <div
            className={`h-full bg-gradient-to-r ${getColor(normalizedScore)} rounded-full`}
            style={{ width: `${normalizedScore}%` }}
          />
        </div>
      </div>
    );
  }

  // Semi-circle gauge visualization for md and lg
  const strokeDashoffset = 188 - (188 * normalizedScore) / 100;

  return (
    <div className="flex flex-col items-center justify-center relative">
      <svg className="w-28 h-20 overflow-visible" viewBox="0 0 100 65">
        <path
          d="M 10,60 A 40,40 0 0,1 90,60"
          fill="none"
          stroke="#1e293b"
          strokeWidth="10"
          strokeLinecap="round"
        />
        <path
          d="M 10,60 A 40,40 0 0,1 90,60"
          fill="none"
          stroke={`url(#gauge-gradient-${normalizedScore})`}
          strokeWidth="10"
          strokeDasharray="188"
          strokeDashoffset={strokeDashoffset}
          strokeLinecap="round"
          className="transition-all duration-700 ease-out"
        />
        <defs>
          <linearGradient id={`gauge-gradient-${normalizedScore}`} x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#10b981" />
            <stop offset="50%" stopColor="#f59e0b" />
            <stop offset="100%" stopColor="#ef4444" />
          </linearGradient>
        </defs>
      </svg>
      <div className="absolute top-7 flex flex-col items-center pointer-events-none">
        <span className={`text-2xl font-black font-mono tracking-tight ${getTextColor(normalizedScore)}`}>
          {normalizedScore}
        </span>
        <span className="text-[9px] uppercase tracking-wider text-slate-500 font-bold -mt-1">
          / 100
        </span>
      </div>
    </div>
  );
}
