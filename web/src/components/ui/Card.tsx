import React, { ReactNode } from 'react';

interface CardProps {
  children: ReactNode;
  className?: string;
  title?: string;
  subtitle?: string;
  action?: ReactNode;
}

export function Card({ children, className = '', title, subtitle, action }: CardProps) {
  return (
    <div
      className={`bg-slate-900/70 border border-slate-800/90 rounded-xl p-5 backdrop-blur-md shadow-xl shadow-black/20 ${className}`}
    >
      {(title || action) && (
        <div className="flex items-center justify-between pb-4 mb-4 border-b border-slate-800/70">
          <div>
            {title && <h3 className="text-base font-semibold text-slate-100">{title}</h3>}
            {subtitle && <p className="text-xs text-slate-400 mt-0.5">{subtitle}</p>}
          </div>
          {action && <div>{action}</div>}
        </div>
      )}
      {children}
    </div>
  );
}

interface StatCardProps {
  label?: string;
  title?: string;
  value: string | number;
  subtext?: string;
  description?: string;
  icon?: ReactNode | React.ComponentType<{ className?: string }>;
  trend?: string;
  trendPositive?: boolean;
  change?: { value: string; positive: boolean };
  accentColor?: string;
}

export function StatCard({
  label,
  title,
  value,
  subtext,
  description,
  icon,
  trend,
  trendPositive,
  change,
  accentColor,
}: StatCardProps) {
  const displayLabel = title || label || '';
  const displaySubtext = description || subtext;
  const isPositive = change ? change.positive : trendPositive;
  const trendText = change ? change.value : trend;

  const renderIcon = () => {
    if (!icon) return null;
    if (React.isValidElement(icon)) return icon;
    if (typeof icon === 'function' || typeof icon === 'object') {
      const IconComponent = icon as React.ComponentType<{ className?: string }>;
      return <IconComponent className={`w-5 h-5 ${accentColor || 'text-slate-400'}`} />;
    }
    return null;
  };

  return (
    <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 backdrop-blur-md relative overflow-hidden transition-all hover:border-slate-700">
      <div className="flex items-center justify-between">
        <p className="text-xs uppercase font-semibold text-slate-400 tracking-wider">{displayLabel}</p>
        {icon && <div className="p-2 rounded-lg bg-slate-800/50 flex items-center justify-center">{renderIcon()}</div>}
      </div>
      <div className="mt-3 flex items-baseline justify-between">
        <p className={`text-3xl font-extrabold tracking-tight ${accentColor || 'text-slate-100'}`}>{value}</p>
        {trendText && (
          <span
            className={`text-xs font-semibold px-2 py-0.5 rounded ${
              isPositive
                ? 'text-emerald-400 bg-emerald-500/10'
                : 'text-rose-400 bg-rose-500/10'
            }`}
          >
            {trendText}
          </span>
        )}
      </div>
      {displaySubtext && <p className="text-xs text-slate-500 mt-2 font-medium">{displaySubtext}</p>}
    </div>
  );
}
