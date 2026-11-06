import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { get } from '../lib/api'
import type { Doctor, Page, Specialty } from '../lib/types'
import { useClinic } from '../lib/queries'
import { dateTime } from '../lib/format'
import { Spinner } from '../components/ui'

export default function Home() {
  const { tz } = useClinic()
  const specialties = useQuery({ queryKey: ['specialties'], queryFn: () => get<Specialty[]>('/specialties') })
  const doctors = useQuery({ queryKey: ['doctors', 'home'], queryFn: () => get<Page<Doctor>>('/doctors?size=6') })

  return (
    <div className="space-y-14">
      <section className="space-y-6 max-w-3xl">
        <h1 className="text-4xl sm:text-5xl">Who would you like to see?</h1>
        <p className="text-xl text-ink-soft max-w-2xl">
          Pick the kind of care you need and we'll show the next free times. Booking takes about two minutes.
        </p>
        {specialties.isLoading ? <Spinner /> : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {specialties.data?.map((s) => (
              <li key={s.id}>
                <Link to={`/book?specialtyId=${s.id}`}
                  className="no-underline block rounded-md border-2 border-ink bg-surface px-5 py-4 text-ink hover:bg-teal-tint">
                  <span className="block text-xl font-extrabold">{s.name}</span>
                  <span className="block text-ink-soft">{s.description}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-4">
        <div className="flex items-baseline justify-between gap-4">
          <h2>Our doctors</h2>
          <Link to="/doctors">See all doctors</Link>
        </div>
        {doctors.isLoading ? <Spinner /> : (
          <ul className="divide-y-2 divide-line border-y-2 border-line">
            {doctors.data?.content.map((d) => (
              <li key={d.id} className="py-4 flex flex-wrap items-center gap-x-6 gap-y-2">
                <div className="mr-auto">
                  <Link to={`/doctors/${d.id}`} className="text-xl font-bold no-underline text-ink hover:underline">{d.fullName}</Link>
                  <p className="text-ink-soft">{d.specialtyName}, {d.experienceYears} years</p>
                </div>
                <p className="text-ink-soft">
                  {d.nextAvailableSlot ? <>Next availability: <strong className="text-ink">{dateTime(d.nextAvailableSlot, tz)}</strong></> : 'No availability this week'}
                </p>
                <Link to={`/book?doctorId=${d.id}`} className="no-underline rounded-md border-2 border-teal px-4 py-2 font-bold text-teal hover:bg-teal-tint">
                  Book
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
