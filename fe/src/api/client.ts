import type {
  Anomaly,
  ApiError,
  ExpenseDetail,
  Expense,
  ExpenseStatus,
  ImportResult,
  Meta,
  Page,
  ResetResult,
  Summary,
} from './types'

/**
 * バックエンドが返すエラー本文を握り潰さないための例外。
 * 「承認できません(INVALID_DATE)」のような業務上の理由をそのまま画面に出す。
 */
export class ApiRequestError extends Error {
  readonly code: string
  readonly status: number

  constructor(status: number, body: ApiError | null, fallback: string) {
    super(body?.message ?? fallback)
    this.code = body?.code ?? 'UNKNOWN'
    this.status = status
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)

  if (!response.ok) {
    let body: ApiError | null = null
    try {
      body = (await response.json()) as ApiError
    } catch {
      // エラー本文がJSONでない場合はステータスだけで諦める
    }
    throw new ApiRequestError(response.status, body, `${response.status} ${response.statusText}`)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export interface ExpenseFilter {
  department?: string
  status?: string
  riskLevel?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

function toQuery(params: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      query.set(key, String(value))
    }
  })
  const s = query.toString()
  return s ? `?${s}` : ''
}

export const api = {
  meta: () => request<Meta>('/api/meta'),

  summary: () => request<Summary>('/api/expenses/summary'),

  expenses: (filter: ExpenseFilter) =>
    request<Page<Expense>>(`/api/expenses${toQuery({ ...filter, sort: undefined })}`),

  expenseDetail: (id: string) => request<ExpenseDetail>(`/api/expenses/${id}`),

  anomalies: (params: { severity?: string; ruleCode?: string; resolved?: string }) =>
    request<Anomaly[]>(`/api/anomalies${toQuery(params)}`),

  /**
   * @param targetYearMonth "2026-06" 形式。省略するとサーバー設定の既定値が使われる。
   *                        対象月は期間外判定だけでなく、年省略日付の年の補完にも効く。
   */
  upload: (file: File, targetYearMonth?: string, department?: string) => {
    const form = new FormData()
    form.append('file', file)
    if (targetYearMonth) {
      form.append('targetYearMonth', targetYearMonth)
    }
    if (department) {
      form.append('department', department)
    }
    return request<ImportResult>('/api/imports', { method: 'POST', body: form })
  },

  review: (
    id: string,
    payload: {
      decision: ExpenseStatus
      reason?: string
      reviewedBy: string
      overrideRedWarnings: boolean
    },
  ) =>
    request<Expense>(`/api/expenses/${id}/review`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),

  /** 全データを削除する。承認履歴も消える。呼ぶ前に必ず確認を取ること。 */
  resetAll: () => request<ResetResult>('/api/imports', { method: 'DELETE' }),

  /** 取込ファイル1件分だけ取り消す。 */
  resetImportFile: (id: string) =>
    request<ResetResult>(`/api/imports/${id}`, { method: 'DELETE' }),

  exportUrl: () => '/api/expenses/export',
}
