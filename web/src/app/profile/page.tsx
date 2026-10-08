'use client';

import React, { useState } from 'react';
import { UserShell } from '@/components/layout/UserShell';
import { Card } from '@/components/ui/Card';
import { useAuth } from '@/context/AuthContext';
import {
  User,
  Shield,
  Key,
  Smartphone,
  CheckCircle2,
  Mail,
  Phone,
  Calendar,
  LogOut,
  ExternalLink,
  ShieldCheck,
  AlertCircle,
} from 'lucide-react';

export default function UserProfilePage() {
  const { user, resetPassword, logout } = useAuth();
  const [resetSent, setResetSent] = useState(false);
  const [isSendingReset, setIsSendingReset] = useState(false);
  const [resetError, setResetError] = useState<string | null>(null);

  const handlePasswordReset = async () => {
    if (!user?.email) return;
    setIsSendingReset(true);
    setResetError(null);
    try {
      await resetPassword(user.email);
      setResetSent(true);
    } catch (err: any) {
      setResetError(err.message || 'Could not send reset email.');
    } finally {
      setIsSendingReset(false);
    }
  };

  return (
    <UserShell>
      <div className="space-y-6 max-w-4xl mx-auto">
        {/* Header */}
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2">
            <User className="w-6 h-6 text-cyan-400" />
            <span>Account Profile & Security</span>
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Manage your verified identity, inspect device telemetry connections, and verify tenant isolation keys.
          </p>
        </div>

        {/* Profile Card */}
        <Card title="User Identity & Authentication" subtitle="Authoritative credentials verified by Firebase">
          <div className="space-y-4">
            <div className="flex items-center space-x-4 p-4 rounded-xl bg-slate-950/70 border border-slate-800">
              <div className="w-14 h-14 rounded-full bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400 font-extrabold text-xl uppercase shrink-0">
                {user?.email?.charAt(0) || 'U'}
              </div>
              <div>
                <div className="flex items-center space-x-2">
                  <h3 className="text-base font-bold text-slate-100">
                    {user?.display_name || user?.email?.split('@')[0] || 'Authenticated User'}
                  </h3>
                  <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-800 text-slate-300 border border-slate-700">
                    {user?.role || 'USER'}
                  </span>
                </div>
                <p className="text-xs text-slate-400 font-mono mt-0.5">{user?.email}</p>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div className="p-3 bg-slate-950/50 rounded-lg border border-slate-800">
                <span className="text-slate-400 block text-[11px]">Primary Ownership Key (UID)</span>
                <span className="font-mono text-cyan-400 break-all select-all font-semibold">
                  {user?.firebase_uid}
                </span>
              </div>

              <div className="p-3 bg-slate-950/50 rounded-lg border border-slate-800">
                <span className="text-slate-400 block text-[11px]">Account Status</span>
                <span className="text-emerald-400 font-semibold flex items-center space-x-1.5 mt-0.5">
                  <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
                  <span>{user?.is_active ? 'Active & Monitored' : 'Suspended'}</span>
                </span>
              </div>

              <div className="p-3 bg-slate-950/50 rounded-lg border border-slate-800">
                <span className="text-slate-400 block text-[11px]">Member Since</span>
                <span className="text-slate-200 font-medium">
                  {user?.created_at ? new Date(user.created_at).toLocaleDateString([], { dateStyle: 'long' }) : 'Recent'}
                </span>
              </div>

              <div className="p-3 bg-slate-950/50 rounded-lg border border-slate-800">
                <span className="text-slate-400 block text-[11px]">Phone Alert Sync</span>
                <span className="text-slate-200 font-medium">
                  {user?.phone_number || 'Not configured'}
                </span>
              </div>
            </div>
          </div>
        </Card>

        {/* Device & Synchronization Card */}
        <Card title="Mobile App & Real-Time Sync" subtitle="State synchronization with your Android client">
          <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 flex items-start space-x-4">
            <div className="p-2.5 rounded-lg bg-blue-500/10 text-blue-400 border border-blue-500/20 shrink-0">
              <Smartphone className="w-5 h-5" />
            </div>
            <div className="space-y-1">
              <div className="flex items-center space-x-2">
                <span className="text-xs font-semibold text-slate-100">Squish Shield Android Client</span>
                <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  Synchronized
                </span>
              </div>
              <p className="text-xs text-slate-400 leading-relaxed">
                When you log into the Android application with <span className="text-slate-300 font-mono">{user?.email}</span>, any QR code scanned or SMS analyzed is saved locally in Room and uploaded to this account dashboard via FastAPI and Firestore.
              </p>
            </div>
          </div>
        </Card>

        {/* Security & Credentials */}
        <Card title="Security & Authentication" subtitle="Password updates and session control">
          <div className="space-y-4">
            {resetSent && (
              <div className="p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/25 flex items-center space-x-2.5 text-xs text-emerald-300">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>Password reset instructions sent to your email address!</span>
              </div>
            )}

            {resetError && (
              <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/25 flex items-center space-x-2.5 text-xs text-rose-300">
                <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
                <span>{resetError}</span>
              </div>
            )}

            <div className="flex flex-col sm:flex-row items-center justify-between p-4 rounded-xl bg-slate-950/50 border border-slate-800 gap-3">
              <div>
                <div className="text-xs font-semibold text-slate-200">Reset Account Password</div>
                <div className="text-[11px] text-slate-400">
                  We will send a secure link to your email to configure a new password.
                </div>
              </div>
              <button
                onClick={handlePasswordReset}
                disabled={isSendingReset || resetSent}
                className="w-full sm:w-auto px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold border border-slate-700 transition-all disabled:opacity-50"
              >
                {isSendingReset ? 'Sending...' : 'Send Reset Link'}
              </button>
            </div>

            <div className="pt-2 flex justify-end">
              <button
                onClick={logout}
                className="inline-flex items-center space-x-2 px-4 py-2 rounded-lg bg-rose-600/10 hover:bg-rose-600/20 text-rose-400 text-xs font-semibold border border-rose-500/30 transition-all"
              >
                <LogOut className="w-4 h-4" />
                <span>Sign Out of All Sessions</span>
              </button>
            </div>
          </div>
        </Card>
      </div>
    </UserShell>
  );
}
