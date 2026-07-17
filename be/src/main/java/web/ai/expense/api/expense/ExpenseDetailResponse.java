package web.ai.expense.api.expense;

import web.ai.expense.domain.imports.RawExpenseRow;

import java.util.List;

/**
 * 明細詳細（計画書 10）。原文と正規化値を並べて返し、警告の根拠も添える。
 * 「なぜこの判定になったか」を画面だけで追えるようにするためのDTO。
 */
public record ExpenseDetailResponse(
        ExpenseResponse expense,
        RawValues raw,
        List<IssueResponse> issues
) {
    public record RawValues(
            Integer rowNumber,
            String rawLine,
            String usageDate,
            String applicantName,
            String category,
            String amount,
            String note
    ) {
        public static RawValues from(RawExpenseRow r) {
            if (r == null) {
                return null;
            }
            return new RawValues(
                    r.getRowNumber(),
                    r.getRawLine(),
                    r.getRawUsageDate(),
                    r.getRawApplicantName(),
                    r.getRawCategory(),
                    r.getRawAmount(),
                    r.getRawNote()
            );
        }
    }
}
