'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { DashboardShell } from '@/components/layout/DashboardShell';
import { Card } from '@/components/ui/Card';
import { TableSkeleton } from '@/components/ui/LoadingState';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { api } from '@/services/api';
import { AuditLogItem } from '@/types';
import {
  FileText,
  ShieldCheck,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  Clock,
  Globe,
  Terminal,
} from 'lucide-react';

export default function AuditLogsPage() {
  const [logs, setLogs] = useState<AuditLogItem[]>([]);
  const [totalCount, setTotalCount] = useState<number>(0);
  const [page, setPage] = useState<number>(1);
  const [pageSize] = useState<number>(20);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [actionFilter, setActionFilter] = useState<string>('');
  const [resourceFilter, setResourceFilter] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchLogs = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await api.auditLogs.list(
        page,
        pageSize,
        actionFilter || undefined,
        resourceFilter || undefined
      );
      setLogs(res.items || []);
      setTotalCount(res.total || 0);
      setTotalPages(res.pages || 1);
    } catch (err: any) {
      setError(err.message || 'Failed to retrieve administrative audit log.');
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize, actionFilter, resourceFilter]);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  return (
    <DashboardShell>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2">
              <FileText className="w-6 h-6 text-cyan-400" />
              <span>Immutable Administrative Audit Trail</span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Cryptographically backed compliance record of all administrative operations, model updates, and high-risk events.
            </p>
          </div>

          <button
            onClick={fetchLogs}
            disabled={isLoading}
            className="inline-flex items-center space-x-2 px-3 py-2 bg-slate-900 hover:bg-slate-800 text-slate-300 text-xs font-semibold rounded-lg border border-slate-700/60 shadow-sm transition-all"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-cyan-400' : ''}`} />
            <span>Refresh Trail</span>
          </button>
        </div>

        {/* Filter Bar */}
        <Card className="p-4 bg-slate-900/90 border-slate-800">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <input
              type="text"
              placeholder="Filter by action (e.g. THREAT_STATUS_UPDATED)..."
              value={actionFilter}
              onChange={(e) => {
                setActionFilter(e.target.value);
                setPage(1);
              }}
              className="w-full px-3.5 py-2 bg-slate-950/70 border border-slate-800 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500/50"
            />
            <input
              type="text"
              placeholder="Filter by resource type (e.g. THREAT_EVENT, USER)..."
              value={resourceFilter}
              onChange={(e) => {
                setResourceFilter(e.target.value);
                setPage(1);
              }}
              className="w-full px-3.5 py-2 bg-slate-950/70 border border-slate-800 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500/50"
            />
          </div>
        </Card>

        {error && (
          <ErrorState
            title="Audit Trail Error"
            message={error}
            onRetry={fetchLogs}
          />
        )}

        {isLoading ? (
          <TableSkeleton rows={8} />
        ) : logs.length === 0 ? (
          <EmptyState
            title="No Audit Records"
            description="No administrative activities or audited security operations have been logged yet."
          />
        ) : (
          <Card className="p-0 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="bg-slate-900/90 border-b border-slate-800 text-slate-400 uppercase tracking-wider font-semibold text-[11px]">
                    <th className="py-3.5 px-4">Timestamp</th>
                    <th className="py-3.5 px-4">Action</th>
                    <th className="py-3.5 px-4">Resource</th>
                    <th className="py-3.5 px-4">Actor</th>
                    <th className="py-3.5 px-4">IP Origin</th>
                    <th className="py-3.5 px-4">Metadata Payload</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-sans">
                  {logs.map((log) => (
                    <tr key={log.id} className="hover:bg-slate-800/30 transition-colors">
                      <td className="py-3.5 px-4 text-slate-400 whitespace-nowrap">
                        <div className="flex items-center space-x-1.5 font-mono text-[11px]">
                          <Clock className="w-3.5 h-3.5 text-slate-500" />
                          <span>{new Date(log.timestamp).toLocaleString()}</span>
                        </div>
                      </td>

                      <td className="py-3.5 px-4">
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-mono font-bold bg-cyan-500/10 text-cyan-300 border border-cyan-500/25">
                          <Terminal className="w-3 h-3 mr-1" />
                          {log.action}
                        </span>
                      </td>

                      <td className="py-3.5 px-4">
                        <div className="text-slate-300 font-mono text-[11px]">
                          <span className="text-slate-400 font-semibold">{log.resource_type}:</span>{' '}
                          <span className="text-slate-200">
                            {log.resource_id ? `${log.resource_id.slice(0, 8)}...` : 'GLOBAL'}
                          </span>
                        </div>
                      </td>

                      <td className="py-3.5 px-4 text-slate-400 font-mono text-[11px]">
                        {log.actor_user_id ? `${log.actor_user_id.slice(0, 8)}...` : 'SYSTEM'}
                      </td>

                      <td className="py-3.5 px-4 text-slate-400 font-mono text-[11px]">
                        <div className="flex items-center space-x-1">
                          <Globe className="w-3 h-3 text-slate-500" />
                          <span>{log.ip_address || '127.0.0.1'}</span>
                        </div>
                      </td>

                      <td className="py-3.5 px-4 text-slate-300 font-mono text-[11px] max-w-xs truncate">
                        {log.metadata_json ? (
                          <span title={JSON.stringify(log.metadata_json)}>
                            {JSON.stringify(log.metadata_json)}
                          </span>
                        ) : (
                          <span className="text-slate-600">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="p-4 border-t border-slate-800 bg-slate-900/60 flex items-center justify-between">
              <span className="text-xs text-slate-400">
                Total <span className="font-semibold text-slate-200">{totalCount}</span> audit log entries
              </span>

              <div className="flex items-center space-x-2">
                <button
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={page <= 1 || isLoading}
                  className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 disabled:opacity-40 disabled:pointer-events-none transition-colors"
                  aria-label="Previous Page"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <span className="text-xs font-semibold px-2 text-slate-300">
                  Page {page} of {totalPages}
                </span>
                <button
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  disabled={page >= totalPages || isLoading}
                  className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 disabled:opacity-40 disabled:pointer-events-none transition-colors"
                  aria-label="Next Page"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          </Card>
        )}
      </div>
    </DashboardShell>
  );
}
