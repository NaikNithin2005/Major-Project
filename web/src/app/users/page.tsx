'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { DashboardShell } from '@/components/layout/DashboardShell';
import { Card } from '@/components/ui/Card';
import { TableSkeleton } from '@/components/ui/LoadingState';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { api } from '@/services/api';
import { User } from '@/types';
import {
  Users,
  UserCheck,
  UserX,
  Shield,
  ShieldCheck,
  ChevronLeft,
  ChevronRight,
  RefreshCw,
  Clock,
  Key,
} from 'lucide-react';

export default function UsersManagementPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [totalCount, setTotalCount] = useState<number>(0);
  const [page, setPage] = useState<number>(1);
  const [pageSize] = useState<number>(20);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [actingUserId, setActingUserId] = useState<string | null>(null);

  const fetchUsers = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await api.users.list(page, pageSize);
      setUsers(res.items || []);
      setTotalCount(res.total || 0);
      setTotalPages(res.pages || 1);
    } catch (err: any) {
      setError(err.message || 'Failed to retrieve registered users.');
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleToggleStatus = async (user: User) => {
    setActingUserId(user.id);
    try {
      const newActive = !user.is_active;
      const updated = await api.users.updateStatus(user.id, newActive);
      setUsers((prev) =>
        prev.map((u) => (u.id === user.id ? { ...u, is_active: updated.is_active } : u))
      );
    } catch (err: any) {
      alert(`Action failed: ${err.message}`);
    } finally {
      setActingUserId(null);
    }
  };

  const handleRoleChange = async (user: User, newRole: 'USER' | 'ADMIN') => {
    if (user.role === newRole) return;
    const confirmMsg = `Are you sure you want to change role for ${user.email} to ${newRole}?`;
    if (!confirm(confirmMsg)) return;

    setActingUserId(user.id);
    try {
      const updated = await api.users.updateRole(user.id, newRole);
      setUsers((prev) =>
        prev.map((u) => (u.id === user.id ? { ...u, role: updated.role } : u))
      );
    } catch (err: any) {
      alert(`Failed to update role: ${err.message}`);
    } finally {
      setActingUserId(null);
    }
  };

  return (
    <DashboardShell>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2">
              <Users className="w-6 h-6 text-cyan-400" />
              <span>User & Role Governance</span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Authorized administrator directory with role assignment and account state management.
            </p>
          </div>

          <button
            onClick={fetchUsers}
            disabled={isLoading}
            className="inline-flex items-center space-x-2 px-3 py-2 bg-slate-900 hover:bg-slate-800 text-slate-300 text-xs font-semibold rounded-lg border border-slate-700/60 shadow-sm transition-all"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-cyan-400' : ''}`} />
            <span>Refresh Directory</span>
          </button>
        </div>

        {error && (
          <ErrorState
            title="User Directory Error"
            message={error}
            onRetry={fetchUsers}
          />
        )}

        {isLoading ? (
          <TableSkeleton rows={8} />
        ) : users.length === 0 ? (
          <EmptyState
            title="No Users Registered"
            description="No mobile client or administrator accounts have been registered in the database yet."
          />
        ) : (
          <Card className="p-0 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="bg-slate-900/90 border-b border-slate-800 text-slate-400 uppercase tracking-wider font-semibold text-[11px]">
                    <th className="py-3.5 px-4">User</th>
                    <th className="py-3.5 px-4">Role</th>
                    <th className="py-3.5 px-4">Account Status</th>
                    <th className="py-3.5 px-4">Registered Date</th>
                    <th className="py-3.5 px-4">Last Activity</th>
                    <th className="py-3.5 px-4 text-right">Administrative Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-sans">
                  {users.map((u) => {
                    const isActing = actingUserId === u.id;

                    return (
                      <tr key={u.id} className="hover:bg-slate-800/30 transition-colors">
                        <td className="py-3.5 px-4">
                          <div className="flex items-center space-x-3">
                            <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center font-bold text-xs text-cyan-400 shrink-0">
                              {u.email ? u.email.charAt(0).toUpperCase() : 'U'}
                            </div>
                            <div>
                              <p className="font-semibold text-slate-200">
                                {u.display_name || u.email.split('@')[0]}
                              </p>
                              <p className="text-[11px] text-slate-400 font-mono">{u.email}</p>
                            </div>
                          </div>
                        </td>

                        <td className="py-3.5 px-4">
                          <div className="flex items-center space-x-2">
                            {u.role === 'ADMIN' ? (
                              <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-amber-500/15 text-amber-300 border border-amber-500/30">
                                <ShieldCheck className="w-3 h-3 mr-1" />
                                ADMIN
                              </span>
                            ) : (
                              <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-slate-800 text-slate-300 border border-slate-700">
                                USER
                              </span>
                            )}
                          </div>
                        </td>

                        <td className="py-3.5 px-4">
                          {u.is_active ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                              Active
                            </span>
                          ) : (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-rose-500/15 text-rose-400 border border-rose-500/30">
                              Suspended
                            </span>
                          )}
                        </td>

                        <td className="py-3.5 px-4 text-slate-400">
                          {new Date(u.created_at).toLocaleDateString()}
                        </td>

                        <td className="py-3.5 px-4 text-slate-400">
                          <div className="flex items-center space-x-1.5">
                            <Clock className="w-3 h-3 text-slate-500" />
                            <span>
                              {u.last_seen_at
                                ? new Date(u.last_seen_at).toLocaleDateString()
                                : 'Active Now'}
                            </span>
                          </div>
                        </td>

                        <td className="py-3.5 px-4 text-right space-x-2">
                          {/* Role Toggle */}
                          <button
                            onClick={() =>
                              handleRoleChange(u, u.role === 'ADMIN' ? 'USER' : 'ADMIN')
                            }
                            disabled={isActing}
                            className="text-[11px] font-semibold text-slate-300 hover:text-cyan-300 px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 border border-slate-700 transition-all disabled:opacity-50"
                            title="Toggle Role"
                          >
                            Set {u.role === 'ADMIN' ? 'USER' : 'ADMIN'}
                          </button>

                          {/* Status Toggle */}
                          <button
                            onClick={() => handleToggleStatus(u)}
                            disabled={isActing}
                            className={`text-[11px] font-semibold px-2.5 py-1 rounded border transition-all disabled:opacity-50 ${
                              u.is_active
                                ? 'bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 border-rose-500/30'
                                : 'bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-300 border-emerald-500/30'
                            }`}
                          >
                            {u.is_active ? 'Suspend' : 'Activate'}
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="p-4 border-t border-slate-800 bg-slate-900/60 flex items-center justify-between">
              <span className="text-xs text-slate-400">
                Total <span className="font-semibold text-slate-200">{totalCount}</span> registered users
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
