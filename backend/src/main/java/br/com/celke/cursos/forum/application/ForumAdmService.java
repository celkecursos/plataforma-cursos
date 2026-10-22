package br.com.celke.cursos.forum.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoDetalhe;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoResumo;
import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.forum.infra.TopicoRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moderação do fórum pelo ADM: vê tudo (ocultos inclusive), oculta, fixa e responde como instrutor. */
@Service
public class ForumAdmService {

    private final TopicoRepository topicos;
    private final RespostaRepository respostas;
    private final ConteudoTurmaService conteudoTurmaService;
    private final LeituraForum leitura;
    private final Clock clock;

    public ForumAdmService(
            TopicoRepository topicos,
            RespostaRepository respostas,
            ConteudoTurmaService conteudoTurmaService,
            LeituraForum leitura,
            Clock clock) {
        this.topicos = topicos;
        this.respostas = respostas;
        this.conteudoTurmaService = conteudoTurmaService;
        this.leitura = leitura;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<TopicoResumo> listar(Long turmaId, Pageable pagina) {
        if (conteudoTurmaService.turma(turmaId).isEmpty()) {
            throw new NaoEncontradoException("Turma não encontrada.");
        }
        return leitura.resumos(topicos.daTurma(turmaId, PageRequest.of(pagina.getPageNumber(), pagina.getPageSize())));
    }

    @Transactional(readOnly = true)
    public TopicoDetalhe topico(Long topicoId) {
        return leitura.detalhe(buscar(topicoId), true);
    }

    /** Oculta/mostra e fixa/solta; só os campos preenchidos mudam. */
    @Transactional
    public TopicoDetalhe moderarTopico(Long topicoId, Boolean oculto, Boolean fixado) {
        Topico topico = buscar(topicoId);
        topico.moderar(oculto, fixado, clock.instant());
        return leitura.detalhe(topico, true);
    }

    @Transactional
    public TopicoDetalhe moderarResposta(Long respostaId, boolean oculto) {
        Resposta resposta = respostas
                .findById(respostaId)
                .orElseThrow(() -> new NaoEncontradoException("Resposta não encontrada."));
        resposta.moderar(oculto, clock.instant());
        return leitura.detalhe(buscar(resposta.getTopicoId()), true);
    }

    /** Resposta do instrutor: marcada como tal e sem limite de mensagens. */
    @Transactional
    public TopicoDetalhe responder(Long adminId, Long topicoId, String corpo) {
        Topico topico = buscar(topicoId);
        Instant agora = clock.instant();
        respostas.save(new Resposta(topicoId, adminId, corpo, true, agora));
        topico.registrarRespostaInstrutor(agora);
        return leitura.detalhe(topico, true);
    }

    private Topico buscar(Long topicoId) {
        return topicos.findById(topicoId).orElseThrow(ForumAlunoService::naoEncontrado);
    }
}
