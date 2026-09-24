import { useState, type FormEvent } from 'react'
import { useCreateUser } from '../../hooks/useUsers'
import { parseApiError } from '../../lib/apiError'
import type { Role } from '../../types/auth'

const inputClass =
  'mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm focus:border-slate-900 focus:outline-none'

export function UserForm() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('USER')
  const createUser = useCreateUser()
  const { message, fieldErrors } = createUser.isError
    ? parseApiError(createUser.error)
    : { message: '', fieldErrors: {} as Record<string, string> }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    createUser.mutate(
      { email, password, role },
      {
        onSuccess: () => {
          setEmail('')
          setPassword('')
          setRole('USER')
        },
      },
    )
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4 rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-lg font-semibold">New user</h2>
      <div className="grid gap-4 sm:grid-cols-3">
        <div>
          <label htmlFor="user-email" className="text-sm font-medium">
            Email
          </label>
          <input
            id="user-email"
            type="email"
            required
            autoComplete="off"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            aria-invalid={Boolean(fieldErrors.email)}
            className={inputClass}
          />
          {fieldErrors.email && <p className="mt-1 text-sm text-red-600">{fieldErrors.email}</p>}
        </div>
        <div>
          <label htmlFor="user-password" className="text-sm font-medium">
            Password
          </label>
          <input
            id="user-password"
            type="password"
            required
            minLength={12}
            maxLength={72}
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-invalid={Boolean(fieldErrors.password)}
            className={inputClass}
          />
          {fieldErrors.password && <p className="mt-1 text-sm text-red-600">{fieldErrors.password}</p>}
        </div>
        <div>
          <label htmlFor="user-role" className="text-sm font-medium">
            Role
          </label>
          <select
            id="user-role"
            value={role}
            onChange={(event) => setRole(event.target.value as Role)}
            className={inputClass}
          >
            <option value="USER">USER</option>
            <option value="ADMIN">ADMIN</option>
          </select>
        </div>
      </div>
      {message && Object.keys(fieldErrors).length === 0 && (
        <p role="alert" className="text-sm text-red-600">
          {message}
        </p>
      )}
      <button
        type="submit"
        disabled={createUser.isPending}
        className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
      >
        {createUser.isPending ? 'Creating…' : 'Create user'}
      </button>
    </form>
  )
}
