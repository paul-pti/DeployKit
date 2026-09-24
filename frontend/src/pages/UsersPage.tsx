import { UserForm } from '../features/users/UserForm'
import { UserList } from '../features/users/UserList'

export function UsersPage() {
  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-semibold tracking-tight">Users</h1>
      <UserForm />
      <UserList />
    </div>
  )
}
