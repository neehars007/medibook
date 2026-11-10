import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useAuth, homeFor } from '../lib/auth'
import { ApiError, post } from '../lib/api'
import { Alert, Button, Field, Input } from '../components/ui'
import AuthCard from '../components/AuthCard'

const schema = z.object({
  email: z.string().email('Enter a valid email address'),
  password: z.string().min(1, 'Enter your password'),
  totpCode: z.string().optional(),
})
type Form = z.infer<typeof schema>

export default function Login() {
  const { login, lostReason } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [error, setError] = useState<string | null>(null)
  const [needsCode, setNeedsCode] = useState(false)
  const [resetSent, setResetSent] = useState(false)
  const { register, handleSubmit, getValues, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) })

  const onSubmit = async (f: Form) => {
    setError(null)
    try {
      const u = await login(f.email, f.password, f.totpCode)
      navigate(params.get('next') || homeFor(u.role), { replace: true })
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Could not log in'
      if (msg.toLowerCase().includes('two-factor')) setNeedsCode(true)
      setError(msg)
    }
  }

  const forgot = async () => {
    const email = getValues('email')
    if (!email) { setError('Enter your email first, then choose "Forgot password".'); return }
    await post('/auth/forgot-password', { email }).catch(() => {})
    setResetSent(true)
  }

  return (
    <AuthCard title="Welcome back" sub="Log in to book and manage your visits.">
      {lostReason && <Alert tone="info">{lostReason}</Alert>}
      {error && <Alert>{error}</Alert>}
      {resetSent && <Alert tone="success">If that email has an account, a reset link is on its way.</Alert>}
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
        <Field label="Email" htmlFor="email" error={errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" {...register('email')} />
        </Field>
        <Field label="Password" htmlFor="password" error={errors.password?.message}>
          <Input id="password" type="password" autoComplete="current-password" {...register('password')} />
        </Field>
        {needsCode && (
          <Field label="6-digit code" htmlFor="totp" hint="From your authenticator app">
            <Input id="totp" inputMode="numeric" autoComplete="one-time-code" maxLength={6} {...register('totpCode')} />
          </Field>
        )}
        <div className="flex justify-end -mt-2">
          <button type="button" onClick={forgot} className="text-sm font-semibold text-brand hover:text-brand-dark">Forgot password?</button>
        </div>
        <Button type="submit" size="lg" className="w-full" busy={isSubmitting}>Log in</Button>
      </form>
      <p className="text-center text-sm text-ink-soft">New patient? <Link to="/register">Create an account</Link></p>
      <details className="rounded-xl border border-line bg-canvas px-4 py-3 text-sm">
        <summary className="cursor-pointer font-semibold">Demo logins</summary>
        <p className="mt-2 text-ink-soft">Password for all: <code className="font-semibold text-ink">Demo@12345</code></p>
        <ul className="mt-1 space-y-0.5 text-ink-soft">
          <li>patient@medibook.test</li><li>reception@medibook.test</li>
          <li>doctor@medibook.test</li><li>admin@medibook.test</li>
        </ul>
      </details>
    </AuthCard>
  )
}
