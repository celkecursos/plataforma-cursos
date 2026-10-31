package br.com.celke.cursos.video.infra;

import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * O que a conversão precisa saber do original, lido da saída JSON do ffprobe. Arquivo sem faixa de
 * vídeo ou sem duração é erro permanente: tentar de novo não muda o arquivo.
 *
 * @param duracao duração em segundos
 */
public record AnaliseVideo(double duracao, int largura, int altura, boolean temAudio) {

    public static AnaliseVideo de(SaidaFfprobe saida) {
        List<Faixa> faixas = saida == null || saida.streams() == null ? List.of() : saida.streams();
        Faixa video = faixas.stream()
                .filter(f -> "video".equals(f.tipo()) && f.largura() != null && f.altura() != null)
                .filter(f -> f.largura() > 0 && f.altura() > 0)
                .findFirst()
                .orElseThrow(() -> new ErroPermanenteException("O arquivo não tem uma faixa de vídeo."));
        boolean temAudio = faixas.stream().anyMatch(f -> "audio".equals(f.tipo()));
        return new AnaliseVideo(duracao(saida), video.largura(), video.altura(), temAudio);
    }

    public int duracaoSegundos() {
        return (int) Math.round(duracao);
    }

    private static double duracao(SaidaFfprobe saida) {
        try {
            double duracao = Double.parseDouble(saida.format().duration());
            if (duracao > 0 && Double.isFinite(duracao)) {
                return duracao;
            }
        } catch (NullPointerException | NumberFormatException e) {
            // cai na mensagem abaixo
        }
        throw new ErroPermanenteException("Não foi possível ler a duração do vídeo.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SaidaFfprobe(List<Faixa> streams, Formato format) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Faixa(
            @JsonProperty("codec_type") String tipo,
            @JsonProperty("width") Integer largura,
            @JsonProperty("height") Integer altura) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Formato(String duration) {}
}
