package web.ai.expense.domain.validation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** 検証結果の警告1件（計画書 4.2 validation_issues）。 */
@Entity
@Table(name = "validation_issues")
@Getter
@Setter
@NoArgsConstructor
public class ValidationIssue {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "expense_id", nullable = false)
    private UUID expenseId;

    /**
     * <p><b>@JdbcTypeCode(VARCHAR) が必要な理由</b>: これが無いと Hibernate は H2 の
     * ネイティブ ENUM 型でカラムを作る。ENUM 型は許可値がカラム定義に焼き込まれるため、
     * enum に定数を足しても ddl-auto=update は既存カラムを変更せず、新しい値の INSERT が
     * 実行時に落ちる。テストは create-drop で毎回作り直すので、この事故は本番だけで起きる。
     * VARCHAR にしておけば定数の追加はスキーマ変更を伴わない。PostgreSQL へ移す際も同じ。
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "rule_code", nullable = false, length = 64)
    private RuleCode ruleCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "severity", nullable = false, length = 16)
    private Severity severity;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    /** 判定の根拠になった値・しきい値。画面で「なぜ出たか」を示すために持つ（計画書 17）。 */
    @Column(name = "evidence", length = 500)
    private String evidence;

    /**
     * 経理が確認・容認したか。RED が解決されない限り集計に入らない（計画書 7）。
     * MVPでは明細の承認時に一括で解決扱いにする。
     */
    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private ValidationIssue(RuleCode ruleCode, Severity severity, String message, String evidence) {
        this.ruleCode = ruleCode;
        this.severity = severity;
        this.message = message;
        this.evidence = evidence;
    }

    public static ValidationIssue of(RuleCode code, String evidence) {
        return new ValidationIssue(code, code.defaultSeverity(), code.description(), evidence);
    }

    public static ValidationIssue of(RuleCode code, Severity severity, String message, String evidence) {
        return new ValidationIssue(code, severity, message, evidence);
    }
}
