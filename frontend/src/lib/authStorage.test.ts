import { beforeEach, describe, expect, it } from 'vitest'
import { clearSession, getToken, loadSession, saveSession, type Session } from './authStorage'
import type { User } from '../types/auth'

const user: User = { id: 'u1', email: 'alice@example.com', role: 'USER', createdAt: '2026-01-01T00:00:00Z' }

function session(overrides: Partial<Session> = {}): Session {
  return { token: 'token-123', expiresAt: Date.now() + 60_000, user, ...overrides }
}

describe('authStorage', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('returns null when nothing was saved', () => {
    expect(loadSession()).toBeNull()
    expect(getToken()).toBeNull()
  })

  it('round-trips a saved session', () => {
    const stored = session()
    saveSession(stored)

    expect(loadSession()).toEqual(stored)
    expect(getToken()).toBe(stored.token)
  })

  it('treats an expired session as absent and clears it', () => {
    saveSession(session({ expiresAt: Date.now() - 1000 }))

    expect(loadSession()).toBeNull()
    expect(sessionStorage.getItem('deploykit.session')).toBeNull()
  })

  it('clearSession removes the stored session', () => {
    saveSession(session())

    clearSession()

    expect(loadSession()).toBeNull()
  })

  it('treats malformed stored data as no session, without throwing', () => {
    sessionStorage.setItem('deploykit.session', 'not-json')

    expect(() => loadSession()).not.toThrow()
    expect(loadSession()).toBeNull()
  })
})
