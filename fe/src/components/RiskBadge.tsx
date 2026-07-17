import type { RiskLevel, Severity } from '../api/types'

/**
 * 危険度バッジ。
 *
 * 計画書 10 は「赤・黄だけで意味を伝えず、状態テキスト、アイコン、説明を提供する」ことを
 * 求めている。色覚特性や白黒印刷で意味が消えないよう、記号とテキストを必ず併記する。
 * title 属性で計画書 7 の定義（赤=処理不能または重大 / 黄=人による確認が必要）も出す。
 */

const RISK: Record<RiskLevel, { mark: string; text: string; title: string; className: string }> = {
  RED: {
    mark: '■',
    text: '要対応',
    title: '処理不能または重大。解決するまで承認できません',
    className: 'badge badge-red',
  },
  YELLOW: {
    mark: '▲',
    text: '要確認',
    title: '人による確認が必要。確認のうえ承認できます',
    className: 'badge badge-yellow',
  },
  NONE: {
    mark: '－',
    text: '警告なし',
    title: '検証ルールに該当しませんでした',
    className: 'badge badge-none',
  },
}

export function RiskBadge({ level }: { level: RiskLevel }) {
  const r = RISK[level]
  return (
    <span className={r.className} title={r.title}>
      <span aria-hidden="true">{r.mark}</span> {r.text}
    </span>
  )
}

export function SeverityBadge({ severity, label }: { severity: Severity; label: string }) {
  const r = RISK[severity]
  return (
    <span className={r.className} title={label}>
      <span aria-hidden="true">{r.mark}</span> {r.text}
    </span>
  )
}
