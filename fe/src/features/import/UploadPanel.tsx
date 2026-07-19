import { useRef, useState } from 'react'
import { api, ApiRequestError } from '../../api/client'
import type { ImportResult, Meta } from '../../api/types'

/**
 * アップロード画面（計画書 10）。
 *
 * ドラッグ＆ドロップと複数ファイル選択に対応し、ファイル単位のエラーを1件ずつ返す。
 * 1ファイルが失敗しても他のファイルの取込は続ける（部署ごとにファイルが届くため、
 * 1つの不備で全部やり直しにすると経理の手間が増える）。
 *
 * 対象月をここで選ばせるのは、月次で使うツールだから。設定に固定すると、翌月は
 * application.yml を書き換えて再起動する必要があり、経理担当者には実行できない。
 */
// 自動判定を選んだときの値。空文字を department 未指定（＝ヘッダー自動判定）の合図にする。
const AUTO_DETECT = ''

export function UploadPanel({ meta, onImported }: { meta: Meta | null; onImported: () => void }) {
  const [results, setResults] = useState<ImportResult[]>([])
  const [busy, setBusy] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [targetYearMonth, setTargetYearMonth] = useState('')
  // 部署の明示選択。既定は自動判定（フェーズ1の許容パス）。
  const [department, setDepartment] = useState<string>(AUTO_DETECT)
  const inputRef = useRef<HTMLInputElement>(null)

  // 未選択のうちはサーバー既定値を使う。meta 取得前でもアップロードできるようにする。
  const effectiveMonth = targetYearMonth || meta?.defaultTargetYearMonth || ''

  async function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return

    const selected = Array.from(files)
    // 部署を明示選択したまま複数ファイルをまとめて入れると、全ファイルがその部署として
    // 強制マッピングされる（ヘッダーが別部署でも取り違えない代わりに、選択ミスは全件に及ぶ）。
    // 事故を防ぐため、明示選択 × 複数ファイルのときだけ確認を取る。
    if (department && selected.length > 1) {
      const label = meta?.departments.find((d) => d.code === department)?.label ?? department
      const ok = window.confirm(
        `選択された ${selected.length} 件のファイルは、すべて「${label}」として処理されます。\n` +
          `（ヘッダーの内容にかかわらず${label}の形式で読み込みます）\n\n進行しますか？`,
      )
      if (!ok) return
    }

    setBusy(true)
    const collected: ImportResult[] = []

    for (const file of selected) {
      try {
        collected.push(await api.upload(file, effectiveMonth || undefined, department || undefined))
      } catch (e) {
        // APIが400を返すケース（拡張子違い、対象月の形式違いなど）も画面に残す
        collected.push({
          importFileId: '',
          fileName: file.name,
          status: 'FAILED',
          charset: null,
          department: null,
          departmentLabel: null,
          targetYearMonth: null,
          rowCount: 0,
          redCount: 0,
          yellowCount: 0,
          errorSummary: e instanceof ApiRequestError ? e.message : String(e),
          sameHashImportFileIds: [],
        })
      }
    }

    setResults((prev) => [...collected, ...prev])
    setBusy(false)
    onImported()
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <section className="upload" aria-label="CSVアップロード">
      <div className="month-picker">
        <label>
          <span className="month-label">対象月</span>
          <input
            type="month"
            value={effectiveMonth}
            onChange={(e) => setTargetYearMonth(e.target.value)}
            aria-describedby="month-help"
          />
        </label>
        <span id="month-help" className="month-help">
          この月の明細として取り込みます。対象月から外れた利用日は警告になり、
          「6/2」のように年のない日付はこの月の年で補完します。
        </span>
      </div>

      <div className="department-picker">
        <label>
          <span className="month-label">部署</span>
          <select
            value={department}
            onChange={(e) => setDepartment(e.target.value)}
            aria-describedby="department-help"
          >
            <option value={AUTO_DETECT}>自動判定（ヘッダーから推定）</option>
            {meta?.departments.map((d) => (
              <option key={d.code} value={d.code}>
                {d.label}
              </option>
            ))}
          </select>
        </label>
        <span id="department-help" className="month-help">
          部署を選ぶと、ヘッダー名が違っても、その部署の形式（列の並び）で強制的に読み込みます。
          他部署と列名が重なるCSVでも取り違えません。「自動判定」はヘッダーから推定し、
          複数部署に一致する場合は部署の指定を求めます。
        </span>
      </div>

      <div
        className={`dropzone ${dragging ? 'dropzone-active' : ''}`}
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragging(false)
          void handleFiles(e.dataTransfer.files)
        }}
      >
        <p className="dropzone-title">CSVファイルをここにドラッグ＆ドロップ</p>
        <p className="dropzone-sub">
          複数ファイルをまとめて選べます。上で部署を選ぶとその形式で読み込み、
          「自動判定」ならヘッダーから推定します。
        </p>
        <button type="button" onClick={() => inputRef.current?.click()} disabled={busy}>
          {busy ? '取込中…' : `${effectiveMonth || '既定の月'} の分として取り込む`}
        </button>
        <input
          ref={inputRef}
          type="file"
          accept=".csv"
          multiple
          hidden
          onChange={(e) => void handleFiles(e.target.files)}
        />
      </div>

      {results.length > 0 && (
        <table className="import-results">
          <caption>取込結果</caption>
          <thead>
            <tr>
              <th scope="col">ファイル</th>
              <th scope="col">結果</th>
              <th scope="col">対象月</th>
              <th scope="col">部署</th>
              <th scope="col">文字コード</th>
              <th scope="col" className="num">行数</th>
              <th scope="col" className="num">要対応</th>
              <th scope="col" className="num">要確認</th>
            </tr>
          </thead>
          <tbody>
            {results.map((r, i) => (
              <tr key={`${r.fileName}-${i}`} className={r.status === 'FAILED' ? 'row-failed' : ''}>
                <td>{r.fileName}</td>
                <td>
                  {r.status === 'COMPLETED' ? (
                    '取込完了'
                  ) : (
                    <span className="error-text">取込失敗</span>
                  )}
                  {r.errorSummary && <div className="error-detail">{r.errorSummary}</div>}
                  {r.sameHashImportFileIds.length > 0 && (
                    <div className="warn-detail">
                      同じ内容のファイルが既に取り込まれています（再取込の可能性）
                    </div>
                  )}
                </td>
                <td>{r.targetYearMonth ?? '—'}</td>
                <td>{r.departmentLabel ?? '—'}</td>
                <td>{r.charset ?? '—'}</td>
                <td className="num">{r.rowCount}</td>
                <td className="num">{r.redCount}</td>
                <td className="num">{r.yellowCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
