import { Link, Outlet } from 'react-router-dom'
import { BackendStatus } from './BackendStatus'

export function Layout() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-4">
          <Link to="/projects" className="text-lg font-semibold tracking-tight">
            DeployKit
          </Link>
          <BackendStatus />
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-6 py-8">
        <Outlet />
      </main>
    </div>
  )
}
