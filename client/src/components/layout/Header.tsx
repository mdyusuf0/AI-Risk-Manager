import React from 'react';
import { Shield, Activity, FileText } from 'lucide-react';

interface HeaderProps {
  healthStatus: 'ok' | 'error' | 'checking';
  onExportReport?: () => void;
}

export const Header: React.FC<HeaderProps> = ({ healthStatus, onExportReport }) => {
  const isOk = healthStatus === 'ok';

  return (
    <header className="flex flex-col md:flex-row justify-between items-start md:items-center bg-surface border border-outline-variant rounded-2xl px-6 py-3.5 mb-6 shadow-stitch">
      <div className="flex items-center gap-3.5 mb-3 md:mb-0">
        <div className="bg-primary-container text-on-primary w-9 h-9 rounded-lg font-bold text-sm flex items-center justify-center shadow-sm">
          SR
        </div>
        <div>
          <div className="font-bold text-lg text-on-surface leading-tight flex items-center gap-2">
            Sentinel Ring
            <span className="text-xs font-semibold text-primary bg-primary-fixed px-2 py-0.5 rounded-full">
              v1.0.0
            </span>
          </div>
          <div className="text-xs text-on-surface-variant">
            Razorpay AI Buildathon · Track 2: AI Risk Manager
          </div>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-4">
        <div
          className={`flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold ${
            isOk
              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
              : 'bg-red-50 text-red-700 border border-red-200'
          }`}
        >
          <span
            className={`w-2 h-2 rounded-full ${
              isOk ? 'bg-emerald-500 animate-pulse' : 'bg-red-500'
            }`}
          />
          {isOk ? 'SPRING BOOT ACTIVE (:8080)' : 'ORCHESTRATOR OFFLINE'}
        </div>

        <div className="text-xs text-secondary border-r border-outline-variant pr-4 hidden sm:block">
          Run ID: <span className="font-mono text-primary font-semibold">SR-2026-ALPHA</span>
        </div>

        {onExportReport && (
          <button
            onClick={onExportReport}
            className="flex items-center gap-1.5 bg-surface text-on-surface border border-outline-variant hover:bg-surface-container-low text-xs font-medium px-3.5 py-1.5 rounded-lg transition-colors"
          >
            <FileText className="w-3.5 h-3.5 text-secondary" />
            Export Report
          </button>
        )}

        <div className="flex items-center gap-1.5 text-xs font-semibold text-primary bg-primary-fixed px-3 py-1 rounded-lg border border-primary/20">
          <Shield className="w-3.5 h-3.5" />
          Defense-Only Protocol
        </div>
      </div>
    </header>
  );
};
