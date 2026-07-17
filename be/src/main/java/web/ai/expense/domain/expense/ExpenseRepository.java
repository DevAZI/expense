package web.ai.expense.domain.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

/**
 * <p><b>Spring Data を domain に置いている理由</b>: 計画書 9.1 は infrastructure/persistence に
 * 実装を置く port/adapter 構成を示すが、MVPでは変換コードの割に得るものが小さいので
 * JpaRepository を直接継承している。計画書 4.2 が求める PostgreSQL への移行可能性は、
 * ここでは「H2固有SQLとネイティブクエリを使わない・検索は Specification に寄せる」という
 * 制約側で担保する。
 */
public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    List<Expense> findBySourceFileId(UUID sourceFileId);

    List<Expense> findByStatus(ExpenseStatus status);
}
