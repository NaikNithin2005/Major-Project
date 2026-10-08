'use client';

import React from 'react';
import { useAuth } from '@/context/AuthContext';
import { Menu, LogOut, ShieldCheck, UserCheck } from 'lucide-react';

interface TopbarProps {
  onToggleSidebar: () => void;
}

export function Topbar({ onToggleSidebar }: TopbarProps) {
  const { user, logout } = useAuth();

  return (
    <header className="h-16 bg-slate-900/90 border-b border-slate-800 backdrop-blur-md px-6 flex items-center justify-between sticky top-0 z-30">
      <div className="flex items-center space-x-4">
        <button
          onClick={onToggleSidebar}
          className="lg:hidden p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-slate-800 focus:outline-none"
          aria-label="Open Sidebar"
        >
          <Menu className="w-5 h-5" />
        </button>

        <div className="hidden sm:flex items-center space-x-2 text-xs text-slate-400">
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-medium">
            <ShieldCheck className="w-3.5 h-3.5 mr-1" />
            Backend Connected
          </span>
          <span className="text-slate-600">•</span>
          <span>FastAPI REST Proxy</span>
        </div>
      </div>

      <div className="flex items-center space-x-4">
        {/* Administrator identity info */}
        <div className="flex items-center space-x-3 pr-2">
          <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-cyan-400 font-bold text-xs">
            {user?.email ? user.email.charAt(0).toUpperCase() : 'A'}
          </div>
          <div className="hidden md:block text-left">
            <div className="flex items-center space-x-1.5">
              <span className="text-xs font-semibold text-slate-200">
                {user?.display_name || user?.email?.split('@')[0] || 'Administrator'}
              </span>
              <span className="inline-flex items-center px-1.5 py-0.2 rounded text-[10px] font-bold bg-amber-500/15 text-amber-300 border border-amber-500/30">
                ADMIN
              </span>
            </div>
            <p className="text-[11px] text-slate-400 truncate max-w-[160px]">
              {user?.email}
            </p>
          </div>
        </div>

        {/* Logout button */}
        <button
          onClick={logout}
          className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 border border-transparent hover:border-rose-500/20 transition-colors"
          title="Sign out of Admin Dashboard"
        >
          <LogOut className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Logout</span>
        </button>
      </div>
    </header>
  );
}
