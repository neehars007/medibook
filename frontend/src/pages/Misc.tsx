import { Link } from 'react-router-dom'
import { Construction, SearchX } from 'lucide-react'
import { useAuth } from '../lib/auth'
import { Empty, buttonClass } from '../components/ui'

export function StaffPlaceholder({ title }: { title: string }) {
  const { user } = useAuth()
  return (
    <div className="max-w-2xl space-y-6">
      <h1>{title}</h1>
      <Empty title="This workspace is coming next" icon={<Construction className="h-6 w-6" />}>
        You're logged in as {user?.email}. Until then, use the API docs at{' '}
        <a href="http://localhost:8080/swagger-ui.html">localhost:8080/swagger-ui.html</a>.
      </Empty>
    </div>
  )
}

export function NotFound() {
  return (
    <div className="mx-auto max-w-md">
      <Empty title="Page not found" icon={<SearchX className="h-6 w-6" />}>
        <p>The link may be old or mistyped.</p>
        <Link to="/" className={buttonClass('primary') + ' mt-4'}>Go to the home page</Link>
      </Empty>
    </div>
  )
}
