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
 * 重複として RED で示し、どれを採用するかは経理が決める（自動削除はしない）。
 *
 * <p><b>判定範囲</b>: 同一取込内の行どうしに加えて、同一対象月に取込済みの明細（別ファイル）とも
 * 突き合わせる。部署が月中と月末に分けて提出したり、同じ精算を別ファイルで二重に上げたりすると、
 * 1ファイル内だけを見る判定では二重計上を取り逃がすため。issue は今回取り込んだ行にだけ付け、
 * 過去に取り込んだ明細の状態は動かさない（{@link BatchRule} 参照）。
 *
 * <p><b>ファイル全体の再取込との関係</b>: バイト単位で同一のファイルを上げ直したケースは
 * import_files のハッシュ照合が「再取込の疑い」として別途知らせる。こちらは中身の異なるファイル間で
 * 一部の明細だけが重なる場合も含めて、行レベルで二重計上の候補を挙げる。両者は補完関係にある。
 */
@Component
public class ExactDuplicateRule implements BatchRule {

    @Override
    public RuleCode code() {
        return RuleCode.EXACT_DUPLICATE;
    }

    @Override
    public List<ValidationIssue> evaluate(List<ExpenseEvaluation> rows, List<Expense> priorExpenses, RuleContext context) {
        Map<String, List<ExpenseEvaluation>> currentGroups = new LinkedHashMap<>();
        for (ExpenseEvaluation row : rows) {
            duplicateKey(row.expense()).ifPresent(key ->
                    currentGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(row));
        }

        // 取込済みの明細を同じキーでまとめておき、今回の各行と突き合わせる。
        Map<String, List<Expense>> priorGroups = new LinkedHashMap<>();
        for (Expense prior : priorExpenses) {
            duplicateKey(prior).ifPresent(key ->
                    priorGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(prior));
        }

        List<ValidationIssue> issues = new ArrayList<>();
        for (Map.Entry<String, List<ExpenseEvaluation>> entry : currentGroups.entrySet()) {
            List<ExpenseEvaluation> sameFile = entry.getValue();
            List<Expense> priorMatches = priorGroups.getOrDefault(entry.getKey(), List.of());

            for (ExpenseEvaluation self : sameFile) {
                List<ExpenseEvaluation> inFilePartners = sameFile.stream()
                        .filter(other -> other != self)
                        .toList();
                if (inFilePartners.isEmpty() && priorMatches.isEmpty()) {
                    continue;
                }
                issues.add(buildIssue(self, inFilePartners, priorMatches));
            }
        }
        return issues;
    }

    private ValidationIssue buildIssue(ExpenseEvaluation self, List<ExpenseEvaluation> inFilePartners,
                                       List<Expense> priorMatches) {
        StringBuilder message = new StringBuilder("同一内容の明細が重複しています");
        if (!inFilePartners.isEmpty()) {
            String rowNumbers = inFilePartners.stream()
                    .map(p -> String.valueOf(p.expense().getSourceRowNumber()))
                    .collect(Collectors.joining(","));
            message.append("（同一ファイル内: 行").append(rowNumbers).append("）");
        }
        if (!priorMatches.isEmpty()) {
            message.append("（別の取込ファイル: ").append(priorMatches.size()).append("件・同一対象月）");
        }
        message.append("。採用する明細を選択してください");

        ValidationIssue issue = ValidationIssue.of(RuleCode.EXACT_DUPLICATE, message.toString());
        issue.setExpenseId(self.expense().getId());
        return issue;
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
        return java.util.Optional.of(String.join("",
                e.getUsageDate().toString(),
                e.getApplicantNameKey(),
                e.getCategory().name(),
                String.valueOf(e.getAmountYen()),
                noteKey));
    }
}
