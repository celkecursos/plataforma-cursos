package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Cobre: catálogo público — só cursos publicados na lista e no detalhe; o detalhe mostra só os
 * módulos e as aulas ativos da turma ativa mais recente, na ordem, sem dados internos.
 * Não cobre: capa (CapaCursoIT), matriz de papéis (CursoAcessoIT).
 * Dado criado: cursos it-<stamp>.
 */
class CatalogoIT extends IntegrationTest {

    private CursoITHelper adm;

    @BeforeEach
    void entrarComoAdmin() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
    }

    /** Slugs de todas as páginas do catálogo, sem login. */
    private List<String> slugsNoCatalogo() {
        List<String> slugs = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            var resposta = mvc.get().uri("/api/cursos?size=100&page=" + pagina).exchange();
            assertThat(resposta).hasStatusOk();
            String corpo = CursoITHelper.corpo(resposta);
            slugs.addAll(JsonPath.read(corpo, "$.itens[*].slug"));
            if (pagina + 1 >= (int) JsonPath.read(corpo, "$.totalPaginas")) {
                return slugs;
            }
        }
    }

    @Test
    @DisplayName("só cursos publicados aparecem: rascunho e arquivado ficam fora e respondem 404")
    void soPublicados() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        assertThat(slugsNoCatalogo()).doesNotContain(slug);
        assertThat(mvc.get().uri("/api/cursos/" + slug))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Curso não encontrado.");

        adm.publicar(curso);
        assertThat(slugsNoCatalogo()).contains(slug);
        assertThat(mvc.get().uri("/api/cursos?size=100"))
                .bodyJson()
                .isLenientlyEqualTo("{\"pagina\":0,\"tamanho\":100}");

        adm.status("/api/adm/cursos/" + curso + "/status", "ARQUIVADO");
        assertThat(slugsNoCatalogo()).doesNotContain(slug);
        assertThat(mvc.get().uri("/api/cursos/" + slug)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("detalhe mostra o conteúdo ativo da turma ativa mais recente, na ordem")
    void conteudoDaTurmaAtiva() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        adm.publicar(curso);

        long antiga = adm.criarTurma(curso, "Antiga");
        adm.ativarTurma(antiga);
        adm.criarModulo(antiga, "Da turma antiga");

        long atual = adm.criarTurma(curso, "Atual");
        adm.ativarTurma(atual);
        long introducao = adm.criarModulo(atual, "Introdução");
        long inativo = adm.criarModulo(atual, "Inativo");
        long semAulas = adm.criarModulo(atual, "Sem aulas");
        long gratuita = adm.criarAula(introducao, "Gratuita", true);
        long escondida = adm.criarAula(introducao, "Escondida", false);
        long paga = adm.criarAula(introducao, "Paga", false);
        adm.criarAula(inativo, "Aula de módulo inativo", false);
        adm.status("/api/adm/modulos/" + inativo + "/status", "INATIVO");
        adm.status("/api/adm/aulas/" + escondida + "/status", "INATIVO");
        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/turmas/" + atual + "/modulos/ordem",
                        "{\"ids\":[%d,%d,%d]}".formatted(semAulas, introducao, inativo)))
                .hasStatusOk();

        long preparacao = adm.criarTurma(curso, "Próxima");
        adm.criarModulo(preparacao, "Da turma em preparação");

        assertThat(mvc.get().uri("/api/cursos/" + slug))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"slug":"%s","titulo":"IT Curso %s","resumo":"Resumo de IT Curso %s","descricao":null,
                         "capaUrl":null,
                         "modulos":[
                           {"titulo":"Sem aulas","aulas":[]},
                           {"titulo":"Introdução","aulas":[
                             {"id":%d,"titulo":"Gratuita","gratuita":true},
                             {"id":%d,"titulo":"Paga","gratuita":false}]}]}
                        """.formatted(slug, slug, slug, gratuita, paga));
    }

    @Test
    @DisplayName("curso publicado sem turma ativa aparece sem conteúdo")
    void semTurmaAtiva() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        adm.criarModulo(adm.criarTurma(curso, "Em preparação"), "Módulo");
        adm.publicar(curso);

        assertThat(mvc.get().uri("/api/cursos/" + slug))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.modulos")
                .asArray()
                .isEmpty();
    }

    @Test
    @DisplayName("a listagem não expõe dados internos do curso")
    void semDadosInternos() {
        String slug = stamp();
        adm.publicar(adm.criarCurso("IT Curso " + slug, slug));

        var resposta = mvc.get().uri("/api/cursos/" + slug).exchange();
        assertThat(resposta).hasStatusOk();
        assertThat(CursoITHelper.corpo(resposta))
                .doesNotContain("\"id\"", "status", "capaChave", "criadoEm", "atualizadoEm");
    }
}
