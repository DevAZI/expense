package web.ai.expense.domain.validation.rules;

import org.springframework.stereotype.Component;
import web.ai.expense.domain.validation.*;

import java.time.YearMonth;
import java.util.Optional;

/**
 * PERIOD_OUT_OF_RANGE（計画書 7）。利用日が取込対象月の外。月またぎ精算かどうかを人が確認する。
 */
@Component
public class PeriodOutOfRangeRule implements RowRule {

    @Override
    public RuleCode code() {
        return RuleCode.PERIOD_OUT_OF_RANGE;
    }

    @Override
    public Optional<ValidationIssue> evaluate(ExpenseEvaluation target, RuleContext context) {
        var usageDate = target.expense().getUsageDate();
        if (usageDate == null) {
            return Optional.empty(); // 日付が無い時点で別のREDが出ている
        }
        YearMonth actual = YearMonth.from(usageDate);
        YearMonth expected = context.targetYearMonth();
        if (actual.equals(expected)) {
            return Optional.empty();
        }
        return Optional.of(ValidationIssue.of(
                RuleCode.PERIOD_OUT_OF_RANGE,
                "利用日=" + usageDate + " は対象月=" + expected + " の外です。月またぎ精算か確認してください"));
    }
}
