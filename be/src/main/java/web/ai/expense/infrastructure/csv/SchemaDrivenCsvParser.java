package web.ai.expense.infrastructure.csv;

import web.ai.expense.config.CsvSchemaProperties;
import web.ai.expense.domain.expense.Department;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 設定のヘッダー別名でCSVを読む汎用アダプター。部署ごとに1インスタンス生成される
 * （生成は {@link CsvParserRegistry}）。
 */
public class SchemaDrivenCsvParser implements DepartmentCsvParser {

    private final Department department;
    private final CsvSchemaProperties.Headers headers;

    public SchemaDrivenCsvParser(Department department, CsvSchemaProperties.Headers headers) {
        this.department = department;
        this.headers = headers;
    }

    @Override
    public Department department() {
        return department;
    }

    /** 必須4項目すべてを解決できるヘッダーだけを自部署のものとみなす。備考は任意。 */
    @Override
    public boolean supports(CsvHeader header) {
        return header.indexOfAny(headers.getUsageDate()).isPresent()
                && header.indexOfAny(headers.getApplicantName()).isPresent()
                && header.indexOfAny(headers.getCategory()).isPresent()
                && header.indexOfAny(headers.getAmount()).isPresent();
    }

    @Override
    public List<ParsedCsvRow> parse(CsvHeader header, List<CsvReader.Row> dataRows) {
        int dateIdx = required(header, headers.getUsageDate(), "日付");
        int applicantIdx = required(header, headers.getApplicantName(), "申請者");
        int categoryIdx = required(header, headers.getCategory(), "費目");
        int amountIdx = required(header, headers.getAmount(), "金額");
        Optional<Integer> noteIdx = header.indexOfAny(headers.getNote());

        int expected = header.size();
        boolean noteIsLastColumn = noteIdx.isPresent() && noteIdx.get() == expected - 1;

        List<ParsedCsvRow> rows = new ArrayList<>();
        for (int i = 0; i < dataRows.size(); i++) {
            List<String> columns = dataRows.get(i).columns();
            int rowNumber = i + 1;
            int actual = columns.size();

            String note = noteIdx.map(idx -> at(columns, idx)).orElse(null);
            boolean recovered = false;

            // 備考に引用符なしのカンマが入ると列が溢れる（実データの開発部3行が該当:
            // 「Meguro to Shibuya, client sync」）。備考が最終列なら溢れた分は
            // 備考の続きでしかないので、結合して原文を復元する。
            // 黙って切り捨てると経理はデータが欠けたことに気づけない。
            if (actual > expected && noteIsLastColumn) {
                List<String> tail = columns.subList(noteIdx.get(), actual);
                note = String.join(",", tail);
                recovered = true;
            }

            rows.add(new ParsedCsvRow(
                    rowNumber,
                    // 元テキストをそのまま渡す。列を再連結して作り直すと引用符が落ち、
                    // 原文と突き合わせられなくなる（CsvReader.sliceRawLine 参照）。
                    dataRows.get(i).rawLine(),
                    at(columns, dateIdx),
                    at(columns, applicantIdx),
                    at(columns, categoryIdx),
                    at(columns, amountIdx),
                    note,
                    expected,
                    actual,
                    recovered
            ));
        }
        return rows;
    }

    private int required(CsvHeader header, List<String> aliases, String label) {
        return header.indexOfAny(aliases)
                .orElseThrow(() -> new CsvImportException(
                        department + " のスキーマに必要な列「" + label + "」がヘッダーにありません"));
    }

    private String at(List<String> columns, int index) {
        if (index < 0 || index >= columns.size()) {
            return null;
        }
        return columns.get(index);
    }
}
