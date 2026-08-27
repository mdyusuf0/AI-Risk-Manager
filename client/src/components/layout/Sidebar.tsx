import React from 'react';
import { PageId } from '../../types/sentinel';
import { 
  LayoutDashboard, 
  FlaskConical, 
  CreditCard, 
  Network, 
  LineChart, 
  History, 
  Settings,
  ShieldAlert
} from 'lucide-react';

interface SidebarProps {
  currentPage: PageId;
  onSelectPage: (page: PageId) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ currentPage, onSelectPage }) => {
  const navItems: { id: PageId; label: string; icon: React.FC<{ className?: string }> }[] = [
    { id: 'overview', label: 'Overview', icon: LayoutDashboard },
    { id: 'dataset-lab', label: 'Dataset Lab', icon: FlaskConical },
    { id: 'payment-simulator', label: 'Payment Simulator', icon: CreditCard },
    { id: 'ring-explorer', label: 'Ring Explorer', icon: Network },
    { id: 'evaluation', label: 'Evaluation', icon: LineChart },
    { id: 'run-history', label: 'Run History', icon: History },
    { id: 'settings', label: 'Settings', icon: Settings },
  ];

  return (
    <aside className="w-64 bg-surface border-r border-outline-variant flex flex-col h-screen fixed left-0 top-0 z-40 py-6">
      <div className="px-6 mb-8">
        <div className="text-[11px] font-bold uppercase tracking-wider text-primary mb-1">
          Razorpay Buildathon
        </div>
        <div className="text-xl font-bold text-on-surface">
          Sentinel Ring
        </div>
        <div className="text-xs text-on-surface-variant">
          Risk Operations Lab
        </div>
      </div>

      <nav className="flex-1 px-3 space-y-1 overflow-y-auto">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = currentPage === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onSelectPage(item.id)}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition-all ${
                isActive
                  ? 'bg-primary-fixed text-primary font-bold border-r-4 border-primary'
                  : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-low'
              }`}
            >
              <Icon className={`w-4 h-4 ${isActive ? 'text-primary' : 'text-secondary'}`} />
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>

      <div className="px-4 mt-auto">
        <div className="p-3.5 bg-surface-container-low rounded-xl border border-outline-variant/60">
          <div className="flex items-center gap-2 text-xs font-bold text-on-surface mb-1">
            <ShieldAlert className="w-4 h-4 text-primary" />
            DEFENSE-ONLY POLICY
          </div>
          <div className="text-[11px] text-secondary leading-snug">
            Flags & explains risk. Human analyst reviews every flag. Zero auto-blocking.
          </div>
        </div>

        <div className="text-[10px] text-outline text-center mt-4">
          v1.0.0 · Stitch Design System
        </div>
      </div>
    </aside>
  );
};
