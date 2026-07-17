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
import web.ai.expense.application.review.ReviewNotAllowedException;
import web.ai.expense.application.summary.SummaryQueryService;
import web.ai.expense.domain.expense.*;
import web.ai.expense.domain.imports.ImportStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 承認ゲートと集計、およびファイル単位の失敗経路。
 *
 * <p>正規化と異常検知そのものは {@link RealDataImportTest} が実データで確認する。
 * ここは「承認していない金額が集計に混ざらない」という会計上いちばん壊してはいけない
 * 性質と、壊れたファイルを掴んだときの振る舞いに絞る。CSVは実データと同じヘッダーで組む。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewAndSummaryTest {

    @Autowired
    ImportExpenseFileUseCase importUseCase;
    @Autowired
    ExpenseRepository expenseRepository;
    @Autowired
    ReviewExpenseUseCase reviewUseCase;
    @Autowired
    SummaryQueryService summaryQueryService;

    /** 営業部の実ヘッダー。1行目は正常、2行目は6/31で解釈不能。 */
    private static final String CSV = """
            日付,申請者,勘定科目,金額(円),備考
            2026/6/12,山田 太郎,交通費,1200,正常な行
            2026/6/31,山田 太郎,交通費,3000,存在しない日付
            """;

    private ImportResult importCsv() {
        return importUseCase.execute("test.csv", CSV.getBytes(StandardCharsets.UTF_8), Department.SALES, null);
    }

    @Test
    @DisplayName("取込直後はすべてPENDINGで、集計にはまだ何も入らない")
    void nothingIsCountedBeforeReview() {
        importCsv();

        var summary = summaryQueryService.summarize();
        assertThat(summary.approvedTotalYen()).isZero();
        assertThat(summary.pendingCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("未解決REDの明細は例外承認の明示なしには承認できない")
    void redBlocksApproval() {
        ImportResult result = importCsv();
        Expense red = redRow(result);

        assertThatThrownBy(() -> reviewUseCase.execute(
                red.getId(), ExpenseStatus.APPROVED, null, "keiri", false))
                .isInstanceOf(ReviewNotAllowedException.class)
                .hasMessageContaining("INVALID_DATE");
    }

    @Test
    @DisplayName("REDの例外承認には理由が必須")
    void overrideRequiresReason() {
        ImportResult result = importCsv();
        Expense red = redRow(result);

        assertThatThrownBy(() -> reviewUseCase.execute(
                red.getId(), ExpenseStatus.APPROVED, "  ", "keiri", true))
                .isInstanceOf(ReviewNotAllowedException.class)
                .hasMessageContaining("理由");
    }

    @Test
    @DisplayName("承認した明細だけが集計に入る")
    void onlyApprovedIsCounted() {
        ImportResult result = importCsv();
        Expense clean = cleanRow(result);

        reviewUseCase.execute(clean.getId(), ExpenseStatus.APPROVED, "確認済み", "keiri", false);

        var summary = summaryQueryService.summarize();
        assertThat(summary.approvedTotalYen()).isEqualTo(1200L); // REDの3,000円は入らない
        assertThat(summary.approvedCount()).isEqualTo(1);
        assertThat(summary.byDepartment())
                .singleElement()
                .satisfies(b -> {
                    assertThat(b.code()).isEqualTo(Department.SALES.name());
                    assertThat(b.label()).isEqualTo("営業部");
                    assertThat(b.totalYen()).isEqualTo(1200L);
                });
        // 集計条件を画面で説明できるよう、除外された理由の内訳も返る（計画書 10）
        assertThat(summary.unresolvedRedCount()).isEqualTo(1);
        assertThat(summary.excludedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("却下した明細は集計に入らない")
    void rejectedIsNotCounted() {
        ImportResult result = importCsv();
        Expense clean = cleanRow(result);

        reviewUseCase.execute(clean.getId(), ExpenseStatus.REJECTED, "私的利用のため", "keiri", false);

        var summary = summaryQueryService.summarize();
        assertThat(summary.approvedTotalYen()).isZero();
        assertThat(summary.rejectedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("REDでも理由付きの例外承認なら集計に入る")
    void redCanBeApprovedWithExplicitOverride() {
        ImportResult result = importCsv();
        Expense red = redRow(result);

        reviewUseCase.execute(red.getId(), ExpenseStatus.APPROVED, "原本で6/30と確認", "keiri", true);

        var summary = summaryQueryService.summarize();
        assertThat(summary.approvedTotalYen()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("文字コードを判定できないファイルはファイル単位で取込失敗にする")
    void failsOnUndetectableCharset() {
        byte[] garbage = {(byte) 0x93, (byte) 0xFA, (byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x81};

        ImportResult result = importUseCase.execute("broken.csv", garbage, null, null);

        assertThat(result.status()).isEqualTo(ImportStatus.FAILED);
        assertThat(result.errorSummary()).contains("文字コード");
    }

    @Test
    @DisplayName("対応するスキーマが無いヘッダーは取込失敗にする（黙って捨てない）")
    void failsOnUnknownHeader() {
        String csv = "col_a,col_b,col_c\n1,2,3\n";

        ImportResult result = importUseCase.execute("unknown.csv", csv.getBytes(StandardCharsets.UTF_8), null, null);

        assertThat(result.status()).isEqualTo(ImportStatus.FAILED);
        assertThat(result.errorSummary()).contains("部署CSVスキーマ");
    }

    @Test
    @DisplayName("同一ファイルの再取込はハッシュで気づける")
    void detectsSameFileReimport() {
        ImportResult first = importCsv();
        ImportResult second = importCsv();

        assertThat(first.sameHashImportFileIds()).isEmpty();
        assertThat(second.sameHashImportFileIds()).contains(first.importFileId());
    }

    private Expense redRow(ImportResult result) {
        return rowsOf(result).stream()
                .filter(e -> e.getRiskLevel() == RiskLevel.RED)
                .findFirst().orElseThrow();
    }

    private Expense cleanRow(ImportResult result) {
        return rowsOf(result).stream()
                .filter(e -> e.getRiskLevel() == RiskLevel.NONE)
                .findFirst().orElseThrow();
    }

    private List<Expense> rowsOf(ImportResult result) {
        return expenseRepository.findBySourceFileId(result.importFileId());
    }
}
