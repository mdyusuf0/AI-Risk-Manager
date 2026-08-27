import { Transaction, GroundTruthItem, OrchestratorResponse, AnalysisRun } from '../types/sentinel';

const BASE_URL = (import.meta as any).env?.VITE_API_BASE_URL || 'http://localhost:8080';
const TIMEOUT_MS = Number((import.meta as any).env?.VITE_REQUEST_TIMEOUT_MS) || 60000;

export class SentinelAPI {
  private static async fetchWithTimeout(url: string, options: RequestInit = {}): Promise<Response> {
    const controller = new AbortController();
    const id = setTimeout(() => controller.abort(), TIMEOUT_MS);
    
    try {
      const response = await fetch(url, {
        ...options,
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json',
          ...(options.headers || {}),
        },
      });
      clearTimeout(id);
      return response;
    } catch (err: any) {
      clearTimeout(id);
      if (err.name === 'AbortError') {
        throw new Error(`Request timed out after ${TIMEOUT_MS / 1000}s`);
      }
      throw err;
    }
  }

  static async checkHealth(): Promise<{ status: string; service: string }> {
    try {
      const res = await this.fetchWithTimeout(`${BASE_URL}/api/health`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    } catch (err) {
      return { status: 'error', service: 'sentinel-ring-orchestrator' };
    }
  }

  static async analyze(
    transactions: Transaction[],
    groundTruth?: GroundTruthItem[] | null
  ): Promise<OrchestratorResponse> {
    const payload: { transactions: Transaction[]; ground_truth?: GroundTruthItem[] } = {
      transactions,
    };

    if (groundTruth && groundTruth.length > 0) {
      payload.ground_truth = groundTruth;
    }

    const res = await this.fetchWithTimeout(`${BASE_URL}/api/analyze`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });

    if (!res.ok) {
      const errText = await res.text().catch(() => '');
      throw new Error(`Spring Boot API error (${res.status}): ${errText || res.statusText}`);
    }

    const data: OrchestratorResponse = await res.json();
    data.total_transactions = transactions.length;
    return data;
  }
}

// Storage helper for analysis run history
const RUNS_STORAGE_KEY = 'sentinel_ring_runs';

export class RunHistoryStore {
  static getRuns(): AnalysisRun[] {
    try {
      const raw = localStorage.getItem(RUNS_STORAGE_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  }

  static saveRun(run: AnalysisRun): void {
    const runs = this.getRuns();
    const updated = [run, ...runs.filter((r) => r.run_id !== run.run_id)].slice(0, 100);
    localStorage.setItem(RUNS_STORAGE_KEY, JSON.stringify(updated));
  }

  static deleteRun(runId: string): boolean {
    const runs = this.getRuns();
    const filtered = runs.filter((r) => r.run_id !== runId);
    localStorage.setItem(RUNS_STORAGE_KEY, JSON.stringify(filtered));
    return true;
  }

  static clearAll(): void {
    localStorage.removeItem(RUNS_STORAGE_KEY);
  }
}
