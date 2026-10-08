'use client';

import React from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js';
import { Bar } from 'react-chartjs-2';

ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend);

interface RiskDistributionBarProps {
  safe?: number;
  low?: number;
  medium?: number;
  high?: number;
  safeCount?: number;
  lowCount?: number;
  mediumCount?: number;
  highCount?: number;
}

export function RiskDistributionBar({
  safe,
  low,
  medium,
  high,
  safeCount,
  lowCount,
  mediumCount,
  highCount,
}: RiskDistributionBarProps) {
  const s = safe ?? safeCount ?? 0;
  const l = low ?? lowCount ?? 0;
  const m = medium ?? mediumCount ?? 0;
  const h = high ?? highCount ?? 0;

  const data = {
    labels: ['Safe (0-25)', 'Low (26-50)', 'Medium (51-75)', 'High (76-100)'],
    datasets: [
      {
        label: 'Threat Count',
        data: [s, l, m, h],
        backgroundColor: [
          'rgba(16, 185, 129, 0.75)', // emerald
          'rgba(14, 165, 233, 0.75)', // sky
          'rgba(245, 158, 11, 0.75)', // amber
          'rgba(244, 63, 94, 0.75)',  // rose
        ],
        borderColor: ['#10b981', '#0ea5e9', '#f59e0b', '#f43f5e'],
        borderWidth: 1,
        borderRadius: 6,
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: false,
      },
      tooltip: {
        backgroundColor: '#0f172a',
        borderColor: '#334155',
        borderWidth: 1,
        titleColor: '#f1f5f9',
        bodyColor: '#cbd5e1',
      },
    },
    scales: {
      x: {
        grid: {
          display: false,
          color: '#1e293b',
        },
        ticks: {
          color: '#94a3b8',
          font: { size: 11 },
        },
      },
      y: {
        beginAtZero: true,
        grid: {
          color: 'rgba(51, 65, 85, 0.4)',
        },
        ticks: {
          color: '#94a3b8',
          font: { size: 10 },
          precision: 0,
        },
      },
    },
  };

  return (
    <div className="h-64 w-full">
      <Bar data={data} options={options} />
    </div>
  );
}
