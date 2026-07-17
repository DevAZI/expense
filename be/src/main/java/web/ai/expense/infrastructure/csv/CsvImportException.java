package web.ai.expense.infrastructure.csv;

/** ファイル単位で取込を中止すべき事象（計画書 5-3）。行単位の警告には使わない。 */
public class CsvImportException extends RuntimeException {

    public CsvImportException(String message) {
        super(message);
    }

    public CsvImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
