import { ApiError, request } from './request'

export interface AccountView {
  id: string
  username: string
  nickname: string
  avatarUrl: string
  isAdmin: boolean
  isFarmer: boolean
  farmerId?: string
  farmerStatus?: 'ACTIVE' | 'DISABLED'
  createdAt: string
}

export interface LoginInput {
  username: string
  password: string
}

export interface RegisterInput extends LoginInput {
  nickname: string
}

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: AccountView
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function accountFrom(value: unknown): AccountView {
  if (!isRecord(value) || typeof value.id !== 'string' || !/^[1-9]\d*$/.test(value.id)
    || typeof value.username !== 'string' || typeof value.nickname !== 'string'
    || typeof value.avatarUrl !== 'string' || typeof value.createdAt !== 'string'
    || typeof value.isAdmin !== 'boolean' || typeof value.isFarmer !== 'boolean'
    || (value.isFarmer && (typeof value.farmerId !== 'string' || !/^[1-9]\d*$/.test(value.farmerId)
      || !['ACTIVE', 'DISABLED'].includes(String(value.farmerStatus))))) {
    throw new ApiError('账号资料返回格式异常，请稍后重试', 'INVALID_RESPONSE', 200)
  }
  return value as unknown as AccountView
}

export async function register(input: RegisterInput): Promise<AccountView> {
  const { username, password, nickname } = input
  const response = await request<unknown>({ path: '/auth/register', method: 'POST', data: { username, password, nickname }, auth: 'none' })
  return accountFrom(response.data)
}

export async function login(input: LoginInput): Promise<TokenResponse> {
  const { username, password } = input
  const response = await request<unknown>({ path: '/auth/login', method: 'POST', data: { username, password }, auth: 'none' })
  const value = response.data
  if (!isRecord(value) || typeof value.accessToken !== 'string' || !value.accessToken
    || value.accessToken.length > 4096 || value.tokenType !== 'Bearer' || value.expiresIn !== 7200) {
    throw new ApiError('登录响应格式异常，请稍后重试', 'INVALID_RESPONSE', 200)
  }
  return { accessToken: value.accessToken, tokenType: 'Bearer', expiresIn: value.expiresIn, user: accountFrom(value.user) }
}

export async function currentAccount(): Promise<AccountView> {
  const response = await request<unknown>({ path: '/me', auth: 'required' })
  return accountFrom(response.data)
}

export async function logout(): Promise<void> {
  await request({ path: '/auth/logout', method: 'POST', auth: 'required' })
}
