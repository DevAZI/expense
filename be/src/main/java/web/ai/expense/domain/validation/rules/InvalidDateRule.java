package web.ai.expense.domain.validation.rules;

import org.springframework.stereotype.Component;
import web.ai.expense.domain.validation.*;
import web.ai.expense.shared.TextNormalizer;

import java.util.Optional;

/**
 * INVALID_DATE（計画書 7）。原文はあるが日付として解釈できない。例: 2026/6/31。
 * AIによる自動修正は行わない（計画書 11「利用しない処理」）。
 */
@Component
public class InvalidDateRule implements RowRule {

    @Override
    public RuleCode code() {
        return RuleCode.INVALID_DATE;
    }

    @Override
    public Optional<ValidationIssue> evaluate(ExpenseEvaluation target, RuleContext context) {
        String rawDate = TextNormalizer.blankToNull(target.raw().getRawUsageDate());
        if (rawDate == null) {
            return Optional.empty(); // 空欄は REQUIRED_FIELD_MISSING の担当
        }
        if (target.expense().getUsageDate() != null) {
            return Optional.empty();
        }
        return Optional.of(ValidationIssue.of(
                RuleCode.INVALID_DATE,
                "原文=" + rawDate + " は実在する日付として解釈できません。原本を確認してください"));
    }
}
