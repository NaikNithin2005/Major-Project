'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { AdminShell } from '@/components/layout/AdminShell';
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

export default function AdminAuditLogsPage() {
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
    <AdminShell>
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2">
              <FileText className="w-6 h-6 text-amber-400" />
              <span>Immutable System Audit Trail</span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Append-only audit log tracking administrative state mutations, role elevations, and high-severity threat detections.
            </p>
          </div>

          <button
            onClick={() => fetchLogs()}
            disabled={isLoading}
            className="inline-flex items-center space-x-2 px-3 py-2 bg-slate-900 hover:bg-slate-800 text-slate-300 text-xs font-semibold rounded-lg border border-slate-700/60 shadow-sm transition-all"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-amber-400' : ''}`} />
            <span>Refresh Audit Log</span>
          </button>
        </div>

        {/* Filter Bar */}
        <Card className="p-4 bg-slate-900/90 border-slate-800">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <input
                type="text"
                placeholder="Filter by action (e.g. THREAT_STATUS_UPDATED, HIGH_RISK)..."
                value={actionFilter}
                onChange={(e) => {
                  setActionFilter(e.target.value);
                  setPage(1);
                }}
                className="w-full px-3 py-2 bg-slate-950/70 border border-slate-800 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-amber-500/50"
              />
            </div>
            <div>
              <input
                type="text"
                placeholder="Filter by resource type (e.g. THREAT_EVENT, USER)..."
                value={resourceFilter}
                onChange={(e) => {
                  setResourceFilter(e.target.value);
                  setPage(1);
                }}
                className="w-full px-3 py-2 bg-slate-950/70 border border-slate-800 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-amber-500/50"
              />
            </div>
          </div>
        </Card>

        {/* Audit Log Table */}
        <Card className="overflow-hidden border-slate-800 bg-slate-900/90">
          {error && (
            <div className="p-6">
              <ErrorState
                title="Error Loading Audit Trail"
                message={error}
                onRetry={fetchLogs}
              />
            </div>
          )}

          {isLoading ? (
            <TableSkeleton rows={8} cols={6} />
          ) : logs.length === 0 ? (
            <EmptyState
              icon={FileText}
              title="No Audit Entries Recorded"
              description="No audit trail entries matched the specified criteria."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-800 bg-slate-950/50 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                    <th className="py-3 px-4">Action</th>
                    <th className="py-3 px-4">Resource Target</th>
                    <th className="py-3 px-4">Actor ID</th>
                    <th className="py-3 px-4">Origin IP</th>
                    <th className="py-3 px-4">Metadata Payload</th>
                    <th className="py-3 px-4">Timestamp</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-xs">
                  {logs.map((log) => (
                    <tr key={log.id} className="hover:bg-slate-800/40 transition-colors">
                      <td className="py-3.5 px-4 font-mono font-semibold text-cyan-400">
                        {log.action}
                      </td>
                      <td className="py-3.5 px-4 font-mono text-[11px] text-slate-300">
                        <span className="text-slate-400">{log.resource_type}:</span>{' '}
                        {log.resource_id ? `${log.resource_id.substring(0, 10)}...` : 'N/A'}
                      </td>
                      <td className="py-3.5 px-4 font-mono text-[11px] text-slate-400">
                        {log.actor_user_id ? `${log.actor_user_id.substring(0, 8)}...` : 'SYSTEM'}
                      </td>
                      <td className="py-3.5 px-4 text-slate-400 font-mono text-[11px]">
                        {log.ip_address || '127.0.0.1'}
                      </td>
                      <td className="py-3.5 px-4 max-w-xs font-mono text-[10px] text-slate-400 truncate">
                        {log.metadata_json ? JSON.stringify(log.metadata_json) : '{}'}
                      </td>
                      <td className="py-3.5 px-4 text-slate-400 text-[11px] whitespace-nowrap">
                        {new Date(log.timestamp).toLocaleDateString([], {
                          month: 'short',
                          day: 'numeric',
                        })}{' '}
                        {new Date(log.timestamp).toLocaleTimeString([], {
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination Footer */}
          {!isLoading && logs.length > 0 && (
            <div className="px-6 py-4 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
              <div>
                Showing <span className="font-semibold text-slate-200">{(page - 1) * pageSize + 1}</span> to{' '}
                <span className="font-semibold text-slate-200">
                  {Math.min(page * pageSize, totalCount)}
                </span>{' '}
                of <span className="font-semibold text-slate-200">{totalCount}</span> entries
              </div>

              <div className="flex items-center space-x-2">
                <button
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={page <= 1}
                  className="p-1.5 rounded-lg border border-slate-800 bg-slate-950 text-slate-300 hover:bg-slate-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <span className="font-mono text-xs px-2 text-slate-300">
                  {page} / {totalPages}
                </span>
                <button
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  disabled={page >= totalPages}
                  className="p-1.5 rounded-lg border border-slate-800 bg-slate-950 text-slate-300 hover:bg-slate-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}
        </Card>
      </div>
    </AdminShell>
  );
}
