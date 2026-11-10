import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Award, CalendarClock, Languages, Search, Star, UserRoundSearch, Video } from 'lucide-react'
import { get, qs } from '../lib/api'
import type { AppointmentType, Doctor, Page, Specialty } from '../lib/types'
import { useClinic } from '../lib/queries'
import { dateTime, money } from '../lib/format'
import { Avatar, Badge, Empty, Input, Select, Spinner, buttonClass } from '../components/ui'

function Rating({ d }: { d: Doctor }) {
  return d.rating ? (
    <span className="flex items-center gap-1 text-sm"><Star className="h-4 w-4 fill-amber text-amber" aria-hidden />
      <strong>{d.rating.toFixed(1)}</strong><span className="text-ink-soft">({d.reviewCount})</span></span>
  ) : <span className="text-sm text-ink-faint">No reviews yet</span>
}

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
    <div className="space-y-6">
      <div className="space-y-1">
        <h1>Find a doctor</h1>
        <p className="text-ink-soft">{doctors.data ? `${doctors.data.page.totalElements} doctors` : 'Search our clinicians'} accepting new patients</p>
      </div>

      <div className="card p-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_1fr]">
        <div className="relative">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-ink-faint" aria-hidden />
          <label htmlFor="q" className="sr-only">Doctor name</label>
          <Input id="q" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search by name" className="pl-10" />
        </div>
        <div>
          <label htmlFor="spec" className="sr-only">Specialty</label>
          <Select id="spec" value={specialtyId} onChange={(e) => setSpecialtyId(e.target.value)}>
            <option value="">All specialties</option>
            {specialties.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </Select>
        </div>
        <div>
          <label htmlFor="lang" className="sr-only">Language</label>
          <Input id="lang" value={language} onChange={(e) => setLanguage(e.target.value)} placeholder="Language" />
        </div>
        <div>
          <label htmlFor="gender" className="sr-only">Doctor's gender</label>
          <Select id="gender" value={gender} onChange={(e) => setGender(e.target.value)}>
            <option value="">Any gender</option><option value="FEMALE">Female</option><option value="MALE">Male</option>
          </Select>
        </div>
      </div>

      {doctors.isLoading ? <Spinner /> : doctors.data?.content.length === 0 ? (
        <Empty title="No doctors match those filters" icon={<UserRoundSearch className="h-6 w-6" />}>Try a different language or clear the name.</Empty>
      ) : (
        <ul className="grid gap-4 md:grid-cols-2">
          {doctors.data?.content.map((d) => (
            <li key={d.id} className="card p-5 flex flex-col gap-4 hover:shadow-lift transition-shadow">
              <div className="flex gap-4">
                <Avatar name={d.fullName} size="lg" />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <Link to={`/doctors/${d.id}`} className="text-lg font-semibold text-ink no-underline hover:text-brand">{d.fullName}</Link>
                    <Rating d={d} />
                  </div>
                  <div className="mt-1 flex flex-wrap items-center gap-2">
                    <Badge tone="brand">{d.specialtyName}</Badge>
                    <span className="text-sm text-ink-soft">{d.qualifications}</span>
                  </div>
                </div>
              </div>
<<<<<<< HEAD

              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div className="flex items-center gap-2"><Award className="h-4 w-4 text-ink-faint" aria-hidden /><dt className="sr-only">Experience</dt><dd>{d.experienceYears} years experience</dd></div>
                <div className="flex items-center gap-2"><Languages className="h-4 w-4 text-ink-faint" aria-hidden /><dt className="sr-only">Speaks</dt><dd className="truncate">{d.languages.join(', ') || 'English'}</dd></div>
=======
              <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
                <dt className="text-ink-soft">Experience</dt><dd>{d.experienceYears} years</dd>
                <dt className="text-ink-soft">Speaks</dt><dd>{d.languages.join(', ') || 'English'}</dd>
                <dt className="text-ink-soft">Visit fee</dt><dd>{money(d.consultationFee)}</dd>
                <dt className="text-ink-soft">Rating</dt><dd>{d.rating ? `${d.rating.toFixed(1)} of 5 (${d.reviewCount})` : 'No reviews yet'}</dd>
                <dt className="text-ink-soft">Next availability</dt><dd className="font-bold">{d.nextAvailableSlot ? dateTime(d.nextAvailableSlot, tz) : 'None this week'}</dd>
>>>>>>> d699e7362f724185e29cf2290bfed6d6043c84bc
              </dl>

              <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-canvas px-4 py-3">
                <div>
                  <p className="label">Next availability</p>
                  <p className="font-semibold flex items-center gap-1.5">
                    <CalendarClock className="h-4 w-4 text-brand" aria-hidden />
                    {d.nextAvailableSlot ? dateTime(d.nextAvailableSlot, tz) : 'None this week'}
                  </p>
                </div>
                <div className="text-right">
                  <p className="label">OP fee</p>
                  <p className="font-semibold">{money(d.consultationFee)}</p>
                </div>
              </div>

              <div className="flex gap-2 mt-auto">
                <Link to={`/book?doctorId=${d.id}`} className={buttonClass('primary') + ' flex-1'}>Book a visit</Link>
                <Link to={`/doctors/${d.id}`} className={buttonClass('secondary')}>View profile</Link>
              </div>
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
    <div className="space-y-6">
      <Link to="/doctors" className="text-sm">All doctors</Link>
      <div className="grid gap-6 lg:grid-cols-[1fr_340px] items-start">
        <div className="space-y-6">
          <section className="card p-6 flex flex-col sm:flex-row gap-5">
            <Avatar name={d.fullName} size="lg" />
            <div className="space-y-2">
              <h1 className="text-3xl">{d.fullName}</h1>
              <div className="flex flex-wrap items-center gap-2"><Badge tone="brand">{d.specialtyName}</Badge><Rating d={d} /></div>
              <p className="text-ink-soft">{d.qualifications}, {d.experienceYears} years in practice. Speaks {d.languages.join(', ') || 'English'}.</p>
              {d.bio && <p>{d.bio}</p>}
            </div>
          </section>

          <section className="card">
            <h2 className="px-6 pt-5 pb-3">Visit types</h2>
            <ul className="divide-y divide-line">
              {types.data?.map((t) => (
                <li key={t.id} className="px-6 py-4 flex flex-wrap items-center justify-between gap-2">
                  <span>
                    <span className="font-semibold flex items-center gap-2">{t.name}{t.mode === 'VIDEO' && <Badge><Video className="h-3.5 w-3.5" />Video</Badge>}</span>
                    <span className="text-sm text-ink-soft">{t.durationMinutes} min{t.followUp ? `, within ${t.followUpWindowDays} days of a visit` : ''}</span>
                  </span>
                  <span className="font-semibold">{money(t.fee ?? d.consultationFee)}</span>
                </li>
              ))}
            </ul>
          </section>

          <section className="card p-6 space-y-4">
            <h2>Patient reviews</h2>
            {reviews.data?.content.length ? (
              <ul className="space-y-4">
                {reviews.data.content.map((r) => (
                  <li key={r.id} className="space-y-1">
                    <p className="flex items-center gap-1">
                      {Array.from({ length: 5 }, (_, i) => <Star key={i} className={`h-4 w-4 ${i < r.rating ? 'fill-amber text-amber' : 'text-line-strong'}`} aria-hidden />)}
                      <span className="sr-only">{r.rating} of 5</span>
                      <span className="ml-2 text-sm text-ink-soft">{r.reviewerInitials}</span>
                    </p>
                    {r.comment && <p>{r.comment}</p>}
                  </li>
                ))}
              </ul>
            ) : <p className="text-ink-soft">No reviews yet. Reviews appear after the clinic checks them for private details.</p>}
          </section>
        </div>

        <aside className="card p-6 space-y-4 lg:sticky lg:top-24">
          <p className="label">Next availability</p>
          <p className="text-xl font-semibold">{d.nextAvailableSlot ? dateTime(d.nextAvailableSlot, tz) : 'Nothing in the next 7 days'}</p>
          <div className="flex justify-between text-sm border-t border-line pt-4"><span className="text-ink-soft">OP fee</span><strong>{money(d.consultationFee)}</strong></div>
          <Link to={`/book?doctorId=${d.id}`} className={buttonClass('primary', 'lg') + ' w-full'}>Book a visit</Link>
        </aside>
      </div>
    </div>
  )
}
