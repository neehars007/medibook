import type { AuthResponse } from './types'

/** Error with the server's ProblemDetail message, so screens can show it as-is. */
export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message)
  }
}

let accessToken: string | null = null
let onSessionLost: () => void = () => {}
let refreshing: Promise<AuthResponse | null> | null = null

export const tokenStore = {
  set(t: string | null) { accessToken = t },
  get() { return accessToken },
  onLost(fn: () => void) { onSessionLost = fn },
}

/** Uses the httpOnly refresh cookie. Deduplicated so parallel 401s trigger one refresh. */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshing) {
    refreshing = fetch('/api/v1/auth/refresh', { method: 'POST', credentials: 'include' })
      .then(async (r) => {
        if (!r.ok) return null
        const body = (await r.json()) as AuthResponse
        accessToken = body.accessToken
        return body
      })
      .catch(() => null)
      .finally(() => { refreshing = null })
  }
  return refreshing
}

async function raw(path: string, init: RequestInit, retry: boolean): Promise<Response> {
  const headers = new Headers(init.headers)
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const res = await fetch(`/api/v1${path}`, { ...init, headers, credentials: 'include' })
  if (res.status === 401 && retry && accessToken) {
    const refreshed = await refreshSession()
    if (refreshed) return raw(path, init, false)
    accessToken = null
    onSessionLost()
  }
  return res
}

async function toError(res: Response): Promise<ApiError> {
  let msg = res.statusText || 'Request failed'
  try {
    const body = await res.json()
    msg = body.detail || body.title || msg
  } catch { /* empty body */ }
  if (res.status === 401) msg = 'Please log in to continue.'
  if (res.status === 429) msg = 'Too many requests. Wait a minute and try again.'
  return new ApiError(res.status, msg)
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await raw(path, init, true)
  if (!res.ok) throw await toError(res)
  if (res.status === 204 || res.headers.get('content-length') === '0') return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const get = <T,>(path: string) => api<T>(path)
export const post = <T,>(path: string, body?: unknown) =>
  api<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })
export const put = <T,>(path: string, body: unknown) => api<T>(path, { method: 'PUT', body: JSON.stringify(body) })
export const patch = <T,>(path: string, body: unknown) => api<T>(path, { method: 'PATCH', body: JSON.stringify(body) })
export const del = <T,>(path: string) => api<T>(path, { method: 'DELETE' })

/** Authenticated file download (PDFs need the bearer token, so a plain link won't do). */
export async function download(path: string, filename: string) {
  const res = await raw(path, {}, true)
  if (!res.ok) throw await toError(res)
  const url = URL.createObjectURL(await res.blob())
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

export const qs = (params: Record<string, string | number | undefined | null>) => {
  const p = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') p.set(k, String(v)) })
  const s = p.toString()
  return s ? `?${s}` : ''
}
