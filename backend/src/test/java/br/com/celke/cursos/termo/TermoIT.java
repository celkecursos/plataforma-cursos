package br.com.celke.cursos.termo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.termo.infra.AceiteTermoRepository;
import br.com.celke.cursos.termo.infra.TermoRepository;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
 * Cobre: termos versionados — publicação pelo ADM (versões, HTML filtrado, matriz de papéis), vigentes
 * públicos, pendentes do usuário, bloqueio da área do aluno e da entrega de vídeo até o aceite (403 com
 * a lista), aceite (com IP), versão antiga recusada, perfil e sessão liberados durante a pendência.
 * Não cobre: regras caso a caso (TermoServiceTest, TermoPendenteInterceptorTest).
 * Dado criado: termos e aceites — apagados ao fim de cada teste, porque um termo vigente bloquearia os
 * alunos das outras classes de teste, que compartilham o banco.
 */
class TermoIT extends IntegrationTest {

    @Autowired
    private TermoRepository termos;

    @Autowired
    private AceiteTermoRepository aceites;

    private String tokenAdmin;
    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarAluno() {
        tokenAdmin = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
        aluno = criarUsuario(Papel.ALUNO);
        token = login(aluno.email(), aluno.senha());
    }

    @AfterEach
    void apagarTermos() {
        aceites.deleteAll();
        termos.deleteAll();
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

    private long publicar(String tipo, String conteudo) {
        return CursoITHelper.idCriado(chamar(
                HttpMethod.POST,
                "/api/adm/termos",
                "{\"tipo\":\"%s\",\"conteudo\":\"%s\"}".formatted(tipo, conteudo),
                tokenAdmin));
    }

    @Test
    @DisplayName("termo novo bloqueia a área do aluno até o aceite; aceite libera; versão nova bloqueia de novo")
    void bloqueioAteOAceite() {
        assertThat(chamar(HttpMethod.GET, "/api/aluno/cursos", null, token))
                .as("sem termos, livre")
                .hasStatusOk();

        long uso = publicar("TERMOS_USO", "Uso **v1** <script>x</script>");
        long privacidade = publicar("POLITICA_PRIVACIDADE", "Privacidade v1");

        MvcTestResult bloqueado = chamar(HttpMethod.GET, "/api/aluno/cursos", null, token);
        assertThat(bloqueado).hasStatus(HttpStatus.FORBIDDEN);
        var json = assertThat(bloqueado).bodyJson();
        json.extractingPath("$.detail").isEqualTo("Aceite os termos atualizados para continuar.");
        json.extractingPath("$.termosPendentes[*].id").isEqualTo(List.of((int) privacidade, (int) uso));
        assertThat(chamar(HttpMethod.GET, "/api/aulas/1/video.m3u8", null, token))
                .hasStatus(HttpStatus.FORBIDDEN);

        // Perfil, sessão e as rotas de termos continuam livres para o aceite ser possível
        assertThat(chamar(HttpMethod.GET, "/api/perfil", null, token)).hasStatusOk();
        assertThat(chamar(HttpMethod.GET, "/api/auth/me", null, token)).hasStatusOk();
        assertThat(chamar(HttpMethod.GET, "/api/termos/pendentes", null, token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].conteudoHtml")
                .asString()
                .doesNotContain("<script");

        assertThat(chamar(HttpMethod.POST, "/api/termos/" + uso + "/aceite", null, token))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(chamar(HttpMethod.GET, "/api/aluno/cursos", null, token))
                .as("ainda falta a privacidade")
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.POST, "/api/termos/" + privacidade + "/aceite", null, token))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(chamar(HttpMethod.GET, "/api/aluno/cursos", null, token)).hasStatusOk();
        assertThat(aceites.existsByUsuarioIdAndTermoId(aluno.id(), uso)).isTrue();

        // Versão 2 dos termos de uso: bloqueia de novo; a versão 1 não pode mais ser aceita
        long uso2 = publicar("TERMOS_USO", "Uso v2");
        assertThat(chamar(HttpMethod.GET, "/api/aluno/cursos", null, token)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.POST, "/api/termos/" + uso + "/aceite", null, token))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Esta versão não é mais a vigente. Aceite a versão atual.");
        assertThat(chamar(HttpMethod.POST, "/api/termos/" + uso2 + "/aceite", null, token))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(chamar(HttpMethod.GET, "/api/aluno/cursos", null, token)).hasStatusOk();
    }

    @Test
    @DisplayName("vigentes são públicos; o histórico do ADM lista as versões da mais nova para a mais antiga")
    void vigentesEHistorico() {
        publicar("TERMOS_USO", "v1");
        publicar("TERMOS_USO", "v2");

        assertThat(chamar(HttpMethod.GET, "/api/termos/vigentes", null, null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].versao")
                .isEqualTo(List.of(2));
        String historico = corpo(chamar(HttpMethod.GET, "/api/adm/termos?tipo=TERMOS_USO", null, tokenAdmin));
        assertThat(JsonPath.<List<Integer>>read(historico, "$[*].versao")).containsExactly(2, 1);
    }

    @Test
    @DisplayName("publicar e listar versões é só do ADM; aceitar exige token; validação 400")
    void acesso() {
        String corpo = "{\"tipo\":\"TERMOS_USO\",\"conteudo\":\"x\"}";

        assertThat(chamar(HttpMethod.POST, "/api/adm/termos", corpo, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.POST, "/api/adm/termos", corpo, token)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.GET, "/api/adm/termos?tipo=TERMOS_USO", null, token))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.GET, "/api/termos/pendentes", null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.POST, "/api/termos/1/aceite", null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.POST, "/api/adm/termos", "{\"conteudo\":\" \"}", tokenAdmin))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("conteudo", "tipo"));
        assertThat(chamar(HttpMethod.POST, "/api/termos/999999999/aceite", null, token))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private static String corpo(MvcTestResult resposta) {
        return CursoITHelper.corpo(resposta);
    }
}
