package web.ai.expense.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import web.ai.expense.application.imports.ImportExpenseFileUseCase;
import web.ai.expense.application.imports.ImportResult;
import web.ai.expense.application.review.ReviewExpenseUseCase;
import web.ai.expense.domain.expense.Department;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.expense.ExpenseRepository;
import web.ai.expense.domain.expense.ExpenseStatus;
import web.ai.expense.domain.validation.RuleCode;
import web.ai.expense.domain.validation.ValidationIssue;
import web.ai.expense.domain.validation.ValidationIssueRepository;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ファイル横断の重複検知。同じ部署が複数のファイルに分けて提出したとき、ファイルAの明細と
 * ファイルBの明細が重複していても、単一ファイル内しか見ない判定では取り逃がす — その穴を塞ぐ。
 *
 * <p>突き合わせ範囲は同一対象月に取込済みの明細（却下済みを除く）。今回取り込むファイルの各行に
 * だけ issue を付け、過去の明細の状態は動かさない。CSVは実データと同じ営業部ヘッダーで組む。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CrossFileDuplicateTest {

    @Autowired
    ImportExpenseFileUseCase importUseCase;
    @Autowired
    ExpenseRepository expenseRepository;
    @Autowired
    ValidationIssueRepository issueRepository;
    @Autowired
    ReviewExpenseUseCase reviewUseCase;

    private ImportResult importSales(String fileName, String body, YearMonth targetMonth) {
        String csv = "日付,申請者,勘定科目,金額(円),備考\n" + body;
        return importUseCase.execute(fileName, csv.getBytes(StandardCharsets.UTF_8),
                Department.SALES, targetMonth);
    }

    @Test
    @DisplayName("別ファイルの同一明細を完全重複として検知し、当該行にだけ付ける")
    void detectsExactDuplicateAcrossFiles() {
        importSales("file_a.csv", "2026/6/10,佐藤 健一,交通費,3480,品川往復\n", YearMonth.of(2026, 6));

        // ファイルBの1行目はファイルAと完全一致、2行目は無関係
        ImportResult b = importSales("file_b.csv",
                "2026/6/10,佐藤 健一,交通費,3480,品川往復\n"
                        + "2026/6/11,鈴木 花子,会議費,2000,定例\n",
                YearMonth.of(2026, 6));

        List<Expense> rowsB = expenseRepository.findBySourceFileId(b.importFileId());
        assertThat(codesOf(row(rowsB, 1))).contains(RuleCode.EXACT_DUPLICATE);
        assertThat(codesOf(row(rowsB, 2))).doesNotContain(RuleCode.EXACT_DUPLICATE);
    }

    @Test
    @DisplayName("備考だけ違う別ファイルの明細を類似重複として検知する")
    void detectsSimilarDuplicateAcrossFiles() {
        importSales("file_a.csv", "2026/6/23,山口 大輔,交通費,1260,新宿→渋谷 タクシー\n", YearMonth.of(2026, 6));

        ImportResult b = importSales("file_b.csv", "2026/6/24,山口 大輔,交通費,1260,タクシー\n", YearMonth.of(2026, 6));

        List<Expense> rowsB = expenseRepository.findBySourceFileId(b.importFileId());
        assertThat(codesOf(row(rowsB, 1))).contains(RuleCode.SIMILAR_DUPLICATE);
        assertThat(codesOf(row(rowsB, 1))).doesNotContain(RuleCode.EXACT_DUPLICATE);
    }

    @Test
    @DisplayName("先に取り込んだファイルの明細には後からの取込で issue が増えない")
    void priorFileRowsAreNotTouched() {
        ImportResult a = importSales("file_a.csv", "2026/6/10,佐藤 健一,交通費,3480,品川往復\n", YearMonth.of(2026, 6));
        Expense priorRow = expenseRepository.findBySourceFileId(a.importFileId()).get(0);
        assertThat(codesOf(priorRow)).doesNotContain(RuleCode.EXACT_DUPLICATE);

        importSales("file_b.csv", "2026/6/10,佐藤 健一,交通費,3480,品川往復\n", YearMonth.of(2026, 6));

        // ファイルAの行は取り込み直さない限り触らない（過去の判定・承認を動かさない）
        assertThat(codesOf(priorRow)).doesNotContain(RuleCode.EXACT_DUPLICATE);
    }

    @Test
    @DisplayName("対象月が違えば別バッチなので突き合わせない")
    void doesNotMatchAcrossDifferentTargetMonths() {
        importSales("june.csv", "2026/6/10,佐藤 健一,交通費,3480,品川往復\n", YearMonth.of(2026, 6));

        ImportResult july = importSales("july.csv", "2026/6/10,佐藤 健一,交通費,3480,品川往復\n", YearMonth.of(2026, 7));

        List<Expense> rowsJuly = expenseRepository.findBySourceFileId(july.importFileId());
        assertThat(codesOf(row(rowsJuly, 1))).doesNotContain(RuleCode.EXACT_DUPLICATE);
    }

    @Test
    @DisplayName("却下済みの過去明細は突き合わせ対象から外す")
    void rejectedPriorRowsAreExcluded() {
        ImportResult a = importSales("file_a.csv", "2026/6/10,佐藤 健一,交通費,3480,品川往復\n", YearMonth.of(2026, 6));
        Expense priorRow = expenseRepository.findBySourceFileId(a.importFileId()).get(0);
        reviewUseCase.execute(priorRow.getId(), ExpenseStatus.REJECTED, "重複のため却下", "keiri", false);

        ImportResult b = importSales("file_b.csv", "2026/6/10,佐藤 健一,交通費,3480,品川往復\n", YearMonth.of(2026, 6));

        List<Expense> rowsB = expenseRepository.findBySourceFileId(b.importFileId());
        assertThat(codesOf(row(rowsB, 1))).doesNotContain(RuleCode.EXACT_DUPLICATE);
    }

    private Expense row(List<Expense> rows, int rowNumber) {
        return rows.stream()
                .filter(e -> e.getSourceRowNumber() == rowNumber)
                .findFirst()
                .orElseThrow(() -> new AssertionError("行 " + rowNumber + " が見つかりません"));
    }

    private Set<RuleCode> codesOf(Expense expense) {
        return issueRepository.findByExpenseId(expense.getId()).stream()
                .map(ValidationIssue::getRuleCode)
                .collect(Collectors.toSet());
    }
}
