package web.ai.expense.application.summary;

import java.util.List;

/**
 * 集計結果（計画書 10）。
 *
 * <p>計画書 10 は「集計条件を明示する」ことを求めている。合計金額だけを返すと、経理は
 * その数字が何を含み何を除いた結果なのかを画面から判断できない。そのため除外件数とその
 * 内訳（未確認・却下・未解決RED）を必ず一緒に返す。
 */
public record ExpenseSummary(
        /** APPROVED かつ未解決REDなしの合計（計画書 5-10）。 */
        long approvedTotalYen,
        int approvedCount,

        /** 集計から外れた明細の内訳。合計してもよいが、意味が違うので分けて返す。 */
        int pendingCount,
        int rejectedCount,
        int unresolvedRedCount,
        int excludedCount,

        /** 全明細件数。 */
        int totalCount,

        List<Breakdown> byDepartment,
        List<Breakdown> byCategory,
        /** 部署横断で比較するための上位グループ集計。 */
        List<Breakdown> byCategoryGroup
) {
    public record Breakdown(String code, String label, long totalYen, int count) {
    }
}
