package web.ai.expense.application.imports;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.ai.expense.domain.expense.Expense;
import web.ai.expense.domain.expense.ExpenseRepository;
import web.ai.expense.domain.imports.ImportFileRepository;
import web.ai.expense.domain.imports.RawExpenseRowRepository;
import web.ai.expense.domain.validation.ValidationIssueRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 取込データの削除。翌月分を入れる前に前月分を空にするために使う。
 *
 * <p><b>承認結果も一緒に消える</b>: MVPではレビュー結果を expenses に直接持たせており
 * （review_decisions テーブルを作っていない）、明細を消せば「誰がいつ何を承認したか」も
 * 消える。計画書 16 が求めるデータ保持・監査とは相容れないので、運用に載せる前に
 * 監査ログの永続化が要る。ASSUMPTIONS.md 記載の仮定。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResetDataUseCase {

    private final ImportFileRepository importFileRepository;
    private final RawExpenseRowRepository rawRowRepository;
    private final ExpenseRepository expenseRepository;
    private final ValidationIssueRepository issueRepository;

    /** 全データを削除する。 */
    @Transactional
    public ResetResult resetAll() {
        ResetResult counts = new ResetResult(
                importFileRepository.count(),
                rawRowRepository.count(),
                expenseRepository.count(),
                issueRepository.count());

        // 派生データから先に消す。FK制約は張っていないが、途中で失敗したときに
        // 明細だけ残って警告が消える、という中途半端な状態を避けるため順序を決めておく。
        issueRepository.deleteAllInBatch();
        expenseRepository.deleteAllInBatch();
        rawRowRepository.deleteAllInBatch();
        importFileRepository.deleteAllInBatch();

        log.warn("全データを初期化しました: {}", counts);
        return counts;
    }

    /**
     * 1ファイル分だけ削除する。間違ったファイルを取り込んだときに、
     * 他のファイルのレビュー結果まで巻き込まずに取り消せるようにするため。
     */
    @Transactional
    public ResetResult resetImportFile(UUID importFileId) {
        if (!importFileRepository.existsById(importFileId)) {
            throw new NoSuchElementException("取込ファイルが見つかりません: " + importFileId);
        }

        List<Expense> expenses = expenseRepository.findBySourceFileId(importFileId);
        List<UUID> expenseIds = expenses.stream().map(Expense::getId).toList();

        long issueCount = 0;
        if (!expenseIds.isEmpty()) {
            List<web.ai.expense.domain.validation.ValidationIssue> issues =
                    issueRepository.findByExpenseIdIn(expenseIds);
            issueCount = issues.size();
            issueRepository.deleteAllInBatch(issues);
        }

        long rawCount = rawRowRepository.findByImportFileId(importFileId).size();

        expenseRepository.deleteAllInBatch(expenses);
        rawRowRepository.deleteByImportFileId(importFileId);
        importFileRepository.deleteById(importFileId);

        ResetResult counts = new ResetResult(1, rawCount, expenses.size(), issueCount);
        log.warn("取込ファイルを削除しました id={} {}", importFileId, counts);
        return counts;
    }
}
