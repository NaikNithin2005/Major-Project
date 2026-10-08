'use client';

import React from 'react';
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js';
import { Doughnut } from 'react-chartjs-2';

ChartJS.register(ArcElement, Tooltip, Legend);

interface CategoryDoughnutProps {
  smsCount?: number;
  qrCount?: number;
  distribution?: Record<string, number>;
}

export function CategoryDoughnut({ smsCount, qrCount, distribution }: CategoryDoughnutProps) {
  // If distribution object is provided
  if (distribution) {
    const labels = Object.keys(distribution);
    const values = Object.values(distribution);
    const total = values.reduce((acc, val) => acc + val, 0);

    if (total === 0) {
      return (
        <div className="flex items-center justify-center h-64 text-slate-500 text-xs">
          No severity distribution recorded yet.
        </div>
      );
    }

    const colorMap: Record<string, { bg: string; border: string }> = {
      SAFE: { bg: 'rgba(16, 185, 129, 0.8)', border: '#059669' },
      LOW: { bg: 'rgba(59, 130, 246, 0.8)', border: '#2563eb' },
      MEDIUM: { bg: 'rgba(245, 158, 11, 0.8)', border: '#d97706' },
      HIGH: { bg: 'rgba(239, 68, 68, 0.8)', border: '#dc2626' },
    };

    const data = {
      labels: labels.map((l, i) => `${l} (${Math.round((values[i] / total) * 100)}%)`),
      datasets: [
        {
          data: values,
          backgroundColor: labels.map((l) => colorMap[l]?.bg || 'rgba(148, 163, 184, 0.8)'),
          borderColor: labels.map((l) => colorMap[l]?.border || '#64748b'),
          borderWidth: 1,
          hoverOffset: 4,
        },
      ],
    };

    const options = {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '70%',
      plugins: {
        legend: {
          position: 'bottom' as const,
          labels: {
            color: '#94a3b8',
            font: { size: 10 },
            padding: 8,
          },
        },
        tooltip: {
          backgroundColor: '#0f172a',
          borderColor: '#334155',
          borderWidth: 1,
          titleColor: '#f1f5f9',
          bodyColor: '#cbd5e1',
        },
      },
    };

    return (
      <div className="h-64 w-full flex flex-col items-center justify-center relative">
        <Doughnut data={data} options={options} />
        <div className="absolute top-[40%] flex flex-col items-center pointer-events-none">
          <span className="text-xl font-bold font-mono text-slate-200">{total}</span>
          <span className="text-[10px] text-slate-400 uppercase tracking-wider">Events</span>
        </div>
      </div>
    );
  }

  // Fallback to channel breakdown (smsCount + qrCount)
  const sc = smsCount || 0;
  const qc = qrCount || 0;
  const total = sc + qc;

  if (total === 0) {
    return (
      <div className="flex items-center justify-center h-64 text-slate-500 text-xs">
        No channel data recorded.
      </div>
    );
  }

  const smsPercent = Math.round((sc / total) * 100);
  const qrPercent = 100 - smsPercent;

  const data = {
    labels: [`Smishing (SMS) ${smsPercent}%`, `Quishing (QR) ${qrPercent}%`],
    datasets: [
      {
        data: [sc, qc],
        backgroundColor: ['rgba(6, 182, 212, 0.8)', 'rgba(168, 85, 247, 0.8)'],
        borderColor: ['#0891b2', '#9333ea'],
        borderWidth: 1,
        hoverOffset: 4,
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '70%',
    plugins: {
      legend: {
        position: 'bottom' as const,
        labels: {
          color: '#94a3b8',
          font: { size: 11 },
          padding: 12,
        },
      },
      tooltip: {
        backgroundColor: '#0f172a',
        borderColor: '#334155',
        borderWidth: 1,
        titleColor: '#f1f5f9',
        bodyColor: '#cbd5e1',
      },
    },
  };

  return (
    <div className="h-64 w-full flex flex-col items-center justify-center relative">
      <Doughnut data={data} options={options} />
      <div className="absolute top-[40%] flex flex-col items-center pointer-events-none">
        <span className="text-xl font-bold font-mono text-slate-200">{total}</span>
        <span className="text-[10px] text-slate-400 uppercase tracking-wider">Events</span>
      </div>
    </div>
  );
}
