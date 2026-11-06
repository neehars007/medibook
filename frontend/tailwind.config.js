/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        paper: '#F3F7F5',     // chart-paper background
        surface: '#FFFFFF',
        ink: { DEFAULT: '#12332B', soft: '#4A6660' },
        line: '#CBD8D3',
        teal: { DEFAULT: '#0E6E63', dark: '#0A5249', tint: '#DCEDE9' },
        card: { DEFAULT: '#F2C14E', tint: '#FCF1D2' }, // appointment-card yellow
        alert: { DEFAULT: '#B3261E', tint: '#FBE9E7' },
      },
      fontFamily: {
        sans: ['"Atkinson Hyperlegible Next"', '"Atkinson Hyperlegible"', 'system-ui', 'sans-serif'],
      },
      fontSize: {
        base: ['1.125rem', '1.6'],
      },
    },
  },
  plugins: [],
}
