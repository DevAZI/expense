package web.ai.expense.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import web.ai.expense.domain.expense.ExpenseCategory;
import web.ai.expense.domain.validation.RuleSnapshot;
import web.ai.expense.shared.CategoryNormalizer;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 業務ルールのMVP用の供給元。
 *
 * <p>計画書 8.1 はこれらをDBのルールテーブルで管理し管理画面から再起動なしに変更することを
 * 求めている。MVPではテーブル数を抑えるため設定に置くが、{@link RuleSnapshot} を組み立てる
 * この1クラスがDB化の際の唯一の差し替え点になるようにしてある。
 */
@ConfigurationProperties(prefix = "expense.rules")
@Validated
@Getter
@Setter
public class ExpenseRuleProperties {

    @NotNull
    private YearMonth targetYearMonth;

    private boolean negativeAmountWarning = true;

    /** 0円の申請を警告として出すか。 */
    private boolean zeroAmountWarning = true;

    /** 取込日から何日先までの利用日を許容するか。0 なら翌日以降が警告。 */
    private int allowedFutureDays = 0;

    /** 類似重複とみなす日付の近接幅（日）。 */
    private int similarDuplicateWindowDays = 3;

    private HighAmount highAmount = new HighAmount();

    /** 入力費目（原文）-> 標準費目。キーは読み込み時に正規化する。 */
    private Map<String, ExpenseCategory> categoryMappings = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class HighAmount {
        private long defaultThresholdYen = 100_000L;
        private Map<ExpenseCategory, Long> byCategory = new LinkedHashMap<>();
    }

    /**
     * 取込1回分のルールを凍結する。取込の開始時に1度だけ呼ぶこと（計画書 8.3）。
     *
     * @param targetYearMonthOverride 取込ごとの対象月。null なら設定の既定値。
     *                                月次で使う以上、対象月だけは経理が毎回指定できないと
     *                                翌月に yml を書き換えて再起動する羽目になる。
     * @param today                   取込の基準日。未来日付の判定に使う。呼び出し側が
     *                                Clock から与えることで、テストで日付を固定できる。
     */
    public RuleSnapshot toSnapshot(YearMonth targetYearMonthOverride, LocalDate today) {
        Map<String, ExpenseCategory> normalizedMappings = new HashMap<>();
        categoryMappings.forEach((source, category) -> {
            String key = CategoryNormalizer.toMappingKey(source);
            if (key != null) {
                normalizedMappings.put(key, category);
            }
        });

        return new RuleSnapshot(
                targetYearMonthOverride != null ? targetYearMonthOverride : targetYearMonth,
                today,
                allowedFutureDays,
                highAmount.getDefaultThresholdYen(),
                highAmount.getByCategory(),
                negativeAmountWarning,
                zeroAmountWarning,
                similarDuplicateWindowDays,
                normalizedMappings
        );
    }
}
