package web.ai.expense.domain.validation;

import web.ai.expense.domain.expense.ExpenseCategory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

/**
 * 取込1回分のルールパラメータを凍結した不変スナップショット（計画書 8.3）。
 *
 * <p>将来DBでルールを管理する際は、ここに rule_version_id を持たせて import_files と
 * 各検証結果へ保存すれば、過去の取込結果を再現できるようになる（計画書 17）。
 */
public record RuleSnapshot(
        YearMonth targetYearMonth,
        /** 取込の基準日。取込開始時に凍結する。 */
        LocalDate today,
        int allowedFutureDays,
        long defaultHighAmountThresholdYen,
        Map<ExpenseCategory, Long> highAmountThresholdsByCategory,
        boolean negativeAmountWarning,
        boolean zeroAmountWarning,
        int similarDuplicateWindowDays,
        Map<String, ExpenseCategory> categoryMappings
) implements RuleContext {

    public RuleSnapshot {
        highAmountThresholdsByCategory = Map.copyOf(highAmountThresholdsByCategory);
        categoryMappings = Map.copyOf(categoryMappings);
    }

    @Override
    public long highAmountThresholdYen(ExpenseCategory category) {
        if (category == null) {
            return defaultHighAmountThresholdYen;
        }
        return highAmountThresholdsByCategory.getOrDefault(category, defaultHighAmountThresholdYen);
    }

    @Override
    public Optional<ExpenseCategory> mapCategory(String normalizedSourceValue) {
        if (normalizedSourceValue == null || normalizedSourceValue.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(categoryMappings.get(normalizedSourceValue));
    }
}
