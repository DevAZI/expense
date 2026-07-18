package web.ai.expense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI補助レイヤーの設定（計画書 11章 / Phase 4）。
 *
 * <p>Bedrock 自体の接続設定（リージョン・モデル・資格情報）は Spring AI の
 * {@code spring.ai.bedrock.*} が持つ。ここが持つのは「AIを業務にどう効かせるか」の
 * アプリ側パラメータだけ。AIは決定的ルールと分離した助言専用で、承認ゲート・
 * リスク判定には関与しない。
 */
@ConfigurationProperties(prefix = "expense.ai")
@Getter
@Setter
public class AiAdvisoryProperties {

    /**
     * AI補助の全体スイッチ。false なら Bedrock を一切呼ばず助言APIは空を返す。
     * 資格情報が無い環境（CI・テスト）で既存機能を壊さないための逃げ道。
     */
    private boolean enabled = true;

    /**
     * 1リクエストで AI に渡す明細の上限。トークン量と費用の上限。
     * 超える分は切り詰めて件数を返却情報に残す。
     */
    private int maxRowsPerRequest = 80;
}
