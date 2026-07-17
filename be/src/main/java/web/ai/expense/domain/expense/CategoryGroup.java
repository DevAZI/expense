package web.ai.expense.domain.expense;

/**
 * 費目の上位グループ。
 *
 * <p>勘定科目の粒度を保ったまま部署横断の集計を出すために挟んでいる。営業部の「交通費」と
 * 開発部の「Travel」は勘定科目としては別物として保持しつつ、グループ単位では並べて比較できる。
 */
public enum CategoryGroup {

    TRAVEL("旅費・交通"),
    ENTERTAINMENT("交際・会議"),
    SUPPLIES("消耗品・書籍"),
    IT("IT・通信"),
    MARKETING("広告・制作");

    private final String label;

    CategoryGroup(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
