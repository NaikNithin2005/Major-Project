'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { AdminShell } from '@/components/layout/AdminShell';
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

export default function AdminUsersManagementPage() {
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
    const targetStatus = !user.is_active;
    const confirmMsg = targetStatus
      ? `Reactivate account for ${user.email}?`
      : `Deactivate account for ${user.email}? User will be forbidden from syncing detections.`;

    if (!confirm(confirmMsg)) return;

    setActingUserId(user.id);
    try {
      const updated = await api.users.updateStatus(user.id, targetStatus);
      setUsers((prev) =>
        prev.map((u) => (u.id === user.id ? { ...u, is_active: updated.is_active } : u))
      );
    } catch (err: any) {
      alert(`Status update failed: ${err.message}`);
    } finally {
      setActingUserId(null);
    }
  };

  const handleToggleRole = async (user: User) => {
    const targetRole: 'USER' | 'ADMIN' = user.role === 'ADMIN' ? 'USER' : 'ADMIN';
    const confirmMsg =
      targetRole === 'ADMIN'
        ? `Grant authoritative ADMINISTRATOR privileges to ${user.email}?`
        : `Demote ${user.email} to standard USER?`;

    if (!confirm(confirmMsg)) return;

    setActingUserId(user.id);
    try {
      const updated = await api.users.updateRole(user.id, targetRole);
      setUsers((prev) =>
        prev.map((u) => (u.id === user.id ? { ...u, role: updated.role } : u))
      );
    } catch (err: any) {
      alert(`Role assignment failed: ${err.message}`);
    } finally {
      setActingUserId(null);
    }
  };

  return (
    <AdminShell>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2">
              <Users className="w-6 h-6 text-cyan-400" />
              <span>User & Client Account Management</span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Inspect synchronized Firebase accounts, manage operational roles, and enforce RBAC access policies.
            </p>
          </div>

          <button
            onClick={() => fetchUsers()}
            disabled={isLoading}
            className="inline-flex items-center space-x-2 px-3 py-2 bg-slate-900 hover:bg-slate-800 text-slate-300 text-xs font-semibold rounded-lg border border-slate-700/60 shadow-sm transition-all"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-cyan-400' : ''}`} />
            <span>Refresh Users</span>
          </button>
        </div>

        {/* Users Table Card */}
        <Card className="overflow-hidden border-slate-800 bg-slate-900/90">
          {error && (
            <div className="p-6">
              <ErrorState
                title="Error Loading User Directory"
                message={error}
                onRetry={fetchUsers}
              />
            </div>
          )}

          {isLoading ? (
            <TableSkeleton rows={8} cols={6} />
          ) : users.length === 0 ? (
            <EmptyState
              icon={Users}
              title="No Users Registered"
              description="No user profiles found in the synchronized database."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-800 bg-slate-950/50 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                    <th className="py-3 px-4">User Details</th>
                    <th className="py-3 px-4">Firebase UID</th>
                    <th className="py-3 px-4">Role Claim</th>
                    <th className="py-3 px-4">Account Status</th>
                    <th className="py-3 px-4">Registered Date</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-xs">
                  {users.map((u) => {
                    const isActing = actingUserId === u.id;

                    return (
                      <tr key={u.id} className="hover:bg-slate-800/40 transition-colors">
                        {/* User Details */}
                        <td className="py-3.5 px-4">
                          <div className="font-semibold text-slate-200">
                            {u.display_name || 'Unnamed User'}
                          </div>
                          <div className="text-[11px] text-slate-400 font-mono">
                            {u.email}
                          </div>
                          {u.phone_number && (
                            <div className="text-[10px] text-slate-500">
                              {u.phone_number}
                            </div>
                          )}
                        </td>

                        {/* Firebase UID */}
                        <td className="py-3.5 px-4 font-mono text-[11px] text-slate-400">
                          <span className="truncate max-w-[120px] block" title={u.firebase_uid}>
                            {u.firebase_uid}
                          </span>
                        </td>

                        {/* Role Claim */}
                        <td className="py-3.5 px-4">
                          {u.role === 'ADMIN' ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500/15 text-amber-300 border border-amber-500/30">
                              <ShieldCheck className="w-3 h-3 mr-1" />
                              ADMIN
                            </span>
                          ) : (
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-slate-800 text-slate-300 border border-slate-700">
                              USER
                            </span>
                          )}
                        </td>

                        {/* Account Status */}
                        <td className="py-3.5 px-4">
                          {u.is_active ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                              Active
                            </span>
                          ) : (
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-rose-500/10 text-rose-400 border border-rose-500/20">
                              Suspended
                            </span>
                          )}
                        </td>

                        {/* Registered Date */}
                        <td className="py-3.5 px-4 text-slate-400 text-[11px] whitespace-nowrap">
                          {new Date(u.created_at).toLocaleDateString([], {
                            year: 'numeric',
                            month: 'short',
                            day: 'numeric',
                          })}
                        </td>

                        {/* Actions */}
                        <td className="py-3.5 px-4 text-right">
                          <div className="flex items-center justify-end space-x-2">
                            <button
                              onClick={() => handleToggleStatus(u)}
                              disabled={isActing}
                              className={`px-2.5 py-1 rounded text-[11px] font-semibold border transition-all disabled:opacity-50 ${
                                u.is_active
                                  ? 'bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 border-rose-500/30'
                                  : 'bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-300 border-emerald-500/30'
                              }`}
                            >
                              {u.is_active ? 'Deactivate' : 'Activate'}
                            </button>

                            <button
                              onClick={() => handleToggleRole(u)}
                              disabled={isActing}
                              className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-[11px] font-semibold border border-slate-700 transition-all disabled:opacity-50"
                              title="Toggle Role Assignment"
                            >
                              {u.role === 'ADMIN' ? 'Make User' : 'Make Admin'}
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination Footer */}
          {!isLoading && users.length > 0 && (
            <div className="px-6 py-4 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
              <div>
                Showing <span className="font-semibold text-slate-200">{(page - 1) * pageSize + 1}</span> to{' '}
                <span className="font-semibold text-slate-200">
                  {Math.min(page * pageSize, totalCount)}
                </span>{' '}
                of <span className="font-semibold text-slate-200">{totalCount}</span> users
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
