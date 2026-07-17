package web.ai.expense.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameNormalizerTest {

    @Test
    @DisplayName("照合キーは氏名の表記揺れを同一に寄せる")
    void comparisonKeyCollapsesSpacing() {
        String withSpace = NameNormalizer.toComparisonKey("田中 美咲");
        String withFullWidthSpace = NameNormalizer.toComparisonKey("田中　美咲");
        String withoutSpace = NameNormalizer.toComparisonKey("田中美咲");

        assertThat(withSpace).isEqualTo(withoutSpace);
        assertThat(withFullWidthSpace).isEqualTo(withoutSpace);
        assertThat(withoutSpace).isEqualTo("田中美咲");
    }

    @Test
    @DisplayName("表示名は英語名の空白を保つ（KimJiho にならない）")
    void displayNamePreservesSpaceInLatinNames() {
        assertThat(NameNormalizer.toDisplayName("Kim Jiho")).isEqualTo("Kim Jiho");
        assertThat(NameNormalizer.toDisplayName("Watanabe  Sho")).isEqualTo("Watanabe Sho");
    }

    @Test
    @DisplayName("表示名は全角空白を半角に寄せ、連続空白を畳む")
    void displayNameNormalizesSpacing() {
        assertThat(NameNormalizer.toDisplayName("田中　美咲")).isEqualTo("田中 美咲");
        assertThat(NameNormalizer.toDisplayName("佐藤 健一")).isEqualTo("佐藤 健一");
    }

    @Test
    @DisplayName("英語名でも照合キーは空白除去・小文字化で突き合わせる")
    void comparisonKeyForLatinNames() {
        assertThat(NameNormalizer.toComparisonKey("Kim Jiho")).isEqualTo("kimjiho");
        assertThat(NameNormalizer.toComparisonKey("KIM JIHO")).isEqualTo("kimjiho");
    }

    @Test
    @DisplayName("空は null に寄せる")
    void blankBecomesNull() {
        assertThat(NameNormalizer.toDisplayName(null)).isNull();
        assertThat(NameNormalizer.toDisplayName("   ")).isNull();
        assertThat(NameNormalizer.toComparisonKey("   ")).isNull();
    }
}
