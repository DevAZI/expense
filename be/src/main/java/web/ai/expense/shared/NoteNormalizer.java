package web.ai.expense.shared;

/**
 * 備考の比較用キー生成（計画書 6）。原文は保持し、比較用に空白・記号を落とした値を別に作る。
 * 完全重複判定（EXACT_DUPLICATE）はこの値で行う。
 */
public final class NoteNormalizer {

    private NoteNormalizer() {
    }

    public static String toComparisonKey(String rawValue) {
        String value = TextNormalizer.nfkc(rawValue);
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
                .replaceAll("[\\s\\p{Punct}　]", "")
                .trim();
    }
}
