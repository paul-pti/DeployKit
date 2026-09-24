import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'

/** Sends anonymous visitors to the login page, remembering where they wanted to go. */
export function RequireAuth() {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

/** The UI mirror of the backend rule: /api/users is ADMIN only. The API enforces it either way. */
export function RequireAdmin() {
  const { isAdmin } = useAuth()
  if (!isAdmin) return <Navigate to="/projects" replace />
  return <Outlet />
}
