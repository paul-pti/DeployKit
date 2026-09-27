export interface Project {
  id: string
  name: string
  repositoryUrl: string
  branch: string
  port: number
  createdAt: string
  updatedAt: string
}

export interface CreateProjectRequest {
  name: string
  repositoryUrl: string
  branch?: string
  port: number
}
