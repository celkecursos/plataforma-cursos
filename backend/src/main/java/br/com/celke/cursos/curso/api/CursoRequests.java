package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.application.DadosAula;
import br.com.celke.cursos.curso.application.DadosCurso;
import br.com.celke.cursos.curso.domain.Slug;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Corpos das rotas de gestão de cursos ({@code /api/adm}). */
public final class CursoRequests {

    private CursoRequests() {}

    /** Sem slug, ele é gerado a partir do título. */
    public record Curso(
            @NotBlank @Size(max = 120) String titulo,

            @Size(max = Slug.TAMANHO_MAXIMO)
            @Pattern(regexp = Slug.FORMATO, message = "deve ter só letras minúsculas, números e hífens")
            String slug,

            @NotBlank @Size(max = 300) String resumo,
            @Size(max = 20_000) String descricao) {

        public DadosCurso dados() {
            return new DadosCurso(titulo, slug, resumo, descricao);
        }
    }

    public record AlterarStatusCurso(@NotNull StatusCurso status) {}

    public record Turma(@NotBlank @Size(max = 120) String nome) {}

    public record AlterarStatusTurma(@NotNull StatusTurma status) {}

    /** Sem carga horária, o certificado sai sem ela. */
    public record CertificadoTurma(
            @Min(1) @Max(100) int percentualConclusao,
            @Positive @Max(10_000) Integer cargaHorariaHoras) {}

    public record Modulo(@NotBlank @Size(max = 150) String titulo) {}

    /** Sem {@code gratuita}, a aula é paga (só para matriculados). */
    public record Aula(
            @NotBlank @Size(max = 150) String titulo,
            @Size(max = 20_000) String descricao,
            Boolean gratuita) {

        public DadosAula dados() {
            return new DadosAula(titulo, descricao, Boolean.TRUE.equals(gratuita));
        }
    }

    public record AlterarStatusConteudo(@NotNull StatusConteudo status) {}

    /** Ids de todos os itens do pai, na nova ordem. */
    public record Ordem(@NotEmpty @Size(max = 500) List<@NotNull Long> ids) {}

    public record IniciarEnvioCapa(
            @NotBlank String contentType, @Positive long tamanho) {}

    public record ConfirmarCapa(@NotBlank @Size(max = 200) String chave) {}
}
