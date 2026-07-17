package web.ai.expense.infrastructure.csv;

/**
 * CSV1行の原文。パーサーは解釈も検証もせず、原文をこの形に取り出すだけを担当する
 * （業務検証は共通サービスへ集約する: 計画書 9.2）。
 */
public record ParsedCsvRow(
        int rowNumber,
        String rawLine,
        String usageDate,
        String applicantName,
        String category,
        String amount,
        String note,
        /** ヘッダーの列数。 */
        int expectedColumnCount,
        /** この行の実際の列数。expected と違えば CSV_STRUCTURE_INVALID の対象。 */
        int actualColumnCount,
        /** 列が溢れていた分を備考へ結合して復元したか。 */
        boolean noteRecovered
) {
    public boolean hasColumnCountMismatch() {
        return expectedColumnCount != actualColumnCount;
    }
}
