import type { DeploymentStatus } from '../../types/deployment'

const badgeClass: Record<DeploymentStatus, string> = {
  PENDING: 'bg-slate-100 text-slate-700',
  BUILDING: 'bg-blue-100 text-blue-800',
  DEPLOYING: 'bg-blue-100 text-blue-800',
  RUNNING: 'bg-emerald-100 text-emerald-800',
  FAILED: 'bg-red-100 text-red-800',
  ROLLED_BACK: 'bg-amber-100 text-amber-800',
}

export function StatusBadge({ status }: { status: DeploymentStatus }) {
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${badgeClass[status]}`}>{status}</span>
  )
}
