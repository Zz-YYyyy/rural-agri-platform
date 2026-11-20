import { ApiError } from '@/api/request'

export function normalizeUsername(value: string): string {
  return value.trim().toLowerCase()
}

export function usernameError(value: string): string {
  return /^[a-z][a-z0-9_]{3,31}$/.test(normalizeUsername(value)) ? '' : '账号需为 4～32 位，以英文字母开头，可含数字和下划线'
}

export function passwordError(value: string): string {
  const characters = Array.from(value)
  let bytes = 0
  for (const character of characters) {
    const point = character.codePointAt(0) ?? 0
    if (point >= 0xd800 && point <= 0xdfff) return '密码包含无效字符'
    bytes += point <= 0x7f ? 1 : point <= 0x7ff ? 2 : point <= 0xffff ? 3 : 4
  }
  return characters.length >= 8 && characters.length <= 72 && bytes <= 72
    ? '' : '密码需至少 8 个字符，UTF-8 长度不超过 72 字节；中文通常每字占 3 字节'
}

export function responseFieldErrors(error: unknown): Record<string, string> {
  const result: Record<string, string> = {}
  if (error instanceof ApiError) {
    for (const field of error.fieldErrors ?? []) {
      if (['username', 'password', 'nickname'].includes(field.field)) result[field.field] = field.message
    }
  }
  return result
}

const RETURN_PAGES = new Set([
  '/pages/home/index', '/pages/category/index', '/pages/assistance/index',
  '/pages/cart/index', '/pages/me/index', '/pages/ai/index',
])

export function safeReturnPage(value: unknown): string {
  if (typeof value !== 'string') return '/pages/me/index'
  try {
    const path = decodeURIComponent(value)
    return RETURN_PAGES.has(path) ? path : '/pages/me/index'
  } catch { return '/pages/me/index' }
}

export function goAfterLogin(target: string): void {
  const url = safeReturnPage(target)
  if (url === '/pages/ai/index') uni.redirectTo({ url })
  else uni.switchTab({ url })
}
