package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão das turmas de um curso pelo ADM. */
@Service
public class TurmaAdmService {

    private final CursoRepository cursos;
    private final TurmaRepository turmas;
    private final ModuloRepository modulos;
    private final Clock clock;

    public TurmaAdmService(CursoRepository cursos, TurmaRepository turmas, ModuloRepository modulos, Clock clock) {
        this.cursos = cursos;
        this.turmas = turmas;
        this.modulos = modulos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Turma> listar(Long cursoId) {
        if (!cursos.existsById(cursoId)) {
            throw CursoAdmService.naoEncontrado();
        }
        return turmas.findByCursoIdOrderById(cursoId);
    }

    @Transactional
    public Turma criar(Long cursoId, String nome) {
        var curso = cursos.findById(cursoId).orElseThrow(CursoAdmService::naoEncontrado);
        return turmas.save(new Turma(curso, nome, clock.instant()));
    }

    @Transactional
    public Turma renomear(Long id, String nome) {
        Turma turma = buscar(id);
        turma.renomear(nome, clock.instant());
        return turma;
    }

    @Transactional
    public Turma alterarStatus(Long id, StatusTurma status) {
        Turma turma = buscar(id);
        turma.alterarStatus(status, clock.instant());
        return turma;
    }

    /** Exclui uma turma sem módulos; turma com conteúdo é encerrada. */
    @Transactional
    public void excluir(Long id) {
        Turma turma = buscar(id);
        if (modulos.existsByTurmaId(id)) {
            throw new ConflitoException("A turma tem módulos. Exclua os módulos ou encerre a turma.");
        }
        turmas.delete(turma);
    }

    private Turma buscar(Long id) {
        return turmas.findById(id).orElseThrow(TurmaAdmService::naoEncontrada);
    }

    static NaoEncontradoException naoEncontrada() {
        return new NaoEncontradoException("Turma não encontrada.");
    }
}
