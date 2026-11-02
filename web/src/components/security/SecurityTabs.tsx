'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ShieldCheck, UserX, AlertTriangle, UserCheck, ShieldAlert, FileX } from 'lucide-react';

const tabs = [
  { name: 'Trusted Users', href: '/security/trusted-users', icon: ShieldCheck },
  { name: 'Untrusted Users', href: '/security/untrusted-users', icon: UserCheck },
  { name: 'Quarantined SMS', href: '/security/quarantined-sms', icon: AlertTriangle },
  { name: 'Blocked Users', href: '/security/blocked-users', icon: UserX },
  { name: 'Blocked SMS', href: '/security/blocked-sms', icon: FileX },
];

export function SecurityTabs() {
  const pathname = usePathname();

  return (
    <div className="flex border-b border-slate-800 space-x-1 sm:space-x-2 overflow-x-auto pb-px">
      {tabs.map((tab) => {
        const Icon = tab.icon;
        const isActive = pathname === tab.href;
        return (
          <Link
            key={tab.name}
            href={tab.href}
            className={`inline-flex items-center space-x-2 px-3.5 py-2.5 text-xs sm:text-sm font-semibold rounded-t-lg transition-all border-b-2 whitespace-nowrap ${
              isActive
                ? 'border-cyan-400 text-cyan-400 bg-slate-900/60'
                : 'border-transparent text-slate-400 hover:text-slate-200 hover:bg-slate-900/30'
            }`}
          >
            <Icon className="w-4 h-4" />
            <span>{tab.name}</span>
          </Link>
        );
      })}
    </div>
  );
}

