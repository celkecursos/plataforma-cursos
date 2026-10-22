package br.com.celke.cursos.video.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.shared.armazenamento.StorageService.ParteEnviada;
import br.com.celke.cursos.video.api.VideoRequests.ConcluirEnvio;
import br.com.celke.cursos.video.api.VideoRequests.IniciarEnvio;
import br.com.celke.cursos.video.api.VideoRequests.Parte;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VideoRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object dto) {
        return VALIDATOR.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Nested
    class Iniciar {

        @Test
        @DisplayName("payload válido passa")
        void valido() {
            assertThat(camposInvalidos(new IniciarEnvio("aula 1.mp4", "video/mp4", 1)))
                    .isEmpty();
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -1})
        @DisplayName("tamanho zero ou negativo é recusado")
        void tamanho(long tamanho) {
            assertThat(camposInvalidos(new IniciarEnvio("a.mp4", "video/mp4", tamanho)))
                    .containsExactly("tamanho");
        }

        @Test
        @DisplayName("nome e tipo obrigatórios; nome com até 255 caracteres")
        void obrigatorios() {
            assertThat(camposInvalidos(new IniciarEnvio(" ", null, 10)))
                    .containsExactlyInAnyOrder("nomeArquivo", "contentType");
            assertThat(camposInvalidos(new IniciarEnvio("a".repeat(256), "video/mp4", 10)))
                    .containsExactly("nomeArquivo");
            assertThat(camposInvalidos(new IniciarEnvio("a".repeat(255), "video/mp4", 10)))
                    .isEmpty();
        }
    }

    @Nested
    class Concluir {

        @Test
        @DisplayName("payload válido passa e vira a lista de partes enviadas")
        void valido() {
            var dto = new ConcluirEnvio(List.of(new Parte(1, "\"abc\""), new Parte(2, "\"def\"")));

            assertThat(camposInvalidos(dto)).isEmpty();
            assertThat(dto.enviadas()).containsExactly(new ParteEnviada(1, "\"abc\""), new ParteEnviada(2, "\"def\""));
        }

        @Test
        @DisplayName("lista vazia ou nula é recusada")
        void listaVazia() {
            assertThat(camposInvalidos(new ConcluirEnvio(List.of()))).containsExactly("partes");
            assertThat(camposInvalidos(new ConcluirEnvio(null))).containsExactly("partes");
        }

        @Test
        @DisplayName("mais de 10.000 partes é recusado")
        void partesDemais() {
            List<Parte> partes = new ArrayList<>(Collections.nCopies(10_001, new Parte(1, "e")));

            assertThat(camposInvalidos(new ConcluirEnvio(partes))).containsExactly("partes");
        }

        @Test
        @DisplayName("cada parte: número de 1 a 10.000 e ETag obrigatório")
        void parte() {
            assertThat(camposInvalidos(new ConcluirEnvio(List.of(new Parte(0, "e")))))
                    .containsExactly("partes[0].numero");
            assertThat(camposInvalidos(new ConcluirEnvio(List.of(new Parte(10_001, "e")))))
                    .containsExactly("partes[0].numero");
            assertThat(camposInvalidos(new ConcluirEnvio(List.of(new Parte(10_000, " ")))))
                    .containsExactly("partes[0].etag");
            assertThat(camposInvalidos(new ConcluirEnvio(List.of(new Parte(1, "e".repeat(201))))))
                    .containsExactly("partes[0].etag");
        }
    }
}
