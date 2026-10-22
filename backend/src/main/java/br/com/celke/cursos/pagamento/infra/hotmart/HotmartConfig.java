package br.com.celke.cursos.pagamento.infra.hotmart;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Monta a Hotmart como um {@code GatewayPagamento}. */
@Configuration
@EnableConfigurationProperties(HotmartProperties.class)
public class HotmartConfig {

    @Bean
    GatewayHotmart gatewayHotmart(HotmartProperties props, JsonMapper jsonMapper, Clock clock) {
        ClienteApiHotmart api =
                new ClienteApiHotmart(ClienteApiHotmart.restClient(RestClient.builder(), props), props, clock);
        return new GatewayHotmart(new InterpretadorHotmart(jsonMapper), api, clock);
    }
}
