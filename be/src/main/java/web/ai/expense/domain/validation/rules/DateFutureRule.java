package web.ai.expense.domain.validation.rules;

import org.springframework.stereotype.Component;
import web.ai.expense.domain.validation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * DATE_FUTURE（計画書 8.5）。取込日より先の利用日。
 *
 * <p><b>REDにしている理由</b>: まだ発生していない経費は原理的に精算できない。日付の打ち間違い
 * （2025→2026、月の取り違え）か、前払いを利用日で申請しているかのどちらかで、いずれも
 * 原本を見ないと確定できない。PERIOD_OUT_OF_RANGE（YELLOW）と違って「確認して承認」で
 * 済ませてよい類ではないので、解決か明示的な例外承認を要求する。
 *
 * <p>基準日は {@link RuleContext#today()} から取る。LocalDate.now() を直接呼ぶと、
 * 取込が日付をまたいだときに同一ファイル内で判定がぶれる。
 */
@Component
public class DateFutureRule implements RowRule {

    @Override
    public RuleCode code() {
        return RuleCode.DATE_FUTURE;
    }

    @Override
    public Optional<ValidationIssue> evaluate(ExpenseEvaluation target, RuleContext context) {
        LocalDate usageDate = target.expense().getUsageDate();
        if (usageDate == null) {
            return Optional.empty(); // 日付が無い時点で別のREDが出ている
        }

        LocalDate limit = context.today().plusDays(context.allowedFutureDays());
        if (!usageDate.isAfter(limit)) {
            return Optional.empty();
        }

        long daysAhead = ChronoUnit.DAYS.between(context.today(), usageDate);
        return Optional.of(ValidationIssue.of(
                RuleCode.DATE_FUTURE,
                "利用日=" + usageDate + " は取込日=" + context.today() + " より " + daysAhead + "日先です"
                        + (context.allowedFutureDays() > 0 ? "（許容=" + context.allowedFutureDays() + "日）" : "")
                        + "。日付の誤りか、まだ発生していない経費の申請かを確認してください"));
    }
}
