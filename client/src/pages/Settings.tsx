import React, { useState, useEffect } from 'react';
import { SentinelAPI, RunHistoryStore } from '../services/api';
import { Settings as SettingsIcon, Shield, Server, Trash2, CheckCircle2, XCircle } from 'lucide-react';

export const SettingsPage: React.FC = () => {
  const [riskThreshold, setRiskThreshold] = useState<number>(0.70);
  const [ringThreshold, setRingThreshold] = useState<number>(0.60);
  const [healthStatus, setHealthStatus] = useState<'ok' | 'error' | 'checking'>('checking');

  useEffect(() => {
    SentinelAPI.checkHealth().then((res) => {
      setHealthStatus(res.status === 'ok' ? 'ok' : 'error');
    });
  }, []);

  const handleClearHistory = () => {
    if (confirm('Are you sure you want to delete all stored analysis run history?')) {
      RunHistoryStore.clearAll();
      alert('Analysis history cleared.');
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-outline-variant pb-4">
        <h2 className="text-2xl font-bold text-on-surface">System Settings</h2>
        <p className="text-sm text-secondary">
          Configure detection thresholds, inspect backend endpoints, and review governance policies.
        </p>
      </div>

      {/* Detection Thresholds */}
      <div className="bg-surface border border-outline-variant rounded-2xl p-6 shadow-stitch space-y-4">
        <div className="text-sm font-bold text-on-surface flex items-center gap-2">
          <SettingsIcon className="w-4 h-4 text-primary" />
          Detection Threshold Parameters
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
          <div>
            <div className="flex justify-between text-xs font-semibold mb-1">
              <span className="text-on-surface">Account Risk Threshold</span>
              <span className="text-primary font-mono">{riskThreshold.toFixed(2)}</span>
            </div>
            <input
              type="range"
              min={0.1}
              max={1.0}
              step={0.05}
              value={riskThreshold}
              onChange={(e) => setRiskThreshold(Number(e.target.value))}
              className="w-full accent-primary"
            />
            <span className="text-[11px] text-secondary">Accounts with risk_score &gt;= threshold will be flagged.</span>
          </div>

          <div>
            <div className="flex justify-between text-xs font-semibold mb-1">
              <span className="text-on-surface">Ring Community Score Threshold</span>
              <span className="text-primary font-mono">{ringThreshold.toFixed(2)}</span>
            </div>
            <input
              type="range"
              min={0.1}
              max={1.0}
              step={0.05}
              value={ringThreshold}
              onChange={(e) => setRingThreshold(Number(e.target.value))}
              className="w-full accent-primary"
            />
            <span className="text-[11px] text-secondary">Louvain ring clusters &gt;= threshold will be flagged.</span>
          </div>
        </div>
      </div>

      {/* Backend Connections Status */}
      <div className="bg-surface border border-outline-variant rounded-2xl p-6 shadow-stitch space-y-3">
        <div className="text-sm font-bold text-on-surface flex items-center gap-2">
          <Server className="w-4 h-4 text-primary" />
          Backend Connection Telemetry
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
          <div className="p-3 bg-surface-container-low rounded-xl border border-outline-variant flex items-center justify-between">
            <div>
              <div className="font-bold text-on-surface">Spring Boot Orchestrator</div>
              <div className="text-secondary font-mono text-[11px]">http://localhost:8080/api</div>
            </div>
            {healthStatus === 'ok' ? (
              <span className="text-emerald-700 font-bold flex items-center gap-1">
                <CheckCircle2 className="w-4 h-4" /> Active
              </span>
            ) : (
              <span className="text-red-600 font-bold flex items-center gap-1">
                <XCircle className="w-4 h-4" /> Offline
              </span>
            )}
          </div>

          <div className="p-3 bg-surface-container-low rounded-xl border border-outline-variant flex items-center justify-between">
            <div>
              <div className="font-bold text-on-surface">Python Micro-Agents</div>
              <div className="text-secondary font-mono text-[11px]">Internal localhost:8000</div>
            </div>
            <span className="text-indigo-700 font-bold flex items-center gap-1">
              <Shield className="w-4 h-4" /> Managed by Orchestrator
            </span>
          </div>
        </div>
      </div>

      {/* Data Management */}
      <div className="bg-surface border border-outline-variant rounded-2xl p-6 shadow-stitch space-y-3">
        <div className="text-sm font-bold text-on-surface flex items-center gap-2 text-error">
          <Trash2 className="w-4 h-4" />
          Data Management
        </div>

        <p className="text-xs text-secondary">
          Flush all locally cached analysis runs from browser storage.
        </p>

        <button
          onClick={handleClearHistory}
          className="bg-red-50 text-red-700 border border-red-200 hover:bg-red-100 font-semibold text-xs px-4 py-2 rounded-lg transition-colors"
        >
          Clear Analysis Run History
        </button>
      </div>
    </div>
  );
};
