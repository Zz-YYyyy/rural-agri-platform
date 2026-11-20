import axios, { AxiosHeaders } from 'axios'
import type { AxiosRequestConfig, RawAxiosHeaders } from 'axios'
import { pinia } from '../stores'
import { useSessionStore } from '../stores/session'

export interface FieldError {
  field: string
  message: string
}

export interface ApiEnvelope<T> {
  code: 'OK'
  message: string
  data: T
  traceId: string
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly status?: number,
    readonly traceId?: string,
    readonly fieldErrors: FieldError[] = [],
    readonly retryAfterSeconds?: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15_000,
  headers: { Accept: 'application/json' },
})

interface RequestIdentity {
  auth?: boolean
  token?: string
}

type ApiRequestConfig = Omit<AxiosRequestConfig, 'headers'> & { headers?: RawAxiosHeaders | AxiosHeaders }

/** 使用相对于 /api/v1 的业务路径；失败保留真实 HTTP 状态，不自动重试写操作。 */
export async function request<T>(config: ApiRequestConfig, identity: RequestIdentity = {}): Promise<T> {
  const token = identity.token ?? (identity.auth === false ? null : useSessionStore(pinia).accessToken)
  const headers = AxiosHeaders.from(config.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  else headers.delete('Authorization')
  try {
    const response = await http.request<unknown>({ ...config, headers })
    const body = response.data
    if (!isRecord(body) || body.code !== 'OK' || !('data' in body)
      || typeof body.message !== 'string' || typeof body.traceId !== 'string') {
      throw new ApiError('服务返回格式不符合接口约定', 'INVALID_RESPONSE', response.status)
    }
    return (body as unknown as ApiEnvelope<T>).data
  } catch (error: unknown) {
    if (!axios.isAxiosError(error)) throw error
    const status = error.response?.status
    if (status === 401) {
      const session = useSessionStore(pinia)
      const sentAuthorization = error.config?.headers.get('Authorization')
      const currentAuthorization = session.accessToken ? `Bearer ${session.accessToken}` : undefined
      // 旧请求的延迟 401 只能使它自己使用的会话失效，不能清掉后来重新登录的凭证。
      if (token && sentAuthorization === currentAuthorization) session.expireSession()
    }
    const body: unknown = error.response?.data
    const data = isRecord(body) ? body : {}
    const fieldErrors = Array.isArray(data.fieldErrors)
      ? data.fieldErrors.filter((item): item is FieldError => isRecord(item)
        && typeof item.field === 'string' && typeof item.message === 'string')
      : []
    const retryAfter = Number(error.response?.headers['retry-after'])
    const fallback = status === 401 ? '登录已失效，请重新登录'
      : status ? '请求未成功，请稍后重试' : '无法连接服务，请检查网络或服务地址'
    throw new ApiError(
      typeof data.message === 'string' ? data.message : fallback,
      typeof data.code === 'string' ? data.code : status ? 'HTTP_ERROR' : 'NETWORK_ERROR',
      status,
      typeof data.traceId === 'string' ? data.traceId : undefined,
      fieldErrors,
      Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined,
    )
  }
}
