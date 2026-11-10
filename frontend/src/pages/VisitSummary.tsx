import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, CalendarPlus, Download, FileText, Pill } from 'lucide-react'
import { ApiError, download, get } from '../lib/api'
import { useClinic } from '../lib/queries'
import { dateTime } from '../lib/format'
import type { VisitSummary as VS } from '../lib/types'
import { Alert, Avatar, Button, Empty, Spinner, buttonClass } from '../components/ui'

function Row({ title, children }: { title: string; children: ReactNode }) {
  if (!children) return null
  return (
    <div className="grid gap-1 sm:grid-cols-[200px_1fr] px-6 py-4">
      <dt className="text-sm font-semibold text-ink-soft">{title}</dt>
      <dd className="whitespace-pre-line">{children}</dd>
    </div>
  )
}

export default function VisitSummary() {
  const { id } = useParams()
  const { tz } = useClinic()
  const [dlError, setDlError] = useState<string | null>(null)
  const q = useQuery({ queryKey: ['visit', id], queryFn: () => get<VS>(`/appointments/${id}/visit-summary`), retry: false })

  if (q.isLoading) return <Spinner />
  if (q.error) {
    const e = q.error as ApiError
    return <Empty title={e.status === 404 ? 'No summary yet' : 'Summary unavailable'} icon={<FileText className="h-6 w-6" />}>
      {e.status === 404 ? 'The doctor has not finalized this visit yet. ' : `${e.message} `}<Link to="/me/appointments">Back to my appointments</Link>
    </Empty>
  }
  const v = q.data!
  const n = v.note
  const vit = n.vitals
  const vitals: [string, string][] = vit ? ([
    ['Blood pressure', vit.systolic && vit.diastolic ? `${vit.systolic}/${vit.diastolic}` : ''],
    ['Pulse', vit.pulse ? `${vit.pulse} bpm` : ''], ['Temperature', vit.temperatureC ? `${vit.temperatureC} °C` : ''],
    ['Weight', vit.weightKg ? `${vit.weightKg} kg` : ''], ['Oxygen', vit.spo2 ? `${vit.spo2}%` : ''],
  ] as [string, string][]).filter(([, x]) => x) : []

  const dl = (kind: 'summary' | 'prescription') =>
    download(`/appointments/${id}/${kind}.pdf`, `${kind}-${id!.slice(0, 8)}.pdf`).catch((e) => setDlError(e.message))

  return (
    <article className="max-w-4xl space-y-6">
      <Link to="/me/appointments" className="inline-flex items-center gap-1.5 text-sm"><ArrowLeft className="h-4 w-4" />My appointments</Link>

      <header className="card p-6 flex flex-col sm:flex-row sm:items-center gap-5">
        <Avatar name={v.doctorName ?? '?'} size="lg" />
        <div className="flex-1">
          <h1 className="text-2xl sm:text-3xl">Visit summary</h1>
          <p className="text-ink-soft">{v.typeName} with <strong className="text-ink">{v.doctorName}</strong>, {dateTime(v.visitDate, tz)}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => dl('summary')}><Download className="h-4 w-4" />Summary PDF</Button>
          {n.prescription.length > 0 && <Button onClick={() => dl('prescription')}><Download className="h-4 w-4" />Prescription</Button>}
        </div>
      </header>
      {dlError && <Alert>{dlError}</Alert>}

      <div className="grid gap-6 lg:grid-cols-[1fr_300px] items-start">
        <div className="space-y-6">
          <section className="card">
            <h2 className="px-6 pt-5 pb-2">Consultation</h2>
            <dl className="divide-y divide-line">
              <Row title="Reason for visit">{n.chiefComplaint}</Row>
              <Row title="Diagnosis">{n.diagnosis && <>{n.diagnosis}{n.icdCode && <span className="ml-2 text-sm text-ink-faint">ICD-10 {n.icdCode}</span>}</>}</Row>
              <Row title="Examination">{n.findings}</Row>
              <Row title="Advice">{n.advice}</Row>
              <Row title="Recommended tests">{n.recommendedTests}</Row>
            </dl>
          </section>

          {n.prescription.length > 0 && (
            <section className="card">
              <h2 className="px-6 pt-5 pb-2">Prescription</h2>
              <ul className="divide-y divide-line">
                {n.prescription.map((r, i) => (
                  <li key={i} className="px-6 py-4 flex gap-4">
                    <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-brand-soft text-brand"><Pill className="h-5 w-5" /></span>
                    <div>
                      <p className="font-semibold">{r.medicine}</p>
                      <p className="text-sm">{[r.dosage, r.frequency, r.durationDays && `for ${r.durationDays} days`].filter(Boolean).join(', ')}</p>
                      {r.instructions && <p className="text-sm text-ink-soft">{r.instructions}</p>}
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {n.addendums.length > 0 && (
            <section className="card p-6 space-y-3">
              <h2>Later notes from your doctor</h2>
              {n.addendums.map((a) => <p key={a.id} className="border-l-2 border-brand pl-4"><span className="text-sm text-ink-faint">{dateTime(a.createdAt, tz)}</span><br />{a.text}</p>)}
            </section>
          )}
        </div>

        <aside className="space-y-4">
          {vitals.length > 0 && (
            <section className="card p-5">
              <h2 className="text-base mb-3">Measurements</h2>
              <dl className="grid grid-cols-2 gap-3">
                {vitals.map(([k, val]) => (
                  <div key={k} className="rounded-xl bg-canvas p-3">
                    <dt className="text-xs text-ink-soft">{k}</dt>
                    <dd className="font-semibold tabular-nums">{val}</dd>
                  </div>
                ))}
              </dl>
            </section>
          )}
          {n.followUpInDays && (
            <section className="card p-5 space-y-3 border-amber/40 bg-amber-soft">
              <p className="font-semibold">Follow-up in {n.followUpInDays} days</p>
              <p className="text-sm text-ink-soft">Your doctor recommends a check-in visit.</p>
              <Link to="/book" className={buttonClass('primary', 'sm') + ' w-full'}><CalendarPlus className="h-4 w-4" />Book follow-up</Link>
            </section>
          )}
        </aside>
      </div>
    </article>
  )
}
