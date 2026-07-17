package web.ai.expense.domain.imports;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import web.ai.expense.domain.expense.Department;

import java.time.LocalDateTime;
import java.util.UUID;

/** 取込ファイルの原本情報（計画書 4.2 import_files）。 */
@Entity
@Table(name = "import_files")
@Getter
@Setter
@NoArgsConstructor
public class ImportFile {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** SHA-256。同一ファイル再取込の検出に使う。 */
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    /** 検出した文字コード。判定不能なら null で status=FAILED。 */
    @Column(name = "charset", length = 32)
    private String charset;

    /** ヘッダーから判定した部署。判定不能なら null で status=FAILED（計画書 2）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "department", length = 32)
    private Department department;

    /**
     * この取込で使った対象月（例 "2026-06"）。
     *
     * <p>対象月は取込ごとに指定できるため、記録しておかないと「なぜこの明細が対象月外に
     * なったのか」を後から説明できない。計画書 8.2 の rule_version_id に相当する
     * 最小限の追跡情報。ルールをDB管理へ移す際はここがバージョンIDに置き換わる。
     */
    @Column(name = "target_year_month", length = 7)
    private String targetYearMonth;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private ImportStatus status;

    /** ファイル単位の失敗理由。行単位の警告はここには入らない。 */
    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public static ImportFile failed(String fileName, String fileHash, String reason) {
        ImportFile f = new ImportFile();
        f.fileName = fileName;
        f.fileHash = fileHash;
        f.status = ImportStatus.FAILED;
        f.errorSummary = reason;
        return f;
    }

    public static ImportFile completed(String fileName, String fileHash, String charset, Department department) {
        ImportFile f = new ImportFile();
        f.fileName = fileName;
        f.fileHash = fileHash;
        f.charset = charset;
        f.department = department;
        f.status = ImportStatus.COMPLETED;
        return f;
    }
}
