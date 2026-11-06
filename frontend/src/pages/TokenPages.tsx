import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, post, tokenStore } from '../lib/api'
import { Alert, Button, Field, Input, Spinner } from '../components/ui'
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
    <div className="max-w-md space-y-4"><h1>Email confirmed</h1><p>Your account is active.</p><Link to="/login">Log in</Link></div>
  ) : (
    <div className="max-w-md space-y-4"><h1>Link not valid</h1><Alert>{state}</Alert></div>
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
    <form onSubmit={submit} className="max-w-md space-y-5">
      <h1>{title}</h1>
      {error && <Alert>{error}</Alert>}
      <Field label="New password" htmlFor="pw" hint="At least 8 characters">
        <Input id="pw" type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} />
      </Field>
      <Button type="submit" busy={busy}>{action}</Button>
    </form>
  )
}

export const ResetPassword = () => <PasswordForm title="Choose a new password" action="Save password" endpoint="/auth/reset-password" field="newPassword" />
export const AcceptInvite = () => <PasswordForm title="Set your password" action="Activate account" endpoint="/auth/accept-invite" field="password" />
