import type { Expense, Meta, Page } from '../../api/types'
import { RiskBadge } from '../../components/RiskBadge'
import type { ExpenseFilter } from '../../api/client'

const yen = (v: number | null) => (v === null ? '—' : `¥${v.toLocaleString('ja-JP')}`)

/**
 * 全体履歴テーブル（計画書 10）。標準化した明細をページングで表示する。
 * 行クリックで明細詳細を開き、原文との対比とレビューはそちらで行う。
 */
export function ExpenseTable({
  page,
  meta,
  filter,
  onFilterChange,
  onSelect,
  selectedId,
}: {
  page: Page<Expense> | null
  meta: Meta | null
  filter: ExpenseFilter
  onFilterChange: (next: ExpenseFilter) => void
  onSelect: (id: string) => void
  selectedId: string | null
}) {
  const set = (patch: Partial<ExpenseFilter>) => onFilterChange({ ...filter, ...patch, page: 0 })

  return (
    <section aria-label="明細一覧">
      <div className="filters">
        <label>
          部署
          <select value={filter.department ?? ''} onChange={(e) => set({ department: e.target.value })}>
            <option value="">すべて</option>
            {meta?.departments.map((d) => (
              <option key={d.code} value={d.code}>
                {d.label}
              </option>
            ))}
          </select>
        </label>

        <label>
          状態
          <select value={filter.status ?? ''} onChange={(e) => set({ status: e.target.value })}>
            <option value="">すべて</option>
            {meta?.statuses.map((s) => (
              <option key={s.code} value={s.code}>
                {s.label}
              </option>
            ))}
          </select>
        </label>

        <label>
          警告
          <select value={filter.riskLevel ?? ''} onChange={(e) => set({ riskLevel: e.target.value })}>
            <option value="">すべて</option>
            <option value="RED">要対応のみ</option>
            <option value="YELLOW">要確認のみ</option>
            <option value="NONE">警告なしのみ</option>
          </select>
        </label>

        <label>
          利用日 開始
          <input type="date" value={filter.from ?? ''} onChange={(e) => set({ from: e.target.value })} />
        </label>
        <label>
          利用日 終了
          <input type="date" value={filter.to ?? ''} onChange={(e) => set({ to: e.target.value })} />
        </label>

        <button type="button" onClick={() => onFilterChange({ page: 0, size: filter.size })}>
          条件をクリア
        </button>
      </div>

      <table className="data-table">
        <thead>
          <tr>
            <th scope="col">利用日</th>
            <th scope="col">部署</th>
            <th scope="col">申請者</th>
            <th scope="col">費目</th>
            <th scope="col" className="num">金額</th>
            <th scope="col">備考</th>
            <th scope="col">状態</th>
            <th scope="col">警告</th>
          </tr>
        </thead>
        <tbody>
          {page?.content.map((e) => (
            <tr
              key={e.id}
              tabIndex={0}
              role="button"
              aria-label={`${e.applicantName ?? ''} の明細を開く`}
              className={selectedId === e.id ? 'row-selected' : ''}
              onClick={() => onSelect(e.id)}
              // マウス以外でも開けるようにする（計画書 10 のキーボード操作要件）
              onKeyDown={(ev) => {
                if (ev.key === 'Enter' || ev.key === ' ') {
                  ev.preventDefault()
                  onSelect(e.id)
                }
              }}
            >
              <td>{e.usageDate ?? <span className="missing">解釈不能</span>}</td>
              <td>{e.departmentLabel}</td>
              <td>{e.applicantName ?? <span className="missing">欠損</span>}</td>
              <td>{e.categoryLabel ?? <span className="missing">未分類</span>}</td>
              <td className="num">{yen(e.amountYen)}</td>
              <td className="note-cell">{e.note ?? ''}</td>
              <td>{e.statusLabel}</td>
              <td>
                <RiskBadge level={e.riskLevel} />
              </td>
            </tr>
          ))}
          {page?.content.length === 0 && (
            <tr>
              <td colSpan={8} className="empty">
                該当する明細がありません
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {page && page.totalPages > 1 && (
        <div className="pager">
          <button
            type="button"
            disabled={page.number <= 0}
            onClick={() => onFilterChange({ ...filter, page: page.number - 1 })}
          >
            前へ
          </button>
          <span>
            {page.number + 1} / {page.totalPages} ページ（全{page.totalElements}件）
          </span>
          <button
            type="button"
            disabled={page.number >= page.totalPages - 1}
            onClick={() => onFilterChange({ ...filter, page: page.number + 1 })}
          >
            次へ
          </button>
        </div>
      )}
    </section>
  )
}
