package web.ai.expense.infrastructure.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.CharArrayReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * バイト列をヘッダー+データ行に分解する。引用符・備考中カンマの扱いは Commons CSV に任せる。
 *
 * <p>MVPのため全件をメモリに読む（計画書 16 のストリーミング要件は対象外）。
 * 上限は spring.servlet.multipart.max-file-size で抑える。
 */
@Component
public class CsvReader {

    /** データ行1つ。解析後の列と、元テキストをそのまま持つ。 */
    public record Row(List<String> columns, String rawLine) {
    }

    public record Content(CsvHeader header, List<Row> dataRows) {
    }

    public Content read(byte[] bytes, Charset charset) {
        String text = new String(bytes, charset);

        try (CSVParser parser = CSVParser.parse(new CharArrayReader(text.toCharArray()), CSVFormat.DEFAULT)) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                throw new CsvImportException("CSVが空です");
            }

            CsvHeader header = new CsvHeader(toList(records.get(0)));

            List<Row> dataRows = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                List<String> columns = toList(records.get(i));
                if (isBlankRow(columns)) {
                    continue;
                }
                dataRows.add(new Row(columns, sliceRawLine(text, records, i)));
            }
            return new Content(header, dataRows);

        } catch (IOException | IllegalStateException e) {
            // 引用符不整合などは Commons CSV が IllegalStateException を投げる。
            // ここまで来たらファイル単位で失敗させる（計画書 7 CSV_STRUCTURE_INVALID）。
            throw new CsvImportException("CSVを解析できません: " + e.getMessage(), e);
        }
    }

    /**
     * 元テキストから、そのレコードの範囲をそのまま切り出す。
     *
     * <p>解析後の列をカンマで再連結して「元の行」とするのは誤り。引用符が落ちるため
     * {@code "2,680"} が {@code 2,680} になり、列数まで違って見える別物になる。
     * 追跡性（計画書 13）は原文と突き合わせられることが前提なので、原文を原文のまま持つ。
     * レコードの開始位置から次のレコードの開始位置までを取れば、引用符も引用符内の改行も保てる。
     */
    private String sliceRawLine(String text, List<CSVRecord> records, int index) {
        long start = records.get(index).getCharacterPosition();
        long end = (index + 1 < records.size())
                ? records.get(index + 1).getCharacterPosition()
                : text.length();

        if (start < 0 || end > text.length() || start >= end) {
            return null; // 位置が取れないときは黙って捏造せず、原文なしとして扱う
        }
        return text.substring((int) start, (int) end).strip();
    }

    private List<String> toList(CSVRecord record) {
        List<String> values = new ArrayList<>(record.size());
        record.forEach(values::add);
        return values;
    }

    private boolean isBlankRow(List<String> row) {
        return row.stream().allMatch(v -> v == null || v.isBlank());
    }
}
