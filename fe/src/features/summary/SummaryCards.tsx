import type { Summary } from '../../api/types'

const yen = (v: number) => `¥${v.toLocaleString('ja-JP')}`

/**
 * サマリーカード（計画書 10）。
 *
 * 合計金額の隣に必ず「何を除いたか」を出す。経理が数字だけ見て、未確認の申請が
 * 含まれていると誤解するのを防ぐため。計画書 10 の「集計条件を明示する」に対応。
 */
export function SummaryCards({ summary }: { summary: Summary }) {
  return (
    <section className="summary" aria-label="集計サマリー">
      <div className="cards">
        <div className="card card-primary">
          <div className="card-label">承認済み合計</div>
          <div className="card-value">{yen(summary.approvedTotalYen)}</div>
          <div className="card-note">{summary.approvedCount}件を集計</div>
        </div>

        <div className="card">
          <div className="card-label">未確認</div>
          <div className="card-value">{summary.pendingCount}件</div>
          <div className="card-note">確認するまで合計に入りません</div>
        </div>

        <div className="card">
          <div className="card-label">要対応の警告</div>
          <div className="card-value">{summary.unresolvedRedCount}件</div>
          <div className="card-note">未解決のまま集計対象外</div>
        </div>

        <div className="card">
          <div className="card-label">却下</div>
          <div className="card-value">{summary.rejectedCount}件</div>
          <div className="card-note">合計に入りません</div>
        </div>
      </div>

      <p className="summary-condition">
        集計条件: 全{summary.totalCount}件のうち、<strong>承認済みで、要対応の警告が残っていない{summary.approvedCount}件</strong>
        だけを合計しています（{summary.excludedCount}件を除外）。
      </p>

      {summary.byDepartment.length > 0 && (
        <div className="breakdowns">
          <BreakdownTable title="部署別" rows={summary.byDepartment} />
          <BreakdownTable title="費目別（勘定科目）" rows={summary.byCategory} />
          <BreakdownTable title="グループ別（部署横断）" rows={summary.byCategoryGroup} />
        </div>
      )}
    </section>
  )
}

function BreakdownTable({ title, rows }: { title: string; rows: Summary['byDepartment'] }) {
  return (
    <div className="breakdown">
      <h3>{title}</h3>
      <table>
        <thead>
          <tr>
            <th scope="col">区分</th>
            <th scope="col" className="num">
              件数
            </th>
            <th scope="col" className="num">
              金額
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.code}>
              <td>{r.label}</td>
              <td className="num">{r.count}</td>
              <td className="num">{yen(r.totalYen)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
