package br.com.celke.cursos.certificado.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CertificadoDominioTest {

    @Nested
    class Regra {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "um ponto abaixo do mínimo,      79, 80,  10, false",
            "exatamente o mínimo,            80, 80,  10, true",
            "acima do mínimo,                95, 80,  10, true",
            "mínimo de 100 com 99,           99, 100, 10, false",
            "mínimo de 100 com todas,        100, 100, 10, true",
            "mínimo de 1 sem nenhuma vista,  0, 1,    10, false",
            "turma sem aulas nunca conclui,  0, 1,    0,  false",
        })
        @DisplayName("concluiu quando as aulas vistas alcançam o mínimo da turma")
        void conclusao(String caso, int visto, int minimo, int total, boolean esperado) {
            assertThat(RegraConclusao.concluiu(visto, minimo, total)).isEqualTo(esperado);
        }
    }

    @Nested
    class Codigo {

        @Test
        @DisplayName("12 caracteres do alfabeto sem ambíguos, em três grupos, diferentes a cada geração")
        void gerar() {
            Set<String> gerados = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                String codigo = CodigoValidacao.gerar();
                assertThat(codigo)
                        .matches("[23456789ABCDEFGHJKMNPQRSTVWXYZ]{4}(-[23456789ABCDEFGHJKMNPQRSTVWXYZ]{4}){2}");
                gerados.add(codigo);
            }
            assertThat(gerados).hasSize(1000);
        }

        @Test
        @DisplayName("normaliza o código digitado: espaços nas pontas e minúsculas")
        void normalizar() {
            assertThat(CodigoValidacao.normalizar("  k7qm-2xrp-h9td ")).isEqualTo("K7QM-2XRP-H9TD");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "K7QM2XRPH9TD", "K7QM-2XRP-H9T", "K7QM-2XRP-H9TO", "K7QM-2XRP-H9T1", "' OR 1=1 --"})
        @DisplayName("formato inválido (tamanho, sem hífens, letras ambíguas, injeção) vira nulo")
        void invalido(String digitado) {
            assertThat(CodigoValidacao.normalizar(digitado)).isNull();
        }
    }

    @Nested
    class Nome {

        @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
        @CsvSource({
            "'Ana Souza',                     'Ana Souza'",
            "'  João   da  Silva  ',          'João da Silva'",
            "'Maria D''Ávila Gonçalves-Lima', 'Maria D''Ávila Gonçalves-Lima'",
        })
        @DisplayName("nome completo aceito, com espaços normalizados")
        void aceito(String informado, String esperado) {
            assertThat(NomeCertificado.normalizar(informado)).isEqualTo(esperado);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"Ana", "A B", "Ana Souza 2", "<b>Ana</b> Souza", "Ana_Souza Lima"})
        @DisplayName("sem sobrenome, curto demais, com número ou símbolos: recusado")
        void recusado(String informado) {
            assertThat(NomeCertificado.normalizar(informado)).isNull();
        }

        @Test
        @DisplayName("nome com mais de 150 caracteres é recusado")
        void longo() {
            assertThat(NomeCertificado.normalizar("Ana " + "a".repeat(147))).isNull();
            assertThat(NomeCertificado.normalizar("Ana " + "a".repeat(146))).isNotNull();
        }
    }
}
