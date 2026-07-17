package web.ai.expense.domain.validation.rules;

import org.springframework.stereotype.Component;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.validation.*;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SIMILAR_DUPLICATE（計画書 7）。同一申請者・同費目・同額で日付が近く、備考だけが違う明細。
 *
 * <p><b>なぜ必要か</b>: 実データの営業部 6/23 に「山口 大輔 / 交通費 / 1,260円」が2行あり、
 * 備考が「新宿→渋谷 タクシー」と「タクシー」で違う。完全重複は備考の一致を条件にするため
 * これを取り逃がし、警告ゼロで承認まで通ってしまう。森本さんの言う「重複っぽいもの」は
 * まさにこれなので、決定的ルールで拾う。
 *
 * <p><b>備考の類似度を判定の条件にしていない理由</b>: 上の例を文字bigramのJaccardで測ると
 * 0.375 で、閾値0.5では落ちる。「タクシー」のような短い備考は長い備考との類似度が構造的に
 * 低く出るため、類似度を門にすると肝心のケースを逃す。ここでは「同一人・同費目・同額・近接日」
 * という決定的な条件だけを門にし、類似度は経理の判断材料として根拠に添えるに留める。
 * 意味的な類似判定は計画書 11 の通りAIの領域で、Phase 4 の範囲。
 */
@Component
public class SimilarDuplicateRule implements BatchRule {

    @Override
    public RuleCode code() {
        return RuleCode.SIMILAR_DUPLICATE;
    }

    @Override
    public List<ValidationIssue> evaluate(List<ExpenseEvaluation> rows, RuleContext context) {
        Map<String, List<ExpenseEvaluation>> groups = new LinkedHashMap<>();
        for (ExpenseEvaluation row : rows) {
            groupKey(row.expense()).ifPresent(key ->
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row));
        }

        int windowDays = context.similarDuplicateWindowDays();
        List<ValidationIssue> issues = new ArrayList<>();

        for (List<ExpenseEvaluation> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            for (ExpenseEvaluation self : group) {
                List<ExpenseEvaluation> partners = group.stream()
                        .filter(other -> other != self)
                        .filter(other -> withinWindow(self.expense(), other.expense(), windowDays))
                        // 備考まで一致する組は EXACT_DUPLICATE の担当。二重に警告しない。
                        .filter(other -> !Objects.equals(noteKey(self.expense()), noteKey(other.expense())))
                        .toList();

                if (partners.isEmpty()) {
                    continue;
                }
                issues.add(buildIssue(self, partners));
            }
        }
        return issues;
    }

    private ValidationIssue buildIssue(ExpenseEvaluation self, List<ExpenseEvaluation> partners) {
        String partnerDesc = partners.stream()
                .map(p -> "行" + p.expense().getSourceRowNumber()
                        + "(" + p.expense().getUsageDate() + " 備考=" + safeNote(p.expense()) + ")")
                .collect(Collectors.joining(", "));

        String evidence = "同一申請者・同費目・同額(" + self.expense().getAmountYen() + "円)で近接: "
                + partnerDesc
                + "。この行の備考=" + safeNote(self.expense())
                + "。備考類似度=" + String.format("%.2f", maxSimilarity(self, partners))
                + "。別利用か二重申請かを確認してください";

        ValidationIssue issue = ValidationIssue.of(RuleCode.SIMILAR_DUPLICATE, evidence);
        issue.setExpenseId(self.expense().getId());
        return issue;
    }

    private double maxSimilarity(ExpenseEvaluation self, List<ExpenseEvaluation> partners) {
        return partners.stream()
                .mapToDouble(p -> bigramJaccard(noteKey(self.expense()), noteKey(p.expense())))
                .max()
                .orElse(0.0);
    }

    private boolean withinWindow(Expense a, Expense b, int windowDays) {
        return Math.abs(ChronoUnit.DAYS.between(a.getUsageDate(), b.getUsageDate())) <= windowDays;
    }

    /** 同一申請者・同費目・同額が揃った行だけを候補にする。日付が無い行は対象外。 */
    private Optional<String> groupKey(Expense e) {
        if (e.getUsageDate() == null || e.getApplicantNameKey() == null
                || e.getCategory() == null || e.getAmountYen() == null) {
            return Optional.empty();
        }
        return Optional.of(e.getApplicantNameKey() + "|" + e.getCategory().name() + "|" + e.getAmountYen());
    }

    private String noteKey(Expense e) {
        return e.getNoteNormalized() == null ? "" : e.getNoteNormalized();
    }

    private String safeNote(Expense e) {
        return e.getNote() == null ? "(空)" : e.getNote();
    }

    /** 文字bigramのJaccard係数。説明用の参考値であって判定の門ではない。 */
    private double bigramJaccard(String a, String b) {
        Set<String> setA = bigrams(a);
        Set<String> setB = bigrams(b);
        if (setA.isEmpty() && setB.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Set<String> bigrams(String value) {
        Set<String> result = new HashSet<>();
        if (value == null || value.length() < 2) {
            return result;
        }
        for (int i = 0; i < value.length() - 1; i++) {
            result.add(value.substring(i, i + 2));
        }
        return result;
    }
}
