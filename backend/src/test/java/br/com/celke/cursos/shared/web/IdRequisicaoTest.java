package br.com.celke.cursos.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdRequisicaoTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {"a", "abc-123", "Req_1.2", "0123456789012345678901234567890123456789012345678901234567890123"})
    @DisplayName("mantém o id recebido quando é curto e só tem caracteres seguros")
    void mantemIdValido(String recebido) {
        assertThat(IdRequisicao.normalizar(recebido)).isEqualTo(recebido);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "com espaço",
                "quebra\nde linha",
                "<script>",
                "01234567890123456789012345678901234567890123456789012345678901234"
            })
    @DisplayName("gera um UUID quando o id recebido é vazio, inseguro ou passa de 64 caracteres")
    void geraIdQuandoInvalido(String recebido) {
        String id = IdRequisicao.normalizar(recebido);
        assertThat(id).isNotEqualTo(recebido);
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    @DisplayName("cada id gerado é diferente")
    void idsGeradosDiferentes() {
        assertThat(IdRequisicao.normalizar(null)).isNotEqualTo(IdRequisicao.normalizar(null));
    }
}
