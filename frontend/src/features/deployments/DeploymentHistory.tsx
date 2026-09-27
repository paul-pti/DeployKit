import { useState } from 'react'
import { useDeploymentHistory, useRollback } from '../../hooks/useDeployments'
import { parseApiError } from '../../lib/apiError'
import { formatDuration, shortSha } from '../../lib/format'
import { DEPLOYMENT_STATUSES, isActive, type Deployment, type DeploymentStatus } from '../../types/deployment'
import { DeploymentLogs } from '../logs/DeploymentLogs'
import { StatusBadge } from './StatusBadge'

function durationOf(deployment: Deployment): string {
  if (isActive(deployment.status)) return deployment.startedAt ? 'in progress' : 'queued'
  return formatDuration(deployment.startedAt, deployment.finishedAt) ?? '—'
}

export function DeploymentHistory({ projectId }: { projectId: string }) {
  const [status, setStatus] = useState<DeploymentStatus | ''>('')
  const [page, setPage] = useState(0)
  // Until a row is chosen, the logs follow the newest deployment.
  const [pinnedId, setPinnedId] = useState<string>()
  const { data, isPending, isError, error } = useDeploymentHistory(projectId, status ? [status] : [], page)
  const selectedId = pinnedId ?? data?.content[0]?.id
  const selected = data?.content.find((deployment) => deployment.id === selectedId)

  // Rollbacks always start from the project's latest deployment, so they are only offered on the unfiltered first page.
  const rollback = useRollback(projectId)
  const latest = page === 0 && !status ? data?.content[0] : undefined
  const canRollBack = latest !== undefined && !isActive(latest.status) && !rollback.isPending
  const isRollbackTarget = (deployment: Deployment) =>
    latest !== undefined &&
    deployment.version < latest.version &&
    (deployment.status === 'RUNNING' || deployment.status === 'ROLLED_BACK') &&
    deployment.image !== latest.image

  const confirmRollback = (message: string, targetVersion?: number) => {
    if (latest && window.confirm(message)) rollback.mutate({ deploymentId: latest.id, targetVersion })
  }

  const onFilterChange = (value: string) => {
    setStatus(value as DeploymentStatus | '')
    setPage(0)
  }

  return (
    <section className="mt-8 space-y-4">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-lg font-semibold">Deployment history</h2>
        <div className="flex items-center gap-3">
          {latest && (
            <button
              type="button"
              disabled={!canRollBack}
              onClick={() =>
                confirmRollback(`Roll back deployment #${latest.version} to the previous successful version?`)
              }
              className="rounded-md border border-amber-600 px-3 py-1 text-sm text-amber-800 hover:bg-amber-50 disabled:opacity-50"
            >
              {rollback.isPending ? 'Rolling back…' : 'Roll back'}
            </button>
          )}
        <label className="flex items-center gap-2 text-sm">
          Status
          <select
            value={status}
            onChange={(event) => onFilterChange(event.target.value)}
            className="rounded-md border border-slate-300 bg-white px-2 py-1 text-sm"
          >
            <option value="">All</option>
            {DEPLOYMENT_STATUSES.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>
        </div>
      </div>

      {rollback.isError && (
        <p role="alert" className="text-sm text-red-600">
          {parseApiError(rollback.error).message}
        </p>
      )}

      {isPending && <p className="text-slate-500">Loading deployments…</p>}
      {isError && (
        <p role="alert" className="text-red-600">
          {parseApiError(error).message}
        </p>
      )}

      {data && data.content.length === 0 && (
        <p className="text-slate-500">
          {status ? `No ${status} deployments.` : 'No deployments yet. Use Deploy above to start one.'}
        </p>
      )}

      {data && data.content.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-3 py-2">Version</th>
                  <th className="px-3 py-2">Commit</th>
                  <th className="px-3 py-2">Status</th>
                  <th className="px-3 py-2">Created</th>
                  <th className="px-3 py-2">Duration</th>
                  <th className="px-3 py-2">Image</th>
                  <th className="px-3 py-2">Failure reason</th>
                  <th className="px-3 py-2">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((deployment) => (
                  <tr key={deployment.id} className={deployment.id === selectedId ? 'bg-slate-50' : undefined}>
                    <td className="whitespace-nowrap px-3 py-2 font-medium">
                      #{deployment.version}
                      {deployment.rollbackOfVersion !== null && (
                        <span
                          className="ml-1 text-xs font-normal text-amber-700"
                          title={`Rollback: restores version #${deployment.rollbackOfVersion}`}
                        >
                          ↩ #{deployment.rollbackOfVersion}
                        </span>
                      )}
                    </td>
                    <td className="px-3 py-2 font-mono" title={deployment.commitSha ?? undefined}>
                      {shortSha(deployment.commitSha)}
                    </td>
                    <td className="px-3 py-2">
                      <StatusBadge status={deployment.status} />
                    </td>
                    <td className="whitespace-nowrap px-3 py-2">
                      {new Date(deployment.createdAt).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })}
                    </td>
                    <td className="whitespace-nowrap px-3 py-2">{durationOf(deployment)}</td>
                    <td className="max-w-36 truncate px-3 py-2" title={deployment.image}>
                      {deployment.image}
                    </td>
                    <td
                      className="max-w-40 truncate px-3 py-2 text-red-700"
                      title={deployment.errorMessage ?? undefined}
                    >
                      {deployment.errorMessage ?? '—'}
                    </td>
                    <td className="px-3 py-2">
                      <div className="flex gap-1">
                      <button
                        type="button"
                        onClick={() => setPinnedId(deployment.id)}
                        aria-pressed={deployment.id === selectedId}
                        aria-label={`View logs of deployment #${deployment.version}`}
                        className="rounded-md border border-slate-300 px-2 py-0.5 text-xs aria-pressed:bg-slate-900 aria-pressed:text-white"
                      >
                        View
                      </button>
                      {isRollbackTarget(deployment) && (
                        <button
                          type="button"
                          disabled={!canRollBack}
                          onClick={() =>
                            confirmRollback(
                              `Roll back to version #${deployment.version} (${deployment.image})?`,
                              deployment.version,
                            )
                          }
                          aria-label={`Roll back to version #${deployment.version}`}
                          title={`Roll back to version #${deployment.version}`}
                          className="whitespace-nowrap rounded-md border border-amber-600 px-2 py-0.5 text-xs text-amber-800 hover:bg-amber-50 disabled:opacity-50"
                        >
                          Restore
                        </button>
                      )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between text-sm text-slate-600">
            <span>
              Page {data.page + 1} of {Math.max(data.totalPages, 1)} · {data.totalElements} deployment
              {data.totalElements === 1 ? '' : 's'}
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setPage((current) => current - 1)}
                disabled={data.page === 0}
                className="rounded-md border border-slate-300 px-3 py-1 disabled:opacity-50"
              >
                Previous
              </button>
              <button
                type="button"
                onClick={() => setPage((current) => current + 1)}
                disabled={data.page + 1 >= data.totalPages}
                className="rounded-md border border-slate-300 px-3 py-1 disabled:opacity-50"
              >
                Next
              </button>
            </div>
          </div>

          {selectedId && <DeploymentLogs key={selectedId} deploymentId={selectedId} version={selected?.version} />}
        </>
      )}
    </section>
  )
}
