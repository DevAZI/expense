package web.ai.expense.application.review;

/**
 * 一括承認の結果。
 *
 * @param approvedCount 承認した明細の件数
 */
public record BulkApproveResult(int approvedCount) {
}
