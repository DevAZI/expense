package web.ai.expense.application.validation;

import org.springframework.stereotype.Component;
import web.ai.expense.domain.expense.RiskLevel;
import web.ai.expense.domain.validation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 決定的検証の入口。行ルールを全行に適用し、続けてバッチルールを適用する。
 * AIはここに関与しない（計画書 11）。
 */
@Component
public class ValidationEngine {

    private final List<RowRule> rowRules;
    private final List<BatchRule> batchRules;

    public ValidationEngine(List<RowRule> rowRules, List<BatchRule> batchRules) {
        this.rowRules = List.copyOf(rowRules);
        this.batchRules = List.copyOf(batchRules);
    }

    /**
     * @param rows expense に id が採番済みであること（issue が expenseId を持つため）
     */
    public List<ValidationIssue> validate(List<ExpenseEvaluation> rows, RuleContext context) {
        List<ValidationIssue> issues = new ArrayList<>();

        for (ExpenseEvaluation row : rows) {
            for (RowRule rule : rowRules) {
                rule.evaluate(row, context).ifPresent(issue -> {
                    issue.setExpenseId(row.expense().getId());
                    issues.add(issue);
                });
            }
        }

        for (BatchRule rule : batchRules) {
            issues.addAll(rule.evaluate(rows, context));
        }

        return issues;
    }

    /** 明細に付いた警告のうち最も重いものを明細のリスクとする。 */
    public RiskLevel riskLevelOf(List<ValidationIssue> issuesOfExpense) {
        boolean hasRed = issuesOfExpense.stream().anyMatch(i -> i.getSeverity() == Severity.RED);
        if (hasRed) {
            return RiskLevel.RED;
        }
        return issuesOfExpense.isEmpty() ? RiskLevel.NONE : RiskLevel.YELLOW;
    }
}
