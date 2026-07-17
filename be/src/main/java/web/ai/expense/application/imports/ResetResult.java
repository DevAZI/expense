package web.ai.expense.application.imports;

/**
 * 初期化で削除した件数。
 *
 * <p>「消えました」だけ返すと、経理は何がどれだけ消えたのか分からない。取り返しがつかない
 * 操作なので、実際に消した件数をそのまま返して画面に出す。
 */
public record ResetResult(
        long importFiles,
        long rawRows,
        long expenses,
        long issues
) {
}
