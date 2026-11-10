/** @type {import('tailwindcss').Config} */
const v = (name) => `rgb(var(--${name}) / <alpha-value>)`
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      // Premium dark theme. Values live in src/index.css as CSS variables.
      colors: {
        canvas: v('inset'),            // inset panels inside cards
        surface: v('surface'),         // cards
        ink: { DEFAULT: v('ink'), soft: v('ink-soft'), faint: v('ink-faint') },
        line: { DEFAULT: v('line'), strong: v('line-strong') },
        brand: { DEFAULT: v('brand'), dark: v('brand-hi'), soft: v('brand-soft'), ring: v('brand-ring') },
        cyan: { DEFAULT: v('cyan'), soft: v('cyan-soft') },
        amber: { DEFAULT: v('amber'), soft: v('amber-soft') },
        danger: { DEFAULT: v('danger'), soft: v('danger-soft') },
        success: { DEFAULT: v('success'), soft: v('success-soft') },
      },
      fontFamily: { sans: ['"Plus Jakarta Sans Variable"', 'system-ui', 'sans-serif'] },
      boxShadow: {
        card: '0 1px 0 0 rgba(255,255,255,0.04) inset, 0 1px 2px rgba(0,0,0,0.4)',
        lift: '0 1px 0 0 rgba(255,255,255,0.06) inset, 0 24px 48px -16px rgba(0,0,0,0.7)',
        glow: '0 0 0 1px rgba(139,147,255,0.35), 0 8px 32px -6px rgba(99,102,241,0.55)',
      },
      keyframes: {
        rise: { '0%': { opacity: '0', transform: 'translateY(12px)' }, '100%': { opacity: '1', transform: 'none' } },
        pulseDot: { '0%,100%': { opacity: '1' }, '50%': { opacity: '.35' } },
      },
      animation: { rise: 'rise .7s cubic-bezier(.2,.7,.2,1) both', pulseDot: 'pulseDot 2s ease-in-out infinite' },
    },
  },
  plugins: [],
}
