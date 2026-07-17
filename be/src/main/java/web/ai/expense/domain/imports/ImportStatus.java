package web.ai.expense.domain.imports;

public enum ImportStatus {
    /** 行が1件以上取り込めた（行単位の警告は別途 validation_issues に入る）。 */
    COMPLETED,
    /** ファイル単位で失敗。文字コード判定不能、ヘッダー不一致など。 */
    FAILED
}
