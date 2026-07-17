package web.ai.expense.domain.expense;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 正規化済み経費明細（計画書 4.1）。
 *
 * <p><b>必須項目が nullable になっている理由</b>: 計画書 4.1 は usageDate / amountYen 等を
 * 必須と定義するが、7 の REQUIRED_FIELD_MISSING・INVALID_DATE は「欠損や不正日付の行を
 * 画面に出して経理に原本確認させる」ことを要求する。両立させるにはこれらの行も明細として
 * 保存する必要があるため、DBレベルでは null を許し、「揃っていること」は検証ルールと
 * 承認ゲート（未解決REDは承認不可）で担保する。
 */
@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
public class Expense {

    @Id
    @GeneratedValue
    private UUID id;

    /** 正規化後の利用日。解析できなかった場合は null（INVALID_DATE が付く）。 */
    @Column(name = "usage_date")
    private LocalDate usageDate;

    // enum は必ず VARCHAR に落とす。H2 のネイティブ ENUM 型だと、定数を足したときに
    // ddl-auto=update が既存カラムを変えず、実行時に INSERT が落ちる（ValidationIssue 参照）。
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "department", nullable = false, length = 32)
    private Department department;

    /** 表示用の申請者名（空白を1つに畳んだもの）。原文は expense_raw_rows 側に残る。 */
    @Column(name = "applicant_name", length = 128)
    private String applicantName;

    /** 同一人物判定用のキー（空白除去・小文字化）。重複判定はこちらを使う。 */
    @Column(name = "applicant_name_key", length = 128)
    private String applicantNameKey;

    /** 標準費目。寄せられなければ null（CATEGORY_UNMAPPED が付く）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "category", length = 32)
    private ExpenseCategory category;

    /** 円単位。浮動小数点は使わない（計画書 4.1）。 */
    @Column(name = "amount_yen")
    private Long amountYen;

    /** 備考原文。 */
    @Column(name = "note", length = 1000)
    private String note;

    /** 重複判定用に空白・記号を落とした備考（計画書 6）。 */
    @Column(name = "note_normalized", length = 1000)
    private String noteNormalized;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private ExpenseStatus status = ExpenseStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "risk_level", nullable = false, length = 16)
    private RiskLevel riskLevel = RiskLevel.NONE;

    @Column(name = "source_file_id", nullable = false)
    private UUID sourceFileId;

    @Column(name = "source_row_number", nullable = false)
    private Integer sourceRowNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // --- レビュー結果 -------------------------------------------------------
    // MVPではテーブル数を抑えるため review_decisions を独立させず明細に持たせている。
    // 現在の状態だけが残り履歴は残らない（計画書 4.2 との差分。9章参照）。

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "reviewed_by", length = 128)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public Expense(UUID sourceFileId, int sourceRowNumber, Department department) {
        this.sourceFileId = sourceFileId;
        this.sourceRowNumber = sourceRowNumber;
        this.department = department;
    }

    public void review(ExpenseStatus decision, String reason, String reviewedBy) {
        this.status = decision;
        this.reviewReason = reason;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
    }
}
