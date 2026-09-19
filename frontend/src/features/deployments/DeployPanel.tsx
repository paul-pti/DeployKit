import { useState } from 'react'
import { useDeployProject, useDeployment } from '../../hooks/useDeployments'
import { parseApiError } from '../../lib/apiError'
import { isActive, type DeploymentStatus } from '../../types/deployment'

const badgeClass: Record<DeploymentStatus, string> = {
  PENDING: 'bg-slate-100 text-slate-700',
  BUILDING: 'bg-blue-100 text-blue-800',
  DEPLOYING: 'bg-blue-100 text-blue-800',
  RUNNING: 'bg-emerald-100 text-emerald-800',
  FAILED: 'bg-red-100 text-red-800',
  ROLLED_BACK: 'bg-amber-100 text-amber-800',
}

function formatDuration(start: string | null, end: string | null): string | null {
  if (!start || !end) return null
  const seconds = Math.max(0, Math.round((new Date(end).getTime() - new Date(start).getTime()) / 1000))
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}

export function DeployPanel({ projectId }: { projectId: string }) {
  const [image, setImage] = useState('')
  const [deploymentId, setDeploymentId] = useState<string>()
  const deploy = useDeployProject(projectId)
  const { data: deployment } = useDeployment(deploymentId)

  const busy = deploy.isPending || isActive(deployment?.status)
  const duration = deployment && formatDuration(deployment.startedAt, deployment.finishedAt)

  const onDeploy = () => {
    deploy.mutate(image.trim() ? { image: image.trim() } : undefined, {
      onSuccess: (created) => setDeploymentId(created.id),
    })
  }

  return (
    <section className="mt-8 space-y-4 rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-lg font-semibold">Deploy</h2>

      <div>
        <label htmlFor="image" className="text-sm font-medium">
          Image (optional)
        </label>
        <input
          id="image"
          value={image}
          onChange={(event) => setImage(event.target.value)}
          placeholder="Defaults to ghcr.io/<owner>/<repo>:<branch>"
          className="mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm focus:border-slate-900 focus:outline-none"
        />
      </div>

      <button
        type="button"
        onClick={onDeploy}
        disabled={busy}
        className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
      >
        {busy ? 'Deploying…' : 'Deploy'}
      </button>

      {deploy.isError && (
        <p role="alert" className="text-sm text-red-600">
          {parseApiError(deploy.error).message}
        </p>
      )}

      {deployment && (
        <div className="space-y-1 text-sm" aria-live="polite">
          <p>
            <span
              className={`rounded-full px-2 py-0.5 text-xs font-medium ${badgeClass[deployment.status]}`}
            >
              {deployment.status}
            </span>
            {duration && <span className="ml-2 text-slate-500">in {duration}</span>}
          </p>
          <p className="break-all text-slate-600">{deployment.image}</p>
          {deployment.errorMessage && <p className="text-red-600">{deployment.errorMessage}</p>}
        </div>
      )}
    </section>
  )
}
