package web.ai.expense.api.anomaly;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.expense.ExpenseRepository;
import web.ai.expense.domain.validation.RuleCode;
import web.ai.expense.domain.validation.Severity;
import web.ai.expense.domain.validation.ValidationIssue;
import web.ai.expense.domain.validation.ValidationIssueRepository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** RED/YELLOW警告の一覧と根拠（計画書 9.3 /api/anomalies）。 */
@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final ValidationIssueRepository issueRepository;
    private final ExpenseRepository expenseRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public List<AnomalyResponse> list(@RequestParam(required = false) Severity severity,
                                      @RequestParam(required = false) RuleCode ruleCode,
                                      @RequestParam(required = false) Boolean resolved) {

        List<ValidationIssue> issues = (severity == null
                ? issueRepository.findAll()
                : issueRepository.findBySeverity(severity)).stream()
                .filter(i -> ruleCode == null || i.getRuleCode() == ruleCode)
                .filter(i -> resolved == null || i.isResolved() == resolved)
                .toList();

        if (issues.isEmpty()) {
            return List.of();
        }

        // 明細をまとめて引いてから突き合わせる。件数分のクエリを撃たない。
        List<UUID> expenseIds = issues.stream().map(ValidationIssue::getExpenseId).distinct().toList();
        Map<UUID, Expense> expenses = expenseRepository.findAllById(expenseIds).stream()
                .collect(Collectors.toMap(Expense::getId, Function.identity()));

        return issues.stream()
                .map(i -> AnomalyResponse.of(i, expenses.get(i.getExpenseId())))
                // 重大なものを先頭へ。経理が上から順に処理すれば済むようにする。
                .sorted(Comparator
                        .comparing((AnomalyResponse a) -> "RED".equals(a.severity()) ? 0 : 1)
                        .thenComparing(a -> a.usageDate() == null ? "" : a.usageDate().toString()))
                .toList();
    }
}
