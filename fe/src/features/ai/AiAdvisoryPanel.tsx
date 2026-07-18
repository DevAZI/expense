import { useState } from 'react'
import { api, ApiRequestError } from '../../api/client'
import type { AiAdvisoryResult, AiSeverity } from '../../api/types'

const yen = (v: number | null) => (v === null ? '—' : `¥${v.toLocaleString('ja-JP')}`)

const AI_SEVERITY: Record<AiSeverity, { mark: string; text: string; className: string }> = {
  HIGH: { mark: '●', text: '要確認(高)', className: 'ai-badge ai-badge-high' },
  MEDIUM: { mark: '●', text: '要確認(中)', className: 'ai-badge ai-badge-medium' },
  LOW: { mark: '●', text: '参考', className: 'ai-badge ai-badge-low' },
}

const ANOMALY_TYPE_LABEL: Record<string, string> = {
  DIGIT_ERROR: '桁誤りの疑い',
  SPLIT_TRANSACTION: '分割申請の疑い',
  CATEGORY_MISMATCH: '費目と内容の不整合',
  BUSINESS_PURPOSE_CONFIRMATION: '業務目的の確認',
  PRIVATE_USE_SUSPICION: '私的利用の疑い',
  TIME_PATTERN: '日時パターンの確認',
  OTHER: 'その他',
}

/**
 * AI補助（異常検知・誤字脱字チェック）。決定的検知の「警告一覧」とは別系統で、
 * ここが出すのはAIの<b>助言</b>。DBには保存されず承認判定にも影響しない参考情報。
 *
 * 実行は明示ボタンでのみ。取込のたびに自動で呼ぶとレイテンシ・費用・失敗が
 * 取込フローに乗るため、経理が必要なときだけ回せるようにしている。
 */
export function AiAdvisoryPanel({ onSelect }: { onSelect: (expenseId: string) => void }) {
  const [result, setResult] = useState<AiAdvisoryResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function run() {
    setBusy(true)
    setError(null)
    try {
      // 未承認（PENDING）の明細だけを対象にする。承認済みを再分析しても意味が薄いため。
      const r = await api.aiAdvisories({})
      setResult(r)
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="AI補助">
      <div className="legend">
        <span>
          AIが未承認の明細を読み、<strong>機械ルールでは拾いにくい異常の候補</strong>と
          <strong>誤字脱字の候補</strong>を挙げます。これは参考情報で、承認・却下の判定には影響しません。
        </span>
      </div>

      <div className="ai-run">
        <button type="button" className="ai-run-button" onClick={() => void run()} disabled={busy}>
          {busy ? 'AIが分析中…（数秒かかります）' : 'AIで未承認の明細を分析'}
        </button>
        {result && result.model && (
          <span className="ai-run-hint">
            モデル: <code>{result.model}</code>
          </span>
        )}
      </div>

      {error && (
        <p className="error-text" role="alert">
          {error}
        </p>
      )}

      {result && (
        <div className="ai-result">
          {!result.available ? (
            <p className="ai-unavailable" role="status">
              {result.message}
            </p>
          ) : (
            <>
              <p className="ai-summary" role="status">
                {result.analyzedCount}件の明細を分析しました（異常候補 {result.anomalies.length}件 / 誤字候補{' '}
                {result.typos.length}件）。
              </p>

              <h3 className="ai-section-title">異常の候補</h3>
              <ul className="anomaly-list">
                {result.anomalies.map((a, i) => {
                  const sev = AI_SEVERITY[a.severity]
                  return (
                    <li key={`a-${a.expenseId}-${i}`} className="anomaly">
                      <div className="anomaly-head">
                        <span className={sev.className}>
                          <span aria-hidden="true">{sev.mark}</span> {sev.text}
                        </span>
                        <code className="rule-code">{ANOMALY_TYPE_LABEL[a.anomalyType] ?? a.anomalyType}</code>
                        <button type="button" className="link" onClick={() => onSelect(a.expenseId)}>
                          明細を開く
                        </button>
                      </div>
                      <div className="anomaly-target">
                        {a.department} / {a.applicantName ?? '申請者不明'} / {a.category ?? '未分類'} / {yen(a.amountYen)}
                        <span className="row-ref"> （元CSV {a.sourceRowNumber}行目）</span>
                      </div>
                      <div className="anomaly-message">{a.reason}</div>
                      {a.relatedRowNumbers.length > 0 && (
                        <div className="anomaly-evidence">
                          関連する明細: 元CSV {a.relatedRowNumbers.join(', ')}行目
                        </div>
                      )}
                    </li>
                  )
                })}
                {result.anomalies.length === 0 && <li className="empty">異常の候補はありませんでした</li>}
              </ul>

              <h3 className="ai-section-title">誤字脱字の候補</h3>
              <ul className="anomaly-list">
                {result.typos.map((t, i) => (
                  <li key={`t-${t.expenseId}-${i}`} className="anomaly">
                    <div className="anomaly-head">
                      <code className="rule-code">{t.field}</code>
                      <button type="button" className="link" onClick={() => onSelect(t.expenseId)}>
                        明細を開く
                      </button>
                    </div>
                    <div className="anomaly-target">
                      <span className="ai-typo-original">{t.original}</span>
                      <span aria-hidden="true"> → </span>
                      <span className="ai-typo-suggestion">{t.suggestion}</span>
                      <span className="row-ref"> （元CSV {t.sourceRowNumber}行目）</span>
                    </div>
                    <div className="anomaly-message">{t.reason}</div>
                  </li>
                ))}
                {result.typos.length === 0 && <li className="empty">誤字脱字の候補はありませんでした</li>}
              </ul>

              <p className="ai-disclaimer">
                ※ AIの提示は確認のための候補です。修正は自動では行われません。必ず原本で確認してください。
              </p>
            </>
          )}
        </div>
      )}
    </section>
  )
}
