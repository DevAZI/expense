package web.ai.expense.shared;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 金額の正規化（計画書 6）。全角数字を半角化し、桁区切りを除去して long にする。
 *
 * <p>円単位の long で扱い、浮動小数点を使わない（計画書 4.1）。
 * 負数は正当な返金でありうるため弾かず、NEGATIVE_AMOUNT（YELLOW）として人に見せる（計画書 8.5 末尾）。
 */
public final class AmountNormalizer {

    private static final Pattern SIGNED_INTEGER = Pattern.compile("^[+-]?\\d+$");

    private AmountNormalizer() {
    }

    /**
     * @return 数値として厳密に解釈できなければ empty（欠損・非数値のどちらも）
     */
    public static Optional<Long> normalize(String rawValue) {
        String value = TextNormalizer.blankToNull(TextNormalizer.nfkc(rawValue));
        if (value == null) {
            return Optional.empty();
        }

        // 桁区切り・通貨記号・空白を落とす。全角カンマは NFKC で半角化済み。
        String cleaned = value
                .replace(",", "")
                .replace("￥", "")
                .replace("¥", "")
                .replace("円", "")
                .replace(" ", "")
                .trim();

        if (cleaned.isEmpty() || !SIGNED_INTEGER.matcher(cleaned).matches()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Long.parseLong(cleaned));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
