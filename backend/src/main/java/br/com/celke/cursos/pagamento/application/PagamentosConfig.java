package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.TransicoesPedido;
import br.com.celke.cursos.shared.cripto.CifraSegredos;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PagamentosProperties.class)
public class PagamentosConfig {

    @Bean
    CifraSegredos cifraSegredos(PagamentosProperties props) {
        return new CifraSegredos(props.chave(), "PAGAMENTOS_CHAVE");
    }

    @Bean
    TransicoesPedido transicoesPedido(PagamentosProperties props) {
        return new TransicoesPedido(props.disputaSuspende());
    }
}
