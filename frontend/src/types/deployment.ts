export type DeploymentStatus =
  | 'PENDING'
  | 'BUILDING'
  | 'DEPLOYING'
  | 'RUNNING'
  | 'FAILED'
  | 'ROLLED_BACK'

export interface Deployment {
  id: string
  projectId: string
  status: DeploymentStatus
  image: string
  commitSha: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  errorMessage: string | null
}

export interface DeployRequest {
  image?: string
  commitSha?: string
}

/** Statuses in which the deployment is still in flight and worth polling. */
export function isActive(status: DeploymentStatus | undefined): boolean {
  return status === 'PENDING' || status === 'BUILDING' || status === 'DEPLOYING'
}
