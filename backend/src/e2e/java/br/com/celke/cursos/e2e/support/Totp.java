package br.com.celke.cursos.e2e.support;

import java.nio.ByteBuffer;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Código do aplicativo autenticador (TOTP, RFC 6238: HMAC-SHA1, 30 s, 6 dígitos), como o celular calcula. */
public final class Totp {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {}

    public static String codigoAtual(String segredoBase32) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodificar(segredoBase32), "HmacSHA1"));
            long passo = System.currentTimeMillis() / 1000 / 30;
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(passo).array());
            int i = hash[hash.length - 1] & 0x0f;
            int binario = ((hash[i] & 0x7f) << 24)
                    | ((hash[i + 1] & 0xff) << 16)
                    | ((hash[i + 2] & 0xff) << 8)
                    | (hash[i + 3] & 0xff);
            return String.format("%06d", binario % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] decodificar(String texto) {
        String limpo = texto.replaceAll("[\\s=]", "").toUpperCase(Locale.ROOT);
        ByteBuffer saida = ByteBuffer.allocate(limpo.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : limpo.toCharArray()) {
            buffer = (buffer << 5) | BASE32.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                saida.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return saida.array();
    }
}
