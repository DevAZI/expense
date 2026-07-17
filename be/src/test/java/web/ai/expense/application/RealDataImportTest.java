package web.ai.expense.application;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import web.ai.expense.application.imports.ImportExpenseFileUseCase;
import web.ai.expense.application.imports.ImportResult;
import web.ai.expense.domain.expense.*;
import web.ai.expense.domain.imports.ImportStatus;
import web.ai.expense.domain.validation.RuleCode;
import web.ai.expense.domain.validation.ValidationIssue;
import web.ai.expense.domain.validation.ValidationIssueRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配布された実データ3ファイルをそのまま流す。合成データではなく実物で確かめるためのテスト。
 *
 * <p>ここで固定している件数・金額は実ファイルを目視で数えた値。ルールやマッピングを変えた際に
 * 実データでの挙動が変わったら、それが意図した変更かをこのテストが問う。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RealDataImportTest {

    /** リポジトリ直下の sample-data/。gradle の test は be/ を作業ディレクトリにする。 */
    private static final Path DATA_DIR = Path.of("..", "sample-data");

    @Autowired
    ImportExpenseFileUseCase importUseCase;
    @Autowired
    ExpenseRepository expenseRepository;
    @Autowired
    ValidationIssueRepository issueRepository;
    @Autowired
    web.ai.expense.domain.imports.RawExpenseRowRepository rawRowRepository;

    @BeforeAll
    static void requireRealData() {
        assertThat(DATA_DIR.resolve("eigyo_keihi_R8-06.csv"))
                .as("実データが sample-data/ にあること")
                .exists();
    }

    private ImportResult importFile(String fileName) throws IOException {
        byte[] bytes = Files.readAllBytes(DATA_DIR.resolve(fileName));
        // 部署は指定しない。ヘッダーからの自動判定が実データで機能することを含めて確認する。
        // 対象月も指定しない。配布データは2026年6月分で、設定の既定値と一致する。
        return importUseCase.execute(fileName, bytes, null, null);
    }

    @Test
    @DisplayName("営業部: CP932・和暦の実ファイルを部署自動判定で取り込める")
    void importsRealSalesFile() throws IOException {
        ImportResult result = importFile("eigyo_keihi_R8-06.csv");

        assertThat(result.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.charset()).isEqualTo("CP932");
        assertThat(result.department()).isEqualTo(Department.SALES);
        assertThat(result.rowCount()).isEqualTo(22);

        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());
        // 全22行が費目に寄せられる（未マッピングが出ないこと）
        assertThat(rows).allSatisfy(e -> assertThat(e.getCategory()).isNotNull());

        Expense first = row(rows, 1);
        assertThat(first.getUsageDate()).isEqualTo(LocalDate.of(2026, 6, 2)); // 令和8年6月2日
        assertThat(first.getApplicantName()).isEqualTo("佐藤 健一");          // 表示名は空白を保つ
        assertThat(first.getCategory()).isEqualTo(ExpenseCategory.TRANSPORTATION);
        assertThat(first.getAmountYen()).isEqualTo(3480L);
    }

    @Test
    @DisplayName("営業部: 勘定科目の区別が保たれる（交通費≠旅費交通費、会議費≠交際費）")
    void preservesAccountCategoryDistinction() throws IOException {
        ImportResult result = importFile("eigyo_keihi_R8-06.csv");
        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());

        assertThat(row(rows, 1).getCategory()).isEqualTo(ExpenseCategory.TRANSPORTATION);   // 交通費
        assertThat(row(rows, 6).getCategory()).isEqualTo(ExpenseCategory.TRAVEL_EXPENSE);   // 旅費交通費
        assertThat(row(rows, 7).getCategory()).isEqualTo(ExpenseCategory.ACCOMMODATION);    // 宿泊費
        assertThat(row(rows, 11).getCategory()).isEqualTo(ExpenseCategory.ENTERTAINMENT);   // 交際費 480,000
        assertThat(row(rows, 21).getCategory()).isEqualTo(ExpenseCategory.MEETING);         // 会議費 3,600
        assertThat(row(rows, 12).getCategory()).isEqualTo(ExpenseCategory.COMMUNICATION);   // 通信費
    }

    @Test
    @DisplayName("営業部: 6/10の完全重複・480,000円の高額・5/28の対象月外を検知する（計画書2章）")
    void detectsKnownSalesAnomalies() throws IOException {
        ImportResult result = importFile("eigyo_keihi_R8-06.csv");
        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());

        // 8,9行目: 佐藤 健一 / 交通費 / 3,480 / 客先訪問 品川往復 が完全一致
        assertThat(codesOf(row(rows, 8))).contains(RuleCode.EXACT_DUPLICATE);
        assertThat(codesOf(row(rows, 9))).contains(RuleCode.EXACT_DUPLICATE);

        // 11行目: 交際費 480,000（しきい値100,000超）
        assertThat(codesOf(row(rows, 11))).contains(RuleCode.HIGH_AMOUNT);

        // 16行目: 令和8年5月28日 は対象月6月の外
        assertThat(row(rows, 16).getUsageDate()).isEqualTo(LocalDate.of(2026, 5, 28));
        assertThat(codesOf(row(rows, 16))).contains(RuleCode.PERIOD_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("営業部: 備考だけ違う同日・同額の二重申請疑いを類似重複で拾う")
    void detectsSimilarDuplicateMissedByExactRule() throws IOException {
        ImportResult result = importFile("eigyo_keihi_R8-06.csv");
        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());

        // 19,20行目: 6/23 山口 大輔 / 交通費 / 1,260 が2件。
        // 備考が「新宿→渋谷 タクシー」と「タクシー」で違うため完全重複からは漏れる。
        assertThat(codesOf(row(rows, 19))).contains(RuleCode.SIMILAR_DUPLICATE);
        assertThat(codesOf(row(rows, 20))).contains(RuleCode.SIMILAR_DUPLICATE);
        assertThat(codesOf(row(rows, 19))).doesNotContain(RuleCode.EXACT_DUPLICATE);

        // 6/9 と 6/25 の旅費交通費 28,400（大阪出張と名古屋出張）は別物。
        // 同額でも16日離れているので誤検知しないこと。
        assertThat(codesOf(row(rows, 6))).doesNotContain(RuleCode.SIMILAR_DUPLICATE);
        assertThat(codesOf(row(rows, 22))).doesNotContain(RuleCode.SIMILAR_DUPLICATE);
    }

    @Test
    @DisplayName("開発部: UTF-8 BOM・英語名・カンマ付き金額を扱える")
    void importsRealDevFile() throws IOException {
        ImportResult result = importFile("dev_expenses_2026-06.csv");

        assertThat(result.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.charset()).isEqualTo("UTF-8-BOM");
        assertThat(result.department()).isEqualTo(Department.DEVELOPMENT);
        assertThat(result.rowCount()).isEqualTo(18);

        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());

        // 英語名が壊れないこと（KimJiho にならない）
        assertThat(row(rows, 1).getApplicantName()).isEqualTo("Kim Jiho");
        assertThat(row(rows, 1).getAmountYen()).isEqualTo(4980L); // "4,980"
        assertThat(row(rows, 1).getCategory()).isEqualTo(ExpenseCategory.SOFTWARE);
        assertThat(row(rows, 4).getCategory()).isEqualTo(ExpenseCategory.BOOKS); // 書籍（英日混在）
    }

    @Test
    @DisplayName("開発部: 備考の引用符なしカンマでデータを落とさず、復元して警告する")
    void recoversNoteSplitByUnquotedComma() throws IOException {
        ImportResult result = importFile("dev_expenses_2026-06.csv");
        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());

        // 3行目: Notes が「Meguro to Shibuya, client sync」で列が1つ溢れている
        Expense row3 = row(rows, 3);
        assertThat(row3.getNote()).isEqualTo("Meguro to Shibuya, client sync");
        assertThat(codesOf(row3)).contains(RuleCode.CSV_STRUCTURE_INVALID);
        // 金額・日付・費目は壊れていない
        assertThat(row3.getAmountYen()).isEqualTo(1340L);
        assertThat(row3.getUsageDate()).isEqualTo(LocalDate.of(2026, 6, 4));
    }

    @Test
    @DisplayName("開発部: 元CSV行を引用符ごとそのまま保持する（再連結で作り直さない）")
    void keepsRawLineVerbatim() throws IOException {
        ImportResult result = importFile("dev_expenses_2026-06.csv");
        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());

        var raw = rawRowRepository
                .findByImportFileIdAndRowNumber(result.importFileId(), row(rows, 1).getSourceRowNumber())
                .orElseThrow();

        // 実ファイルの1行目そのもの。金額の引用符が落ちて "4,980" が 4,980 になると、
        // 6列のCSVに見えてしまい原文と突き合わせられない（追跡性: 計画書 13）
        assertThat(raw.getRawLine())
                .isEqualTo("2026-06-02,Kim Jiho,Software,\"4,980\",GitHub Copilot monthly");
    }

    @Test
    @DisplayName("開発部: 6/31の不正日付・金額欠損・負数返金・298,000円のGPU部品を検知する")
    void detectsKnownDevAnomalies() throws IOException {
        ImportResult result = importFile("dev_expenses_2026-06.csv");
        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());

        // 5行目: Hardware 298,000（計画書 8.4 で要確認とされている額）
        assertThat(codesOf(row(rows, 5))).contains(RuleCode.HIGH_AMOUNT);

        // 6行目: "-3,200" 返金
        assertThat(row(rows, 6).getAmountYen()).isEqualTo(-3200L);
        assertThat(codesOf(row(rows, 6))).contains(RuleCode.NEGATIVE_AMOUNT);

        // 10行目: 金額が空欄
        assertThat(row(rows, 10).getAmountYen()).isNull();
        assertThat(codesOf(row(rows, 10))).contains(RuleCode.REQUIRED_FIELD_MISSING);

        // 11行目: 2026/6/31 は存在しない日付。丸めずREDにする
        assertThat(row(rows, 11).getUsageDate()).isNull();
        assertThat(row(rows, 11).getRiskLevel()).isEqualTo(RiskLevel.RED);
        assertThat(codesOf(row(rows, 11))).contains(RuleCode.INVALID_DATE);

        // 7,8行目: Osaka pre-meeting の完全重複
        assertThat(codesOf(row(rows, 7))).contains(RuleCode.EXACT_DUPLICATE);
        assertThat(codesOf(row(rows, 8))).contains(RuleCode.EXACT_DUPLICATE);
    }

    @Test
    @DisplayName("マーケ: BOMなしUTF-8・複数日付形式・全角数字・氏名表記揺れを扱える")
    void importsRealMarketingFile() throws IOException {
        ImportResult result = importFile("marketing_keihi_202606.csv");

        assertThat(result.status()).isEqualTo(ImportStatus.COMPLETED);
        // 計画書2章は「UTF-8 BOM」と書いているが実物にBOMは無い
        assertThat(result.charset()).isEqualTo("UTF-8");
        assertThat(result.department()).isEqualTo(Department.MARKETING);
        assertThat(result.rowCount()).isEqualTo(17);

        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());

        assertThat(row(rows, 1).getUsageDate()).isEqualTo(LocalDate.of(2026, 6, 2));  // 6/2（年省略）
        assertThat(row(rows, 3).getUsageDate()).isEqualTo(LocalDate.of(2026, 6, 5));  // 2026年6月5日
        assertThat(row(rows, 9).getUsageDate()).isEqualTo(LocalDate.of(2026, 6, 13)); // 06月13日

        assertThat(row(rows, 4).getAmountYen()).isEqualTo(12800L);  // １２８００
        assertThat(row(rows, 17).getAmountYen()).isEqualTo(1460L);  // １４６０

        // 「田中　美咲」と「田中美咲」は表示は別、照合キーは同一
        assertThat(row(rows, 1).getApplicantName()).isEqualTo("田中 美咲");
        assertThat(row(rows, 3).getApplicantName()).isEqualTo("田中美咲");
        assertThat(row(rows, 1).getApplicantNameKey()).isEqualTo(row(rows, 3).getApplicantNameKey());
    }

    @Test
    @DisplayName("マーケ: 費目誤記「交通日」は自動補正せず人に返す")
    void doesNotAutoCorrectCategoryTypo() throws IOException {
        ImportResult result = importFile("marketing_keihi_202606.csv");
        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());

        Expense typo = row(rows, 5); // 6/9 小林 蓮 交通日 980
        assertThat(typo.getCategory()).isNull();
        assertThat(codesOf(typo)).contains(RuleCode.CATEGORY_UNMAPPED);
    }

    @Test
    @DisplayName("マーケ: 1,200,000円の広告費・6/16の完全重複・金額欠損を検知する")
    void detectsKnownMarketingAnomalies() throws IOException {
        ImportResult result = importFile("marketing_keihi_202606.csv");
        List<Expense> rows = expenseRepository.findBySourceFileId(result.importFileId());

        assertThat(codesOf(row(rows, 6))).contains(RuleCode.HIGH_AMOUNT);        // 広告費 1,200,000
        assertThat(codesOf(row(rows, 10))).contains(RuleCode.EXACT_DUPLICATE);   // 6/16 消耗品費 3,980
        assertThat(codesOf(row(rows, 11))).contains(RuleCode.EXACT_DUPLICATE);
        assertThat(codesOf(row(rows, 13))).contains(RuleCode.REQUIRED_FIELD_MISSING); // 金額空欄

        // 6/2 と 6/24 の Meta広告 350,000 は同額だが22日離れており別物。誤検知しないこと
        assertThat(codesOf(row(rows, 1))).doesNotContain(RuleCode.SIMILAR_DUPLICATE);
        assertThat(codesOf(row(rows, 16))).doesNotContain(RuleCode.SIMILAR_DUPLICATE);
    }

    @Test
    @DisplayName("3ファイルとも部署の明示なしにヘッダーだけで判定できる")
    void detectsDepartmentFromHeaderAlone() throws IOException {
        assertThat(importFile("eigyo_keihi_R8-06.csv").department()).isEqualTo(Department.SALES);
        assertThat(importFile("dev_expenses_2026-06.csv").department()).isEqualTo(Department.DEVELOPMENT);
        assertThat(importFile("marketing_keihi_202606.csv").department()).isEqualTo(Department.MARKETING);
    }

    private Expense row(List<Expense> rows, int rowNumber) {
        return rows.stream()
                .filter(e -> e.getSourceRowNumber() == rowNumber)
                .findFirst()
                .orElseThrow(() -> new AssertionError("行 " + rowNumber + " が見つかりません"));
    }

    private Set<RuleCode> codesOf(Expense expense) {
        return issueRepository.findByExpenseId(expense.getId()).stream()
                .map(ValidationIssue::getRuleCode)
                .collect(Collectors.toSet());
    }
}
