package br.com.celke.cursos.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.fixtures.ImagemFixture;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.infra.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: perfil do próprio usuário — dados sem senha nem hash, troca de nome, troca de senha (atual
 * errada, política, igual à atual, sucesso encerrando as sessões) e foto (reduzida, sem EXIF, troca e
 * remoção; chave de outro usuário recusada); anônimo 401.
 * Não cobre: regras caso a caso (PerfilServiceTest).
 * Dado criado: usuários próprios (o seed é só leitura), fotos em uploads/fotos/ e fotos/.
 */
class PerfilIT extends IntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private StorageService storage;

    @Autowired
    private UsuarioRepository usuarios;

    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarUsuario() {
        aluno = criarUsuario(Papel.ALUNO);
        token = login(aluno.email(), aluno.senha());
    }

    private MvcTestResult chamar(HttpMethod metodo, String uri, String corpo, String tokenUsado) {
        var requisicao = mvc.method(metodo).uri(uri);
        if (corpo != null) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        if (tokenUsado != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(tokenUsado));
        }
        return requisicao.exchange();
    }

    private String enviarFoto(byte[] imagem, String contentType, String tokenUsado) throws Exception {
        String corpo = corpo(chamar(
                HttpMethod.POST,
                "/api/perfil/foto/envio",
                "{\"contentType\":\"%s\",\"tamanho\":%d}".formatted(contentType, imagem.length),
                tokenUsado));
        var put = http.send(
                HttpRequest.newBuilder(URI.create(JsonPath.read(corpo, "$.url")))
                        .header("Content-Type", contentType)
                        .PUT(BodyPublishers.ofByteArray(imagem))
                        .build(),
                BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);
        return JsonPath.read(corpo, "$.chave");
    }

    private static String corpo(MvcTestResult resposta) {
        try {
            return resposta.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("mostra e atualiza o nome, sem expor senha nem hash")
    void dados() {
        MvcTestResult perfil = chamar(HttpMethod.GET, "/api/perfil", null, token);
        assertThat(perfil).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"email":"%s","papeis":["ALUNO"],"fotoUrl":null}
                """.formatted(aluno.email()));
        assertThat(corpo(perfil)).doesNotContain("senha").doesNotContain("hash").doesNotContain("$argon2");

        assertThat(chamar(HttpMethod.PUT, "/api/perfil", "{\"nome\":\"  Ana Paula  \"}", token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.nome")
                .isEqualTo("Ana Paula");
        assertThat(chamar(HttpMethod.PUT, "/api/perfil", "{\"nome\":\" \"}", token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("nome"));
    }

    @Test
    @DisplayName("troca de senha: recusa a atual errada, a curta e a igual; troca e encerra as sessões")
    void trocaDeSenha() {
        String rota = "/api/perfil/senha";
        Cookie refresh = entrar(aluno.email(), aluno.senha()).getResponse().getCookie("refresh_token");

        assertThat(chamar(
                        HttpMethod.PUT,
                        rota,
                        "{\"senhaAtual\":\"errada-123456\",\"novaSenha\":\"nova-senha-forte\"}",
                        token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A senha atual não confere.");
        assertThat(chamar(
                        HttpMethod.PUT,
                        rota,
                        "{\"senhaAtual\":\"%s\",\"novaSenha\":\"curta\"}".formatted(aluno.senha()),
                        token))
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A senha deve ter pelo menos 10 caracteres.");
        assertThat(chamar(
                        HttpMethod.PUT,
                        rota,
                        "{\"senhaAtual\":\"%s\",\"novaSenha\":\"%s\"}".formatted(aluno.senha(), aluno.senha()),
                        token))
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A nova senha precisa ser diferente da atual.");

        MvcTestResult trocada = chamar(
                HttpMethod.PUT,
                rota,
                "{\"senhaAtual\":\"%s\",\"novaSenha\":\"nova-senha-forte\"}".formatted(aluno.senha()),
                token);
        assertThat(trocada).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(trocada.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("refresh_token=")
                .contains("Max-Age=0");

        // A sessão antiga não renova mais; a senha nova entra
        assertThat(mvc.post()
                        .uri("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .cookie(refresh)
                        .exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        entrar(aluno.email(), "nova-senha-forte");
    }

    @Test
    @DisplayName("foto: reduzida e sem EXIF, trocada (apaga a anterior) e removida")
    void foto() throws Exception {
        String envio = enviarFoto(ImagemFixture.jpegComExif(1200, 900, "GPS -23.5505"), "image/jpeg", token);
        MvcTestResult confirmada =
                chamar(HttpMethod.PUT, "/api/perfil/foto", "{\"chave\":\"%s\"}".formatted(envio), token);
        assertThat(confirmada).hasStatusOk();
        assertThat(storage.info(envio)).as("envio apagado").isEmpty();
        String primeira = usuarios.findById(aluno.id()).orElseThrow().getFotoChave();
        assertThat(primeira).matches("fotos/" + aluno.id() + "/[0-9a-f-]{36}\\.jpg");
        byte[] gravada = storage.baixar(primeira);
        var imagem = ImageIO.read(new ByteArrayInputStream(gravada));
        assertThat(imagem.getWidth()).isLessThanOrEqualTo(400);
        assertThat(imagem.getHeight()).isLessThanOrEqualTo(400);
        assertThat(new String(gravada, StandardCharsets.ISO_8859_1)).doesNotContain("GPS -23.5505");
        String url = JsonPath.read(corpo(confirmada), "$.fotoUrl");
        assertThat(http.send(HttpRequest.newBuilder(URI.create(url)).build(), BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(200);

        String segunda = enviarFoto(ImagemFixture.png(50, 50), "image/png", token);
        assertThat(chamar(HttpMethod.PUT, "/api/perfil/foto", "{\"chave\":\"%s\"}".formatted(segunda), token))
                .hasStatusOk();
        assertThat(storage.info(primeira)).as("foto anterior apagada").isEmpty();

        assertThat(chamar(HttpMethod.DELETE, "/api/perfil/foto", null, token)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(chamar(HttpMethod.GET, "/api/perfil", null, token))
                .bodyJson()
                .extractingPath("$.fotoUrl")
                .isNull();
    }

    @Test
    @DisplayName("foto enviada por outro usuário não vira a foto deste (IDOR)")
    void fotoDeOutro() throws Exception {
        UsuarioTeste outro = criarUsuario(Papel.ALUNO);
        String alheia = enviarFoto(ImagemFixture.png(20, 20), "image/png", login(outro.email(), outro.senha()));

        assertThat(chamar(HttpMethod.PUT, "/api/perfil/foto", "{\"chave\":\"%s\"}".formatted(alheia), token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envio de foto inválido. Comece um novo envio.");
        assertThat(storage.info(alheia)).isPresent();
    }

    @Test
    @DisplayName("rotas do perfil exigem token")
    void anonimo() {
        assertThat(chamar(HttpMethod.GET, "/api/perfil", null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.PUT, "/api/perfil/senha", "{}", null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.POST, "/api/perfil/foto/envio", "{}", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
