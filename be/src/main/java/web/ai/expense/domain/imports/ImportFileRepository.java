package web.ai.expense.domain.imports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportFileRepository extends JpaRepository<ImportFile, UUID> {

    /** 同一ファイルの再取込検出に使う（計画書 12 Phase 1）。 */
    List<ImportFile> findByFileHash(String fileHash);

    /** ファイル横断の重複判定で、同一対象月に取込済みの他ファイルを特定するのに使う。 */
    List<ImportFile> findByTargetYearMonthAndStatus(String targetYearMonth, ImportStatus status);
}
