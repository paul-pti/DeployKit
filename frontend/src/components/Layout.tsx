import { Link, Outlet } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { BackendStatus } from './BackendStatus'

export function Layout() {
  const { user, isAdmin, logout } = useAuth()

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-6 py-4">
          <nav className="flex items-center gap-6">
            <Link to="/projects" className="text-lg font-semibold tracking-tight">
              DeployKit
            </Link>
            {isAdmin && (
              <Link to="/users" className="text-sm text-slate-600 hover:text-slate-900">
                Users
              </Link>
            )}
          </nav>
          <div className="flex items-center gap-4">
            <BackendStatus />
            {user && (
              <div className="flex items-center gap-3 text-sm">
                <span className="text-slate-600">
                  {user.email} <span className="text-xs text-slate-400">({user.role})</span>
                </span>
                <button
                  type="button"
                  onClick={logout}
                  className="rounded-md border border-slate-300 px-3 py-1 hover:bg-slate-100"
                >
                  Log out
                </button>
              </div>
            )}
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-6 py-8">
        <Outlet />
      </main>
    </div>
  )
}
