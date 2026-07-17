package web.ai.expense.api.expense;

import web.ai.expense.domain.validation.Severity;
import web.ai.expense.domain.validation.ValidationIssue;

import java.util.UUID;

/** 警告1件。ルールIDと根拠を必ず返す（計画書 10・17）。 */
public record IssueResponse(
        UUID id,
        UUID expenseId,
        String ruleCode,
        String severity,
        /** 色だけに頼らず意味を伝えるためのテキスト（計画書 10）。 */
        String severityLabel,
        String message,
        String evidence,
        boolean resolved
) {
    public static IssueResponse from(ValidationIssue i) {
        return new IssueResponse(
                i.getId(),
                i.getExpenseId(),
                i.getRuleCode().name(),
                i.getSeverity().name(),
                severityLabel(i.getSeverity()),
                i.getMessage(),
                i.getEvidence(),
                i.isResolved()
        );
    }

    public static String severityLabel(Severity severity) {
        return severity == Severity.RED ? "処理不能または重大" : "人による確認が必要";
    }
}
