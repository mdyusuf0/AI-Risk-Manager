import React, { useState, useEffect } from 'react';
import { PageId, OrchestratorResponse } from './types/sentinel';
import { SentinelAPI } from './services/api';
import { ExportService } from './services/export';
import { Header } from './components/layout/Header';
import { Sidebar } from './components/layout/Sidebar';
import { Overview } from './pages/Overview';
import { DatasetLab } from './pages/DatasetLab';
import { PaymentSimulator } from './pages/PaymentSimulator';
import { RingExplorer } from './pages/RingExplorer';
import { Evaluation } from './pages/Evaluation';
import { RunHistory } from './pages/RunHistory';
import { SettingsPage } from './pages/Settings';

export const App: React.FC = () => {
  const [currentPage, setCurrentPage] = useState<PageId>('overview');
  const [healthStatus, setHealthStatus] = useState<'ok' | 'error' | 'checking'>('checking');
  const [analysis, setAnalysis] = useState<OrchestratorResponse | null>(null);

  useEffect(() => {
    SentinelAPI.checkHealth().then((res) => {
      setHealthStatus(res.status === 'ok' ? 'ok' : 'error');
    });
  }, []);

  const handleExportReport = () => {
    if (!analysis) {
      alert('No active analysis results to export.');
      return;
    }

    const markdownContent = ExportService.toMarkdown(analysis);
    const blob = new Blob([markdownContent], { type: 'text/markdown' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `sentinel_report_${Date.now()}.md`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="min-h-screen bg-background flex">
      {/* Sidebar */}
      <Sidebar currentPage={currentPage} onSelectPage={setCurrentPage} />

      {/* Main Content Area */}
      <div className="flex-1 ml-64 p-8 max-w-[1440px]">
        <Header healthStatus={healthStatus} onExportReport={handleExportReport} />

        <main>
          {currentPage === 'overview' && (
            <Overview analysis={analysis} onAnalysisUpdate={setAnalysis} />
          )}
          {currentPage === 'dataset-lab' && (
            <DatasetLab onAnalysisUpdate={setAnalysis} />
          )}
          {currentPage === 'payment-simulator' && (
            <PaymentSimulator onAnalysisUpdate={setAnalysis} />
          )}
          {currentPage === 'ring-explorer' && (
            <RingExplorer analysis={analysis} />
          )}
          {currentPage === 'evaluation' && (
            <Evaluation analysis={analysis} />
          )}
          {currentPage === 'run-history' && (
            <RunHistory
              onLoadRun={(res) => {
                setAnalysis(res);
                setCurrentPage('overview');
              }}
            />
          )}
          {currentPage === 'settings' && <SettingsPage />}
        </main>
      </div>
    </div>
  );
};

export default App;
