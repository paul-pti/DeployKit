import { useQuery } from '@tanstack/react-query'
import { fetchHealth } from '../services/healthService'

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: fetchHealth,
    refetchInterval: 15_000,
  })
}
