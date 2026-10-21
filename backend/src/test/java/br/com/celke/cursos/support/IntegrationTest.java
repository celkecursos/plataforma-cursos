package br.com.celke.cursos.support;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.shared.fila.ProcessadorFila;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Base de todos os testes de integração (*IT). Não altere para atender um teste específico:
 * qualquer variação cria outro contexto Spring e deixa a suíte lenta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(Containers.class)
public abstract class IntegrationTest {

    /** Senha dos usuários criados por {@link #criarUsuario(Papel...)}. */
    protected static final String SENHA_TESTE = "senha-do-usuario-de-teste";

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProcessadorFila processadorFila;

    /** Identificador único em runtime para os dados criados pelo teste (prefixo {@code it-}). */
    protected static String stamp() {
        return "it-" + Long.toString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), 36);
    }

    /** Faz login, confere o 200 e devolve a resposta (corpo com o access token e cookie de refresh). */
    protected MvcTestResult entrar(String email, String senha) {
        MvcTestResult resposta = mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .exchange();
        assertThat(resposta).hasStatusOk();
        return resposta;
    }

    /** Faz login e devolve o access token. */
    protected String login(String email, String senha) {
        return accessToken(entrar(email, senha));
    }

    protected static String accessToken(MvcTestResult resposta) {
        try {
            return JsonPath.read(resposta.getResponse().getContentAsString(), "$.accessToken");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Processa todos os jobs pendentes (no worker isso é feito pelo consumo agendado). */
    protected void processarFila() {
        while (processadorFila.processarProximo()) {
            // continua enquanto houver job disponível
        }
    }

    protected static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    /**
     * Cria um usuário próprio do teste (e-mail com {@code stamp}), para fluxos que alteram a sessão
     * ou os dados do usuário — os usuários do seed são só leitura.
     */
    protected UsuarioTeste criarUsuario(Papel... papeis) {
        String email = stamp() + "@teste.celke.uno";
        var usuario = usuarioService.criarConfirmado("Usuário " + email, email, SENHA_TESTE, Set.of(papeis));
        return new UsuarioTeste(usuario.getId(), email, SENHA_TESTE);
    }

    protected record UsuarioTeste(Long id, String email, String senha) {}
}
