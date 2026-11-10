import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { CalendarPlus, LogOut, Phone, ShieldAlert } from 'lucide-react'
import { useAuth, homeFor } from '../lib/auth'
import { useClinic } from '../lib/queries'
import SessionTimeout from './SessionTimeout'
import { buttonClass } from './ui'

const navCls = ({ isActive }: { isActive: boolean }) =>
  `no-underline rounded-lg px-3 py-2 text-[15px] font-semibold transition-colors ${isActive ? 'text-ink bg-white/[0.07]' : 'text-ink-soft hover:text-ink hover:bg-white/[0.04]'}`

export function Logo({ name }: { name?: string }) {
  return (
    <span className="flex items-center gap-2.5">
      <span className="grid h-9 w-9 place-items-center rounded-xl bg-grad text-white shadow-glow">
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor" aria-hidden><path d="M9.5 3h5v6.5H21v5h-6.5V21h-5v-6.5H3v-5h6.5z" /></svg>
      </span>
      <span className="text-[17px] font-bold tracking-tight text-ink truncate max-w-[9rem] sm:max-w-none">{name ?? 'MediBook'}</span>
    </span>
  )
}

export default function Layout() {
  const { user, logout } = useAuth()
  const { data: clinic } = useClinic()
  const navigate = useNavigate()

  return (
    <div className="relative min-h-screen flex flex-col overflow-x-clip">
      {/* Ambient aurora glow */}
      <div aria-hidden className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[720px]">
        <div className="absolute left-1/2 top-[-260px] h-[560px] w-[900px] -translate-x-1/2 rounded-full bg-[#6366F1] opacity-[0.18] blur-[140px]" />
        <div className="absolute right-[-120px] top-[80px] h-[360px] w-[420px] rounded-full bg-[#22D3EE] opacity-[0.10] blur-[120px]" />
        <div className="absolute left-[-160px] top-[220px] h-[320px] w-[420px] rounded-full bg-[#A855F7] opacity-[0.10] blur-[120px]" />
      </div>
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 z-50 bg-surface px-4 py-2 rounded-lg">Skip to content</a>

      <div className="border-b border-white/[0.06] bg-white/[0.02] text-sm text-ink-soft">
        <div className="mx-auto max-w-6xl px-4 py-2 flex items-center justify-center gap-2">
          <ShieldAlert className="h-4 w-4 text-amber" aria-hidden />
          <span>{clinic?.emergencyNotice || 'This service is not for emergencies. In an emergency, call 911.'}</span>
        </div>
      </div>

      <header className="sticky top-0 z-40 border-b border-white/[0.06] bg-[#070A13]/70 backdrop-blur-xl">
        <div className="mx-auto max-w-6xl px-4 h-16 flex items-center gap-4">
          <Link to="/" className="no-underline mr-auto min-w-0"><Logo name={clinic?.name} /></Link>
          <nav aria-label="Main" className="hidden sm:flex items-center gap-1">
            <NavLink to="/doctors" className={navCls}>Find a doctor</NavLink>
            {user?.role === 'PATIENT' && <NavLink to="/me/appointments" className={navCls}>My appointments</NavLink>}
            {user && user.role !== 'PATIENT' && <NavLink to={homeFor(user.role)} className={navCls}>Workspace</NavLink>}
          </nav>
          <div className="flex items-center gap-2">
            {user ? (
              <div className="flex items-center gap-2">
                <span className="hidden md:block text-sm text-ink-soft max-w-[180px] truncate" title={user.email}>{user.email}</span>
                <button aria-label="Log out" title="Log out"
                  className="grid h-10 w-10 place-items-center rounded-lg text-ink-soft hover:bg-white/[0.06] hover:text-ink"
                  onClick={async () => { await logout(); navigate('/') }}>
                  <LogOut className="h-5 w-5" />
                </button>
              </div>
            ) : (
              <NavLink to="/login" className={(a) => navCls(a) + ' whitespace-nowrap'}>Log in</NavLink>
            )}
            <Link to="/book" className={buttonClass('primary', 'sm') + ' !h-10 !px-4'}>
              <CalendarPlus className="h-4 w-4" aria-hidden /><span className="hidden sm:inline">Book a visit</span><span className="sm:hidden">Book</span>
            </Link>
          </div>
        </div>
        <nav aria-label="Main mobile" className="sm:hidden border-t border-white/[0.06] px-4 py-2 flex gap-1 overflow-x-auto">
          <NavLink to="/doctors" className={navCls}>Find a doctor</NavLink>
          {user?.role === 'PATIENT' && <NavLink to="/me/appointments" className={navCls}>My appointments</NavLink>}
        </nav>
      </header>

      <main id="main" className="flex-1 mx-auto w-full max-w-6xl px-4 py-8 sm:py-10">
        <Outlet />
      </main>

      <footer className="border-t border-white/[0.06] bg-white/[0.015]">
        <div className="mx-auto max-w-6xl px-4 py-8 grid gap-6 sm:grid-cols-3 text-sm text-ink-soft">
          <div className="space-y-2">
            <Logo name={clinic?.name} />
            <p>Portfolio demo. All patients, doctors and records are fictional.</p>
          </div>
          {clinic && (
            <address className="not-italic space-y-1">
              <p className="font-semibold text-ink">Visit us</p>
              <p>{clinic.address}</p>
              <p className="flex items-center gap-1.5"><Phone className="h-4 w-4" aria-hidden />{clinic.phone}</p>
            </address>
          )}
          <div className="space-y-1">
            <p className="font-semibold text-ink">Patients</p>
            <p><Link to="/book">Book a visit</Link></p>
            <p><Link to="/doctors">Find a doctor</Link></p>
          </div>
        </div>
      </footer>

      {user && <SessionTimeout />}
    </div>
  )
}
