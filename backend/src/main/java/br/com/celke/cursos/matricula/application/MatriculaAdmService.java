package br.com.celke.cursos.matricula.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Matrícula manual pelo ADM: matricular, alterar o período e cancelar. */
@Service
public class MatriculaAdmService {

    private final MatriculaRepository matriculas;
    private final UsuarioService usuarioService;
    private final ConteudoTurmaService conteudoTurmaService;
    private final Clock clock;

    public MatriculaAdmService(
            MatriculaRepository matriculas,
            UsuarioService usuarioService,
            ConteudoTurmaService conteudoTurmaService,
            Clock clock) {
        this.matriculas = matriculas;
        this.usuarioService = usuarioService;
        this.conteudoTurmaService = conteudoTurmaService;
        this.clock = clock;
    }

    /** Matrículas da turma (inclusive canceladas), das mais recentes para as mais antigas. */
    @Transactional(readOnly = true)
    public Page<MatriculaComAluno> listar(Long turmaId, Pageable pagina) {
        buscarTurma(turmaId);
        Page<Matricula> page = matriculas.findByTurmaId(
                turmaId,
                PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, Usuario> usuarios = usuarioService.buscarPorIds(
                page.getContent().stream().map(Matricula::getUsuarioId).toList());
        return page.map(m -> new MatriculaComAluno(m, usuarios.get(m.getUsuarioId())));
    }

    @Transactional
    public MatriculaComAluno matricular(Long turmaId, NovaMatricula dados) {
        TurmaResumo turma = buscarTurma(turmaId);
        if (turma.turmaStatus() == StatusTurma.ENCERRADA) {
            throw new ConflitoException("A turma está encerrada e não recebe novas matrículas.");
        }
        Usuario usuario = usuarioService
                .buscarPorEmail(dados.email())
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
        if (matriculas.existsByUsuarioIdAndTurmaIdAndStatus(usuario.getId(), turmaId, StatusMatricula.ATIVA)) {
            throw jaMatriculado();
        }
        if (dados.transacao() != null
                && !dados.transacao().isBlank()
                && matriculas.existsByFormaPagamentoAndTransacao(
                        dados.formaPagamento(), dados.transacao().strip())) {
            throw new ConflitoException("Esta transação já gerou uma matrícula.");
        }
        Instant agora = clock.instant();
        Matricula matricula = new Matricula(
                usuario.getId(),
                turmaId,
                dados.inicio() == null ? agora : dados.inicio(),
                dados.fim(),
                dados.formaPagamento(),
                dados.transacao(),
                agora);
        try {
            // O índice único do banco decide quando duas matrículas iguais chegam ao mesmo tempo
            return new MatriculaComAluno(matriculas.saveAndFlush(matricula), usuario);
        } catch (DataIntegrityViolationException e) {
            throw jaMatriculado();
        }
    }

    @Transactional
    public MatriculaComAluno alterarPeriodo(Long id, Instant inicio, Instant fim) {
        Matricula matricula = buscar(id);
        matricula.alterarPeriodo(inicio, fim, clock.instant());
        return comAluno(matricula);
    }

    @Transactional
    public MatriculaComAluno cancelar(Long id) {
        Matricula matricula = buscar(id);
        matricula.cancelar(clock.instant());
        return comAluno(matricula);
    }

    private MatriculaComAluno comAluno(Matricula matricula) {
        return new MatriculaComAluno(
                matricula, usuarioService.buscarPorId(matricula.getUsuarioId()).orElse(null));
    }

    private Matricula buscar(Long id) {
        return matriculas.findById(id).orElseThrow(() -> new NaoEncontradoException("Matrícula não encontrada."));
    }

    private TurmaResumo buscarTurma(Long turmaId) {
        return conteudoTurmaService
                .turma(turmaId)
                .orElseThrow(() -> new NaoEncontradoException("Turma não encontrada."));
    }

    private static ConflitoException jaMatriculado() {
        return new ConflitoException("O aluno já tem matrícula nesta turma. Altere o período da matrícula atual.");
    }

    public record MatriculaComAluno(Matricula matricula, Usuario usuario) {}
}
