import { apiClient } from '../lib/apiClient'
import type { DeploymentLogsResponse } from '../types/logs'

export interface LogsQuery {
  tail: number
  /** Read the logs of the previous container, which explains a crash loop. */
  previous: boolean
}

export async function getDeploymentLogs(id: string, query: LogsQuery): Promise<DeploymentLogsResponse> {
  const { data } = await apiClient.get<DeploymentLogsResponse>(`/api/deployments/${id}/logs`, {
    params: { tail: query.tail, previous: query.previous },
  })
  return data
}
