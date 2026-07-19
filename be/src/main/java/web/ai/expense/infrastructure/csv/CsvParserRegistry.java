package web.ai.expense.infrastructure.csv;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import web.ai.expense.config.CsvSchemaProperties;
import web.ai.expense.domain.expense.Department;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 部署別アダプターを選ぶ（計画書 2・5-3）。ファイル名では判定しない。
 *
 * <p>選択の入口は2経路ある:
 * <ul>
 *   <li><b>明示選択（推奨）</b> — 呼び出し側が {@code requestedDepartment} を渡す。
 *       ヘッダー自動判定を<b>経由せず</b>、その部署のスキーマで直接読む。他部署が同じ
 *       ヘッダー形式を使い回しても曖昧にならず、誤判定のリスクが構造的に無い。
 *   <li><b>自動判定（フェーズ1の許容パス）</b> — {@code requestedDepartment} が null のとき、
 *       ヘッダーに一致する部署を推定する。一致が複数なら例外を投げて明示を要求する。
 * </ul>
 * 最終的には UI 側で部署をドロップダウン選択させ、明示選択パスを既定にしていく。
 */
@Component
public class CsvParserRegistry {

    private final List<DepartmentCsvParser> parsers;
    /** 明示選択パス用。部署 -> その部署のアダプター（1部署につき1つ）。 */
    private final Map<Department, DepartmentCsvParser> byDepartment;

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

        Map<Department, DepartmentCsvParser> index = new LinkedHashMap<>();
        for (DepartmentCsvParser parser : all) {
            // 手書きを先に入れているので putIfAbsent で手書き優先を保つ。
            index.putIfAbsent(parser.department(), parser);
        }
        this.byDepartment = Map.copyOf(index);
    }

    /**
     * @param requestedDepartment 呼び出し側が明示した部署。null なら自動判定。
     * @throws CsvImportException 判定不能・曖昧な場合（計画書 2「判定不能なファイルは取込エラー」）
     */
    public DepartmentCsvParser select(CsvHeader header, Department requestedDepartment) {
        if (requestedDepartment != null) {
            return selectExplicit(requestedDepartment);
        }
        return autoDetect(header);
    }

    /**
     * 明示選択パス。自動判定を行わず、指定部署のスキーマを返す。
     * ヘッダー名が別名に一致しなくても拒否しない。実際の列マッピングは
     * {@link DepartmentCsvParser#parse} の forced=true 側が正準列位置で強制的に行う
     * （呼び出し側は requestedDepartment != null を forced として渡す）。
     */
    private DepartmentCsvParser selectExplicit(Department requestedDepartment) {
        DepartmentCsvParser chosen = byDepartment.get(requestedDepartment);
        if (chosen == null) {
            throw new CsvImportException(
                    "部署「" + requestedDepartment.label() + "」のCSVスキーマが未定義です");
        }
        return chosen;
    }

    /** 自動判定パス（フェーズ1の許容）。ヘッダーに一致する部署を推定する。 */
    private DepartmentCsvParser autoDetect(CsvHeader header) {
        List<DepartmentCsvParser> candidates = parsers.stream()
                .filter(p -> p.supports(header))
                .toList();

        if (candidates.isEmpty()) {
            throw new CsvImportException(
                    "ヘッダーに対応する部署CSVスキーマがありません: " + header.names()
                            + "。部署を指定して取り込んでください");
        }

        if (candidates.size() > 1) {
            // 部署間でヘッダー別名が重なると起きる。黙って先頭を選ぶと誤った部署で
            // 集計されるため、部署の明示を要求する。
            String names = candidates.stream()
                    .map(p -> p.department().label())
                    .collect(Collectors.joining(", "));
            throw new CsvImportException(
                    "ヘッダーが複数の部署スキーマに一致しました(" + names + ")。部署を指定してください");
        }

        return candidates.get(0);
    }
}
