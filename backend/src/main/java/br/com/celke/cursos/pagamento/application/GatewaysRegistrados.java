package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Os meios de pagamento disponíveis (um bean por tipo). Dois beans do mesmo tipo impedem a subida. */
@Component
public class GatewaysRegistrados {

    private final Map<TipoGateway, GatewayPagamento> porTipo = new EnumMap<>(TipoGateway.class);

    public GatewaysRegistrados(List<GatewayPagamento> gateways) {
        for (GatewayPagamento gateway : gateways) {
            GatewayPagamento anterior = porTipo.put(gateway.tipo(), gateway);
            if (anterior != null) {
                throw new IllegalStateException("Dois meios de pagamento do tipo " + gateway.tipo() + ": "
                        + anterior.getClass().getName() + " e "
                        + gateway.getClass().getName());
            }
        }
    }

    /** @throws IllegalStateException se o tipo gravado no banco não tem implementação nesta versão */
    public GatewayPagamento de(TipoGateway tipo) {
        GatewayPagamento gateway = porTipo.get(tipo);
        if (gateway == null) {
            throw new IllegalStateException("Meio de pagamento sem implementação: " + tipo);
        }
        return gateway;
    }

    public List<GatewayPagamento> todos() {
        return porTipo.values().stream()
                .sorted(Comparator.comparing(GatewayPagamento::tipo))
                .toList();
    }
}
