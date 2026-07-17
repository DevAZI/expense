package web.ai.expense.application.review;

/** 未解決のRED警告がある明細を、例外承認の明示なしに承認しようとした場合（計画書 7）。 */
public class ReviewNotAllowedException extends RuntimeException {

    public ReviewNotAllowedException(String message) {
        super(message);
    }
}
