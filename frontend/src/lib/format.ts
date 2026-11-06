// All times are shown in the clinic's time zone, not the browser's.
const cache = new Map<string, Intl.DateTimeFormat>()
const fmt = (tz: string, opts: Intl.DateTimeFormatOptions) => {
  const key = tz + JSON.stringify(opts)
  if (!cache.has(key)) cache.set(key, new Intl.DateTimeFormat('en-US', { timeZone: tz, ...opts }))
  return cache.get(key)!
}

export const time = (iso: string, tz: string) => fmt(tz, { hour: 'numeric', minute: '2-digit' }).format(new Date(iso))
export const day = (iso: string, tz: string) => fmt(tz, { weekday: 'long', month: 'long', day: 'numeric' }).format(new Date(iso))
export const shortDay = (iso: string, tz: string) => fmt(tz, { weekday: 'short', month: 'short', day: 'numeric' }).format(new Date(iso))
export const dateTime = (iso: string, tz: string) => `${day(iso, tz)}, ${time(iso, tz)}`

/** yyyy-mm-dd for "today + n" in the clinic's time zone. */
export function isoDate(tz: string, plusDays = 0): string {
  const d = new Date(Date.now() + plusDays * 86_400_000)
  const parts = fmt(tz, { year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(d)
  const get = (t: string) => parts.find((p) => p.type === t)!.value
  return `${get('year')}-${get('month')}-${get('day')}`
}

/** Noon UTC of a yyyy-mm-dd: safe for showing the weekday of that calendar date. */
export const dateLabel = (ymd: string) =>
  new Intl.DateTimeFormat('en-US', { timeZone: 'UTC', weekday: 'short', month: 'short', day: 'numeric' }).format(new Date(`${ymd}T12:00:00Z`))

export const money = (n: number | null | undefined) =>
  n === null || n === undefined ? '' : n === 0 ? 'Free' : new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(n)

export const statusLabel: Record<string, string> = {
  PENDING_PAYMENT: 'Awaiting payment', CONFIRMED: 'Confirmed', CHECKED_IN: 'Checked in',
  IN_CONSULTATION: 'With the doctor', COMPLETED: 'Completed', EXPIRED: 'Expired', CANCELLED: 'Cancelled', NO_SHOW: 'Missed',
}
