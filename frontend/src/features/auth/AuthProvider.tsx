import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { clearSession, loadSession, saveSession, UNAUTHORIZED_EVENT, type Session } from '../../lib/authStorage'
import { login as requestLogin } from '../../services/authService'
import { AuthContext, type AuthContextValue } from './authContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(() => loadSession())

  const endSession = useCallback(() => {
    clearSession()
    setSession(null)
    // Nothing fetched for one account may be shown to the next.
    queryClient.clear()
  }, [queryClient])

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await requestLogin({ email, password })
      const next: Session = {
        token: response.accessToken,
        expiresAt: Date.now() + response.expiresIn * 1000,
        user: response.user,
      }
      queryClient.clear()
      saveSession(next)
      setSession(next)
    },
    [queryClient],
  )

  // The API rejected our token (revoked secret, expiry seen by the server): leave.
  useEffect(() => {
    window.addEventListener(UNAUTHORIZED_EVENT, endSession)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, endSession)
  }, [endSession])

  // Log out by ourselves when the token expires rather than waiting for a 401.
  useEffect(() => {
    if (!session) return
    const timer = window.setTimeout(endSession, Math.max(session.expiresAt - Date.now(), 0))
    return () => window.clearTimeout(timer)
  }, [session, endSession])

  const value = useMemo<AuthContextValue>(
    () => ({
      user: session?.user ?? null,
      isAdmin: session?.user.role === 'ADMIN',
      login,
      logout: endSession,
    }),
    [session, login, endSession],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
