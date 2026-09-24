import { apiClient } from '../lib/apiClient'
import type { LoginRequest, LoginResponse, User } from '../types/auth'

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/api/auth/login', request)
  return data
}

export async function fetchMe(): Promise<User> {
  const { data } = await apiClient.get<User>('/api/auth/me')
  return data
}
