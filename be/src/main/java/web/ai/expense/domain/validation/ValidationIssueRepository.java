package web.ai.expense.domain.validation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ValidationIssueRepository extends JpaRepository<ValidationIssue, UUID> {

    List<ValidationIssue> findByExpenseId(UUID expenseId);

    List<ValidationIssue> findByExpenseIdIn(List<UUID> expenseIds);

    List<ValidationIssue> findBySeverity(Severity severity);

    void deleteByExpenseId(UUID expenseId);

    /** 未解決のREDを持つ明細を集計から外すために使う（計画書 7・13）。 */
    List<ValidationIssue> findBySeverityAndResolved(Severity severity, boolean resolved);
}
