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

    // 正準列順。配布された3部署の実ファイルはいずれも
    // 「日付・申請者・費目・金額・備考」の並びで一致する。部署を明示選択したのに
    // ヘッダー名が別名に無いとき、この位置で強制的にマッピングする根拠にする。
    private static final int CANON_DATE = 0;
    private static final int CANON_APPLICANT = 1;
    private static final int CANON_CATEGORY = 2;
    private static final int CANON_AMOUNT = 3;
    private static final int CANON_NOTE = 4;

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
    public List<ParsedCsvRow> parse(CsvHeader header, List<CsvReader.Row> dataRows, boolean forced) {
        int dateIdx = resolve(header, headers.getUsageDate(), CANON_DATE, "日付", forced);
        int applicantIdx = resolve(header, headers.getApplicantName(), CANON_APPLICANT, "申請者", forced);
        int categoryIdx = resolve(header, headers.getCategory(), CANON_CATEGORY, "費目", forced);
        int amountIdx = resolve(header, headers.getAmount(), CANON_AMOUNT, "金額", forced);
        Optional<Integer> noteIdx = header.indexOfAny(headers.getNote());
        if (noteIdx.isEmpty() && forced && header.size() > CANON_NOTE) {
            // 備考は任意項目。強制マッピングでも正準位置に列があれば拾う。
            noteIdx = Optional.of(CANON_NOTE);
        }

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

    /**
     * 列位置を解決する。まず別名で探し、見つからなければ:
     * <ul>
     *   <li>強制マッピング時（部署明示選択）は正準列位置を使う。ヘッダー名の差異を無視して
     *       その部署として読み切る。位置がその行に無ければ {@link #at} が null を返し、
     *       後段の検証（REQUIRED_FIELD_MISSING 等）が拾う。</li>
     *   <li>自動判定時は必須列不足として取込失敗にする。</li>
     * </ul>
     */
    private int resolve(CsvHeader header, List<String> aliases, int canonicalIndex, String label, boolean forced) {
        Optional<Integer> byAlias = header.indexOfAny(aliases);
        if (byAlias.isPresent()) {
            return byAlias.get();
        }
        if (forced) {
            return canonicalIndex;
        }
        throw new CsvImportException(
                department + " のスキーマに必要な列「" + label + "」がヘッダーにありません");
    }

    private String at(List<String> columns, int index) {
        if (index < 0 || index >= columns.size()) {
            return null;
        }
        return columns.get(index);
    }
}
