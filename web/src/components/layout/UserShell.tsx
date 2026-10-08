'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { LoadingSpinner } from '../ui/LoadingState';
import {
  Shield,
  ShieldAlert,
  ShieldCheck,
  LayoutDashboard,
  History,
  User,
  LogOut,
  Menu,
  X,
  ExternalLink,
  Smartphone,
  Lock,
} from 'lucide-react';

interface UserShellProps {
  children: React.ReactNode;
}

export function UserShell({ children }: UserShellProps) {
  const { user, isLoading, isAuthenticated, isAdmin, logout } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.push('/login');
    }
  }, [isLoading, isAuthenticated, router]);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <LoadingSpinner message="Authenticating with Squish Shield security service..." />
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return null;
  }

  const navLinks = [
    { name: 'Overview', href: '/', icon: LayoutDashboard },
    { name: 'Threat History', href: '/threats', icon: History },
    { name: 'Profile & Security', href: '/profile', icon: User },
  ];

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      {/* Top Navigation Bar */}
      <header className="sticky top-0 z-40 bg-slate-900/95 border-b border-slate-800/80 backdrop-blur-md">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            {/* Brand Logo */}
            <div className="flex items-center space-x-6">
              <Link href="/" className="flex items-center space-x-3 group">
                <div className="w-9 h-9 rounded-lg bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400 group-hover:border-cyan-400 transition-colors">
                  <Shield className="w-5 h-5" />
                </div>
                <div>
                  <span className="font-bold text-base text-slate-100 tracking-tight">
                    SQUISH<span className="text-cyan-400 font-extrabold ml-1">SHIELD</span>
                  </span>
                  <span className="block text-[10px] text-slate-400 font-medium tracking-wider uppercase">
                    Quishing & Smishing Defense
                  </span>
                </div>
              </Link>

              {/* Desktop Nav Links */}
              <nav className="hidden md:flex items-center space-x-1 pl-4">
                {navLinks.map((item) => {
                  const Icon = item.icon;
                  const isActive = pathname === item.href || (item.href !== '/' && pathname.startsWith(item.href));
                  return (
                    <Link
                      key={item.name}
                      href={item.href}
                      className={`inline-flex items-center space-x-2 px-3.5 py-1.5 rounded-md text-xs font-semibold transition-colors ${
                        isActive
                          ? 'bg-slate-800 text-cyan-400 border border-slate-700/60 shadow-sm'
                          : 'text-slate-300 hover:text-white hover:bg-slate-800/50'
                      }`}
                    >
                      <Icon className="w-4 h-4" />
                      <span>{item.name}</span>
                    </Link>
                  );
                })}
              </nav>
            </div>

            {/* Right-Side Controls */}
            <div className="flex items-center space-x-3">
              {/* Admin Console Switcher (Visible only if user is an ADMIN) */}
              {isAdmin && (
                <Link
                  href="/admin"
                  className="hidden sm:inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-md bg-amber-500/15 border border-amber-500/30 text-amber-300 hover:bg-amber-500/25 text-xs font-semibold transition-all shadow-sm"
                  title="Switch to Administrative Control Center"
                >
                  <Lock className="w-3.5 h-3.5" />
                  <span>Admin Console</span>
                  <ExternalLink className="w-3 h-3 ml-0.5 opacity-70" />
                </Link>
              )}

              {/* User Identity Chip */}
              <div className="flex items-center space-x-2.5 pl-2 border-l border-slate-800">
                <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-cyan-400 font-bold text-xs uppercase shadow-inner">
                  {user.email.charAt(0)}
                </div>
                <div className="hidden lg:block text-left">
                  <div className="text-xs font-medium text-slate-200 truncate max-w-[140px]">
                    {user.display_name || user.email.split('@')[0]}
                  </div>
                  <div className="flex items-center space-x-1 text-[10px] text-slate-400">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
                    <span>Protected</span>
                  </div>
                </div>
              </div>

              {/* Logout Button */}
              <button
                onClick={logout}
                className="p-2 rounded-md text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
                title="Sign out of Squish Shield"
              >
                <LogOut className="w-4 h-4" />
              </button>

              {/* Mobile Menu Button */}
              <button
                onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                className="md:hidden p-2 rounded-md text-slate-400 hover:text-white hover:bg-slate-800 focus:outline-none"
              >
                {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
              </button>
            </div>
          </div>
        </div>

        {/* Mobile Navigation Drawer */}
        {mobileMenuOpen && (
          <div className="md:hidden border-t border-slate-800 bg-slate-900 px-4 pt-3 pb-4 space-y-2">
            {navLinks.map((item) => {
              const Icon = item.icon;
              const isActive = pathname === item.href;
              return (
                <Link
                  key={item.name}
                  href={item.href}
                  onClick={() => setMobileMenuOpen(false)}
                  className={`flex items-center space-x-3 px-3 py-2 rounded-md text-sm font-medium ${
                    isActive
                      ? 'bg-slate-800 text-cyan-400 font-semibold'
                      : 'text-slate-300 hover:bg-slate-800/60'
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  <span>{item.name}</span>
                </Link>
              );
            })}

            {isAdmin && (
              <Link
                href="/admin"
                onClick={() => setMobileMenuOpen(false)}
                className="flex items-center space-x-3 px-3 py-2 rounded-md text-sm font-medium text-amber-300 bg-amber-500/10 border border-amber-500/20"
              >
                <Lock className="w-4 h-4" />
                <span>Switch to Admin Console</span>
              </Link>
            )}

            <div className="pt-2 border-t border-slate-800 text-xs text-slate-400 flex items-center justify-between">
              <span>Signed in as {user.email}</span>
              <button onClick={logout} className="text-rose-400 font-medium">
                Log out
              </button>
            </div>
          </div>
        )}
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 py-6 sm:py-8">
        {children}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800/80 bg-slate-900/50 py-4 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-2">
          <span>Squish Shield — Offline-First Quishing & Smishing Defense Platform</span>
          <span className="flex items-center space-x-2">
            <span className="w-2 h-2 rounded-full bg-emerald-400 inline-block"></span>
            <span>Real-time Multi-User Protection Active</span>
          </span>
        </div>
      </footer>
    </div>
  );
}
