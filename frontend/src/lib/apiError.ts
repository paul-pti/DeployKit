import { isAxiosError } from 'axios'

export interface ParsedApiError {
  message: string
  fieldErrors: Record<string, string>
}

/** Extracts a user-facing message and per-field errors from an RFC 7807 problem response. */
export function parseApiError(error: unknown): ParsedApiError {
  if (isAxiosError(error)) {
    if (!error.response) {
      return { message: 'Cannot reach the server', fieldErrors: {} }
    }
    const data = error.response.data as { detail?: string; errors?: Record<string, string> } | undefined
    return {
      message: data?.detail ?? `Request failed (${error.response.status})`,
      fieldErrors: data?.errors ?? {},
    }
  }
  return { message: 'Unexpected error', fieldErrors: {} }
}
