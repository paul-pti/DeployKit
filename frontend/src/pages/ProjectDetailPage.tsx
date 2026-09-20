import { Link, useNavigate, useParams } from 'react-router-dom'
import { DeployPanel } from '../features/deployments/DeployPanel'
import { DeploymentHistory } from '../features/deployments/DeploymentHistory'
import { useDeleteProject, useProject } from '../hooks/useProjects'
import { parseApiError } from '../lib/apiError'

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { data: project, isPending, isError, error } = useProject(id)
  const deleteProject = useDeleteProject()

  const onDelete = () => {
    if (project && window.confirm(`Delete project "${project.name}"?`)) {
      deleteProject.mutate(project.id, { onSuccess: () => navigate('/projects') })
    }
  }

  return (
    <section>
      <Link to="/projects" className="text-sm text-slate-500 hover:text-slate-900">
        ← Projects
      </Link>

      {isPending && <p className="mt-4 text-slate-500">Loading project…</p>}
      {isError && (
        <p role="alert" className="mt-4 text-red-600">
          {parseApiError(error).message}
        </p>
      )}

      {project && (
        <>
          <h1 className="mt-2 text-2xl font-semibold">{project.name}</h1>
          <dl className="mt-4 grid max-w-xl grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
            <dt className="text-slate-500">Repository</dt>
            <dd className="break-all">{project.repositoryUrl}</dd>
            <dt className="text-slate-500">Branch</dt>
            <dd>{project.branch}</dd>
            <dt className="text-slate-500">Port</dt>
            <dd>{project.port}</dd>
            <dt className="text-slate-500">Created</dt>
            <dd>{new Date(project.createdAt).toLocaleString()}</dd>
          </dl>
          <button
            type="button"
            onClick={onDelete}
            disabled={deleteProject.isPending}
            className="mt-6 text-sm text-red-600 hover:underline disabled:opacity-50"
          >
            Delete project
          </button>
          <DeployPanel projectId={project.id} />
          <DeploymentHistory projectId={project.id} />
        </>
      )}
    </section>
  )
}
