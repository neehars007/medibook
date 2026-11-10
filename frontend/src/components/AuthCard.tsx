import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Logo } from './Layout'

export default function AuthCard({ title, sub, children }: { title: string; sub?: string; children: ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-md py-4">
      <div className="card shadow-lift p-7 sm:p-8 space-y-6">
        <Link to="/" className="no-underline inline-block"><Logo /></Link>
        <div className="space-y-1">
          <h1 className="text-2xl sm:text-3xl">{title}</h1>
          {sub && <p className="text-ink-soft">{sub}</p>}
        </div>
        {children}
      </div>
    </div>
  )
}
