package web.ai.expense.infrastructure.csv;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Optional;

/**
 * 文字コード検出（計画書 6）。UTF-8 BOM、UTF-8、CP932 を順に判定し、判定不能なら取込を中止する。
 *
 * <p><b>順序に意味がある</b>: CP932 のバイト列は厳密な UTF-8 デコードでほぼ必ず失敗するが、
 * UTF-8 のバイト列は CP932 として「文字化けしつつも成功」してしまう。先に CP932 を試すと
 * 営業部以外のファイルが黙って文字化けするため、必ず UTF-8 を先に試す。
 */
@Component
public class CharsetDetector {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final Charset CP932 = Charset.forName("Windows-31J");

    public record Detected(Charset charset, String label, byte[] content) {
    }

    /**
     * @return 判定できなければ empty。呼び出し側はファイル単位の取込失敗にする。
     */
    public Optional<Detected> detect(byte[] bytes) {
        if (hasUtf8Bom(bytes)) {
            byte[] withoutBom = new byte[bytes.length - UTF8_BOM.length];
            System.arraycopy(bytes, UTF8_BOM.length, withoutBom, 0, withoutBom.length);
            // BOMを落とさないと先頭ヘッダー名の頭に U+FEFF が残り、ヘッダー照合が静かに失敗する。
            return Optional.of(new Detected(java.nio.charset.StandardCharsets.UTF_8, "UTF-8-BOM", withoutBom));
        }

        if (decodesCleanly(bytes, java.nio.charset.StandardCharsets.UTF_8)) {
            return Optional.of(new Detected(java.nio.charset.StandardCharsets.UTF_8, "UTF-8", bytes));
        }

        if (decodesCleanly(bytes, CP932)) {
            return Optional.of(new Detected(CP932, "CP932", bytes));
        }

        return Optional.empty();
    }

    private boolean hasUtf8Bom(byte[] bytes) {
        if (bytes.length < UTF8_BOM.length) {
            return false;
        }
        for (int i = 0; i < UTF8_BOM.length; i++) {
            if (bytes[i] != UTF8_BOM[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean decodesCleanly(byte[] bytes, Charset charset) {
        var decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
