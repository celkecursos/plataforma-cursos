package br.com.celke.cursos.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Cobre: varredura de TODAS as rotas registradas no Spring MVC — sem login, só as rotas públicas
 * declaradas aqui respondem sem pedir autenticação; com token de ALUNO, toda rota {@code /api/adm/**}
 * responde 403. Uma rota nova esquecida fora da proteção quebra este teste.
 * Não cobre: regras de cada rota (os *AcessoIT de cada módulo).
 * Dado criado: nenhum (ids inexistentes; a segurança responde antes da regra).
 */
class RotasProtegidasIT extends IntegrationTest {

    /** Rotas que respondem sem login, de propósito (método + padrão da rota). */
    static final Set<String> PUBLICAS = Set.of(
            "POST /api/auth/login",
            "POST /api/auth/segundo-fator",
            "POST /api/auth/segundo-fator/cadastro",
            "POST /api/auth/refresh",
            "POST /api/auth/logout",
            "POST /api/auth/cadastro",
            "POST /api/auth/confirmar-email",
            "POST /api/auth/reenviar-confirmacao",
            "POST /api/auth/recuperar-senha",
            "POST /api/auth/redefinir-senha",
            "GET /api/cursos",
            "GET /api/cursos/{slug}",
            "GET /api/cursos/{slug}/capa",
            "GET /api/termos/vigentes",
            "GET /api/certificados/{codigo}",
            "POST /api/pagamentos/webhooks/{webhookToken}",
            "GET /api/pagamentos/pedidos/{codigo}");

    private static final String DETALHE_401 = "Autenticação necessária.";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeamentos;

    record Rota(HttpMethod metodo, String padrao) {

        String chave() {
            return metodo.name() + " " + padrao;
        }

        /** Caminho concreto: variáveis trocadas por valores que não existem. */
        String caminho() {
            return padrao.replaceAll("\\{variante[^}]*}", "720p")
                    .replaceAll("\\{(slug|codigo|webhookToken)}", "nao-existe")
                    .replaceAll("\\{[^}]+}", "999999999");
        }
    }

    private List<Rota> rotas() {
        List<Rota> rotas = new ArrayList<>();
        mapeamentos.getHandlerMethods().forEach((info, metodo) -> {
            Set<String> padroes = info.getPatternValues();
            Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
            for (String padrao : padroes) {
                if (!padrao.startsWith("/api/")) {
                    continue;
                }
                for (RequestMethod m : metodos) {
                    rotas.add(new Rota(HttpMethod.valueOf(m.name()), padrao));
                }
            }
        });
        return rotas;
    }

    private MvcTestResult chamar(Rota rota, String token) {
        var requisicao = mvc.method(rota.metodo()).uri(rota.caminho());
        if (rota.metodo() != HttpMethod.GET && rota.metodo() != HttpMethod.DELETE) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        if (token != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao.exchange();
    }

    private static boolean pediuLogin(MvcTestResult resposta) throws Exception {
        return resposta.getResponse().getStatus() == 401
                && resposta.getResponse().getContentAsString().contains(DETALHE_401);
    }

    @Test
    @DisplayName("sem login, toda rota fora da lista pública pede autenticação (401)")
    void anonimo() throws Exception {
        List<Rota> rotas = rotas();
        assertThat(rotas).hasSizeGreaterThan(100);
        Set<String> abertas = new TreeSet<>();
        Set<String> publicasQuePedemLogin = new TreeSet<>();
        for (Rota rota : rotas) {
            boolean pediu = pediuLogin(chamar(rota, null));
            if (PUBLICAS.contains(rota.chave())) {
                if (pediu) {
                    publicasQuePedemLogin.add(rota.chave());
                }
            } else if (!pediu) {
                abertas.add(rota.chave());
            }
        }
        assertThat(abertas).as("rotas restritas abertas sem login").isEmpty();
        assertThat(publicasQuePedemLogin).as("rotas públicas que pedem login").isEmpty();
        assertThat(rotas.stream().map(Rota::chave)).containsAll(PUBLICAS);
    }

    @Test
    @DisplayName("com token de ALUNO, toda rota /api/adm/** responde 403")
    void alunoNoAdm() throws Exception {
        String aluno = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);
        Set<String> liberadas = new TreeSet<>();
        int total = 0;
        for (Rota rota : rotas()) {
            if (!rota.padrao().startsWith("/api/adm/")) {
                continue;
            }
            total++;
            if (chamar(rota, aluno).getResponse().getStatus() != 403) {
                liberadas.add(rota.chave());
            }
        }
        assertThat(total).isGreaterThan(60);
        assertThat(liberadas).as("rotas do ADM liberadas para aluno").isEmpty();
    }

    @Test
    @DisplayName("fora da API: actuator só expõe o health; documentação desligada")
    void foraDaApi() throws Exception {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
        for (String caminho : List.of(
                "/actuator", "/actuator/env", "/actuator/heapdump", "/actuator/configprops", "/actuator/loggers")) {
            int status = mvc.get().uri(caminho).exchange().getResponse().getStatus();
            assertThat(status).as(caminho).isIn(401, 404);
        }
        for (String caminho : List.of("/v3/api-docs", "/swagger-ui.html")) {
            assertThat(mvc.get().uri(caminho).exchange().getResponse().getStatus())
                    .as(caminho)
                    .isIn(404, 302);
        }
    }
}
