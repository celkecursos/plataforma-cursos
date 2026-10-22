package br.com.celke.cursos.matricula.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaContexto;
import br.com.celke.cursos.matricula.domain.AcessoAulaPolicy;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Porta de entrada da {@link AcessoAulaPolicy} para os outros módulos: toda rota de aula, vídeo,
 * arquivo, fórum ou progresso chama {@link #exigirAcesso} antes de responder.
 */
@Service
public class AcessoAulaService {

    private final ConteudoTurmaService conteudoTurmaService;
    private final MatriculaRepository matriculas;
    private final AcessoAulaPolicy policy;

    public AcessoAulaService(
            ConteudoTurmaService conteudoTurmaService, MatriculaRepository matriculas, AcessoAulaPolicy policy) {
        this.conteudoTurmaService = conteudoTurmaService;
        this.matriculas = matriculas;
        this.policy = policy;
    }

    /**
     * Devolve a aula se o usuário pode acessá-la. Aula inexistente ou inativa responde 404; sem
     * matrícula vigente (e sem ser aula aberta), 403.
     */
    @Transactional(readOnly = true)
    public AulaContexto exigirAcesso(Long usuarioId, Long aulaId) {
        AulaContexto aula = conteudoTurmaService
                .aulaAtiva(aulaId)
                .orElseThrow(() -> new NaoEncontradoException("Aula não encontrada."));
        var matricula = matriculas
                .findByUsuarioIdAndTurmaIdAndStatus(usuarioId, aula.turmaId(), StatusMatricula.ATIVA)
                .orElse(null);
        if (!policy.podeAcessarAula(matricula, aula.aberta())) {
            throw new AcessoNegadoException("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
        }
        return aula;
    }
}
