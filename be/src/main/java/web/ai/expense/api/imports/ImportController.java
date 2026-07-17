package web.ai.expense.api.imports;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import web.ai.expense.application.imports.ImportExpenseFileUseCase;
import web.ai.expense.application.imports.ImportResult;
import web.ai.expense.application.imports.ResetDataUseCase;
import web.ai.expense.application.imports.ResetResult;
import web.ai.expense.domain.expense.Department;
import web.ai.expense.domain.imports.ImportFile;
import web.ai.expense.domain.imports.ImportFileRepository;
import web.ai.expense.infrastructure.csv.CsvImportException;

import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportExpenseFileUseCase importUseCase;
    private final ResetDataUseCase resetUseCase;
    private final ImportFileRepository importFileRepository;

    /**
     * CSVアップロードと取込（計画書 9.3）。MVPのため同期実行。
     *
     * @param department 省略時はヘッダーから自動判定する（計画書 2）。部署間でヘッダーが
     *                   重なり判定が曖昧な場合のみ、明示が必要になる。
     */
    @PostMapping
    public ResponseEntity<ImportResult> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "department", required = false) Department department,
            @RequestParam(value = "targetYearMonth", required = false) String targetYearMonth) {

        if (file.isEmpty()) {
            throw new CsvImportException("ファイルが空です");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
            // ファイル名は信用しないが、拡張子違いの誤アップロードは早期に弾く（計画書 16）。
            throw new CsvImportException("CSVファイルを指定してください");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new CsvImportException("ファイルを読み取れません: " + e.getMessage(), e);
        }

        return ResponseEntity.ok(
                importUseCase.execute(fileName, content, department, parseTargetYearMonth(targetYearMonth)));
    }

    /**
     * 対象月は YearMonth の自動バインドに任せず自前で解析する。バインドに失敗すると
     * 「Failed to convert value of type String」のような、経理には意味の分からない
     * 400 が返るため。null（未指定）は設定の既定値を使う合図。
     */
    private YearMonth parseTargetYearMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new CsvImportException("対象月の形式が正しくありません: " + value + "（例: 2026-06）");
        }
    }

    @GetMapping("/{id}")
    public ImportFile get(@PathVariable UUID id) {
        return importFileRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("取込ファイルが見つかりません: " + id));
    }

    @GetMapping
    public java.util.List<ImportFile> list() {
        return importFileRepository.findAll();
    }

    /**
     * 全データの初期化。翌月分を入れる前に前月分を空にする用途。
     *
     * <p>承認履歴も一緒に消える（MVPではレビュー結果を明細に持たせているため）。
     * 取り返しがつかないので、画面側で件数を見せて確認を取ってから呼ぶこと。
     */
    @DeleteMapping
    public ResetResult resetAll() {
        return resetUseCase.resetAll();
    }

    /** 1ファイル分だけ取り消す。間違ったファイルを取り込んだとき用。 */
    @DeleteMapping("/{id}")
    public ResetResult resetOne(@PathVariable UUID id) {
        return resetUseCase.resetImportFile(id);
    }
}
