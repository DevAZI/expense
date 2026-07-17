package web.ai.expense.api.expense;

import web.ai.expense.domain.expense.Expense;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 一覧用（計画書 10 全体履歴テーブル）。
 *
 * <p>コードと日本語ラベルを両方返す。画面側で enum 名を日本語へ訳す表を持つと、
 * 費目を足したときにバックエンドと画面の二箇所を直すことになり、片方だけ直して
 * 「TRANSPORTATION」と生の英字が経理の画面に出る事故になりやすい。
 */
public record ExpenseResponse(
        UUID id,
        LocalDate usageDate,
        String department,
        String departmentLabel,
        String applicantName,
        String category,
        String categoryLabel,
        String categoryGroup,
        String categoryGroupLabel,
        Long amountYen,
        String note,
        String status,
        String statusLabel,
        String riskLevel,
        UUID sourceFileId,
        Integer sourceRowNumber,
        String reviewReason,
        String reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {
    public static ExpenseResponse from(Expense e) {
        var category = e.getCategory();
        return new ExpenseResponse(
                e.getId(),
                e.getUsageDate(),
                e.getDepartment() == null ? null : e.getDepartment().name(),
                e.getDepartment() == null ? null : e.getDepartment().label(),
                e.getApplicantName(),
                category == null ? null : category.name(),
                category == null ? null : category.label(),
                category == null ? null : category.group().name(),
                category == null ? null : category.group().label(),
                e.getAmountYen(),
                e.getNote(),
                e.getStatus().name(),
                statusLabel(e),
                e.getRiskLevel().name(),
                e.getSourceFileId(),
                e.getSourceRowNumber(),
                e.getReviewReason(),
                e.getReviewedBy(),
                e.getReviewedAt(),
                e.getCreatedAt()
        );
    }

    private static String statusLabel(Expense e) {
        return switch (e.getStatus()) {
            case PENDING -> "未確認";
            case APPROVED -> "承認済み";
            case REJECTED -> "却下";
        };
    }
}
