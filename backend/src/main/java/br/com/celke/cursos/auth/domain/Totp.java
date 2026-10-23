package br.com.celke.cursos.auth.domain;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Código de uso único por tempo (TOTP, RFC 6238) no formato dos aplicativos autenticadores: HMAC-SHA1,
 * passos de 30 segundos, 6 dígitos. Aceita um passo antes e um depois do atual (relógio do celular
 * adiantado ou atrasado).
 */
public final class Totp {

    public static final int DIGITOS = 6;
    public static final long PASSO_SEGUNDOS = 30;
    /** Passos aceitos antes e depois do atual. */
    static final int TOLERANCIA = 1;

    private static final int BYTES_SEGREDO = 20;
    private static final String ALFABETO_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Totp() {}

    /** Segredo novo de 160 bits, em Base32 (o formato que o usuário digita no aplicativo). */
    public static String gerarSegredo() {
        byte[] bytes = new byte[BYTES_SEGREDO];
        ALEATORIO.nextBytes(bytes);
        return base32(bytes);
    }

    /** Endereço {@code otpauth://} do QR code lido pelo aplicativo. */
    public static String uri(String emissor, String conta, String segredo) {
        String rotulo = codificar(emissor) + ":" + codificar(conta);
        return "otpauth://totp/" + rotulo + "?secret=" + segredo + "&issuer=" + codificar(emissor)
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PASSO_SEGUNDOS;
    }

    public static long passo(Instant instante) {
        return Math.floorDiv(instante.getEpochSecond(), PASSO_SEGUNDOS);
    }

    /** Código de um passo, com zeros à esquerda. */
    public static String codigo(String segredo, long passo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodificarBase32(segredo), "HmacSHA1"));
            byte[] hash =
                    mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(passo).array());
            int deslocamento = hash[hash.length - 1] & 0x0f;
            int binario = ((hash[deslocamento] & 0x7f) << 24)
                    | ((hash[deslocamento + 1] & 0xff) << 16)
                    | ((hash[deslocamento + 2] & 0xff) << 8)
                    | (hash[deslocamento + 3] & 0xff);
            return String.format("%0" + DIGITOS + "d", binario % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Passo do código informado, se ele vale agora e é posterior a {@code ultimoPassoUsado} (o mesmo
     * código não serve duas vezes); vazio se não vale. {@code ultimoPassoUsado} nulo aceita qualquer passo.
     */
    public static OptionalLong verificar(String segredo, String codigo, Instant agora, Long ultimoPassoUsado) {
        if (codigo == null) {
            return OptionalLong.empty();
        }
        String digitos = codigo.replaceAll("\\s", "");
        if (!digitos.matches("\\d{" + DIGITOS + "}")) {
            return OptionalLong.empty();
        }
        long atual = passo(agora);
        for (long passo = atual - TOLERANCIA; passo <= atual + TOLERANCIA; passo++) {
            boolean confere = MessageDigest.isEqual(
                    codigo(segredo, passo).getBytes(StandardCharsets.US_ASCII),
                    digitos.getBytes(StandardCharsets.US_ASCII));
            if (confere && (ultimoPassoUsado == null || passo > ultimoPassoUsado)) {
                return OptionalLong.of(passo);
            }
        }
        return OptionalLong.empty();
    }

    static String base32(byte[] bytes) {
        StringBuilder texto = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                texto.append(ALFABETO_BASE32.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            texto.append(ALFABETO_BASE32.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return texto.toString();
    }

    static byte[] decodificarBase32(String texto) {
        String limpo = texto.replaceAll("[\\s=-]", "").toUpperCase(Locale.ROOT);
        ByteBuffer saida = ByteBuffer.allocate(limpo.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : limpo.toCharArray()) {
            int valor = ALFABETO_BASE32.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Segredo fora do Base32.");
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                saida.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return saida.array();
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
