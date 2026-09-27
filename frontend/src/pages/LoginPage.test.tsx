import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../features/auth/authContext'
import { LoginPage } from './LoginPage'

function renderLoginPage(login: AuthContextValue['login']) {
  const value: AuthContextValue = { user: null, isAdmin: false, login, logout: () => {} }
  render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/projects" element={<div>Projects page</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('LoginPage', () => {
  it('logs in and navigates to the dashboard on success', async () => {
    const login = vi.fn().mockResolvedValue(undefined)
    renderLoginPage(login)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Email'), 'admin@example.com')
    await user.type(screen.getByLabelText('Password'), 'correct-password')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(login).toHaveBeenCalledWith('admin@example.com', 'correct-password')
    await waitFor(() => expect(screen.getByText('Projects page')).toBeInTheDocument())
  })

  it('shows the error and clears the password on wrong credentials', async () => {
    const login = vi.fn().mockRejectedValue({
      isAxiosError: true,
      response: { status: 401, data: { detail: 'Invalid email or password' } },
    })
    renderLoginPage(login)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Email'), 'admin@example.com')
    await user.type(screen.getByLabelText('Password'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(screen.getByLabelText('Password')).toHaveValue('')
  })
})
