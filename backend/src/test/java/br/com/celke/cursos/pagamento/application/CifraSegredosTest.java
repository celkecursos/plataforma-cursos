package br.com.celke.cursos.pagamento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CifraSegredosTest {

    private static final String CHAVE = "dGVzdGUtZGEtY2hhdmUtZGUtcGFnYW1lbnRvcy0zMmI=";
    private static final String OUTRA_CHAVE = "b3V0cmEtY2hhdmUtZGUtcGFnYW1lbnRvcy0zMi1ieXQ=";
    private final CifraSegredos cifra = new CifraSegredos(CHAVE);

    @Test
    @DisplayName("ida e volta devolve o texto; o blob não contém o texto")
    void idaEVolta() {
        byte[] blob = cifra.cifrar("{\"hottok\":\"segredo-123\"}", "token-a");

        assertThat(new String(blob, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("segredo-123");
        assertThat(cifra.decifrar(blob, "token-a")).isEqualTo("{\"hottok\":\"segredo-123\"}");
    }

    @Test
    @DisplayName("cada gravação usa um IV diferente")
    void ivDiferente() {
        byte[] primeiro = cifra.cifrar("igual", "token-a");
        byte[] segundo = cifra.cifrar("igual", "token-a");

        assertThat(Arrays.copyOf(primeiro, 12)).isNotEqualTo(Arrays.copyOf(segundo, 12));
        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    @DisplayName("chave errada não decifra")
    void chaveErrada() {
        byte[] blob = cifra.cifrar("segredo", "token-a");

        assertThatThrownBy(() -> new CifraSegredos(OUTRA_CHAVE).decifrar(blob, "token-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Não foi possível decifrar os segredos do meio de pagamento. A PAGAMENTOS_CHAVE mudou?");
    }

    @Test
    @DisplayName("blob de um gateway não decifra em outro (dado associado)")
    void outroGateway() {
        byte[] blob = cifra.cifrar("segredo", "token-a");

        assertThatThrownBy(() -> cifra.decifrar(blob, "token-b")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("blob alterado ou curto demais não decifra")
    void blobInvalido() {
        byte[] blob = cifra.cifrar("segredo", "token-a");
        blob[blob.length - 1] ^= 1;

        assertThatThrownBy(() -> cifra.decifrar(blob, "token-a")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cifra.decifrar(new byte[12], "token-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Segredos gravados em formato inválido.");
        assertThatThrownBy(() -> cifra.decifrar(null, "token-a")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("sem chave a cifra fica indisponível e recusa cifrar e decifrar")
    void semChave() {
        CifraSegredos semChave = new CifraSegredos(" ");

        assertThat(semChave.disponivel()).isFalse();
        assertThat(new CifraSegredos(null).disponivel()).isFalse();
        assertThatThrownBy(() -> semChave.cifrar("a", "b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PAGAMENTOS_CHAVE não está definida.");
        assertThatThrownBy(() -> semChave.decifrar(new byte[20], "b")).isInstanceOf(IllegalStateException.class);
    }
}
