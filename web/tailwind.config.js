/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        safe: '#10B981',
        low: '#3B82F6',
        medium: '#F59E0B',
        high: '#EF4444',
      },
    },
  },
  plugins: [],
}
