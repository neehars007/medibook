import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Clock } from 'lucide-react'
import { useAuth } from '../lib/auth'
import { refreshSession } from '../lib/api'
import { Button } from './ui'

const LIMIT_MS = 15 * 60_000   // backend inactivity timeout
const WARN_MS = 13 * 60_000    // show the warning 2 minutes before

/** Clinic security norm: warn, then log out after 15 minutes without activity. */
export default function SessionTimeout() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const last = useRef(Date.now())
  const [remaining, setRemaining] = useState<number | null>(null)

  useEffect(() => {
    const bump = () => { if (remaining === null) last.current = Date.now() }
    const events = ['mousedown', 'keydown', 'touchstart', 'scroll']
    events.forEach((e) => window.addEventListener(e, bump, { passive: true }))
    const t = setInterval(() => {
      const idle = Date.now() - last.current
      if (idle >= LIMIT_MS) {
        clearInterval(t)
        logout('You were logged out after 15 minutes without activity.').then(() => navigate('/login'))
      } else if (idle >= WARN_MS) {
        setRemaining(Math.ceil((LIMIT_MS - idle) / 1000))
      }
    }, 1000)
    return () => { events.forEach((e) => window.removeEventListener(e, bump)); clearInterval(t) }
  }, [logout, navigate, remaining])

  if (remaining === null) return null
  const m = Math.floor(remaining / 60)
  const s = String(remaining % 60).padStart(2, '0')
  return (
    <div role="alertdialog" aria-modal="true" aria-labelledby="to-title" className="fixed inset-0 z-50 grid place-items-center bg-ink/40 backdrop-blur-sm p-4">
      <div className="card w-full max-w-sm p-6 space-y-4 shadow-lift">
        <div className="grid h-11 w-11 place-items-center rounded-full bg-amber-soft text-amber"><Clock className="h-5 w-5" /></div>
        <div className="space-y-1">
          <h2 id="to-title">Still there?</h2>
          <p className="text-ink-soft">For your privacy you'll be logged out in <strong className="text-ink tabular-nums">{m}:{s}</strong>.</p>
        </div>
        <div className="flex gap-2">
          <Button className="flex-1" onClick={async () => { await refreshSession(); last.current = Date.now(); setRemaining(null) }}>Stay logged in</Button>
          <Button variant="secondary" onClick={() => logout().then(() => navigate('/'))}>Log out</Button>
        </div>
      </div>
    </div>
  )
}
