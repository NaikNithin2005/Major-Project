import React from 'react';
import { Loader2 } from 'lucide-react';

interface LoadingStateProps {
  message?: string;
  rows?: number;
  cols?: number;
}

export function LoadingSpinner({ message = 'Loading security data...' }: { message?: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 px-4 space-y-3" role="status" aria-live="polite">
      <Loader2 className="w-8 h-8 text-cyan-400 animate-spin" />
      <p className="text-sm font-medium text-slate-400 tracking-wide">{message}</p>
    </div>
  );
}

export function TableSkeleton({ rows = 5, cols }: LoadingStateProps) {
  return (
    <div className="w-full space-y-3 animate-pulse p-4" role="status" aria-label="Loading table content">
      <div className="h-10 bg-slate-800/60 rounded-lg w-full" />
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="h-14 bg-slate-900/60 rounded-lg w-full border border-slate-800/40" />
      ))}
    </div>
  );
}
