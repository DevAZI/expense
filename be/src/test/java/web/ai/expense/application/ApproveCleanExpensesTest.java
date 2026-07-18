package web.ai.expense.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import web.ai.expense.application.imports.ImportExpenseFileUseCase;
import web.ai.expense.application.imports.ImportResult;
import web.ai.expense.application.review.ApproveCleanExpensesUseCase;
import web.ai.expense.application.review.BulkApproveResult;
import web.ai.expense.application.summary.SummaryQueryService;
import web.ai.expense.domain.expense.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「警告なし」明細の一括承認。
 *
 * <p>会計上いちばん壊してはいけないのは「確認が必要な明細（RED/YELLOW）が
 * 一括承認で素通りする」こと。ここはその境界に絞って確認する。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApproveCleanExpensesTest {

    @Autowired
    ImportExpenseFileUseCase importUseCase;
    @Autowired
    ApproveCleanExpensesUseCase approveCleanUseCase;
    @Autowired
    ExpenseRepository expenseRepository;
    @Autowired
    SummaryQueryService summaryQueryService;

    /** NONE（正常）/ RED（6/31で解釈不能）/ YELLOW（0円）を1行ずつ含む。 */
    private static final String CSV = """
            日付,申請者,勘定科目,金額(円),備考
            2026/6/12,山田 太郎,交通費,1200,正常な行
            2026/6/31,山田 太郎,交通費,3000,存在しない日付
            2026/6/15,鈴木 花子,消耗品費,0,ゼロ円
            """;

    private ImportResult importCsv() {
        return importUseCase.execute("test.csv", CSV.getBytes(StandardCharsets.UTF_8), Department.SALES, null);
    }

    @Test
    @DisplayName("警告なしの明細だけが承認され、RED/YELLOWはPENDINGのまま残る")
    void approvesOnlyCleanRows() {
        ImportResult result = importCsv();

        BulkApproveResult approved = approveCleanUseCase.approveClean("keiri");

        assertThat(approved.approvedCount()).isEqualTo(1);

        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());
        assertThat(rows).filteredOn(e -> e.getRiskLevel() == RiskLevel.NONE)
                .allSatisfy(e -> {
                    assertThat(e.getStatus()).isEqualTo(ExpenseStatus.APPROVED);
                    assertThat(e.getReviewedBy()).isEqualTo("keiri");
                });
        // 確認が必要な明細は一括承認で素通りさせない
        assertThat(rows).filteredOn(e -> e.getRiskLevel() != RiskLevel.NONE)
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.getStatus()).isEqualTo(ExpenseStatus.PENDING));

        // 集計にはNONEの1,200円だけが入る
        var summary = summaryQueryService.summarize();
        assertThat(summary.approvedCount()).isEqualTo(1);
        assertThat(summary.approvedTotalYen()).isEqualTo(1200L);
    }

    @Test
    @DisplayName("対象が無ければ0件（何度呼んでも安全）")
    void isIdempotent() {
        importCsv();

        assertThat(approveCleanUseCase.approveClean("keiri").approvedCount()).isEqualTo(1);
        // 2回目は警告なしのPENDINGが残っていない
        assertThat(approveCleanUseCase.approveClean("keiri").approvedCount()).isZero();
    }
}
