import { ApiError, request } from './http'

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

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: 7200
  user: AccountView
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function account(value: unknown): AccountView {
  if (!isRecord(value) || typeof value.id !== 'string' || !/^[1-9]\d*$/.test(value.id)
    || typeof value.username !== 'string' || typeof value.nickname !== 'string'
    || typeof value.avatarUrl !== 'string' || typeof value.isAdmin !== 'boolean'
    || typeof value.isFarmer !== 'boolean' || typeof value.createdAt !== 'string') {
    throw new ApiError('账号资料返回格式异常，请稍后重试', 'INVALID_RESPONSE')
  }
  return value as unknown as AccountView
}

export async function loginAccount(username: string, password: string): Promise<TokenResponse> {
  const value = await request<unknown>({
    method: 'POST', url: '/auth/login', data: { username, password },
  }, { auth: false })
  if (!isRecord(value) || typeof value.accessToken !== 'string' || value.accessToken.length === 0
    || value.accessToken.length > 4096 || value.tokenType !== 'Bearer' || value.expiresIn !== 7200) {
    throw new ApiError('登录结果返回格式异常，请稍后重试', 'INVALID_RESPONSE')
  }
  return { accessToken: value.accessToken, tokenType: 'Bearer', expiresIn: 7200, user: account(value.user) }
}

export async function getCurrentAccount(token: string): Promise<AccountView> {
  return account(await request<unknown>({ method: 'GET', url: '/me' }, { token }))
}

export function logoutAccount(token: string): Promise<Record<string, never>> {
  return request({ method: 'POST', url: '/auth/logout' }, { token })
}
