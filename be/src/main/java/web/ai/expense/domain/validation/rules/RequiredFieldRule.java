package web.ai.expense.domain.validation.rules;

import org.springframework.stereotype.Component;
import web.ai.expense.domain.validation.*;
import web.ai.expense.shared.TextNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REQUIRED_FIELD_MISSING（計画書 7）。
 *
 * <p>日付と費目については「原文が空」の場合だけをここで扱う。原文があるのに値にできない場合は
 * INVALID_DATE / CATEGORY_UNMAPPED がそれぞれ担当する。同じ1つの事象に2つの警告が付くと、
 * 経理が原本を確認すべきなのか費目を選ぶべきなのか読み取れなくなるため。
 */
@Component
public class RequiredFieldRule implements RowRule {

    @Override
    public RuleCode code() {
        return RuleCode.REQUIRED_FIELD_MISSING;
    }

    @Override
    public Optional<ValidationIssue> evaluate(ExpenseEvaluation target, RuleContext context) {
        var expense = target.expense();
        var raw = target.raw();
        List<String> missing = new ArrayList<>();

        if (TextNormalizer.blankToNull(raw.getRawUsageDate()) == null) {
            missing.add("日付");
        }
        if (expense.getApplicantName() == null) {
            missing.add("申請者");
        }
        if (TextNormalizer.blankToNull(raw.getRawCategory()) == null) {
            missing.add("費目");
        }
        if (expense.getAmountYen() == null) {
            // 空欄と「約5000」のような非数値の両方がここに来る。どちらも承認可能な金額が
            // 存在しないという意味では同じで、原本確認という対応も同じ。
            missing.add("金額");
        }

        if (missing.isEmpty()) {
            return Optional.empty();
        }

        String evidence = "欠損項目=" + String.join("/", missing)
                + ", 原文[日付=" + nullSafe(raw.getRawUsageDate())
                + ", 申請者=" + nullSafe(raw.getRawApplicantName())
                + ", 費目=" + nullSafe(raw.getRawCategory())
                + ", 金額=" + nullSafe(raw.getRawAmount()) + "]";

        return Optional.of(ValidationIssue.of(RuleCode.REQUIRED_FIELD_MISSING, evidence));
    }

    private String nullSafe(String value) {
        return value == null ? "(空)" : value;
    }
}
