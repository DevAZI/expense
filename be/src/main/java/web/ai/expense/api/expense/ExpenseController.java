package web.ai.expense.api.expense;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.ai.expense.application.export.ExpenseCsvExportService;
import web.ai.expense.application.summary.ExpenseSummary;
import web.ai.expense.application.summary.SummaryQueryService;
import web.ai.expense.domain.expense.*;
import web.ai.expense.domain.imports.RawExpenseRowRepository;
import web.ai.expense.domain.validation.ValidationIssueRepository;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseRepository expenseRepository;
    private final RawExpenseRowRepository rawRowRepository;
    private final ValidationIssueRepository issueRepository;
    private final SummaryQueryService summaryQueryService;
    private final ExpenseCsvExportService exportService;

    @GetMapping
    public Page<ExpenseResponse> list(
            @RequestParam(required = false) Department department,
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 50) Pageable pageable) {

        return expenseRepository
                .findAll(ExpenseSpecifications.filter(department, status, riskLevel, from, to), pageable)
                .map(ExpenseResponse::from);
    }

    @GetMapping("/summary")
    public ExpenseSummary summary() {
        return summaryQueryService.summarize();
    }

    /** 原文・正規化値・警告をまとめて返す（計画書 10 明細詳細）。 */
    @GetMapping("/{id}")
    public ExpenseDetailResponse detail(@PathVariable UUID id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("明細が見つかりません: " + id));

        var raw = rawRowRepository
                .findByImportFileIdAndRowNumber(expense.getSourceFileId(), expense.getSourceRowNumber())
                .orElse(null);

        List<IssueResponse> issues = issueRepository.findByExpenseId(id).stream()
                .map(IssueResponse::from)
                .toList();

        return new ExpenseDetailResponse(
                ExpenseResponse.from(expense),
                ExpenseDetailResponse.RawValues.from(raw),
                issues);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        String csv = exportService.exportApproved();
        // Excelでの文字化けを避けるため UTF-8 BOM を付ける。
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = csv.getBytes(Charset.forName("UTF-8"));
        byte[] withBom = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(body, 0, withBom, bom.length, body.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"expenses.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(withBom);
    }
}
