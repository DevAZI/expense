package web.ai.expense.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AmountNormalizerTest {

    @Test
    @DisplayName("全角数字を半角化して数値にする")
    void handlesFullWidthDigits() {
        assertThat(AmountNormalizer.normalize("１２８００")).contains(12800L);
    }

    @Test
    @DisplayName("桁区切りカンマを除去する")
    void stripsThousandSeparator() {
        assertThat(AmountNormalizer.normalize("4,980")).contains(4980L);
        assertThat(AmountNormalizer.normalize("1,200,000")).contains(1200000L);
    }

    @Test
    @DisplayName("負数は返金として受け入れる")
    void acceptsNegative() {
        assertThat(AmountNormalizer.normalize("-3000")).contains(-3000L);
        assertThat(AmountNormalizer.normalize("-1,500")).contains(-1500L);
    }

    @Test
    @DisplayName("通貨記号・円を除去する")
    void stripsCurrencyMarks() {
        assertThat(AmountNormalizer.normalize("¥4,980")).contains(4980L);
        assertThat(AmountNormalizer.normalize("4980円")).contains(4980L);
    }

    @Test
    @DisplayName("欠損は empty（REQUIRED_FIELD_MISSING の前提）")
    void treatsBlankAsMissing() {
        assertThat(AmountNormalizer.normalize(null)).isEmpty();
        assertThat(AmountNormalizer.normalize("")).isEmpty();
        assertThat(AmountNormalizer.normalize("  ")).isEmpty();
    }

    @Test
    @DisplayName("小数・非数値は解釈しない（円単位のみ扱う）")
    void rejectsNonInteger() {
        assertThat(AmountNormalizer.normalize("1234.5")).isEmpty();
        assertThat(AmountNormalizer.normalize("約5000")).isEmpty();
        assertThat(AmountNormalizer.normalize("N/A")).isEmpty();
    }

    @Test
    @DisplayName("0円は数値として通す（判断は検証ルール側）")
    void acceptsZero() {
        assertThat(AmountNormalizer.normalize("0")).contains(0L);
    }
}
