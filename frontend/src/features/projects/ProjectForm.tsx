import { useState, type ChangeEvent, type FormEvent, type InputHTMLAttributes } from 'react'
import { parseApiError } from '../../lib/apiError'
import { useCreateProject } from '../../hooks/useProjects'

const emptyForm = { name: '', repositoryUrl: '', branch: '', port: '8080' }
type FormField = keyof typeof emptyForm

const inputClass =
  'mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm focus:border-slate-900 focus:outline-none'

export function ProjectForm() {
  const [form, setForm] = useState(emptyForm)
  const createProject = useCreateProject()
  const { message, fieldErrors } = createProject.isError
    ? parseApiError(createProject.error)
    : { message: '', fieldErrors: {} as Record<string, string> }

  const update = (name: FormField) => (event: ChangeEvent<HTMLInputElement>) =>
    setForm((current) => ({ ...current, [name]: event.target.value }))

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    createProject.mutate(
      {
        name: form.name,
        repositoryUrl: form.repositoryUrl,
        branch: form.branch || undefined,
        port: Number(form.port),
      },
      { onSuccess: () => setForm(emptyForm) },
    )
  }

  const field = (id: FormField, label: string, extra: InputHTMLAttributes<HTMLInputElement> = {}) => (
    <div>
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        value={form[id]}
        onChange={update(id)}
        aria-invalid={Boolean(fieldErrors[id])}
        className={inputClass}
        {...extra}
      />
      {fieldErrors[id] && <p className="mt-1 text-sm text-red-600">{fieldErrors[id]}</p>}
    </div>
  )

  return (
    <form onSubmit={onSubmit} className="space-y-4 rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-lg font-semibold">New project</h2>
      <div className="grid gap-4 sm:grid-cols-2">
        {field('name', 'Name', { placeholder: 'my-app', required: true })}
        {field('repositoryUrl', 'GitHub repository URL', {
          placeholder: 'https://github.com/owner/repo',
          required: true,
        })}
        {field('branch', 'Branch', { placeholder: 'main' })}
        {field('port', 'Application port', { type: 'number', min: 1, max: 65535, required: true })}
      </div>
      {message && Object.keys(fieldErrors).length === 0 && (
        <p role="alert" className="text-sm text-red-600">
          {message}
        </p>
      )}
      <button
        type="submit"
        disabled={createProject.isPending}
        className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
      >
        {createProject.isPending ? 'Creating…' : 'Create project'}
      </button>
    </form>
  )
}
