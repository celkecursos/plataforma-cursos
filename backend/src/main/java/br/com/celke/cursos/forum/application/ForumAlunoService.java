package br.com.celke.cursos.forum.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.forum.application.AnexoForumService.AnexoGravado;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoDetalhe;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoResumo;
import br.com.celke.cursos.forum.domain.Anexo;
import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.AnexoRepository;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.forum.infra.TopicoRepository;
import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fórum na área do aluno: só com matrícula vigente na turma ({@link AcessoAulaService#exigirAcessoTurma}).
 * Mensagens ocultas pela moderação não aparecem; publicar respeita o {@link LimiteForum}.
 */
@Service
public class ForumAlunoService {

    private final TopicoRepository topicos;
    private final RespostaRepository respostas;
    private final AnexoRepository anexos;
    private final AcessoAulaService acessoAulaService;
    private final ConteudoTurmaService conteudoTurmaService;
    private final AnexoForumService anexoForumService;
    private final LimiteForum limite;
    private final LeituraForum leitura;
    private final TransactionTemplate transacao;
    private final Clock clock;

    public ForumAlunoService(
            TopicoRepository topicos,
            RespostaRepository respostas,
            AnexoRepository anexos,
            AcessoAulaService acessoAulaService,
            ConteudoTurmaService conteudoTurmaService,
            AnexoForumService anexoForumService,
            LimiteForum limite,
            LeituraForum leitura,
            TransactionTemplate transacao,
            Clock clock) {
        this.topicos = topicos;
        this.respostas = respostas;
        this.anexos = anexos;
        this.acessoAulaService = acessoAulaService;
        this.conteudoTurmaService = conteudoTurmaService;
        this.anexoForumService = anexoForumService;
        this.limite = limite;
        this.leitura = leitura;
        this.transacao = transacao;
        this.clock = clock;
    }

    /** Tópicos visíveis da turma (ou só os de uma aula), fixados primeiro. */
    @Transactional(readOnly = true)
    public Page<TopicoResumo> listar(Long usuarioId, Long turmaId, Long aulaId, Pageable pagina) {
        acessoAulaService.exigirAcessoTurma(usuarioId, turmaId);
        return leitura.resumos(
                topicos.visiveis(turmaId, aulaId, PageRequest.of(pagina.getPageNumber(), pagina.getPageSize())));
    }

    /** Pergunta nova na turma ou numa aula ativa da turma. */
    public TopicoDetalhe perguntar(
            Long usuarioId, Long turmaId, Long aulaId, String titulo, String corpo, List<String> chavesAnexos) {
        acessoAulaService.exigirAcessoTurma(usuarioId, turmaId);
        if (aulaId != null
                && conteudoTurmaService
                        .aulaAtiva(aulaId)
                        .filter(aula -> aula.turmaId().equals(turmaId))
                        .isEmpty()) {
            throw new NaoEncontradoException("Aula não encontrada.");
        }
        limite.exigirDentroDoLimite(usuarioId);
        List<AnexoGravado> gravados = anexoForumService.processar(usuarioId, chavesAnexos);
        try {
            return transacao.execute(status -> {
                Instant agora = clock.instant();
                Topico topico = topicos.save(new Topico(turmaId, aulaId, usuarioId, titulo, corpo, agora));
                gravados.forEach(
                        g -> anexos.save(Anexo.doTopico(topico.getId(), usuarioId, g.chave(), g.tamanho(), agora)));
                return leitura.detalhe(topico, false);
            });
        } catch (RuntimeException e) {
            anexoForumService.descartar(gravados);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public TopicoDetalhe topico(Long usuarioId, Long topicoId) {
        Topico topico = visivel(topicoId);
        acessoAulaService.exigirAcessoTurma(usuarioId, topico.getTurmaId());
        return leitura.detalhe(topico, false);
    }

    /** Resposta de aluno a um tópico visível da turma dele. */
    public TopicoDetalhe responder(Long usuarioId, Long topicoId, String corpo, List<String> chavesAnexos) {
        Topico topico = visivel(topicoId);
        acessoAulaService.exigirAcessoTurma(usuarioId, topico.getTurmaId());
        limite.exigirDentroDoLimite(usuarioId);
        List<AnexoGravado> gravados = anexoForumService.processar(usuarioId, chavesAnexos);
        try {
            return transacao.execute(status -> {
                Instant agora = clock.instant();
                Resposta resposta = respostas.save(new Resposta(topicoId, usuarioId, corpo, false, agora));
                gravados.forEach(
                        g -> anexos.save(Anexo.daResposta(resposta.getId(), usuarioId, g.chave(), g.tamanho(), agora)));
                return leitura.detalhe(topico, false);
            });
        } catch (RuntimeException e) {
            anexoForumService.descartar(gravados);
            throw e;
        }
    }

    private Topico visivel(Long topicoId) {
        return topicos.findByIdAndOcultoFalse(topicoId).orElseThrow(ForumAlunoService::naoEncontrado);
    }

    static NaoEncontradoException naoEncontrado() {
        return new NaoEncontradoException("Tópico não encontrado.");
    }
}
