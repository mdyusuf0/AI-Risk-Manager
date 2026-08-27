import React, { useState } from 'react';
import { OrchestratorResponse } from '../types/sentinel';
import { SimulationService } from '../services/simulation';
import { SentinelAPI, RunHistoryStore } from '../services/api';
import { CreditCard, Zap, Play, Loader2, CheckCircle2, AlertTriangle } from 'lucide-react';

interface PaymentSimulatorProps {
  onAnalysisUpdate: (res: OrchestratorResponse) => void;
}

export const PaymentSimulator: React.FC<PaymentSimulatorProps> = ({ onAnalysisUpdate }) => {
  const [selectedPreset, setSelectedPreset] = useState<string>('Shared Device Ring');
  const [numAccounts, setNumAccounts] = useState<number>(10);
  const [sharedDevices, setSharedDevices] = useState<number>(2);
  const [sharedIps, setSharedIps] = useState<number>(2);
  const [minAmount, setMinAmount] = useState<number>(10);
  const [maxAmount, setMaxAmount] = useState<number>(1000);
  const [includeGt, setIncludeGt] = useState<boolean>(true);

  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const presets = [
    { name: 'Clean Traffic', desc: 'Unique devices, IPs, and legitimate transaction amounts.' },
    { name: 'Shared Device Ring', desc: '5 accounts sharing 1 device fingerprint over a short time window.' },
    { name: 'Multi-Signal Ring', desc: '4 accounts linked by device fingerprint AND hashed bank account.' },
    { name: 'IP-Only Cluster', desc: '6 accounts sharing public coffee shop IP (Safeguard Test: capped < 0.60).' },
    { name: 'Mixed Scenario', desc: 'Complex mix of clean traffic, shared device rings, and public IP clusters.' },
    { name: 'Custom Sandbox', desc: 'User-defined account count, device sharing, and amount ranges.' },
  ];

  const handleExecuteSimulation = async () => {
    setIsLoading(true);
    setErrorMsg(null);

    const { transactions, groundTruth } = SimulationService.generateScenario(selectedPreset, {
      numAccounts,
      numSharedDevices: sharedDevices,
      numSharedIps: sharedIps,
      amountRange: [minAmount, maxAmount],
      includeGroundTruth: includeGt,
    });

    try {
      const res = await SentinelAPI.analyze(transactions, groundTruth.length > 0 ? groundTruth : null);
      res.total_transactions = transactions.length;
      res.is_synthetic = true;
      res.dataset_name = `Synthetic (${selectedPreset})`;
      
      onAnalysisUpdate(res);

      RunHistoryStore.saveRun({
        run_id: `sim_${Date.now().toString(36)}`,
        dataset_name: `Synthetic (${selectedPreset})`,
        created_at: new Date().toISOString(),
        accounts_count: res.verdict?.length || 0,
        flagged_count: res.verdict?.filter((v) => v.flagged).length || 0,
        rings_count: new Set(res.verdict?.map((v) => v.ring_id).filter(Boolean)).size,
        metrics: res.metrics,
        results: res,
      });

      setIsLoading(false);
    } catch (err: any) {
      setIsLoading(false);
      setErrorMsg(err.message || 'Simulation execution failed.');
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-outline-variant pb-4">
        <h2 className="text-2xl font-bold text-on-surface">Payment Simulator</h2>
        <p className="text-sm text-secondary">
          Synthetic attack sandbox for real-time multi-account abuse testing.
        </p>
      </div>

      {/* Scenario Selector Card */}
      <div className="bg-surface border border-outline-variant rounded-2xl p-6 shadow-stitch">
        <div className="text-sm font-bold text-on-surface mb-3 flex items-center justify-between">
          <span className="flex items-center gap-2">
            <Zap className="w-4 h-4 text-primary" />
            Select Fraud Scenario Preset
          </span>
          <span className="bg-emerald-50 text-emerald-700 text-[10px] font-bold px-2.5 py-0.5 rounded-full border border-emerald-200">
            SYNTHETIC DATA BADGE
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-6">
          {presets.map((p) => {
            const isSelected = selectedPreset === p.name;
            return (
              <div
                key={p.name}
                onClick={() => setSelectedPreset(p.name)}
                className={`p-4 rounded-xl border cursor-pointer transition-all ${
                  isSelected
                    ? 'bg-primary-fixed/40 border-primary shadow-sm'
                    : 'bg-surface hover:bg-surface-container-low border-outline-variant'
                }`}
              >
                <div className="flex items-center justify-between mb-1">
                  <span className={`text-sm font-bold ${isSelected ? 'text-primary' : 'text-on-surface'}`}>
                    {p.name}
                  </span>
                  {isSelected && <CheckCircle2 className="w-4 h-4 text-primary" />}
                </div>
                <div className="text-xs text-secondary leading-snug">{p.desc}</div>
              </div>
            );
          })}
        </div>

        {selectedPreset === 'Custom Sandbox' && (
          <div className="bg-surface-container-low p-4 rounded-xl border border-outline-variant mb-6 space-y-4">
            <div className="text-xs font-bold text-on-surface">Custom Sandbox Controls</div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div>
                <label className="text-xs text-secondary font-medium block mb-1">Total Accounts ({numAccounts})</label>
                <input
                  type="range"
                  min={3}
                  max={25}
                  value={numAccounts}
                  onChange={(e) => setNumAccounts(Number(e.target.value))}
                  className="w-full accent-primary"
                />
              </div>
              <div>
                <label className="text-xs text-secondary font-medium block mb-1">Shared Devices ({sharedDevices})</label>
                <input
                  type="range"
                  min={0}
                  max={5}
                  value={sharedDevices}
                  onChange={(e) => setSharedDevices(Number(e.target.value))}
                  className="w-full accent-primary"
                />
              </div>
              <div>
                <label className="text-xs text-secondary font-medium block mb-1">Shared IPs ({sharedIps})</label>
                <input
                  type="range"
                  min={0}
                  max={5}
                  value={sharedIps}
                  onChange={(e) => setSharedIps(Number(e.target.value))}
                  className="w-full accent-primary"
                />
              </div>
            </div>
          </div>
        )}

        {errorMsg && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl text-xs font-semibold flex items-center gap-2 mb-4">
            <AlertTriangle className="w-4 h-4" />
            {errorMsg}
          </div>
        )}

        <button
          onClick={handleExecuteSimulation}
          disabled={isLoading}
          className="w-full flex items-center justify-center gap-2 bg-primary text-on-primary font-bold text-sm py-3 rounded-xl hover:bg-primary-container disabled:opacity-50 transition-all shadow-md"
        >
          {isLoading ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              Calling Spring Boot Orchestrator...
            </>
          ) : (
            <>
              <Play className="w-4 h-4" />
              Generate Synthetic Transactions & Execute Analysis
            </>
          )}
        </button>
      </div>
    </div>
  );
};
