import type { DeploymentStatus } from './deployment'

export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'

/** Logs of one pod of the application. `error` explains why `log` is empty (container not started, ...). */
export interface PodLogs {
  pod: string
  phase: string | null
  ready: boolean
  restarts: number
  reason: string | null
  log: string
  error: string | null
}

/** One step of the deployment workflow (queued, helm output, rollout progress, failure, ...). */
export interface DeploymentEvent {
  timestamp: string
  level: LogLevel
  message: string
}

export interface DeploymentLogsResponse {
  deploymentId: string
  status: DeploymentStatus
  pods: PodLogs[]
  /** Why `pods` is empty or incomplete (deployment not started, pods replaced, cluster unreachable, ...). */
  podsNote: string | null
  events: DeploymentEvent[]
}
