package br.com.celke.cursos.usuario.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PoliticaSenhaTest {

    /** Texto sem sequência, repetição nem palavra comum, do tamanho pedido. */
    private static String imprevisivel(int tamanho) {
        String base = "Vx7#qLm2!Rt9@pZ4$wK8%nB3&";
        return base.repeat(tamanho / base.length() + 1).substring(0, tamanho);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "um caractere abaixo do mínimo, 11,  'A senha deve ter pelo menos 12 caracteres.'",
        "exatamente o mínimo,           12,  ",
        "exatamente o máximo,           128, ",
        "um caractere acima do máximo,  129, 'A senha deve ter no máximo 128 caracteres.'",
    })
    void validaTamanho(String caso, int tamanho, String esperado) {
        assertThat(PoliticaSenha.validar(imprevisivel(tamanho), PoliticaSenha.MINIMO_ADMIN))
                .isEqualTo(esperado);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"             "})
    void senhaObrigatoria(String senha) {
        assertThat(PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ADMIN)).isEqualTo("A senha é obrigatória.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"123456789012", "1234567890", "qwertyuiop", "QWERTYUIOP", "password123"})
    @DisplayName("senha da lista de comuns e vazadas, sem diferenciar maiúsculas, é recusada")
    void comum(String senha) {
        assertThat(PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ALUNO)).isEqualTo(PoliticaSenha.COMUM);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "contém o usuário do e-mail, mariana.silva2026,  Mariana Silva,       mariana.silva@celke.com.br",
        "contém uma palavra do nome, xablau-OLIVEIRA-77, João de Oliveira,    joao@celke.com.br",
        "contém o e-mail inteiro,    joao@celke.com.br!, João,                joao@celke.com.br",
    })
    @DisplayName("senha com o nome ou o e-mail da pessoa é recusada")
    void pessoal(String caso, String senha, String nome, String email) {
        assertThat(PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ALUNO, nome, email))
                .isEqualTo(PoliticaSenha.PESSOAL);
    }

    @Test
    @DisplayName("trechos curtos do nome não contam e dados vazios são ignorados")
    void trechosCurtos() {
        assertThat(PoliticaSenha.validar("bananada-Vx7#q", PoliticaSenha.MINIMO_ALUNO, "Ana Lu", null, " "))
                .isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "%%%%%%%%%%%%",
                "abababababab",
                "xyzxyzxyzxyz",
                "Vx7#Vx7#Vx7#",
                "lkjhgfdsapoi",
                "zyxwvutsrqpo",
                "6789012345678",
                "7ujm8ik9ol0p"
            })
    @DisplayName("sequência de teclado ou de alfabeto e repetição são recusadas")
    void previsivel(String senha) {
        assertThat(PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ALUNO)).isEqualTo(PoliticaSenha.PREVISIVEL);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"123456A#b123", "minha-nova-senha", "senha-forte-do-teste-e2e", "cavalo correto bateria"})
    @DisplayName("senhas longas e fora dos padrões passam")
    void aceitas(String senha) {
        assertThat(PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ADMIN, "Ana Souza", "ana@celke.com.br"))
                .isNull();
    }
}
