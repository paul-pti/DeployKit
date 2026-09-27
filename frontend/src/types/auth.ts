export type Role = 'USER' | 'ADMIN'

export interface User {
  id: string
  email: string
  role: Role
  createdAt: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  /** Lifetime of the token, in seconds. */
  expiresIn: number
  user: User
}

export interface CreateUserRequest {
  email: string
  password: string
  role: Role
}
