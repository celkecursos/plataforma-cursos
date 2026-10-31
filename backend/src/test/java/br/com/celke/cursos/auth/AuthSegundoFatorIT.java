package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.auth.application.SegundoFatorService;
import br.com.celke.cursos.auth.domain.Totp;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Segundo fator dos administradores: cadastro no primeiro acesso, código do aplicativo, códigos de
 * recuperação de uso único, código errado e o aluno que entra sem segunda etapa.
 */
class AuthSegundoFatorIT extends IntegrationTest {

    @Autowired
    private SegundoFatorService segundoFatorService;

    @Autowired
    private JdbcClient jdbc;

    private MvcTestResult login(UsuarioTeste usuario) {
        return mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(usuario.email(), usuario.senha()))
                .exchange();
    }

    private MvcTestResult post(String rota, String corpo) {
        return mvc.post()
                .uri(rota)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
                .exchange();
    }

    private static String ler(MvcTestResult resposta, String caminho) throws Exception {
        return JsonPath.read(resposta.getResponse().getContentAsString(), caminho);
    }

    @Test
    @DisplayName(
            "primeiro acesso: senha certa dá o desafio sem cookie, o QR code e, com o código, a sessão e os códigos")
    void cadastroNoPrimeiroAcesso() throws Exception {
        UsuarioTeste admin = criarUsuario(Papel.ADMIN);

        MvcTestResult primeira = login(admin);
        assertThat(primeira).hasStatusOk();
        assertThat(primeira.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(ler(primeira, "$.segundoFator")).isEqualTo("CADASTRO");
        String desafio = ler(primeira, "$.desafio");

        MvcTestResult cadastro = post("/api/auth/segundo-fator/cadastro", "{\"desafio\":\"%s\"}".formatted(desafio));
        assertThat(cadastro).hasStatusOk();
        String segredo = ler(cadastro, "$.segredo");
        assertThat(ler(cadastro, "$.uri")).startsWith("otpauth://totp/Plataforma%20de%20Cursos:");

        MvcTestResult errado =
                post("/api/auth/segundo-fator", "{\"desafio\":\"%s\",\"codigo\":\"000000\"}".formatted(desafio));
        assertThat(errado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(errado).bodyJson().extractingPath("$.erros[0].campo").isEqualTo("codigo");

        String codigo = Totp.codigo(segredo, Totp.passo(Instant.now()));
        MvcTestResult certo =
                post("/api/auth/segundo-fator", "{\"desafio\":\"%s\",\"codigo\":\"%s\"}".formatted(desafio, codigo));
        assertThat(certo).hasStatusOk();
        assertThat(certo.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("HttpOnly");
        List<String> codigos = JsonPath.read(certo.getResponse().getContentAsString(), "$.codigosRecuperacao");
        assertThat(codigos).hasSize(10).allMatch(c -> c.matches("[a-z2-9]{5}-[a-z2-9]{5}"));

        // O desafio só vale uma vez
        assertThat(post("/api/auth/segundo-fator", "{\"desafio\":\"%s\",\"codigo\":\"%s\"}".formatted(desafio, codigo)))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        // Próximo login pede só o código; o cadastro de outro aplicativo é recusado
        MvcTestResult segunda = login(admin);
        assertThat(ler(segunda, "$.segundoFator")).isEqualTo("CODIGO");
        assertThat(post(
                        "/api/auth/segundo-fator/cadastro",
                        "{\"desafio\":\"%s\"}".formatted(ler(segunda, "$.desafio"))))
                .hasStatus(HttpStatus.CONFLICT);

        // Código de recuperação vale uma vez, com ou sem o hífen e em maiúsculas
        String recuperacao = codigos.get(0);
        MvcTestResult comRecuperacao = post(
                "/api/auth/segundo-fator",
                "{\"desafio\":\"%s\",\"codigoRecuperacao\":\"%s\"}"
                        .formatted(
                                ler(segunda, "$.desafio"),
                                recuperacao.replace("-", "").toUpperCase()));
        assertThat(comRecuperacao).hasStatusOk();
        assertThat(comRecuperacao).bodyJson().hasPath("$.accessToken");
        MvcTestResult terceira = login(admin);
        assertThat(post(
                        "/api/auth/segundo-fator",
                        "{\"desafio\":\"%s\",\"codigoRecuperacao\":\"%s\"}"
                                .formatted(ler(terceira, "$.desafio"), recuperacao)))
                .hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(jdbc.sql("""
                        SELECT resultado FROM auditoria_login WHERE usuario_id = ? ORDER BY id
                        """).param(admin.id()).query(String.class).list())
                .containsSubsequence(
                        "SEGUNDO_FATOR_PENDENTE",
                        "SEGUNDO_FATOR_INVALIDO",
                        "SEGUNDO_FATOR_CADASTRADO",
                        "SEGUNDO_FATOR_PENDENTE",
                        "CODIGO_RECUPERACAO",
                        "SEGUNDO_FATOR_PENDENTE",
                        "SEGUNDO_FATOR_INVALIDO");
    }

    @Test
    @DisplayName("o segredo fica cifrado no banco e redefinir volta para o cadastro")
    void segredoCifradoERedefinir() throws Exception {
        UsuarioTeste admin = criarUsuario(Papel.ADMIN);
        entrar(admin.email(), admin.senha());

        byte[] gravado = jdbc.sql("SELECT segredo FROM segundo_fator WHERE usuario_id = ?")
                .param(admin.id())
                .query(byte[].class)
                .single();
        // Cifrado (IV + texto + tag), não o Base32 de 32 caracteres que o aplicativo recebe
        assertThat(gravado).hasSizeGreaterThan(32);
        assertThat(new String(gravado, StandardCharsets.ISO_8859_1)).doesNotMatch("[A-Z2-7]+");

        assertThat(segundoFatorService.redefinir(admin.id())).isTrue();
        assertThat(ler(login(admin), "$.segundoFator")).isEqualTo("CADASTRO");
    }

    @Test
    @DisplayName("aluno entra direto, sem segunda etapa")
    void alunoSemSegundaEtapa() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);

        assertThat(login(aluno)).bodyJson().hasPath("$.accessToken").doesNotHavePath("$.segundoFator");
    }

    @Test
    @DisplayName("sem código nem código de recuperação, a validação recusa")
    void semCodigo() {
        assertThat(post("/api/auth/segundo-fator", "{\"desafio\":\"qualquer\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
