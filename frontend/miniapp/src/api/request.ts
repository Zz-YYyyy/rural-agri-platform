import { useSessionStore } from '@/stores/session'

export interface ApiResponse<T> {
  code: 'OK'
  message: string
  data: T
  traceId: string
}

export interface FieldError {
  field: string
  message: string
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly status: number,
    readonly traceId?: string,
    readonly fieldErrors?: FieldError[],
    readonly retryAfterSeconds?: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

interface RequestOptions {
  path: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: Record<string, unknown>
  auth?: 'optional' | 'required' | 'none'
  idempotencyKey?: string
}

function baseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL?.trim().replace(/\/$/, '')
  // #ifdef H5
  if (!configured) return '/api/v1'
  if (/^\/(?!\/)/.test(configured)) return configured
  // #endif
  if (!configured || !/^https?:\/\//i.test(configured)) {
    throw new ApiError('请配置可访问的服务地址', 'API_NOT_CONFIGURED', 0)
  }
  return configured
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function fieldErrors(value: unknown): FieldError[] | undefined {
  if (!Array.isArray(value)) return undefined
  return value.filter((item): item is FieldError => isObject(item) && typeof item.field === 'string' && typeof item.message === 'string')
}

function retryAfter(header: Record<string, unknown>): number | undefined {
  const name = Object.keys(header).find((key) => key.toLowerCase() === 'retry-after')
  const value = name ? String(header[name]) : ''
  if (!/^\d+$/.test(value)) return undefined
  const seconds = Number(value)
  return Number.isSafeInteger(seconds) && seconds > 0 ? seconds : undefined
}

/** Resource paths are relative to /api/v1, e.g. /products. Mutations are never auto-retried. */
export async function request<T>(options: RequestOptions): Promise<ApiResponse<T>> {
  if (!/^\/(?!\/)/.test(options.path) || options.path.startsWith('/api/v1')) {
    throw new ApiError('接口路径格式错误', 'INVALID_REQUEST_PATH', 0)
  }
  const session = useSessionStore()
  session.expireIfNeeded()
  const token = options.auth === 'none' ? '' : session.accessToken
  if (options.auth === 'required' && !token) {
    throw new ApiError('请先登录后再操作', 'UNAUTHENTICATED', 401)
  }
  if (options.idempotencyKey && !/^[A-Za-z\d_-]{16,64}$/.test(options.idempotencyKey)) {
    throw new ApiError('请求标识格式错误', 'INVALID_IDEMPOTENCY_KEY', 0)
  }
  const header: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) header.Authorization = `Bearer ${token}`
  if (options.idempotencyKey) header['Idempotency-Key'] = options.idempotencyKey

  return new Promise((resolve, reject) => {
    uni.request({
      url: baseUrl() + options.path,
      method: options.method ?? 'GET',
      data: options.data,
      header,
      timeout: 15000,
      success(response) {
        const body: unknown = response.data
        if (response.statusCode === 401 && token && session.accessToken === token) session.clearCredential()
        if (!isObject(body)) {
          reject(new ApiError('服务返回格式异常，请稍后再试', 'INVALID_RESPONSE', response.statusCode))
          return
        }
        if (response.statusCode >= 200 && response.statusCode < 300) {
          if (body.code === 'OK' && 'data' in body && typeof body.message === 'string' && typeof body.traceId === 'string') {
            resolve(body as unknown as ApiResponse<T>)
          } else {
            reject(new ApiError('服务返回格式异常，请稍后再试', 'INVALID_RESPONSE', response.statusCode))
          }
          return
        }
        reject(new ApiError(
          typeof body.message === 'string' ? body.message : '请求未完成，请稍后再试',
          typeof body.code === 'string' ? body.code : 'INVALID_RESPONSE',
          response.statusCode,
          typeof body.traceId === 'string' ? body.traceId : undefined,
          fieldErrors(body.fieldErrors),
          retryAfter(response.header),
        ))
      },
      fail() {
        reject(new ApiError('网络连接失败，请稍后再试', 'NETWORK_ERROR', 0))
      },
    })
  })
}
