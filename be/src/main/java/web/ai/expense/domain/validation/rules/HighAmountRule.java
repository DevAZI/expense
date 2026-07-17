package web.ai.expense.domain.validation.rules;

import org.springframework.stereotype.Component;
import web.ai.expense.domain.validation.*;

import java.util.Optional;

/**
 * HIGH_AMOUNT（計画書 7）。費目別しきい値を超えた金額に領収書・承認根拠の確認を促す。
 * しきい値は必ず RuleContext から取る（ルールが設定やDBを直接読まない理由は RuleContext 参照）。
 */
@Component
public class HighAmountRule implements RowRule {

    @Override
    public RuleCode code() {
        return RuleCode.HIGH_AMOUNT;
    }

    @Override
    public Optional<ValidationIssue> evaluate(ExpenseEvaluation target, RuleContext context) {
        Long amount = target.expense().getAmountYen();
        if (amount == null) {
            return Optional.empty();
        }
        long threshold = context.highAmountThresholdYen(target.expense().getCategory());
        if (amount <= threshold) {
            return Optional.empty();
        }
        return Optional.of(ValidationIssue.of(
                RuleCode.HIGH_AMOUNT,
                "金額=" + amount + "円 > しきい値=" + threshold + "円"
                        + (target.expense().getCategory() == null ? "（費目未確定のため既定値を適用）" : "（費目=" + target.expense().getCategory() + "）")));
    }
}
