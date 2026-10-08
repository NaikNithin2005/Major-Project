import React, { ReactNode } from 'react';
import { ShieldCheck } from 'lucide-react';

interface EmptyStateProps {
  title?: string;
  description?: string;
  icon?: ReactNode | React.ComponentType<{ className?: string }>;
  action?: ReactNode;
  actionLabel?: string;
  onAction?: () => void;
}

export function EmptyState({
  title = 'No threats detected',
  description = 'No matching security records were found for the current query or filter criteria.',
  icon,
  action,
  actionLabel,
  onAction,
}: EmptyStateProps) {
  const renderIcon = () => {
    if (!icon) return <ShieldCheck className="w-8 h-8 text-emerald-400" />;
    if (React.isValidElement(icon)) return icon;
    if (typeof icon === 'function' || typeof icon === 'object') {
      const IconComponent = icon as React.ComponentType<{ className?: string }>;
      return <IconComponent className="w-8 h-8 text-cyan-400" />;
    }
    return null;
  };

  return (
    <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
      <div className="p-4 rounded-full bg-slate-800/60 border border-slate-700/50 text-slate-400 mb-4 shadow-inner">
        {renderIcon()}
      </div>
      <h4 className="text-base font-semibold text-slate-200">{title}</h4>
      <p className="text-xs text-slate-400 max-w-sm mt-1 mb-6 leading-relaxed">
        {description}
      </p>
      {action && <div>{action}</div>}
      {actionLabel && onAction && (
        <button
          onClick={onAction}
          className="px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold border border-slate-700 transition-colors"
        >
          {actionLabel}
        </button>
      )}
    </div>
  );
}
