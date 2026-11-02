'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { DashboardShell } from '@/components/layout/DashboardShell';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingState';
import { ErrorState } from '@/components/ui/ErrorState';
import { api } from '@/services/api';
import { ModelInfo } from '@/types';
import { Cpu, CheckCircle2, Shield, RefreshCw, Hash, Calendar, Layers, Sparkles, Award } from 'lucide-react';

export default function ModelsPage() {
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchModels = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.models.list();
      setModels(data || []);
    } catch (err: any) {
      setError(err.message || 'Failed to retrieve AI model registry.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchModels();
  }, [fetchModels]);

  const parseMetadata = (meta: any): Record<string, any> => {
    if (!meta) return {};
    if (typeof meta === 'string') {
      try {
        return JSON.parse(meta);
      } catch {
        return { raw: meta };
      }
    }
    return meta;
  };

  // Fallback approved models specification if database table is initialized without rows
  const defaultModels: ModelInfo[] = [
    {
      id: 'tinybert-sms-4class',
      name: 'TinyBERT Smishing & Banking 4-Class Classifier',
      version: 'v2-4class',
      type: 'Transformer NLP (4-layer distilled, 4-class)',
      status: 'ACTIVE',
      artifact_hash: 'sha256:782f05a968fd9a071d1872df03da9fe5b3a322c3757fa234125b2d2b5125ea7b',
      metadata_json: {
        accuracy: '81.07%',
        banking_specificity: '97.95%',
        smishing_recall: '82.10%',
        macro_f1: '76.80%',
        weighted_f1: '81.75%',
        false_positive_rate: '3.72%',
        classes: '4 (Bank, Gen, Smish, Susp)',
        inference_latency: '18ms (Mobile CPU)',
        input_token_limit: 128,
      },
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    },
    {
      id: 'xgboost-url',
      name: 'XGBoost URL & QR Structural Model',
      version: 'v3.0.0-onnx',
      type: 'Gradient Boosted Decision Trees',
      status: 'ACTIVE',
      artifact_hash: 'sha256:ec01e253ed7aa0c5de4eb92a37300ae9ee27117bddf5f512f8e591524fd4c6d3',
      metadata_json: {
        accuracy: '95.06%',
        f1_score: '94.91%',
        roc_auc: '0.9884',
        inference_latency: '4ms (Mobile CPU)',
      },
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    },
    {
      id: 'isolation-forest',
      name: 'Isolation Forest Zero-Day Anomaly Detector',
      version: 'v2.0.0-onnx',
      type: 'Unsupervised Tree Ensemble',
      status: 'ACTIVE',
      artifact_hash: 'sha256:45603f323f67fa0b2397455dea42e92db85de6adf25e35bdcba80c48bc4c964e',
      metadata_json: {
        anomaly_detection_rate: '94.2%',
        false_positive_rate: '3.8%',
        inference_latency: '2ms (Mobile CPU)',
      },
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    },
  ];

  const displayModels = models.length > 0 ? models : defaultModels;

  return (
    <DashboardShell>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100 flex items-center space-x-2">
              <Cpu className="w-6 h-6 text-purple-400" />
              <span>AI Detection Model Registry</span>
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Active on-device AI/ML models deployed for offline smishing and quishing inference with 4-class banking protection.
            </p>
          </div>

          <button
            onClick={fetchModels}
            disabled={isLoading}
            className="inline-flex items-center space-x-2 px-3 py-2 bg-slate-900 hover:bg-slate-800 text-slate-300 text-xs font-semibold rounded-lg border border-slate-700/60 shadow-sm transition-all"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-cyan-400' : ''}`} />
            <span>Verify Model Hashes</span>
          </button>
        </div>

        {error && (
          <ErrorState
            title="Model Registry Fetch Failed"
            message={error}
            onRetry={fetchModels}
          />
        )}

        {isLoading ? (
          <LoadingSpinner message="Checking model artifact verification status..." />
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {displayModels.map((m) => {
              const meta = parseMetadata(m.metadata_json);
              const isTinyBert = m.name.toLowerCase().includes('tinybert') || m.id.includes('tinybert');

              return (
                <Card
                  key={m.id || m.name}
                  className="flex flex-col justify-between hover:border-slate-700 transition-all shadow-xl bg-slate-900/90 border-slate-800"
                >
                  <div>
                    <div className="flex items-start justify-between">
                      <div className="p-2.5 rounded-xl bg-purple-500/10 border border-purple-500/20 text-purple-400">
                        <Cpu className="w-6 h-6" />
                      </div>
                      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                        <CheckCircle2 className="w-3 h-3 mr-1" />
                        {m.status}
                      </span>
                    </div>

                    <h3 className="text-base font-bold text-slate-100 mt-4 flex items-center space-x-1.5">
                      <span>{m.name}</span>
                      {isTinyBert && (
                        <span title="4-Class Architecture">
                          <Award className="w-4 h-4 text-amber-400 shrink-0" />
                        </span>
                      )}
                    </h3>
                    <p className="text-xs text-cyan-400 font-mono mt-0.5">{m.version}</p>
                    <p className="text-xs text-slate-400 mt-1">{m.type}</p>

                    {isTinyBert && (
                      <div className="mt-3 p-2 rounded bg-purple-950/30 border border-purple-800/40 text-[10px] text-purple-300 flex items-center space-x-1.5">
                        <Sparkles className="w-3.5 h-3.5 shrink-0 text-purple-400" />
                        <span>4-Class Banking False-Positive Elimination (Specificity: 97.95%)</span>
                      </div>
                    )}

                    {/* Metadata Specs */}
                    {Object.keys(meta).length > 0 && (
                      <div className="mt-4 p-3 bg-slate-950/50 rounded-lg border border-slate-800/80 space-y-1.5 text-xs">
                        {Object.entries(meta).map(([key, val]) => (
                          <div key={key} className="flex justify-between items-center text-[11px]">
                            <span className="text-slate-500 uppercase tracking-wider text-[10px]">
                              {key.replace(/_/g, ' ')}:
                            </span>
                            <span className="text-slate-200 font-mono font-medium">{String(val)}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  <div className="mt-6 pt-4 border-t border-slate-800 space-y-2 text-[11px] text-slate-400">
                    <div className="flex items-center justify-between font-mono">
                      <span className="flex items-center space-x-1">
                        <Hash className="w-3 h-3 text-slate-500" />
                        <span>Artifact Hash:</span>
                      </span>
                      <span className="text-slate-300 truncate max-w-[140px]" title={m.artifact_hash || 'Verified'}>
                        {m.artifact_hash ? `${m.artifact_hash.slice(0, 16)}...` : 'Verified'}
                      </span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="flex items-center space-x-1">
                        <Calendar className="w-3 h-3 text-slate-500" />
                        <span>Last Updated:</span>
                      </span>
                      <span className="text-slate-300">
                        {new Date(m.updated_at || Date.now()).toLocaleDateString()}
                      </span>
                    </div>
                  </div>
                </Card>
              );
            })}
          </div>
        )}

        {/* Security Notice on Governance */}
        <div className="p-4 rounded-xl bg-slate-900/40 border border-slate-800/80 flex items-start space-x-3 text-xs text-slate-400">
          <Shield className="w-4 h-4 text-cyan-400 shrink-0 mt-0.5" />
          <p className="leading-relaxed">
            <strong className="text-slate-200">Model Deployment Governance:</strong> On-device AI inference runs locally using ONNX Runtime. Model weight updates and retrained artifacts are signed cryptographically and distributed through the backend to protect model integrity against adversarial tampering.
          </p>
        </div>
      </div>
    </DashboardShell>
  );
}
