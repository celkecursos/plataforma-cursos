package br.com.celke.cursos.video.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.video.infra.AnaliseVideo.Faixa;
import br.com.celke.cursos.video.infra.AnaliseVideo.Formato;
import br.com.celke.cursos.video.infra.AnaliseVideo.SaidaFfprobe;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class AnaliseVideoTest {

    private static final Faixa VIDEO = new Faixa("video", 1920, 1080);
    private static final Faixa AUDIO = new Faixa("audio", null, null);

    @Test
    @DisplayName("lê a saída real do ffprobe: dimensões da faixa de vídeo, duração e áudio")
    void saidaDoFfprobe() {
        String json = """
                {"streams":[{"index":0,"codec_type":"video","width":1280,"height":720,"r_frame_rate":"24/1"},
                            {"index":1,"codec_type":"audio","sample_rate":"48000"}],
                 "format":{"filename":"original","duration":"3.000000","size":"43649"}}
                """;
        SaidaFfprobe saida = JsonMapper.builder().build().readValue(json, SaidaFfprobe.class);

        assertThat(AnaliseVideo.de(saida)).isEqualTo(new AnaliseVideo(3.0, 1280, 720, true));
    }

    @Test
    @DisplayName("sem faixa de áudio")
    void semAudio() {
        assertThat(AnaliseVideo.de(new SaidaFfprobe(List.of(VIDEO), new Formato("10.5"))))
                .isEqualTo(new AnaliseVideo(10.5, 1920, 1080, false));
    }

    @Test
    @DisplayName("usa a primeira faixa de vídeo com dimensões (capa embutida sem dimensões é ignorada)")
    void primeiraFaixaValida() {
        var saida = new SaidaFfprobe(
                List.of(AUDIO, new Faixa("video", null, null), new Faixa("video", 640, 360), VIDEO), new Formato("1"));

        assertThat(AnaliseVideo.de(saida).altura()).isEqualTo(360);
    }

    @ParameterizedTest(name = "{0} s → {1}")
    @CsvSource({"2.4, 2", "2.5, 3", "3599.6, 3600"})
    @DisplayName("duração em segundos inteiros, arredondada")
    void duracaoSegundos(double duracao, int esperado) {
        assertThat(new AnaliseVideo(duracao, 1, 1, false).duracaoSegundos()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("arquivo sem faixa de vídeo (só áudio, vazio ou sem resposta) é erro permanente")
    void semVideo() {
        for (SaidaFfprobe saida : List.of(
                new SaidaFfprobe(List.of(AUDIO), new Formato("3")),
                new SaidaFfprobe(List.of(new Faixa("video", 0, 720)), new Formato("3")),
                new SaidaFfprobe(null, new Formato("3")))) {
            assertThatThrownBy(() -> AnaliseVideo.de(saida))
                    .isInstanceOf(ErroPermanenteException.class)
                    .hasMessage("O arquivo não tem uma faixa de vídeo.");
        }
        assertThatThrownBy(() -> AnaliseVideo.de(null)).isInstanceOf(ErroPermanenteException.class);
    }

    @ParameterizedTest(name = "duração \"{0}\"")
    @NullSource
    @ValueSource(strings = {"N/A", "0", "-1", "NaN", "Infinity"})
    @DisplayName("duração ausente, zero ou inválida é erro permanente")
    void duracaoInvalida(String duracao) {
        var saida = new SaidaFfprobe(List.of(VIDEO), new Formato(duracao));

        assertThatThrownBy(() -> AnaliseVideo.de(saida))
                .isInstanceOf(ErroPermanenteException.class)
                .hasMessage("Não foi possível ler a duração do vídeo.");
    }

    @Test
    @DisplayName("sem o bloco de formato é erro permanente")
    void semFormato() {
        assertThatThrownBy(() -> AnaliseVideo.de(new SaidaFfprobe(List.of(VIDEO), null)))
                .isInstanceOf(ErroPermanenteException.class)
                .hasMessage("Não foi possível ler a duração do vídeo.");
    }
}
