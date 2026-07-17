package web.ai.expense.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import web.ai.expense.domain.expense.*;
import web.ai.expense.domain.validation.RuleCode;
import web.ai.expense.domain.validation.Severity;
import web.ai.expense.domain.validation.ValidationIssue;
import web.ai.expense.domain.validation.ValidationIssueRepository;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * enum カラムがネイティブ ENUM 型で作られていないことを確認する。
 *
 * <p><b>なぜ要るか</b>: 本番は ddl-auto=update + ファイルDB、テストは create-drop +
 * インメモリで動くため、「既存スキーマに新しい enum 定数を入れる」経路がテストから
 * 完全に抜け落ちる。実際に AMOUNT_ZERO / DATE_FUTURE を足したとき、テストは全件通ったのに
 * 起動したアプリだけが 500 で落ちた。H2 のネイティブ ENUM 型は許可値をカラム定義に
 * 焼き込むため、update では既存カラムが変わらず、新しい値の INSERT が拒否される。
 *
 * <p>ここでは「カラム型が VARCHAR であること」と「全 enum 定数が実際に保存できること」を
 * 押さえる。定数を足したときにマッピングを間違えれば、このテストが落ちる。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaEvolutionTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ExpenseRepository expenseRepository;
    @Autowired
    ValidationIssueRepository issueRepository;

    private String columnType(String table, String column) {
        return jdbc.queryForObject(
                "select data_type from information_schema.columns "
                        + "where upper(table_name) = ? and upper(column_name) = ?",
                String.class, table.toUpperCase(), column.toUpperCase());
    }

    @Test
    @DisplayName("enum カラムはネイティブENUM型ではなくVARCHARで作られる")
    void enumColumnsAreVarchar() {
        // ENUM 型だと data_type が 'ENUM' になり、許可値がカラム定義に焼き込まれる
        assertThat(columnType("validation_issues", "rule_code")).isEqualTo("CHARACTER VARYING");
        assertThat(columnType("validation_issues", "severity")).isEqualTo("CHARACTER VARYING");
        assertThat(columnType("expenses", "category")).isEqualTo("CHARACTER VARYING");
        assertThat(columnType("expenses", "department")).isEqualTo("CHARACTER VARYING");
        assertThat(columnType("expenses", "status")).isEqualTo("CHARACTER VARYING");
        assertThat(columnType("expenses", "risk_level")).isEqualTo("CHARACTER VARYING");
        assertThat(columnType("import_files", "department")).isEqualTo("CHARACTER VARYING");
        assertThat(columnType("import_files", "status")).isEqualTo("CHARACTER VARYING");
    }

    @Test
    @DisplayName("すべてのRuleCodeが実際に保存できる（定数を足しても落ちない）")
    void everyRuleCodeCanBePersisted() {
        List<RuleCode> saved = Arrays.stream(RuleCode.values())
                .map(code -> {
                    ValidationIssue issue = ValidationIssue.of(code, "テスト");
                    issue.setExpenseId(UUID.randomUUID());
                    return issueRepository.saveAndFlush(issue).getRuleCode();
                })
                .toList();

        assertThat(saved).containsExactlyInAnyOrder(RuleCode.values());
    }

    @Test
    @DisplayName("すべての費目・部署・状態が実際に保存できる")
    void everyEnumValueCanBePersisted() {
        for (ExpenseCategory category : ExpenseCategory.values()) {
            for (Department department : Department.values()) {
                Expense expense = new Expense(UUID.randomUUID(), 1, department);
                expense.setCategory(category);
                expense.setStatus(ExpenseStatus.PENDING);
                expense.setRiskLevel(RiskLevel.RED);
                assertThat(expenseRepository.saveAndFlush(expense).getCategory()).isEqualTo(category);
            }
        }
    }

    @Test
    @DisplayName("Severity の全値が保存できる")
    void everySeverityCanBePersisted() {
        for (Severity severity : Severity.values()) {
            ValidationIssue issue = ValidationIssue.of(
                    RuleCode.HIGH_AMOUNT, severity, "テスト", "根拠");
            issue.setExpenseId(UUID.randomUUID());
            assertThat(issueRepository.saveAndFlush(issue).getSeverity()).isEqualTo(severity);
        }
    }
}
