package web.ai.expense.application.review;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.expense.ExpenseRepository;
import web.ai.expense.domain.expense.ExpenseStatus;
import web.ai.expense.domain.expense.RiskLevel;

import java.util.List;

/**
 * 「警告なし」の明細を一括承認する（計画書 7・10 の運用補助）。
 *
 * <p><b>承認ゲートと矛盾しない理由</b>: 対象は {@code status=PENDING} かつ
 * {@code riskLevel=NONE} の明細だけ。リスクは付与された警告の最大重要度から決まるため
 * （{@code ValidationEngine.riskLevelOf}）、NONE は「RED も YELLOW も1件も無い」と同値。
 * したがって {@link ReviewExpenseUseCase} の未解決RED承認ゲートに一切触れず、
 * 解決すべき警告も存在しない。人が1件ずつ開いて確認する必要がないものだけをまとめて通す。
 *
 * <p>YELLOW は「人による確認が必要」なので対象に含めない。これらは従来どおり
 * 明細詳細から個別に承認する。
 */
@Service
@RequiredArgsConstructor
public class ApproveCleanExpensesUseCase {

    private final ExpenseRepository expenseRepository;

    /**
     * @param reviewedBy 操作者名（認証が無いMVPでは受け取る。計画書 16）。
     * @return 承認した件数
     */
    @Transactional
    public BulkApproveResult approveClean(String reviewedBy) {
        List<Expense> clean = expenseRepository
                .findByStatusAndRiskLevel(ExpenseStatus.PENDING, RiskLevel.NONE);

        for (Expense e : clean) {
            // 警告なしなので理由は不要。一括承認の記録として理由に固定文言を残す。
            e.review(ExpenseStatus.APPROVED, "警告なしのため一括承認", reviewedBy);
        }
        expenseRepository.saveAll(clean);

        return new BulkApproveResult(clean.size());
    }
}
