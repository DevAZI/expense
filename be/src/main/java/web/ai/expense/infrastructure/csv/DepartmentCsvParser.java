package web.ai.expense.infrastructure.csv;

import web.ai.expense.domain.expense.Department;

import java.util.List;

/** 部署別CSVアダプター（計画書 9.2）。ヘッダー判定と原文取り出しだけを担当する。 */
public interface DepartmentCsvParser {

    /** このヘッダーを自分の部署のものとして扱えるか。 */
    boolean supports(CsvHeader header);

    Department department();

    List<ParsedCsvRow> parse(CsvHeader header, List<CsvReader.Row> dataRows);
}
