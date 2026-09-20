import { apiClient } from '../lib/apiClient'
import type { DeployRequest, Deployment, DeploymentPage, DeploymentStatus } from '../types/deployment'

export async function deployProject(projectId: string, request?: DeployRequest): Promise<Deployment> {
  const { data } = await apiClient.post<Deployment>(`/api/projects/${projectId}/deploy`, request)
  return data
}

export interface HistoryQuery {
  statuses: DeploymentStatus[]
  page: number
  size: number
}

export async function listDeployments(projectId: string, query: HistoryQuery): Promise<DeploymentPage> {
  // The backend expects a repeated `status` parameter, which axios would otherwise send as `status[]`.
  const params = new URLSearchParams()
  query.statuses.forEach((status) => params.append('status', status))
  params.set('page', String(query.page))
  params.set('size', String(query.size))
  const { data } = await apiClient.get<DeploymentPage>(`/api/projects/${projectId}/deployments`, { params })
  return data
}

export async function getDeployment(id: string): Promise<Deployment> {
  const { data } = await apiClient.get<Deployment>(`/api/deployments/${id}`)
  return data
}
