package web.ai.expense.domain.validation.rules;

import org.springframework.stereotype.Component;
import web.ai.expense.domain.validation.*;

import java.util.Optional;

/**
 * AMOUNT_ZERO（計画書 8.5）。0円の申請。誤入力・取消漏れ・対象外明細の可能性を確認する。
 *
 * <p>0円は集計に影響しないので放置しても合計は狂わないが、放置すると「なぜ0円で申請したのか」
 * が誰にも分からないまま承認される。金額欄の入力ミス（桁を消した、コピー漏れ）の可能性が
 * あるので、確認だけはさせる。取消漏れであれば却下すべき明細でもある。
 *
 * <p>負数は {@link NegativeAmountRule} の担当。0を負数と一緒に扱うと「返金の確認」という
 * 誤った対応を促してしまう。
 */
@Component
public class AmountZeroRule implements RowRule {

    @Override
    public RuleCode code() {
        return RuleCode.AMOUNT_ZERO;
    }

    @Override
    public Optional<ValidationIssue> evaluate(ExpenseEvaluation target, RuleContext context) {
        if (!context.zeroAmountWarning()) {
            return Optional.empty();
        }
        Long amount = target.expense().getAmountYen();
        if (amount == null || amount != 0L) {
            return Optional.empty();
        }
        return Optional.of(ValidationIssue.of(
                RuleCode.AMOUNT_ZERO,
                "金額=0円（原文=" + nullSafe(target.raw().getRawAmount()) + "）。"
                        + "入力漏れ・取消漏れ・対象外明細のいずれかを確認してください"));
    }

    private String nullSafe(String value) {
        return value == null ? "(空)" : value;
    }
}
