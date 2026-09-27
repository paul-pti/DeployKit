import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <section>
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <Link to="/projects" className="mt-2 inline-block text-slate-600 underline">
        Back to projects
      </Link>
    </section>
  )
}
