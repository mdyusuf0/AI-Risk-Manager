import React from 'react';
import { OrchestratorResponse, VerdictItem } from '../types/sentinel';
import { MetricCard } from '../components/ui/MetricCard';
import { SentinelAPI } from '../services/api';
import { SimulationService } from '../services/simulation';
import { CreditCard, Users, ShieldAlert, Network, Flame, Zap, Play } from 'lucide-react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from 'recharts';

interface OverviewProps {
  analysis: OrchestratorResponse | null;
  onAnalysisUpdate: (res: OrchestratorResponse) => void;
}

export const Overview: React.FC<OverviewProps> = ({ analysis, onAnalysisUpdate }) => {
  const verdicts = analysis?.verdict || [];
  const totalTxns = analysis?.total_transactions || verdicts.length;
  const totalAccounts = verdicts.length;
  const flaggedCount = verdicts.filter((v) => v.flagged).length;
  const ringsCount = new Set(verdicts.map((v) => v.ring_id).filter((r) => r && r !== '—')).size;
  const maxRiskScore = verdicts.length > 0 ? Math.max(...verdicts.map((v) => v.risk_score)) : 0;

  // Histogram data calculation
  const getHistogramData = () => {
    const bins = Array(5).fill(0);
    verdicts.forEach((v) => {
      const idx = Math.min(4, Math.floor(v.risk_score * 5));
      bins[idx]++;
    });
    return [
      { range: '0.0 - 0.2', count: bins[0] },
      { range: '0.2 - 0.4', count: bins[1] },
      { range: '0.4 - 0.6', count: bins[2] },
      { range: '0.6 - 0.8', count: bins[3] },
      { range: '0.8 - 1.0', count: bins[4] },
    ];
  };

  const pieData = [
    { name: 'Flagged Risk', value: flaggedCount, color: '#BA1A1A' },
    { name: 'Safe Accounts', value: Math.max(0, totalAccounts - flaggedCount), color: '#10B981' },
  ];

  const handleRunDemo = async (preset: string) => {
    const { transactions, groundTruth } = SimulationService.generateScenario(preset);
    try {
      const res = await SentinelAPI.analyze(transactions, groundTruth);
      res.total_transactions = transactions.length;
      res.is_synthetic = true;
      onAnalysisUpdate(res);
    } catch (err: any) {
      alert(`Simulation failed: ${err.message}`);
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-outline-variant pb-4">
        <div>
          <h2 className="text-2xl font-bold text-on-surface">System Overview</h2>
          <p className="text-sm text-secondary">
            Real-time risk telemetry and synthetic data metrics.
          </p>
        </div>
        {analysis?.is_synthetic && (
          <span className="bg-primary-fixed text-primary px-3.5 py-1 rounded-full text-xs font-bold border border-primary/20 self-start sm:self-auto">
            SYNTHETIC DATA ACTIVE
          </span>
        )}
      </div>

      {/* 1-Click Instant Demo Simulation Panel */}
      <div className="bg-surface border border-outline-variant rounded-2xl p-5 shadow-stitch">
        <div className="text-sm font-bold text-on-surface mb-1 flex items-center gap-2">
          <Zap className="w-4 h-4 text-primary" />
          1-Click Instant Demo Simulation
        </div>
        <p className="text-xs text-secondary mb-4">
          Test real-time execution across preset attack vectors:
        </p>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <button
            onClick={() => handleRunDemo('Shared Device Ring')}
            className="flex items-center justify-center gap-2 bg-primary text-on-primary font-semibold text-xs px-4 py-2.5 rounded-lg hover:bg-primary-container transition-all"
          >
            <Play className="w-3.5 h-3.5" />
            Shared Device Ring
          </button>

          <button
            onClick={() => handleRunDemo('Multi-Signal Ring')}
            className="flex items-center justify-center gap-2 bg-surface text-on-surface border border-outline-variant hover:bg-surface-container-low font-medium text-xs px-4 py-2.5 rounded-lg transition-all"
          >
            <Play className="w-3.5 h-3.5 text-secondary" />
            Multi-Signal Ring
          </button>

          <button
            onClick={() => handleRunDemo('IP-Only Cluster')}
            className="flex items-center justify-center gap-2 bg-surface text-on-surface border border-outline-variant hover:bg-surface-container-low font-medium text-xs px-4 py-2.5 rounded-lg transition-all"
          >
            <Play className="w-3.5 h-3.5 text-secondary" />
            Public IP Cluster (Safeguard Test)
          </button>
        </div>
      </div>

      {/* 5 KPI Metric Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        <MetricCard
          title="Transactions"
          value={totalTxns.toLocaleString()}
          icon={<CreditCard className="w-4 h-4 text-primary" />}
          subtitle="Ingested records"
          color="#3525CD"
        />
        <MetricCard
          title="Accounts"
          value={totalAccounts.toLocaleString()}
          icon={<Users className="w-4 h-4 text-indigo-500" />}
          subtitle="Unique account population"
          color="#4F46E5"
        />
        <MetricCard
          title="Flagged Accounts"
          value={flaggedCount}
          icon={<ShieldAlert className="w-4 h-4 text-error" />}
          subtitle="Score >= 0.70 or Ring >= 0.60"
          color="#BA1A1A"
        />
        <MetricCard
          title="Detected Rings"
          value={ringsCount}
          icon={<Network className="w-4 h-4 text-amber-500" />}
          subtitle="Louvain community clusters"
          color="#F59E0B"
        />
        <MetricCard
          title="Highest Risk Score"
          value={maxRiskScore.toFixed(4)}
          icon={<Flame className="w-4 h-4 text-orange-600" />}
          subtitle="Max account risk"
          color="#7E3000"
        />
      </div>

      {/* Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Risk Score Distribution Histogram */}
        <div className="bg-surface border border-outline-variant rounded-2xl p-5 shadow-stitch lg:col-span-2">
          <div className="text-sm font-bold text-on-surface mb-4">Risk Score Distribution</div>
          <div className="h-60 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={getHistogramData()}>
                <XAxis dataKey="range" stroke="#6B7280" fontSize={11} />
                <YAxis stroke="#6B7280" fontSize={11} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#FFFFFF', borderColor: '#E5E7EB', borderRadius: '8px' }}
                />
                <Bar dataKey="count" fill="#4F46E5" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Flagged Donut Chart */}
        <div className="bg-surface border border-outline-variant rounded-2xl p-5 shadow-stitch flex flex-col justify-between">
          <div className="text-sm font-bold text-on-surface mb-2">Flagged vs Safe Ratio</div>
          <div className="h-52 w-full flex items-center justify-center">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={pieData}
                  cx="50%"
                  cy="50%"
                  innerRadius={55}
                  outerRadius={75}
                  paddingAngle={4}
                  dataKey="value"
                >
                  {pieData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="flex justify-center gap-6 text-xs text-secondary mt-2">
            <div className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-error" /> Flagged ({flaggedCount})
            </div>
            <div className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" /> Safe ({Math.max(0, totalAccounts - flaggedCount)})
            </div>
          </div>
        </div>
      </div>

      {/* Verdict Table */}
      <div className="bg-surface border border-outline-variant rounded-2xl p-5 shadow-stitch">
        <div className="text-sm font-bold text-on-surface mb-4">Verdict Summary Telemetry</div>
        {verdicts.length === 0 ? (
          <div className="text-center py-8 text-secondary text-xs">
            No verdicts generated. Run a simulation above or upload data in Dataset Lab.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left text-on-surface">
              <thead className="bg-surface-container-low text-secondary font-semibold uppercase tracking-wider text-[11px]">
                <tr>
                  <th className="p-3">Account ID</th>
                  <th className="p-3">Risk Score</th>
                  <th className="p-3">Flag Status</th>
                  <th className="p-3">Ring Cluster ID</th>
                  <th className="p-3">AI Evidence Explanation</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant">
                {verdicts.map((v) => (
                  <tr key={v.account_id} className="hover:bg-surface-container-low/50">
                    <td className="p-3 font-mono font-medium">{v.account_id}</td>
                    <td className="p-3 font-semibold">{v.risk_score.toFixed(4)}</td>
                    <td className="p-3">
                      {v.flagged ? (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-red-100 text-red-800 border border-red-200">
                          🚨 FLAGGED
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200">
                          ✅ SAFE
                        </span>
                      )}
                    </td>
                    <td className="p-3 font-mono text-secondary">{v.ring_id || '—'}</td>
                    <td className="p-3 text-on-surface-variant max-w-xs truncate" title={v.explanation || ''}>
                      {v.explanation || 'Normal transaction behavior.'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
