package web.ai.expense.shared;

/**
 * 費目のマッピング用キー生成（計画書 6）。前後空白を除去し、英日混在に備えて小文字化する。
 * 標準費目への変換自体は RuleContext のマッピング表が行う。
 */
public final class CategoryNormalizer {

    private CategoryNormalizer() {
    }

    public static String toMappingKey(String rawValue) {
        String value = TextNormalizer.blankToNull(TextNormalizer.nfkc(rawValue));
        if (value == null) {
            return null;
        }
        return value.replaceAll("\\s+", "").toLowerCase();
    }
}
