package web.ai.expense.application.ai;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import web.ai.expense.config.AiAdvisoryProperties;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.validation.ValidationIssue;
import web.ai.expense.domain.validation.ValidationIssueRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI補助レイヤー（計画書 11章 / Phase 4）。Bedrock Converse 経由で Claude を呼び、
 * <b>決定的ルールでは拾いきれない</b>異常の候補と、誤字脱字の候補を助言として返す。
 *
 * <p><b>設計の要</b>: ここは {@code ValidationEngine} と完全に分離している。
 * 出力は DB に保存されず、{@code ValidationIssue}・承認ゲート・リスク判定に一切影響しない。
 * CLAUDE.md の方針「AIは ValidationEngine に関与しない」を守るため、AIは決定的判定の
 * <i>あと</i>に、独立した参照情報としてだけ動く。
 *
 * <p><b>決定的検知結果を入力に同梱する</b>: 各明細に既存の RED/YELLOW を1行添えて渡す。
 * これによりモデルは機械ルールが見る観点（高額・完全重複・対象月外など）を再判定せず、
 * その確認観点の補足と、機械では拾いにくい観点だけに集中できる。
 *
 * <p><b>非ブロッキング</b>: 無効設定・{@code ChatModel} 不在（資格情報なし）・呼出例外の
 * いずれでも、既存の取込〜承認フローは一切止まらない。失敗は空の結果＋理由メッセージに畳む。
 */
@Service
@Slf4j
public class AiAdvisoryService {

    private static final String SYSTEM_PROMPT = """
            あなたは日本企業の経理担当者を補助するアシスタントです。
            入力された経費明細について、人間が領収書・申請内容・社内規程を確認するための
            「候補」だけを提示してください。

            あなたは承認・却下・不正認定・金額や日付の自動修正をしてはいけません。
            私的利用や不正行為を「事実」として断定してはいけません。ただし、入力の事実
            （費目・金額・備考など）から私的用途の可能性がうかがえる場合は、「疑い」として
            人間の確認を促すことはできます（あくまで確認の候補であって、認定ではありません）。
            根拠が十分でない場合は指摘せず、空配列を返してください。

            ## 分析対象

            - 各明細は先頭の `#index` で一意に識別されます。
            - 金額の単位は円です。
            - 明細に利用時刻がない場合、深夜利用・営業時間外利用を指摘してはいけません。
            - 明細に取引先名、領収書番号、証憑情報がない場合、それらを推測してはいけません。
            - 各明細の末尾に「既存検知:」がある場合、それは機械ルールが既に検出した観点です。
              必須項目欠損・不正日付・完全重複・対象月外・数値変換失敗・設定済み閾値超過は、
              AIが改めて判定しません。既存検知がある場合だけ、その確認観点を補足できます。

            ## 出力対象

            次の2種類だけを出力してください。

            ### 1. anomalies（機械的ルールだけでは判断しにくい、確認価値の高い異常候補）

            対象例:
            - 金額の桁誤りの可能性
            - しきい値・承認上限を回避するための分割申請の可能性
            - 費目と金額・備考・取引先の明らかな不整合
            - 業務目的や利用理由の確認が必要な支出
            - 会社業務ではなく私的用途（私的利用）の疑いがある支出。備考・費目・金額から
              業務目的が読み取れず、個人的な用途がうかがえる場合（例: 業務と無関係に見える
              品目、個人消費が疑われる飲食・物販など）。日時情報がある場合は休日・深夜の
              私的利用の疑いも含めてよい。
            - 日時情報が存在する場合だけ、休日・深夜などの確認が必要な支出

            出力条件:
            - 必ず具体的な明細番号(index)と、入力中に存在する事実だけを根拠にすること
            - 比較対象がある場合は、その明細番号を relatedIndexes に含めること
            - 「不正」「私的利用」「誤入力」を事実として断定しないこと。私的利用は必ず
              「疑い」「確認が必要」という確認の候補として書くこと
            - 私的利用の疑いは、備考が空・情報不足というだけでは出力せず、私的用途を
              うかがわせる具体的な記述や品目が入力にある場合にだけ出力すること
            - 単なる高額、単なる負数、単なる休日利用だけでは出力しないこと
            - 高額判定・日付妥当性・完全重複は機械ルールの責務であり、AI単独では出力しないこと
            - category は DIGIT_ERROR / SPLIT_TRANSACTION / CATEGORY_MISMATCH /
              BUSINESS_PURPOSE_CONFIRMATION / PRIVATE_USE_SUSPICION / TIME_PATTERN / OTHER のいずれか

            ### 2. typos（明らかな誤字、脱字、表記揺れの候補）

            出力条件:
            - original と suggestion は必須とする
            - 意味が変わる修正、推測による補完、文体改善は出力しないこと
            - 金額、日付、申請者、費目を推測で修正しないこと
            - 入力内の明確な一致候補がある場合だけ出力すること
            - 確信が持てない場合は出力しないこと
            - field は applicantName / category / note のいずれか

            該当する候補がない場合は、必ず空配列を返してください。
            事実に基づき、推測を断定として書かないこと。理由は日本語で簡潔に書いてください。
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ValidationIssueRepository issueRepository;
    private final AiAdvisoryProperties properties;
    private final String configuredModel;

    public AiAdvisoryService(ObjectProvider<ChatModel> chatModelProvider,
                             ValidationIssueRepository issueRepository,
                             AiAdvisoryProperties properties,
                             @Value("${spring.ai.bedrock.converse.chat.options.model:unknown}") String configuredModel) {
        this.chatModelProvider = chatModelProvider;
        this.issueRepository = issueRepository;
        this.properties = properties;
        this.configuredModel = configuredModel;
    }

    /**
     * 与えられた明細群を分析して助言を返す。呼出側は結果の available を見て扱う。
     * 例外は投げない（助言レイヤーは業務フローを止めない）。
     */
    public AiAdvisoryResult analyze(List<Expense> expenses) {
        if (!properties.isEnabled()) {
            return AiAdvisoryResult.disabled();
        }
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return AiAdvisoryResult.unavailable(
                    "AIモデルを利用できません（Bedrockの資格情報が未設定の可能性があります）。");
        }
        if (expenses == null || expenses.isEmpty()) {
            return AiAdvisoryResult.empty(configuredModel, 0, "対象の明細がありません。");
        }

        // トークン量・費用の上限。超える分は切り詰める（先頭から）。
        List<Expense> target = expenses.size() > properties.getMaxRowsPerRequest()
                ? expenses.subList(0, properties.getMaxRowsPerRequest())
                : expenses;

        // 決定的検知結果を明細に添えるため、対象明細分の issue をまとめて引く。
        Map<UUID, List<ValidationIssue>> issuesByExpense = issueRepository
                .findByExpenseIdIn(target.stream().map(Expense::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ValidationIssue::getExpenseId));

        // index -> Expense、Expense.id -> index。AIには UUID を触らせず整数 index だけを
        // 往復させ、突き合わせはサーバ側で行う（UUIDのハルシネーションを避けるため）。
        Map<Integer, Expense> byIndex = new HashMap<>();
        Map<UUID, Integer> indexOf = new HashMap<>();
        StringBuilder userText = new StringBuilder("以下の経費明細を分析してください。\n\n");
        int idx = 1;
        for (Expense e : target) {
            byIndex.put(idx, e);
            indexOf.put(e.getId(), idx);
            userText.append(formatRow(idx, e, issuesByExpense.getOrDefault(e.getId(), List.of()))).append('\n');
            idx++;
        }

        try {
            LlmAnalysis analysis = ChatClient.create(chatModel)
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userText.toString())
                    .call()
                    .entity(LlmAnalysis.class);

            if (analysis == null) {
                return AiAdvisoryResult.empty(configuredModel, target.size(),
                        "AIから有効な応答が得られませんでした。");
            }
            return mapResult(analysis, byIndex, target.size());
        } catch (Exception ex) {
            // 助言は業務を止めない。失敗は理由付きで畳んで返す。
            log.warn("AI助言の呼び出しに失敗しました: {}", ex.getMessage());
            return AiAdvisoryResult.unavailable(
                    "AIの呼び出しに失敗しました: " + ex.getClass().getSimpleName());
        }
    }

    private String formatRow(int index, Expense e, List<ValidationIssue> issues) {
        String base = "#%d | 部署:%s | 日付:%s | 申請者:%s | 費目:%s | 金額:%s円 | 備考:%s".formatted(
                index,
                e.getDepartment() == null ? "" : e.getDepartment().label(),
                e.getUsageDate() == null ? "(不明)" : e.getUsageDate(),
                nullToEmpty(e.getApplicantName()),
                e.getCategory() == null ? "(未分類)" : e.getCategory().label(),
                e.getAmountYen() == null ? "(不明)" : e.getAmountYen(),
                nullToEmpty(e.getNote()));
        if (issues.isEmpty()) {
            return base;
        }
        // 決定的ルールが既に検出した観点。モデルはこれを再判定せず、確認観点の補足だけに使う。
        String detected = issues.stream()
                .map(i -> i.getRuleCode().name() + "(" + i.getSeverity() + ")")
                .distinct()
                .collect(Collectors.joining(", "));
        return base + " | 既存検知:" + detected;
    }

    private AiAdvisoryResult mapResult(LlmAnalysis analysis, Map<Integer, Expense> byIndex, int analyzedCount) {
        List<AiAdvisoryResult.AiAnomaly> anomalies = new ArrayList<>();
        for (LlmAnomaly a : safe(analysis.anomalies())) {
            Expense e = byIndex.get(a.index());
            if (e == null) {
                continue; // 範囲外 index はハルシネーションとみなし捨てる
            }
            anomalies.add(new AiAdvisoryResult.AiAnomaly(
                    e.getId(), e.getSourceRowNumber(),
                    e.getDepartment() == null ? null : e.getDepartment().label(),
                    e.getApplicantName(),
                    e.getCategory() == null ? null : e.getCategory().label(),
                    e.getAmountYen(),
                    normalizeSeverity(a.severity()),
                    normalizeType(a.category()),
                    a.reason(),
                    resolveRelated(a.relatedIndexes(), byIndex)));
        }

        List<AiAdvisoryResult.AiTypo> typos = new ArrayList<>();
        for (LlmTypo t : safe(analysis.typos())) {
            Expense e = byIndex.get(t.index());
            if (e == null) {
                continue;
            }
            typos.add(new AiAdvisoryResult.AiTypo(
                    e.getId(), e.getSourceRowNumber(),
                    t.field(), t.original(), t.suggestion(), t.reason()));
        }

        String message = "AI補助を実行しました（異常候補 %d件 / 誤字候補 %d件）。これは参考情報で、承認判定には影響しません。"
                .formatted(anomalies.size(), typos.size());
        return new AiAdvisoryResult(true, true, configuredModel, message, analyzedCount, anomalies, typos);
    }

    /** モデルが返した関連 index を、範囲内のものだけ sourceRowNumber に解決する。 */
    private List<Integer> resolveRelated(List<Integer> relatedIndexes, Map<Integer, Expense> byIndex) {
        if (relatedIndexes == null || relatedIndexes.isEmpty()) {
            return List.of();
        }
        List<Integer> rows = new ArrayList<>();
        for (Integer ri : relatedIndexes) {
            Expense e = ri == null ? null : byIndex.get(ri);
            if (e != null) {
                rows.add(e.getSourceRowNumber());
            }
        }
        return rows;
    }

    private static String normalizeSeverity(String s) {
        if (s == null) {
            return "LOW";
        }
        return switch (s.trim().toUpperCase()) {
            case "HIGH" -> "HIGH";
            case "MEDIUM" -> "MEDIUM";
            case "LOW" -> "LOW";
            default -> "LOW";
        };
    }

    private static String normalizeType(String s) {
        if (s == null) {
            return "OTHER";
        }
        return switch (s.trim().toUpperCase()) {
            case "DIGIT_ERROR", "SPLIT_TRANSACTION", "CATEGORY_MISMATCH",
                 "BUSINESS_PURPOSE_CONFIRMATION", "PRIVATE_USE_SUSPICION",
                 "TIME_PATTERN", "OTHER" -> s.trim().toUpperCase();
            default -> "OTHER";
        };
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // --- AI構造化出力のバインド用DTO（この層の内部表現） -----------------------
    // ChatClient の .entity(...) が JSON スキーマ指示を組み立てるので、フィールドの
    // 説明はモデルへの指示としても効く。UUIDは載せない（index だけ往復させる）。
    // JSON整形の指示は .entity(...) が自動付与するため、プロンプト本文には書かない。

    @JsonClassDescription("経費明細の分析結果。異常候補と誤字候補の配列を持つ。")
    record LlmAnalysis(
            @JsonPropertyDescription("機械ルールでは拾いにくい異常の候補") List<LlmAnomaly> anomalies,
            @JsonPropertyDescription("誤字脱字・表記揺れの候補") List<LlmTypo> typos) {
    }

    record LlmAnomaly(
            @JsonPropertyDescription("対象明細の先頭に付いた#番号（整数）") int index,
            @JsonPropertyDescription("重要度。HIGH / MEDIUM / LOW のいずれか") String severity,
            @JsonPropertyDescription("観点の種別。DIGIT_ERROR / SPLIT_TRANSACTION / "
                    + "CATEGORY_MISMATCH / BUSINESS_PURPOSE_CONFIRMATION / PRIVATE_USE_SUSPICION / "
                    + "TIME_PATTERN / OTHER") String category,
            @JsonPropertyDescription("その明細を確認すべき理由。入力の事実に基づき日本語で簡潔に") String reason,
            @JsonPropertyDescription("分割申請など比較対象がある場合の、関連明細の#番号の配列") List<Integer> relatedIndexes) {
    }

    record LlmTypo(
            @JsonPropertyDescription("対象明細の先頭に付いた#番号（整数）") int index,
            @JsonPropertyDescription("対象フィールド。applicantName / category / note のいずれか") String field,
            @JsonPropertyDescription("原文の該当箇所") String original,
            @JsonPropertyDescription("修正案") String suggestion,
            @JsonPropertyDescription("修正を勧める根拠。日本語で簡潔に") String reason) {
    }
}
