package br.com.celke.cursos.video.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FormatoVideoTest {

    private static byte[] hex(String valor) {
        return HexFormat.of().parseHex(valor);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "video/mp4,         MP4",
        "video/quicktime,   MOV",
        "video/x-matroska,  MKV",
        "video/webm,        WEBM",
    })
    @DisplayName("reconhece os tipos aceitos")
    void tiposAceitos(String contentType, FormatoVideo esperado) {
        assertThat(FormatoVideo.doContentType(contentType)).contains(esperado);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"video/avi", "image/png", "VIDEO/MP4", "''"})
    @DisplayName("recusa tipos fora da lista (inclusive com outra caixa)")
    void tiposRecusados(String contentType) {
        assertThat(FormatoVideo.doContentType(contentType)).isEmpty();
    }

    @Test
    @DisplayName("recusa tipo nulo")
    void tipoNulo() {
        assertThat(FormatoVideo.doContentType(null)).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
        "MP4,  000000206674797069736f6d, true",
        "MOV,  0000001466747970717420, true",
        "MP4,  1a45dfa39f4286810142f781, false",
        "MKV,  1a45dfa39f4286810142f781, true",
        "WEBM, 1a45dfa3, true",
        "WEBM, 000000206674797069736f6d, false",
        "MP4,  3c68746d6c3e3c7363726970, false",
        "MP4,  00000020667479, false",
        "MKV,  1a45df, false",
    })
    @DisplayName("confere a assinatura real do início do arquivo")
    void assinatura(FormatoVideo formato, String inicio, boolean esperado) {
        assertThat(formato.assinaturaConfere(hex(inicio))).isEqualTo(esperado);
    }

    @Test
    @DisplayName("texto com extensão de vídeo não passa como MP4")
    void textoComExtensaoFalsa() {
        byte[] html = "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

        assertThat(FormatoVideo.MP4.assinaturaConfere(html)).isFalse();
        assertThat(FormatoVideo.WEBM.assinaturaConfere(html)).isFalse();
    }
}
