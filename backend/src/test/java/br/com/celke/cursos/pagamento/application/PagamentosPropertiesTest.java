package br.com.celke.cursos.pagamento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class PagamentosPropertiesTest {

    private static PagamentosProperties com(String chave) {
        return new PagamentosProperties(
                chave,
                DataSize.ofKilobytes(256),
                600,
                Duration.ofDays(180),
                false,
                null,
                new PagamentosProperties.Reconciliacao(
                        false, Duration.ofHours(24), Duration.ofDays(3), Duration.ofDays(60)));
    }

    @Test
    @DisplayName("chave em branco é aceita (a API sobe sem gravar segredos); 32 bytes em Base64 também")
    void aceitas() {
        assertThat(com(null).chave()).isNull();
        assertThat(com("").chave()).isEmpty();
        assertThat(com("dGVzdGUtZGEtY2hhdmUtZGUtcGFnYW1lbnRvcy0zMmI=").chave()).isNotBlank();
    }

    @Test
    @DisplayName("chave fora de Base64 ou com outro tamanho impede a subida")
    void recusadas() {
        assertThatThrownBy(() -> com("não é base64!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PAGAMENTOS_CHAVE precisa estar em Base64.");
        assertThatThrownBy(() -> com("Y3VydGE="))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PAGAMENTOS_CHAVE precisa ter 32 bytes (openssl rand -base64 32); tem 5.");
    }
}
