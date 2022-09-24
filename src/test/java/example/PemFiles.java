package example;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.util.Base64;
import java.util.List;

/*
 * https://www.rfc-editor.org/rfc/rfc7468
 * https://www.rfc-editor.org/rfc/rfc1421
 */
public class PemFiles {

    private static final String LS = "\r\n";
    private static final Charset CHARSET = StandardCharsets.US_ASCII;

    public static byte[] read(final Path path) throws IOException, InvalidKeyException {
        final List<String> lines = Files.readAllLines(path, CHARSET);
        final int size = lines.size();
        if (size < 3) {
            throw new InvalidKeyException("size: " + size);
        }
        final String preeb = lines.get(0);
        final String posteb = lines.get(size - 1);
        if (!preeb.startsWith("-----BEGIN ") || !preeb.endsWith("-----")
                || !posteb.startsWith("-----END ") || !posteb.endsWith("-----")) {
            throw new InvalidKeyException("invalid header or footer");
        }

        final String base64text = String.join(LS, lines.subList(1, size - 1));
        return Base64.getMimeDecoder().decode(base64text);
    }

    public static Path write(final Path path, final byte[] bytes, final String label) throws IOException {
        // PEMの行の長さは64文字
        final Base64.Encoder encoder = Base64.getMimeEncoder(64, LS.getBytes(CHARSET));
        final String base64text = encoder.encodeToString(bytes);
        // Pre-Encapsulation Boundary (Pre-EB)
        final String preeb = "-----BEGIN " + label + "-----";
        // Post-Encapsulation Boundary (Post-EB)
        final String posteb = "-----END " + label + "-----";

        final String text = preeb + LS + base64text + LS + posteb + LS;
        return Files.write(path, text.getBytes(CHARSET));
    }

}
