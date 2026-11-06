import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ApiError, download, get } from '../lib/api'
import { useClinic } from '../lib/queries'
import { dateTime } from '../lib/format'
import type { VisitSummary as VS } from '../lib/types'
import { Alert, Button, Empty, Spinner } from '../components/ui'

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  if (!children) return null
  return <section className="space-y-1"><h2 className="text-xl">{title}</h2><div className="whitespace-pre-line">{children}</div></section>
}

export default function VisitSummary() {
  const { id } = useParams()
  const { tz } = useClinic()
  const [dlError, setDlError] = useState<string | null>(null)
  const q = useQuery({ queryKey: ['visit', id], queryFn: () => get<VS>(`/appointments/${id}/visit-summary`), retry: false })

  if (q.isLoading) return <Spinner />
  if (q.error) {
    const e = q.error as ApiError
    return <Empty title={e.status === 404 ? 'No summary yet' : 'Summary unavailable'}>
      {e.status === 404 ? 'The doctor has not finalized this visit yet.' : e.message} <Link to="/me/appointments">Back to my appointments</Link>
    </Empty>
  }
  const v = q.data!
  const n = v.note
  const vit = n.vitals
  const vitals = vit && [
    vit.systolic && vit.diastolic && `Blood pressure ${vit.systolic}/${vit.diastolic}`,
    vit.pulse && `Pulse ${vit.pulse}`, vit.temperatureC && `Temperature ${vit.temperatureC} °C`,
    vit.weightKg && `Weight ${vit.weightKg} kg`, vit.spo2 && `Oxygen ${vit.spo2}%`,
  ].filter(Boolean).join('. ')

  const dl = (kind: 'summary' | 'prescription') =>
    download(`/appointments/${id}/${kind}.pdf`, `${kind}-${id!.slice(0, 8)}.pdf`).catch((e) => setDlError(e.message))

  return (
    <article className="max-w-2xl space-y-8">
      <header className="space-y-2">
        <Link to="/me/appointments">My appointments</Link>
        <h1>Visit summary</h1>
        <p className="text-lg">{v.typeName} with <strong>{v.doctorName}</strong>, {dateTime(v.visitDate, tz)}</p>
        <div className="flex flex-wrap gap-3 pt-2">
          <Button variant="secondary" onClick={() => dl('summary')}>Download summary (PDF)</Button>
          {n.prescription.length > 0 && <Button onClick={() => dl('prescription')}>Download prescription (PDF)</Button>}
        </div>
        {dlError && <Alert>{dlError}</Alert>}
      </header>

      <Section title="Why you came in">{n.chiefComplaint}</Section>
      <Section title="Diagnosis">{n.diagnosis}{n.icdCode ? ` (${n.icdCode})` : ''}</Section>
      <Section title="Measurements">{vitals}</Section>
      <Section title="What the doctor found">{n.findings}</Section>

      {n.prescription.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-xl">Prescription</h2>
          <ul className="space-y-3">
            {n.prescription.map((r, i) => (
              <li key={i} className="border-l-4 border-teal pl-4">
                <p className="font-bold text-lg">{r.medicine}</p>
                <p>{[r.dosage, r.frequency, r.durationDays && `for ${r.durationDays} days`].filter(Boolean).join(', ')}</p>
                {r.instructions && <p className="text-ink-soft">{r.instructions}</p>}
              </li>
            ))}
          </ul>
        </section>
      )}

      <Section title="Advice">{n.advice}</Section>
      <Section title="Recommended tests">{n.recommendedTests}</Section>
      {n.followUpInDays && (
        <section className="rounded-md bg-card-tint border-2 border-card p-4 space-y-2">
          <p><strong>Your doctor recommends a follow-up in {n.followUpInDays} days.</strong></p>
          <Link to="/book">Book a follow-up</Link>
        </section>
      )}
      {n.addendums.length > 0 && (
        <section className="space-y-2">
          <h2 className="text-xl">Later notes from your doctor</h2>
          {n.addendums.map((a) => <p key={a.id} className="border-l-4 border-line pl-4"><span className="text-ink-soft">{dateTime(a.createdAt, tz)}</span><br />{a.text}</p>)}
        </section>
      )}
    </article>
  )
}
