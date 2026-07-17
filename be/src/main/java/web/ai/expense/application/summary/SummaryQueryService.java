package web.ai.expense.application.summary;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.ai.expense.domain.expense.*;
import web.ai.expense.domain.validation.Severity;
import web.ai.expense.domain.validation.ValidationIssue;
import web.ai.expense.domain.validation.ValidationIssueRepository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * サマリー（計画書 5-10）。APPROVED かつ未解決のRED警告を持たない明細だけを集計する。
 */
@Service
@RequiredArgsConstructor
public class SummaryQueryService {

    private final ExpenseRepository expenseRepository;
    private final ValidationIssueRepository issueRepository;

    @Transactional(readOnly = true)
    public ExpenseSummary summarize() {
        List<Expense> all = expenseRepository.findAll();

        Set<UUID> blockedByRed = issueRepository.findBySeverityAndResolved(Severity.RED, false).stream()
                .map(ValidationIssue::getExpenseId)
                .collect(Collectors.toSet());

        List<Expense> countable = all.stream()
                .filter(e -> e.getStatus() == ExpenseStatus.APPROVED)
                .filter(e -> !blockedByRed.contains(e.getId()))
                .filter(e -> e.getAmountYen() != null)
                .toList();

        long total = countable.stream().mapToLong(Expense::getAmountYen).sum();

        int pending = (int) all.stream().filter(e -> e.getStatus() == ExpenseStatus.PENDING).count();
        int rejected = (int) all.stream().filter(e -> e.getStatus() == ExpenseStatus.REJECTED).count();

        return new ExpenseSummary(
                total,
                countable.size(),
                pending,
                rejected,
                blockedByRed.size(),
                all.size() - countable.size(),
                all.size(),
                breakdown(countable, e -> e.getDepartment().name(), e -> e.getDepartment().label()),
                breakdown(countable.stream().filter(e -> e.getCategory() != null).toList(),
                        e -> e.getCategory().name(), e -> e.getCategory().label()),
                breakdown(countable.stream().filter(e -> e.getCategory() != null).toList(),
                        e -> e.getCategory().group().name(), e -> e.getCategory().group().label())
        );
    }

    private List<ExpenseSummary.Breakdown> breakdown(List<Expense> rows,
                                                     Function<Expense, String> codeOf,
                                                     Function<Expense, String> labelOf) {
        Map<String, List<Expense>> grouped = rows.stream()
                .collect(Collectors.groupingBy(codeOf, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> new ExpenseSummary.Breakdown(
                        entry.getKey(),
                        labelOf.apply(entry.getValue().get(0)),
                        entry.getValue().stream().mapToLong(Expense::getAmountYen).sum(),
                        entry.getValue().size()))
                .sorted(Comparator.comparingLong(ExpenseSummary.Breakdown::totalYen).reversed())
                .toList();
    }
}
