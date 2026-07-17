package web.ai.expense.api.support;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import web.ai.expense.config.ExpenseRuleProperties;
import web.ai.expense.domain.expense.Department;
import web.ai.expense.domain.expense.ExpenseCategory;
import web.ai.expense.domain.validation.RuleCode;

import java.util.Arrays;
import java.util.List;

/**
 * 画面のフィルター選択肢。
 *
 * <p>費目やルールを増やしたときに画面側の定数を直し忘れると、選択肢に出てこないだけの
 * 静かな不整合になる。定義元のenumから配ってその事故を無くす。
 */
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final ExpenseRuleProperties ruleProperties;

    public record Option(String code, String label) {
    }

    public record RuleOption(String code, String label, String severity, String description) {
    }

    public record Meta(
            /** 取込画面の対象月の初期値。取込ごとに変更できる。 */
            String defaultTargetYearMonth,
            List<Option> departments,
            List<Option> categories,
            List<Option> statuses,
            List<Option> riskLevels,
            List<RuleOption> rules
    ) {
    }

    @GetMapping
    public Meta meta() {
        return new Meta(
                ruleProperties.getTargetYearMonth().toString(),
                Arrays.stream(Department.values())
                        .map(d -> new Option(d.name(), d.label())).toList(),
                Arrays.stream(ExpenseCategory.values())
                        .map(c -> new Option(c.name(), c.label())).toList(),
                List.of(new Option("PENDING", "未確認"),
                        new Option("APPROVED", "承認済み"),
                        new Option("REJECTED", "却下")),
                List.of(new Option("RED", "処理不能または重大"),
                        new Option("YELLOW", "人による確認が必要"),
                        new Option("NONE", "警告なし")),
                Arrays.stream(RuleCode.values())
                        .map(r -> new RuleOption(r.name(), r.name(),
                                r.defaultSeverity().name(), r.description())).toList()
        );
    }
}
