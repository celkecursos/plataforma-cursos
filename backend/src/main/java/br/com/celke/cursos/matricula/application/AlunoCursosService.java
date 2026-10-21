package br.com.celke.cursos.matricula.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.matricula.domain.AcessoAulaPolicy;
import br.com.celke.cursos.matricula.domain.FiltroMeusCursos;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.SituacaoMatricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Área do aluno: os cursos dele e o conteúdo das turmas em que tem matrícula vigente. */
@Service
public class AlunoCursosService {

    private final MatriculaRepository matriculas;
    private final ConteudoTurmaService conteudoTurmaService;
    private final AcessoAulaPolicy policy;
    private final Clock clock;

    public AlunoCursosService(
            MatriculaRepository matriculas,
            ConteudoTurmaService conteudoTurmaService,
            AcessoAulaPolicy policy,
            Clock clock) {
        this.matriculas = matriculas;
        this.conteudoTurmaService = conteudoTurmaService;
        this.policy = policy;
        this.clock = clock;
    }

    /** Cursos do aluno na aba pedida, em ordem alfabética. */
    @Transactional(readOnly = true)
    public List<MeuCurso> meusCursos(Long usuarioId, FiltroMeusCursos filtro) {
        Instant agora = clock.instant();
        List<Matricula> doFiltro = matriculas.findByUsuarioIdAndStatus(usuarioId, StatusMatricula.ATIVA).stream()
                .filter(m -> filtro.aceita(m, agora))
                .toList();
        Map<Long, TurmaResumo> turmas = conteudoTurmaService.turmas(
                doFiltro.stream().map(Matricula::getTurmaId).toList());
        return doFiltro.stream()
                .map(m -> new MeuCurso(m, turmas.get(m.getTurmaId()), m.situacao(agora)))
                .sorted(Comparator.comparing((MeuCurso c) -> c.turma().cursoTitulo())
                        .thenComparing(c -> c.matricula().getId()))
                .toList();
    }

    /** Turma com os módulos e as aulas ativos; exige matrícula vigente (403 sem ela). */
    @Transactional(readOnly = true)
    public TurmaDoAluno turma(Long usuarioId, Long turmaId) {
        TurmaResumo turma = conteudoTurmaService
                .turma(turmaId)
                .orElseThrow(() -> new NaoEncontradoException("Turma não encontrada."));
        Matricula matricula = matriculas
                .findByUsuarioIdAndTurmaIdAndStatus(usuarioId, turmaId, StatusMatricula.ATIVA)
                .orElse(null);
        if (!policy.podeAcessarTurma(matricula)) {
            throw new AcessoNegadoException("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
        }
        return new TurmaDoAluno(matricula, turma, conteudoTurmaService.conteudoAtivo(turmaId));
    }

    @Transactional
    public void arquivar(Long usuarioId, Long matriculaId) {
        doAluno(usuarioId, matriculaId).arquivar(clock.instant());
    }

    @Transactional
    public void desarquivar(Long usuarioId, Long matriculaId) {
        doAluno(usuarioId, matriculaId).desarquivar(clock.instant());
    }

    /** Matrícula do próprio aluno; a de outro aluno ou cancelada responde 404, sem revelar que existe. */
    private Matricula doAluno(Long usuarioId, Long matriculaId) {
        return matriculas
                .findByIdAndUsuarioId(matriculaId, usuarioId)
                .filter(m -> m.getStatus() == StatusMatricula.ATIVA)
                .orElseThrow(() -> new NaoEncontradoException("Matrícula não encontrada."));
    }

    public record MeuCurso(Matricula matricula, TurmaResumo turma, SituacaoMatricula situacao) {}

    public record TurmaDoAluno(Matricula matricula, TurmaResumo turma, List<ModuloConteudo> modulos) {}
}
