package br.com.celke.cursos.video.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do envio e da conversão de vídeos.
 *
 * @param tamanhoMaximo maior arquivo aceito no envio
 * @param validadeEnvio validade das URLs de envio das partes
 * @param ffmpeg executável do ffmpeg (no PATH ou caminho completo)
 * @param ffprobe executável do ffprobe
 * @param prefixoComando programa e argumentos colocados antes do ffmpeg (no worker, {@code nice -n 10}
 *     para a conversão ter prioridade baixa)
 * @param preset preset do x264: mais lento comprime melhor e gasta mais CPU
 * @param crf qualidade constante do x264 (menor = melhor e maior)
 * @param diretorioTrabalho onde o worker baixa o original e gera o HLS (apagado no fim de cada job)
 * @param margemPlaylist somada à duração do vídeo na validade das URLs dos segmentos (pausas, avanço)
 * @param retencaoLogAcesso por quanto tempo o log de acesso aos vídeos fica guardado
 */
@Validated
@ConfigurationProperties("app.video")
public record VideoProperties(
        @NotNull DataSize tamanhoMaximo,
        @NotNull Duration validadeEnvio,
        @NotBlank String ffmpeg,
        @NotBlank String ffprobe,
        List<String> prefixoComando,
        @NotBlank String preset,
        @Min(18) @Max(30) int crf,
        @NotNull Path diretorioTrabalho,
        @NotNull Duration margemPlaylist,
        @NotNull Duration retencaoLogAcesso) {

    public VideoProperties {
        prefixoComando = prefixoComando == null
                ? List.of()
                : prefixoComando.stream()
                        .map(String::strip)
                        .filter(p -> !p.isEmpty())
                        .toList();
    }
}
