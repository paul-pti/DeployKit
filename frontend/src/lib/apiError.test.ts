import { describe, expect, it } from 'vitest'
import { parseApiError } from './apiError'

function axiosError(overrides: Record<string, unknown>) {
  return { isAxiosError: true, ...overrides }
}

describe('parseApiError', () => {
  it('reports an unreachable server when there is no response', () => {
    expect(parseApiError(axiosError({ response: undefined }))).toEqual({
      message: 'Cannot reach the server',
      fieldErrors: {},
    })
  })

  it('extracts the detail and field errors of a problem+json response', () => {
    const result = parseApiError(
      axiosError({
        response: { status: 400, data: { detail: 'Validation failed', errors: { email: 'must not be blank' } } },
      }),
    )

    expect(result).toEqual({ message: 'Validation failed', fieldErrors: { email: 'must not be blank' } })
  })

  it('falls back to the HTTP status when the response carries no detail', () => {
    const result = parseApiError(axiosError({ response: { status: 500, data: undefined } }))

    expect(result).toEqual({ message: 'Request failed (500)', fieldErrors: {} })
  })

  it('reports an unexpected error for anything that is not an Axios error', () => {
    expect(parseApiError(new Error('boom'))).toEqual({ message: 'Unexpected error', fieldErrors: {} })
    expect(parseApiError('nope')).toEqual({ message: 'Unexpected error', fieldErrors: {} })
  })
})
