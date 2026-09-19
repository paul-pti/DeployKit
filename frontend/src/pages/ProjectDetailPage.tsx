import { Link, useParams } from 'react-router-dom'

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>()

  return (
    <section>
      <Link to="/projects" className="text-sm text-slate-500 hover:text-slate-900">
        ← Projects
      </Link>
      <h1 className="mt-2 text-2xl font-semibold">Project {id}</h1>
      <p className="mt-2 text-slate-600">Deployments and logs will appear here in later phases.</p>
    </section>
  )
}
