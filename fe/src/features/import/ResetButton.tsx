import { useState } from 'react'
import { api, ApiRequestError } from '../../api/client'
import type { ResetResult, Summary } from '../../api/types'

/**
 * データ初期化（翌月分を入れる前に前月分を空にする）。
 *
 * 取り返しがつかない操作なので二段階にする。1回目のクリックでは何も消さず、
 * 消える件数を見せてから確定させる。承認済みの明細が消えることは特に強く出す。
 * 確認作業をやり直す羽目になるのが、このツールで一番痛い事故なので。
 */
export function ResetButton({ summary, onReset }: { summary: Summary | null; onReset: () => void }) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState<ResetResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const total = summary?.totalCount ?? 0
  const approved = summary?.approvedCount ?? 0

  async function run() {
    setBusy(true)
    setError(null)
    try {
      const result = await api.resetAll()
      setDone(result)
      setConfirming(false)
      onReset()
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  if (done) {
    return (
      <div className="reset-done" role="status">
        初期化しました（明細{done.expenses}件 / 警告{done.issues}件 / 取込ファイル{done.importFiles}件を削除）。
        新しい月のCSVを取り込めます。
        <button type="button" className="link" onClick={() => setDone(null)}>
          閉じる
        </button>
      </div>
    )
  }

  if (!confirming) {
    return (
      <div className="reset-area">
        <button
          type="button"
          className="reset-trigger"
          onClick={() => setConfirming(true)}
          disabled={total === 0}
          title={total === 0 ? 'データがありません' : '取り込んだデータをすべて削除します'}
        >
          データを初期化
        </button>
        <span className="reset-hint">
          {total === 0 ? 'データはありません' : `現在 ${total} 件のデータがあります`}
        </span>
      </div>
    )
  }

  return (
    <div className="reset-confirm" role="alertdialog" aria-label="初期化の確認">
      <p className="reset-warning">
        <strong>取り込んだデータをすべて削除します。元に戻せません。</strong>
      </p>
      <ul className="reset-list">
        <li>明細 {total} 件</li>
        {approved > 0 && (
          <li className="reset-critical">
            うち承認済み {approved} 件 — <strong>確認した記録も一緒に消えます</strong>
          </li>
        )}
        <li>検知した警告と、取込ファイルの記録</li>
      </ul>
      <p className="reset-note">
        書き出しが必要な場合は、先に「承認済みをCSVで書き出す」を実行してください。
      </p>

      {error && (
        <p className="error-text" role="alert">
          {error}
        </p>
      )}

      <div className="reset-actions">
        <button type="button" className="reject" disabled={busy} onClick={() => void run()}>
          {busy ? '削除中…' : 'すべて削除する'}
        </button>
        <button type="button" disabled={busy} onClick={() => setConfirming(false)}>
          やめる
        </button>
      </div>
    </div>
  )
}
