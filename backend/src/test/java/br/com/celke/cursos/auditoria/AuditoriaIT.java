package br.com.celke.cursos.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.auditoria.application.AuditoriaService;
import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.pagamento.PagamentoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Cobre: trilha de auditoria — inclusão e alteração pelo ADM (quem, rota, IP, só os campos que
 * mudaram), ação do aluno (perfil e troca de senha com o hash oculto), segredo do meio de pagamento
 * oculto, operação recusada não deixa rastro, entidade fora da trilha (progresso), mudança feita pelo
 * worker com o job como origem, filtros, só inclusão no banco, retenção e matriz de papéis.
 * Não cobre: conversão de valores caso a caso (ValoresAuditoriaTest).
 * Dado criado: cursos e contas it-<stamp>, uma Hotmart e um registro antigo da retenção.
 */
class AuditoriaIT extends IntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AuditoriaService auditoriaService;

    private CursoITHelper adm;
    private Long adminId;

    @BeforeEach
    void login() {
        String token = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
        adm = new CursoITHelper(mvc, bearer(token));
        adminId = ((Number) JsonPath.read(
                        CursoITHelper.corpo(mvc.get()
                                .uri("/api/perfil")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .exchange()),
                        "$.id"))
                .longValue();
    }

    /** Antes e depois dos registros, juntos e sem espaços (o jsonb volta com espaço depois dos dois-pontos). */
    private static String mudancas(List<Map<String, Object>> registros) {
        return registros.stream()
                .map(r -> r.get("antes") + " " + r.get("depois"))
                .collect(Collectors.joining())
                .replace(" ", "");
    }

    private List<Map<String, Object>> trilha(String filtro) {
        return JsonPath.read(
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/auditoria?size=100&" + filtro, null)), "$.itens");
    }

    @Test
    @DisplayName("ADM cria e altera um curso: inclusão completa e alteração só do que mudou, com quem, rota e IP")
    void cursoPeloAdm() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/cursos/" + curso,
                        "{\"titulo\":\"Novo título\",\"slug\":\"%s\",\"resumo\":\"Resumo de IT Curso %s\"}"
                                .formatted(slug, slug)))
                .hasStatusOk();

        List<Map<String, Object>> registros = trilha("entidade=Curso&entidadeId=" + curso);
        assertThat(registros).extracting(r -> r.get("operacao")).containsExactly("ALTERACAO", "INCLUSAO");
        Map<String, Object> alteracao = registros.get(0);
        assertThat(alteracao)
                .containsEntry("usuarioEmail", UsuariosSeed.ADMIN_EMAIL)
                .containsEntry("origem", "PUT /api/adm/cursos/{id}")
                .containsEntry("ip", "127.0.0.1");
        assertThat(alteracao.get("requestId")).isNotNull();
        assertThat(JsonPath.<Map<String, Object>>read((String) alteracao.get("antes"), "$"))
                .containsEntry("titulo", "IT Curso " + slug)
                .doesNotContainKey("slug");
        assertThat(JsonPath.<Map<String, Object>>read((String) alteracao.get("depois"), "$"))
                .containsEntry("titulo", "Novo título")
                .containsKey("atualizadoEm")
                .doesNotContainKey("resumo");
        Map<String, Object> inclusao = registros.get(1);
        assertThat(inclusao).containsEntry("origem", "POST /api/adm/cursos").containsEntry("antes", null);
        assertThat(JsonPath.<Map<String, Object>>read((String) inclusao.get("depois"), "$"))
                .containsEntry("slug", slug)
                .containsEntry("status", "RASCUNHO");

        assertThat(trilha("usuarioId=" + adminId + "&entidade=Curso&entidadeId=" + curso))
                .hasSize(2);
        assertThat(trilha(
                        "email=" + UsuariosSeed.ADMIN_EMAIL + "&operacao=INCLUSAO&entidade=Curso&entidadeId=" + curso))
                .hasSize(1);
        assertThat(trilha("email=ninguem-" + stamp() + "@teste.celke.uno")).isEmpty();
        assertThat(trilha("entidade=Curso&entidadeId=" + curso + "&de="
                        + Instant.now().plusSeconds(3600)))
                .isEmpty();
    }

    @Test
    @DisplayName("aluno troca nome e senha: registrado com o hash da senha oculto")
    void acaoDoAluno() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String token = login(aluno.email(), aluno.senha());
        assertThat(mvc.put()
                        .uri("/api/perfil")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome Novo\"}"))
                .hasStatusOk();
        assertThat(mvc.put()
                        .uri("/api/perfil/senha")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"senhaAtual\":\"%s\",\"novaSenha\":\"outra-senha-forte\"}".formatted(aluno.senha())))
                .hasStatus2xxSuccessful();

        List<Map<String, Object>> registros =
                trilha("entidade=Usuario&entidadeId=" + aluno.id() + "&operacao=ALTERACAO");
        assertThat(registros).extracting(r -> r.get("usuarioEmail")).containsOnly(aluno.email());
        String tudo = mudancas(registros);
        assertThat(tudo).contains("NomeNovo").contains("\"senhaHash\":\"***\"").doesNotContain("argon2");
    }

    @Test
    @DisplayName("segredo do meio de pagamento oculto; operação recusada não deixa rastro")
    void segredoERecusa() {
        String hottok = "hottok-" + stamp();
        long gateway = new PagamentoITHelper(mvc, adm).hotmart(hottok, false).id();
        List<Map<String, Object>> registros = trilha("entidade=Gateway&entidadeId=" + gateway);
        assertThat(registros).isNotEmpty();
        assertThat(mudancas(registros)).contains("\"segredos\":\"***\"").doesNotContain(hottok);

        String slug = stamp();
        adm.criarCurso("IT Curso " + slug, slug);
        int antes = trilha("entidade=Curso").size();
        assertThat(adm.enviar(
                        "POST",
                        "/api/adm/cursos",
                        "{\"titulo\":\"Repetido\",\"slug\":\"%s\",\"resumo\":\"x\"}".formatted(slug)))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(trilha("entidade=Curso")).hasSize(antes);
    }

    @Test
    @DisplayName("mudança feita pelo worker fica com o job como origem e sem usuário; progresso fica fora")
    void worker() {
        CursoITHelper admin = adm;
        var estrutura = new MatriculaITHelper(admin).cursoPublicado(stamp());
        PagamentoITHelper pagamentos = new PagamentoITHelper(mvc, admin);
        var gateway = pagamentos.hotmart("hottok-" + stamp(), true);
        String produto = stamp();
        pagamentos.oferta(estrutura.turma(), produto, "k2", null);
        String email = stamp() + "@teste.celke.uno";
        pagamentos.enviar(
                gateway,
                pagamentos
                        .notificacao("PURCHASE_APPROVED")
                        .xcod(null)
                        .transacao("HP-" + stamp())
                        .produto(produto, "k2")
                        .comprador(email, "Compradora")
                        .json(),
                "recebido");
        processarFila();

        List<Map<String, Object>> matriculas = trilha("entidade=Matricula&operacao=INCLUSAO");
        Map<String, Object> daCompra = matriculas.stream()
                .filter(r ->
                        ((String) r.get("depois")).replace(" ", "").contains("\"turmaId\":" + estrutura.turma() + ","))
                .findFirst()
                .orElseThrow();
        assertThat(daCompra)
                .containsEntry("origem", "job pagamento.processar-evento")
                .containsEntry("usuarioId", null)
                .containsEntry("ip", null);
        assertThat(trilha("entidade=ProgressoAula")).isEmpty();
        assertThat(CursoITHelper.corpo(adm.enviar("GET", "/api/adm/auditoria/entidades", null)))
                .contains("\"Matricula\"", "\"Curso\"")
                .doesNotContain("RefreshToken");
        int id = (Integer) daCompra.get("id");
        assertThat(adm.enviar("GET", "/api/adm/auditoria/" + id, null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.entidade")
                .isEqualTo("Matricula");
        assertThat(adm.enviar("GET", "/api/adm/auditoria/999999999", null)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("o banco só aceita inclusão; a retenção apaga só o que passou do prazo")
    void somenteInclusaoERetencao() {
        Instant velho = Instant.now().minus(3000, ChronoUnit.DAYS);
        long antigo = jdbc.sql("""
                        INSERT INTO auditoria (operacao, entidade, entidade_id, origem, em)
                        VALUES ('INCLUSAO', 'Teste', :id, 'teste', :em) RETURNING id
                        """)
                .param("id", stamp())
                .param("em", velho.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .single();

        assertThatThrownBy(() -> jdbc.sql("UPDATE auditoria SET origem = 'x' WHERE id = :id")
                        .param("id", antigo)
                        .update())
                .hasMessageContaining("A auditoria só aceita inclusão");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM auditoria WHERE id = :id")
                        .param("id", antigo)
                        .update())
                .hasMessageContaining("A auditoria só aceita inclusão");

        String slug = stamp();
        long recente = adm.criarCurso("IT Curso " + slug, slug);
        assertThat(auditoriaService.aplicarRetencao()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM auditoria WHERE id = :id")
                        .param("id", antigo)
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(trilha("entidade=Curso&entidadeId=" + recente)).hasSize(1);
    }

    @Test
    @DisplayName("só o ADMIN consulta: anônimo 401, aluno 403")
    void papeis() {
        String aluno = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);
        for (String rota : List.of("/api/adm/auditoria", "/api/adm/auditoria/entidades", "/api/adm/auditoria/1")) {
            assertThat(mvc.get().uri(rota)).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(mvc.get().uri(rota).header(HttpHeaders.AUTHORIZATION, bearer(aluno)))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
        assertThat(adm.enviar("GET", "/api/adm/auditoria", null)).hasStatusOk();
        assertThat(adm.enviar("GET", "/api/adm/auditoria/entidades", null)).hasStatusOk();
    }
}
