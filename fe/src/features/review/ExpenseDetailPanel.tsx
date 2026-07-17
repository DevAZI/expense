import { useEffect, useState } from 'react'
import { api, ApiRequestError } from '../../api/client'
import type { ExpenseDetail, ExpenseStatus } from '../../api/types'
import { RiskBadge, SeverityBadge } from '../../components/RiskBadge'

const yen = (v: number | null) => (v === null ? '—' : `¥${v.toLocaleString('ja-JP')}`)

/**
 * 明細詳細・レビュー（計画書 10）。
 *
 * 原文と正規化値を並べて出す。「令和8年6月2日」が「2026-06-02」になった、
 * 「１２８００」が「12800」になった、という変換をその場で確かめられないと、
 * 経理はツールの出した数字を信用できない。
 */
export function ExpenseDetailPanel({
  expenseId,
  onClose,
  onReviewed,
}: {
  expenseId: string
  onClose: () => void
  onReviewed: () => void
}) {
  const [detail, setDetail] = useState<ExpenseDetail | null>(null)
  const [reason, setReason] = useState('')
  const [reviewedBy, setReviewedBy] = useState('経理担当')
  const [override, setOverride] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // 明細ごとの入力状態（理由・例外承認チェック）は App 側の key={expenseId} による
  // 再マウントでリセットされる。ここで setState して消すと、別の明細に付けた理由が
  // 一瞬残る・カスケードレンダリングが起きる、の両方を招く。
  useEffect(() => {
    let cancelled = false
    api
      .expenseDetail(expenseId)
      .then((d) => {
        // 素早く選択を切り替えたとき、古いレスポンスで新しい選択を上書きしない
        if (!cancelled) setDetail(d)
      })
      .catch((e) => {
        if (!cancelled) setError(String(e))
      })
    return () => {
      cancelled = true
    }
  }, [expenseId])

  async function review(decision: ExpenseStatus) {
    setBusy(true)
    setError(null)
    try {
      await api.review(expenseId, { decision, reason, reviewedBy, overrideRedWarnings: override })
      const refreshed = await api.expenseDetail(expenseId)
      setDetail(refreshed)
      onReviewed()
    } catch (e) {
      // 未解決REDを例外承認なしで承認した場合の409はここに来る。
      // 理由をそのまま出して、次に何をすべきか分かるようにする。
      setError(e instanceof ApiRequestError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  if (!detail) {
    return (
      <aside className="detail-panel" aria-label="明細詳細">
        <button type="button" className="close" onClick={onClose} aria-label="閉じる">
          ×
        </button>
        <p>{error ?? '読み込み中…'}</p>
      </aside>
    )
  }

  const e = detail.expense
  const hasUnresolvedRed = detail.issues.some((i) => i.severity === 'RED' && !i.resolved)

  return (
    <aside className="detail-panel" aria-label="明細詳細">
      <button type="button" className="close" onClick={onClose} aria-label="閉じる">
        ×
      </button>

      <h2>
        明細詳細 <RiskBadge level={e.riskLevel} />
      </h2>
      <p className="source-ref">
        取込元: 元CSV {e.sourceRowNumber}行目 / 現在の状態: <strong>{e.statusLabel}</strong>
      </p>

      <h3>原文と正規化後の対比</h3>
      <table className="compare">
        <thead>
          <tr>
            <th scope="col">項目</th>
            <th scope="col">CSVの原文</th>
            <th scope="col">正規化後</th>
          </tr>
        </thead>
        <tbody>
          <CompareRow label="利用日" raw={detail.raw?.usageDate} normalized={e.usageDate} />
          <CompareRow label="申請者" raw={detail.raw?.applicantName} normalized={e.applicantName} />
          <CompareRow label="費目" raw={detail.raw?.category} normalized={e.categoryLabel} />
          <CompareRow label="金額" raw={detail.raw?.amount} normalized={e.amountYen === null ? null : yen(e.amountYen)} />
          <CompareRow label="備考" raw={detail.raw?.note} normalized={e.note} />
        </tbody>
      </table>

      {detail.raw?.rawLine && (
        <details className="raw-line">
          <summary>元CSVの行をそのまま見る</summary>
          <pre>{detail.raw.rawLine}</pre>
        </details>
      )}

      <h3>警告 {detail.issues.length > 0 && `(${detail.issues.length}件)`}</h3>
      {detail.issues.length === 0 ? (
        <p className="empty">警告はありません。</p>
      ) : (
        <ul className="issue-list">
          {detail.issues.map((i) => (
            <li key={i.id} className={i.resolved ? 'issue-resolved' : ''}>
              <div className="anomaly-head">
                <SeverityBadge severity={i.severity} label={i.severityLabel} />
                <code className="rule-code">{i.ruleCode}</code>
                {i.resolved && <span className="resolved-tag">確認済み</span>}
              </div>
              <div>{i.message}</div>
              {i.evidence && <div className="anomaly-evidence">根拠: {i.evidence}</div>}
            </li>
          ))}
        </ul>
      )}

      <h3>レビュー</h3>
      {e.reviewedAt && (
        <p className="prev-review">
          前回: {e.statusLabel}（{e.reviewedBy} / {new Date(e.reviewedAt).toLocaleString('ja-JP')}）
          {e.reviewReason && <> 理由: {e.reviewReason}</>}
        </p>
      )}

      <label className="field">
        操作者
        <input value={reviewedBy} onChange={(ev) => setReviewedBy(ev.target.value)} />
      </label>

      <label className="field">
        理由
        <textarea
          value={reason}
          onChange={(ev) => setReason(ev.target.value)}
          placeholder="却下や例外承認の理由を記録します"
          rows={2}
        />
      </label>

      {hasUnresolvedRed && (
        <label className="field checkbox">
          <input type="checkbox" checked={override} onChange={(ev) => setOverride(ev.target.checked)} />
          <span>
            要対応の警告を承知のうえで例外承認する（理由が必須です）
          </span>
        </label>
      )}

      {error && <p className="error-text" role="alert">{error}</p>}

      <div className="actions">
        <button type="button" className="approve" disabled={busy} onClick={() => void review('APPROVED')}>
          承認
        </button>
        <button type="button" className="reject" disabled={busy} onClick={() => void review('REJECTED')}>
          却下
        </button>
        <button type="button" disabled={busy} onClick={() => void review('PENDING')}>
          保留に戻す
        </button>
      </div>
    </aside>
  )
}

function CompareRow({
  label,
  raw,
  normalized,
}: {
  label: string
  raw: string | null | undefined
  normalized: string | number | null | undefined
}) {
  const changed = raw != null && normalized != null && String(raw).trim() !== String(normalized)
  return (
    <tr>
      <th scope="row">{label}</th>
      <td className="raw-value">{raw ?? <span className="missing">（空）</span>}</td>
      <td className={changed ? 'normalized-changed' : ''}>
        {normalized ?? <span className="missing">解釈できず</span>}
      </td>
    </tr>
  )
}
