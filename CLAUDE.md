# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

経費CSV統合・異常検知システム。各部署から届く形式差のあるCSVを取り込み、共通形式へ正規化し、
決定的なルールで異常を検知して、経理担当者が承認した明細だけを集計する。

**これは採用課題（株式会社DigiMan / 目安4時間・上限6時間）の提出物である。**
評価は 要件の解像度25 / AIのコントロール25 / 検証20 / スコープと完成度20 / ドキュメント10。
依頼者の経理・森本さんは産休中で追加質問ができないため、曖昧な点は仮定を置いて
`ASSUMPTIONS.md` に残すことが要求されている。判断の記録がそのまま評価対象。

要件は `expense-csv-system-development-plan.md`（以下「計画書」）。ただし計画書は
11のルールテーブル・22ルール・5ロールを含む企業システム規模で、課題の時間枠を大きく超える。
**現在の実装はMVPスコープ**で、意図的に外した範囲は下の「MVPスコープ」を参照。

- `be/` — Spring Boot 4.1 / Java 21 / Gradle。パッケージルート `web.ai.expense`
- `fe/` — React 19 + TypeScript + Vite
- `sample-data/` — 2種類が混在しているので注意
  - `eigyo_keihi_R8-06.csv` / `dev_expenses_2026-06.csv` / `marketing_keihi_202606.csv`
    → **配布された実データ**。`RealDataImportTest` がこれを直接読む。中身を変えないこと
  - `test_broken_*.csv` → **こちらが合成データ**。実データが通らない経路を狙って自作した異常系

## Commands

```
# backend（be/ から）
./gradlew bootRun                                  # http://localhost:8080
./gradlew test                                     # 全40件
./gradlew test --tests '*RealDataImportTest'       # 実データ3ファイルを流す（最重要）
./gradlew test --tests 'web.ai.expense.shared.*'   # 正規化ロジックだけ（速い）

# frontend（fe/ から。バックエンドを先に起動しておくこと）
npm run dev      # http://localhost:5173 → /api は 8080 へプロキシ
npm run build    # tsc -b を含む
npm run lint
```

テスト失敗の詳細はコンソールが文字化けするので `build/test-results/test/*.xml` を見る。

## アーキテクチャ

レイヤードで、依存は常に `domain` へ向く（`api` → `application` → `domain` ← `infrastructure`）。

### 動かす前に知っておくべき4点

**1. ルールは必ず `RuleContext` 経由でパラメータを読む。**
`RowRule` / `BatchRule` の実装が設定やDBを直接読んではいけない。取込開始時に
`ExpenseRuleProperties.toSnapshot()` で `RuleSnapshot` を1回だけ凍結して引き回す（計画書 8.3）。
DB管理へ移す際に差し替えるのは `ExpenseRuleProperties` 1クラスだけで済む。

**2. `RowRule` と `BatchRule` の分離は意図的。**
完全重複・類似重複は1行では判定できない。新ルールを足すときは、まず1行で閉じるかを判断する。

**3. `Expense` の必須項目は nullable。**
壊れた行も明細として保存しないと「原本確認を促す」画面が作れない（計画書 4.1 と 7 の衝突）。
「揃っていること」はDB制約ではなく検証ルールと承認ゲートで担保する。
`ReviewExpenseUseCase` が唯一の承認ゲートで、未解決REDは理由付きの例外承認なしには承認できない。

**4. 氏名は表示名と照合キーを分ける。**
`applicantName`（空白を1つに畳む）と `applicantNameKey`（空白除去・小文字化）。
重複判定は必ずキーを使う。表示名で突き合わせると「田中 美咲」と「田中美咲」が別人になる。
逆に表示名を空白除去すると開発部の "Kim Jiho" が "KimJiho" になる。両方とも実データで踏んだ。

### 検証ルールの追加手順

1. `RuleCode` に定数を足す（重要度と説明文を持つ）
2. `RowRule` か `BatchRule` を1クラス実装して `@Component`
3. パラメータが要るなら `RuleContext` → `RuleSnapshot` → `ExpenseRuleProperties` → yml

`ValidationEngine` は変更不要。Spring が実装を集める。

## ハマりどころ（実際に踏んだもの）

**Spring Boot の Map キーはブラケット記法が必須。**
`application.yml` の `category-mappings` のキーが `"[交通費]"` と囲ってあるのは飾りではない。
Boot は Map キーを「小文字英数字と `-`」しか許さず、日本語や大文字のキーを**例外も出さずに捨てる**。
囲いを外すとマッピングが空になり、全件 CATEGORY_UNMAPPED になる。

**enum カラムには必ず `@JdbcTypeCode(SqlTypes.VARCHAR)` を付ける。**
付けないと Hibernate は H2 のネイティブ ENUM 型でカラムを作る。ENUM 型は許可値が
カラム定義に焼き込まれるため、enum に定数を足しても `ddl-auto=update` は既存カラムを
変更せず、新しい値の INSERT が**実行時に500で落ちる**。
**テストは `create-drop` で毎回スキーマを作り直すのでこの事故を検出できない** —
実際 AMOUNT_ZERO / DATE_FUTURE を足したとき、テストは全件通ったのに起動したアプリだけが
落ちた。`SchemaEvolutionTest` がカラム型を検査してこれを塞いでいる。Flyway を使わない以上、
スキーマ進化の安全網はこのテストしかない。壊れた既存DBは `be/data` を消せば作り直される。

**文字コード検出の順序は変えない。** BOM → 厳密UTF-8 → CP932。
CP932を先に試すとUTF-8ファイルが「文字化けしつつデコード成功」して静かに壊れる。

**日付は全書式 `ResolverStyle.STRICT`。** 緩めると `2026/6/31` が 6/30 に丸められINVALID_DATEが消える。

**部署別ヘッダー別名の衝突に注意。** 別名が部署間で重なると `CsvParserRegistry` が例外を投げ、
`department` の明示を要求する。例えば SALES の applicant-name に「氏名」を足すと MARKETING と衝突する。

**フロントは effect 本体で setState しない。** eslint の `react-hooks/set-state-in-effect` が落ちる。
明細ごとの状態リセットは `key={expenseId}` による再マウントで行う。

## 実データについて（`sample-data/`）

配布された3ファイル。**計画書 2章の記述と1点違う**: マーケティング部は BOM なしの UTF-8
（計画書は「UTF-8 BOM」と書いている）。それ以外の記述は実データと一致。

実ヘッダー:
- 営業部 `日付,申請者,勘定科目,金額(円),備考` — CP932・和暦
- 開発部 `Date,Employee,Category,Amount(JPY),Notes` — UTF-8 BOM・英語名・英日混在費目
- マーケ `利用日,氏名,費目,支払金額,メモ` — UTF-8・複数日付形式・全角数字

3部署は分類体系そのものが違う（営業部=勘定科目、マーケ=費目、開発部=英語の実費種別）。
`ExpenseCategory` は13費目で、勘定科目の区別を保つ（交通費≠旅費交通費、会議費≠交際費）。
部署横断で比較したいときは `CategoryGroup` で丸める。

## MVPスコープ（意図的に未実装）

| 計画書 | 状態 |
|---|---|
| Spring AI による費目候補・誤記候補（11章、Phase 4） | **依存ごと外している**。`build.gradle` にコメント残置。復活は2行 + yml数行 |
| ルール・マスターのDB管理（8章、11テーブル） | `application.yml` + `ExpenseRuleProperties`。ロジックはコード／パラメータは設定の分離だけ先に作ってある |
| 8.5 の追加22ルール | 大半は未実装。実装済みは11ルール（8.5 からは AMOUNT_ZERO と DATE_FUTURE のみ採用） |
| review_decisions / audit_logs テーブル | `expenses` に承認結果を直接持たせている。**レビュー履歴は残らず現在状態のみ** |
| 認証・認可（16章の5ロール） | なし。操作者名はリクエストボディで受け取る |
| 非同期ジョブ・ストリーミング（16章） | 同期実行・全件メモリ |
| Flyway / Liquibase | 使わない方針。スキーマは `ddl-auto` |

DBテーブルは4つ: `import_files` / `expense_raw_rows` / `expenses` / `validation_issues`。

実装済みルール（11）:
- RED: REQUIRED_FIELD_MISSING, INVALID_DATE, DATE_FUTURE, EXACT_DUPLICATE
- YELLOW: SIMILAR_DUPLICATE, CSV_STRUCTURE_INVALID, PERIOD_OUT_OF_RANGE, HIGH_AMOUNT,
  NEGATIVE_AMOUNT, AMOUNT_ZERO, CATEGORY_UNMAPPED

DATE_FUTURE の基準日は `RuleContext.today()`（取込開始時に Clock から凍結）。
ルールが `LocalDate.now()` を直接呼ぶと日付をまたぐ取込で判定がぶれ、境界もテストできない。

**対象月は取込ごとに指定する**。`POST /api/imports` の `targetYearMonth`（"2026-06" 形式、
省略時は `expense.rules.target-year-month` の既定値）。画面のアップロード欄で選ぶ。
設定固定にすると翌月に yml を書き換えて再起動する必要があり、依頼者（経理・非エンジニア）
には実行できないため。使った対象月は `import_files.target_year_month` に記録する
（計画書 8.2 の rule_version_id に相当する最小限の追跡情報。ルールをDB管理へ移す際は
ここがバージョンIDに置き換わる）。

**対象月は期間外判定だけでなく、年省略日付の年の補完にも効く。**
`DateNormalizer` は対象月に「いちばん近い年」を選ぶ。対象月の年を機械的に当てると、
対象月が2027年1月のときの「12/28」（前年12月の月またぎ精算）が2027-12-28になり11ヶ月ずれる。

**データ初期化** (`DELETE /api/imports` = 全削除、`DELETE /api/imports/{id}` = 1ファイル分)。
翌月分を入れる前に前月分を空にする用途。計画書には無い追加機能。
**承認履歴も一緒に消える** — MVPはレビュー結果を `expenses` に持たせており、明細を消せば
「誰がいつ何を承認したか」も消える。運用に載せるなら監査ログの永続化が先に要る。

計画書から意図的に逸脱した2点（ASSUMPTIONS.md に記載すること）:
- `CSV_STRUCTURE_INVALID` は計画書ではREDだがYELLOWにした。該当する開発部4行は備考のカンマだけが
  原因で、金額も日付も正しく、無損失で復元できるため。REDにすると問題のない行が承認不能になる。
- `SIMILAR_DUPLICATE` は備考の類似度を判定の門にしていない。営業部6/23のペアは類似度0.38で、
  閾値0.5では落ちる。「同一人・同費目・同額・近接3日」を門にし、類似度は根拠の参考値に留めた。

## 残作業

- `ASSUMPTIONS.md` / `RETRO.md` / `README.md`（提出物の必須構成）
- `logs/`（AIセッションログを**無加工で全量**。`~/.claude/projects/D--kostaEx-digiman/` の JSONL）
- 提出物のディレクトリ構成への組み替え（README / ASSUMPTIONS / RETRO / logs / src）
