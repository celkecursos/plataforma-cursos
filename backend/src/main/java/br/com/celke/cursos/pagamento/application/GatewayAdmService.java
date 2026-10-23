package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.auth.application.ContaProperties;
import br.com.celke.cursos.auth.application.TokenSeguro;
import br.com.celke.cursos.pagamento.application.ConfiguracaoGatewayService.SegredoResumo;
import br.com.celke.cursos.pagamento.domain.CampoConfiguracao;
import br.com.celke.cursos.pagamento.domain.Capacidade;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.ResultadoTeste;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.shared.cripto.CifraSegredos;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Meios de pagamento pelo painel: tipos disponíveis, configuração, teste das credenciais e ativação. */
@Service
public class GatewayAdmService {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayAdmService.class);

    private final GatewayRepository gateways;
    private final GatewaysRegistrados registrados;
    private final ConfiguracaoGatewayService configuracoes;
    private final CifraSegredos cifra;
    private final ContaProperties conta;
    private final Clock clock;

    public GatewayAdmService(
            GatewayRepository gateways,
            GatewaysRegistrados registrados,
            ConfiguracaoGatewayService configuracoes,
            CifraSegredos cifra,
            ContaProperties conta,
            Clock clock) {
        this.gateways = gateways;
        this.registrados = registrados;
        this.configuracoes = configuracoes;
        this.cifra = cifra;
        this.conta = conta;
        this.clock = clock;
    }

    public TiposDisponiveis tipos() {
        return new TiposDisponiveis(
                cifra.disponivel(),
                registrados.todos().stream()
                        .map(g -> new TipoDisponivel(g.tipo(), g.nome(), g.camposConfiguracao(), g.capacidades()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public List<GatewayDetalhe> listar() {
        return gateways.findAllByOrderById().stream().map(this::detalhe).toList();
    }

    @Transactional(readOnly = true)
    public GatewayDetalhe buscar(Long id) {
        return detalhe(gateway(id));
    }

    @Transactional
    public GatewayDetalhe criar(TipoGateway tipo, String nome, Map<String, String> valores) {
        registrados.de(tipo);
        Instant agora = clock.instant();
        Gateway gateway = new Gateway(tipo, nome, TokenSeguro.gerar(), agora);
        configuracoes.configurar(gateway, nome, valores, agora);
        return detalhe(gateways.save(gateway));
    }

    @Transactional
    public GatewayDetalhe atualizar(Long id, String nome, Map<String, String> valores) {
        Gateway gateway = gateway(id);
        configuracoes.configurar(gateway, nome, valores, clock.instant());
        return detalhe(gateway);
    }

    /** Confere as credenciais gravadas com o meio de pagamento. */
    @Transactional(readOnly = true)
    public ResultadoTeste testar(Long id) {
        Gateway gateway = gateway(id);
        try {
            return registrados.de(gateway.getTipo()).testar(configuracoes.ler(gateway));
        } catch (IllegalStateException e) {
            LOG.warn("Teste do gateway {}: {}", id, e.getMessage());
            return ResultadoTeste.falha(e.getMessage());
        }
    }

    /** Passa a ser o meio de pagamento dos novos checkouts; o anterior continua recebendo notificações. */
    @Transactional
    public GatewayDetalhe ativar(Long id) {
        Gateway gateway = gateway(id);
        gateways.desativarOutros(id);
        gateway = gateway(id);
        gateway.ativar(clock.instant());
        gateways.flush();
        return detalhe(gateway);
    }

    @Transactional
    public GatewayDetalhe desativar(Long id) {
        Gateway gateway = gateway(id);
        gateway.desativar(clock.instant());
        return detalhe(gateway);
    }

    private Gateway gateway(Long id) {
        return gateways.findById(id).orElseThrow(() -> new NaoEncontradoException("Meio de pagamento não encontrado."));
    }

    private GatewayDetalhe detalhe(Gateway gateway) {
        GatewayPagamento implementacao = registrados.de(gateway.getTipo());
        Map<String, SegredoResumo> segredos;
        boolean legiveis = true;
        try {
            segredos = configuracoes.resumoSegredos(gateway);
        } catch (IllegalStateException e) {
            // Chave ausente ou trocada: o painel avisa e pede para informar os segredos de novo
            segredos = Map.of();
            legiveis = false;
        }
        return new GatewayDetalhe(
                gateway,
                implementacao.nome(),
                configuracoes.comuns(gateway),
                segredos,
                legiveis,
                conta.urlAluno() + "/api/pagamentos/webhooks/" + gateway.getWebhookToken());
    }

    public record TiposDisponiveis(boolean cifraDisponivel, List<TipoDisponivel> tipos) {}

    public record TipoDisponivel(
            TipoGateway tipo, String nome, List<CampoConfiguracao> campos, Set<Capacidade> capacidades) {}

    /**
     * @param valores campos comuns (os secretos nunca saem; ver {@code segredos})
     * @param segredosLegiveis falso se a chave mudou ou falta: os segredos precisam ser informados de novo
     * @param webhookUrl endereço para cadastrar no meio de pagamento
     */
    public record GatewayDetalhe(
            Gateway gateway,
            String nomeTipo,
            Map<String, String> valores,
            Map<String, SegredoResumo> segredos,
            boolean segredosLegiveis,
            String webhookUrl) {}
}
