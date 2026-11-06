import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { ApiError, del, get, post, qs } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useClinic } from '../lib/queries'
import { dateLabel, dateTime, day, isoDate, money, time } from '../lib/format'
import type {
  AppointmentType, BookingResponse, Doctor, Hold, IntakeForm, Mode, Page, Patient, PaymentMode, Slot, Specialty,
} from '../lib/types'
import { Alert, Button, Empty, Field, Input, Spinner, Textarea } from '../components/ui'

/*
 * Booking wizard. Choices that survive a login redirect (specialty, doctor, type, patient) live in
 * the URL; the slot hold and the form are kept in memory.
 */
const STEPS = ['Doctor', 'Visit type', 'Who it is for', 'Day and time', 'Details', 'Confirm'] as const
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

  return (
    <div className="max-w-3xl space-y-8">
      <h1>Book a visit</h1>
      <Steps current={step} />
      {hold && !booking && <HoldTimer hold={hold} onExpired={() => setHold(null)} />}

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
      {step === 5 && booking && <Confirmation booking={booking} />}
    </div>
  )
}

function Steps({ current }: { current: number }) {
  return (
    <ol className="flex flex-wrap gap-x-5 gap-y-2" aria-label="Booking steps">
      {STEPS.map((s, i) => (
        <li key={s} aria-current={i === current ? 'step' : undefined}
          className={`flex items-center gap-2 ${i === current ? 'font-extrabold text-ink' : i < current ? 'text-teal' : 'text-ink-soft'}`}>
          <span className={`grid h-7 w-7 place-items-center rounded-full border-2 text-sm font-bold
            ${i === current ? 'border-ink bg-card' : i < current ? 'border-teal bg-teal text-white' : 'border-line'}`}>
            {i < current ? '✓' : i + 1}
          </span>
          {s}
        </li>
      ))}
    </ol>
  )
}

function Back({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="font-bold text-teal underline underline-offset-4 decoration-2 py-2">
      Back
    </button>
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
    <section className="space-y-6">
      <div className="space-y-3">
        <h2>What kind of care do you need?</h2>
        <div className="flex flex-wrap gap-2">
          {specialties.data?.map((s) => (
            <button key={s.id} onClick={() => onSpecialty(s.id)} aria-pressed={s.id === specialtyId}
              className={`rounded-md border-2 px-4 py-2 font-bold ${s.id === specialtyId ? 'border-ink bg-ink text-paper' : 'border-line bg-surface hover:border-ink'}`}>
              {s.name}
            </button>
          ))}
        </div>
      </div>
      {specialtyId && (
        <div className="space-y-3">
          <h2>Choose a doctor</h2>
          {doctors.isLoading ? <Spinner /> : (
            <ul className="space-y-3">
              <li>
                <button onClick={() => onDoctor(ANY)} className="w-full text-left rounded-md border-2 border-teal bg-teal-tint px-5 py-4 hover:border-ink">
                  <span className="block text-lg font-extrabold">First available doctor</span>
                  <span className="text-ink-soft">See every open time in this specialty</span>
                </button>
              </li>
              {doctors.data?.content.map((d) => (
                <li key={d.id}>
                  <button onClick={() => onDoctor(d.id)} className="w-full text-left rounded-md border-2 border-line bg-surface px-5 py-4 hover:border-ink">
                    <span className="flex flex-wrap justify-between gap-2">
                      <span className="text-lg font-extrabold">{d.fullName}</span>
                      <span>{money(d.consultationFee)}</span>
                    </span>
                    <span className="block text-ink-soft">
                      {d.experienceYears} years, speaks {d.languages.join(', ') || 'English'}.{' '}
                      {d.nextAvailableSlot ? `Next availability ${dateTime(d.nextAvailableSlot, tz)}` : 'No availability this week'}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
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
    <section className="space-y-4">
      <Back onClick={onBack} />
      <h2>What kind of visit{doctor ? ` with ${doctor.fullName}` : ''}?</h2>
      {types.isLoading ? <Spinner /> : (
        <ul className="space-y-3">
          {types.data?.map((t) => (
            <li key={t.id}>
              <button onClick={() => onPick(t.id)} className="w-full text-left rounded-md border-2 border-line bg-surface px-5 py-4 hover:border-ink">
                <span className="flex flex-wrap justify-between gap-2">
                  <span className="text-lg font-extrabold">{t.name}</span>
                  <span className="font-bold">{t.fee !== null ? money(t.fee) : doctor ? money(doctor.consultationFee) : "Doctor's fee"}</span>
                </span>
                <span className="block text-ink-soft">
                  {t.durationMinutes} minutes{t.mode === 'VIDEO' ? ', by video' : t.mode === 'IN_PERSON' ? ', at the clinic' : ''}.
                  {t.followUp && ` Only within ${t.followUpWindowDays} days of a visit with the same doctor.`}
                </span>
              </button>
            </li>
          ))}
        </ul>
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
      <section className="space-y-4">
        <Back onClick={onBack} />
        <h2>Log in to continue</h2>
        <p>We need to know who the visit is for. Your choices so far are saved.</p>
        <div className="flex gap-3">
          <Link to={`/login?next=${encodeURIComponent(loc.pathname + loc.search)}`} className="no-underline rounded-md bg-teal px-5 py-3 font-bold text-white">Log in</Link>
          <Link to="/register" className="no-underline rounded-md border-2 border-ink px-5 py-3 font-bold text-ink">Create an account</Link>
        </div>
      </section>
    )
  }
  if (user.role !== 'PATIENT') {
    return <Alert tone="info">Staff accounts book for patients from the reception desk view.</Alert>
  }
  if (me.isLoading) return <Spinner />
  const people = [...(me.data ? [{ ...me.data, label: 'Me' }] : []), ...(deps.data ?? []).map((d) => ({ ...d, label: d.relationship ?? 'Family member' }))]

  return (
    <section className="space-y-4">
      <Back onClick={onBack} />
      <h2>Who is this visit for?</h2>
      <ul className="grid gap-3 sm:grid-cols-2">
        {people.map((p) => (
          <li key={p.id}>
            <button onClick={() => onPick(p.id)} className="w-full text-left rounded-md border-2 border-line bg-surface px-5 py-4 hover:border-ink">
              <span className="block text-lg font-extrabold">{p.fullName}</span>
              <span className="text-ink-soft">{p.label}</span>
            </button>
          </li>
        ))}
      </ul>
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
    <section className="space-y-6">
      <Back onClick={onBack} />
      <h2>Pick a day and time</h2>
      {t?.followUp && <Alert tone="info">Follow-up times only appear if this patient saw the same doctor in the last {t.followUpWindowDays} days.</Alert>}

      <div className="-mx-4 px-4 overflow-x-auto">
        <div className="flex gap-2 pb-2" role="radiogroup" aria-label="Day">
          {days.map((d) => (
            <button key={d} role="radio" aria-checked={d === date} onClick={() => { setDate(d); setPicked(null) }}
              className={`shrink-0 rounded-md border-2 px-3 py-2 font-bold ${d === date ? 'border-ink bg-ink text-paper' : 'border-line bg-surface hover:border-ink'}`}>
              {dateLabel(d)}
            </button>
          ))}
        </div>
      </div>

      {!t?.mode && (
        <fieldset className="flex flex-wrap gap-4">
          <legend className="font-bold mb-2">How would you like to be seen?</legend>
          {([['', 'Either'], ['IN_PERSON', 'At the clinic'], ['VIDEO', 'Video call']] as const).map(([v, label]) => (
            <label key={v} className="flex items-center gap-2">
              <input type="radio" name="mode" className="h-5 w-5 accent-teal" checked={mode === v} onChange={() => { setMode(v); setPicked(null) }} />
              {label}
            </label>
          ))}
        </fieldset>
      )}

      {holdM.error && <Alert>{(holdM.error as ApiError).message}</Alert>}

      {slots.isLoading ? <Spinner label="Finding open times" /> : groups.length === 0 ? (
        <Empty title={`No open times on ${dateLabel(date)}`}>Try another day. The clinic is closed on Sundays.</Empty>
      ) : (
        <div className="space-y-5">
          {groups.map(([label, list]) => (
            <div key={label} className="space-y-2">
              <h3>{label}</h3>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3" role="radiogroup" aria-label={`${label} times`}>
                {list.map((s) => {
                  const sel = picked?.startAt === s.startAt && picked.doctorId === s.doctorId
                  return (
                    <button key={s.doctorId + s.startAt} role="radio" aria-checked={sel} onClick={() => setPicked(s)}
                      className={`ticket px-3 py-3 text-center ${sel ? 'ticket-selected' : 'hover:border-ink'}`}>
                      <span className="block text-lg font-extrabold">{time(s.startAt, tz)}</span>
                      <span className="block text-sm text-ink-soft">{s.mode === 'VIDEO' ? 'Video' : 'In person'}</span>
                      {doctorId === ANY && <span className="block text-sm">{s.doctorName}</span>}
                    </button>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      )}

      {picked && (
        <div className="sticky bottom-0 -mx-4 border-t-2 border-ink bg-surface px-4 py-4 flex flex-wrap items-center gap-4">
          <p className="mr-auto"><strong>{day(picked.startAt, tz)}</strong> at <strong>{time(picked.startAt, tz)}</strong></p>
          <Button onClick={() => holdM.mutate(picked)} busy={holdM.isPending}>Hold this time</Button>
        </div>
      )}
    </section>
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
    <p className="rounded-md bg-card-tint border-2 border-card px-4 py-2" role="timer" aria-live="off">
      We're holding this time for you for <strong>{Math.floor(s / 60)}:{String(s % 60).padStart(2, '0')}</strong>.
    </p>
  )
}

// ---------- Step 5: intake + payment ----------
function Details({ hold, typeId, patientId, onBack, onBooked }:
  { hold: Hold; typeId: string; patientId: string; onBack: () => void; onBooked: (b: BookingResponse) => void }) {
  const { tz, data: clinic } = useClinic()
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

  return (
    <form onSubmit={handleSubmit((f) => book.mutate(f))} className="space-y-6" noValidate>
      <Back onClick={onBack} />
      <div className="ticket ticket-selected px-6 py-4">
        <p className="text-lg"><strong>{type?.name}</strong> with <strong>{doctor.data?.fullName}</strong></p>
        <p>{day(hold.startAt, tz)} at {time(hold.startAt, tz)}, {hold.mode === 'VIDEO' ? 'by video' : 'at the clinic'}</p>
      </div>

      <fieldset className="space-y-5">
        <legend className="text-2xl font-bold mb-1">About your visit</legend>
        <p className="text-ink-soft">Only your doctor sees these answers.{required ? '' : ' All optional for this visit type.'}</p>
        <Field label="Reason for the visit" htmlFor="reason" error={errors.reason?.message}>
          <Input id="reason" {...register('reason', { required: required && 'Tell us briefly why you are coming in' })} />
        </Field>
        <Field label="Symptoms" htmlFor="symptoms"><Textarea id="symptoms" {...register('symptoms')} /></Field>
        <Field label="How long have you had them?" htmlFor="duration"><Input id="duration" placeholder="e.g. 3 days" {...register('durationOfSymptoms')} /></Field>
        <Field label="Medicines you take now" htmlFor="meds"><Textarea id="meds" placeholder="Name and dose, or 'none'" {...register('currentMeds')} /></Field>
        <Field label="Anything else the doctor should know" htmlFor="notes"><Textarea id="notes" {...register('extraNotes')} /></Field>
        <div className="space-y-1">
          <label className="flex items-start gap-3">
            <input type="checkbox" className="mt-1 h-6 w-6 accent-teal"
              {...register('allergiesConfirmed', { validate: (v) => v || 'Please confirm before booking' })} />
            <span>I have checked that my allergies in my medical history are up to date.</span>
          </label>
          {errors.allergiesConfirmed && <p role="alert" className="text-alert font-bold">{errors.allergiesConfirmed.message}</p>}
        </div>
      </fieldset>

      <fieldset className="space-y-3">
        <legend className="text-2xl font-bold mb-1">Payment</legend>
        {fee <= 0 ? <p>This visit is free.</p> : (
          <>
            <p>Visit fee: <strong>{money(fee)}</strong></p>
            {policy !== 'PAY_AT_CLINIC_ONLY' && (
              <label className="flex items-center gap-3">
                <input type="radio" name="pay" className="h-5 w-5 accent-teal" checked={payment === 'ONLINE'} onChange={() => setPayment('ONLINE')} />
                Pay online now
              </label>
            )}
            {policy !== 'ONLINE_ONLY' && (
              <label className="flex items-center gap-3">
                <input type="radio" name="pay" className="h-5 w-5 accent-teal" checked={payment === 'PAY_AT_CLINIC'} onChange={() => setPayment('PAY_AT_CLINIC')} />
                Pay at the front desk on the day
              </label>
            )}
          </>
        )}
      </fieldset>

      {book.error && <Alert>{(book.error as ApiError).message}</Alert>}
      <Button type="submit" busy={book.isPending}>Confirm booking</Button>
    </form>
  )
}

// ---------- Step 6: the appointment card ----------
function Confirmation({ booking: b }: { booking: BookingResponse }) {
  const { tz } = useClinic()
  const pending = b.status === 'PENDING_PAYMENT'
  return (
    <section className="space-y-6">
      <div className="ticket ticket-selected px-6 py-6 sm:px-10 space-y-4 max-w-xl">
        <p className="font-bold">{b.clinicName}</p>
        <div>
          <p className="text-3xl sm:text-4xl font-extrabold leading-tight">{day(b.startAt, tz)}</p>
          <p className="text-3xl sm:text-4xl font-extrabold leading-tight">{time(b.startAt, tz)}</p>
        </div>
        <p className="text-lg">with <strong>{b.doctorName}</strong></p>
        <div className="border-t-2 border-dashed border-ink pt-4 space-y-1">
          <p>{b.videoVisit ? 'Video call. The Join button opens 10 minutes before.' : b.clinicAddress}</p>
          <p>{b.paymentMode === 'FREE' ? 'No charge' : b.paymentMode === 'PAY_AT_CLINIC' ? `Pay ${money(b.amount)} at the desk` : `${money(b.amount)} paid online`}</p>
          <p className="text-sm">Ref {b.appointmentId.slice(0, 8).toUpperCase()}</p>
        </div>
      </div>
      {pending && <Alert tone="info">Your time is reserved while payment completes. We'll email you once it's confirmed.</Alert>}
      <p>A confirmation email with a calendar invite is on its way. We'll remind you 24 hours and 2 hours before.</p>
      <div className="flex flex-wrap gap-3">
        {b.mapUrl && !b.videoVisit && <a href={b.mapUrl} target="_blank" rel="noreferrer" className="no-underline rounded-md border-2 border-ink px-5 py-3 font-bold text-ink">Open map</a>}
        <Link to="/me/appointments" className="no-underline rounded-md bg-teal px-5 py-3 font-bold text-white">See my appointments</Link>
      </div>
    </section>
  )
}
