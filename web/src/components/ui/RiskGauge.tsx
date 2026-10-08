import React from 'react';
import { RiskBadge } from './Badge';

interface RiskGaugeProps {
  score: number;
  category?: string;
  size?: 'sm' | 'md' | 'lg';
}

export function RiskGauge({ score, category, size = 'md' }: RiskGaugeProps) {
  const normalizedScore = Math.max(0, Math.min(100, Math.round(score)));

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

  if (size === 'sm') {
    return (
      <div className="flex items-center space-x-2">
        <span className={`font-bold font-mono text-sm ${getTextColor(normalizedScore)}`}>
          {normalizedScore}
        </span>
        <div className="w-16 bg-slate-800 h-1.5 rounded-full overflow-hidden">
          <div
            className={`h-full bg-gradient-to-r ${getColor(normalizedScore)}`}
            style={{ width: `${normalizedScore}%` }}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-xs uppercase tracking-wider text-slate-400 font-semibold">
          Calculated Risk Score
        </span>
        <RiskBadge category={computedCategory} size="sm" />
      </div>

      <div className="flex items-baseline space-x-2">
        <span className={`text-4xl font-extrabold font-mono tracking-tight ${getTextColor(normalizedScore)}`}>
          {normalizedScore}
        </span>
        <span className="text-slate-500 text-sm font-semibold">/ 100</span>
      </div>

      <div className="w-full bg-slate-800/80 rounded-full h-2.5 overflow-hidden border border-slate-700/50">
        <div
          className={`h-full rounded-full bg-gradient-to-r ${getColor(normalizedScore)} transition-all duration-500`}
          style={{ width: `${normalizedScore}%` }}
        />
      </div>

      <div className="flex justify-between text-[10px] text-slate-500 font-medium px-0.5">
        <span>0 Safe</span>
        <span>26 Low</span>
        <span>51 Medium</span>
        <span>76 High</span>
        <span>100</span>
      </div>
    </div>
  );
}
