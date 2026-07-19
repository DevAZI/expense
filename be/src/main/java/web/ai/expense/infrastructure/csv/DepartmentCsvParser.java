package web.ai.expense.infrastructure.csv;

import web.ai.expense.domain.expense.Department;

import java.util.List;

/** 部署別CSVアダプター（計画書 9.2）。ヘッダー判定と原文取り出しだけを担当する。 */
public interface DepartmentCsvParser {

    /** このヘッダーを自分の部署のものとして扱えるか（自動判定用）。 */
    boolean supports(CsvHeader header);

    Department department();

    /**
     * @param forced 部署が明示選択されたときに true。ヘッダー名が別名に一致しなくても、
     *               正準列順（日付・申請者・費目・金額・備考）の位置で強制的にマッピングする。
     *               自動判定のときは false で、別名に一致しない必須列があれば取込失敗にする。
     */
    List<ParsedCsvRow> parse(CsvHeader header, List<CsvReader.Row> dataRows, boolean forced);
}
