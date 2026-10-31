package br.com.celke.cursos.curso.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "acentos e cedilha viram letras simples | Programação Ágil       | programacao-agil",
                "símbolos viram um único hífen          | Java & Spring: do Zero! | java-spring-do-zero",
                "hífens das pontas são removidos        | --Docker--             | docker",
                "números são mantidos                   | React 19 em 2026       | react-19-em-2026",
                "só símbolos gera slug vazio            | !!!                    | ''",
            })
    void geraAPartirDoTitulo(String caso, String titulo, String esperado) {
        assertThat(Slug.gerar(titulo)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("título longo é cortado em 80 caracteres sem terminar em hífen")
    void cortaNoTamanhoMaximo() {
        String titulo = "a".repeat(79) + " bcd";
        String slug = Slug.gerar(titulo);
        assertThat(slug).isEqualTo("a".repeat(79));
        assertThat(Slug.valido(slug)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
        "java-spring, true",
        "curso2026,   true",
        "Java,        false",
        "java--spring, false",
        "-java,       false",
        "java-,       false",
        "java_spring, false",
        "'',          false",
    })
    void validaOFormato(String slug, boolean esperado) {
        assertThat(Slug.valido(slug)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("nulo e acima de 80 caracteres são inválidos; 80 é o limite")
    void validaTamanho() {
        assertThat(Slug.valido(null)).isFalse();
        assertThat(Slug.valido("a".repeat(80))).isTrue();
        assertThat(Slug.valido("a".repeat(81))).isFalse();
    }
}
