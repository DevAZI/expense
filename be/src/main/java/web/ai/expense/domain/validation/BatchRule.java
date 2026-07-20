package web.ai.expense.domain.validation;

import web.ai.expense.domain.expense.Expense;

import java.util.List;

/**
 * 行をまたいで初めて判定できるルール。
 *
 * <p><b>RowRule と分けている理由</b>: 完全重複・類似重複・桁違い（中央値比較）・分割取引は
 * 1行だけ見ても判定できない。
 *
 * <p><b>{@code priorExpenses} を受け取る理由</b>: 重複は取込ファイルの中だけで起きるとは限らず、
 * 同じ対象月に別々のファイルで届いた明細どうしでも起きる（例: 部署が月中と月末に分けて提出、
 * あるいは同じ精算を二重にアップロード）。そのため現在の取込行（{@code rows}）に加えて、同一
 * 対象月に取込済みの明細（{@code priorExpenses}）を突き合わせ対象として渡す。issue は現在の
 * 取込行にだけ付ける — 過去に取り込んで既に承認・却下した明細の状態は動かさない。
 *
 * <p>MVPは全件メモリ前提で {@code priorExpenses} は呼び出し側が対象月で絞って読み込んだ List。
 * 計画書 16 の「大きなCSVをストリーミング処理し全件をメモリに載せない」を将来満たす際は、
 * この引数が永続化済みデータへの集合クエリ（Repository/Specification）に置き換わり、RowRule 側は
 * 無傷で済む。この境界を先に切っておくのが分離の目的。
 */
public interface BatchRule {

    RuleCode code();

    /**
     * @param rows          今回の取込で読み込んだ行。issue はこの行にだけ設定すること。
     * @param priorExpenses 同一対象月に取込済みの明細（今回のファイルは含まない）。突き合わせ対象で、
     *                      ここに issue は付けない。ファイル内だけで判定するルールは無視してよい。
     * @return 各 issue には expenseId を設定しておくこと。
     */
    List<ValidationIssue> evaluate(List<ExpenseEvaluation> rows, List<Expense> priorExpenses, RuleContext context);
}
