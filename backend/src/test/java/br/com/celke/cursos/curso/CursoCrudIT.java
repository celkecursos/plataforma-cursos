package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Cobre: criar, buscar, atualizar, publicar, listar com filtro e excluir curso; slug gerado e
 * normalizado; slug duplicado; validação; exclusão só de item vazio (curso, turma, módulo).
 * Não cobre: ordem de módulos e aulas (ConteudoOrdemIT), capa (CapaCursoIT), catálogo (CatalogoIT).
 * Dado criado: cursos com título e slug it-<stamp>.
 */
class CursoCrudIT extends IntegrationTest {

    private CursoITHelper adm;

    @BeforeEach
    void entrarComoAdmin() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
    }

    @Test
    @DisplayName("cria (slug gerado do título), busca, atualiza, publica, lista e exclui")
    void cicloDoCurso() {
        String stamp = stamp();
        var criacao = adm.enviar(
                "POST",
                "/api/adm/cursos",
                "{\"titulo\":\"  Programação Ágil %s \",\"resumo\":\" Resumo \",\"descricao\":\"   \"}"
                        .formatted(stamp));
        assertThat(criacao).hasStatus(HttpStatus.CREATED);
        long id = CursoITHelper.idCriado(criacao);
        assertThat(criacao.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/adm/cursos/" + id);
        assertThat(criacao).bodyJson().isLenientlyEqualTo("""
                        {"titulo":"Programação Ágil %s","slug":"programacao-agil-%s","resumo":"Resumo",
                         "descricao":null,"status":"RASCUNHO","capaUrl":null}
                        """.formatted(stamp, stamp));

        assertThat(adm.enviar("GET", "/api/adm/cursos/" + id, null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.slug")
                .isEqualTo("programacao-agil-" + stamp);

        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/cursos/" + id,
                        "{\"titulo\":\"Ágil\",\"slug\":\"%s\",\"resumo\":\"Novo\",\"descricao\":\"Texto\"}"
                                .formatted(stamp)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"titulo\":\"Ágil\",\"slug\":\"%s\",\"resumo\":\"Novo\",\"descricao\":\"Texto\"}"
                        .formatted(stamp));

        adm.publicar(id);
        assertThat(adm.enviar("GET", "/api/adm/cursos?status=PUBLICADO&size=100", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.itens[*].slug")
                .asArray()
                .contains(stamp);
        String rascunho = stamp();
        adm.criarCurso("IT Rascunho " + rascunho, rascunho);
        var rascunhos = adm.enviar("GET", "/api/adm/cursos?status=RASCUNHO&size=100", null);
        assertThat(rascunhos)
                .bodyJson()
                .extractingPath("$.itens[*].slug")
                .asArray()
                .contains(rascunho)
                .doesNotContain(stamp);
        assertThat(rascunhos)
                .bodyJson()
                .extractingPath("$.itens[*].status")
                .asArray()
                .containsOnly("RASCUNHO");

        assertThat(adm.enviar("DELETE", "/api/adm/cursos/" + id, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(adm.enviar("GET", "/api/adm/cursos/" + id, null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"status\":404,\"detail\":\"Curso não encontrado.\",\"instance\":\"/api/adm/cursos/%d\"}"
                                .formatted(id));
    }

    @Test
    @DisplayName("slug já usado por outro curso responde 409, na criação e na edição")
    void slugDuplicado() {
        String slug = stamp();
        adm.criarCurso("IT Curso " + slug, slug);
        long outro = adm.criarCurso("IT Outro " + slug, slug + "-outro");
        String esperado = "Já existe um curso com o slug \"%s\".".formatted(slug);

        assertThat(adm.enviar(
                        "POST",
                        "/api/adm/cursos",
                        "{\"titulo\":\"X\",\"slug\":\"%s\",\"resumo\":\"R\"}".formatted(slug)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo(esperado);
        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/cursos/" + outro,
                        "{\"titulo\":\"X\",\"slug\":\"%s\",\"resumo\":\"R\"}".formatted(slug)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo(esperado);
    }

    @Test
    @DisplayName("dados inválidos respondem 400 com a lista de campos")
    void validacao() {
        assertThat(adm.enviar("POST", "/api/adm/cursos", "{\"titulo\":\" \",\"slug\":\"Com Espaço\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .asArray()
                .containsExactlyInAnyOrder("resumo", "slug", "titulo");
        assertThat(adm.enviar("PATCH", "/api/adm/cursos/1/status", "{\"status\":\"PUBLICADA\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(adm.enviar("GET", "/api/adm/cursos?status=QUALQUER", null)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("só exclui item vazio: aula → módulo → turma → curso")
    void exclusaoSoDeItemVazio() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        long turma = adm.criarTurma(curso, "Turma");
        long modulo = adm.criarModulo(turma, "Módulo");
        long aula = adm.criarAula(modulo, "Aula", false);

        assertThat(adm.enviar("DELETE", "/api/adm/cursos/" + curso, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O curso tem turmas. Exclua as turmas vazias ou arquive o curso.");
        assertThat(adm.enviar("DELETE", "/api/adm/turmas/" + turma, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A turma tem módulos. Exclua os módulos ou encerre a turma.");
        assertThat(adm.enviar("DELETE", "/api/adm/modulos/" + modulo, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O módulo tem aulas. Exclua as aulas ou inative o módulo.");

        assertThat(adm.enviar("DELETE", "/api/adm/aulas/" + aula, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(adm.enviar("DELETE", "/api/adm/modulos/" + modulo, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(adm.enviar("DELETE", "/api/adm/turmas/" + turma, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(adm.enviar("DELETE", "/api/adm/cursos/" + curso, null)).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("turma: cria em preparação, renomeia, ativa e lista no curso")
    void turma() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        long turma = adm.criarTurma(curso, "Turma 1");

        assertThat(adm.enviar("PUT", "/api/adm/turmas/" + turma, "{\"nome\":\" Turma 2026 \"}"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"cursoId\":%d,\"nome\":\"Turma 2026\",\"status\":\"PREPARACAO\"}".formatted(curso));
        adm.ativarTurma(turma);
        assertThat(adm.enviar("GET", "/api/adm/cursos/" + curso + "/turmas", null))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("[{\"id\":%d,\"status\":\"ATIVA\"}]".formatted(turma));
        assertThat(adm.enviar("POST", "/api/adm/cursos/999999999/turmas", "{\"nome\":\"T\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
