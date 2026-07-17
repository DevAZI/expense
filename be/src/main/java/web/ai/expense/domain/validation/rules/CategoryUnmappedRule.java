package web.ai.expense.domain.validation.rules;

import org.springframework.stereotype.Component;
import web.ai.expense.domain.validation.*;
import web.ai.expense.shared.TextNormalizer;

import java.util.Optional;

/**
 * CATEGORY_UNMAPPED（計画書 7）。原文はあるが標準費目に寄せられない。例: 「交通日」。
 *
 * <p>誤記をマッピング表に足して黙って通すことはしない。計画書 11 では費目候補の提示は
 * AI の担当で、確定は人が行うと定めているため、MVPでは警告として人に返すところまでを担う。
 */
@Component
public class CategoryUnmappedRule implements RowRule {

    @Override
    public RuleCode code() {
        return RuleCode.CATEGORY_UNMAPPED;
    }

    @Override
    public Optional<ValidationIssue> evaluate(ExpenseEvaluation target, RuleContext context) {
        String rawCategory = TextNormalizer.blankToNull(target.raw().getRawCategory());
        if (rawCategory == null) {
            return Optional.empty(); // 空欄は REQUIRED_FIELD_MISSING の担当
        }
        if (target.expense().getCategory() != null) {
            return Optional.empty();
        }
        return Optional.of(ValidationIssue.of(
                RuleCode.CATEGORY_UNMAPPED,
                "原文=" + rawCategory + " に対応する標準費目がありません。マッピング追加または人手選択が必要です"));
    }
}
