import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth, homeFor } from '../lib/auth'
import { useClinic } from '../lib/queries'
import SessionTimeout from './SessionTimeout'

const navCls = ({ isActive }: { isActive: boolean }) =>
  `no-underline px-3 py-2 rounded-md font-bold ${isActive ? 'bg-teal-tint text-teal-dark' : 'text-ink hover:bg-teal-tint'}`

export default function Layout() {
  const { user, logout } = useAuth()
  const { data: clinic } = useClinic()
  const navigate = useNavigate()

  return (
    <div className="min-h-screen flex flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 bg-surface px-4 py-2 rounded-md">Skip to content</a>

      <div className="bg-ink text-paper text-center text-base px-4 py-2">
        <strong>{clinic?.emergencyNotice || 'This service is not for emergencies. In an emergency, call 911.'}</strong>
      </div>

      <header className="border-b-2 border-line bg-surface">
        <div className="mx-auto max-w-5xl px-4 py-3 flex flex-wrap items-center gap-x-4 gap-y-2">
          <Link to="/" className="no-underline text-ink flex items-center gap-2 mr-auto">
            <img src="/favicon.svg" alt="" className="h-9 w-9" />
            <span className="text-xl font-extrabold">{clinic?.name ?? 'MediBook'}</span>
          </Link>
          <nav aria-label="Main" className="flex flex-wrap items-center gap-1">
            <NavLink to="/doctors" className={navCls}>Doctors</NavLink>
            {user?.role === 'PATIENT' && <NavLink to="/me/appointments" className={navCls}>My appointments</NavLink>}
            {user && user.role !== 'PATIENT' && <NavLink to={homeFor(user.role)} className={navCls}>Workspace</NavLink>}
            {user ? (
              <button className="px-3 py-2 rounded-md font-bold text-ink hover:bg-teal-tint"
                onClick={async () => { await logout(); navigate('/') }}>
                Log out
              </button>
            ) : (
              <NavLink to="/login" className={navCls}>Log in</NavLink>
            )}
            <Link to="/book" className="no-underline ml-1 rounded-md bg-teal px-4 py-2 font-bold text-white hover:bg-teal-dark">
              Book a visit
            </Link>
          </nav>
        </div>
      </header>

      <main id="main" className="flex-1 mx-auto w-full max-w-5xl px-4 py-8">
        <Outlet />
      </main>

      <footer className="border-t-2 border-line text-ink-soft">
        <div className="mx-auto max-w-5xl px-4 py-6 text-base space-y-1">
          {clinic && (
            <address className="not-italic">
              <strong className="text-ink">{clinic.name}</strong><br />
              {clinic.address}<br />
              {clinic.phone}
            </address>
          )}
          <p>Portfolio demo. All patients, doctors and records are fictional.</p>
        </div>
      </footer>

      {user && <SessionTimeout />}
    </div>
  )
}
