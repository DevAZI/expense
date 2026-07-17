package web.ai.expense.application.export;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.expense.ExpenseRepository;
import web.ai.expense.domain.expense.ExpenseStatus;
import web.ai.expense.domain.validation.Severity;
import web.ai.expense.domain.validation.ValidationIssue;
import web.ai.expense.domain.validation.ValidationIssueRepository;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 承認済み明細のCSV出力（計画書 9.3）。
 *
 * <p>出力時に数式インジェクションを無害化する（計画書 8.5 末尾）。金額は数値として厳密に
 * 解析済みなので、無害化の対象は備考など文字列列だけに限る。負数の返金を「-」で始まるから
 * といって一律に潰さないのがポイント。
 */
@Service
@RequiredArgsConstructor
public class ExpenseCsvExportService {

    private static final Set<Character> FORMULA_PREFIXES = Set.of('=', '+', '-', '@');

    private final ExpenseRepository expenseRepository;
    private final ValidationIssueRepository issueRepository;

    @Transactional(readOnly = true)
    public String exportApproved() {
        Set<UUID> blockedByRed = issueRepository.findBySeverityAndResolved(Severity.RED, false).stream()
                .map(ValidationIssue::getExpenseId)
                .collect(Collectors.toSet());

        List<Expense> rows = expenseRepository.findByStatus(ExpenseStatus.APPROVED).stream()
                .filter(e -> !blockedByRed.contains(e.getId()))
                .toList();

        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT)) {
            printer.printRecord("利用日", "部署", "申請者", "費目", "金額", "備考", "状態");
            for (Expense e : rows) {
                printer.printRecord(
                        e.getUsageDate(),
                        e.getDepartment(),
                        sanitize(e.getApplicantName()),
                        e.getCategory(),
                        e.getAmountYen(),
                        sanitize(e.getNote()),
                        e.getStatus()
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException("CSVを生成できません", e);
        }
        return out.toString();
    }

    /** 表計算ソフトが数式として解釈しうる先頭文字を無害化する。文字列列にのみ適用する。 */
    private String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        return FORMULA_PREFIXES.contains(first) ? "'" + value : value;
    }
}
