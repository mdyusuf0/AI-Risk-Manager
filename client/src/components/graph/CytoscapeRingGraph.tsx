import React, { useEffect, useRef, useState } from 'react';
import cytoscape from 'cytoscape';
import { VerdictItem } from '../../types/sentinel';

interface CytoscapeRingGraphProps {
  verdicts: VerdictItem[];
  onSelectNode?: (verdict: VerdictItem) => void;
}

export const CytoscapeRingGraph: React.FC<CytoscapeRingGraphProps> = ({
  verdicts,
  onSelectNode,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const cyRef = useRef<cytoscape.Core | null>(null);
  const [selectedAccount, setSelectedAccount] = useState<VerdictItem | null>(null);

  useEffect(() => {
    if (!containerRef.current || !verdicts || verdicts.length === 0) return;

    // Prepare Cytoscape elements
    const elements: cytoscape.ElementDefinition[] = [];

    verdicts.forEach((v) => {
      elements.push({
        data: {
          id: v.account_id,
          label: v.account_id,
          risk: v.risk_score,
          flagged: v.flagged,
          ringId: v.ring_id || 'No Ring',
          explanation: v.explanation || '',
        },
      });
    });

    // Group accounts by ring_id and add connecting edges
    const ringGroups: Record<string, string[]> = {};
    verdicts.forEach((v) => {
      if (v.ring_id && v.ring_id !== '—') {
        ringGroups[v.ring_id] = ringGroups[v.ring_id] || [];
        ringGroups[v.ring_id].push(v.account_id);
      }
    });

    Object.entries(ringGroups).forEach(([ringId, members]) => {
      for (let i = 0; i < members.length; i++) {
        for (let j = i + 1; j < members.length; j++) {
          elements.push({
            data: {
              id: `edge_${ringId}_${members[i]}_${members[j]}`,
              source: members[i],
              target: members[j],
              ringId,
            },
          });
        }
      }
    });

    const cy = cytoscape({
      container: containerRef.current,
      elements,
      style: [
        {
          selector: 'node',
          style: {
            'background-color': (ele: any) => (ele.data('flagged') ? '#BA1A1A' : '#10B981'),
            width: (ele: any) => Math.max(28, Math.min(60, ele.data('risk') * 55)),
            height: (ele: any) => Math.max(28, Math.min(60, ele.data('risk') * 55)),
            label: 'data(label)',
            color: '#191C1D',
            'font-family': 'Inter, sans-serif',
            'font-size': '11px',
            'font-weight': 600,
            'text-valign': 'bottom',
            'text-margin-y': 5,
            'border-width': 2,
            'border-color': '#FFFFFF',
          },
        },
        {
          selector: 'node:selected',
          style: {
            'border-width': 4,
            'border-color': '#4F46E5',
          },
        },
        {
          selector: 'edge',
          style: {
            width: 2,
            'line-color': '#4F46E5',
            'curve-style': 'bezier',
            opacity: 0.7,
          },
        },
      ],
      layout: {
        name: 'cose',
        animate: false,
        padding: 40,
      },
    });

    cy.on('tap', 'node', (evt) => {
      const node = evt.target;
      const accId = node.id();
      const match = verdicts.find((v) => v.account_id === accId);
      if (match) {
        setSelectedAccount(match);
        if (onSelectNode) onSelectNode(match);
      }
    });

    cyRef.current = cy;

    return () => {
      if (cyRef.current) {
        cyRef.current.destroy();
      }
    };
  }, [verdicts, onSelectNode]);

  return (
    <div className="relative w-full h-[480px] bg-background border border-outline-variant rounded-2xl overflow-hidden">
      <div ref={containerRef} className="w-full h-full" />

      {selectedAccount && (
        <div className="absolute bottom-4 right-4 bg-surface border border-outline-variant rounded-xl p-4 shadow-lg max-w-xs text-xs">
          <div className="flex justify-between items-center mb-2">
            <span className="font-bold text-on-surface">{selectedAccount.account_id}</span>
            <button
              onClick={() => setSelectedAccount(null)}
              className="text-secondary hover:text-on-surface"
            >
              ✕
            </button>
          </div>
          <div className="space-y-1 text-on-surface-variant">
            <div>Risk Score: <span className="font-semibold text-primary">{selectedAccount.risk_score.toFixed(4)}</span></div>
            <div>Flagged: <span className={`font-semibold ${selectedAccount.flagged ? 'text-error' : 'text-safe'}`}>{selectedAccount.flagged ? 'Yes' : 'No'}</span></div>
            <div>Ring ID: <span className="font-semibold">{selectedAccount.ring_id || '—'}</span></div>
            {selectedAccount.explanation && (
              <div className="mt-2 pt-2 border-t border-outline-variant text-[11px] leading-snug">
                {selectedAccount.explanation}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
