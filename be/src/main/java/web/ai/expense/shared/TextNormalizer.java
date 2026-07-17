package web.ai.expense.shared;

import java.text.Normalizer;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** NFKC 正規化（全角英数字・全角記号を半角へ寄せる）。null 安全。 */
    public static String nfkc(String value) {
        if (value == null) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC);
    }

    /** null と空白のみを null に寄せる。 */
    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
