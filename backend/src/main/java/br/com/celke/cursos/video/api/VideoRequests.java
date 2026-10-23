package br.com.celke.cursos.video.api;

import br.com.celke.cursos.shared.armazenamento.StorageService.ParteEnviada;
import br.com.celke.cursos.video.domain.DivisaoPartes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Corpos das rotas de vídeo do ADM. */
public final class VideoRequests {

    private VideoRequests() {}

    /** O tipo e o tamanho são conferidos de novo no arquivo real, depois do envio. */
    public record IniciarEnvio(
            @NotBlank @Size(max = 255) String nomeArquivo,
            @NotBlank String contentType,
            @Positive long tamanho) {}

    /** O ETag de cada parte vem no cabeçalho {@code ETag} da resposta do PUT da parte. */
    public record ConcluirEnvio(
            @NotEmpty @Size(max = DivisaoPartes.MAXIMO_PARTES)
            List<@Valid @NotNull Parte> partes) {

        public List<ParteEnviada> enviadas() {
            return partes.stream()
                    .map(p -> new ParteEnviada(p.numero(), p.etag()))
                    .toList();
        }
    }

    public record Parte(
            @Min(1) @Max(DivisaoPartes.MAXIMO_PARTES) int numero,
            @NotBlank @Size(max = 200) String etag) {}
}
