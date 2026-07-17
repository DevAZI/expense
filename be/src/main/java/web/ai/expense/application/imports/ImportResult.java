package web.ai.expense.application.imports;

import web.ai.expense.domain.expense.Department;
import web.ai.expense.domain.imports.ImportStatus;

import java.util.List;
import java.util.UUID;

public record ImportResult(
        UUID importFileId,
        String fileName,
        ImportStatus status,
        String charset,
        Department department,
        String departmentLabel,
        /** この取込で使った対象月（例 "2026-06"）。画面で必ず見せる。 */
        String targetYearMonth,
        int rowCount,
        int redCount,
        int yellowCount,
        String errorSummary,
        /** 同一内容で既に取り込まれているファイル。空でなければ再取込の疑い。 */
        List<UUID> sameHashImportFileIds
) {
    public static ImportResult failed(UUID id, String fileName, String reason) {
        return new ImportResult(id, fileName, ImportStatus.FAILED, null, null, null, null, 0, 0, 0, reason, List.of());
    }
}
