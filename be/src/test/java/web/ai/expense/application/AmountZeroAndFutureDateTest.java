package web.ai.expense.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import web.ai.expense.application.imports.ImportExpenseFileUseCase;
import web.ai.expense.application.imports.ImportResult;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.expense.ExpenseRepository;
import web.ai.expense.domain.expense.RiskLevel;
import web.ai.expense.domain.validation.RuleCode;
import web.ai.expense.domain.validation.ValidationIssue;
import web.ai.expense.domain.validation.ValidationIssueRepository;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AMOUNT_ZERO と DATE_FUTURE。
 *
 * <p>取込日を 2026-07-17 に固定する。実時刻に依存させると「明日」を書いたテストが翌日には
 * 意味を失い、境界（当日は未来ではない）も検証できない。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AmountZeroAndFutureDateTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-07-17T10:00:00Z"), ZONE);
        }
    }

    @Autowired
    ImportExpenseFileUseCase importUseCase;
    @Autowired
    ExpenseRepository expenseRepository;
    @Autowired
    ValidationIssueRepository issueRepository;

    private ImportResult importCsv(String body) {
        String csv = "日付,申請者,勘定科目,金額(円),備考\n" + body;
        return importUseCase.execute("t.csv", csv.getBytes(StandardCharsets.UTF_8), null, YearMonth.of(2026, 7));
    }

    // --- AMOUNT_ZERO ---

    @Test
    @DisplayName("0円の申請を警告する")
    void flagsZeroAmount() {
        ImportResult r = importCsv("2026/7/1,佐藤 健一,交通費,0,先方都合でキャンセル\n");
        Expense row = rows(r).get(0);

        assertThat(row.getAmountYen()).isZero();
        assertThat(codesOf(row)).contains(RuleCode.AMOUNT_ZERO);
        assertThat(row.getRiskLevel()).isEqualTo(RiskLevel.YELLOW);
    }

    @Test
    @DisplayName("0円は負数の警告とは別物として扱う（対応が違うため）")
    void zeroIsNotReportedAsNegative() {
        ImportResult r = importCsv("2026/7/1,佐藤 健一,交通費,0,0円\n2026/7/2,佐藤 健一,交通費,-500,返金\n");

        assertThat(codesOf(row(r, 1))).contains(RuleCode.AMOUNT_ZERO).doesNotContain(RuleCode.NEGATIVE_AMOUNT);
        assertThat(codesOf(row(r, 2))).contains(RuleCode.NEGATIVE_AMOUNT).doesNotContain(RuleCode.AMOUNT_ZERO);
    }

    @Test
    @DisplayName("金額が空欄の行は0円ではない（欠損として扱う）")
    void blankAmountIsNotZero() {
        ImportResult r = importCsv("2026/7/1,佐藤 健一,交通費,,金額なし\n");
        Expense row = rows(r).get(0);

        assertThat(row.getAmountYen()).isNull();
        assertThat(codesOf(row)).contains(RuleCode.REQUIRED_FIELD_MISSING).doesNotContain(RuleCode.AMOUNT_ZERO);
    }

    @Test
    @DisplayName("通常の金額には0円の警告を出さない")
    void normalAmountIsNotFlagged() {
        ImportResult r = importCsv("2026/7/1,佐藤 健一,交通費,1200,通常\n");
        assertThat(codesOf(rows(r).get(0))).doesNotContain(RuleCode.AMOUNT_ZERO);
    }

    // --- DATE_FUTURE ---

    @Test
    @DisplayName("取込日より先の利用日をREDにする")
    void flagsFutureDate() {
        ImportResult r = importCsv("2026/7/31,山口 大輔,交通費,1200,未来の利用日\n");
        Expense row = rows(r).get(0);

        assertThat(codesOf(row)).contains(RuleCode.DATE_FUTURE);
        assertThat(row.getRiskLevel()).isEqualTo(RiskLevel.RED);
    }

    @Test
    @DisplayName("取込日当日は未来ではない（境界）")
    void todayIsNotFuture() {
        ImportResult r = importCsv("2026/7/17,山口 大輔,交通費,1200,取込日当日\n");
        assertThat(codesOf(rows(r).get(0))).doesNotContain(RuleCode.DATE_FUTURE);
    }

    @Test
    @DisplayName("取込日の翌日は未来（境界）")
    void tomorrowIsFuture() {
        ImportResult r = importCsv("2026/7/18,山口 大輔,交通費,1200,取込日の翌日\n");
        assertThat(codesOf(rows(r).get(0))).contains(RuleCode.DATE_FUTURE);
    }

    @Test
    @DisplayName("過去の利用日は未来の警告を出さない")
    void pastDateIsNotFlagged() {
        ImportResult r = importCsv("2026/7/1,山口 大輔,交通費,1200,過去\n");
        assertThat(codesOf(rows(r).get(0))).doesNotContain(RuleCode.DATE_FUTURE);
    }

    @Test
    @DisplayName("日付が解釈できない行に未来の警告を重ねない")
    void invalidDateDoesNotAlsoReportFuture() {
        ImportResult r = importCsv("2026/7/32,山口 大輔,交通費,1200,存在しない日付\n");
        Expense row = rows(r).get(0);

        assertThat(codesOf(row)).contains(RuleCode.INVALID_DATE).doesNotContain(RuleCode.DATE_FUTURE);
    }

    @Test
    @DisplayName("未来日付は理由付きの例外承認なしには承認できない（REDのため）")
    void futureDateBlocksApproval() {
        ImportResult r = importCsv("2026/7/31,山口 大輔,交通費,1200,未来\n");
        Expense row = rows(r).get(0);

        List<ValidationIssue> issues = issueRepository.findByExpenseId(row.getId());
        assertThat(issues)
                .filteredOn(i -> i.getRuleCode() == RuleCode.DATE_FUTURE)
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.isResolved()).isFalse();
                    // 根拠に「何日先か」を出す。日付の打ち間違いか前払いかの判断材料になる
                    assertThat(i.getEvidence()).contains("2026-07-17").contains("14日先");
                });
    }

    private List<Expense> rows(ImportResult r) {
        return expenseRepository.findBySourceFileId(r.importFileId());
    }

    private Expense row(ImportResult r, int rowNumber) {
        return rows(r).stream().filter(e -> e.getSourceRowNumber() == rowNumber)
                .findFirst().orElseThrow();
    }

    private Set<RuleCode> codesOf(Expense expense) {
        return issueRepository.findByExpenseId(expense.getId()).stream()
                .map(ValidationIssue::getRuleCode)
                .collect(Collectors.toSet());
    }
}
