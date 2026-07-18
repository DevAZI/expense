package web.ai.expense.api.review;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import web.ai.expense.application.review.ApproveCleanExpensesUseCase;
import web.ai.expense.application.review.BulkApproveResult;

/**
 * 一括レビュー。個別レビュー（{@link ReviewController}）とは別系統。
 *
 * <p>現状は「警告なしの明細をまとめて承認」だけを提供する。YELLOW/RED を含む
 * 一括操作は用意しない（人による確認が要るため個別レビューに委ねる）。
 */
@RestController
@RequestMapping("/api/expenses/approve-clean")
@RequiredArgsConstructor
public class BulkReviewController {

    private final ApproveCleanExpensesUseCase approveCleanUseCase;

    /** PENDING かつ警告なし（riskLevel=NONE）の明細をすべて承認する。 */
    @PostMapping
    public BulkApproveResult approveClean(@Valid @RequestBody BulkApproveRequest request) {
        return approveCleanUseCase.approveClean(request.reviewedBy());
    }
}
