import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { AlertCircle, CheckCircle2, Info, Loader2 } from 'lucide-react'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
type Size = 'md' | 'lg' | 'sm'
const variants: Record<Variant, string> = {
  primary: 'bg-grad text-white shadow-glow hover:brightness-110',
  secondary: 'bg-white/[0.04] text-ink border border-line-strong hover:bg-white/[0.08] hover:border-ink-faint/50',
  ghost: 'bg-transparent text-ink-soft hover:text-ink hover:bg-white/[0.05]',
  danger: 'bg-transparent text-danger border border-danger/30 hover:bg-danger-soft',
}
const sizes: Record<Size, string> = { sm: 'h-9 px-3 text-sm', md: 'h-11 px-5', lg: 'h-12 px-6 text-[17px]' }

export function buttonClass(variant: Variant = 'primary', size: Size = 'md') {
  return `inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition-colors no-underline
    disabled:opacity-50 disabled:cursor-not-allowed ${variants[variant]} ${sizes[size]}`
}

export function Button({ variant = 'primary', size = 'md', busy, className = '', children, disabled, ...rest }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: Size; busy?: boolean }) {
  return (
    <button {...rest} disabled={disabled || busy} className={`${buttonClass(variant, size)} ${className}`}>
      {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
      {children}
    </button>
  )
}

export function Field({ label, hint, error, children, htmlFor }:
  { label: string; hint?: string; error?: string; children: ReactNode; htmlFor: string }) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="block text-sm font-semibold text-ink">{label}</label>
      {children}
      {hint && !error && <p className="text-sm text-ink-faint">{hint}</p>}
      {error && <p role="alert" className="text-sm font-medium text-danger">{error}</p>}
    </div>
  )
}

const inputCls = `w-full rounded-xl border border-line-strong bg-white/[0.03] px-3.5 text-ink placeholder:text-ink-faint
  focus:border-brand-ring focus:bg-white/[0.05] focus:ring-4 focus:ring-brand-ring/25 focus:outline-none transition`

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className = '', ...rest }, ref) => <input ref={ref} {...rest} className={`${inputCls} h-11 ${className}`} />)
Input.displayName = 'Input'

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className = '', ...rest }, ref) => <textarea ref={ref} rows={3} {...rest} className={`${inputCls} py-2.5 ${className}`} />)
Textarea.displayName = 'Textarea'

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className = '', children, ...rest }, ref) => <select ref={ref} {...rest} className={`${inputCls} h-11 pr-9 ${className}`}>{children}</select>)
Select.displayName = 'Select'

export function Alert({ tone = 'error', children }: { tone?: 'error' | 'info' | 'success'; children: ReactNode }) {
  const map = {
    error: ['border-danger/20 bg-danger-soft text-danger', AlertCircle],
    success: ['border-success/20 bg-success-soft text-success', CheckCircle2],
    info: ['border-brand/20 bg-brand-soft text-brand-dark', Info],
  } as const
  const [cls, Icon] = map[tone]
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={`flex gap-3 rounded-xl border px-4 py-3 text-sm font-medium ${cls}`}>
      <Icon className="h-5 w-5 shrink-0" aria-hidden />
      <div>{children}</div>
    </div>
  )
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex items-center gap-3 py-10 text-ink-soft" role="status">
      <Loader2 className="h-5 w-5 animate-spin text-brand" aria-hidden />{label}…
    </div>
  )
}

export function Empty({ title, children, icon }: { title: string; children?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="card px-6 py-12 text-center">
      {icon && <div className="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-2xl bg-brand-soft text-brand ring-1 ring-brand/20">{icon}</div>}
      <p className="font-semibold text-lg">{title}</p>
      {children && <div className="mt-1 text-ink-soft">{children}</div>}
    </div>
  )
}

/** Initials avatar, tinted per name so doctors are easy to tell apart. */
const tints = [
  'bg-[#1B2150] text-[#A5B4FC] ring-1 ring-[#A5B4FC]/20', 'bg-[#0B2E38] text-[#67E8F9] ring-1 ring-[#67E8F9]/20',
  'bg-[#2A1C44] text-[#D8B4FE] ring-1 ring-[#D8B4FE]/20', 'bg-[#33200F] text-[#FDBA74] ring-1 ring-[#FDBA74]/20',
  'bg-[#0D2E24] text-[#6EE7B7] ring-1 ring-[#6EE7B7]/20',
]
export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' | 'lg' }) {
  const clean = name.replace(/^Dr\.?\s+/i, '').replace(/^(Mr|Mrs|Ms|Miss)\.?\s+/i, '')
  const parts = clean.split(/\s+/).filter(Boolean)
  const initials = ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase()
  const tint = tints[Math.abs([...name].reduce((h, c) => (h * 31 + c.charCodeAt(0)) | 0, 7)) % tints.length]
  const dim = size === 'lg' ? 'h-16 w-16 text-xl' : size === 'sm' ? 'h-9 w-9 text-sm' : 'h-12 w-12 text-base'
  return <span aria-hidden className={`grid shrink-0 place-items-center rounded-full font-semibold ${dim} ${tint}`}>{initials}</span>
}

export function Badge({ tone = 'neutral', children }: { tone?: 'neutral' | 'brand' | 'success' | 'danger' | 'amber'; children: ReactNode }) {
  const cls = {
    neutral: 'bg-white/[0.04] text-ink-soft border border-line',
    brand: 'bg-brand-soft text-brand-dark border border-brand/20',
    success: 'bg-success-soft text-success border border-success/20',
    danger: 'bg-danger-soft text-danger border border-danger/20',
    amber: 'bg-amber-soft text-amber border border-amber/20',
  }[tone]
  return <span className={`chip ${cls}`}>{children}</span>
}
