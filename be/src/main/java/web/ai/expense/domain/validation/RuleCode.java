package web.ai.expense.domain.validation;

/**
 * MVPで実装する検証ルール。計画書 15「完了条件」が要求する範囲に絞っている。
 *
 * <p>未実装（Phase 2 以降）: SIMILAR_DUPLICATE と AI_SUGGESTION は AI・類似度計算が要るため、
 * 8.5 の追加22ルールは DB パラメータ管理が前提のため見送り。追加は enum への定数追加と
 * {@link RowRule} 実装1クラスで済む構造にしてある。
 */
public enum RuleCode {

    REQUIRED_FIELD_MISSING(Severity.RED, "日付・申請者・費目・金額のいずれかが欠損しています"),
    INVALID_DATE(Severity.RED, "利用日を日付として解釈できません"),
    DATE_FUTURE(Severity.RED, "利用日が取込日より先です"),
    EXACT_DUPLICATE(Severity.RED, "日付・申請者・費目・金額・備考が一致する明細があります"),
    SIMILAR_DUPLICATE(Severity.YELLOW, "同一申請者・近接日・同額で、備考だけが違う明細があります"),
    CSV_STRUCTURE_INVALID(Severity.YELLOW, "CSVの列数がヘッダーと一致しません"),
    PERIOD_OUT_OF_RANGE(Severity.YELLOW, "利用日が取込対象月の外です"),
    HIGH_AMOUNT(Severity.YELLOW, "費目別のしきい値を超える金額です"),
    NEGATIVE_AMOUNT(Severity.YELLOW, "金額が0円未満です"),
    AMOUNT_ZERO(Severity.YELLOW, "金額が0円です"),
    CATEGORY_UNMAPPED(Severity.YELLOW, "標準費目に変換できませんでした");

    private final Severity defaultSeverity;
    private final String description;

    RuleCode(Severity defaultSeverity, String description) {
        this.defaultSeverity = defaultSeverity;
        this.description = description;
    }

    public Severity defaultSeverity() {
        return defaultSeverity;
    }

    public String description() {
        return description;
    }
}
