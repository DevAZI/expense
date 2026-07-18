/** バックエンドのDTOに対応する型。手で写しているので、API側を変えたらここも直す。 */

export type Severity = 'RED' | 'YELLOW'
export type RiskLevel = 'RED' | 'YELLOW' | 'NONE'
export type ExpenseStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface Expense {
  id: string
  usageDate: string | null
  department: string | null
  departmentLabel: string | null
  applicantName: string | null
  category: string | null
  categoryLabel: string | null
  categoryGroup: string | null
  categoryGroupLabel: string | null
  amountYen: number | null
  note: string | null
  status: ExpenseStatus
  statusLabel: string
  riskLevel: RiskLevel
  sourceFileId: string
  sourceRowNumber: number
  reviewReason: string | null
  reviewedBy: string | null
  reviewedAt: string | null
  createdAt: string
}

export interface Issue {
  id: string
  expenseId: string
  ruleCode: string
  severity: Severity
  severityLabel: string
  message: string
  evidence: string | null
  resolved: boolean
}

export interface RawValues {
  rowNumber: number | null
  rawLine: string | null
  usageDate: string | null
  applicantName: string | null
  category: string | null
  amount: string | null
  note: string | null
}

export interface ExpenseDetail {
  expense: Expense
  raw: RawValues | null
  issues: Issue[]
}

export interface Anomaly {
  issueId: string
  expenseId: string
  ruleCode: string
  severity: Severity
  severityLabel: string
  message: string
  evidence: string | null
  resolved: boolean
  usageDate: string | null
  departmentLabel: string | null
  applicantName: string | null
  categoryLabel: string | null
  amountYen: number | null
  note: string | null
  statusLabel: string | null
  sourceRowNumber: number | null
}

/** AI補助（助言専用・非ブロッキング）。RED/YELLOW とは別体系で承認判定には影響しない。 */
export type AiSeverity = 'HIGH' | 'MEDIUM' | 'LOW'

export interface AiAnomaly {
  expenseId: string
  sourceRowNumber: number
  department: string | null
  applicantName: string | null
  category: string | null
  amountYen: number | null
  severity: AiSeverity
  anomalyType: string
  reason: string
  relatedRowNumbers: number[]
}

export interface AiTypo {
  expenseId: string
  sourceRowNumber: number
  field: string
  original: string
  suggestion: string
  reason: string
}

export interface AiAdvisoryResult {
  enabled: boolean
  available: boolean
  model: string | null
  message: string
  analyzedCount: number
  anomalies: AiAnomaly[]
  typos: AiTypo[]
}

export interface Breakdown {
  code: string
  label: string
  totalYen: number
  count: number
}

export interface Summary {
  approvedTotalYen: number
  approvedCount: number
  pendingCount: number
  rejectedCount: number
  unresolvedRedCount: number
  excludedCount: number
  totalCount: number
  byDepartment: Breakdown[]
  byCategory: Breakdown[]
  byCategoryGroup: Breakdown[]
}

export interface ImportResult {
  importFileId: string
  fileName: string
  status: 'COMPLETED' | 'FAILED'
  charset: string | null
  department: string | null
  departmentLabel: string | null
  targetYearMonth: string | null
  rowCount: number
  redCount: number
  yellowCount: number
  errorSummary: string | null
  sameHashImportFileIds: string[]
}

export interface BulkApproveResult {
  approvedCount: number
}

export interface ResetResult {
  importFiles: number
  rawRows: number
  expenses: number
  issues: number
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface Option {
  code: string
  label: string
}

export interface RuleOption extends Option {
  severity: Severity
  description: string
}

export interface Meta {
  defaultTargetYearMonth: string
  departments: Option[]
  categories: Option[]
  statuses: Option[]
  riskLevels: Option[]
  rules: RuleOption[]
}

export interface ApiError {
  code: string
  message: string
  timestamp: string
}
