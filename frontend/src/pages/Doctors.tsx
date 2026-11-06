import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { get, qs } from '../lib/api'
import type { AppointmentType, Doctor, Page, Specialty } from '../lib/types'
import { useClinic } from '../lib/queries'
import { dateTime, money } from '../lib/format'
import { Empty, Field, Input, Select, Spinner } from '../components/ui'

export function DoctorList() {
  const { tz } = useClinic()
  const [specialtyId, setSpecialtyId] = useState('')
  const [q, setQ] = useState('')
  const [language, setLanguage] = useState('')
  const [gender, setGender] = useState('')
  const specialties = useQuery({ queryKey: ['specialties'], queryFn: () => get<Specialty[]>('/specialties') })
  const doctors = useQuery({
    queryKey: ['doctors', specialtyId, q, language, gender],
    queryFn: () => get<Page<Doctor>>(`/doctors${qs({ specialtyId, q, language, gender, size: 50 })}`),
  })

  return (
    <div className="space-y-8">
      <h1>Find a doctor</h1>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Name" htmlFor="q"><Input id="q" value={q} onChange={(e) => setQ(e.target.value)} placeholder="e.g. Mehta" /></Field>
        <Field label="Specialty" htmlFor="spec">
          <Select id="spec" value={specialtyId} onChange={(e) => setSpecialtyId(e.target.value)}>
            <option value="">All specialties</option>
            {specialties.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </Select>
        </Field>
        <Field label="Language" htmlFor="lang"><Input id="lang" value={language} onChange={(e) => setLanguage(e.target.value)} placeholder="e.g. Spanish" /></Field>
        <Field label="Doctor's gender" htmlFor="gender">
          <Select id="gender" value={gender} onChange={(e) => setGender(e.target.value)}>
            <option value="">No preference</option><option value="FEMALE">Female</option><option value="MALE">Male</option>
          </Select>
        </Field>
      </div>

      {doctors.isLoading ? <Spinner /> : doctors.data?.content.length === 0 ? (
        <Empty title="No doctors match those filters">Try a different language or clear the name.</Empty>
      ) : (
        <ul className="grid gap-4 md:grid-cols-2">
          {doctors.data?.content.map((d) => (
            <li key={d.id} className="rounded-md border-2 border-line bg-surface p-5 flex flex-col gap-3">
              <div>
                <Link to={`/doctors/${d.id}`} className="text-xl font-extrabold no-underline text-ink hover:underline">{d.fullName}</Link>
                <p className="text-ink-soft">{d.specialtyName}, {d.qualifications}</p>
              </div>
              <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
                <dt className="text-ink-soft">Experience</dt><dd>{d.experienceYears} years</dd>
                <dt className="text-ink-soft">Speaks</dt><dd>{d.languages.join(', ') || 'English'}</dd>
                <dt className="text-ink-soft">Visit fee</dt><dd>{money(d.consultationFee)}</dd>
                <dt className="text-ink-soft">Rating</dt><dd>{d.rating ? `${d.rating.toFixed(1)} of 5 (${d.reviewCount})` : 'No reviews yet'}</dd>
                <dt className="text-ink-soft">Next availability</dt><dd className="font-bold">{d.nextAvailableSlot ? dateTime(d.nextAvailableSlot, tz) : 'None this week'}</dd>
              </dl>
              <Link to={`/book?doctorId=${d.id}`} className="mt-auto self-start no-underline rounded-md bg-teal px-5 py-3 font-bold text-white hover:bg-teal-dark">
                Book a visit
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function DoctorDetail() {
  const { id } = useParams()
  const { tz } = useClinic()
  const doctor = useQuery({ queryKey: ['doctor', id], queryFn: () => get<Doctor>(`/doctors/${id}`) })
  const types = useQuery({
    queryKey: ['types', doctor.data?.specialtyId], enabled: !!doctor.data,
    queryFn: () => get<AppointmentType[]>(`/appointment-types?specialtyId=${doctor.data!.specialtyId}`),
  })
  const reviews = useQuery({
    queryKey: ['reviews', id],
    queryFn: () => get<Page<{ id: string; rating: number; comment: string | null; reviewerInitials: string; createdAt: string }>>(`/doctors/${id}/reviews`),
  })
  if (doctor.isLoading) return <Spinner />
  if (!doctor.data) return <Empty title="Doctor not found" />
  const d = doctor.data

  return (
    <div className="space-y-10 max-w-3xl">
      <section className="space-y-3">
        <p className="text-ink-soft font-bold">{d.specialtyName}</p>
        <h1>{d.fullName}</h1>
        <p className="text-xl">{d.qualifications}, {d.experienceYears} years in practice. Speaks {d.languages.join(', ') || 'English'}.</p>
        {d.bio && <p className="text-ink-soft">{d.bio}</p>}
        <p>Next availability: <strong>{d.nextAvailableSlot ? dateTime(d.nextAvailableSlot, tz) : 'nothing in the next 7 days'}</strong></p>
        <Link to={`/book?doctorId=${d.id}`} className="inline-block no-underline rounded-md bg-teal px-5 py-3 font-bold text-white hover:bg-teal-dark">Book a visit</Link>
      </section>

      <section className="space-y-3">
        <h2>Visit types</h2>
        <ul className="divide-y-2 divide-line border-y-2 border-line">
          {types.data?.map((t) => (
            <li key={t.id} className="py-3 flex flex-wrap justify-between gap-2">
              <span><strong>{t.name}</strong> <span className="text-ink-soft">({t.durationMinutes} min{t.mode === 'VIDEO' ? ', video' : ''})</span></span>
              <span>{money(t.fee ?? d.consultationFee)}{t.followUp && <span className="text-ink-soft"> within {t.followUpWindowDays} days of a visit</span>}</span>
            </li>
          ))}
        </ul>
      </section>

      <section className="space-y-3">
        <h2>Patient reviews</h2>
        {reviews.data?.content.length ? (
          <ul className="space-y-3">
            {reviews.data.content.map((r) => (
              <li key={r.id} className="border-l-4 border-teal pl-4">
                <p className="font-bold">{r.rating} of 5 from {r.reviewerInitials}</p>
                {r.comment && <p>{r.comment}</p>}
              </li>
            ))}
          </ul>
        ) : <p className="text-ink-soft">No reviews yet. Reviews appear after the clinic checks them for private details.</p>}
      </section>
    </div>
  )
}
