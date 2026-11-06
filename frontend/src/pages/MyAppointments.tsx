import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, download, get, post } from '../lib/api'
import { useClinic } from '../lib/queries'
import { day, statusLabel, time } from '../lib/format'
import type { Appointment } from '../lib/types'
import { Alert, Button, Empty, Spinner } from '../components/ui'

export default function MyAppointments() {
  const [tab, setTab] = useState<'upcoming' | 'past'>('upcoming')
  const list = useQuery({ queryKey: ['my-appointments', tab], queryFn: () => get<Appointment[]>(`/appointments/me?status=${tab}`) })

  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1>My appointments</h1>
        <Link to="/book" className="no-underline rounded-md bg-teal px-5 py-3 font-bold text-white hover:bg-teal-dark">Book a visit</Link>
      </div>
      <div role="tablist" className="flex gap-2 border-b-2 border-line">
        {(['upcoming', 'past'] as const).map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
            className={`-mb-0.5 px-4 py-2 font-bold border-b-4 ${tab === t ? 'border-teal text-ink' : 'border-transparent text-ink-soft hover:text-ink'}`}>
            {t === 'upcoming' ? 'Upcoming' : 'Past'}
          </button>
        ))}
      </div>
      {list.isLoading ? <Spinner /> : list.data?.length === 0 ? (
        <Empty title={tab === 'upcoming' ? 'No upcoming visits' : 'No past visits yet'}>
          {tab === 'upcoming' && <Link to="/book">Book a visit</Link>}
        </Empty>
      ) : (
        <ul className="space-y-4">{list.data?.map((a) => <AppointmentRow key={a.id} a={a} />)}</ul>
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

  return (
    <li className={`rounded-md border-2 bg-surface p-5 space-y-3 ${muted ? 'border-line opacity-75' : 'border-ink'}`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="text-xl font-extrabold">{day(a.startAt, tz)}, {time(a.startAt, tz)}</p>
          <p>{a.typeName} with <strong>{a.doctorName}</strong>{a.patientName ? ` for ${a.patientName}` : ''}</p>
          <p className="text-ink-soft">{a.mode === 'VIDEO' ? 'Video call' : a.roomName ? `At the clinic, ${a.roomName}` : 'At the clinic'}</p>
        </div>
        <span className={`rounded-full px-3 py-1 font-bold ${a.status === 'CONFIRMED' ? 'bg-teal-tint text-teal-dark' : a.status === 'CANCELLED' ? 'bg-alert-tint text-alert' : 'bg-paper text-ink'}`}>
          {statusLabel[a.status]}
        </span>
      </div>
      {a.cancelReason && <p className="text-ink-soft">Reason: {a.cancelReason}</p>}

      <div className="flex flex-wrap gap-3">
        {a.hasVisitSummary && <Link to={`/me/visits/${a.id}`} className="no-underline rounded-md bg-teal px-4 py-2 font-bold text-white">View visit summary</Link>}
        {a.mode === 'VIDEO' && canCancel && <Button variant="secondary" onClick={() => join.mutate()} busy={join.isPending}>Join video call</Button>}
        {a.paymentStatus === 'SUCCEEDED' && (
          <Button variant="ghost" onClick={() => download(`/appointments/${a.id}/receipt.pdf`, `receipt-${a.id.slice(0, 8)}.pdf`)}>Download receipt</Button>
        )}
        {canCancel && !confirming && <Button variant="danger" onClick={() => setConfirming(true)}>Cancel visit</Button>}
      </div>

      {confirming && (
        <div className="rounded-md border-2 border-alert bg-alert-tint p-4 space-y-3">
          <p>Cancel this visit? Cancelling less than 4 hours before means the fee is not refunded.</p>
          <div className="flex gap-3">
            <Button variant="danger" onClick={() => cancel.mutate()} busy={cancel.isPending}>Yes, cancel it</Button>
            <Button variant="secondary" onClick={() => setConfirming(false)}>Keep it</Button>
          </div>
        </div>
      )}
      {(cancel.error || join.error) && <Alert>{((cancel.error || join.error) as ApiError).message}</Alert>}
    </li>
  )
}
