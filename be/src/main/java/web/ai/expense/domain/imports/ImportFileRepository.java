package web.ai.expense.domain.imports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportFileRepository extends JpaRepository<ImportFile, UUID> {

    /** 同一ファイルの再取込検出に使う（計画書 12 Phase 1）。 */
    List<ImportFile> findByFileHash(String fileHash);
}
