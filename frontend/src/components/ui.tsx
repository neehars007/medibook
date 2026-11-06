import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
const variants: Record<Variant, string> = {
  primary: 'bg-teal text-white hover:bg-teal-dark border-2 border-teal hover:border-teal-dark',
  secondary: 'bg-surface text-ink border-2 border-ink hover:bg-teal-tint',
  ghost: 'bg-transparent text-teal border-2 border-transparent hover:bg-teal-tint',
  danger: 'bg-surface text-alert border-2 border-alert hover:bg-alert-tint',
}

export function Button({ variant = 'primary', busy, className = '', children, disabled, ...rest }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; busy?: boolean }) {
  return (
    <button
      {...rest}
      disabled={disabled || busy}
      className={`inline-flex items-center justify-center gap-2 rounded-md px-5 py-3 font-bold min-h-[48px]
        disabled:opacity-50 disabled:cursor-not-allowed ${variants[variant]} ${className}`}
    >
      {busy && <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" aria-hidden />}
      {children}
    </button>
  )
}

export function Field({ label, hint, error, children, htmlFor }:
  { label: string; hint?: string; error?: string; children: ReactNode; htmlFor: string }) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="block font-bold">{label}</label>
      {hint && <p id={`${htmlFor}-hint`} className="text-ink-soft text-base">{hint}</p>}
      {children}
      {error && <p role="alert" className="text-alert font-bold">{error}</p>}
    </div>
  )
}

const inputCls = 'w-full rounded-md border-2 border-line bg-surface px-4 py-3 text-base text-ink placeholder:text-ink-soft/70 focus:border-teal'

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className = '', ...rest }, ref) => <input ref={ref} {...rest} className={`${inputCls} ${className}`} />)
Input.displayName = 'Input'

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className = '', ...rest }, ref) => <textarea ref={ref} rows={3} {...rest} className={`${inputCls} ${className}`} />)
Textarea.displayName = 'Textarea'

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className = '', children, ...rest }, ref) => <select ref={ref} {...rest} className={`${inputCls} ${className}`}>{children}</select>)
Select.displayName = 'Select'

export function Alert({ tone = 'error', children }: { tone?: 'error' | 'info' | 'success'; children: ReactNode }) {
  const cls = tone === 'error' ? 'border-alert bg-alert-tint text-alert'
    : tone === 'success' ? 'border-teal bg-teal-tint text-teal-dark' : 'border-line bg-surface text-ink'
  return <div role={tone === 'error' ? 'alert' : 'status'} className={`rounded-md border-l-4 px-4 py-3 font-medium ${cls}`}>{children}</div>
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex items-center gap-3 py-8 text-ink-soft" role="status">
      <span className="h-5 w-5 animate-spin rounded-full border-2 border-teal border-t-transparent" aria-hidden />
      {label}…
    </div>
  )
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="rounded-md border-2 border-dashed border-line px-6 py-10">
      <p className="text-xl font-bold">{title}</p>
      {children && <div className="mt-2 text-ink-soft">{children}</div>}
    </div>
  )
}
