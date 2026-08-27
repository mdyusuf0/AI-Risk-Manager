import React, { useState, useEffect } from 'react';
import { AnalysisRun, OrchestratorResponse } from '../types/sentinel';
import { RunHistoryStore } from '../services/api';
import { ExportService } from '../services/export';
import { History, Search, Download, Trash2, ArrowUpRight } from 'lucide-react';

interface RunHistoryProps {
  onLoadRun: (res: OrchestratorResponse) => void;
}

export const RunHistory: React.FC<RunHistoryProps> = ({ onLoadRun }) => {
  const [runs, setRuns] = useState<AnalysisRun[]>([]);
  const [searchTerm, setSearchTerm] = useState<string>('');

  useEffect(() => {
    setRuns(RunHistoryStore.getRuns());
  }, []);

  const handleDelete = (runId: string) => {
    RunHistoryStore.deleteRun(runId);
    setRuns(RunHistoryStore.getRuns());
  };

  const handleDownload = (run: AnalysisRun, format: 'json' | 'csv' | 'md') => {
    let content = '';
    let mime = 'text/plain';
    let filename = `sentinel_${run.run_id}.${format}`;

    if (format === 'json') {
      content = ExportService.toJSON(run.results);
      mime = 'application/json';
    } else if (format === 'csv') {
      content = ExportService.toCSV(run.results.verdict || []);
      mime = 'text/csv';
    } else {
      content = ExportService.toMarkdown(run.results);
      mime = 'text/markdown';
    }

    const blob = new Blob([content], { type: mime });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  };

  const filteredRuns = runs.filter((r) =>
    (r.dataset_name || '').toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div className="border-b border-outline-variant pb-4">
        <h2 className="text-2xl font-bold text-on-surface">Analysis Run History</h2>
        <p className="text-sm text-secondary">
          Audit trail of past fraud detection runs with export options.
        </p>
      </div>

      <div className="bg-surface border border-outline-variant rounded-2xl p-5 shadow-stitch space-y-4">
        <div className="relative">
          <Search className="w-4 h-4 text-outline absolute left-3 top-3" />
          <input
            type="text"
            placeholder="Search runs by dataset name..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-4 py-2 bg-surface-container-low border border-outline-variant rounded-xl text-xs text-on-surface focus:outline-none focus:border-primary"
          />
        </div>

        {filteredRuns.length === 0 ? (
          <div className="text-center py-10 text-secondary text-xs">
            <History className="w-8 h-8 text-outline mx-auto mb-2" />
            No analysis runs found. Run a simulation or upload a dataset.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left text-on-surface">
              <thead className="bg-surface-container-low text-secondary font-semibold uppercase tracking-wider text-[11px]">
                <tr>
                  <th className="p-3">Run ID</th>
                  <th className="p-3">Dataset Name</th>
                  <th className="p-3">Date</th>
                  <th className="p-3">Accounts</th>
                  <th className="p-3">Flagged</th>
                  <th className="p-3">Rings</th>
                  <th className="p-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant">
                {filteredRuns.map((r) => (
                  <tr key={r.run_id} className="hover:bg-surface-container-low/50">
                    <td className="p-3 font-mono font-medium text-primary">{r.run_id.substring(0, 10)}</td>
                    <td className="p-3 font-medium">{r.dataset_name}</td>
                    <td className="p-3 text-secondary">{new Date(r.created_at).toLocaleString()}</td>
                    <td className="p-3">{r.accounts_count}</td>
                    <td className="p-3 font-semibold text-error">{r.flagged_count}</td>
                    <td className="p-3">{r.rings_count}</td>
                    <td className="p-3 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => onLoadRun(r.results)}
                          className="flex items-center gap-1 bg-primary/10 text-primary hover:bg-primary/20 px-2.5 py-1 rounded-md text-[11px] font-semibold transition-colors"
                        >
                          <ArrowUpRight className="w-3 h-3" /> Load
                        </button>

                        <button
                          onClick={() => handleDownload(r, 'json')}
                          className="flex items-center gap-1 bg-surface-container-low text-on-surface border border-outline-variant hover:bg-surface-container-high px-2 py-1 rounded-md text-[11px] font-medium transition-colors"
                        >
                          <Download className="w-3 h-3 text-secondary" /> JSON
                        </button>

                        <button
                          onClick={() => handleDownload(r, 'csv')}
                          className="flex items-center gap-1 bg-surface-container-low text-on-surface border border-outline-variant hover:bg-surface-container-high px-2 py-1 rounded-md text-[11px] font-medium transition-colors"
                        >
                          <Download className="w-3 h-3 text-secondary" /> CSV
                        </button>

                        <button
                          onClick={() => handleDelete(r.run_id)}
                          className="p-1 text-secondary hover:text-error transition-colors"
                          title="Delete Run"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
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
