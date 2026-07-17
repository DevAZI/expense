package web.ai.expense.domain.expense;

/**
 * 標準費目。実データ3部署に出現する費目を1:1で表現する。
 *
 * <p><b>粗く寄せない理由</b>: 営業部のヘッダーは「勘定科目」で、交通費と旅費交通費、
 * 会議費と交際費を別物として使い分けている（6/24 会議費3,600 社内打合せ弁当 と
 * 6/12 交際費480,000 会食10名）。計画書 8.4 は交通費・旅費交通費・Travel を
 * TRANSPORTATION へ統合するよう書いているが、それをやると営業部の勘定科目の
 * 使い分けが集計から消える。部署横断で見たいときは {@link #group()} で丸める。
 *
 * <p>3部署は分類体系そのものが違う（営業部=勘定科目、マーケ=費目、開発部=英語の費目）。
 * ここは「観測された値をすべて表現できる集合」であって、正式な勘定科目マスタではない。
 */
public enum ExpenseCategory {

    // 営業部・マーケティング部（日本語の勘定科目・費目）
    TRANSPORTATION("交通費", CategoryGroup.TRAVEL),
    TRAVEL_EXPENSE("旅費交通費", CategoryGroup.TRAVEL),
    ACCOMMODATION("宿泊費", CategoryGroup.TRAVEL),
    ENTERTAINMENT("交際費", CategoryGroup.ENTERTAINMENT),
    MEETING("会議費", CategoryGroup.ENTERTAINMENT),
    SUPPLIES("消耗品費", CategoryGroup.SUPPLIES),
    COMMUNICATION("通信費", CategoryGroup.IT),
    ADVERTISING("広告費", CategoryGroup.MARKETING),
    PRODUCTION("制作費", CategoryGroup.MARKETING),

    // 開発部（英語の費目。勘定科目ではなく実費の種類で書かれている）
    SOFTWARE("ソフトウェア", CategoryGroup.IT),
    HARDWARE("ハードウェア", CategoryGroup.IT),
    BOOKS("書籍", CategoryGroup.SUPPLIES),
    MEALS("飲食費", CategoryGroup.ENTERTAINMENT);

    private final String label;
    private final CategoryGroup group;

    ExpenseCategory(String label, CategoryGroup group) {
        this.label = label;
        this.group = group;
    }

    /** 画面・CSV出力で使う日本語表記。 */
    public String label() {
        return label;
    }

    /** 部署横断で比較するための上位グループ。 */
    public CategoryGroup group() {
        return group;
    }
}
