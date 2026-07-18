package web.ai.expense.api.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import web.ai.expense.application.ai.AiAdvisoryResult;
import web.ai.expense.application.ai.AiAdvisoryService;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.expense.ExpenseRepository;
import web.ai.expense.domain.expense.ExpenseStatus;

import java.util.List;
import java.util.UUID;

/**
 * AI補助（異常検知・誤字脱字チェック）の入口。
 *
 * <p><b>決定的検知の {@code /api/anomalies} とは別系統</b>。ここが返すのは AI の助言で、
 * DBには保存されず承認判定にも影響しない。経理が原本確認するための参考情報として使う。
 *
 * <p>同期実行で Bedrock を呼ぶため応答に数秒かかりうる。無効設定・資格情報なし・呼出失敗は
 * 例外にせず、{@code available=false} と理由メッセージを載せた 200 で返す（助言は業務を止めない）。
 */
@RestController
@RequestMapping("/api/ai/advisories")
@RequiredArgsConstructor
public class AiAdvisoryController {

    private final AiAdvisoryService advisoryService;
    private final ExpenseRepository expenseRepository;

    /**
     * 経費明細を AI で分析する。
     *
     * @param fileId 指定時はその取込ファイルの明細だけを対象にする。
     * @param status 指定時はそのステータスの明細を対象にする（既定: PENDING = 未承認だけ）。
     *               fileId と両方指定した場合は fileId を優先する。
     */
    @PostMapping
    @Transactional(readOnly = true)
    public AiAdvisoryResult analyze(@RequestParam(required = false) UUID fileId,
                                    @RequestParam(required = false) ExpenseStatus status) {
        List<Expense> targets = (fileId != null)
                ? expenseRepository.findBySourceFileId(fileId)
                : expenseRepository.findByStatus(status != null ? status : ExpenseStatus.PENDING);

        return advisoryService.analyze(targets);
    }
}
