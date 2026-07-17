package web.ai.expense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import web.ai.expense.domain.expense.Department;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部署別CSVスキーマ（計画書 8.2 department_csv_schemas 相当）。
 *
 * <p>計画書 9.2 は SalesCsvParser 等を個別クラスで実装する想定だが、8.2 はヘッダー定義を
 * 差し替え可能にすることを求めている。MVPでは設定からスキーマを読み、部署ごとに
 * SchemaDrivenCsvParser のインスタンスを1つずつ生成することで、9.2 のインターフェースを
 * 保ったまま 8.2 の可変性を満たす。設定で表現できない部署固有の癖が出てきた時点で、
 * その部署だけ DepartmentCsvParser を手書き実装すればよい。
 */
@ConfigurationProperties(prefix = "expense.csv")
@Getter
@Setter
public class CsvSchemaProperties {

    private Map<Department, Schema> schemas = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Schema {
        private Headers headers = new Headers();
    }

    /** 各項目に対応しうるヘッダー名の別名リスト。 */
    @Getter
    @Setter
    public static class Headers {
        private List<String> usageDate = new ArrayList<>();
        private List<String> applicantName = new ArrayList<>();
        private List<String> category = new ArrayList<>();
        private List<String> amount = new ArrayList<>();
        private List<String> note = new ArrayList<>();
    }
}
