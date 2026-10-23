package br.com.celke.cursos.forum.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Corpos das rotas do fórum. O corpo das mensagens é Markdown; o HTML é gerado e filtrado pela API. */
public final class ForumRequests {

    /** Tamanho máximo do Markdown de uma mensagem. */
    public static final int CORPO_MAXIMO = 10_000;

    private ForumRequests() {}

    /**
     * @param aulaId aula da turma (opcional): sem ela, o tópico é geral da turma
     * @param anexos chaves das imagens já enviadas pelo próprio usuário
     */
    public record Perguntar(
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = CORPO_MAXIMO) String corpo,
            Long aulaId,
            @Size(max = 10) List<@NotBlank @Size(max = 200) String> anexos) {}

    public record Responder(
            @NotBlank @Size(max = CORPO_MAXIMO) String corpo,
            @Size(max = 10) List<@NotBlank @Size(max = 200) String> anexos) {}

    public record ResponderInstrutor(
            @NotBlank @Size(max = CORPO_MAXIMO) String corpo) {}

    public record IniciarEnvioAnexo(
            @NotBlank String contentType, @Positive long tamanho) {}

    /** Só os campos enviados mudam. */
    public record ModerarTopico(Boolean oculto, Boolean fixado) {}

    public record ModerarResposta(@NotNull Boolean oculto) {}
}
