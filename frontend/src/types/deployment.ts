export type DeploymentStatus =
  | 'PENDING'
  | 'BUILDING'
  | 'DEPLOYING'
  | 'RUNNING'
  | 'FAILED'
  | 'ROLLED_BACK'

export const DEPLOYMENT_STATUSES: DeploymentStatus[] = [
  'PENDING',
  'BUILDING',
  'DEPLOYING',
  'RUNNING',
  'FAILED',
  'ROLLED_BACK',
]

export interface Deployment {
  id: string
  projectId: string
  /** Per-project deployment number: 1, 2, 3, ... */
  version: number
  /** For a rollback: the version whose image it restores; null for a regular deployment. */
  rollbackOfVersion: number | null
  status: DeploymentStatus
  image: string
  commitSha: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  errorMessage: string | null
}

/** Body of a rollback request; without a target the previous successful version is restored. */
export interface RollbackRequest {
  targetVersion?: number
}

/** One page of a project's deployment history, newest first. `page` is zero-based. */
export interface DeploymentPage {
  content: Deployment[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface DeployRequest {
  image?: string
  commitSha?: string
}

/** Statuses in which the deployment is still in flight and worth polling. */
export function isActive(status: DeploymentStatus | undefined): boolean {
  return status === 'PENDING' || status === 'BUILDING' || status === 'DEPLOYING'
}
