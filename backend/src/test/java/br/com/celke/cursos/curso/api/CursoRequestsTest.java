package br.com.celke.cursos.curso.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CursoRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static CursoRequests.Curso curso(String titulo, String slug, String resumo, String descricao) {
        return new CursoRequests.Curso(titulo, slug, resumo, descricao);
    }

    @Test
    @DisplayName("curso válido, com ou sem slug, não tem erros")
    void cursoValido() {
        assertThat(camposInvalidos(curso("Java", "java", "Resumo", "Descrição")))
                .isEmpty();
        assertThat(camposInvalidos(curso("Java", null, "Resumo", null))).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "título em branco,             ' ',   java, Resumo, titulo",
        "resumo em branco,             Java,  java, ' ',    resumo",
        "slug com maiúscula,           Java,  Java, Resumo, slug",
        "slug com espaço,              Java,  'a b', Resumo, slug",
        "slug com hífen duplo,         Java,  a--b, Resumo, slug",
    })
    void camposDoCurso(String caso, String titulo, String slug, String resumo, String campoInvalido) {
        assertThat(camposInvalidos(curso(titulo, slug, resumo, null))).containsExactly(campoInvalido);
    }

    @Test
    @DisplayName("limites de tamanho do curso: título 120, slug 80, resumo 300, descrição 20 mil")
    void tamanhosDoCurso() {
        assertThat(camposInvalidos(curso("a".repeat(120), "a".repeat(80), "a".repeat(300), "a".repeat(20_000))))
                .isEmpty();
        assertThat(camposInvalidos(curso("a".repeat(121), "a".repeat(81), "a".repeat(301), "a".repeat(20_001))))
                .containsExactlyInAnyOrder("titulo", "slug", "resumo", "descricao");
    }

    @Test
    @DisplayName("status, turma, módulo e aula validam os campos obrigatórios")
    void outrosCorpos() {
        assertThat(camposInvalidos(new CursoRequests.AlterarStatusCurso(null))).containsExactly("status");
        assertThat(camposInvalidos(new CursoRequests.AlterarStatusTurma(null))).containsExactly("status");
        assertThat(camposInvalidos(new CursoRequests.AlterarStatusConteudo(null)))
                .containsExactly("status");
        assertThat(camposInvalidos(new CursoRequests.Turma(" "))).containsExactly("nome");
        assertThat(camposInvalidos(new CursoRequests.Turma("a".repeat(121)))).containsExactly("nome");
        assertThat(camposInvalidos(new CursoRequests.Modulo("a".repeat(151)))).containsExactly("titulo");
        assertThat(camposInvalidos(new CursoRequests.Aula("", "a".repeat(20_001), true)))
                .containsExactlyInAnyOrder("titulo", "descricao");
        assertThat(camposInvalidos(new CursoRequests.Aula("Aula", null, false))).isEmpty();
    }

    @Test
    @DisplayName("aula sem o campo gratuita é paga")
    void aulaSemGratuitaEPaga() {
        assertThat(new CursoRequests.Aula("Aula", null, null).dados().gratuita())
                .isFalse();
        assertThat(new CursoRequests.Aula("Aula", null, true).dados().gratuita())
                .isTrue();
    }

    @Test
    @DisplayName("ordem exige de 1 a 500 ids, sem nulos")
    void ordem() {
        assertThat(camposInvalidos(new CursoRequests.Ordem(List.of()))).containsExactly("ids");
        assertThat(camposInvalidos(new CursoRequests.Ordem(null))).containsExactly("ids");
        assertThat(camposInvalidos(new CursoRequests.Ordem(Arrays.asList(1L, null))))
                .containsExactly("ids[1].<list element>");
        List<Long> quinhentos = new ArrayList<>();
        for (long i = 1; i <= 500; i++) {
            quinhentos.add(i);
        }
        assertThat(camposInvalidos(new CursoRequests.Ordem(quinhentos))).isEmpty();
        quinhentos.add(501L);
        assertThat(camposInvalidos(new CursoRequests.Ordem(quinhentos))).containsExactly("ids");
    }

    @ParameterizedTest(name = "tamanho {0}")
    @CsvSource({"0, false", "-1, false", "1, true"})
    void envioDaCapaExigeTamanhoPositivo(long tamanho, boolean valido) {
        Set<String> invalidos = camposInvalidos(new CursoRequests.IniciarEnvioCapa("image/png", tamanho));
        if (valido) {
            assertThat(invalidos).isEmpty();
        } else {
            assertThat(invalidos).containsExactly("tamanho");
        }
    }

    @Test
    @DisplayName("envio e confirmação da capa exigem tipo e chave")
    void capa() {
        assertThat(camposInvalidos(new CursoRequests.IniciarEnvioCapa(" ", 10))).containsExactly("contentType");
        assertThat(camposInvalidos(new CursoRequests.ConfirmarCapa(""))).containsExactly("chave");
        assertThat(camposInvalidos(new CursoRequests.ConfirmarCapa("a".repeat(201))))
                .containsExactly("chave");
    }
}
