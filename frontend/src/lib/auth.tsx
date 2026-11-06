import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { post, refreshSession, tokenStore } from './api'
import type { AuthResponse, Role } from './types'

interface User { id: string; email: string; role: Role }
interface AuthState {
  user: User | null
  ready: boolean
  login: (email: string, password: string, totpCode?: string) => Promise<User>
  logout: (reason?: string) => Promise<void>
  lostReason: string | null
}

const AuthContext = createContext<AuthState | null>(null)

const toUser = (r: AuthResponse): User => ({ id: r.userId, email: r.email, role: r.role })

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [ready, setReady] = useState(false)
  const [lostReason, setLostReason] = useState<string | null>(null)
  const qc = useQueryClient()

  // Restore the session from the refresh cookie on page load.
  useEffect(() => {
    refreshSession().then((r) => {
      if (r) setUser(toUser(r))
      setReady(true)
    })
  }, [])

  useEffect(() => {
    tokenStore.onLost(() => {
      setUser(null)
      setLostReason('Your session ended. Log in again to continue.')
      qc.clear()
    })
  }, [qc])

  const login = useCallback(async (email: string, password: string, totpCode?: string) => {
    const r = await post<AuthResponse>('/auth/login', { email, password, totpCode: totpCode || null })
    tokenStore.set(r.accessToken)
    const u = toUser(r)
    setUser(u)
    setLostReason(null)
    return u
  }, [])

  const logout = useCallback(async (reason?: string) => {
    try { await post('/auth/logout') } catch { /* already gone */ }
    tokenStore.set(null)
    setUser(null)
    setLostReason(reason ?? null)
    qc.clear()
  }, [qc])

  const value = useMemo(() => ({ user, ready, login, logout, lostReason }), [user, ready, login, logout, lostReason])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth outside AuthProvider')
  return ctx
}

export function homeFor(role: Role): string {
  switch (role) {
    case 'PATIENT': return '/me/appointments'
    case 'DOCTOR': return '/doctor/today'
    case 'RECEPTIONIST': return '/reception/today'
    default: return '/admin/dashboard'
  }
}
