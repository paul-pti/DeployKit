import axios from 'axios'
import { clearSession, getToken, UNAUTHORIZED_EVENT } from './authStorage'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 10_000,
  headers: { Accept: 'application/json' },
})

apiClient.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

apiClient.interceptors.response.use(undefined, (error: unknown) => {
  // A 401 on the login call just means "wrong credentials"; anywhere else the session is gone.
  if (axios.isAxiosError(error) && error.response?.status === 401 && error.config?.url !== '/api/auth/login') {
    clearSession()
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
  }
  return Promise.reject(error)
})
