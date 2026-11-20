import { currentAccount, login, logout } from '@/api/auth'
import type { AccountView, LoginInput } from '@/api/auth'
import { ApiError } from '@/api/request'
import { useSessionStore } from '@/stores/session'

let pendingVerification: { token: string; promise: Promise<AccountView | null> } | null = null
let loginSequence = 0

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 429 && error.retryAfterSeconds) {
      return `${error.message}，请在 ${error.retryAfterSeconds} 秒后重试`
    }
    if (error.code === 'NETWORK_ERROR' || error.code === 'API_NOT_CONFIGURED') {
      return '暂时无法连接账号服务，请检查服务地址或稍后重试'
    }
    return error.message
  }
  return error instanceof Error ? error.message : '操作暂未完成，请稍后重试'
}

export async function verifySession(): Promise<AccountView | null> {
  const session = useSessionStore()
  session.expireIfNeeded()
  const token = session.accessToken
  if (!token) return null
  if (pendingVerification?.token === token) return pendingVerification.promise
  session.beginVerification(token)
  const promise = (async () => {
    try {
      const account = await currentAccount()
      return session.verifiedAccount(token, account) ? account : null
    } catch (error) {
      if (session.accessToken === token) {
        if (error instanceof ApiError && (error.status === 401 || error.code === 'ACCOUNT_DISABLED')) {
          session.clearCredential()
        } else {
          session.verificationFailed(token, errorMessage(error))
        }
      }
      throw error
    } finally {
      if (pendingVerification?.token === token) pendingVerification = null
    }
  })()
  pendingVerification = { token, promise }
  return promise
}

export async function restoreSession(): Promise<void> {
  const session = useSessionStore()
  session.restoreCredential()
  try { await verifySession() } catch { /* My page offers retry; visitors can keep browsing. */ }
}

export async function signIn(input: LoginInput): Promise<boolean> {
  const sequence = ++loginSequence
  const response = await login(input)
  if (sequence !== loginSequence) return false
  const session = useSessionStore()
  session.setCredential(response.accessToken, response.expiresIn)
  // /me is the authoritative identity for restoring sessions and role-based UI.
  return Boolean(await verifySession())
}

export async function signOut(): Promise<void> {
  ++loginSequence
  const session = useSessionStore()
  const token = session.accessToken
  try {
    if (token) await logout()
  } finally {
    if (session.accessToken === token) session.clearCredential()
  }
}
