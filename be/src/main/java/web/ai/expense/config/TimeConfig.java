package web.ai.expense.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 現在日時の供給元。
 *
 * <p>DATE_FUTURE のようなルールが {@code LocalDate.now()} を直接呼ぶと、テストで
 * 「今日」を固定できず境界（今日は未来ではない / 明日は未来）を検証できない。
 * Bean にしておけばテストで差し替えられる。
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
