import { apiClient } from '../lib/apiClient'
import type { CreateProjectRequest, Project } from '../types/project'

export async function listProjects(): Promise<Project[]> {
  const { data } = await apiClient.get<Project[]>('/api/projects')
  return data
}

export async function getProject(id: string): Promise<Project> {
  const { data } = await apiClient.get<Project>(`/api/projects/${id}`)
  return data
}

export async function createProject(request: CreateProjectRequest): Promise<Project> {
  const { data } = await apiClient.post<Project>('/api/projects', request)
  return data
}

export async function deleteProject(id: string): Promise<void> {
  await apiClient.delete(`/api/projects/${id}`)
}
