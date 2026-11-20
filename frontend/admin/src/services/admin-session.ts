import { getCurrentAccount, loginAccount, logoutAccount } from '../api/auth'
import { ApiError } from '../api/http'
import { pinia } from '../stores'
import { useSessionStore } from '../stores/session'
import { modules } from '../modules'

let pendingVerification: { token: string; promise: Promise<boolean> } | undefined

/** Only known local module routes are accepted as post-login destinations. */
export function safeReturnPath(value: unknown): string {
  return typeof value === 'string' && modules.some((item) => item.path === value) ? value : '/dashboard'
}

export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    const wait = error.retryAfterSeconds ? ` 请在 ${error.retryAfterSeconds} 秒后重试。` : ''
    const trace = error.traceId ? `（排查编号：${error.traceId}）` : ''
    return `${error.message}${wait}${trace}`
  }
  return '暂时无法完成操作，请稍后重试。'
}

async function rejectNonAdmin(token: string): Promise<never> {
  const session = useSessionStore(pinia)
  let revokeConfirmed = true
  try {
    await logoutAccount(token)
  } catch (error) {
    revokeConfirmed = error instanceof ApiError && error.status === 401
  }
  session.clearCredential(token)
  const suffix = revokeConfirmed ? '' : ' 本机未保留凭证，但服务端会话退出暂未确认。'
  throw new ApiError(`该账号没有管理员权限，请使用管理员账号登录。${suffix}`, 'ADMIN_REQUIRED', 403)
}

export async function signInAdmin(username: string, password: string): Promise<void> {
  const session = useSessionStore(pinia)
  const normalized = username.trim().toLowerCase()
  if (!/^[a-z][a-z0-9_]{3,31}$/.test(normalized)) {
    throw new ApiError('账号需为 4～32 位，以英文字母开头，可含数字和下划线。', 'VALIDATION_ERROR', 400)
  }
  if (password.length < 8 || password.length > 72 || new TextEncoder().encode(password).length > 72) {
    throw new ApiError('密码需为 8～72 个字符，UTF-8 编码不超过 72 字节。', 'VALIDATION_ERROR', 400)
  }
  const result = await loginAccount(normalized, password)
  // A login response alone does not grant access. Read fresh server-side capabilities.
  let profile
  try {
    profile = await getCurrentAccount(result.accessToken)
  } catch (error) {
    try { await logoutAccount(result.accessToken) } catch { /* No browser credential has been retained. */ }
    throw error
  }
  if (!profile.isAdmin) await rejectNonAdmin(result.accessToken)
  if (profile.id !== result.user.id) {
    try { await logoutAccount(result.accessToken) } catch { /* Do not retain mismatched credentials. */ }
    throw new ApiError('登录账号与身份校验结果不一致，请重新登录。', 'INVALID_RESPONSE')
  }
  session.setCredential(result.accessToken, result.expiresIn)
  session.confirmAccount(result.accessToken, profile)
  session.showNotice('')
}

export async function ensureAdminSession(): Promise<boolean> {
  const session = useSessionStore(pinia)
  const token = session.accessToken
  if (!token) return false
  if (session.expiresAt <= Date.now()) {
    session.expireSession()
    return false
  }
  if (pendingVerification?.token === token) return pendingVerification.promise
  const promise = (async () => {
    session.beginVerification(token)
    try {
      const profile = await getCurrentAccount(token)
      if (session.accessToken !== token) return false
      if (!profile.isAdmin) await rejectNonAdmin(token)
      return session.confirmAccount(token, profile)
    } catch (error) {
      session.failVerification(token)
      throw error
    }
  })()
  pendingVerification = { token, promise }
  try {
    return await promise
  } finally {
    if (pendingVerification?.promise === promise) pendingVerification = undefined
  }
}

export async function signOutAdmin(): Promise<void> {
  const session = useSessionStore(pinia)
  const token = session.accessToken
  if (!token) return
  try {
    await logoutAccount(token)
    if (session.accessToken === token) session.showNotice('已退出当前账号。', 'success')
  } catch (error) {
    if (session.accessToken === token || !session.accessToken) {
      const expired = error instanceof ApiError && error.status === 401
      session.showNotice(expired ? '登录状态已失效，请重新登录。'
        : `已清除本机登录状态，服务端退出未确认完成。${describeError(error)}`, 'warning')
    }
  } finally {
    session.clearCredential(token)
  }
}
