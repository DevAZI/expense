import { useCallback, useEffect, useState } from 'react'
import './App.css'
import { api, type ExpenseFilter } from './api/client'
import type { Anomaly, Expense, Meta, Page, Summary } from './api/types'
import { UploadPanel } from './features/import/UploadPanel'
import { ResetButton } from './features/import/ResetButton'
import { SummaryCards } from './features/summary/SummaryCards'
import { ExpenseTable } from './features/expenses/ExpenseTable'
import { AnomalyList, type AnomalyFilter } from './features/anomalies/AnomalyList'
import { AiAdvisoryPanel } from './features/ai/AiAdvisoryPanel'
import { ApproveCleanButton } from './features/review/ApproveCleanButton'
import { ExpenseDetailPanel } from './features/review/ExpenseDetailPanel'

type Tab = 'expenses' | 'anomalies' | 'ai'

function App() {
  const [meta, setMeta] = useState<Meta | null>(null)
  const [summary, setSummary] = useState<Summary | null>(null)
  const [page, setPage] = useState<Page<Expense> | null>(null)
  const [anomalies, setAnomalies] = useState<Anomaly[]>([])
  const [tab, setTab] = useState<Tab>('expenses')
  const [filter, setFilter] = useState<ExpenseFilter>({ page: 0, size: 50 })
  const [anomalyFilter, setAnomalyFilter] = useState<AnomalyFilter>({})
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  // reload のたびに増える。子が「データが変わった」ことを検知して数え直すための合図。
  const [reloadNonce, setReloadNonce] = useState(0)

  useEffect(() => {
    api.meta().then(setMeta).catch((e) => setError(String(e)))
  }, [])

  // setState はすべて Promise のコールバック内で行う。effect の本体で同期的に
  // setState するとカスケードレンダリングになる（eslint の react-hooks が検出する）。
  const reload = useCallback(() => {
    return Promise.all([api.summary(), api.expenses(filter), api.anomalies(anomalyFilter)])
      .then(([s, p, a]) => {
        setSummary(s)
        setPage(p)
        setAnomalies(a)
        setError(null)
        setReloadNonce((n) => n + 1)
      })
      .catch((e) => setError(String(e)))
  }, [filter, anomalyFilter])

  useEffect(() => {
    void reload()
  }, [reload])

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <h1>経費CSV 集計・確認ツール</h1>
          <p className="subtitle">
            部署ごとに形式の違うCSVを取り込み、共通形式に揃えて、確認が必要な申請を洗い出します。
          </p>
        </div>
        <a className="export-link" href={api.exportUrl()}>
          承認済みをCSVで書き出す
        </a>
      </header>

      {error && (
        <p className="error-banner" role="alert">
          サーバーに接続できません: {error}
        </p>
      )}

      <UploadPanel meta={meta} onImported={reload} />

      <ResetButton
        summary={summary}
        onReset={() => {
          // 消したあとに開いたままの明細詳細が残ると、存在しないIDを引いて
          // エラーになる。選択も一緒に閉じる。
          setSelectedId(null)
          void reload()
        }}
      />

      {summary && <SummaryCards summary={summary} />}

      <ApproveCleanButton
        reloadToken={reloadNonce}
        onApproved={() => {
          // 承認で開いていた明細の状態が変わるので、選択は閉じて一覧を引き直す。
          setSelectedId(null)
          void reload()
        }}
      />

      <nav className="tabs" aria-label="表示切替">
        <button
          type="button"
          className={tab === 'expenses' ? 'tab tab-active' : 'tab'}
          aria-pressed={tab === 'expenses'}
          onClick={() => setTab('expenses')}
        >
          明細一覧{page ? `（${page.totalElements}）` : ''}
        </button>
        <button
          type="button"
          className={tab === 'anomalies' ? 'tab tab-active' : 'tab'}
          aria-pressed={tab === 'anomalies'}
          onClick={() => setTab('anomalies')}
        >
          警告一覧{anomalies.length ? `（${anomalies.length}）` : ''}
        </button>
        <button
          type="button"
          className={tab === 'ai' ? 'tab tab-active' : 'tab'}
          aria-pressed={tab === 'ai'}
          onClick={() => setTab('ai')}
        >
          AI補助
        </button>
      </nav>

      <div className="main">
        <div className="content">
          {tab === 'expenses' && (
            <ExpenseTable
              page={page}
              meta={meta}
              filter={filter}
              onFilterChange={setFilter}
              onSelect={setSelectedId}
              selectedId={selectedId}
            />
          )}
          {tab === 'anomalies' && (
            <AnomalyList
              anomalies={anomalies}
              meta={meta}
              filter={anomalyFilter}
              onFilterChange={setAnomalyFilter}
              onSelect={setSelectedId}
            />
          )}
          {tab === 'ai' && <AiAdvisoryPanel onSelect={setSelectedId} />}
        </div>

        {selectedId && (
          // key で明細ごとに作り直す。理由・例外承認チェックの入力状態が
          // 別の明細へ持ち越されるのを防ぐ（誤った理由での承認は事故になる）。
          <ExpenseDetailPanel
            key={selectedId}
            expenseId={selectedId}
            onClose={() => setSelectedId(null)}
            onReviewed={reload}
          />
        )}
      </div>
    </div>
  )
}

export default App
