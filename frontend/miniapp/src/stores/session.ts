import { defineStore } from 'pinia'
import type { AccountView } from '@/api/auth'

const TOKEN_KEY = 'rural-agri-access-token'
const EXPIRY_KEY = 'rural-agri-access-expires-at'

export const useSessionStore = defineStore('session', {
  state: () => ({
    accessToken: '',
    expiresAt: 0,
    account: null as AccountView | null,
    checking: false,
    verificationError: '',
  }),
  getters: {
    authenticated: (state) => Boolean(state.accessToken && state.account && state.expiresAt > Date.now()),
  },
  actions: {
    restoreCredential() {
      try {
        const token: unknown = uni.getStorageSync(TOKEN_KEY)
        const expiresAt: unknown = uni.getStorageSync(EXPIRY_KEY)
        if (typeof token === 'string' && token.length > 0 && token.length <= 4096
          && typeof expiresAt === 'number' && Number.isFinite(expiresAt) && expiresAt > Date.now()) {
          this.accessToken = token
          this.expiresAt = expiresAt
          this.account = null
          this.verificationError = ''
          return
        }
      } catch {
        // Storage availability never provides authorization.
      }
      this.clearCredential()
    },
    setCredential(token: string, expiresIn: number) {
      const expiresAt = Date.now() + expiresIn * 1000
      this.accessToken = token
      this.expiresAt = expiresAt
      this.account = null
      this.verificationError = ''
      try {
        uni.setStorageSync(TOKEN_KEY, token)
        uni.setStorageSync(EXPIRY_KEY, expiresAt)
      } catch {
        this.clearCredential()
        throw new Error('无法保存登录状态，请检查本机存储设置')
      }
    },
    expireIfNeeded() {
      if (this.accessToken && this.expiresAt <= Date.now()) this.clearCredential()
    },
    beginVerification(token: string) {
      if (this.accessToken !== token) return
      this.checking = true
      this.account = null
      this.verificationError = ''
    },
    verifiedAccount(token: string, account: AccountView) {
      this.expireIfNeeded()
      if (this.accessToken !== token) return false
      this.account = account
      this.checking = false
      this.verificationError = ''
      return true
    },
    verificationFailed(token: string, message: string) {
      if (this.accessToken !== token) return
      this.account = null
      this.checking = false
      this.verificationError = message
    },
    clearCredential() {
      this.accessToken = ''
      this.expiresAt = 0
      this.account = null
      this.checking = false
      this.verificationError = ''
      try {
        uni.removeStorageSync(TOKEN_KEY)
        uni.removeStorageSync(EXPIRY_KEY)
      } catch {
        // The in-memory session is still revoked locally even when storage is unavailable.
      }
    },
  },
})
