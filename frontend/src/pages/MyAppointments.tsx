import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarPlus, CalendarX2, Download, FileText, MapPin, Video } from 'lucide-react'
import { ApiError, download, get, post } from '../lib/api'
import { useClinic } from '../lib/queries'
import { statusLabel, time } from '../lib/format'
import type { Appointment } from '../lib/types'
import { Alert, Avatar, Badge, Button, Empty, Spinner, buttonClass } from '../components/ui'

const tone = (s: Appointment['status']) =>
  s === 'CONFIRMED' || s === 'CHECKED_IN' ? 'success' : s === 'CANCELLED' || s === 'NO_SHOW' ? 'danger'
    : s === 'PENDING_PAYMENT' ? 'amber' : s === 'COMPLETED' ? 'brand' : 'neutral'

export default function MyAppointments() {
  const [tab, setTab] = useState<'upcoming' | 'past'>('upcoming')
  const list = useQuery({ queryKey: ['my-appointments', tab], queryFn: () => get<Appointment[]>(`/appointments/me?status=${tab}`) })

  return (
    <div className="max-w-4xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-1">
          <h1>My appointments</h1>
          <p className="text-ink-soft">Visits for you and your family.</p>
        </div>
        <Link to="/book" className={buttonClass('primary')}><CalendarPlus className="h-4 w-4" />Book a visit</Link>
      </div>

      <div role="tablist" className="inline-flex rounded-xl border border-line bg-surface p-1">
        {(['upcoming', 'past'] as const).map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
            className={`rounded-lg px-4 py-1.5 text-sm font-semibold ${tab === t ? 'bg-grad text-white' : 'text-ink-soft hover:text-ink'}`}>
            {t === 'upcoming' ? 'Upcoming' : 'Past'}
          </button>
        ))}
      </div>

      {list.isLoading ? <Spinner /> : list.data?.length === 0 ? (
        <Empty title={tab === 'upcoming' ? 'No upcoming visits' : 'No past visits yet'} icon={<CalendarX2 className="h-6 w-6" />}>
          {tab === 'upcoming' && <Link to="/book">Book your next visit</Link>}
        </Empty>
      ) : (
        <ul className="space-y-3">{list.data?.map((a) => <AppointmentRow key={a.id} a={a} />)}</ul>
      )}
    </div>
  )
}

function AppointmentRow({ a }: { a: Appointment }) {
  const { tz } = useClinic()
  const qc = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const cancel = useMutation({
    mutationFn: () => post(`/appointments/${a.id}/cancel`, { reason: 'Cancelled by patient online' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['my-appointments'] }),
  })
  const join = useMutation({ mutationFn: () => get<{ url: string }>(`/appointments/${a.id}/video-link`), onSuccess: (r) => window.open(r.url, '_blank') })
  const canCancel = a.status === 'CONFIRMED' || a.status === 'PENDING_PAYMENT'
  const muted = ['CANCELLED', 'EXPIRED', 'NO_SHOW'].includes(a.status)
  const d = new Date(a.startAt)
  const part = (o: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat('en-US', { timeZone: tz, ...o }).format(d)

  return (
    <li className={`card overflow-hidden ${muted ? 'opacity-70' : ''}`}>
      <div className="flex flex-col sm:flex-row">
        <div className="flex sm:flex-col items-center justify-center gap-2 sm:gap-0 bg-brand-soft text-brand-dark px-5 py-3 sm:w-28">
          <span className="text-sm font-semibold">{part({ month: 'short' })}</span>
          <span className="text-3xl font-bold leading-none">{part({ day: 'numeric' })}</span>
          <span className="text-sm">{part({ weekday: 'short' })}</span>
        </div>

        <div className="flex-1 p-5 space-y-3">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="flex items-center gap-3">
              <Avatar name={a.doctorName ?? '?'} size="sm" />
              <div>
                <p className="font-semibold">{a.doctorName}</p>
                <p className="text-sm text-ink-soft">{a.typeName}{a.patientName ? `, for ${a.patientName}` : ''}</p>
              </div>
            </div>
            <Badge tone={tone(a.status)}>{statusLabel[a.status]}</Badge>
          </div>

          <div className="flex flex-wrap gap-x-5 gap-y-1 text-sm text-ink-soft">
            <span className="font-semibold text-ink">{time(a.startAt, tz)}</span>
            <span className="flex items-center gap-1.5">
              {a.mode === 'VIDEO' ? <><Video className="h-4 w-4" />Video call</> : <><MapPin className="h-4 w-4" />{a.roomName ?? 'At the clinic'}</>}
            </span>
            {a.tokenNumber && <span>Token {a.tokenNumber}</span>}
          </div>
          {a.cancelReason && <p className="text-sm text-ink-soft">Reason: {a.cancelReason}</p>}

          <div className="flex flex-wrap gap-2 pt-1">
            {a.hasVisitSummary && <Link to={`/me/visits/${a.id}`} className={buttonClass('primary', 'sm')}><FileText className="h-4 w-4" />Visit summary</Link>}
            {a.mode === 'VIDEO' && canCancel && <Button size="sm" variant="secondary" onClick={() => join.mutate()} busy={join.isPending}><Video className="h-4 w-4" />Join call</Button>}
            {a.paymentStatus === 'SUCCEEDED' && (
              <Button size="sm" variant="ghost" onClick={() => download(`/appointments/${a.id}/receipt.pdf`, `receipt-${a.id.slice(0, 8)}.pdf`)}>
                <Download className="h-4 w-4" />Receipt
              </Button>
            )}
            {canCancel && !confirming && <Button size="sm" variant="danger" onClick={() => setConfirming(true)}>Cancel</Button>}
          </div>

          {confirming && (
            <div className="rounded-xl border border-danger/20 bg-danger-soft p-4 space-y-3 text-sm">
              <p>Cancel this visit? Cancelling less than 4 hours before means the fee is not refunded.</p>
              <div className="flex gap-2">
                <Button size="sm" variant="danger" onClick={() => cancel.mutate()} busy={cancel.isPending}>Yes, cancel</Button>
                <Button size="sm" variant="secondary" onClick={() => setConfirming(false)}>Keep it</Button>
              </div>
            </div>
          )}
          {(cancel.error || join.error) && <Alert>{((cancel.error || join.error) as ApiError).message}</Alert>}
        </div>
      </div>
    </li>
  )
}
