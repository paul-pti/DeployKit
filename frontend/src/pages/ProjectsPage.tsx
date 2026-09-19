import { ProjectForm } from '../features/projects/ProjectForm'
import { ProjectList } from '../features/projects/ProjectList'

export function ProjectsPage() {
  return (
    <section className="space-y-6">
      <h1 className="text-2xl font-semibold">Projects</h1>
      <ProjectForm />
      <ProjectList />
    </section>
  )
}
