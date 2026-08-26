import React from 'react';

export default function AdminDashboardPage() {
  return (
    <main className="min-h-screen bg-slate-950 text-slate-100 p-8 font-sans">
      <header className="flex justify-between items-center pb-6 border-b border-slate-800">
        <div>
          <h1 className="text-3xl font-bold bg-gradient-to-r from-blue-400 to-teal-400 bg-clip-text text-transparent">
            Quishing & Smishing Admin Dashboard
          </h1>
          <p className="text-slate-400 text-sm mt-1">
            Real-Time Threat Intelligence & Security Analytics Subsystem
          </p>
        </div>
        <div className="flex items-center space-x-3">
          <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            Phase 0 Foundation Shell
          </span>
        </div>
      </header>

      <section className="mt-8 grid grid-cols-1 md:grid-cols-4 gap-6">
        <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-5 backdrop-blur-md">
          <p className="text-xs uppercase font-semibold text-slate-400 tracking-wider">System Status</p>
          <p className="text-2xl font-bold text-emerald-400 mt-2">Operational</p>
          <span className="text-xs text-slate-500 mt-1 block">API Gateway Ready</span>
        </div>

        <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-5 backdrop-blur-md">
          <p className="text-xs uppercase font-semibold text-slate-400 tracking-wider">Total Threats Tracked</p>
          <p className="text-2xl font-bold text-slate-200 mt-2">0</p>
          <span className="text-xs text-slate-500 mt-1 block">Phase 0 Baseline</span>
        </div>

        <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-5 backdrop-blur-md">
          <p className="text-xs uppercase font-semibold text-slate-400 tracking-wider">Active Sensors</p>
          <p className="text-2xl font-bold text-blue-400 mt-2">0</p>
          <span className="text-xs text-slate-500 mt-1 block">On-Device Offline Inference</span>
        </div>

        <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-5 backdrop-blur-md">
          <p className="text-xs uppercase font-semibold text-slate-400 tracking-wider">AI Models Version</p>
          <p className="text-2xl font-bold text-purple-400 mt-2">v1.0.0-p0</p>
          <span className="text-xs text-slate-500 mt-1 block">TinyBERT + XGBoost</span>
        </div>
      </section>

      <section className="mt-8 bg-slate-900/40 border border-slate-800/80 rounded-xl p-6">
        <h2 className="text-xl font-semibold text-slate-200 mb-2">Phase 0 Verification Notice</h2>
        <p className="text-slate-400 text-sm leading-relaxed">
          The Web Admin Dashboard repository shell is initialized and independently structured with Next.js, React, and Tailwind CSS.
          Protected API telemetry integration and PostgreSQL analytics components will be connected in Phase 9 according to <code className="text-teal-300">Phases.md</code>.
        </p>
      </section>
    </main>
  );
}
