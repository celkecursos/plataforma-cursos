package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.Slug;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.time.Clock;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de cursos pelo ADM. */
@Service
public class CursoAdmService {

    private final CursoRepository cursos;
    private final TurmaRepository turmas;
    private final Clock clock;

    public CursoAdmService(CursoRepository cursos, TurmaRepository turmas, Clock clock) {
        this.cursos = cursos;
        this.turmas = turmas;
        this.clock = clock;
    }

    /** Cursos em ordem alfabética, opcionalmente só os de um status (a ordenação do cliente é ignorada). */
    @Transactional(readOnly = true)
    public Page<Curso> listar(StatusCurso status, Pageable pagina) {
        Pageable ordenada = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by("titulo", "id"));
        return status == null ? cursos.findAll(ordenada) : cursos.findByStatus(status, ordenada);
    }

    @Transactional(readOnly = true)
    public Curso buscar(Long id) {
        return cursos.findById(id).orElseThrow(CursoAdmService::naoEncontrado);
    }

    @Transactional
    public Curso criar(DadosCurso dados) {
        String slug = slugDe(dados);
        if (cursos.existsBySlug(slug)) {
            throw slugEmUso(slug);
        }
        return cursos.save(new Curso(dados.titulo(), slug, dados.resumo(), dados.descricao(), clock.instant()));
    }

    @Transactional
    public Curso atualizar(Long id, DadosCurso dados) {
        Curso curso = buscar(id);
        String slug = slugDe(dados);
        if (cursos.existsBySlugAndIdNot(slug, id)) {
            throw slugEmUso(slug);
        }
        curso.atualizar(dados.titulo(), slug, dados.resumo(), dados.descricao(), clock.instant());
        return curso;
    }

    @Transactional
    public Curso alterarStatus(Long id, StatusCurso status) {
        Curso curso = buscar(id);
        curso.alterarStatus(status, clock.instant());
        return curso;
    }

    /**
     * Exclui um curso sem turmas e devolve a chave da capa, que o chamador apaga do bucket. Curso com
     * turmas é arquivado, para não perder matrículas e histórico.
     */
    @Transactional
    public Optional<String> excluir(Long id) {
        Curso curso = buscar(id);
        if (turmas.existsByCursoId(id)) {
            throw new ConflitoException("O curso tem turmas. Exclua as turmas vazias ou arquive o curso.");
        }
        cursos.delete(curso);
        return Optional.ofNullable(curso.getCapaChave());
    }

    /** Troca a capa e devolve a chave da anterior, que o chamador apaga do bucket. */
    @Transactional
    public Optional<String> trocarCapa(Long id, String novaChave) {
        Curso curso = buscar(id);
        Optional<String> anterior = Optional.ofNullable(curso.getCapaChave());
        curso.definirCapa(novaChave, clock.instant());
        return anterior;
    }

    private static String slugDe(DadosCurso dados) {
        String slug = dados.slug() == null || dados.slug().isBlank() ? Slug.gerar(dados.titulo()) : dados.slug();
        if (!Slug.valido(slug)) {
            throw new RegraNegocioException(
                    HttpStatus.BAD_REQUEST, "Não foi possível gerar o slug a partir do título. Informe o slug.");
        }
        return slug;
    }

    private static ConflitoException slugEmUso(String slug) {
        return new ConflitoException("Já existe um curso com o slug \"%s\".".formatted(slug));
    }

    static NaoEncontradoException naoEncontrado() {
        return new NaoEncontradoException("Curso não encontrado.");
    }
}
