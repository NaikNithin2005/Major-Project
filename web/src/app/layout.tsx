import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'Web Admin Security Dashboard - Quishing & Smishing Detection',
  description: 'Centralized Administrative Security & Threat Intelligence Dashboard',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  )
}
