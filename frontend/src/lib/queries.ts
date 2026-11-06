import { useQuery } from '@tanstack/react-query'
import { get } from './api'
import type { Clinic } from './types'

/** Clinic profile + timezone. Falls back to New York so formatting never crashes before it loads. */
export function useClinic() {
  const q = useQuery({ queryKey: ['clinic'], queryFn: () => get<Clinic>('/clinic'), staleTime: 10 * 60_000 })
  return { ...q, tz: q.data?.timezone ?? 'America/New_York' }
}
