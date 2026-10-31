package br.com.celke.cursos.shared.cripto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Segredos guardados no banco (credenciais dos meios de pagamento, chave do segundo fator) cifrados com
 * AES-256-GCM. Cada gravação usa um IV aleatório de 12 bytes, guardado na frente do texto cifrado. O
 * dado associado amarra o blob ao dono (o gateway, o usuário): copiado para outro, não decifra.
 */
public class CifraSegredos {

    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final SecretKey chave;
    private final String variavel;

    /**
     * @param chaveBase64 32 bytes em Base64; em branco, a cifra fica indisponível
     * @param variavel variável de ambiente da chave, citada nas mensagens de erro
     */
    public CifraSegredos(String chaveBase64, String variavel) {
        this.variavel = variavel;
        this.chave = chaveBase64 == null || chaveBase64.isBlank()
                ? null
                : new SecretKeySpec(Base64.getDecoder().decode(chaveBase64.strip()), "AES");
    }

    public boolean disponivel() {
        return chave != null;
    }

    public byte[] cifrar(String texto, String dadoAssociado) {
        exigirChave();
        byte[] iv = new byte[TAMANHO_IV];
        ALEATORIO.nextBytes(iv);
        try {
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            cifra.updateAAD(dadoAssociado.getBytes(StandardCharsets.UTF_8));
            byte[] cifrado = cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + cifrado.length)
                    .put(iv)
                    .put(cifrado)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível cifrar os segredos.", e);
        }
    }

    /** @throws IllegalStateException com chave errada, dado associado diferente ou blob alterado */
    public String decifrar(byte[] blob, String dadoAssociado) {
        exigirChave();
        if (blob == null || blob.length <= TAMANHO_IV) {
            throw new IllegalStateException("Segredos gravados em formato inválido.");
        }
        try {
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, blob, 0, TAMANHO_IV));
            cifra.updateAAD(dadoAssociado.getBytes(StandardCharsets.UTF_8));
            byte[] texto = cifra.doFinal(blob, TAMANHO_IV, blob.length - TAMANHO_IV);
            return new String(texto, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível decifrar os segredos. A " + variavel + " mudou?", e);
        }
    }

    private void exigirChave() {
        if (chave == null) {
            throw new IllegalStateException(variavel + " não está definida.");
        }
    }
}
