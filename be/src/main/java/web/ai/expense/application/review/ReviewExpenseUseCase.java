package web.ai.expense.application.review;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.expense.ExpenseRepository;
import web.ai.expense.domain.expense.ExpenseStatus;
import web.ai.expense.domain.validation.Severity;
import web.ai.expense.domain.validation.ValidationIssue;
import web.ai.expense.domain.validation.ValidationIssueRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 経理担当者による承認・却下・保留（計画書 5-9）。
 *
 * <p>承認のゲートがここにある。REDが未解決のまま承認できてしまうと、計画書 7 の
 * 「赤警告を持つ明細は解決または明示的な例外承認まで集計に含めない」が崩れる。
 */
@Service
@RequiredArgsConstructor
public class ReviewExpenseUseCase {

    private final ExpenseRepository expenseRepository;
    private final ValidationIssueRepository issueRepository;

    @Transactional
    public Expense execute(UUID expenseId, ExpenseStatus decision, String reason,
                           String reviewedBy, boolean overrideRedWarnings) {

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new NoSuchElementException("明細が見つかりません: " + expenseId));

        List<ValidationIssue> issues = issueRepository.findByExpenseId(expenseId);

        if (decision == ExpenseStatus.APPROVED) {
            List<ValidationIssue> unresolvedRed = issues.stream()
                    .filter(i -> i.getSeverity() == Severity.RED && !i.isResolved())
                    .toList();

            if (!unresolvedRed.isEmpty() && !overrideRedWarnings) {
                String codes = unresolvedRed.stream()
                        .map(i -> i.getRuleCode().name())
                        .collect(Collectors.joining(", "));
                throw new ReviewNotAllowedException(
                        "未解決の赤警告があるため承認できません(" + codes + ")。"
                                + "例外承認する場合は overrideRedWarnings=true と理由を指定してください");
            }

            if (!unresolvedRed.isEmpty() && (reason == null || reason.isBlank())) {
                throw new ReviewNotAllowedException("赤警告の例外承認には理由が必要です");
            }

            // 承認したら、その明細に付いていた警告は確認済みとして解決扱いにする。
            issues.forEach(i -> i.setResolved(true));
            issueRepository.saveAll(issues);
        }

        expense.review(decision, reason, reviewedBy);
        return expenseRepository.save(expense);
    }
}
