package br.com.celke.cursos.usuario.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PoliticaSenhaTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "um caractere abaixo do mínimo, 11,  'A senha deve ter pelo menos 12 caracteres.'",
        "exatamente o mínimo,           12,  ",
        "exatamente o máximo,           128, ",
        "um caractere acima do máximo,  129, 'A senha deve ter no máximo 128 caracteres.'",
    })
    void validaTamanho(String caso, int tamanho, String esperado) {
        assertThat(PoliticaSenha.validar("a".repeat(tamanho), PoliticaSenha.MINIMO_ADMIN))
                .isEqualTo(esperado);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"             "})
    void senhaObrigatoria(String senha) {
        assertThat(PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ADMIN)).isEqualTo("A senha é obrigatória.");
    }
}
