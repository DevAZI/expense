package web.ai.expense.domain.imports;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * 元CSV行（計画書 4.2 expense_raw_rows）。
 *
 * <p>正規化後の値と分離して保存することで、明細画面での「原文 vs 正規化値」の対比
 * （計画書 10）と追跡性（13 追跡性）を成立させる。正規化の実装を直しても原本は動かない。
 */
@Entity
@Table(name = "expense_raw_rows")
@Getter
@Setter
@NoArgsConstructor
public class RawExpenseRow {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "import_file_id", nullable = false)
    private UUID importFileId;

    /** 元CSVの行番号（ヘッダーを除いた1始まり）。 */
    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    /** 行の原文。 */
    @Column(name = "raw_line", length = 2000)
    private String rawLine;

    // 列ごとの原文。正規化前の姿を残す。
    @Column(name = "raw_usage_date", length = 100)
    private String rawUsageDate;

    @Column(name = "raw_applicant_name", length = 200)
    private String rawApplicantName;

    @Column(name = "raw_category", length = 200)
    private String rawCategory;

    @Column(name = "raw_amount", length = 100)
    private String rawAmount;

    @Column(name = "raw_note", length = 1000)
    private String rawNote;

    /** ヘッダーの列数。 */
    @Column(name = "expected_column_count")
    private Integer expectedColumnCount;

    /** この行の実際の列数。ヘッダーと違えば CSV_STRUCTURE_INVALID の対象。 */
    @Column(name = "actual_column_count")
    private Integer actualColumnCount;

    /** 溢れた列を備考へ結合して復元したか。 */
    @Column(name = "note_recovered", nullable = false)
    private boolean noteRecovered = false;
}
