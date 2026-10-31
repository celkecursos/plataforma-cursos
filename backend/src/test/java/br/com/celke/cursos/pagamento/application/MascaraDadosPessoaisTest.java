package br.com.celke.cursos.pagamento.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class MascaraDadosPessoaisTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("mascara documento, telefone e IP em qualquer profundidade; o resto fica igual")
    void mascaraCorpo() {
        var arvore = json.readTree("""
                {"data":{"buyer":{"email":"a@b.c","document":"12345678909","checkout_phone":"5511999990000"},
                 "purchase":{"buyer_ip":"200.1.2.3","transaction":"HP1"},
                 "lista":[{"doc":"987"}], "producer":{"document":null}}}
                """);

        String mascarado = json.writeValueAsString(MascaraDadosPessoais.mascarar(arvore));

        assertThat(mascarado)
                .contains("\"email\":\"a@b.c\"")
                .contains("\"document\":\"***09\"")
                .contains("\"checkout_phone\":\"***00\"")
                .contains("\"buyer_ip\":\"***.3\"")
                .contains("\"transaction\":\"HP1\"")
                .contains("\"doc\":\"***87\"")
                .contains("\"document\":null")
                .doesNotContain("12345678909", "5511999990000", "200.1.2.3");
    }

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource(
            value = {"12345678909, ***09", "12, ***", "1, ***", "'  ab12 ', ***12"},
            nullValues = "nulo")
    void mascaraValor(String valor, String esperado) {
        assertThat(MascaraDadosPessoais.mascarar(valor)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("vazio e nulo ficam como estão")
    void vazio() {
        assertThat(MascaraDadosPessoais.mascarar((String) null)).isNull();
        assertThat(MascaraDadosPessoais.mascarar(" ")).isEqualTo(" ");
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
        "document, true",
        "document_type, true",
        "CPF, true",
        "checkout_phone, true",
        "buyer_ip, true",
        "ip, true",
        "email, false",
        "zipcode, false",
        "shipping, false",
        "product, false",
    })
    void camposPessoais(String campo, boolean pessoal) {
        assertThat(MascaraDadosPessoais.pessoal(campo)).isEqualTo(pessoal);
    }
}
