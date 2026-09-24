import { useUsers } from '../../hooks/useUsers'
import { parseApiError } from '../../lib/apiError'

export function UserList() {
  const { data, isPending, isError, error } = useUsers()

  if (isPending) return <p className="text-sm text-slate-500">Loading users…</p>
  if (isError)
    return (
      <p role="alert" className="text-sm text-red-600">
        {parseApiError(error).message}
      </p>
    )

  return (
    <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white">
      {data.map((user) => (
        <li key={user.id} className="flex items-center justify-between px-5 py-3 text-sm">
          <span className="font-medium">{user.email}</span>
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
            {user.role}
          </span>
        </li>
      ))}
    </ul>
  )
}
