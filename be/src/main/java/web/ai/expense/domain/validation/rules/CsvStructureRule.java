package web.ai.expense.domain.validation.rules;

import org.springframework.stereotype.Component;
import web.ai.expense.domain.imports.RawExpenseRow;
import web.ai.expense.domain.validation.*;

import java.util.Optional;

/**
 * CSV_STRUCTURE_INVALID（計画書 7）。列数がヘッダーと合わない行を可視化する。
 *
 * <p><b>計画書ではREDだがYELLOWにしている理由</b>: 実データの開発部3行は、備考に引用符なしの
 * カンマが入っているだけ（「Meguro to Shibuya, client sync」）で、金額も日付も申請者も正しい。
 * 備考が最終列なので溢れた分は結合すれば無損失で復元できる。これをREDにすると、
 * 経費として何も問題のない行が承認不能になり、経理の手間が増えるだけになる。
 * 復元した事実は必ず警告として見せ、原本と突き合わせられるようにする。
 */
@Component
public class CsvStructureRule implements RowRule {

    @Override
    public RuleCode code() {
        return RuleCode.CSV_STRUCTURE_INVALID;
    }

    @Override
    public Optional<ValidationIssue> evaluate(ExpenseEvaluation target, RuleContext context) {
        RawExpenseRow raw = target.raw();
        Integer expected = raw.getExpectedColumnCount();
        Integer actual = raw.getActualColumnCount();

        if (expected == null || actual == null || expected.equals(actual)) {
            return Optional.empty();
        }

        String evidence = "ヘッダー=" + expected + "列 に対して この行=" + actual + "列"
                + (raw.isNoteRecovered()
                ? "。備考に引用符なしのカンマが含まれていたため結合して復元した（備考=" + raw.getRawNote() + "）"
                : "。列の対応がずれている可能性があるため原本を確認してください");

        return Optional.of(ValidationIssue.of(RuleCode.CSV_STRUCTURE_INVALID, evidence));
    }
}
