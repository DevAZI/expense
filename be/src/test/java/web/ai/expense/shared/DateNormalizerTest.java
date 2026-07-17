package web.ai.expense.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DateNormalizerTest {

    private static final YearMonth TARGET = YearMonth.of(2026, 6);

    @Test
    @DisplayName("和暦を西暦へ変換する（令和8年 = 2026年）")
    void convertsJapaneseEra() {
        assertThat(DateNormalizer.normalize("令和8年6月2日", TARGET))
                .contains(LocalDate.of(2026, 6, 2));
    }

    @Test
    @DisplayName("和暦の元年表記を1年として扱う")
    void convertsGannen() {
        assertThat(DateNormalizer.normalize("令和元年5月1日", TARGET))
                .contains(LocalDate.of(2019, 5, 1));
    }

    @Test
    @DisplayName("西暦の年月日形式を変換する")
    void convertsWesternFormats() {
        assertThat(DateNormalizer.normalize("2026年6月5日", TARGET)).contains(LocalDate.of(2026, 6, 5));
        assertThat(DateNormalizer.normalize("2026/6/5", TARGET)).contains(LocalDate.of(2026, 6, 5));
        assertThat(DateNormalizer.normalize("2026-06-05", TARGET)).contains(LocalDate.of(2026, 6, 5));
    }

    @Test
    @DisplayName("年省略の月日形式は対象月の年を補う")
    void fillsYearFromTargetMonth() {
        assertThat(DateNormalizer.normalize("6/2", TARGET)).contains(LocalDate.of(2026, 6, 2));
        assertThat(DateNormalizer.normalize("6月2日", TARGET)).contains(LocalDate.of(2026, 6, 2));
        assertThat(DateNormalizer.normalize("06月13日", TARGET)).contains(LocalDate.of(2026, 6, 13));
    }

    @Test
    @DisplayName("年省略で年をまたぐ場合、対象月にいちばん近い年を選ぶ")
    void picksNearestYearAcrossYearBoundary() {
        // 対象月が1月のときの「12/28」は前年12月の月またぎ精算。翌年12月ではない
        assertThat(DateNormalizer.normalize("12/28", YearMonth.of(2027, 1)))
                .contains(LocalDate.of(2026, 12, 28));

        // 対象月が12月のときの「1/5」は翌年1月
        assertThat(DateNormalizer.normalize("1/5", YearMonth.of(2026, 12)))
                .contains(LocalDate.of(2027, 1, 5));

        // 年またぎでなければ対象月の年のまま
        assertThat(DateNormalizer.normalize("12/28", YearMonth.of(2026, 12)))
                .contains(LocalDate.of(2026, 12, 28));
        assertThat(DateNormalizer.normalize("1/5", YearMonth.of(2027, 1)))
                .contains(LocalDate.of(2027, 1, 5));
    }

    @Test
    @DisplayName("年省略の2/29は、前後1年に閏年があってもその年を勝手に選ばない")
    void doesNotSilentlyJumpToLeapYear() {
        // 2026年2月は閏年ではない。近くの2024/2028も候補年の範囲外なので解釈失敗
        assertThat(DateNormalizer.normalize("2/29", YearMonth.of(2026, 2))).isEmpty();

        // 対象月が2024年2月なら成立する
        assertThat(DateNormalizer.normalize("2/29", YearMonth.of(2024, 2)))
                .contains(LocalDate.of(2024, 2, 29));
    }

    @Test
    @DisplayName("存在しない日付は丸めずに解釈失敗にする（INVALID_DATE の前提）")
    void rejectsNonExistentDate() {
        assertThat(DateNormalizer.normalize("2026/6/31", TARGET)).isEmpty();
        assertThat(DateNormalizer.normalize("6/31", TARGET)).isEmpty();
        assertThat(DateNormalizer.normalize("2026/2/30", TARGET)).isEmpty();
    }

    @Test
    @DisplayName("全角数字を含む日付を変換する")
    void handlesFullWidthDigits() {
        assertThat(DateNormalizer.normalize("２０２６/６/５", TARGET)).contains(LocalDate.of(2026, 6, 5));
    }

    @Test
    @DisplayName("空・非日付は解釈失敗")
    void rejectsBlankAndGarbage() {
        assertThat(DateNormalizer.normalize(null, TARGET)).isEmpty();
        assertThat(DateNormalizer.normalize("", TARGET)).isEmpty();
        assertThat(DateNormalizer.normalize("   ", TARGET)).isEmpty();
        assertThat(DateNormalizer.normalize("不明", TARGET)).isEmpty();
    }

    @Test
    @DisplayName("うるう年を正しく扱う")
    void handlesLeapYear() {
        Optional<LocalDate> leap = DateNormalizer.normalize("2024/2/29", YearMonth.of(2024, 2));
        assertThat(leap).contains(LocalDate.of(2024, 2, 29));
        assertThat(DateNormalizer.normalize("2026/2/29", TARGET)).isEmpty();
    }
}
