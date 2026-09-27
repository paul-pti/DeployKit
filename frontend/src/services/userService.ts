import { apiClient } from '../lib/apiClient'
import type { CreateUserRequest, User } from '../types/auth'

export async function listUsers(): Promise<User[]> {
  const { data } = await apiClient.get<User[]>('/api/users')
  return data
}

export async function createUser(request: CreateUserRequest): Promise<User> {
  const { data } = await apiClient.post<User>('/api/users', request)
  return data
}
