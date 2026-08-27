import React, { useState } from 'react';
import Papa from 'papaparse';
import { OrchestratorResponse, Transaction, GroundTruthItem } from '../types/sentinel';
import { SentinelAPI, RunHistoryStore } from '../services/api';
import { Upload, FileText, CheckCircle2, AlertCircle, Play, Loader2 } from 'lucide-react';

interface DatasetLabProps {
  onAnalysisUpdate: (res: OrchestratorResponse) => void;
}

export const DatasetLab: React.FC<DatasetLabProps> = ({ onAnalysisUpdate }) => {
  const [file, setFile] = useState<File | null>(null);
  const [parsedRows, setParsedRows] = useState<any[]>([]);
  const [columnMap, setColumnMap] = useState<Record<string, string>>({});
  const [validationError, setValidationError] = useState<string | null>(null);
  const [useGroundTruth, setUseGroundTruth] = useState(true);
  const [isAnalyzing, setIsAnalyzing] = useState(false);

  const stdColumns = [
    { key: 'account_id', label: 'Account ID', required: true, aliases: ['account_id', 'accountid', 'acc_id', 'user_id', 'account'] },
    { key: 'amount', label: 'Amount', required: true, aliases: ['amount', 'txn_amount', 'transaction_amount', 'value', 'amt'] },
    { key: 'device_id', label: 'Device ID', required: false, aliases: ['device_id', 'deviceid', 'device_fingerprint', 'device'] },
    { key: 'ip', label: 'IP Address', required: false, aliases: ['ip', 'ip_address', 'ipaddress', 'client_ip'] },
    { key: 'bank_ref', label: 'Bank Account Ref', required: false, aliases: ['bank_ref', 'bank_reference', 'bin', 'card_bin', 'bank_account'] },
    { key: 'timestamp', label: 'Timestamp', required: false, aliases: ['timestamp', 'time', 'date', 'created_at'] },
    { key: 'is_fraud', label: 'Is Fraud (Ground Truth)', required: false, aliases: ['is_fraud', 'fraud', 'ground_truth', 'label', 'isfraud'] },
  ];

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const uploadedFile = e.target.files?.[0];
    if (!uploadedFile) return;

    setFile(uploadedFile);
    setValidationError(null);

    Papa.parse(uploadedFile, {
      header: true,
      skipEmptyLines: true,
      complete: (results) => {
        const rows = results.data as any[];
        setParsedRows(rows);

        if (rows.length > 0) {
          const sampleRow = rows[0];
          const detectedMap: Record<string, string> = {};

          stdColumns.forEach((std) => {
            const match = Object.keys(sampleRow).find((col) =>
              std.aliases.includes(col.toLowerCase().trim())
            );
            if (match) {
              detectedMap[std.key] = match;
            }
          });

          setColumnMap(detectedMap);

          if (!detectedMap.account_id || !detectedMap.amount) {
            setValidationError('Missing required columns: account_id or amount.');
          }
        }
      },
      error: (err) => {
        setValidationError(`Failed to parse CSV file: ${err.message}`);
      },
    });
  };

  const handleRunAnalysis = async () => {
    if (!parsedRows || parsedRows.length === 0) return;
    setIsAnalyzing(true);
    setValidationError(null);

    const transactions: Transaction[] = [];
    const groundTruth: GroundTruthItem[] = [];

    parsedRows.forEach((row, idx) => {
      const accId = String(row[columnMap.account_id] || '').trim();
      const amountVal = parseFloat(row[columnMap.amount]);

      if (accId && !isNaN(amountVal)) {
        const devId = columnMap.device_id && row[columnMap.device_id] ? String(row[columnMap.device_id]).trim() : null;
        const ipVal = columnMap.ip && row[columnMap.ip] ? String(row[columnMap.ip]).trim() : null;
        const bankVal = columnMap.bank_ref && row[columnMap.bank_ref] ? String(row[columnMap.bank_ref]).trim() : null;
        const tsVal = columnMap.timestamp && row[columnMap.timestamp] ? String(row[columnMap.timestamp]).trim() : null;

        // Blank to null normalization
        const txn: Transaction = {
          id: `txn_${idx + 1}_${Math.random().toString(36).substring(2, 6)}`,
          account_id: accId,
          amount: amountVal,
          device_id: devId || null,
          ip: ipVal || null,
          bank_ref: bankVal || null,
          timestamp: tsVal || null,
        };

        transactions.push(txn);

        if (useGroundTruth && columnMap.is_fraud && row[columnMap.is_fraud] !== undefined) {
          const rawGt = String(row[columnMap.is_fraud]).toLowerCase();
          const isFraud = rawGt === 'true' || rawGt === '1' || rawGt === 'yes';
          groundTruth.push({ account_id: accId, is_fraud: isFraud });
        }
      }
    });

    try {
      const res = await SentinelAPI.analyze(transactions, groundTruth.length > 0 ? groundTruth : null);
      res.total_transactions = transactions.length;
      res.dataset_name = file?.name || 'Uploaded Dataset';
      
      onAnalysisUpdate(res);

      RunHistoryStore.saveRun({
        run_id: `run_${Date.now().toString(36)}`,
        dataset_name: file?.name || 'Uploaded Dataset',
        created_at: new Date().toISOString(),
        accounts_count: res.verdict?.length || 0,
        flagged_count: res.verdict?.filter((v) => v.flagged).length || 0,
        rings_count: new Set(res.verdict?.map((v) => v.ring_id).filter(Boolean)).size,
        metrics: res.metrics,
        results: res,
      });

      setIsAnalyzing(false);
    } catch (err: any) {
      setIsAnalyzing(false);
      setValidationError(err.message || 'Pipeline analysis failed.');
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-outline-variant pb-4">
        <h2 className="text-2xl font-bold text-on-surface">Dataset Lab</h2>
        <p className="text-sm text-secondary">
          Upload custom transaction datasets (CSV) to detect payment abuse rings.
        </p>
      </div>

      {/* File Upload Box */}
      <div className="bg-surface border border-outline-variant rounded-2xl p-6 shadow-stitch">
        <div className="text-sm font-bold text-on-surface mb-2 flex items-center gap-2">
          <Upload className="w-4 h-4 text-primary" />
          Upload Dataset File (.csv)
        </div>
        <p className="text-xs text-secondary mb-4">
          Supports standard payment attributes: account_id, amount, device_id, ip, bank_ref, timestamp, is_fraud.
        </p>

        <label className="border-2 border-dashed border-outline-variant hover:border-primary rounded-xl p-8 flex flex-col items-center justify-center cursor-pointer bg-surface-container-low transition-colors">
          <FileText className="w-8 h-8 text-primary mb-2" />
          <span className="text-sm font-medium text-on-surface">
            {file ? file.name : 'Click or drop CSV file here'}
          </span>
          <span className="text-xs text-secondary mt-1">Maximum size 50MB</span>
          <input
            type="file"
            accept=".csv"
            onChange={handleFileUpload}
            className="hidden"
          />
        </label>
      </div>

      {validationError && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl text-xs font-semibold flex items-center gap-2">
          <AlertCircle className="w-4 h-4" />
          {validationError}
        </div>
      )}

      {parsedRows.length > 0 && (
        <div className="bg-surface border border-outline-variant rounded-2xl p-6 shadow-stitch space-y-6">
          <div className="flex items-center justify-between border-b border-outline-variant pb-3">
            <span className="text-sm font-bold text-on-surface flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600" />
              Column Alignment & Quality Audit
            </span>
            <span className="text-xs font-mono font-semibold text-primary">
              {parsedRows.length.toLocaleString()} Records Found
            </span>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            {stdColumns.map((col) => {
              const mapped = columnMap[col.key];
              return (
                <div key={col.key} className="bg-surface-container-low p-3 rounded-xl border border-outline-variant/60 text-xs">
                  <div className="text-secondary font-medium mb-1">{col.label}</div>
                  <div className={`font-mono font-bold ${mapped ? 'text-emerald-700' : col.required ? 'text-red-600' : 'text-outline'}`}>
                    {mapped ? `✓ ${mapped}` : col.required ? '✗ Missing' : '— Null'}
                  </div>
                </div>
              );
            })}
          </div>

          {columnMap.is_fraud && (
            <div className="flex items-center gap-2 text-xs text-on-surface">
              <input
                type="checkbox"
                id="gt_check"
                checked={useGroundTruth}
                onChange={(e) => setUseGroundTruth(e.target.checked)}
                className="rounded text-primary focus:ring-primary"
              />
              <label htmlFor="gt_check" className="font-medium cursor-pointer">
                Pass ground-truth labels (<code>is_fraud</code>) to Python Evaluation Agent?
              </label>
            </div>
          )}

          <div className="pt-2">
            <button
              onClick={handleRunAnalysis}
              disabled={isAnalyzing || !columnMap.account_id || !columnMap.amount}
              className="w-full flex items-center justify-center gap-2 bg-primary text-on-primary font-bold text-sm py-3 rounded-xl hover:bg-primary-container disabled:opacity-50 transition-all shadow-md"
            >
              {isAnalyzing ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Spring Boot Analyzing Graph & Scores...
                </>
              ) : (
                <>
                  <Play className="w-4 h-4" />
                  Execute Full Pipeline Analysis
                </>
              )}
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
