package web.ai.expense.domain.validation.rules;

import org.springframework.stereotype.Component;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.validation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * EXACT_DUPLICATE（計画書 7）。日付・申請者・費目・金額・正規化備考の5項目が一致する明細を
 * 同一グループとして RED で示し、どれを採用するかは経理が決める（自動削除はしない）。
 *
 * <p><b>MVPの制約</b>: 判定対象は同一取込内の行に限る。別ファイルとの重複（再取込を含む）は
 * 計画書 8.5 の FILE_REIMPORT の領域で、DBに対する集合クエリが必要になるため未実装。
 */
@Component
public class ExactDuplicateRule implements BatchRule {

    @Override
    public RuleCode code() {
        return RuleCode.EXACT_DUPLICATE;
    }

    @Override
    public List<ValidationIssue> evaluate(List<ExpenseEvaluation> rows, RuleContext context) {
        Map<String, List<ExpenseEvaluation>> groups = new LinkedHashMap<>();

        for (ExpenseEvaluation row : rows) {
            duplicateKey(row.expense()).ifPresent(key ->
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row));
        }

        List<ValidationIssue> issues = new ArrayList<>();
        for (List<ExpenseEvaluation> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            String rowNumbers = group.stream()
                    .map(r -> String.valueOf(r.expense().getSourceRowNumber()))
                    .collect(Collectors.joining(","));

            for (ExpenseEvaluation member : group) {
                ValidationIssue issue = ValidationIssue.of(
                        RuleCode.EXACT_DUPLICATE,
                        "同一内容の行=" + rowNumbers + "（" + group.size() + "件）。採用する明細を選択してください");
                issue.setExpenseId(member.expense().getId());
                issues.add(issue);
            }
        }
        return issues;
    }

    /**
     * 5項目が揃っている行だけを重複判定の対象にする。欠損のある行同士は、
     * 「同じ欠け方をしている」だけで重複とは言えないため対象外にする。
     */
    private java.util.Optional<String> duplicateKey(Expense e) {
        if (e.getUsageDate() == null || e.getApplicantNameKey() == null
                || e.getCategory() == null || e.getAmountYen() == null) {
            return java.util.Optional.empty();
        }
        String noteKey = e.getNoteNormalized() == null ? "" : e.getNoteNormalized();
        return java.util.Optional.of(String.join("",
                e.getUsageDate().toString(),
                e.getApplicantNameKey(),
                e.getCategory().name(),
                String.valueOf(e.getAmountYen()),
                noteKey));
    }
}
