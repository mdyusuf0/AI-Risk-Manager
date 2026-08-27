export interface Transaction {
  id: string;
  account_id: string;
  amount: number;
  device_id?: string | null;
  ip?: string | null;
  bank_ref?: string | null;
  timestamp?: string | null;
  is_fraud?: boolean;
}

export interface GroundTruthItem {
  account_id: string;
  is_fraud: boolean;
}

export interface VerdictItem {
  account_id: string;
  risk_score: number;
  flagged: boolean;
  ring_id?: string | null;
  explanation?: string | null;
}

export interface MetricsResult {
  precision: number;
  recall: number;
  f1_score?: number;
  false_positive_cost_estimate: number;
  currency: string;
}

export interface OrchestratorResponse {
  verdict: VerdictItem[];
  metrics?: MetricsResult | null;
  total_transactions?: number;
  is_synthetic?: boolean;
  dataset_name?: string;
  created_at?: string;
}

export interface AnalysisRun {
  run_id: string;
  dataset_name: string;
  created_at: string;
  accounts_count: number;
  flagged_count: number;
  rings_count: number;
  metrics?: MetricsResult | null;
  results: OrchestratorResponse;
}

export type PageId = 
  | 'overview' 
  | 'dataset-lab' 
  | 'payment-simulator' 
  | 'ring-explorer' 
  | 'evaluation' 
  | 'run-history' 
  | 'settings';
