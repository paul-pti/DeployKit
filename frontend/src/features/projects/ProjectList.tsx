import { Link } from 'react-router-dom'
import { parseApiError } from '../../lib/apiError'
import { useDeleteProject, useProjects } from '../../hooks/useProjects'

export function ProjectList() {
  const { data, isPending, isError, error } = useProjects()
  const deleteProject = useDeleteProject()

  if (isPending) return <p className="text-slate-500">Loading projects…</p>
  if (isError) return <p role="alert" className="text-red-600">{parseApiError(error).message}</p>
  if (data.length === 0) {
    return <p className="text-slate-500">No projects yet. Create your first one above.</p>
  }

  const onDelete = (id: string, name: string) => {
    if (window.confirm(`Delete project "${name}"?`)) deleteProject.mutate(id)
  }

  return (
    <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white">
      {data.map((project) => (
        <li key={project.id} className="flex items-center justify-between gap-4 p-4">
          <div className="min-w-0">
            <Link to={`/projects/${project.id}`} className="font-medium hover:underline">
              {project.name}
            </Link>
            <p className="truncate text-sm text-slate-500">
              {project.repositoryUrl} · {project.branch} · port {project.port}
            </p>
          </div>
          <button
            type="button"
            onClick={() => onDelete(project.id, project.name)}
            disabled={deleteProject.isPending}
            className="text-sm text-red-600 hover:underline disabled:opacity-50"
          >
            Delete
          </button>
        </li>
      ))}
    </ul>
  )
}
