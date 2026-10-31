package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.Ordenacao;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão das aulas de um módulo pelo ADM. Toda mudança de ordem trava o módulo, para duas
 * operações simultâneas não gerarem posições repetidas.
 */
@Service
public class AulaAdmService {

    private final ModuloRepository modulos;
    private final AulaRepository aulas;
    private final Clock clock;

    public AulaAdmService(ModuloRepository modulos, AulaRepository aulas, Clock clock) {
        this.modulos = modulos;
        this.aulas = aulas;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Aula> listar(Long moduloId) {
        if (!modulos.existsById(moduloId)) {
            throw ModuloAdmService.naoEncontrado();
        }
        return aulas.findByModuloIdOrderByOrdem(moduloId);
    }

    /** A aula nova entra no fim do módulo. */
    @Transactional
    public Aula criar(Long moduloId, DadosAula dados) {
        Modulo modulo = modulos.travar(moduloId).orElseThrow(ModuloAdmService::naoEncontrado);
        int ordem = Ordenacao.proxima(aulas.findByModuloIdOrderByOrdem(moduloId));
        return aulas.save(
                new Aula(modulo, dados.titulo(), dados.descricao(), dados.gratuita(), ordem, clock.instant()));
    }

    @Transactional
    public Aula atualizar(Long id, DadosAula dados) {
        Aula aula = buscar(id);
        aula.atualizar(dados.titulo(), dados.descricao(), dados.gratuita(), clock.instant());
        return aula;
    }

    @Transactional
    public Aula alterarStatus(Long id, StatusConteudo status) {
        Aula aula = buscar(id);
        aula.alterarStatus(status, clock.instant());
        return aula;
    }

    @Transactional
    public List<Aula> reordenar(Long moduloId, List<Long> ids) {
        modulos.travar(moduloId).orElseThrow(ModuloAdmService::naoEncontrado);
        List<Aula> atuais = aulas.findByModuloIdOrderByOrdem(moduloId);
        Ordenacao.reordenar(atuais, ids);
        return atuais.stream()
                .sorted((a, b) -> Integer.compare(a.getOrdem(), b.getOrdem()))
                .toList();
    }

    /** Exclui a aula (só sem vídeo e sem arquivos) e refaz a sequência das que ficam. */
    @Transactional
    public void excluir(Long id) {
        Aula aula = buscar(id);
        Long moduloId = aula.getModulo().getId();
        modulos.travar(moduloId);
        try {
            aulas.delete(aula);
            aulas.flush();
        } catch (DataIntegrityViolationException e) {
            // As chaves estrangeiras do vídeo e dos arquivos de apoio impedem a exclusão
            throw new ConflitoException("A aula tem vídeo ou arquivos de apoio. Remova-os ou inative a aula.");
        }
        Ordenacao.compactar(aulas.findByModuloIdOrderByOrdem(moduloId));
    }

    private Aula buscar(Long id) {
        return aulas.findById(id).orElseThrow(() -> new NaoEncontradoException("Aula não encontrada."));
    }
}
