package web.ai.expense.domain.validation.rules;

import org.springframework.stereotype.Component;
import web.ai.expense.domain.validation.*;

import java.util.Optional;

/**
 * NEGATIVE_AMOUNT（計画書 7）。返金・取消は正当なので拒否せず、確認を促す YELLOW に留める。
 */
@Component
public class NegativeAmountRule implements RowRule {

    @Override
    public RuleCode code() {
        return RuleCode.NEGATIVE_AMOUNT;
    }

    @Override
    public Optional<ValidationIssue> evaluate(ExpenseEvaluation target, RuleContext context) {
        if (!context.negativeAmountWarning()) {
            return Optional.empty();
        }
        Long amount = target.expense().getAmountYen();
        if (amount == null || amount >= 0) {
            return Optional.empty();
        }
        return Optional.of(ValidationIssue.of(
                RuleCode.NEGATIVE_AMOUNT,
                "金額=" + amount + "円。返金・取消であることを確認してください"));
    }
}
