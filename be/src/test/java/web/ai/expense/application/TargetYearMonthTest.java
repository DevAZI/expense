package web.ai.expense.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import web.ai.expense.application.imports.ImportExpenseFileUseCase;
import web.ai.expense.application.imports.ImportResult;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.expense.ExpenseRepository;
import web.ai.expense.domain.expense.RiskLevel;
import web.ai.expense.domain.imports.ImportFileRepository;
import web.ai.expense.domain.validation.RuleCode;
import web.ai.expense.domain.validation.ValidationIssue;
import web.ai.expense.domain.validation.ValidationIssueRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 取込ごとの対象月指定。
 *
 * <p>これが無いと、翌月分を取り込むのに application.yml を書き換えて再起動する必要があり、
 * 依頼者（経理・非エンジニア）には実行できない。対象月は期間外判定だけでなく、
 * 年省略日付の年の補完にも効くため、間違えると日付そのものがずれる。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TargetYearMonthTest {

    @Autowired
    ImportExpenseFileUseCase importUseCase;
    @Autowired
    ExpenseRepository expenseRepository;
    @Autowired
    ValidationIssueRepository issueRepository;
    @Autowired
    ImportFileRepository importFileRepository;

    /** 7月分。マーケの実データと同じく年を省略した日付で書かれている。 */
    private static final String JULY_CSV = """
            利用日,氏名,費目,支払金額,メモ
            7/2,田中 美咲,広告費,350000,Meta広告 7月前半
            7/15,小林 蓮,交通費,1140,撮影スタジオ移動
            """;

    private ImportResult importJuly(YearMonth target) {
        return importUseCase.execute("july.csv", JULY_CSV.getBytes(StandardCharsets.UTF_8), null, target);
    }

    @Test
    @DisplayName("対象月を指定すれば、その月の明細は対象月外にならない")
    void julyDataWithJulyTargetHasNoPeriodWarning() {
        ImportResult result = importJuly(YearMonth.of(2026, 7));
        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(e -> assertThat(codesOf(e)).doesNotContain(RuleCode.PERIOD_OUT_OF_RANGE));
        assertThat(row(rows, 1).getUsageDate()).isEqualTo(LocalDate.of(2026, 7, 2));
    }

    @Test
    @DisplayName("対象月が既定の6月のままだと、7月分は全行が対象月外になる（これを直したかった）")
    void julyDataWithJuneTargetIsAllFlagged() {
        // 設定の既定値（2026-06）を使う
        ImportResult result = importUseCase.execute(
                "july.csv", JULY_CSV.getBytes(StandardCharsets.UTF_8), null, null);
        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());

        assertThat(rows).allSatisfy(e -> {
            assertThat(codesOf(e)).contains(RuleCode.PERIOD_OUT_OF_RANGE);
            assertThat(e.getRiskLevel()).isEqualTo(RiskLevel.YELLOW);
        });
    }

    @Test
    @DisplayName("対象月を指定しなければ設定の既定値を使う")
    void defaultsToConfiguredMonth() {
        ImportResult result = importUseCase.execute(
                "july.csv", JULY_CSV.getBytes(StandardCharsets.UTF_8), null, null);

        assertThat(result.targetYearMonth()).isEqualTo("2026-06");
    }

    @Test
    @DisplayName("使った対象月を取込ファイルに記録する（判定理由を後から説明するため）")
    void recordsTargetYearMonthOnImportFile() {
        ImportResult result = importJuly(YearMonth.of(2026, 7));

        assertThat(result.targetYearMonth()).isEqualTo("2026-07");
        assertThat(importFileRepository.findById(result.importFileId()))
                .get()
                .satisfies(f -> assertThat(f.getTargetYearMonth()).isEqualTo("2026-07"));
    }

    @Test
    @DisplayName("対象月ごとに凍結されるので、月をまたいで取り込んでも互いの判定に影響しない")
    void eachImportUsesItsOwnSnapshot() {
        ImportResult july = importJuly(YearMonth.of(2026, 7));

        String juneCsv = """
                利用日,氏名,費目,支払金額,メモ
                6/5,田中 美咲,広告費,350000,Meta広告 6月
                """;
        ImportResult june = importUseCase.execute(
                "june.csv", juneCsv.getBytes(StandardCharsets.UTF_8), null, YearMonth.of(2026, 6));

        // 7月分は7月として、6月分は6月として、それぞれ警告なし
        assertThat(expenseRepository.findBySourceFileId(july.importFileId()))
                .allSatisfy(e -> assertThat(codesOf(e)).doesNotContain(RuleCode.PERIOD_OUT_OF_RANGE));
        assertThat(expenseRepository.findBySourceFileId(june.importFileId()))
                .allSatisfy(e -> assertThat(codesOf(e)).doesNotContain(RuleCode.PERIOD_OUT_OF_RANGE));
    }

    @Test
    @DisplayName("年をまたぐ月またぎ精算で、年が1年ずれない")
    void yearBoundaryIsHandled() {
        String januaryCsv = """
                利用日,氏名,費目,支払金額,メモ
                1/5,田中 美咲,広告費,350000,1月分
                12/28,小林 蓮,交通費,1140,前年12月の月またぎ精算
                """;
        ImportResult result = importUseCase.execute(
                "jan.csv", januaryCsv.getBytes(StandardCharsets.UTF_8), null, YearMonth.of(2027, 1));
        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());

        assertThat(row(rows, 1).getUsageDate()).isEqualTo(LocalDate.of(2027, 1, 5));
        // 2027-12-28 ではなく 2026-12-28。対象月にいちばん近い年を採る
        assertThat(row(rows, 2).getUsageDate()).isEqualTo(LocalDate.of(2026, 12, 28));
        // 前年12月なので対象月外として人に見せる
        assertThat(codesOf(row(rows, 2))).contains(RuleCode.PERIOD_OUT_OF_RANGE);
    }

    private Expense row(List<Expense> rows, int rowNumber) {
        return rows.stream().filter(e -> e.getSourceRowNumber() == rowNumber)
                .findFirst().orElseThrow(() -> new AssertionError("行 " + rowNumber + " が見つかりません"));
    }

    private Set<RuleCode> codesOf(Expense expense) {
        return issueRepository.findByExpenseId(expense.getId()).stream()
                .map(ValidationIssue::getRuleCode)
                .collect(Collectors.toSet());
    }
}
