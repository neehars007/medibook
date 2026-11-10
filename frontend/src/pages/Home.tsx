import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, BellRing, ChevronRight, LockKeyhole, Search, Video } from 'lucide-react'
import { get } from '../lib/api'
import type { Doctor, Page, Specialty } from '../lib/types'
import { useClinic } from '../lib/queries'
import { shortDay, time } from '../lib/format'
import { Avatar, Button, Select, Spinner } from '../components/ui'
import SpecialtyIcon from '../components/SpecialtyIcon'

/** Counts up once when the value arrives: the page's single orchestrated motion. */
function CountUp({ to, suffix = '' }: { to: number; suffix?: string }) {
  const [n, setN] = useState(0)
  useEffect(() => {
    if (!to) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { setN(to); return }
    const start = performance.now()
    let raf = 0
    const tick = (t: number) => {
      const p = Math.min(1, (t - start) / 900)
      setN(Math.round(to * (1 - Math.pow(1 - p, 3))))
      if (p < 1) raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [to])
  return <>{n}{suffix}</>
}

function hoursUntil(iso: string) {
  return Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 3_600_000))
}

export default function Home() {
  const { tz, data: clinic } = useClinic()
  const navigate = useNavigate()
  const [spec, setSpec] = useState('')
  const specialties = useQuery({ queryKey: ['specialties'], queryFn: () => get<Specialty[]>('/specialties') })
  const doctors = useQuery({ queryKey: ['doctors', 'home'], queryFn: () => get<Page<Doctor>>('/doctors?size=12') })
  const soonest = [...(doctors.data?.content ?? [])]
    .filter((d) => d.nextAvailableSlot)
    .sort((a, b) => a.nextAvailableSlot!.localeCompare(b.nextAvailableSlot!))
    .slice(0, 4)
  const nextHours = soonest[0]?.nextAvailableSlot ? hoursUntil(soonest[0].nextAvailableSlot) : 0

  return (
    <div className="space-y-24">
      {/* Hero */}
      <section className="relative -mx-4 px-4 pt-6 sm:pt-12">
        <div aria-hidden className="grid-bg absolute inset-0 -z-10" />
        <div className="grid gap-12 lg:grid-cols-[1.15fr_1fr] items-center">
          <div className="space-y-7 animate-rise">
            <span className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.04] px-3 py-1.5 text-sm text-ink-soft">
              <span className="relative flex h-2 w-2"><span className="absolute inset-0 rounded-full bg-success animate-pulseDot" /><span className="h-2 w-2 rounded-full bg-success" /></span>
              Doctors taking bookings this week
            </span>
            <h1 className="text-grad text-[2.6rem] sm:text-6xl lg:text-[4.2rem] leading-[1.02] tracking-[-0.045em] font-bold">
              Care that fits<br />your calendar.
            </h1>
            <p className="text-lg text-ink-soft max-w-lg">
              Live availability across every specialist at {clinic?.name ?? 'the clinic'}. In person or video, confirmed in seconds.
            </p>

            <form className="flex flex-col sm:flex-row gap-2 rounded-2xl border border-white/10 bg-white/[0.04] p-2 backdrop-blur-xl max-w-xl shadow-lift"
              onSubmit={(e) => { e.preventDefault(); navigate(spec ? `/book?specialtyId=${spec}` : '/book') }}>
              <div className="relative flex-1">
                <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" aria-hidden />
                <label htmlFor="hero-spec" className="sr-only">Specialty</label>
                <Select id="hero-spec" value={spec} onChange={(e) => setSpec(e.target.value)} className="border-0 bg-transparent pl-10 focus:ring-0">
                  <option value="">Choose a specialty</option>
                  {specialties.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </Select>
              </div>
              <Button type="submit" size="lg">Find times<ArrowRight className="h-4 w-4" aria-hidden /></Button>
            </form>

            <dl className="grid grid-cols-3 max-w-lg divide-x divide-white/[0.08] rounded-2xl border border-white/[0.08] bg-white/[0.02]">
              <div className="px-4 py-3">
                <dt className="text-xs text-ink-faint">Specialists</dt>
                <dd className="text-2xl font-bold tabular-nums"><CountUp to={doctors.data?.page.totalElements ?? 0} /></dd>
              </div>
              <div className="px-4 py-3">
                <dt className="text-xs text-ink-faint">Specialties</dt>
                <dd className="text-2xl font-bold tabular-nums"><CountUp to={specialties.data?.length ?? 0} /></dd>
              </div>
              <div className="px-4 py-3">
                <dt className="text-xs text-ink-faint">Next opening</dt>
                <dd className="text-2xl font-bold tabular-nums text-grad-accent">{soonest.length ? (nextHours < 1 ? 'Now' : <>in <CountUp to={nextHours} suffix="h" /></>) : '—'}</dd>
              </div>
            </dl>
          </div>

          {/* Live schedule panel */}
          <aside className="relative animate-rise [animation-delay:120ms]" aria-labelledby="soon-title">
            <div aria-hidden className="absolute -inset-px rounded-[22px] bg-gradient-to-b from-[#8B93FF]/50 via-[#22D3EE]/10 to-transparent" />
            <div className="relative rounded-[21px] bg-[#0B1020]/95 p-5 sm:p-6 backdrop-blur-xl shadow-lift">
              <div className="flex items-center justify-between mb-2">
                <div>
                  <h2 id="soon-title" className="text-base">Next available</h2>
                  <p className="text-xs text-ink-faint">Updated live from the schedule</p>
                </div>
                <Link to="/doctors" className="text-sm">All doctors</Link>
              </div>
              {doctors.isLoading ? <Spinner /> : (
                <ul className="mt-3 space-y-2">
                  {soonest.map((d) => (
                    <li key={d.id}>
                      <Link to={`/book?doctorId=${d.id}`}
                        className="group no-underline flex items-center gap-3 rounded-xl border border-transparent p-3 hover:border-white/10 hover:bg-white/[0.04] transition-colors">
                        <Avatar name={d.fullName} />
                        <span className="flex-1 min-w-0">
                          <span className="block font-semibold text-ink truncate">{d.fullName}</span>
                          <span className="block text-sm text-ink-faint">{d.specialtyName}</span>
                        </span>
                        <span className="rounded-lg border border-cyan/20 bg-cyan-soft px-2.5 py-1 text-right">
                          <span className="block text-[11px] font-semibold text-cyan">{shortDay(d.nextAvailableSlot!, tz)}</span>
                          <span className="block text-sm font-semibold text-ink tabular-nums">{time(d.nextAvailableSlot!, tz)}</span>
                        </span>
                        <ChevronRight className="h-4 w-4 text-ink-faint group-hover:text-ink transition-transform group-hover:translate-x-0.5" aria-hidden />
                      </Link>
                    </li>
                  ))}
                  {soonest.length === 0 && <li className="py-6 text-ink-soft">No open times this week.</li>}
                </ul>
              )}
            </div>
          </aside>
        </div>
      </section>

      {/* Specialties */}
      <section className="space-y-6">
        <div className="flex items-end justify-between gap-4">
          <div>
            <h2 className="text-2xl sm:text-3xl">Browse by specialty</h2>
            <p className="text-ink-soft mt-1">Pick an area of care to see every open time.</p>
          </div>
        </div>
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {specialties.data?.map((s) => (
            <li key={s.id}>
              <Link to={`/book?specialtyId=${s.id}`} className="card card-hover group no-underline block p-5 h-full">
                <span className="grid h-11 w-11 place-items-center rounded-xl bg-brand-soft text-brand ring-1 ring-brand/20 mb-5 group-hover:bg-grad group-hover:text-white transition-colors">
                  <SpecialtyIcon icon={s.icon} />
                </span>
                <span className="block font-semibold text-ink">{s.name}</span>
                <span className="block text-sm text-ink-soft mt-1">{s.description}</span>
                <span className="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-brand">See times<ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" /></span>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      {/* Features */}
      <section className="grid gap-4 md:grid-cols-3">
        {[
          [Video, 'In person or video', 'Every doctor lists both clinic and video sessions. Join calls straight from your appointments.'],
          [BellRing, 'Reminders that arrive', 'Email confirmation with a calendar invite, then reminders 24 hours and 2 hours before.'],
          [LockKeyhole, 'Private by design', 'Notes and prescriptions are encrypted and visible only to you and your doctor.'],
        ].map(([Icon, t, d]) => {
          const I = Icon as typeof Video
          return (
            <div key={t as string} className="card p-6">
              <I className="h-5 w-5 text-cyan" aria-hidden />
              <p className="mt-4 font-semibold">{t as string}</p>
              <p className="mt-1 text-sm text-ink-soft">{d as string}</p>
            </div>
          )
        })}
      </section>
    </div>
  )
}
