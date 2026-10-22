package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.Ordenacao;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão dos módulos de uma turma pelo ADM. Toda mudança de ordem trava a turma, para duas
 * operações simultâneas não gerarem posições repetidas.
 */
@Service
public class ModuloAdmService {

    private final TurmaRepository turmas;
    private final ModuloRepository modulos;
    private final AulaRepository aulas;
    private final Clock clock;

    public ModuloAdmService(TurmaRepository turmas, ModuloRepository modulos, AulaRepository aulas, Clock clock) {
        this.turmas = turmas;
        this.modulos = modulos;
        this.aulas = aulas;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Modulo> listar(Long turmaId) {
        if (!turmas.existsById(turmaId)) {
            throw TurmaAdmService.naoEncontrada();
        }
        return modulos.findByTurmaIdOrderByOrdem(turmaId);
    }

    /** O módulo novo entra no fim da turma. */
    @Transactional
    public Modulo criar(Long turmaId, String titulo) {
        Turma turma = turmas.travar(turmaId).orElseThrow(TurmaAdmService::naoEncontrada);
        int ordem = Ordenacao.proxima(modulos.findByTurmaIdOrderByOrdem(turmaId));
        return modulos.save(new Modulo(turma, titulo, ordem, clock.instant()));
    }

    @Transactional
    public Modulo renomear(Long id, String titulo) {
        Modulo modulo = buscar(id);
        modulo.renomear(titulo, clock.instant());
        return modulo;
    }

    @Transactional
    public Modulo alterarStatus(Long id, StatusConteudo status) {
        Modulo modulo = buscar(id);
        modulo.alterarStatus(status, clock.instant());
        return modulo;
    }

    @Transactional
    public List<Modulo> reordenar(Long turmaId, List<Long> ids) {
        turmas.travar(turmaId).orElseThrow(TurmaAdmService::naoEncontrada);
        List<Modulo> atuais = modulos.findByTurmaIdOrderByOrdem(turmaId);
        Ordenacao.reordenar(atuais, ids);
        return atuais.stream()
                .sorted((a, b) -> Integer.compare(a.getOrdem(), b.getOrdem()))
                .toList();
    }

    /** Exclui um módulo sem aulas e refaz a sequência dos que ficam. */
    @Transactional
    public void excluir(Long id) {
        Modulo modulo = buscar(id);
        Long turmaId = modulo.getTurma().getId();
        turmas.travar(turmaId);
        if (aulas.existsByModuloId(id)) {
            throw new ConflitoException("O módulo tem aulas. Exclua as aulas ou inative o módulo.");
        }
        modulos.delete(modulo);
        modulos.flush();
        Ordenacao.compactar(modulos.findByTurmaIdOrderByOrdem(turmaId));
    }

    private Modulo buscar(Long id) {
        return modulos.findById(id).orElseThrow(ModuloAdmService::naoEncontrado);
    }

    static NaoEncontradoException naoEncontrado() {
        return new NaoEncontradoException("Módulo não encontrado.");
    }
}
