import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError, post } from '../lib/api'
import { Alert, Button, Field, Input, Select } from '../components/ui'

const schema = z.object({
  fullName: z.string().min(2, 'Enter your full name'),
  email: z.string().email('Enter a valid email address'),
  phone: z.string().min(7, 'Enter a phone number'),
  dateOfBirth: z.string().min(1, 'Enter your date of birth')
    .refine((d) => new Date(d) < new Date(), 'Date of birth must be in the past'),
  gender: z.enum(['FEMALE', 'MALE', 'OTHER', 'UNDISCLOSED']),
  password: z.string().min(8, 'Use at least 8 characters'),
})
type Form = z.infer<typeof schema>

export default function Register() {
  const [done, setDone] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const { register, handleSubmit, formState: { errors, isSubmitting } } =
    useForm<Form>({ resolver: zodResolver(schema), defaultValues: { gender: 'UNDISCLOSED' } })

  const onSubmit = async (f: Form) => {
    setError(null)
    try {
      await post('/auth/register', f)
      setDone(f.email)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not create the account')
    }
  }

  if (done) {
    return (
      <div className="max-w-md space-y-4">
        <h1>Check your email</h1>
        <p>We sent a confirmation link to <strong>{done}</strong>. Open it to activate your account, then log in.</p>
        <p className="text-ink-soft">Running locally? The email is in MailHog at <a href="http://localhost:8025">localhost:8025</a>.</p>
      </div>
    )
  }

  return (
    <div className="max-w-md space-y-6">
      <h1>Create your account</h1>
      {error && <Alert>{error}</Alert>}
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
        <Field label="Full name" htmlFor="fullName" error={errors.fullName?.message}>
          <Input id="fullName" autoComplete="name" {...register('fullName')} />
        </Field>
        <Field label="Email" htmlFor="email" error={errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" {...register('email')} />
        </Field>
        <Field label="Phone" htmlFor="phone" error={errors.phone?.message}>
          <Input id="phone" type="tel" autoComplete="tel" {...register('phone')} />
        </Field>
        <Field label="Date of birth" htmlFor="dob" error={errors.dateOfBirth?.message}>
          <Input id="dob" type="date" autoComplete="bday" {...register('dateOfBirth')} />
        </Field>
        <Field label="Gender" htmlFor="gender">
          <Select id="gender" {...register('gender')}>
            <option value="UNDISCLOSED">Prefer not to say</option>
            <option value="FEMALE">Female</option>
            <option value="MALE">Male</option>
            <option value="OTHER">Other</option>
          </Select>
        </Field>
        <Field label="Password" htmlFor="password" hint="At least 8 characters" error={errors.password?.message}>
          <Input id="password" type="password" autoComplete="new-password" {...register('password')} />
        </Field>
        <Button type="submit" busy={isSubmitting}>Create account</Button>
      </form>
      <p>Already registered? <Link to="/login">Log in</Link></p>
    </div>
  )
}
