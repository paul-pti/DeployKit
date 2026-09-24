import type { User } from '../types/auth'

export interface Session {
  token: string
  /** Epoch milliseconds after which the token is no longer accepted. */
  expiresAt: number
  user: User
}

/** Fired when the API answers 401 to an authenticated call: the session is over. */
export const UNAUTHORIZED_EVENT = 'deploykit:unauthorized'

const KEY = 'deploykit.session'

// sessionStorage: the token disappears with the tab. Storage can throw (private mode, blocked
// site data), in which case the app simply behaves as logged out.
export function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    if (!session.token || typeof session.expiresAt !== 'number' || session.expiresAt <= Date.now()) {
      clearSession()
      return null
    }
    return session
  } catch {
    return null
  }
}

export function saveSession(session: Session): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(session))
  } catch {
    // Not persisted: the session lives in memory only.
  }
}

export function clearSession(): void {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // Nothing to clear.
  }
}

export function getToken(): string | null {
  return loadSession()?.token ?? null
}
