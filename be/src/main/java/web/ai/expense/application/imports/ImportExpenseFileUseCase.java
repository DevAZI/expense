package web.ai.expense.application.imports;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.ai.expense.application.validation.ValidationEngine;
import web.ai.expense.config.ExpenseRuleProperties;
import web.ai.expense.domain.expense.Department;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.expense.ExpenseRepository;
import web.ai.expense.domain.expense.ExpenseStatus;
import web.ai.expense.domain.imports.*;
import web.ai.expense.domain.validation.*;
import web.ai.expense.infrastructure.csv.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CSV取込の調停（計画書 5）。
 *
 * <p>MVPでは同期実行。計画書 16 が求める非同期ジョブ化・二重実行防止は対象外で、
 * ファイルサイズ上限（multipart 設定）で現実的な処理時間に収めている。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportExpenseFileUseCase {

    private final CharsetDetector charsetDetector;
    private final CsvReader csvReader;
    private final CsvParserRegistry parserRegistry;
    private final ExpenseNormalizer normalizer;
    private final ValidationEngine validationEngine;
    private final ExpenseRuleProperties ruleProperties;
    private final Clock clock;
    private final ImportFileRepository importFileRepository;
    private final RawExpenseRowRepository rawRowRepository;
    private final ExpenseRepository expenseRepository;
    private final ValidationIssueRepository issueRepository;

    /**
     * @param targetYearMonth この取込の対象月。null なら設定の既定値。
     *                        対象月は期間外判定だけでなく、年省略日付（"6/2"）の年の補完にも効く。
     */
    @Transactional
    public ImportResult execute(String fileName, byte[] content, Department requestedDepartment,
                                YearMonth targetYearMonth) {
        String hash = sha256(content);

        // ルールは取込の開始時に1回だけ凍結する（計画書 8.3）。以降の処理は
        // このスナップショットしか見ないため、途中でルールが変わっても判定はぶれない。
        // 基準日も同じ理由でここで固定する（日付をまたぐ取込で未来判定がぶれないように）。
        RuleSnapshot snapshot = ruleProperties.toSnapshot(targetYearMonth, LocalDate.now(clock));

        List<UUID> sameHash = importFileRepository.findByFileHash(hash).stream()
                .map(ImportFile::getId)
                .toList();

        var detected = charsetDetector.detect(content);
        if (detected.isEmpty()) {
            return saveFailure(fileName, hash, "文字コードを判定できません（UTF-8/UTF-8 BOM/CP932 のいずれでもない）");
        }

        CsvReader.Content csv;
        DepartmentCsvParser parser;
        List<ParsedCsvRow> parsedRows;
        try {
            csv = csvReader.read(detected.get().content(), detected.get().charset());
            parser = parserRegistry.select(csv.header(), requestedDepartment);
            // 部署を明示選択した取込は強制マッピング（ヘッダー名の差異を無視して正準列位置で読む）。
            boolean forced = requestedDepartment != null;
            parsedRows = parser.parse(csv.header(), csv.dataRows(), forced);
        } catch (CsvImportException e) {
            log.warn("取込失敗 file={} reason={}", fileName, e.getMessage());
            return saveFailure(fileName, hash, e.getMessage());
        }

        ImportFile importFile = ImportFile.completed(fileName, hash, detected.get().label(), parser.department());
        importFile.setRowCount(parsedRows.size());
        // 実際に使った対象月を残す。判定理由を後から説明するために要る。
        importFile.setTargetYearMonth(snapshot.targetYearMonth().toString());
        importFileRepository.save(importFile);

        List<ExpenseEvaluation> evaluations = persistRows(parsedRows, importFile, parser.department(), snapshot);
        // ファイル横断の重複判定のため、同一対象月に取込済みの明細を突き合わせ相手として渡す。
        List<Expense> priorExpenses = loadPriorExpensesInScope(snapshot.targetYearMonth(), importFile.getId());
        List<ValidationIssue> issues = validationEngine.validate(evaluations, priorExpenses, snapshot);
        applyRiskLevels(evaluations, issues);
        issueRepository.saveAll(issues);

        long red = issues.stream().filter(i -> i.getSeverity() == Severity.RED).count();
        long yellow = issues.stream().filter(i -> i.getSeverity() == Severity.YELLOW).count();

        return new ImportResult(
                importFile.getId(), fileName, importFile.getStatus(), importFile.getCharset(),
                importFile.getDepartment(), importFile.getDepartment().label(),
                importFile.getTargetYearMonth(),
                parsedRows.size(), (int) red, (int) yellow, null, sameHash);
    }

    private List<ExpenseEvaluation> persistRows(List<ParsedCsvRow> parsedRows, ImportFile importFile,
                                                Department department, RuleSnapshot snapshot) {
        List<ExpenseEvaluation> evaluations = new ArrayList<>(parsedRows.size());

        for (ParsedCsvRow row : parsedRows) {
            RawExpenseRow raw = normalizer.toRawRow(row, importFile.getId());
            Expense expense = normalizer.toExpense(row, importFile.getId(), department, snapshot);
            evaluations.add(new ExpenseEvaluation(expense, raw));
        }

        // 検証は id を前提にするため、ルール実行前に採番を済ませる。
        rawRowRepository.saveAll(evaluations.stream().map(ExpenseEvaluation::raw).toList());
        expenseRepository.saveAll(evaluations.stream().map(ExpenseEvaluation::expense).toList());

        return evaluations;
    }

    /**
     * ファイル横断の重複判定に使う、既に取込済みの明細を集める。
     *
     * <p>突き合わせ範囲を同一対象月に絞るのは、対象月が経理の照合単位だから。別の対象月で
     * 取り込んだ明細（＝別の精算バッチ）を混ぜると、月をまたいだ正当な定期費用まで重複扱いに
     * なりうる。今回取り込み中のファイルは（対象月クエリに引っかかるので）id で明示的に除く。
     */
    private List<Expense> loadPriorExpensesInScope(YearMonth targetYearMonth, UUID currentFileId) {
        List<UUID> priorFileIds = importFileRepository
                .findByTargetYearMonthAndStatus(targetYearMonth.toString(), ImportStatus.COMPLETED)
                .stream()
                .map(ImportFile::getId)
                .filter(id -> !id.equals(currentFileId))
                .toList();
        if (priorFileIds.isEmpty()) {
            return List.of();
        }
        return expenseRepository.findBySourceFileIdInAndStatusNot(priorFileIds, ExpenseStatus.REJECTED);
    }

    private void applyRiskLevels(List<ExpenseEvaluation> evaluations, List<ValidationIssue> issues) {
        Map<UUID, List<ValidationIssue>> byExpense = issues.stream()
                .collect(Collectors.groupingBy(ValidationIssue::getExpenseId));

        for (ExpenseEvaluation evaluation : evaluations) {
            Expense expense = evaluation.expense();
            List<ValidationIssue> own = byExpense.getOrDefault(expense.getId(), List.of());
            expense.setRiskLevel(validationEngine.riskLevelOf(own));
        }
        expenseRepository.saveAll(evaluations.stream().map(ExpenseEvaluation::expense).toList());
    }

    private ImportResult saveFailure(String fileName, String hash, String reason) {
        ImportFile failed = ImportFile.failed(fileName, hash, reason);
        importFileRepository.save(failed);
        return ImportResult.failed(failed.getId(), fileName, reason);
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が利用できません", e);
        }
    }
}
