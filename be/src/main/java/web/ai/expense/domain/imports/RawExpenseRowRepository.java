package web.ai.expense.domain.imports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RawExpenseRowRepository extends JpaRepository<RawExpenseRow, UUID> {

    List<RawExpenseRow> findByImportFileId(UUID importFileId);

    /** 明細詳細で「原文 vs 正規化値」を並べるために使う（計画書 10）。 */
    Optional<RawExpenseRow> findByImportFileIdAndRowNumber(UUID importFileId, int rowNumber);

    void deleteByImportFileId(UUID importFileId);
}
