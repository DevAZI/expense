package web.ai.expense.infrastructure.csv;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import web.ai.expense.config.CsvSchemaProperties;
import web.ai.expense.domain.expense.Department;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ヘッダーから部署別アダプターを選ぶ（計画書 2・5-3）。ファイル名では判定しない。
 */
@Component
public class CsvParserRegistry {

    private final List<DepartmentCsvParser> parsers;

    /**
     * @param handWrittenProvider 部署固有の手書きアダプター。MVPでは0件なので ObjectProvider で受ける
     *                            （List で受けると候補0件のとき起動に失敗する）。
     */
    public CsvParserRegistry(CsvSchemaProperties properties,
                             ObjectProvider<DepartmentCsvParser> handWrittenProvider) {
        List<DepartmentCsvParser> handWritten = handWrittenProvider.stream().toList();
        List<DepartmentCsvParser> all = new ArrayList<>(handWritten);
        // 手書き実装がある部署は設定駆動より優先する（部署固有の癖への逃げ道）。
        properties.getSchemas().forEach((department, schema) -> {
            boolean overridden = handWritten.stream().anyMatch(p -> p.department() == department);
            if (!overridden) {
                all.add(new SchemaDrivenCsvParser(department, schema.getHeaders()));
            }
        });
        this.parsers = List.copyOf(all);
    }

    /**
     * @param requestedDepartment 呼び出し側が明示した部署。null なら自動判定。
     * @throws CsvImportException 判定不能・曖昧な場合（計画書 2「判定不能なファイルは取込エラー」）
     */
    public DepartmentCsvParser select(CsvHeader header, Department requestedDepartment) {
        List<DepartmentCsvParser> candidates = parsers.stream()
                .filter(p -> p.supports(header))
                .toList();

        if (candidates.isEmpty()) {
            throw new CsvImportException(
                    "ヘッダーに対応する部署CSVスキーマがありません: " + header.names());
        }

        if (requestedDepartment != null) {
            return candidates.stream()
                    .filter(p -> p.department() == requestedDepartment)
                    .findFirst()
                    .orElseThrow(() -> new CsvImportException(
                            "指定された部署 " + requestedDepartment + " のスキーマはこのヘッダーに一致しません: " + header.names()));
        }

        if (candidates.size() > 1) {
            // 部署間でヘッダー別名が重なると起きる。黙って先頭を選ぶと誤った部署で
            // 集計されるため、部署の明示を要求する。
            String names = candidates.stream()
                    .map(p -> p.department().name())
                    .collect(Collectors.joining(", "));
            throw new CsvImportException(
                    "ヘッダーが複数の部署スキーマに一致しました(" + names + ")。department を指定してください");
        }

        return candidates.get(0);
    }
}
