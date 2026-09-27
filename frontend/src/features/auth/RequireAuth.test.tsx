import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthContext, type AuthContextValue } from './authContext'
import { RequireAdmin, RequireAuth } from './RequireAuth'
import type { User } from '../../types/auth'

const baseAuth: AuthContextValue = {
  user: null,
  isAdmin: false,
  login: async () => {},
  logout: () => {},
}

const aUser: User = { id: 'u1', email: 'alice@example.com', role: 'USER', createdAt: '2026-01-01T00:00:00Z' }
const anAdmin: User = { id: 'u2', email: 'admin@example.com', role: 'ADMIN', createdAt: '2026-01-01T00:00:00Z' }

function renderBehind(guard: 'auth' | 'admin', value: AuthContextValue) {
  const Guard = guard === 'auth' ? RequireAuth : RequireAdmin
  render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route path="/login" element={<div>Login page</div>} />
          <Route path="/projects" element={<div>Projects page</div>} />
          <Route element={<Guard />}>
            <Route path="/protected" element={<div>Protected content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('RequireAuth', () => {
  it('redirects an anonymous visitor to /login', () => {
    renderBehind('auth', baseAuth)

    expect(screen.getByText('Login page')).toBeInTheDocument()
  })

  it('renders the protected content for a signed-in user', () => {
    renderBehind('auth', { ...baseAuth, user: aUser })

    expect(screen.getByText('Protected content')).toBeInTheDocument()
  })
})

describe('RequireAdmin', () => {
  it('sends a non-admin back to /projects', () => {
    renderBehind('admin', { ...baseAuth, user: aUser, isAdmin: false })

    expect(screen.getByText('Projects page')).toBeInTheDocument()
  })

  it('renders the protected content for an admin', () => {
    renderBehind('admin', { ...baseAuth, user: anAdmin, isAdmin: true })

    expect(screen.getByText('Protected content')).toBeInTheDocument()
  })
})
