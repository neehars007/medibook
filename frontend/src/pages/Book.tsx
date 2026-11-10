import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import {
  ArrowLeft, Building2, CalendarCheck2, CalendarDays, Check, ChevronRight, Clock, CreditCard, MapPin, Stethoscope,
  Timer, UserRound, Users, Video, Zap,
} from 'lucide-react'
import { ApiError, del, get, post, qs } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useClinic } from '../lib/queries'
import { dateLabel, dateTime, day, isoDate, money, time } from '../lib/format'
import type {
  AppointmentType, BookingResponse, Doctor, Hold, IntakeForm, Mode, Page, Patient, PaymentMode, Slot, Specialty,
} from '../lib/types'
import { Alert, Avatar, Badge, Button, Empty, Field, Input, Spinner, Textarea, buttonClass } from '../components/ui'
import SpecialtyIcon from '../components/SpecialtyIcon'

/*
 * Booking wizard. Choices that survive a login redirect (specialty, doctor, type, patient) live in
 * the URL; the slot hold and the form are kept in memory.
 */
const STEPS = ['Doctor', 'Visit type', 'Patient', 'Date & time', 'Details', 'Done'] as const
const ANY = 'any'

export default function Book() {
  const [params, setParams] = useSearchParams()
  const specialtyId = params.get('specialtyId') ?? ''
  const doctorId = params.get('doctorId') ?? ''
  const typeId = params.get('typeId') ?? ''
  const patientId = params.get('patientId') ?? ''

  const [hold, setHold] = useState<Hold | null>(null)
  const [booking, setBooking] = useState<BookingResponse | null>(null)

  const set = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)))
    setParams(next)
  }

  // A doctor link (/book?doctorId=…) implies the specialty.
  const doctor = useQuery({
    queryKey: ['doctor', doctorId], enabled: !!doctorId && doctorId !== ANY,
    queryFn: () => get<Doctor>(`/doctors/${doctorId}`),
  })
  const effectiveSpecialty = specialtyId || doctor.data?.specialtyId || ''

  let step = 0
  if (doctorId && effectiveSpecialty) step = 1
  if (step === 1 && typeId) step = 2
  if (step === 2 && patientId) step = 3
  if (step === 3 && hold) step = 4
  if (booking) step = 5

  if (step === 5 && booking) return <Confirmation booking={booking} />

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        <h1>Book a visit</h1>
        <Steps current={step} />
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_320px] items-start">
        <div className="card p-5 sm:p-7 min-w-0">
          {step === 0 && doctorId && doctorId !== ANY && doctor.isLoading && <Spinner />}
          {step === 0 && !(doctorId && doctorId !== ANY && doctor.isLoading) && <ChooseDoctor specialtyId={specialtyId} onSpecialty={(id) => set({ specialtyId: id, doctorId: null })}
            onDoctor={(id) => set({ specialtyId: effectiveSpecialty || specialtyId, doctorId: id })} />}
          {step === 1 && <ChooseType specialtyId={effectiveSpecialty} doctor={doctor.data}
            onBack={() => set({ doctorId: null, typeId: null })} onPick={(id) => set({ typeId: id })} />}
          {step === 2 && <ChoosePatient onBack={() => set({ typeId: null })} onPick={(id) => set({ patientId: id })} />}
          {step === 3 && <ChooseTime specialtyId={effectiveSpecialty} doctorId={doctorId} typeId={typeId} patientId={patientId}
            onBack={() => set({ patientId: null })} onHeld={setHold} />}
          {step === 4 && hold && <Details hold={hold} typeId={typeId} patientId={patientId}
            onBack={async () => { await del(`/holds/${hold.holdId}`).catch(() => {}); setHold(null) }}
            onBooked={(b) => { setBooking(b); setHold(null) }} />}
        </div>
        <Summary specialtyId={effectiveSpecialty} doctorId={doctorId} typeId={typeId} patientId={patientId} hold={hold}
          onExpired={() => setHold(null)} />
      </div>
    </div>
  )
}

// ---------- Progress ----------
function Steps({ current }: { current: number }) {
  return (
    <ol className="grid grid-cols-6 gap-2" aria-label="Booking steps">
      {STEPS.map((s, i) => (
        <li key={s} aria-current={i === current ? 'step' : undefined} className="space-y-2">
          <span className={`block h-1.5 rounded-full ${i < current ? 'bg-brand' : i === current ? 'bg-brand/60' : 'bg-line'}`} />
          <span className={`hidden sm:block text-sm ${i === current ? 'font-semibold text-ink' : i < current ? 'text-brand' : 'text-ink-faint'}`}>
            {s}
          </span>
        </li>
      ))}
    </ol>
  )
}

function StepHeader({ title, sub, onBack }: { title: string; sub?: string; onBack?: () => void }) {
  return (
    <div className="mb-6 space-y-1">
      {onBack && (
        <button type="button" onClick={onBack} className="mb-3 inline-flex items-center gap-1.5 text-sm font-semibold text-ink-soft hover:text-ink">
          <ArrowLeft className="h-4 w-4" aria-hidden />Back
        </button>
      )}
      <h2 className="text-2xl">{title}</h2>
      {sub && <p className="text-ink-soft">{sub}</p>}
    </div>
  )
}

function Option({ onClick, children, selected }: { onClick: () => void; children: ReactNode; selected?: boolean }) {
  return (
    <button type="button" onClick={onClick}
      className={`group w-full text-left rounded-xl border px-4 py-4 flex items-center gap-4 transition-colors
        ${selected ? 'border-brand bg-brand-soft' : 'border-line hover:border-brand/50 hover:bg-canvas'}`}>
      {children}
      <ChevronRight className="ml-auto h-5 w-5 shrink-0 text-ink-faint group-hover:text-brand" aria-hidden />
    </button>
  )
}

// ---------- Sidebar summary ----------
function Summary({ specialtyId, doctorId, typeId, patientId, hold, onExpired }:
  { specialtyId: string; doctorId: string; typeId: string; patientId: string; hold: Hold | null; onExpired: () => void }) {
  const { tz, data: clinic } = useClinic()
  const { user } = useAuth()
  const specialties = useQuery({ queryKey: ['specialties'], queryFn: () => get<Specialty[]>('/specialties') })
  const docId = hold?.doctorId ?? (doctorId !== ANY ? doctorId : '')
  const doctor = useQuery({ queryKey: ['doctor', docId], enabled: !!docId, queryFn: () => get<Doctor>(`/doctors/${docId}`) })
  const types = useQuery({ queryKey: ['types', specialtyId], enabled: !!specialtyId, queryFn: () => get<AppointmentType[]>(`/appointment-types?specialtyId=${specialtyId}`) })
  const me = useQuery({ queryKey: ['me'], enabled: user?.role === 'PATIENT' && !!patientId, queryFn: () => get<Patient>('/patients/me') })
  const deps = useQuery({ queryKey: ['dependents'], enabled: user?.role === 'PATIENT' && !!patientId, queryFn: () => get<Patient[]>('/patients/me/dependents') })
  const type = types.data?.find((t) => t.id === typeId)
  const patient = [me.data, ...(deps.data ?? [])].find((p) => p?.id === patientId)
  const fee = type ? (type.fee ?? doctor.data?.consultationFee) : undefined

  const rows: [ReactNode, string, string | undefined][] = [
    [<Stethoscope className="h-4 w-4" />, 'Specialty', specialties.data?.find((s) => s.id === specialtyId)?.name],
    [<UserRound className="h-4 w-4" />, 'Doctor', doctor.data?.fullName ?? (doctorId === ANY ? 'First available' : undefined)],
    [<CalendarCheck2 className="h-4 w-4" />, 'Visit', type ? `${type.name}, ${type.durationMinutes} min` : undefined],
    [<Users className="h-4 w-4" />, 'Patient', patient?.fullName],
    [<CalendarDays className="h-4 w-4" />, 'When', hold ? dateTime(hold.startAt, tz) : undefined],
    [hold?.mode === 'VIDEO' ? <Video className="h-4 w-4" /> : <MapPin className="h-4 w-4" />, 'Where',
      hold ? (hold.mode === 'VIDEO' ? 'Video call' : clinic?.name) : undefined],
  ]

  return (
    <aside className="space-y-3 lg:sticky lg:top-24">
      {hold && <HoldTimer hold={hold} onExpired={onExpired} />}
      <div className="card p-5">
        <h2 className="text-base mb-4">Your appointment</h2>
        <dl className="space-y-3">
          {rows.map(([icon, label, value]) => (
            <div key={label} className="flex gap-3">
              <span className={`mt-0.5 ${value ? 'text-brand' : 'text-ink-faint'}`} aria-hidden>{icon}</span>
              <div className="min-w-0">
                <dt className="text-sm text-ink-faint">{label}</dt>
                <dd className={value ? 'font-medium' : 'text-ink-faint'}>{value ?? 'Not chosen yet'}</dd>
              </div>
            </div>
          ))}
        </dl>
        {fee !== undefined && (
          <div className="mt-4 pt-4 border-t border-line flex justify-between">
            <span className="text-ink-soft">OP fee</span><strong>{money(fee)}</strong>
          </div>
        )}
      </div>
    </aside>
  )
}

function HoldTimer({ hold, onExpired }: { hold: Hold; onExpired: () => void }) {
  const [left, setLeft] = useState(() => new Date(hold.expiresAt).getTime() - Date.now())
  useEffect(() => {
    const t = setInterval(() => {
      const l = new Date(hold.expiresAt).getTime() - Date.now()
      setLeft(l)
      if (l <= 0) { clearInterval(t); onExpired() }
    }, 1000)
    return () => clearInterval(t)
  }, [hold, onExpired])
  const s = Math.max(0, Math.floor(left / 1000))
  return (
    <div className="flex items-center gap-3 rounded-xl border border-amber/40 bg-amber-soft px-4 py-3 text-sm" role="timer" aria-live="off">
      <Timer className="h-5 w-5 text-amber" aria-hidden />
      <span>Time held for <strong className="tabular-nums">{Math.floor(s / 60)}:{String(s % 60).padStart(2, '0')}</strong></span>
    </div>
  )
}

// ---------- Step 1: doctor ----------
function ChooseDoctor({ specialtyId, onSpecialty, onDoctor }:
  { specialtyId: string; onSpecialty: (id: string) => void; onDoctor: (id: string) => void }) {
  const { tz } = useClinic()
  const specialties = useQuery({ queryKey: ['specialties'], queryFn: () => get<Specialty[]>('/specialties') })
  const doctors = useQuery({
    queryKey: ['doctors', specialtyId], enabled: !!specialtyId,
    queryFn: () => get<Page<Doctor>>(`/doctors${qs({ specialtyId, size: 50 })}`),
  })

  return (
    <section>
      <StepHeader title="What kind of care do you need?" />
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {specialties.data?.map((s) => (
          <button key={s.id} onClick={() => onSpecialty(s.id)} aria-pressed={s.id === specialtyId}
            className={`rounded-xl border p-4 text-left transition-colors ${s.id === specialtyId ? 'border-brand bg-brand-soft' : 'border-line hover:border-brand/50'}`}>
            <span className={`grid h-9 w-9 place-items-center rounded-lg mb-2 ${s.id === specialtyId ? 'bg-grad text-white' : 'bg-brand-soft text-brand'}`}>
              <SpecialtyIcon icon={s.icon} className="h-4 w-4" />
            </span>
            <span className="block font-semibold text-sm">{s.name}</span>
          </button>
        ))}
      </div>

      {specialtyId && (
        <div className="mt-8">
          <h3 className="text-lg mb-3">Choose a doctor</h3>
          {doctors.isLoading ? <Spinner /> : (
            <div className="space-y-2">
              <Option onClick={() => onDoctor(ANY)}>
                <span className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-grad text-white"><Zap className="h-5 w-5" /></span>
                <span>
                  <span className="block font-semibold">First available doctor</span>
                  <span className="block text-sm text-ink-soft">See every open time in this specialty</span>
                </span>
              </Option>
              {doctors.data?.content.map((d) => (
                <Option key={d.id} onClick={() => onDoctor(d.id)}>
                  <Avatar name={d.fullName} />
                  <span className="min-w-0">
                    <span className="block font-semibold">{d.fullName}</span>
                    <span className="block text-sm text-ink-soft">
                      {d.experienceYears} yrs, {d.languages.join(', ') || 'English'}
                    </span>
                    <span className="block text-sm text-brand font-medium">
                      {d.nextAvailableSlot ? `Next availability ${dateTime(d.nextAvailableSlot, tz)}` : 'No availability this week'}
                    </span>
                  </span>
                  <span className="ml-auto font-semibold whitespace-nowrap">{money(d.consultationFee)}</span>
                </Option>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  )
}

// ---------- Step 2: visit type ----------
function ChooseType({ specialtyId, doctor, onBack, onPick }:
  { specialtyId: string; doctor?: Doctor; onBack: () => void; onPick: (id: string) => void }) {
  const types = useQuery({
    queryKey: ['types', specialtyId],
    queryFn: () => get<AppointmentType[]>(`/appointment-types?specialtyId=${specialtyId}`),
  })
  return (
    <section>
      <StepHeader title="What type of visit?" sub={doctor ? `With ${doctor.fullName}` : undefined} onBack={onBack} />
      {types.isLoading ? <Spinner /> : (
        <div className="space-y-2">
          {types.data?.map((t) => (
            <Option key={t.id} onClick={() => onPick(t.id)}>
              <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-brand-soft text-brand">
                {t.mode === 'VIDEO' ? <Video className="h-5 w-5" /> : <Building2 className="h-5 w-5" />}
              </span>
              <span className="min-w-0">
                <span className="flex flex-wrap items-center gap-2 font-semibold">{t.name}
                  {t.followUp && <Badge tone="amber">Follow-up only</Badge>}</span>
                <span className="block text-sm text-ink-soft">
                  <Clock className="inline h-3.5 w-3.5 -mt-0.5 mr-1" aria-hidden />{t.durationMinutes} min
                  {t.mode === 'VIDEO' ? ', by video' : t.mode === 'IN_PERSON' ? ', at the clinic' : ', in person or video'}
                  {t.followUp && `, within ${t.followUpWindowDays} days of a visit`}
                </span>
              </span>
              <span className="ml-auto font-semibold whitespace-nowrap">
                {t.fee !== null ? money(t.fee) : doctor ? money(doctor.consultationFee) : "Doctor's fee"}
              </span>
            </Option>
          ))}
        </div>
      )}
    </section>
  )
}

// ---------- Step 3: patient ----------
function ChoosePatient({ onBack, onPick }: { onBack: () => void; onPick: (id: string) => void }) {
  const { user, ready } = useAuth()
  const loc = useLocation()
  const me = useQuery({ queryKey: ['me'], enabled: user?.role === 'PATIENT', queryFn: () => get<Patient>('/patients/me') })
  const deps = useQuery({ queryKey: ['dependents'], enabled: user?.role === 'PATIENT', queryFn: () => get<Patient[]>('/patients/me/dependents') })

  if (!ready) return <Spinner />
  if (!user) {
    return (
      <section>
        <StepHeader title="Log in to continue" sub="We need to know who the visit is for. Your choices so far are saved." onBack={onBack} />
        <div className="flex flex-wrap gap-3">
          <Link to={`/login?next=${encodeURIComponent(loc.pathname + loc.search)}`} className={buttonClass('primary')}>Log in</Link>
          <Link to="/register" className={buttonClass('secondary')}>Create an account</Link>
        </div>
      </section>
    )
  }
  if (user.role !== 'PATIENT') {
    return <Alert tone="info">Staff accounts book for patients from the reception desk view.</Alert>
  }
  if (me.isLoading) return <Spinner />
  const people = [...(me.data ? [{ ...me.data, label: 'Myself' }] : []), ...(deps.data ?? []).map((d) => ({ ...d, label: d.relationship ?? 'Family member' }))]

  return (
    <section>
      <StepHeader title="Who is this visit for?" onBack={onBack} />
      <div className="grid gap-2 sm:grid-cols-2">
        {people.map((p) => (
          <Option key={p.id} onClick={() => onPick(p.id)}>
            <Avatar name={p.fullName} />
            <span><span className="block font-semibold">{p.fullName}</span><span className="block text-sm text-ink-soft">{p.label}</span></span>
          </Option>
        ))}
      </div>
    </section>
  )
}

// ---------- Step 4: day and time ----------
function ChooseTime({ specialtyId, doctorId, typeId, patientId, onBack, onHeld }:
  { specialtyId: string; doctorId: string; typeId: string; patientId: string; onBack: () => void; onHeld: (h: Hold) => void }) {
  const { tz } = useClinic()
  const days = useMemo(() => Array.from({ length: 14 }, (_, i) => isoDate(tz, i)), [tz])
  const [date, setDate] = useState(days[0])
  const [mode, setMode] = useState<Mode | ''>('')
  const [picked, setPicked] = useState<Slot | null>(null)
  const type = useQuery({ queryKey: ['types', specialtyId], queryFn: () => get<AppointmentType[]>(`/appointment-types?specialtyId=${specialtyId}`) })
  const t = type.data?.find((x) => x.id === typeId)

  const slots = useQuery({
    queryKey: ['availability', doctorId, specialtyId, typeId, date, patientId, mode],
    queryFn: () => get<Slot[]>(`/availability${qs({
      doctorId: doctorId === ANY ? undefined : doctorId,
      specialtyId: doctorId === ANY ? specialtyId : undefined,
      typeId, date, mode: mode || undefined, patientId: t?.followUp ? patientId : undefined,
    })}`),
    enabled: !!t,
  })

  const holdM = useMutation({
    mutationFn: (s: Slot) => post<Hold>('/holds', { doctorId: s.doctorId, typeId, startAt: s.startAt, mode: s.mode, patientId }),
    onSuccess: onHeld,
    onError: () => { setPicked(null); slots.refetch() },
  })

  const groups = useMemo(() => {
    const g: Record<string, Slot[]> = { Morning: [], Afternoon: [], Evening: [] }
    for (const s of slots.data ?? []) {
      const h = Number(new Intl.DateTimeFormat('en-US', { timeZone: tz, hour: 'numeric', hourCycle: 'h23' }).format(new Date(s.startAt)))
      g[h < 12 ? 'Morning' : h < 17 ? 'Afternoon' : 'Evening'].push(s)
    }
    return Object.entries(g).filter(([, v]) => v.length)
  }, [slots.data, tz])

  return (
    <section>
      <StepHeader title="Choose a date and time" sub={`All times in ${tz.replace('_', ' ')}`} onBack={onBack} />
      {t?.followUp && <div className="mb-4"><Alert tone="info">Follow-up times only appear if this patient saw the same doctor in the last {t.followUpWindowDays} days.</Alert></div>}

      <div className="-mx-5 sm:-mx-7 px-5 sm:px-7 overflow-x-auto">
        <div className="flex gap-2 pb-2" role="radiogroup" aria-label="Day">
          {days.map((d) => {
            const [wd, md] = [dateLabel(d).split(', ')[0], dateLabel(d).split(', ')[1]]
            const sel = d === date
            return (
              <button key={d} role="radio" aria-checked={sel} onClick={() => { setDate(d); setPicked(null) }}
                className={`shrink-0 w-[4.5rem] rounded-xl border py-2.5 text-center transition-colors ${sel ? 'border-transparent bg-grad text-white shadow-glow' : 'border-line hover:border-brand/50'}`}>
                <span className={`block text-xs font-medium ${sel ? 'text-white/80' : 'text-ink-faint'}`}>{wd}</span>
                <span className="block font-semibold">{md}</span>
              </button>
            )
          })}
        </div>
      </div>

      {!t?.mode && (
        <div className="mt-4 inline-flex rounded-xl border border-line p-1 bg-canvas" role="radiogroup" aria-label="Visit mode">
          {([['', 'Any'], ['IN_PERSON', 'In person'], ['VIDEO', 'Video']] as const).map(([v, label]) => (
            <button key={v} role="radio" aria-checked={mode === v} onClick={() => { setMode(v); setPicked(null) }}
              className={`rounded-lg px-4 py-1.5 text-sm font-semibold ${mode === v ? 'bg-surface shadow-card text-ink' : 'text-ink-soft hover:text-ink'}`}>
              {label}
            </button>
          ))}
        </div>
      )}

      {holdM.error && <div className="mt-4"><Alert>{(holdM.error as ApiError).message}</Alert></div>}

      <div className="mt-6">
        {slots.isLoading ? <Spinner label="Finding open times" /> : groups.length === 0 ? (
          <Empty title={`No open times on ${dateLabel(date)}`} icon={<CalendarDays className="h-6 w-6" />}>Try another day. The clinic is closed on Sundays.</Empty>
        ) : (
          <div className="space-y-6">
            {groups.map(([label, list]) => (
              <div key={label}>
                <h3 className="mb-2 text-sm font-semibold text-ink-soft">{label}</h3>
                <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 gap-2" role="radiogroup" aria-label={`${label} times`}>
                  {list.map((s) => {
                    const sel = picked?.startAt === s.startAt && picked.doctorId === s.doctorId
                    return (
                      <button key={s.doctorId + s.startAt} role="radio" aria-checked={sel} onClick={() => setPicked(s)}
                        className={`rounded-lg border px-2 py-2 text-center transition-colors ${sel ? 'border-transparent bg-grad text-white shadow-glow' : 'border-line hover:border-brand hover:text-brand'}`}>
                        <span className="block font-semibold tabular-nums">{time(s.startAt, tz)}</span>
                        <span className={`flex items-center justify-center gap-1 text-xs ${sel ? 'text-white/80' : 'text-ink-faint'}`}>
                          {s.mode === 'VIDEO' ? <><Video className="h-3 w-3" />Video</> : 'In person'}
                        </span>
                        {doctorId === ANY && <span className={`block truncate text-xs ${sel ? 'text-white/90' : 'text-ink-soft'}`}>{s.doctorName}</span>}
                      </button>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {picked && (
        <div className="sticky bottom-0 -mx-5 sm:-mx-7 -mb-5 sm:-mb-7 mt-6 rounded-b-2xl border-t border-line bg-surface/90 backdrop-blur px-5 sm:px-7 py-4 flex flex-wrap items-center gap-4">
          <p className="mr-auto"><span className="text-ink-soft">Selected:</span> <strong>{day(picked.startAt, tz)}, {time(picked.startAt, tz)}</strong></p>
          <Button onClick={() => holdM.mutate(picked)} busy={holdM.isPending}>Continue</Button>
        </div>
      )}
    </section>
  )
}

// ---------- Step 5: intake + payment ----------
function Details({ hold, typeId, patientId, onBack, onBooked }:
  { hold: Hold; typeId: string; patientId: string; onBack: () => void; onBooked: (b: BookingResponse) => void }) {
  const { data: clinic } = useClinic()
  const doctor = useQuery({ queryKey: ['doctor', hold.doctorId], queryFn: () => get<Doctor>(`/doctors/${hold.doctorId}`) })
  const types = useQuery({
    queryKey: ['types', doctor.data?.specialtyId], enabled: !!doctor.data,
    queryFn: () => get<AppointmentType[]>(`/appointment-types?specialtyId=${doctor.data!.specialtyId}`),
  })
  const type = types.data?.find((t) => t.id === typeId)
  const fee = type?.fee ?? doctor.data?.consultationFee ?? 0
  const policy = clinic?.paymentPolicy ?? 'EITHER'
  const [payment, setPayment] = useState<PaymentMode>(policy === 'PAY_AT_CLINIC_ONLY' ? 'PAY_AT_CLINIC' : 'ONLINE')
  const { register, handleSubmit, formState: { errors } } = useForm<IntakeForm>({
    defaultValues: { reason: '', symptoms: '', durationOfSymptoms: '', currentMeds: '', allergiesConfirmed: false, extraNotes: '' },
  })

  const book = useMutation({
    mutationFn: (intake: IntakeForm) => post<BookingResponse>('/appointments', {
      holdId: hold.holdId, patientId, intakeForm: intake, paymentMode: fee > 0 ? payment : null,
    }),
    onSuccess: onBooked,
  })

  if (doctor.isLoading || types.isLoading) return <Spinner />
  const required = type?.intakeRequired ?? false

  const payOption = (value: PaymentMode, title: string, sub: string, icon: ReactNode) => (
    <label className={`flex cursor-pointer items-center gap-3 rounded-xl border p-4 transition-colors ${payment === value ? 'border-brand bg-brand-soft' : 'border-line hover:border-brand/50'}`}>
      <input type="radio" name="pay" className="h-4 w-4 accent-brand" checked={payment === value} onChange={() => setPayment(value)} />
      <span className="text-brand">{icon}</span>
      <span><span className="block font-semibold">{title}</span><span className="block text-sm text-ink-soft">{sub}</span></span>
    </label>
  )

  return (
    <form onSubmit={handleSubmit((f) => book.mutate(f))} noValidate>
      <StepHeader title="A few details" sub="Only your doctor sees these answers." onBack={onBack} />

      <div className="grid gap-5 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <Field label={`Reason for the visit${required ? '' : ' (optional)'}`} htmlFor="reason" error={errors.reason?.message}>
            <Input id="reason" placeholder="e.g. Persistent cough" {...register('reason', { required: required && 'Tell us briefly why you are coming in' })} />
          </Field>
        </div>
        <div className="sm:col-span-2">
          <Field label="Symptoms" htmlFor="symptoms"><Textarea id="symptoms" {...register('symptoms')} /></Field>
        </div>
        <Field label="How long have you had them?" htmlFor="duration"><Input id="duration" placeholder="e.g. 3 days" {...register('durationOfSymptoms')} /></Field>
        <Field label="Current medicines" htmlFor="meds"><Input id="meds" placeholder="Name and dose, or none" {...register('currentMeds')} /></Field>
        <div className="sm:col-span-2">
          <Field label="Anything else the doctor should know" htmlFor="notes"><Textarea id="notes" rows={2} {...register('extraNotes')} /></Field>
        </div>
        <div className="sm:col-span-2 space-y-1">
          <label className="flex items-start gap-3 rounded-xl bg-canvas p-4">
            <input type="checkbox" className="mt-1 h-4 w-4 accent-brand"
              {...register('allergiesConfirmed', { validate: (v) => v || 'Please confirm before booking' })} />
            <span className="text-sm">I confirm the allergies in my medical history are up to date.</span>
          </label>
          {errors.allergiesConfirmed && <p role="alert" className="text-sm font-medium text-danger">{errors.allergiesConfirmed.message}</p>}
        </div>
      </div>

      <div className="mt-8 border-t border-line pt-6 space-y-3">
        <h3 className="text-lg">Payment</h3>
        {fee <= 0 ? <p className="text-ink-soft">This visit is free.</p> : (
          <div className="grid gap-2 sm:grid-cols-2">
            {policy !== 'PAY_AT_CLINIC_ONLY' && payOption('ONLINE', `Pay ${money(fee)} now`, 'Card, secure checkout', <CreditCard className="h-5 w-5" />)}
            {policy !== 'ONLINE_ONLY' && payOption('PAY_AT_CLINIC', 'Pay at the clinic', 'At the front desk on the day', <Building2 className="h-5 w-5" />)}
          </div>
        )}
      </div>

      {book.error && <div className="mt-4"><Alert>{(book.error as ApiError).message}</Alert></div>}
      <div className="mt-6 flex justify-end">
        <Button type="submit" size="lg" busy={book.isPending}>Confirm booking</Button>
      </div>
    </form>
  )
}

// ---------- Done ----------
function Confirmation({ booking: b }: { booking: BookingResponse }) {
  const { tz } = useClinic()
  const pending = b.status === 'PENDING_PAYMENT'
  return (
    <section className="mx-auto max-w-xl space-y-6 text-center">
      <div className="mx-auto grid h-16 w-16 place-items-center rounded-full bg-success-soft text-success">
        <Check className="h-8 w-8" strokeWidth={3} aria-hidden />
      </div>
      <div className="space-y-2">
        <h1>{pending ? 'Almost done' : "You're booked"}</h1>
        <p className="text-ink-soft">A confirmation email with a calendar invite is on its way. We'll remind you 24 hours and 2 hours before.</p>
      </div>

      <div className="card text-left divide-y divide-line">
        <div className="p-5 flex items-center gap-4">
          <Avatar name={b.doctorName} size="lg" />
          <div>
            <p className="font-semibold text-lg">{b.doctorName}</p>
            <p className="text-ink-soft">{b.clinicName}</p>
          </div>
        </div>
        <dl className="p-5 grid gap-4 sm:grid-cols-2">
          <div><dt className="label">Date</dt><dd className="font-semibold">{day(b.startAt, tz)}</dd></div>
          <div><dt className="label">Time</dt><dd className="font-semibold">{time(b.startAt, tz)}</dd></div>
          <div><dt className="label">Location</dt><dd className="font-semibold">{b.videoVisit ? 'Video call (Join opens 10 min before)' : b.clinicAddress}</dd></div>
          <div><dt className="label">Payment</dt><dd className="font-semibold">
            {b.paymentMode === 'FREE' ? 'No charge' : b.paymentMode === 'PAY_AT_CLINIC' ? `${money(b.amount)} at the desk` : `${money(b.amount)} paid online`}</dd></div>
        </dl>
        <div className="px-5 py-3 flex justify-between text-sm text-ink-soft">
          <span>Reference</span><span className="font-semibold text-ink tabular-nums">{b.appointmentId.slice(0, 8).toUpperCase()}</span>
        </div>
      </div>

      {pending && <Alert tone="info">Your time is reserved while payment completes. We'll email you once it's confirmed.</Alert>}
      <div className="flex flex-wrap justify-center gap-3">
        <Link to="/me/appointments" className={buttonClass('primary')}>View my appointments</Link>
        {b.mapUrl && !b.videoVisit && <a href={b.mapUrl} target="_blank" rel="noreferrer" className={buttonClass('secondary')}><MapPin className="h-4 w-4" />Get directions</a>}
      </div>
    </section>
  )
}
