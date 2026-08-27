import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import App from '../App';

describe('Sentinel Ring React Frontend App', () => {
  it('renders brand header title', () => {
    render(<App />);
    const matches = screen.getAllByText('Sentinel Ring');
    expect(matches.length).toBeGreaterThan(0);
    expect(matches[0]).toBeInTheDocument();
  });

  it('renders sidebar navigation items', () => {
    render(<App />);
    expect(screen.getByText('Overview')).toBeInTheDocument();
    expect(screen.getByText('Dataset Lab')).toBeInTheDocument();
    expect(screen.getByText('Payment Simulator')).toBeInTheDocument();
    expect(screen.getByText('Ring Explorer')).toBeInTheDocument();
    expect(screen.getByText('Evaluation')).toBeInTheDocument();
    expect(screen.getByText('Run History')).toBeInTheDocument();
    expect(screen.getByText('Settings')).toBeInTheDocument();
  });
});
