package web.ai.expense.application.ai;

import java.util.List;
import java.util.UUID;

/**
 * AI補助の結果（助言専用・非ブロッキング）。
 *
 * <p>この結果は決定的ルールの {@code ValidationIssue} とは別物で、DBには保存せず、
 * 承認ゲートにもリスク判定にも影響しない。あくまで経理が原本確認するための「参考情報」。
 *
 * @param enabled       設定でAI補助が有効か
 * @param available     実際にモデルを呼べたか（無効・資格情報無し・呼出失敗なら false）
 * @param model         使用したモデルID（呼べた場合）
 * @param message       状態の説明（無効・失敗の理由など）。画面にそのまま出せる日本語
 * @param analyzedCount 実際にAIへ渡した明細件数
 * @param anomalies     決定的ルールでは拾えない異常の候補
 * @param typos         誤字脱字・表記の候補
 */
public record AiAdvisoryResult(
        boolean enabled,
        boolean available,
        String model,
        String message,
        int analyzedCount,
        List<AiAnomaly> anomalies,
        List<AiTypo> typos
) {

    /** AIが指摘した異常の候補1件。 */
    public record AiAnomaly(
            UUID expenseId,
            int sourceRowNumber,
            String department,
            String applicantName,
            String category,
            Long amountYen,
            /** AIの見立てた重要度（HIGH / MEDIUM / LOW）。RED/YELLOWとは別体系。 */
            String severity,
            /** 観点の種別（DIGIT_ERROR / SPLIT_TRANSACTION / CATEGORY_MISMATCH /
             *  BUSINESS_PURPOSE_CONFIRMATION / TIME_PATTERN / OTHER）。 */
            String anomalyType,
            String reason,
            /** 分割申請など比較対象がある場合の、関連明細の sourceRowNumber。 */
            List<Integer> relatedRowNumbers
    ) {}

    /** AIが指摘した誤字脱字・表記の候補1件。 */
    public record AiTypo(
            UUID expenseId,
            int sourceRowNumber,
            /** 対象フィールド（note / applicantName / category など）。 */
            String field,
            String original,
            String suggestion,
            String reason
    ) {}

    static AiAdvisoryResult disabled() {
        return new AiAdvisoryResult(false, false, null,
                "AI補助は設定で無効です（expense.ai.enabled=false）。", 0, List.of(), List.of());
    }

    static AiAdvisoryResult unavailable(String message) {
        return new AiAdvisoryResult(true, false, null, message, 0, List.of(), List.of());
    }

    static AiAdvisoryResult empty(String model, int analyzedCount, String message) {
        return new AiAdvisoryResult(true, true, model, message, analyzedCount, List.of(), List.of());
    }
}
