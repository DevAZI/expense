package web.ai.expense.domain.validation;

import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.imports.RawExpenseRow;

/**
 * 検証ルールへ渡す単位。正規化後と原文の両方を持つ。
 *
 * <p>両方必要なのは、REQUIRED_FIELD_MISSING（原文が空）と INVALID_DATE（原文はあるが
 * 解釈できない）を区別するため。正規化後だけ見るとどちらも「usageDate が null」で潰れる。
 */
public record ExpenseEvaluation(Expense expense, RawExpenseRow raw) {
}
