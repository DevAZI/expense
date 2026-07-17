package web.ai.expense.infrastructure.csv;

import web.ai.expense.shared.TextNormalizer;

import java.util.List;
import java.util.Optional;

/** CSVの1行目。ヘッダー名は照合前に正規化する（BOM残り・全角・前後空白を吸収）。 */
public record CsvHeader(List<String> names) {

    public CsvHeader {
        names = names.stream().map(CsvHeader::normalize).toList();
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String normalized = TextNormalizer.nfkc(name)
                .replace("﻿", "")
                .replaceAll("\\s+", "")
                .toLowerCase();
        return normalized;
    }

    /** 別名リストのいずれかに一致する列の位置を返す。 */
    public Optional<Integer> indexOfAny(List<String> aliases) {
        for (String alias : aliases) {
            String target = normalize(alias);
            int index = names.indexOf(target);
            if (index >= 0) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    public int size() {
        return names.size();
    }
}
