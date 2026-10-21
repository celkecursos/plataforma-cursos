package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.util.List;
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
    private final ConteudoTurmaService conteudoTurmaService;

    public CatalogoService(CursoRepository cursos, TurmaRepository turmas, ConteudoTurmaService conteudoTurmaService) {
        this.cursos = cursos;
        this.turmas = turmas;
        this.conteudoTurmaService = conteudoTurmaService;
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
        List<ModuloConteudo> conteudo = turmas.findFirstByCursoIdAndStatusOrderByIdDesc(
                        curso.getId(), StatusTurma.ATIVA)
                .map(turma -> conteudoTurmaService.conteudoAtivo(turma.getId()))
                .orElse(List.of());
        return new CursoDetalhe(curso, conteudo);
    }

    public record CursoDetalhe(Curso curso, List<ModuloConteudo> modulos) {}
}
