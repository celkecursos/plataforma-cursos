package br.com.celke.cursos.video.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DivisaoPartesTest {

    private static final long MIB = 1024L * 1024;

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "1 byte,                          1,           16777216, 1",
        "exatamente uma parte,            16777216,    16777216, 1",
        "um byte além de uma parte,       16777217,    16777216, 2",
        "100 MiB,                         104857600,   16777216, 7",
        "limite de 10.000 partes de 16 MiB, 167772160000, 16777216, 10000",
        "um byte além do limite,          167772160001, 17825792, 9412",
        "5 GiB,                           5368709120,  16777216, 320",
        "500 GiB,                         536870912000, 54525952, 9847",
    })
    @DisplayName("partes de 16 MiB até 10.000 partes; acima disso, partes maiores em múltiplos de 1 MiB")
    void divisao(String caso, long tamanho, long parte, int quantidade) {
        DivisaoPartes divisao = DivisaoPartes.de(tamanho);

        assertThat(divisao.tamanhoParte()).isEqualTo(parte);
        assertThat(divisao.quantidade()).isEqualTo(quantidade);
        assertThat(divisao.quantidade()).isLessThanOrEqualTo(DivisaoPartes.MAXIMO_PARTES);
        assertThat(divisao.tamanhoParte() % MIB).isZero();
        assertThat(divisao.tamanhoParte() * divisao.quantidade()).isGreaterThanOrEqualTo(tamanho);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("tamanho zero ou negativo é recusado")
    void tamanhoInvalido(long tamanho) {
        assertThatThrownBy(() -> DivisaoPartes.de(tamanho))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O tamanho precisa ser positivo.");
    }
}
