import { apiClient } from '../lib/apiClient'
import type { DeployRequest, Deployment } from '../types/deployment'

export async function deployProject(projectId: string, request?: DeployRequest): Promise<Deployment> {
  const { data } = await apiClient.post<Deployment>(`/api/projects/${projectId}/deploy`, request)
  return data
}

export async function getDeployment(id: string): Promise<Deployment> {
  const { data } = await apiClient.get<Deployment>(`/api/deployments/${id}`)
  return data
}
