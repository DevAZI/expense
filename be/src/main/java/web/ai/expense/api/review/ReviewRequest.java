package web.ai.expense.api.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import web.ai.expense.domain.expense.ExpenseStatus;

public record ReviewRequest(
        @NotNull ExpenseStatus decision,
        String reason,
        /** 認証が無いMVPでは操作者名を受け取る。認可導入時は認証情報から取る（計画書 16）。 */
        @NotBlank String reviewedBy,
        /** 未解決REDの明細を例外承認する場合のみ true。理由が必須になる。 */
        boolean overrideRedWarnings
) {
}
