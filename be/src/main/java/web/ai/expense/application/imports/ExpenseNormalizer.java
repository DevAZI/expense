package web.ai.expense.application.imports;

import org.springframework.stereotype.Component;
import web.ai.expense.domain.expense.Department;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.imports.RawExpenseRow;
import web.ai.expense.domain.validation.RuleContext;
import web.ai.expense.infrastructure.csv.ParsedCsvRow;
import web.ai.expense.shared.*;

import java.util.UUID;

/**
 * 原文から正規化明細を作る（計画書 6）。
 *
 * <p>ここは「寄せられるものは寄せ、寄せられないものは null にする」だけを行い、良し悪しの
 * 判断はしない。判断を検証ルールに一本化することで、判定理由を画面で説明できる状態を保つ。
 */
@Component
public class ExpenseNormalizer {

    public Expense toExpense(ParsedCsvRow row, UUID importFileId, Department department, RuleContext context) {
        Expense expense = new Expense(importFileId, row.rowNumber(), department);

        expense.setUsageDate(DateNormalizer.normalize(row.usageDate(), context.targetYearMonth()).orElse(null));
        expense.setApplicantName(NameNormalizer.toDisplayName(row.applicantName()));
        expense.setApplicantNameKey(NameNormalizer.toComparisonKey(row.applicantName()));

        String categoryKey = CategoryNormalizer.toMappingKey(row.category());
        expense.setCategory(categoryKey == null ? null : context.mapCategory(categoryKey).orElse(null));

        expense.setAmountYen(AmountNormalizer.normalize(row.amount()).orElse(null));
        expense.setNote(TextNormalizer.blankToNull(row.note()));
        expense.setNoteNormalized(NoteNormalizer.toComparisonKey(row.note()));

        return expense;
    }

    public RawExpenseRow toRawRow(ParsedCsvRow row, UUID importFileId) {
        RawExpenseRow raw = new RawExpenseRow();
        raw.setImportFileId(importFileId);
        raw.setRowNumber(row.rowNumber());
        raw.setRawLine(truncate(row.rawLine(), 2000));
        raw.setRawUsageDate(truncate(row.usageDate(), 100));
        raw.setRawApplicantName(truncate(row.applicantName(), 200));
        raw.setRawCategory(truncate(row.category(), 200));
        raw.setRawAmount(truncate(row.amount(), 100));
        raw.setRawNote(truncate(row.note(), 1000));
        raw.setExpectedColumnCount(row.expectedColumnCount());
        raw.setActualColumnCount(row.actualColumnCount());
        raw.setNoteRecovered(row.noteRecovered());
        return raw;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
