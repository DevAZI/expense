import { useEffect, useState } from 'react'
import { api, ApiRequestError } from '../../api/client'

/**
 * 「警告なし」の未承認明細を一括承認するボタン。
 *
 * 対象は PENDING かつ riskLevel=NONE（RED も YELLOW も無い）だけ。人が1件ずつ開いて
 * 確認する必要がないものをまとめて通す。YELLOW/RED は個別に明細詳細から承認する。
 *
 * 件数はサーバに問い合わせて表示する。取込・承認・初期化のたびに未承認数が変わるので、
 * reloadToken（未承認件数など）が変わったら数え直す。
 */
export function ApproveCleanButton({
  reloadToken,
  onApproved,
}: {
  reloadToken: number
  onApproved: () => void
}) {
  const [cleanCount, setCleanCount] = useState<number | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [reviewedBy, setReviewedBy] = useState('経理担当')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [doneCount, setDoneCount] = useState<number | null>(null)

  // 警告なし・未承認の件数を数える。既存の一覧APIを totalElements 目的で1件だけ引く。
  useEffect(() => {
    let cancelled = false
    api
      .expenses({ status: 'PENDING', riskLevel: 'NONE', page: 0, size: 1 })
      .then((p) => {
        if (!cancelled) setCleanCount(p.totalElements)
      })
      .catch(() => {
        if (!cancelled) setCleanCount(null)
      })
    return () => {
      cancelled = true
    }
  }, [reloadToken])

  async function run() {
    setBusy(true)
    setError(null)
    try {
      const result = await api.approveClean(reviewedBy.trim() || '経理担当')
      setDoneCount(result.approvedCount)
      setConfirming(false)
      onApproved()
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  if (doneCount !== null) {
    return (
      <div className="approve-clean-done" role="status">
        警告なしの明細 {doneCount} 件を承認しました。
        <button type="button" className="link" onClick={() => setDoneCount(null)}>
          閉じる
        </button>
      </div>
    )
  }

  const count = cleanCount ?? 0

  if (!confirming) {
    return (
      <div className="approve-clean-area">
        <button
          type="button"
          className="approve-clean-trigger"
          onClick={() => setConfirming(true)}
          disabled={count === 0}
          title={count === 0 ? '警告なしの未承認明細はありません' : '警告なしの未承認明細をまとめて承認します'}
        >
          警告なしを一括承認{count > 0 ? `（${count}件）` : ''}
        </button>
        <span className="approve-clean-hint">
          {count === 0 ? '警告なしの未承認明細はありません' : 'RED・YELLOW を含む明細は対象外です'}
        </span>
      </div>
    )
  }

  return (
    <div className="approve-clean-confirm" role="alertdialog" aria-label="一括承認の確認">
      <p>
        <strong>警告なしの未承認明細 {count} 件</strong>をまとめて承認します。
        （要対応・要確認の警告が付いた明細は対象外です。）
      </p>

      <label className="field">
        操作者
        <input value={reviewedBy} onChange={(e) => setReviewedBy(e.target.value)} />
      </label>

      {error && (
        <p className="error-text" role="alert">
          {error}
        </p>
      )}

      <div className="approve-clean-actions">
        <button type="button" className="approve" disabled={busy} onClick={() => void run()}>
          {busy ? '承認中…' : `${count}件を承認する`}
        </button>
        <button type="button" disabled={busy} onClick={() => setConfirming(false)}>
          やめる
        </button>
      </div>
    </div>
  )
}
