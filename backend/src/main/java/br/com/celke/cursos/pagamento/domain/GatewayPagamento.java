package br.com.celke.cursos.pagamento.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Um meio de pagamento. Cada um é um bean; o núcleo não conhece nenhum pelo nome. */
public interface GatewayPagamento {

    TipoGateway tipo();

    /** Nome mostrado no painel (ex.: "Hotmart"). */
    String nome();

    /** Campos que o painel mostra para configurar (o front monta o formulário a partir daqui). */
    List<CampoConfiguracao> camposConfiguracao();

    /** O que este meio de pagamento sabe fazer. */
    Set<Capacidade> capacidades();

    /** Confere as credenciais com o meio de pagamento (botão "Testar" do painel). */
    ResultadoTeste testar(ConfiguracaoGateway configuracao);

    /** Monta o checkout do pedido: URL para redirecionar ou dados para um formulário embutido. */
    Checkout iniciarCheckout(Pedido pedido, Oferta oferta, Comprador comprador, ConfiguracaoGateway configuracao);

    /** Autentica a notificação recebida (cabeçalhos e corpo crus). */
    boolean autenticar(NotificacaoRecebida notificacao, ConfiguracaoGateway configuracao);

    /**
     * Traduz a notificação para eventos normalizados (uma notificação pode gerar mais de um).
     *
     * @throws NotificacaoInvalidaException se o corpo não é do formato esperado
     */
    List<EventoPagamento> interpretar(NotificacaoRecebida notificacao);

    /** Situação atual da transação no meio de pagamento (reconciliação), se tiver CONSULTA_API. */
    Optional<EventoPagamento> consultar(String transacao, ConfiguracaoGateway configuracao);
}
