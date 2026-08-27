import React, { useState } from 'react';
import { OrchestratorResponse, VerdictItem } from '../types/sentinel';
import { CytoscapeRingGraph } from '../components/graph/CytoscapeRingGraph';
import { Network, Info, ShieldAlert, CheckCircle2 } from 'lucide-react';

interface RingExplorerProps {
  analysis: OrchestratorResponse | null;
}

export const RingExplorer: React.FC<RingExplorerProps> = ({ analysis }) => {
  const verdicts = analysis?.verdict || [];
  const rings = Array.from(new Set(verdicts.map((v) => v.ring_id).filter((r) => r && r !== '—'))) as string[];

  const [selectedRing, setSelectedRing] = useState<string>(rings[0] || '');

  const ringMembers = verdicts.filter((v) => v.ring_id === selectedRing);

  return (
    <div className="space-y-6">
      <div className="border-b border-outline-variant pb-4">
        <h2 className="text-2xl font-bold text-on-surface">Ring Explorer</h2>
        <p className="text-sm text-secondary">
          Interactive network graph visualization of shared device & attribute clusters.
        </p>
      </div>

      {verdicts.length === 0 ? (
        <div className="bg-surface border border-outline-variant rounded-2xl p-12 text-center shadow-stitch">
          <Network className="w-12 h-12 text-outline mx-auto mb-3" />
          <h3 className="text-base font-bold text-on-surface mb-1">No Graph Data in Session</h3>
          <p className="text-xs text-secondary mb-4">
            Run an analysis in Dataset Lab or Payment Simulator to inspect connected accounts.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Cytoscape Graph on Left (2 Cols) */}
          <div className="bg-surface border border-outline-variant rounded-2xl p-5 shadow-stitch lg:col-span-2 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-sm font-bold text-on-surface flex items-center gap-2">
                <Network className="w-4 h-4 text-primary" />
                Account Linkage Graph (Cytoscape.js)
              </span>
              <span className="text-xs text-secondary">
                🔴 Red = Flagged · 🟢 Green = Safe · Size = Risk
              </span>
            </div>

            <CytoscapeRingGraph verdicts={verdicts} />
          </div>

          {/* Cluster Inspector on Right (1 Col) */}
          <div className="bg-surface border border-outline-variant rounded-2xl p-5 shadow-stitch space-y-4">
            <div className="text-sm font-bold text-on-surface flex items-center gap-2 border-b border-outline-variant pb-3">
              <Info className="w-4 h-4 text-primary" />
              Cluster Inspector
            </div>

            {rings.length > 0 ? (
              <>
                <div>
                  <label className="text-xs text-secondary font-medium block mb-1">Select Detected Ring</label>
                  <select
                    value={selectedRing}
                    onChange={(e) => setSelectedRing(e.target.value)}
                    className="w-full bg-surface-container-low border border-outline-variant rounded-xl p-2.5 text-xs font-semibold text-on-surface"
                  >
                    {rings.map((r) => (
                      <option key={r} value={r}>
                        {r} ({verdicts.filter((v) => v.ring_id === r).length} accounts)
                      </option>
                    ))}
                  </select>
                </div>

                <div className="bg-primary-fixed/40 border border-primary/30 p-4 rounded-xl space-y-2">
                  <div className="text-[11px] font-bold uppercase tracking-wider text-primary">
                    Cluster Details
                  </div>
                  <div className="text-base font-extrabold text-on-surface">{selectedRing}</div>
                  <div className="text-xs text-on-surface-variant space-y-1">
                    <div>Members: <span className="font-bold text-on-surface">{ringMembers.length}</span></div>
                    <div>Avg Risk Score: <span className="font-mono font-bold text-primary">{(ringMembers.reduce((acc, m) => acc + m.risk_score, 0) / (ringMembers.length || 1)).toFixed(4)}</span></div>
                    <div>Flagged Accounts: <span className="font-bold text-error">{ringMembers.filter(m => m.flagged).length} / {ringMembers.length}</span></div>
                  </div>
                </div>

                <div>
                  <div className="text-xs font-bold text-on-surface mb-1.5">AI Evidence Explanation</div>
                  <div className="p-3 bg-surface-container-low border border-outline-variant rounded-xl text-xs text-on-surface-variant leading-relaxed">
                    {ringMembers[0]?.explanation || 'No explanation provided.'}
                  </div>
                </div>

                <div>
                  <div className="text-xs font-bold text-on-surface mb-2">Member Accounts</div>
                  <div className="space-y-1.5 max-h-40 overflow-y-auto pr-1">
                    {ringMembers.map((m) => (
                      <div
                        key={m.account_id}
                        className="flex items-center justify-between p-2 bg-surface-container-low rounded-lg border border-outline-variant/60 text-xs"
                      >
                        <span className="font-mono font-medium">{m.account_id}</span>
                        {m.flagged ? (
                          <span className="text-[10px] font-bold text-error flex items-center gap-1">
                            <ShieldAlert className="w-3 h-3" /> Flagged
                          </span>
                        ) : (
                          <span className="text-[10px] font-bold text-emerald-700 flex items-center gap-1">
                            <CheckCircle2 className="w-3 h-3" /> Safe
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </>
            ) : (
              <div className="text-xs text-secondary text-center py-6">
                No multi-account rings detected in the current dataset.
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
