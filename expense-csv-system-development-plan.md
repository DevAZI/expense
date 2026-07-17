```markdown
# 経費CSV統合・異常検知システム 開発プラン

## 1. 概要

### 1.1 目的

各部署から届く形式差のある経費CSVを取り込み、共通形式へ標準化する。金額欠損、不正日付、重複、高額、表記揺れなどを検知し、経理担当者が確認・承認した明細のみを月次集計へ反映する。

### 1.2 基本方針

- フロントエンドは React + Vite、バックエンドは Java + Spring Boot を採用する。
- 開発・検証用データベースは H2 Database を採用する。
- 日付、金額、必須項目、対象月、完全重複の判定は Java による決定的な処理とする。
- Spring AI は費目候補、誤記候補、類似備考、判定理由の説明を補助する目的に限定する。
- AI は金額・日付・承認状態を自動で補正または確定してはならない。
- 元CSVと正規化後データ、検証結果、レビュー履歴を分離して保存し、変更経緯を追跡可能にする。

## 2. 対象データと前提

| 部署 | 入力ファイル例 | 主な差分・検知例 |
|---|---|---|
| 営業部 | `eigyo_keihi_R8-06-3.csv` | CP932、和暦、費目の前後空白、6月10日の完全重複、480,000円の高額、対象月外の5月28日 |
| 開発部 | `dev_expenses_2026-06-2.csv` | UTF-8 BOM、英日混在の費目、カンマ付き金額、負数返金、金額欠損、6月31日、完全重複、備考中カンマ |
| マーケティング部 | `marketing_keihi_202606.csv` | UTF-8 BOM、複数日付形式、全角数字、氏名表記揺れ、費目誤記、金額欠損、完全重複、高額広告費 |

ファイル名で部署を固定判定しない。CSVヘッダーの定義により部署別アダプターを選択し、判定不能なファイルは取込エラーとして返す。

## 3. 技術構成

| 層 | 技術 | 責務 |
|---|---|---|
| UI | React, Vite, TypeScript | アップロード、集計表示、履歴検索、警告確認、レビュー操作 |
| API | Spring Boot, Spring Web, Bean Validation | REST API、入力検証、例外応答 |
| 業務処理 | Java 21, Spring Service | CSV取込、正規化、決定的検証、集計、承認制御 |
| AI補助 | Spring AI | 意味的な費目候補・誤記候補・備考類似の提案 |
| 永続化 | Spring Data JPA, H2 Database | 原本、正規化明細、警告、レビュー、監査ログ |
| CSV | Apache Commons CSV または Jackson CSV | 文字コード・引用符・列数を考慮した解析 |

## 4. 共通データモデル

### 4.1 正規化明細

| 項目 | 型 | 必須 | 説明 |
|---|---|---:|---|
| id | UUID | ○ | 明細ID |
| usageDate | LocalDate | ○ | 利用日。内部表現はISO日付 |
| department | Enum | ○ | SALES / DEVELOPMENT / MARKETING |
| applicantName | String | ○ | 正規化後の申請者名 |
| category | Enum | ○ | 標準費目 |
| amountYen | Long | ○ | 円単位の金額。浮動小数点は使用しない |
| note | String | - | 備考原文 |
| status | Enum | ○ | PENDING / APPROVED / REJECTED |
| riskLevel | Enum | ○ | NONE / YELLOW / RED |
| sourceFileId | UUID | ○ | 取込ファイルへの参照 |
| sourceRowNumber | Integer | ○ | 元CSV行番号 |
| createdAt | LocalDateTime | ○ | 取込日時 |

### 4.2 保持するテーブル

| テーブル | 用途 |
|---|---|
| import_files | ファイル名、ハッシュ、文字コード、取込状態、エラー概要 |
| expense_raw_rows | 元行テキスト、元ヘッダー、行番号、解析結果 |
| expenses | 正規化済み経費明細 |
| validation_issues | ルールID、重要度、メッセージ、根拠値、解決状態 |
| review_decisions | 承認・却下・保留、理由、操作者、日時 |
| audit_logs | 変更前後の値、操作者、操作日時 |

H2は開発・評価用途として使用する。運用環境で同時更新や永続データ保護が求められる場合は、PostgreSQL等への移行を前提とし、JPA Entity と Repository に依存を閉じ込める。

## 5. 処理フロー

1. 利用者がCSVをアップロードする。
2. APIが拡張子、サイズ、ファイルハッシュ、文字コード、ヘッダー、CSV構文を検査する。
3. ヘッダーから部署別アダプターを選択する。対応外または列数不整合はファイル単位の取込失敗とする。
4. 行ごとに元データを `expense_raw_rows` へ保存する。
5. 日付、氏名、費目、金額、備考を正規化し、正規化明細を作成する。
6. Javaルールで必須値、不正日付、対象月、重複、金額、しきい値を検証する。
7. 必要な行だけSpring AIへ渡し、費目誤記候補・類似重複候補を取得する。AI出力は警告候補として保存する。
8. 警告と明細を画面へ返す。初期状態はすべて `PENDING` とする。
9. 経理担当者が承認・却下・保留を判断し、理由と監査ログを記録する。
10. サマリーは `APPROVED` かつ赤警告が未解決でない明細だけを集計する。

## 6. 正規化ルール

| 対象 | ルール | 例 |
|---|---|---|
| 文字コード | UTF-8 BOM、UTF-8、CP932を順に検出する。判定不能時は取込を中止する | 営業部CP932、他部署UTF-8 BOM |
| 日付 | 和暦・西暦・月日形式を `LocalDate` へ変換する。年省略は対象月の年を補う | `令和8年6月2日`、`6/2`、`2026年6月5日` |
| 金額 | 全角数字を半角化し、桁区切りカンマを除去して `long` に変換する | `１２８００`、`"4,980"` |
| 氏名 | Unicode正規化、全半角空白統一、連続空白除去を行う。原文も保存する | `田中 美咲` と `田中美咲` |
| 費目 | 前後空白を除去し、設定済みマッピングで標準費目へ変換する | ` 交通費`、`Travel`、`交通日` |
| 備考 | 原文を保持し、比較用に空白・記号を正規化した値を別途作る | 類似重複検出に利用 |

## 7. 検証ルール

| ID | 重要度 | 条件 | 画面表示・対応 |
|---|---|---|---|
| REQUIRED_FIELD_MISSING | RED | 日付、申請者、費目、金額のいずれかが欠損 | 承認不可。原本確認を促す |
| INVALID_DATE | RED | `LocalDate` に変換できない | 承認不可。例: 2026/6/31 |
| CSV_STRUCTURE_INVALID | RED | ヘッダー不一致、列数不足・超過、引用符不整合 | 行またはファイルを隔離し再提出を促す |
| EXACT_DUPLICATE | RED | 日付・申請者・費目・金額・正規化備考が一致 | 同一グループを表示し、経理が採否を決定 |
| PERIOD_OUT_OF_RANGE | YELLOW | 利用日が取込対象月外 | 月またぎ精算か確認 |
| SIMILAR_DUPLICATE | YELLOW | 同一申請者・近接日・同額かつ備考類似度が閾値以上 | 別利用か二重申請か確認 |
| HIGH_AMOUNT | YELLOW | 費目別しきい値を超える | 領収書・承認根拠を確認 |
| NEGATIVE_AMOUNT | YELLOW | 金額が0未満 | 返金・取消であることを確認 |
| CATEGORY_UNMAPPED | YELLOW | 標準費目に変換できない | マッピング追加または人手選択 |
| AI_SUGGESTION | YELLOW | AIが誤記・費目候補・意味的類似を提案 | 自動補正せず候補として表示 |

赤警告を持つ明細は、解決または明示的な例外承認が行われるまでサマリーに含めない。黄警告は承認者の確認後に承認可能とする。

## 8. DBによるマスター・検証ルール管理
### 8.1 方針
費目マッピング、高額しきい値、対象月判定、重複判定の閾値、部署別CSV定義、申請者・承認権限は `application.yml` では管理しない。これらはH2のマスター・ルールテーブルで管理し、管理者画面から変更できるようにする。`application.yml` に残すのは、DB接続、ファイル上限、許可MIMEタイプ、AI接続先、ログレベル等の**システム稼働設定だけ**とする。業務ルールを変更するために、再ビルド・再デプロイ・アプリケーション再起動を要求してはならない。
### 8.2 管理対象とテーブル
| テーブル | 管理内容 | 重要な列 |
|---|---|---|
| `category_masters` | 標準費目 | code, name, active, display_order |
| `category_mapping_rules` | 入力費目から標準費目への変換 | source_department, source_value_normalized, category_code, priority, effective_from, effective_to, version, active |
| `validation_rule_definitions` | 検証ルールの定義と有効状態 | rule_code, name, severity, rule_type, enabled, version |
| `validation_rule_parameters` | ルールの可変パラメータ | rule_code, parameter_key, parameter_value, value_type, effective_from, effective_to |
| `amount_threshold_rules` | 部署・費目ごとの金額しきい値 | department, category_code, warning_amount_yen, error_amount_yen, effective_from, effective_to |
| `duplicate_rule_settings` | 完全・類似重複の照合条件 | department, date_window_days, note_similarity_threshold, same_applicant_required, same_amount_required |
| `period_control_rules` | 対象月・月またぎ精算の許容期間 | target_year_month, prior_month_days, next_month_days, enabled |
| `department_csv_schemas` | 部署別CSVヘッダー・必須列・文字コード候補 | department, header_definition_json, required_columns_json, charset_candidates_json, active |
| `employee_masters` | 氏名表記揺れ、所属、在籍状態、申請上限 | employee_code, canonical_name, department, aliases_json, active |
| `approval_policy_rules` | 金額・費目・部署別の承認者・二段階承認条件 | department, category_code, min_amount_yen, approver_role, approval_step |
| `rule_versions` | 取込時点のルールスナップショット | version_id, snapshot_json, created_at, created_by |
マスターの物理削除は禁止し、`active` と有効期間で無効化する。既存明細の再現性を損なわないため、更新ではなく新バージョンを作成し、取込処理は使用した `rule_version_id` を `import_files` と各検証結果へ保存する。
### 8.3 リアルタイム変更と整合性
- 管理者が保存したルールは、Beanの再起動なしで次の取込・再検証処理から反映する。
- 複数インスタンスを想定し、ルールキャッシュを使う場合は更新イベントで全インスタンスのキャッシュを無効化する。初期実装では毎取込時にDBから有効ルールを読み込む。
- 進行中の取込は開始時点で取得したルールスナップショットを最後まで使う。途中変更で同一ファイル内の判定結果が変わらないようにする。
- 既存データは自動的に再判定しない。管理者が対象期間・対象ファイルを指定して「再検証」を実行し、旧結果と新結果を比較してから反映する。
- 重要度をREDへ変更する、承認済み明細に影響する変更、しきい値を緩和する変更は、管理者の登録後に別権限者の承認を要する二者承認とする。
- 更新競合はJPAの楽観ロック（`@Version`）で検出し、古い編集画面からの上書きを拒否する。
### 8.4 初期データ
初期費目・検証ルール・しきい値はFlywayまたはLiquibaseのマイグレーションでH2へ投入する。今回のサンプルで確認された初期ルールには、交通費・旅費交通費・Travel・交通日を `TRANSPORTATION` に寄せるマッピング、広告費1,200,000円やGPU部品298,000円を要確認にする費目別しきい値、完全重複、金額欠損、不正日付、対象月外、負数返金を含める。
## 8.5 追加するヒューマンエラー検証
既存の欠損・不正日付・重複・高額・費目未マッピングに加えて、次のルールを実装対象とする。金額や日数などの値はすべてDBのパラメータで変更可能とする。
| ID | 初期重要度 | 検証内容 | 初期パラメータ例 |
|---|---|---|---|
| AMOUNT_ZERO | YELLOW | 金額が0円。誤入力・取消漏れ・対象外明細を確認 | zero_allowed=false |
| AMOUNT_DIGIT_ANOMALY | YELLOW | 同一費目・部署の中央値に対して桁違いの可能性 | median_multiplier=10 |
| AMOUNT_ROUND_NUMBER | YELLOW | 高額な切りのよい金額。領収書・見積との照合を促す | minimum_amount_yen=100000, unit_yen=10000 |
| TAX_INCONSISTENCY | YELLOW | 税込・税抜区分または税額の組合せが不自然 | tax_rate=0.10, tolerance_yen=1 |
| DATE_FUTURE | RED | 取込日より未来の利用日 | allowed_future_days=0 |
| DATE_WEEKEND_HOLIDAY | YELLOW | 休日・深夜利用。業務利用の説明を確認 | enabled=true |
| DATE_DUPLICATE_PATTERN | YELLOW | 同一申請者が同日同費目を複数申請 | count_threshold=2 |
| DATE_ORDER_ANOMALY | YELLOW | 定期契約等でないのに利用日が著しく逆転・連続 | lookback_days=31 |
| EMPLOYEE_UNKNOWN | RED | 在籍・所属マスターにない申請者 | alias_matching=false |
| DEPARTMENT_MISMATCH | YELLOW | CSV部署と申請者の所属が不一致 | allow_cross_department=false |
| CATEGORY_POLICY_VIOLATION | YELLOW | 部署・役職・費目の利用制限に抵触 | policy_id=department-category-policy |
| REQUIRED_NOTE_MISSING | YELLOW | 高額、交際費、返金等で備考がない | category/amount別に必須化 |
| NOTE_TOO_SHORT | YELLOW | 備考が短すぎ、用途を説明できない | min_length=5 |
| RECEIPT_REQUIRED | YELLOW | 証憑必須額を超えるが証憑情報がない | receipt_required_amount_yen=30000 |
| RECEIPT_DUPLICATE | RED | 同じ領収書番号、ハッシュ、OCR結果を複数利用 | matching_keys=receiptNo,fileHash |
| SPLIT_TRANSACTION | YELLOW | 同日・同一申請者・同一加盟店の小額分割で上限回避の疑い | aggregation_window_days=1 |
| CURRENCY_OR_UNIT_MISMATCH | RED | 円以外、千円単位、通貨記号混入で安全に解釈不可 | accepted_currency=JPY |
| FORBIDDEN_CHARACTER_OR_FORMULA | RED | CSVインジェクションの先頭記号、制御文字、危険な数式 | prefixes=["=","+","-","@"] |
| HEADER_MAPPING_AMBIGUOUS | RED | 同一列が複数の必須項目候補に対応する | fail_on_ambiguous=true |
| FILE_REIMPORT | YELLOW | 同一ファイルハッシュまたは高い行集合一致率で再取込 | row_overlap_threshold=0.95 |
| APPROVAL_SELF_REVIEW | RED | 申請者本人が自身の明細を承認 | enabled=true |
| APPROVAL_LIMIT_EXCEEDED | RED | 承認者の権限額を超える承認 | approval_limit_by_role |
`FORBIDDEN_CHARACTER_OR_FORMULA` は、正当な返金を示す負数まで一律に拒否しない。金額列は数値として厳密に解析し、備考などの文字列列でCSV/Excel式として解釈され得る先頭文字を無害化してエクスポートする。
## 8.6 管理者機能
| 機能 | 内容 |
|---|---|
| マスター管理 | 費目、費目マッピング、従業員・別名、部署、CSVスキーマ、承認ポリシーの追加・編集・無効化 |
| ルール管理 | 有効/無効、重要度、しきい値、対象部署・費目、適用期間の編集 |
| 変更プレビュー | 変更後ルールを指定期間の明細に試行し、警告件数・承認済み合計への影響・対象明細を表示 |
| 二者承認 | 高リスク変更を申請・承認に分離し、自分の変更を自分で承認できないようにする |
| 予約適用 | 指定日時から新ルールを有効化し、期限終了時は旧ルールへ戻す |
| 再検証 | ルールバージョンと対象範囲を指定し、非同期ジョブで再判定。旧新差分を確認後に採用 |
| 監査・復元 | 誰が、いつ、何を、なぜ変更したかを記録し、過去バージョンへロールバック |

## 9. バックエンド設計

### 9.1 パッケージ構成

```text
com.example.expense
├── api                 # Controller, request/response DTO, exception handler
├── application         # Use case / orchestration service
├── domain
│   ├── expense         # Entity, enum, repository interface, domain service
│   ├── validation      # Rule, issue, severity
│   └── review          # Review decision
├── infrastructure
│   ├── csv             # Charset detector, department parser adapters
│   ├── persistence     # JPA repository implementations
│   └── ai              # Spring AI client adapter
├── config              # ConfigurationProperties, Web/AI config
└── shared              # Normalizer, date/amount utility

```

### 9.2 部署別CSVアダプター

`DepartmentCsvParser` インターフェースを作成し、`SalesCsvParser`、`DevelopmentCsvParser`、`MarketingCsvParser` を実装する。各アダプターは入力ヘッダー判定と、部署固有列から中間DTOへの変換だけを担当し、業務検証は共通サービスに集約する。

```java
public interface DepartmentCsvParser {
    boolean supports(CsvHeader header);
    Department department();
    List<RawExpenseRow> parse(InputStream input, Charset charset);
}

```

### 9.3 API

| Method | Path | 用途 |
| --- | --- | --- |
| POST | `/api/imports` | CSVアップロードと非同期取込開始 |
| GET | `/api/imports/{id}` | 取込進捗、成功・失敗件数、構文エラー取得 |
| GET | `/api/expenses` | 明細一覧。部署、状態、警告、期間で絞り込み |
| GET | `/api/expenses/summary` | 承認済み合計、部署別集計、警告件数 |
| GET | `/api/anomalies` | RED/YELLOW警告の一覧と根拠 |
| POST | `/api/expenses/{id}/review` | 承認、却下、保留、理由の登録 |
| GET | `/api/expenses/export` | 条件指定したCSVエクスポート |

## 10. フロントエンド設計

| 画面・コンポーネント | 内容 |
| --- | --- |
| アップロード画面 | ドラッグ＆ドロップ、複数ファイル選択、取込状態、ファイル単位エラー |
| サマリーカード | 承認済み合計、部署別合計、未確認件数、赤警告件数。集計条件を明示する |
| 全体履歴テーブル | 標準化した日付・部署・申請者・費目・金額・備考・状態・警告をページング表示 |
| 異常値警告セクション | 赤・黄バッジ、ルールID、検出根拠、候補明細リンク、フィルター |
| 明細詳細・レビュー | 原文と正規化値の比較、警告理由、AI提案、承認・却下・保留、理由入力 |

赤は「処理不能または重大」、黄は「人による確認が必要」と画面上に固定表示し、色だけに依存しない。キーボード操作とテキストラベルも提供する。

## 11. Spring AI の利用範囲

### 利用する処理

* 未マッピング費目の標準費目候補を最大3件提示する。
* `交通日` のような誤記・表記揺れの候補を提示する。
* 備考の意味的類似を補助し、類似重複の説明文を作成する。
* 経理担当者向けに警告理由を短く説明する。

### 利用しない処理

* 金額空欄の推定・補完
* 不正日付の自動修正
* 承認・却下の自動決定
* 決定的な合計計算・税計算・閾値比較

AI応答は JSON スキーマで検証し、`confidence` が設定値未満、形式不正、タイムアウトの場合は提案なしとして処理を継続する。原文の個人情報を外部モデルへ送る場合は、利用規約・データ保持・マスキング方針を別途確認する。

## 12. 開発フェーズ

### Phase 1: 基盤・取込

* Spring Boot、React-Vite、H2、JPA、FlywayまたはLiquibaseの初期設定
* DBマスター、ルールバージョン、変更監査、管理者権限の実装
* ファイルアップロード、ハッシュによる同一ファイル検出、文字コード検出
* 部署別CSVアダプターと元データ保存

### Phase 2: 正規化・決定的検証

* 日付、金額、氏名、費目、備考の正規化
* 必須、日付、対象月、完全重複、負数、高額、未マッピングのルール実装
* 検証結果・警告API・H2永続化

### Phase 3: UI・レビュー・集計

* アップロード画面、サマリーカード、履歴テーブル、警告一覧
* 明細詳細、承認・却下・保留、レビュー理由、監査ログ
* 承認済みだけを対象とする集計・CSV出力

### Phase 4: AI補助・品質向上

* Spring AIアダプター、構造化出力検証、タイムアウト・フォールバック
* 類似重複と費目候補の補助
* 権限管理、運用ログ、性能・セキュリティ検証

## 13. 受入基準とテスト

| 区分 | 受入基準 |
| --- | --- |
| 文字コード | CP932とUTF-8 BOMのCSVを文字化けなく取り込める |
| 日付 | 和暦・西暦・月日形式を変換でき、6月31日のような日付を赤警告にできる |
| 金額 | 全角数字・カンマ付き数値を正しく数値化し、金額欠損を赤警告にできる |
| 重複 | 同一の5項目が一致する明細を赤警告としてグループ表示できる |
| 類似 | 同一申請者・近接日・同額・類似備考を黄警告として表示できる |
| 高額 | 管理画面で変更・承認した費目別しきい値が、再起動なしに次回取込へ反映される |
| 集計 | PENDING、REJECTED、未解決REDの明細が承認済み合計に含まれない |
| 追跡性 | 任意の正規化明細から元ファイル、元行、警告、レビュー履歴を参照できる |
| AI障害 | AIが失敗してもCSV取込と決定的検証が完了し、AI提案なしで画面表示できる |

## 14. リスクと対応

| リスク | 対応 |
| --- | --- |
| CSVの非標準形式 | 行単位エラー隔離、原文保存、エラー詳細表示、部署別アダプター化 |
| 閾値の誤設定 | `application.yml` のプロファイル分離、設定変更監査、テストで境界値確認 |
| AIの誤提案 | 提案扱いに限定し、確定処理をJavaと人間のレビューに限定 |
| H2の運用制約 | 初期段階ではファイルモードで永続化し、運用移行時はRDBMSへ移行可能な設計を維持 |
| 個人情報の外部送信 | AI送信前のマスキング、閉域モデルの検討、送信ログと保持方針の確認 |

## 15. 完了条件

* 3部署のサンプルCSVを取り込み、共通フォーマットで一覧表示できる。
* 金額欠損、不正日付、完全重複、対象月外、高額、費目未マッピングを規定どおりに表示できる。
* 経理担当者が明細をレビューし、承認済みだけをサマリーに反映できる。
* 主要ルールとマスターをDBの管理画面から変更でき、承認・バージョン管理・監査を経て再起動なしに反映できる。
* 取込元・元行・判定理由・レビュー履歴を追跡できる。

## 16. 改訂で追加した非機能要件

| 分類 | 要件 |
| --- | --- |
| 認証・認可 | ADMIN、ACCOUNTING_REVIEWER、DEPARTMENT_MANAGER、UPLOADER、VIEWERを分け、API・画面の両方で認可する |
| 可用性 | ファイル取込・再検証はジョブ化し、失敗時に状態、失敗行、再実行可否を提示する。二重実行を防ぐ |
| 性能 | 一覧はサーバー側ページング・検索を使い、大きなCSVはストリーミングで処理して全件をメモリに載せない |
| セキュリティ | ファイル名を信用せず、サイズ・MIME・拡張子・CSV構文を検査する。アップロード保存先はWeb公開領域から分離する |
| データ保護 | H2ファイルのバックアップ、リストア手順、保持期間、テスト環境への本番データ持出し制限を定義する |
| 同時更新 | レビューとルール編集に楽観ロックを適用し、競合時は差分を示して再編集させる |
| 運用監視 | 取込失敗率、警告発生率、AI失敗率、処理時間、DB容量を計測し、異常時にログと通知を残す |
| アクセシビリティ | 赤・黄だけで意味を伝えず、状態テキスト、アイコン、説明、キーボード操作を提供する |
| データ移行性 | H2固有SQLへの依存を抑え、将来PostgreSQLへ移行できるようFlyway/LiquibaseとJPAでスキーマを管理する |

## 17. 改訂後の完了条件

* 管理者が費目マッピング、しきい値、対象月、重複条件、CSVスキーマをDBから変更できる。
* 変更はバージョン、有効期間、変更理由、操作者、承認者を持ち、過去の取込結果を再現できる。
* 新規追加のヒューマンエラー検証を含め、警告の根拠・設定値・適用ルールバージョンを明細画面で確認できる。
* ルール変更後に対象範囲をプレビュー・再検証でき、旧新の判定差分と集計影響を確認してから採用できる。

```

```