package web.ai.expense.domain.validation;

import java.util.List;

/**
 * 行をまたいで初めて判定できるルール。
 *
 * <p><b>RowRule と分けている理由</b>: 完全重複・類似重複・桁違い（中央値比較）・分割取引は
 * 1行だけ見ても判定できない。MVPは全件メモリ前提なので List を受け取れば済むが、計画書 16 の
 * 「大きなCSVをストリーミング処理し全件をメモリに載せない」を将来満たす際、この境界が無いと
 * 全ルールを作り直すことになる。その時に変わるのはこのインターフェースの引数（List から
 * 永続化済みデータへの集合クエリ）だけで、RowRule 側は無傷で済む。
 */
public interface BatchRule {

    RuleCode code();

    /** 返す各 issue には expenseId を設定しておくこと。 */
    List<ValidationIssue> evaluate(List<ExpenseEvaluation> rows, RuleContext context);
}
