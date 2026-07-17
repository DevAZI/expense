package web.ai.expense.domain.validation;

import web.ai.expense.domain.expense.ExpenseCategory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/**
 * 検証ルールが参照できるパラメータの唯一の入口。
 *
 * <p><b>これが存在する理由</b>: 計画書 8.3 は「進行中の取込は開始時点のルールスナップショットを
 * 最後まで使う」ことを要求する。ルール実装が設定やDBを直接読むと、取込の途中でルールが
 * 変わったときに同一ファイル内で判定基準がぶれる。そのためルールは必ずこの不変オブジェクト
 * 経由で値を読み、スナップショットは取込の開始時に1回だけ組み立てる。
 *
 * <p>MVPでは実装が設定（application.yml）由来だが、DBのルールテーブルへ移す際に
 * 変わるのはこのインターフェースの実装を作る場所だけで、ルール本体は変更不要。
 */
public interface RuleContext {

    /** 取込対象月。期間外判定と、年省略日付の年補完に使う。 */
    YearMonth targetYearMonth();

    /**
     * 取込の基準日。「未来の利用日」を判定する起点。
     *
     * <p>ルールが LocalDate.now() を直接呼ばずここから取るのは、取込が日付をまたいでも
     * 同一ファイル内で判定がぶれないようにするため（計画書 8.3 のスナップショットと同じ理由）。
     */
    LocalDate today();

    /** 取込基準日から何日先までを許容するか。0 なら翌日以降が警告。 */
    int allowedFutureDays();

    /** 0円の申請を警告として出すか。 */
    boolean zeroAmountWarning();

    /** 費目別の高額しきい値（円）。この値を超えたら HIGH_AMOUNT。 */
    long highAmountThresholdYen(ExpenseCategory category);

    /** 負数金額を警告として出すか。 */
    boolean negativeAmountWarning();

    /** 類似重複とみなす日付の近接幅（日）。 */
    int similarDuplicateWindowDays();

    /** 正規化済みの入力費目を標準費目へ寄せる。寄せられなければ empty。 */
    Optional<ExpenseCategory> mapCategory(String normalizedSourceValue);
}
