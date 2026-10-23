package br.com.celke.cursos.pagamento.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CampoConfiguracaoTest {

    private static final List<String> HOTMART = List.of("pay.hotmart.com", "sandbox.hotmart.com");

    @ParameterizedTest(name = "{0}")
    @CsvSource({"https://pay.hotmart.com", "https://sandbox.hotmart.com/", "https://PAY.hotmart.com/x"})
    @DisplayName("endereço HTTPS de um domínio permitido passa")
    void aceita(String url) {
        assertThat(CampoConfiguracao.erroUrl(url, HOTMART)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http://pay.hotmart.com,             'Informe um endereço começando com https://.'",
        "https://pay.hotmart.com.golpe.io,   'O endereço precisa ser de pay.hotmart.com ou sandbox.hotmart.com.'",
        "https://pay.hotmart.com@golpe.io,   'Informe um endereço começando com https://.'",
        "https://user@pay.hotmart.com,       'Informe um endereço começando com https://.'",
        "javascript:alert(1),                'Informe um endereço começando com https://.'",
        "nao é url,                          'Informe um endereço começando com https://.'",
    })
    @DisplayName("HTTP, outro domínio, credencial na URL e lixo são recusados")
    void recusa(String url, String esperado) {
        assertThat(CampoConfiguracao.erroUrl(url, HOTMART)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("sem lista de domínios, vale qualquer HTTPS")
    void semLista() {
        assertThat(CampoConfiguracao.erroUrl("https://qualquer.exemplo", List.of()))
                .isNull();
    }
}
