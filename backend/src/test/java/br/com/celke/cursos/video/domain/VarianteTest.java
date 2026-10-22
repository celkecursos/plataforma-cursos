package br.com.celke.cursos.video.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VarianteTest {

    @ParameterizedTest(name = "origem {0} linhas → {1}")
    @CsvSource({
        "2160, 720p 1080p",
        "1081, 720p 1080p",
        "1080, 720p 1080p",
        "1079, 720p",
        "720,  720p",
        "719,  718p",
        "480,  480p",
        "361,  360p",
        "2,    2p",
    })
    @DisplayName("nunca aumenta a resolução: só as alturas-alvo que cabem, ou a própria altura (par)")
    void variantesPorOrigem(int alturaOrigem, String esperadas) {
        assertThat(Variante.paraOrigem(alturaOrigem).stream().map(Variante::nome))
                .containsExactly(esperadas.split(" "));
    }

    @Test
    @DisplayName("tetos de taxa: 2800k em 720p e 5000k em 1080p, com buffer do dobro")
    void tetosPadrao() {
        assertThat(Variante.paraOrigem(1080)).containsExactly(new Variante(720, 2800), new Variante(1080, 5000));
        assertThat(new Variante(1080, 5000).bufsizeKbps()).isEqualTo(10_000);
    }

    @ParameterizedTest(name = "{0} linhas → {1} kbps")
    @CsvSource({"480, 1920", "360, 1440", "150, 600", "100, 600"})
    @DisplayName("abaixo de 720p, o teto é proporcional à altura, com mínimo de 600 kbps")
    void tetoProporcional(int altura, int kbps) {
        assertThat(Variante.paraOrigem(altura)).isEqualTo(List.of(new Variante(altura, kbps)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 0, -720})
    @DisplayName("altura de origem inválida é recusada")
    void alturaInvalida(int altura) {
        assertThatThrownBy(() -> Variante.paraOrigem(altura))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Altura de origem inválida: " + altura);
    }
}
