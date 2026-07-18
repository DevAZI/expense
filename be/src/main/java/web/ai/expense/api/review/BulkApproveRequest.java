package web.ai.expense.api.review;

import jakarta.validation.constraints.NotBlank;

/**
 * 「警告なし」明細の一括承認リクエスト。
 *
 * @param reviewedBy 操作者名。認証が無いMVPでは受け取る（計画書 16）。
 */
public record BulkApproveRequest(
        @NotBlank String reviewedBy
) {
}
