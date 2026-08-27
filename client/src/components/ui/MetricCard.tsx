import React from 'react';

interface MetricCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  color?: string;
  icon?: React.ReactNode;
  badgeText?: string;
}

export const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  subtitle,
  color = '#4F46E5',
  icon,
  badgeText,
}) => {
  return (
    <div
      className="group relative bg-surface border border-outline-variant rounded-2xl p-5 shadow-stitch flex flex-col justify-between transition-all duration-300 ease-out hover:-translate-y-1 hover:border-primary/50 hover:shadow-[0_4px_20px_rgba(79,70,229,0.18)] overflow-hidden"
      style={{ borderLeftWidth: '4px', borderLeftColor: color }}
    >
      {/* Subtle edge lighting glow on hover */}
      <div 
        className="absolute inset-0 opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity duration-300 bg-gradient-to-r from-primary/5 via-transparent to-primary/5" 
      />

      <div className="flex items-center justify-between mb-2 relative z-10">
        <span className="text-xs font-semibold uppercase tracking-wider text-secondary flex items-center gap-1.5 group-hover:text-primary transition-colors">
          {icon}
          {title}
        </span>
        {badgeText && (
          <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-surface-container text-on-surface-variant">
            {badgeText}
          </span>
        )}
      </div>

      <div className="flex items-baseline gap-2 my-1 relative z-10">
        <span className="text-2xl lg:text-3xl font-extrabold text-on-surface tracking-tight group-hover:scale-[1.02] transition-transform">
          {value}
        </span>
      </div>

      {subtitle && <div className="text-xs text-outline mt-1 relative z-10">{subtitle}</div>}
    </div>
  );
};
