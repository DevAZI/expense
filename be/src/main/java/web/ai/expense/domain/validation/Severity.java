package web.ai.expense.domain.validation;

import web.ai.expense.domain.expense.RiskLevel;

public enum Severity {
    /** 人による確認が必要。確認後の承認は可能（計画書 7）。 */
    YELLOW,
    /** 処理不能または重大。解決するまで承認不可・集計対象外（計画書 7）。 */
    RED;

    public RiskLevel toRiskLevel() {
        return this == RED ? RiskLevel.RED : RiskLevel.YELLOW;
    }
}
