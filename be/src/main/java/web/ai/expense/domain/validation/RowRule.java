package web.ai.expense.domain.validation;

import java.util.Optional;

/** 1行だけを見て判定できるルール。 */
public interface RowRule {

    RuleCode code();

    Optional<ValidationIssue> evaluate(ExpenseEvaluation target, RuleContext context);
}
