import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, post, tokenStore } from '../lib/api'
import { Alert, Button, Field, Input, Spinner, buttonClass } from '../components/ui'
import AuthCard from '../components/AuthCard'
import type { AuthResponse } from '../lib/types'

/** Landing pages for the links in verification, reset and invite emails. */
export function VerifyEmail() {
  const [params] = useSearchParams()
  const [state, setState] = useState<'working' | 'ok' | string>('working')
  useEffect(() => {
    post('/auth/verify-email', { token: params.get('token') ?? '' })
      .then(() => setState('ok'))
      .catch((e) => setState(e instanceof ApiError ? e.message : 'Verification failed'))
  }, [params])
  if (state === 'working') return <Spinner label="Confirming your email" />
  return state === 'ok' ? (
    <AuthCard title="Email confirmed" sub="Your account is active.">
      <Link to="/login" className={buttonClass('primary', 'lg') + ' w-full'}>Log in</Link>
    </AuthCard>
  ) : (
    <AuthCard title="Link not valid"><Alert>{state}</Alert></AuthCard>
  )
}

function PasswordForm({ title, action, endpoint, field }:
  { title: string; action: string; endpoint: string; field: 'newPassword' | 'password' }) {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [pw, setPw] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (pw.length < 8) { setError('Use at least 8 characters'); return }
    setBusy(true)
    try {
      const r = await post<AuthResponse | undefined>(endpoint, { token: params.get('token'), [field]: pw })
      if (r?.accessToken) { tokenStore.set(r.accessToken); window.location.assign('/') } else navigate('/login')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    } finally { setBusy(false) }
  }
  return (
    <AuthCard title={title}>
      <form onSubmit={submit} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        <Field label="New password" htmlFor="pw" hint="At least 8 characters">
          <Input id="pw" type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} />
        </Field>
        <Button type="submit" size="lg" className="w-full" busy={busy}>{action}</Button>
      </form>
    </AuthCard>
  )
}

export const ResetPassword = () => <PasswordForm title="Choose a new password" action="Save password" endpoint="/auth/reset-password" field="newPassword" />
export const AcceptInvite = () => <PasswordForm title="Set your password" action="Activate account" endpoint="/auth/accept-invite" field="password" />
