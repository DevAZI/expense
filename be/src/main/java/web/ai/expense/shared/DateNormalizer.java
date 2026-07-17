package web.ai.expense.shared;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.chrono.JapaneseChronology;
import java.time.chrono.JapaneseDate;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 日付の正規化（計画書 6）。和暦・西暦・月日形式を LocalDate へ寄せる。
 *
 * <p>すべての書式で {@link ResolverStyle#STRICT} を使う。これが 2026/6/31 を
 * 「6/30 に丸める」のではなく解釈失敗にするための肝で、INVALID_DATE（RED）の前提になる。
 */
public final class DateNormalizer {

    /** 「6/2」「6月2日」のように年が無い形式。年は取込対象月から補う（計画書 6）。 */
    private static final Pattern MONTH_DAY = Pattern.compile("^(\\d{1,2})[/\\-月](\\d{1,2})日?$");

    private static final List<DateTimeFormatter> WESTERN_FORMATS = List.of(
            strict("uuuu-MM-dd"),
            strict("uuuu/M/d"),
            strict("uuuu-M-d"),
            strict("uuuu.M.d"),
            strict("uuuu年M月d日")
    );

    /** 和暦。JapaneseChronology に解決させるので、令和/平成の改元境界も含めて正しく扱える。 */
    private static final DateTimeFormatter JAPANESE_ERA = new DateTimeFormatterBuilder()
            .appendPattern("GGGGy年M月d日")
            .toFormatter(Locale.JAPAN)
            .withChronology(JapaneseChronology.INSTANCE)
            .withResolverStyle(ResolverStyle.STRICT);

    private DateNormalizer() {
    }

    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.JAPAN).withResolverStyle(ResolverStyle.STRICT);
    }

    /**
     * @param rawValue        CSV上の原文
     * @param targetYearMonth 年省略時に補う対象月
     * @return 解釈できなければ empty（呼び出し側が INVALID_DATE を立てる）
     */
    public static Optional<LocalDate> normalize(String rawValue, YearMonth targetYearMonth) {
        String value = TextNormalizer.blankToNull(TextNormalizer.nfkc(rawValue));
        if (value == null) {
            return Optional.empty();
        }
        value = value.replace(" ", "");

        Optional<LocalDate> japanese = tryJapaneseEra(value);
        if (japanese.isPresent()) {
            return japanese;
        }

        for (DateTimeFormatter formatter : WESTERN_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(value, formatter));
            } catch (Exception ignored) {
                // 次の書式を試す
            }
        }

        return tryMonthDay(value, targetYearMonth);
    }

    private static Optional<LocalDate> tryJapaneseEra(String value) {
        if (!value.startsWith("令和") && !value.startsWith("平成") && !value.startsWith("昭和")) {
            return Optional.empty();
        }
        // 「令和1年」ではなく「令和元年」と書かれる場合がある
        String candidate = value.replace("元年", "1年");
        try {
            JapaneseDate japaneseDate = JapaneseDate.from(JAPANESE_ERA.parse(candidate));
            return Optional.of(LocalDate.from(japaneseDate));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> tryMonthDay(String value, YearMonth targetYearMonth) {
        var matcher = MONTH_DAY.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int month = Integer.parseInt(matcher.group(1));
        int day = Integer.parseInt(matcher.group(2));

        // 対象月にいちばん近い年を選ぶ。単純に対象月の年を当てると、対象月が2027年1月の
        // ときの「12/28」（前年12月の月またぎ精算）が2027-12-28になり11ヶ月ずれる。
        // 候補年の前後1年から最も近いものを採る。
        LocalDate best = null;
        long bestDistanceMonths = Long.MAX_VALUE;

        for (int year = targetYearMonth.getYear() - 1; year <= targetYearMonth.getYear() + 1; year++) {
            LocalDate candidate;
            try {
                candidate = LocalDate.of(year, month, day);
            } catch (DateTimeException e) {
                continue; // 2/29 が閏年でない、など
            }
            long distance = Math.abs(ChronoUnit.MONTHS.between(YearMonth.from(candidate), targetYearMonth));
            if (distance < bestDistanceMonths) {
                bestDistanceMonths = distance;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }
}
