package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.application.CapaCursoService.EnvioCapa;
import br.com.celke.cursos.curso.application.CatalogoService.CursoDetalhe;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Respostas das rotas de cursos: gestão ({@code /api/adm}) e catálogo público ({@code /api/cursos}). */
public final class CursoResponses {

    private CursoResponses() {}

    /** Curso na gestão; {@code capaUrl} é temporária (vale uma hora). */
    public record CursoAdm(
            Long id,
            String titulo,
            String slug,
            String resumo,
            String descricao,
            StatusCurso status,
            String capaUrl,
            Instant criadoEm,
            Instant atualizadoEm) {

        public static CursoAdm de(br.com.celke.cursos.curso.domain.Curso curso, Optional<URL> capa) {
            return new CursoAdm(
                    curso.getId(),
                    curso.getTitulo(),
                    curso.getSlug(),
                    curso.getResumo(),
                    curso.getDescricao(),
                    curso.getStatus(),
                    capa.map(URL::toString).orElse(null),
                    curso.getCriadoEm(),
                    curso.getAtualizadoEm());
        }
    }

    public record Turma(Long id, Long cursoId, String nome, StatusTurma status) {

        public static Turma de(br.com.celke.cursos.curso.domain.Turma turma) {
            return new Turma(turma.getId(), turma.getCurso().getId(), turma.getNome(), turma.getStatus());
        }
    }

    public record Modulo(Long id, Long turmaId, String titulo, int ordem, StatusConteudo status) {

        public static Modulo de(br.com.celke.cursos.curso.domain.Modulo modulo) {
            return new Modulo(
                    modulo.getId(),
                    modulo.getTurma().getId(),
                    modulo.getTitulo(),
                    modulo.getOrdem(),
                    modulo.getStatus());
        }
    }

    public record Aula(
            Long id,
            Long moduloId,
            String titulo,
            String descricao,
            int ordem,
            boolean gratuita,
            StatusConteudo status) {

        public static Aula de(br.com.celke.cursos.curso.domain.Aula aula) {
            return new Aula(
                    aula.getId(),
                    aula.getModulo().getId(),
                    aula.getTitulo(),
                    aula.getDescricao(),
                    aula.getOrdem(),
                    aula.isGratuita(),
                    aula.getStatus());
        }
    }

    /** Onde e como enviar a imagem: PUT na {@code url} com o cabeçalho {@code Content-Type} informado. */
    public record EnvioCapaResponse(String chave, String url, String contentType, Instant expiraEm) {

        public static EnvioCapaResponse de(EnvioCapa envio) {
            return new EnvioCapaResponse(envio.chave(), envio.url().toString(), envio.contentType(), envio.expiraEm());
        }
    }

    /**
     * Curso no catálogo. {@code capaUrl} é um endereço estável da API (relativo), que redireciona para
     * a imagem: pode ir para HTML em cache ou pré-renderizado sem vencer.
     */
    public record CursoCatalogo(String slug, String titulo, String resumo, String capaUrl) {

        public static CursoCatalogo de(br.com.celke.cursos.curso.domain.Curso curso) {
            return new CursoCatalogo(curso.getSlug(), curso.getTitulo(), curso.getResumo(), enderecoCapa(curso));
        }
    }

    public record CursoCatalogoDetalhe(
            String slug, String titulo, String resumo, String descricao, String capaUrl, List<ModuloCatalogo> modulos) {

        public static CursoCatalogoDetalhe de(CursoDetalhe detalhe) {
            var curso = detalhe.curso();
            return new CursoCatalogoDetalhe(
                    curso.getSlug(),
                    curso.getTitulo(),
                    curso.getResumo(),
                    curso.getDescricao(),
                    enderecoCapa(curso),
                    detalhe.modulos().stream().map(ModuloCatalogo::de).toList());
        }
    }

    public record ModuloCatalogo(String titulo, List<AulaCatalogo> aulas) {

        static ModuloCatalogo de(ModuloConteudo modulo) {
            return new ModuloCatalogo(
                    modulo.titulo(),
                    modulo.aulas().stream().map(AulaCatalogo::de).toList());
        }
    }

    public record AulaCatalogo(Long id, String titulo, boolean gratuita) {

        static AulaCatalogo de(AulaConteudo aula) {
            return new AulaCatalogo(aula.id(), aula.titulo(), aula.gratuita());
        }
    }

    private static String enderecoCapa(br.com.celke.cursos.curso.domain.Curso curso) {
        return curso.getCapaChave() == null ? null : "/api/cursos/%s/capa".formatted(curso.getSlug());
    }
}
