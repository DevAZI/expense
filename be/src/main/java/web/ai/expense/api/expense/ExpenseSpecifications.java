package web.ai.expense.api.expense;

import org.springframework.data.jpa.domain.Specification;
import web.ai.expense.domain.expense.*;

import java.time.LocalDate;

/**
 * 一覧の絞り込み（計画書 9.3）。ネイティブSQLを使わず Specification に寄せることで、
 * H2からPostgreSQLへの移行時にこの層を書き換えずに済むようにしている。
 */
public final class ExpenseSpecifications {

    private ExpenseSpecifications() {
    }

    public static Specification<Expense> filter(Department department, ExpenseStatus status,
                                                RiskLevel riskLevel, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (department != null) {
                predicates.add(cb.equal(root.get("department"), department));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (riskLevel != null) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("usageDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("usageDate"), to));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
