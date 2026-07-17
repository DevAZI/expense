package web.ai.expense.api.review;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import web.ai.expense.api.expense.ExpenseResponse;
import web.ai.expense.application.review.ReviewExpenseUseCase;

import java.util.UUID;

@RestController
@RequestMapping("/api/expenses/{id}/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewExpenseUseCase reviewUseCase;

    /** 承認・却下・保留の登録（計画書 9.3）。 */
    @PostMapping
    public ExpenseResponse review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        var expense = reviewUseCase.execute(
                id, request.decision(), request.reason(), request.reviewedBy(), request.overrideRedWarnings());
        return ExpenseResponse.from(expense);
    }
}
