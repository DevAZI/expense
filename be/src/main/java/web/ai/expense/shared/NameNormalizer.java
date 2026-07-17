package web.ai.expense.shared;

/**
 * 氏名の正規化（計画書 6）。
 *
 * <p><b>表示名と照合キーを分けている理由</b>: 「田中 美咲」と「田中美咲」を同一人物として
 * 重複判定したいので、照合キーでは空白を落とす必要がある。しかし同じ処理を表示名に使うと、
 * 開発部の "Kim Jiho" が "KimJiho" になって別人の名前になってしまう（実データで発覚）。
 * 表示は空白1つに畳むだけに留め、空白除去は照合キーだけで行う。
 */
public final class NameNormalizer {

    private NameNormalizer() {
    }

    /**
     * 画面・CSV出力で使う表示名。全角空白を半角へ寄せ、連続空白を1つに畳む。
     * 「田中　美咲」→「田中 美咲」、「Kim  Jiho」→「Kim Jiho」。
     */
    public static String toDisplayName(String rawValue) {
        String value = TextNormalizer.nfkc(rawValue);
        if (value == null) {
            return null;
        }
        String collapsed = value.trim().replaceAll("\\s+", " ");
        return collapsed.isEmpty() ? null : collapsed;
    }

    /**
     * 重複判定・同一人物判定に使うキー。空白をすべて落として小文字化する。
     * 「田中 美咲」「田中　美咲」「田中美咲」→ すべて "田中美咲"。
     */
    public static String toComparisonKey(String rawValue) {
        String value = TextNormalizer.nfkc(rawValue);
        if (value == null) {
            return null;
        }
        String key = value.replaceAll("\\s+", "").toLowerCase();
        return key.isEmpty() ? null : key;
    }
}
