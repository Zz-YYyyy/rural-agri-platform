import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { AccountView } from '../api/auth'

const STORAGE_KEY = 'rural-agri.admin.session'

interface SavedCredential {
  accessToken: string
  expiresAt: number
}

function readCredential(): SavedCredential | null {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null')
    if (typeof value === 'object' && value !== null && 'accessToken' in value && 'expiresAt' in value
      && typeof value.accessToken === 'string' && value.accessToken.length > 0 && value.accessToken.length <= 4096
      && typeof value.expiresAt === 'number' && Number.isFinite(value.expiresAt)
      && value.expiresAt > Date.now() && value.expiresAt <= Date.now() + 7_200_000) {
      return { accessToken: value.accessToken, expiresAt: value.expiresAt }
    }
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // Storage may be unavailable. A fresh login can still use an in-memory session.
  }
  return null
}

export const useSessionStore = defineStore('admin-session', () => {
  const saved = readCredential()
  const accessToken = ref<string | null>(saved?.accessToken ?? null)
  const expiresAt = ref(saved?.expiresAt ?? 0)
  // Roles are never restored from browser storage. /me must confirm them each time.
  const user = ref<AccountView | null>(null)
  const verifiedToken = ref<string | null>(null)
  const checking = ref(false)
  const verificationFailed = ref(false)
  const sessionExpired = ref(false)
  const notice = ref('')
  const noticeType = ref<'warning' | 'error' | 'success' | 'info'>('info')
  const isVerifiedAdmin = computed(() => Boolean(accessToken.value && verifiedToken.value === accessToken.value && user.value?.isAdmin))
  let expiryTimer: ReturnType<typeof setTimeout> | undefined

  function persist() {
    try {
      if (accessToken.value) sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ accessToken: accessToken.value, expiresAt: expiresAt.value }))
      else sessionStorage.removeItem(STORAGE_KEY)
    } catch {
      // Do not replace the current successful login with a storage error.
    }
  }

  function scheduleExpiry() {
    if (expiryTimer) clearTimeout(expiryTimer)
    if (accessToken.value) expiryTimer = setTimeout(expireSession, Math.max(0, expiresAt.value - Date.now()))
  }

  function setCredential(token: string, lifetimeSeconds: number) {
    accessToken.value = token
    expiresAt.value = Date.now() + lifetimeSeconds * 1000
    user.value = null
    verifiedToken.value = null
    verificationFailed.value = false
    sessionExpired.value = false
    persist()
    scheduleExpiry()
  }

  function clearCredential(expectedToken?: string) {
    if (expectedToken && accessToken.value !== expectedToken) return
    accessToken.value = null
    expiresAt.value = 0
    user.value = null
    verifiedToken.value = null
    checking.value = false
    verificationFailed.value = false
    if (expiryTimer) clearTimeout(expiryTimer)
    persist()
  }

  function expireSession() {
    clearCredential()
    sessionExpired.value = true
    showNotice('登录状态已失效，请重新登录。', 'warning')
  }

  function beginVerification(token: string) {
    if (accessToken.value !== token) return
    checking.value = true
    verificationFailed.value = false
    verifiedToken.value = null
    user.value = null
  }

  function confirmAccount(token: string, account: AccountView) {
    if (accessToken.value !== token || expiresAt.value <= Date.now()) return false
    user.value = account
    verifiedToken.value = token
    checking.value = false
    verificationFailed.value = false
    sessionExpired.value = false
    return true
  }

  function failVerification(token: string) {
    if (accessToken.value !== token) return
    checking.value = false
    verifiedToken.value = null
    user.value = null
    verificationFailed.value = true
  }

  function showNotice(message: string, type: typeof noticeType.value = 'info') {
    notice.value = message
    noticeType.value = type
  }

  scheduleExpiry()
  return {
    accessToken, expiresAt, user, checking, verificationFailed, isVerifiedAdmin,
    sessionExpired, notice, noticeType, setCredential, clearCredential, expireSession,
    beginVerification, confirmAccount, failVerification, showNotice,
  }
})
