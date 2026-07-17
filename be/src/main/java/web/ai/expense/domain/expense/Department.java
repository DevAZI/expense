package web.ai.expense.domain.expense;

public enum Department {

    SALES("営業部"),
    DEVELOPMENT("開発部"),
    MARKETING("マーケティング部");

    private final String label;

    Department(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
