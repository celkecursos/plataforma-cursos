package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo público: só cursos publicados e, no detalhe, só o conteúdo ativo da turma ativa mais
 * recente. Curso em rascunho ou arquivado responde 404, como se não existisse.
 */
@Service
public class CatalogoService {

    private final CursoRepository cursos;
    private final TurmaRepository turmas;
    private final ModuloRepository modulos;
    private final AulaRepository aulas;

    public CatalogoService(
            CursoRepository cursos, TurmaRepository turmas, ModuloRepository modulos, AulaRepository aulas) {
        this.cursos = cursos;
        this.turmas = turmas;
        this.modulos = modulos;
        this.aulas = aulas;
    }

    @Transactional(readOnly = true)
    public Page<Curso> listar(Pageable pagina) {
        Pageable ordenada = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by("titulo", "id"));
        return cursos.findByStatus(StatusCurso.PUBLICADO, ordenada);
    }

    @Transactional(readOnly = true)
    public Curso buscar(String slug) {
        return cursos.findBySlugAndStatus(slug, StatusCurso.PUBLICADO)
                .orElseThrow(() -> new NaoEncontradoException("Curso não encontrado."));
    }

    @Transactional(readOnly = true)
    public CursoDetalhe detalhar(String slug) {
        Curso curso = buscar(slug);
        List<ModuloDetalhe> conteudo = turmas.findFirstByCursoIdAndStatusOrderByIdDesc(curso.getId(), StatusTurma.ATIVA)
                .map(turma -> conteudo(modulos.findByTurmaIdAndStatusOrderByOrdem(turma.getId(), StatusConteudo.ATIVO)))
                .orElse(List.of());
        return new CursoDetalhe(curso, conteudo);
    }

    private List<ModuloDetalhe> conteudo(List<Modulo> ativos) {
        Map<Long, List<Aula>> aulasPorModulo = aulas
                .findByModuloIdInAndStatusOrderByOrdem(
                        ativos.stream().map(Modulo::getId).toList(), StatusConteudo.ATIVO)
                .stream()
                .collect(Collectors.groupingBy(aula -> aula.getModulo().getId()));
        return ativos.stream()
                .map(modulo -> new ModuloDetalhe(modulo, aulasPorModulo.getOrDefault(modulo.getId(), List.of())))
                .toList();
    }

    public record CursoDetalhe(Curso curso, List<ModuloDetalhe> modulos) {}

    public record ModuloDetalhe(Modulo modulo, List<Aula> aulas) {}
}
