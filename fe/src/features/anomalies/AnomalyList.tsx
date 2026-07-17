import type { Anomaly, Meta } from '../../api/types'
import { SeverityBadge } from '../../components/RiskBadge'

const yen = (v: number | null) => (v === null ? '—' : `¥${v.toLocaleString('ja-JP')}`)

export interface AnomalyFilter {
  severity?: string
  ruleCode?: string
  resolved?: string
}

/**
 * 異常値警告セクション（計画書 10）。
 *
 * ルールIDと検出根拠を必ず並べる。「なぜこの警告が出たか」が画面から読めないと、
 * 経理は結局CSVを開き直すことになり、自動化の意味が薄れる。
 */
export function AnomalyList({
  anomalies,
  meta,
  filter,
  onFilterChange,
  onSelect,
}: {
  anomalies: Anomaly[]
  meta: Meta | null
  filter: AnomalyFilter
  onFilterChange: (next: AnomalyFilter) => void
  onSelect: (expenseId: string) => void
}) {
  return (
    <section aria-label="異常値警告">
      <div className="legend">
        <span className="badge badge-red">■ 要対応</span>
        <span>処理不能または重大。解決するまで承認できません。</span>
        <span className="badge badge-yellow">▲ 要確認</span>
        <span>人による確認が必要。確認のうえ承認できます。</span>
      </div>

      <div className="filters">
        <label>
          重要度
          <select value={filter.severity ?? ''} onChange={(e) => onFilterChange({ ...filter, severity: e.target.value })}>
            <option value="">すべて</option>
            <option value="RED">要対応のみ</option>
            <option value="YELLOW">要確認のみ</option>
          </select>
        </label>

        <label>
          ルール
          <select value={filter.ruleCode ?? ''} onChange={(e) => onFilterChange({ ...filter, ruleCode: e.target.value })}>
            <option value="">すべて</option>
            {meta?.rules.map((r) => (
              <option key={r.code} value={r.code}>
                {r.code}
              </option>
            ))}
          </select>
        </label>

        <label>
          解決状態
          <select value={filter.resolved ?? ''} onChange={(e) => onFilterChange({ ...filter, resolved: e.target.value })}>
            <option value="">すべて</option>
            <option value="false">未解決のみ</option>
            <option value="true">解決済みのみ</option>
          </select>
        </label>

        <button type="button" onClick={() => onFilterChange({})}>
          条件をクリア
        </button>
      </div>

      <ul className="anomaly-list">
        {anomalies.map((a) => (
          <li key={a.issueId} className={`anomaly ${a.resolved ? 'anomaly-resolved' : ''}`}>
            <div className="anomaly-head">
              <SeverityBadge severity={a.severity} label={a.severityLabel} />
              <code className="rule-code">{a.ruleCode}</code>
              {a.resolved && <span className="resolved-tag">確認済み</span>}
              <button type="button" className="link" onClick={() => onSelect(a.expenseId)}>
                明細を開く
              </button>
            </div>

            <div className="anomaly-target">
              {a.usageDate ?? '日付不明'} / {a.departmentLabel} / {a.applicantName ?? '申請者不明'} /{' '}
              {a.categoryLabel ?? '未分類'} / {yen(a.amountYen)}
              {a.sourceRowNumber !== null && <span className="row-ref"> （元CSV {a.sourceRowNumber}行目）</span>}
            </div>

            <div className="anomaly-message">{a.message}</div>
            {a.evidence && <div className="anomaly-evidence">根拠: {a.evidence}</div>}
          </li>
        ))}
        {anomalies.length === 0 && <li className="empty">該当する警告がありません</li>}
      </ul>
    </section>
  )
}
