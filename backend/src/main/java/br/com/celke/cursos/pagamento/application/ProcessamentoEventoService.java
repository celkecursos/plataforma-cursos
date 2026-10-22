package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.auth.application.ContaProperties;
import br.com.celke.cursos.auth.application.RecuperacaoSenhaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService.Concessao;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService.NovaConcessao;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.EventoRecebido;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.PedidoHistorico;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.domain.TransicoesPedido;
import br.com.celke.cursos.pagamento.domain.TransicoesPedido.Decisao;
import br.com.celke.cursos.pagamento.domain.TransicoesPedido.EfeitoMatricula;
import br.com.celke.cursos.pagamento.infra.EventoRecebidoRepository;
import br.com.celke.cursos.pagamento.infra.OfertaRepository;
import br.com.celke.cursos.pagamento.infra.PedidoHistoricoRepository;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Aplica um evento gravado ao pedido (roda no worker, uma transação por evento, com o pedido
 * travado): acha ou cria o pedido, decide a mudança pela {@link TransicoesPedido}, grava o histórico,
 * concede ou cancela a matrícula e envia os e-mails. Exceção desfaz tudo e a fila tenta de novo.
 */
@Service
public class ProcessamentoEventoService {

    private final EventoRecebidoRepository eventos;
    private final PedidoRepository pedidos;
    private final PedidoHistoricoRepository historico;
    private final OfertaRepository ofertas;
    private final UsuarioService usuarioService;
    private final MatriculaPagamentoService matriculaPagamentoService;
    private final ConteudoTurmaService conteudoTurmaService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final EmailService emailService;
    private final AvisosPagamento avisos;
    private final TransicoesPedido transicoes;
    private final ContaProperties conta;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ProcessamentoEventoService(
            EventoRecebidoRepository eventos,
            PedidoRepository pedidos,
            PedidoHistoricoRepository historico,
            OfertaRepository ofertas,
            UsuarioService usuarioService,
            MatriculaPagamentoService matriculaPagamentoService,
            ConteudoTurmaService conteudoTurmaService,
            RecuperacaoSenhaService recuperacaoSenhaService,
            EmailService emailService,
            AvisosPagamento avisos,
            TransicoesPedido transicoes,
            ContaProperties conta,
            JsonMapper jsonMapper,
            Clock clock) {
        this.eventos = eventos;
        this.pedidos = pedidos;
        this.historico = historico;
        this.ofertas = ofertas;
        this.usuarioService = usuarioService;
        this.matriculaPagamentoService = matriculaPagamentoService;
        this.conteudoTurmaService = conteudoTurmaService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
        this.emailService = emailService;
        this.avisos = avisos;
        this.transicoes = transicoes;
        this.conta = conta;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public void processar(Long eventoId) {
        Instant agora = clock.instant();
        EventoRecebido evento = eventos.travar(eventoId)
                .orElseThrow(() -> new ErroPermanenteException("Evento de pagamento " + eventoId + " não existe."));
        if (evento.getStatus() != StatusEvento.RECEBIDO) {
            // Já processado (job repetido): nada a fazer
            return;
        }
        if (evento.getDados() == null) {
            evento.falhou("O evento não tem dados para processar.", agora);
            return;
        }
        EventoPagamento dados = jsonMapper.readValue(evento.getDados(), EventoPagamento.class);
        if (dados.statusPedido() == null) {
            evento.ignorado(null, "O evento " + dados.tipoOriginal() + " não muda o pedido.", agora);
            return;
        }
        Optional<Pedido> encontrado = localizar(evento.getGatewayId(), dados);
        Pedido pedido;
        if (encontrado.isPresent()) {
            pedido = encontrado.get();
        } else {
            Optional<Oferta> oferta = ofertas.findByGatewayIdAndProdutoExternoAndOfertaExterna(
                    evento.getGatewayId(), dados.produtoExterno(), dados.ofertaExterna());
            if (oferta.isEmpty()) {
                evento.ignorado(
                        null,
                        "Oferta não cadastrada (produto %s, oferta %s)."
                                .formatted(dados.produtoExterno(), dados.ofertaExterna()),
                        agora);
                return;
            }
            if (dados.comprador() == null || dados.comprador().email() == null) {
                evento.ignorado(null, "A notificação não traz o e-mail do comprador.", agora);
                return;
            }
            pedido = pedidos.saveAndFlush(Pedido.externo(
                    oferta.get(), dados.comprador().email(), dados.comprador().nome(), dados.transacao(), agora));
            historico.save(new PedidoHistorico(
                    pedido.getId(),
                    null,
                    StatusPedido.CRIADO,
                    evento.getId(),
                    "Venda sem checkout da plataforma",
                    agora));
        }
        pedido.vincularTransacao(dados.transacao(), agora);

        StatusPedido anterior = pedido.getStatus();
        Decisao decisao =
                transicoes.decidir(anterior, dados.statusPedido(), pedido.getUltimoEventoEm(), dados.ocorridoEm());
        if (!decisao.aplica()) {
            evento.ignorado(pedido.getId(), decisao.motivo(), agora);
            return;
        }
        pedido.registrarPagamento(dados.valorCentavos(), dados.moeda(), dados.formaPagamento(), agora);
        pedido.mudarStatus(dados.statusPedido(), dados.ocorridoEm(), agora);
        historico.save(new PedidoHistorico(
                pedido.getId(), anterior, dados.statusPedido(), evento.getId(), dados.tipoOriginal(), agora));
        aplicar(decisao.efeito(), pedido, dados, agora);
        if (dados.statusPedido() == StatusPedido.CHARGEBACK || dados.statusPedido() == StatusPedido.EM_DISPUTA) {
            avisos.pedidoContestado(pedido, dados.statusPedido());
        }
        evento.processado(pedido.getId(), agora);
    }

    /** O job desistiu (tentativas esgotadas): o evento fica em ERRO e o admin é avisado. */
    @Transactional
    public void falhar(Long eventoId, String motivo) {
        eventos.findById(eventoId).ifPresent(evento -> {
            evento.falhou(motivo, clock.instant());
            avisos.eventoComErro(evento.getId(), evento.getTipoOriginal(), motivo);
        });
    }

    /**
     * Tira o acesso de um pedido atrasado há mais tempo que o tolerado; o pedido continua
     * {@code ATRASADO} e a aprovação da cobrança devolve o acesso.
     */
    @Transactional
    public void suspenderPorAtraso(Long pedidoId) {
        pedidos.travar(pedidoId)
                .filter(p -> p.getStatus() == StatusPedido.ATRASADO && p.getMatriculaId() != null)
                .filter(p -> matriculaPagamentoService.ativa(p.getMatriculaId()))
                .ifPresent(p -> {
                    matriculaPagamentoService.cancelar(p.getMatriculaId());
                    historico.save(new PedidoHistorico(
                            p.getId(),
                            StatusPedido.ATRASADO,
                            StatusPedido.ATRASADO,
                            null,
                            "Acesso suspenso pelo atraso da cobrança",
                            clock.instant()));
                });
    }

    /**
     * O pedido do evento: pelo nosso código (se a transação bate), senão pela transação já conhecida.
     * Vazio: venda sem pedido na plataforma.
     */
    private Optional<Pedido> localizar(Long gatewayId, EventoPagamento dados) {
        if (dados.codigoPedido() != null) {
            Optional<Pedido> peloCodigo = pedidos.travarPorCodigo(dados.codigoPedido())
                    .filter(p -> p.getGatewayId().equals(gatewayId))
                    .filter(p -> p.getTransacaoExterna() == null
                            || dados.transacao() == null
                            || p.getTransacaoExterna().equals(dados.transacao()));
            if (peloCodigo.isPresent()) {
                return peloCodigo;
            }
        }
        if (dados.transacao() != null) {
            return pedidos.travarPorTransacao(gatewayId, dados.transacao());
        }
        return Optional.empty();
    }

    private void aplicar(EfeitoMatricula efeito, Pedido pedido, EventoPagamento dados, Instant agora) {
        switch (efeito) {
            case CONCEDER -> conceder(pedido, dados, agora);
            case CANCELAR -> {
                if (pedido.getMatriculaId() != null) {
                    matriculaPagamentoService.cancelar(pedido.getMatriculaId());
                }
            }
            case NENHUM -> {
                // o status muda, a matrícula não
            }
        }
    }

    private void conceder(Pedido pedido, EventoPagamento dados, Instant agora) {
        Oferta oferta = ofertas.findById(pedido.getOfertaId()).orElseThrow();
        Usuario usuario = comprador(pedido, agora);
        Instant inicio = dados.ocorridoEm() == null || dados.ocorridoEm().isAfter(agora) ? agora : dados.ocorridoEm();
        Concessao concessao = matriculaPagamentoService.conceder(new NovaConcessao(
                usuario.getId(),
                oferta.getTurmaId(),
                inicio,
                oferta.getDiasAcesso(),
                pedido.getFormaPagamento() == null ? FormaPagamento.OUTRO : pedido.getFormaPagamento(),
                pedido.getTransacaoExterna(),
                pedido.getId(),
                pedido.getMatriculaId()));
        pedido.vincularMatricula(concessao.matriculaId(), agora);
        if (concessao.novoAcesso()) {
            enviarConfirmacao(usuario, oferta.getTurmaId());
        }
    }

    /** Dono do pedido; sem conta com o e-mail, cria uma sem senha (o link de criar senha vai no e-mail). */
    private Usuario comprador(Pedido pedido, Instant agora) {
        if (pedido.getUsuarioId() != null) {
            return usuarioService.buscarPorId(pedido.getUsuarioId()).orElseThrow();
        }
        Usuario usuario = usuarioService
                .buscarPorEmail(pedido.getEmail())
                .orElseGet(() -> usuarioService.criarAlunoSemSenha(nomeDoComprador(pedido), pedido.getEmail()));
        pedido.vincularUsuario(usuario.getId(), agora);
        return usuario;
    }

    private void enviarConfirmacao(Usuario usuario, Long turmaId) {
        String curso = conteudoTurmaService
                .turma(turmaId)
                .map(ConteudoTurmaService.TurmaResumo::cursoTitulo)
                .orElse("seu curso");
        Map<String, String> variaveis = new HashMap<>();
        variaveis.put("nome", usuario.getNome());
        variaveis.put("curso", curso);
        variaveis.put("linkCurso", conta.urlAluno() + "/turmas/" + turmaId);
        if (!usuario.possuiSenha()) {
            recuperacaoSenhaService.linkCriarSenha(usuario.getId()).ifPresent(link -> {
                variaveis.put("linkSenha", link.url());
                variaveis.put("validade", link.validade());
            });
        }
        emailService.enviar(usuario.getEmail(), "Sua matrícula foi confirmada", "matricula-confirmada", variaveis);
    }

    private static String nomeDoComprador(Pedido pedido) {
        if (pedido.getNome() != null) {
            return pedido.getNome();
        }
        String local = pedido.getEmail().substring(0, pedido.getEmail().indexOf('@'));
        return local.isBlank() ? "Aluno" : local;
    }
}
