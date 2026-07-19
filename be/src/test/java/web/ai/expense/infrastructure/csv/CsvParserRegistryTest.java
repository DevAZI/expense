package web.ai.expense.infrastructure.csv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import web.ai.expense.config.CsvSchemaProperties;
import web.ai.expense.domain.expense.Department;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 部署選択の2経路（明示選択 / 自動判定）と、明示選択時の強制マッピングを検証する。
 *
 * <p>明示選択パスの主眼は、他部署が同じ／似たヘッダー形式を使い回して自動判定が曖昧・不一致に
 * なる状況でも、明示指定した部署で確定して読み切れること。ヘッダー名が別名に一致しなくても、
 * 正準列順（日付・申請者・費目・金額・備考）の位置で強制的にマッピングする。
 */
class CsvParserRegistryTest {

    /** 手書きアダプター0件を表す空の ObjectProvider。 */
    private static final ObjectProvider<DepartmentCsvParser> NO_HANDWRITTEN = emptyProvider();

    private CsvParserRegistry registryWith(CsvSchemaProperties properties) {
        return new CsvParserRegistry(properties, NO_HANDWRITTEN);
    }

    /** 営業部とマーケで applicant-name の別名「氏名」が重なる設定。自動判定が曖昧になる。 */
    private CsvSchemaProperties overlappingSchemas() {
        CsvSchemaProperties props = new CsvSchemaProperties();
        props.getSchemas().put(Department.SALES, schema(
                List.of("日付"), List.of("申請者", "氏名"), List.of("勘定科目"), List.of("金額(円)"), List.of("備考")));
        props.getSchemas().put(Department.MARKETING, schema(
                List.of("利用日", "日付"), List.of("氏名"), List.of("費目", "勘定科目"), List.of("金額(円)"), List.of("メモ")));
        return props;
    }

    /** 配布実データの yml に対応する現実的なスキーマ（強制マッピングの検証用）。 */
    private CsvSchemaProperties realisticSchemas() {
        CsvSchemaProperties props = new CsvSchemaProperties();
        props.getSchemas().put(Department.SALES, schema(
                List.of("日付", "使用日"), List.of("申請者", "申請者名"), List.of("勘定科目", "科目"),
                List.of("金額(円)", "金額"), List.of("備考", "摘要")));
        props.getSchemas().put(Department.MARKETING, schema(
                List.of("利用日"), List.of("氏名", "担当者名"), List.of("費目"),
                List.of("支払金額"), List.of("メモ", "備考")));
        return props;
    }

    private CsvSchemaProperties.Schema schema(List<String> date, List<String> applicant,
                                              List<String> category, List<String> amount, List<String> note) {
        CsvSchemaProperties.Schema s = new CsvSchemaProperties.Schema();
        s.getHeaders().setUsageDate(date);
        s.getHeaders().setApplicantName(applicant);
        s.getHeaders().setCategory(category);
        s.getHeaders().setAmount(amount);
        s.getHeaders().setNote(note);
        return s;
    }

    private CsvHeader header(String... names) {
        return new CsvHeader(List.of(names));
    }

    @Test
    @DisplayName("明示選択: 自動判定が曖昧なヘッダーでも、指定部署で確定して読める")
    void explicitSelectionResolvesAmbiguousHeader() {
        CsvParserRegistry registry = registryWith(overlappingSchemas());
        // 「日付・氏名・勘定科目・金額(円)」は SALES とも MARKETING とも解釈できる曖昧なヘッダー。
        CsvHeader ambiguous = header("日付", "氏名", "勘定科目", "金額(円)", "備考");

        assertThat(registry.select(ambiguous, Department.SALES).department()).isEqualTo(Department.SALES);
        assertThat(registry.select(ambiguous, Department.MARKETING).department()).isEqualTo(Department.MARKETING);
    }

    @Test
    @DisplayName("自動判定: 同じ曖昧ヘッダーは複数一致で例外になり、部署の指定を促す")
    void autoDetectRejectsAmbiguousHeader() {
        CsvParserRegistry registry = registryWith(overlappingSchemas());
        CsvHeader ambiguous = header("日付", "氏名", "勘定科目", "金額(円)", "備考");

        assertThatThrownBy(() -> registry.select(ambiguous, null))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("部署を指定してください");
    }

    @Test
    @DisplayName("明示選択: 別名に一致しない列名でも拒否せず、正準列位置で強制マッピングして読む")
    void explicitSelectionForcesMappingByPosition() {
        CsvParserRegistry registry = registryWith(realisticSchemas());
        // マーケの別名に「申請者」「支払金額(円)」は無い。それでもマーケ指定で読み切れること。
        CsvHeader header = header("利用日", "申請者", "費目", "支払金額(円)", "メモ");

        DepartmentCsvParser parser = registry.select(header, Department.MARKETING);
        assertThat(parser.department()).isEqualTo(Department.MARKETING);

        CsvReader.Row row = new CsvReader.Row(
                List.of("2026-07-01", "金 智浩", "ソフトウェア費", "4,980", "GitHub Copilot 月額利用料"),
                "2026-07-01,金 智浩,ソフトウェア費,\"4,980\",GitHub Copilot 月額利用料");

        List<ParsedCsvRow> parsed = parser.parse(header, List.of(row), true);

        assertThat(parsed).hasSize(1);
        ParsedCsvRow r = parsed.get(0);
        assertThat(r.usageDate()).isEqualTo("2026-07-01"); // 別名一致（利用日）
        assertThat(r.applicantName()).isEqualTo("金 智浩");  // 位置2列目で強制マッピング
        assertThat(r.category()).isEqualTo("ソフトウェア費");
        assertThat(r.amount()).isEqualTo("4,980");          // 位置4列目で強制マッピング
        assertThat(r.note()).isEqualTo("GitHub Copilot 月額利用料");
    }

    @Test
    @DisplayName("自動判定: 別名に一致しない必須列があれば取込失敗にする（強制はしない）")
    void autoDetectDoesNotForce() {
        CsvParserRegistry registry = registryWith(realisticSchemas());
        // どの部署の別名にも一致しない applicant/amount 列名。自動判定では読み切れない。
        CsvHeader header = header("利用日", "申請者", "費目", "支払金額(円)", "メモ");

        assertThatThrownBy(() -> registry.select(header, null))
                .isInstanceOf(CsvImportException.class);
    }

    private static ObjectProvider<DepartmentCsvParser> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public DepartmentCsvParser getObject(Object... args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public DepartmentCsvParser getIfAvailable() {
                return null;
            }

            @Override
            public DepartmentCsvParser getIfUnique() {
                return null;
            }

            @Override
            public DepartmentCsvParser getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Stream<DepartmentCsvParser> stream() {
                return Stream.empty();
            }
        };
    }
}
