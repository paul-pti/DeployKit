import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { getDeploymentLogs } from '../services/logService'

const TAIL_LINES = 300
export const LOGS_REFRESH_MS = 3000

/** Logs of a deployment; re-fetched every 3 seconds while `refresh` is on (and the tab is visible). */
export function useDeploymentLogs(id: string | undefined, options: { previous: boolean; refresh: boolean }) {
  return useQuery({
    queryKey: ['deployments', id, 'logs', { previous: options.previous }],
    queryFn: () => getDeploymentLogs(id as string, { tail: TAIL_LINES, previous: options.previous }),
    enabled: Boolean(id),
    refetchInterval: options.refresh ? LOGS_REFRESH_MS : false,
    // Keep showing the previous lines while the next request is in flight, so the terminal does not flicker.
    placeholderData: keepPreviousData,
  })
}
