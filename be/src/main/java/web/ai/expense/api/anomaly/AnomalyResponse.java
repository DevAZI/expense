package web.ai.expense.api.anomaly;

import web.ai.expense.api.expense.IssueResponse;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.validation.ValidationIssue;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 警告1件 + 対象明細の要約（計画書 10 異常値警告セクション）。
 *
 * <p>警告だけを返すと画面側が明細を引き直すことになり、経理は「どの申請の話か」を
 * 一覧で把握できない。一覧で判断できるだけの文脈を最初から添える。
 */
public record AnomalyResponse(
        UUID issueId,
        UUID expenseId,
        String ruleCode,
        String severity,
        String severityLabel,
        String message,
        String evidence,
        boolean resolved,
        // 対象明細の要約
        LocalDate usageDate,
        String departmentLabel,
        String applicantName,
        String categoryLabel,
        Long amountYen,
        String note,
        String statusLabel,
        Integer sourceRowNumber
) {
    public static AnomalyResponse of(ValidationIssue issue, Expense expense) {
        IssueResponse i = IssueResponse.from(issue);
        return new AnomalyResponse(
                i.id(),
                i.expenseId(),
                i.ruleCode(),
                i.severity(),
                i.severityLabel(),
                i.message(),
                i.evidence(),
                i.resolved(),
                expense == null ? null : expense.getUsageDate(),
                expense == null || expense.getDepartment() == null ? null : expense.getDepartment().label(),
                expense == null ? null : expense.getApplicantName(),
                expense == null || expense.getCategory() == null ? null : expense.getCategory().label(),
                expense == null ? null : expense.getAmountYen(),
                expense == null ? null : expense.getNote(),
                expense == null ? null : statusLabel(expense),
                expense == null ? null : expense.getSourceRowNumber()
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
