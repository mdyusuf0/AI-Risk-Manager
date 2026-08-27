import React, { useState } from 'react';
import { OrchestratorResponse } from '../types/sentinel';
import { MetricCard } from '../components/ui/MetricCard';
import { LineChart, AlertCircle, RefreshCw } from 'lucide-react';

interface EvaluationProps {
  analysis: OrchestratorResponse | null;
}

export const Evaluation: React.FC<EvaluationProps> = ({ analysis }) => {
  const metrics = analysis?.metrics;
  const verdicts = analysis?.verdict || [];

  const [costPerFp, setCostPerFp] = useState<number>(50.0);
  const [currency, setCurrency] = useState<string>('USD');

  const precision = metrics?.precision ?? 0;
  const recall = metrics?.recall ?? 0;
  const f1 = precision + recall > 0 ? (2 * precision * recall) / (precision + recall) : 0;
  const fpCost = metrics?.false_positive_cost_estimate ?? 0;

  return (
    <div className="space-y-6">
      <div className="border-b border-outline-variant pb-4">
        <h2 className="text-2xl font-bold text-on-surface">Held-out Evaluation</h2>
        <p className="text-sm text-secondary">
          Compare predictions against held-out ground truth test labels.
        </p>
      </div>

      <div className="bg-amber-50 border border-amber-200 text-amber-900 p-4 rounded-xl text-xs leading-relaxed flex items-start gap-2.5">
        <AlertCircle className="w-4 h-4 text-amber-700 shrink-0 mt-0.5" />
        <div>
          <b>Disclaimer:</b> Precision and recall are measured against held-out test labels, not live operational outcomes.
          <code className="mx-1 bg-amber-100 px-1.5 py-0.5 rounded text-amber-950 font-semibold">risk_score</code> is a model prediction;
          <code className="mx-1 bg-amber-100 px-1.5 py-0.5 rounded text-amber-950 font-semibold">is_fraud</code> is a supplied ground-truth label.
        </div>
      </div>

      {!metrics ? (
        <div className="bg-surface border border-outline-variant rounded-2xl p-12 text-center shadow-stitch">
          <LineChart className="w-12 h-12 text-outline mx-auto mb-3" />
          <h3 className="text-base font-bold text-on-surface mb-1">No Evaluation Metrics Available</h3>
          <p className="text-xs text-secondary max-w-md mx-auto mb-4">
            To view Precision, Recall, and False-Positive cost estimations, run an analysis with a dataset containing an <code>is_fraud</code> ground truth column in Dataset Lab or Payment Simulator.
          </p>
        </div>
      ) : (
        <>
          {/* KPI Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <MetricCard
              title="Precision"
              value={`${(precision * 100).toFixed(1)}%`}
              subtitle="TP / (TP + FP)"
              color="#3525CD"
            />
            <MetricCard
              title="Recall"
              value={`${(recall * 100).toFixed(1)}%`}
              subtitle="TP / (TP + FN)"
              color="#10B981"
            />
            <MetricCard
              title="F1 Score"
              value={`${(f1 * 100).toFixed(1)}%`}
              subtitle="Harmonic Mean of P & R"
              color="#8B5CF6"
            />
            <MetricCard
              title={`FP Cost (${currency})`}
              value={`$${fpCost.toFixed(2)}`}
              subtitle={`Configured $${costPerFp}/FP`}
              color="#BA1A1A"
            />
          </div>

          {/* Confusion Matrix & Cost Form */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* 2x2 Confusion Matrix Heatmap Grid */}
            <div className="bg-surface border border-outline-variant rounded-2xl p-5 shadow-stitch lg:col-span-2 space-y-3">
              <div className="text-sm font-bold text-on-surface">2x2 Confusion Matrix Grid</div>
              <div className="text-xs text-secondary mb-3">
                Rows = Ground Truth (Supplied Label) · Columns = Model Prediction
              </div>

              <div className="grid grid-cols-2 gap-3 max-w-md mx-auto">
                <div className="bg-indigo-50 border border-indigo-200 p-6 rounded-xl text-center">
                  <div className="text-2xl font-extrabold text-indigo-900">TP</div>
                  <div className="text-xs font-semibold text-indigo-700 mt-1">True Positive</div>
                </div>

                <div className="bg-red-50 border border-red-200 p-6 rounded-xl text-center">
                  <div className="text-2xl font-extrabold text-red-900">FP</div>
                  <div className="text-xs font-semibold text-red-700 mt-1">False Positive</div>
                </div>

                <div className="bg-amber-50 border border-amber-200 p-6 rounded-xl text-center">
                  <div className="text-2xl font-extrabold text-amber-900">FN</div>
                  <div className="text-xs font-semibold text-amber-700 mt-1">False Negative</div>
                </div>

                <div className="bg-emerald-50 border border-emerald-200 p-6 rounded-xl text-center">
                  <div className="text-2xl font-extrabold text-emerald-900">TN</div>
                  <div className="text-xs font-semibold text-emerald-700 mt-1">True Negative</div>
                </div>
              </div>
            </div>

            {/* Cost Configuration */}
            <div className="bg-surface border border-outline-variant rounded-2xl p-5 shadow-stitch space-y-4">
              <div className="text-sm font-bold text-on-surface">Cost Parameters</div>
              
              <div>
                <label className="text-xs text-secondary font-medium block mb-1">Currency</label>
                <input
                  type="text"
                  value={currency}
                  onChange={(e) => setCurrency(e.target.value)}
                  className="w-full bg-surface-container-low border border-outline-variant rounded-xl p-2.5 text-xs font-semibold text-on-surface"
                />
              </div>

              <div>
                <label className="text-xs text-secondary font-medium block mb-1">Cost per False Positive ($)</label>
                <input
                  type="number"
                  value={costPerFp}
                  onChange={(e) => setCostPerFp(Number(e.target.value))}
                  className="w-full bg-surface-container-low border border-outline-variant rounded-xl p-2.5 text-xs font-semibold text-on-surface"
                />
              </div>

              <button
                onClick={() => alert(`Cost updated to $${costPerFp} ${currency} per false positive.`)}
                className="w-full flex items-center justify-center gap-2 bg-primary text-on-primary font-semibold text-xs py-2.5 rounded-xl hover:bg-primary-container transition-all"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                Update Cost Estimation
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
};
