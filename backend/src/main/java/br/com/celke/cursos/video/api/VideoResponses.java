package br.com.celke.cursos.video.api;

import br.com.celke.cursos.video.application.EnvioVideoService.EnvioIniciado;
import br.com.celke.cursos.video.domain.StatusVideo;
import br.com.celke.cursos.video.domain.Video;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Respostas das rotas de vídeo do ADM. */
public final class VideoResponses {

    private VideoResponses() {}

    /**
     * Vídeo da aula na gestão. {@code thumbnailUrl} é temporária (vale uma hora). Os dados da conversão
     * são os da última que deu certo; {@code status} e {@code erro} são os do último envio.
     */
    public record VideoAdm(
            Long aulaId,
            StatusVideo status,
            boolean publicado,
            String nomeOriginal,
            Long tamanhoOriginal,
            Long tamanhoFinal,
            Double reducaoPercentual,
            Integer duracaoSegundos,
            Integer largura,
            Integer altura,
            List<String> variantes,
            Double segundosProcessamentoPorMinuto,
            String thumbnailUrl,
            String erro,
            Instant processadoEm,
            Instant atualizadoEm) {

        public static VideoAdm de(Video video, Optional<URL> thumbnail) {
            return new VideoAdm(
                    video.getAulaId(),
                    video.getStatus(),
                    video.getPlaylistChave() != null,
                    video.getNomeOriginal(),
                    video.getTamanhoOriginal(),
                    video.getTamanhoFinal(),
                    video.reducaoPercentual(),
                    video.getDuracaoSegundos(),
                    video.getLargura(),
                    video.getAltura(),
                    video.getVariantes() == null
                            ? List.of()
                            : List.of(video.getVariantes().split(",")),
                    video.segundosPorMinutoDeVideo(),
                    thumbnail.map(URL::toString).orElse(null),
                    video.getErro(),
                    video.getProcessadoEm(),
                    video.getAtualizadoEm());
        }
    }

    /**
     * Onde enviar cada parte: PUT do pedaço do arquivo ({@code tamanhoParte} bytes; a última parte leva
     * o resto) na {@code url} da parte, guardando o cabeçalho {@code ETag} da resposta para a conclusão.
     */
    public record EnvioVideoResponse(long tamanhoParte, List<ParteUrl> partes, Instant expiraEm) {

        public static EnvioVideoResponse de(EnvioIniciado envio) {
            return new EnvioVideoResponse(
                    envio.tamanhoParte(),
                    envio.partes().stream()
                            .map(p -> new ParteUrl(p.numero(), p.url().toString()))
                            .toList(),
                    envio.expiraEm());
        }
    }

    public record ParteUrl(int numero, String url) {}
}
