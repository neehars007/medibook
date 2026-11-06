import { Link } from 'react-router-dom'
import { useAuth } from '../lib/auth'

export function StaffPlaceholder({ title }: { title: string }) {
  const { user } = useAuth()
  return (
    <div className="max-w-2xl space-y-3">
      <h1>{title}</h1>
      <p>You're logged in as <strong>{user?.email}</strong>. This workspace is the next part of the build.</p>
      <p className="text-ink-soft">Until then, use the API docs at <a href="http://localhost:8080/swagger-ui.html">localhost:8080/swagger-ui.html</a>.</p>
    </div>
  )
}

export function NotFound() {
  return (
    <div className="space-y-3">
      <h1>Page not found</h1>
      <p>The link may be old or mistyped. <Link to="/">Go to the home page</Link></p>
    </div>
  )
}
