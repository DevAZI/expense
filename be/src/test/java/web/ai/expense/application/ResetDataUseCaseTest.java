package web.ai.expense.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import web.ai.expense.application.imports.ImportExpenseFileUseCase;
import web.ai.expense.application.imports.ImportResult;
import web.ai.expense.application.imports.ResetDataUseCase;
import web.ai.expense.application.imports.ResetResult;
import web.ai.expense.application.review.ReviewExpenseUseCase;
import web.ai.expense.application.summary.SummaryQueryService;
import web.ai.expense.domain.expense.*;
import web.ai.expense.domain.imports.ImportFileRepository;
import web.ai.expense.domain.imports.RawExpenseRowRepository;
import web.ai.expense.domain.validation.ValidationIssueRepository;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 初期化。データが消える操作なので、消えすぎないこと・消え残らないことの両方を固定する。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ResetDataUseCaseTest {

    @Autowired
    ImportExpenseFileUseCase importUseCase;
    @Autowired
    ResetDataUseCase resetUseCase;
    @Autowired
    ReviewExpenseUseCase reviewUseCase;
    @Autowired
    SummaryQueryService summaryQueryService;
    @Autowired
    ImportFileRepository importFileRepository;
    @Autowired
    RawExpenseRowRepository rawRowRepository;
    @Autowired
    ExpenseRepository expenseRepository;
    @Autowired
    ValidationIssueRepository issueRepository;

    private static final String SALES_CSV = """
            日付,申請者,勘定科目,金額(円),備考
            2026/6/12,山田 太郎,交通費,1200,正常な行
            2026/6/31,山田 太郎,交通費,3000,存在しない日付
            """;

    private static final String MARKETING_CSV = """
            利用日,氏名,費目,支払金額,メモ
            6/5,田中 美咲,広告費,350000,Meta広告
            """;

    private ImportResult importSales() {
        return importUseCase.execute("sales.csv", SALES_CSV.getBytes(StandardCharsets.UTF_8), null, null);
    }

    private ImportResult importMarketing() {
        return importUseCase.execute("mkt.csv", MARKETING_CSV.getBytes(StandardCharsets.UTF_8), null, null);
    }

    @Test
    @DisplayName("全初期化で4テーブルすべてが空になる")
    void resetAllClearsEverything() {
        importSales();
        assertThat(expenseRepository.count()).isPositive();
        assertThat(issueRepository.count()).isPositive();

        ResetResult result = resetUseCase.resetAll();

        assertThat(importFileRepository.count()).isZero();
        assertThat(rawRowRepository.count()).isZero();
        assertThat(expenseRepository.count()).isZero();
        assertThat(issueRepository.count()).isZero();

        // 何を消したかを件数で返す
        assertThat(result.importFiles()).isEqualTo(1);
        assertThat(result.expenses()).isEqualTo(2);
        assertThat(result.rawRows()).isEqualTo(2);
        assertThat(result.issues()).isPositive();
    }

    @Test
    @DisplayName("全初期化のあと集計は0に戻る")
    void summaryIsEmptyAfterReset() {
        ImportResult imported = importSales();
        Expense clean = expenseRepository.findBySourceFileId(imported.importFileId()).stream()
                .filter(e -> e.getRiskLevel() == RiskLevel.NONE).findFirst().orElseThrow();
        reviewUseCase.execute(clean.getId(), ExpenseStatus.APPROVED, "確認済み", "keiri", false);
        assertThat(summaryQueryService.summarize().approvedTotalYen()).isEqualTo(1200L);

        resetUseCase.resetAll();

        var summary = summaryQueryService.summarize();
        assertThat(summary.approvedTotalYen()).isZero();
        assertThat(summary.totalCount()).isZero();
        assertThat(summary.unresolvedRedCount()).isZero();
    }

    @Test
    @DisplayName("初期化後に同じファイルを入れ直せる（ハッシュ履歴も消えている）")
    void canReimportSameFileAfterReset() {
        importSales();
        resetUseCase.resetAll();

        ImportResult again = importSales();

        assertThat(again.rowCount()).isEqualTo(2);
        // 再取込の警告が出ないこと。前月分を消したのに「再取込では?」と出ると紛らわしい
        assertThat(again.sameHashImportFileIds()).isEmpty();
    }

    @Test
    @DisplayName("ファイル単位の削除は他のファイルを巻き込まない")
    void resetOneFileLeavesOthersIntact() {
        ImportResult sales = importSales();
        ImportResult marketing = importMarketing();

        // マーケの明細を承認しておく。営業部を消してもこれが残ることを確かめる
        Expense mktRow = expenseRepository.findBySourceFileId(marketing.importFileId()).get(0);
        reviewUseCase.execute(mktRow.getId(), ExpenseStatus.APPROVED, "確認済み", "keiri", false);

        ResetResult result = resetUseCase.resetImportFile(sales.importFileId());

        assertThat(result.expenses()).isEqualTo(2);
        assertThat(expenseRepository.findBySourceFileId(sales.importFileId())).isEmpty();
        assertThat(rawRowRepository.findByImportFileId(sales.importFileId())).isEmpty();
        assertThat(importFileRepository.findById(sales.importFileId())).isEmpty();

        // マーケ側は明細も承認結果も無傷
        assertThat(expenseRepository.findBySourceFileId(marketing.importFileId())).hasSize(1);
        assertThat(importFileRepository.findById(marketing.importFileId())).isPresent();
        assertThat(summaryQueryService.summarize().approvedTotalYen()).isEqualTo(350000L);
    }

    @Test
    @DisplayName("ファイル単位の削除で、そのファイルの警告だけが消える")
    void resetOneFileDeletesOnlyItsIssues() {
        ImportResult sales = importSales();
        importMarketing();
        long before = issueRepository.count();

        resetUseCase.resetImportFile(sales.importFileId());

        assertThat(issueRepository.count()).isLessThan(before);
        // 営業部の明細に紐づく警告が残っていないこと
        assertThat(issueRepository.findAll())
                .noneSatisfy(i -> assertThat(expenseRepository.findById(i.getExpenseId())).isEmpty());
    }

    @Test
    @DisplayName("存在しないファイルの削除は404相当のエラーにする")
    void resetUnknownFileFails() {
        assertThatThrownBy(() -> resetUseCase.resetImportFile(UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("データが無い状態で初期化しても壊れない")
    void resetOnEmptyDatabaseIsSafe() {
        ResetResult result = resetUseCase.resetAll();

        assertThat(result.importFiles()).isZero();
        assertThat(result.expenses()).isZero();
    }
}
