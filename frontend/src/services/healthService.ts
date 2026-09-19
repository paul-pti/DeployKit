import { apiClient } from '../lib/apiClient'
import type { HealthResponse } from '../types/health'

export async function fetchHealth(): Promise<HealthResponse> {
  const { data } = await apiClient.get<HealthResponse>('/api/health')
  return data
}
