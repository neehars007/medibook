import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from '../lib/auth'
import type { Role } from '../lib/types'
import { Spinner } from './ui'

export default function RequireAuth({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, ready } = useAuth()
  const loc = useLocation()
  if (!ready) return <Spinner label="Checking your session" />
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(loc.pathname + loc.search)}`} replace />
  if (roles && !roles.includes(user.role)) {
    return (
      <div className="card mx-auto max-w-md p-8 text-center space-y-2">
        <h1 className="text-2xl">Not available for your account</h1>
        <p className="text-ink-soft">This page is for {roles.map((r) => r.toLowerCase()).join(' or ')} accounts.</p>
      </div>
    )
  }
  return <>{children}</>
}
