/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        background: '#F9FAFB',
        surface: '#FFFFFF',
        'surface-dim': '#D9DADB',
        'surface-bright': '#F8F9FA',
        'surface-container-lowest': '#FFFFFF',
        'surface-container-low': '#F3F4F5',
        'surface-container': '#EDEEEF',
        'surface-container-high': '#E7E8E9',
        'surface-container-highest': '#E1E3E4',
        'on-surface': '#191C1D',
        'on-surface-variant': '#464555',
        'outline': '#777587',
        'outline-variant': '#C7C4D8',
        primary: '#3525CD',
        'primary-container': '#4F46E5',
        'on-primary': '#FFFFFF',
        'on-primary-container': '#DAD7FF',
        'primary-fixed': '#E2DFFF',
        secondary: '#555F6F',
        'secondary-container': '#D6E0F3',
        'on-secondary-container': '#596373',
        error: '#BA1A1A',
        'error-container': '#FFDAD6',
        'on-error-container': '#93000A',
        safe: '#10B981',
        warning: '#F59E0B'
      },
      borderRadius: {
        '2xl': '16px',
        'xl': '12px',
        'lg': '8px',
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
      },
      boxShadow: {
        stitch: '0 4px 6px -1px rgba(0, 0, 0, 0.04), 0 2px 4px -1px rgba(0, 0, 0, 0.02)',
      }
    },
  },
  plugins: [],
};
