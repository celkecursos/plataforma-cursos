package br.com.celke.cursos.arquivo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.arquivo.infra.ArquivoApoioRepository;
import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.shared.armazenamento.ArmazenamentoProperties;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: arquivos de apoio com o Garage real — envio pelo ADM (confirmação que confere o conteúdo e
 * move para arquivos/), listagem e download do aluno pela política (matriz de acesso: vigente, sem
 * matrícula, vencida, cancelada, ainda não iniciada, aula gratuita; módulo só com matrícula), URL de
 * download baixando como anexo, exclusão e os bloqueios (extensão falsa, chave de outra aula, aula e
 * módulo com arquivos).
 * Não cobre: formatos e nomes caso a caso (FormatoArquivoTest, NomeArquivoTest, ArquivoAdmServiceTest).
 * Dado criado: curso it-<stamp> publicado, arquivos em uploads/arquivos/ e arquivos/, e um aluno próprio.
 */
class ArquivoIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0, 'c', 'o', 'd', 'i', 'g', 'o'};
    private static final byte[] PDF = "%PDF-1.7\n%apostila\n".getBytes(StandardCharsets.UTF_8);

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private StorageService storage;

    @Autowired
    private ArquivoApoioRepository arquivos;

    @Autowired
    private ArmazenamentoProperties armazenamento;

    private MatriculaITHelper helper;
    private ArquivoITHelper envio;
    private MatriculaITHelper.Estrutura estrutura;
    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarCursoEAluno() {
        CursoITHelper adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        helper = new MatriculaITHelper(adm);
        envio = new ArquivoITHelper(adm);
        estrutura = helper.cursoPublicado(stamp());
        aluno = criarUsuario(Papel.ALUNO);
        token = login(aluno.email(), aluno.senha());
    }

    private String aula(long id) {
        return "/api/adm/aulas/" + id;
    }

    private String modulo(long id) {
        return "/api/adm/modulos/" + id;
    }

    private MvcTestResult get(String uri, String tokenUsado) {
        var requisicao = mvc.get().uri(uri);
        if (tokenUsado != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(tokenUsado));
        }
        return requisicao.exchange();
    }

    private MvcTestResult download(long arquivo) {
        return get("/api/aluno/arquivos/" + arquivo + "/download", token);
    }

    private void matricularVigente() {
        Instant agora = Instant.now();
        helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), agora.plus(DIA));
    }

    @Test
    @DisplayName("ADM publica o arquivo da aula; o aluno matriculado lista e baixa como anexo; ADM remove")
    void cicloDoArquivoDaAula() throws Exception {
        String upload = envio.enviar(aula(estrutura.aulaPaga()), ZIP, "application/zip");
        MvcTestResult criado =
                envio.confirmar(aula(estrutura.aulaPaga()), upload, "Código-fonte", "Código da aula 1.zip");
        assertThat(criado).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
                {"aulaId":%d,"moduloId":null,"titulo":"Código-fonte","nomeArquivo":"Codigo-da-aula-1.zip",
                 "contentType":"application/zip","tamanho":%d}
                """.formatted(
                        estrutura.aulaPaga(), ZIP.length));
        assertThat(CursoITHelper.corpo(criado)).doesNotContain("chave").doesNotContain("uploads/");
        long id = CursoITHelper.idCriado(criado);
        String chave = arquivos.findById(id).orElseThrow().getChave();
        assertThat(chave).startsWith("arquivos/aulas/" + estrutura.aulaPaga() + "/");
        assertThat(storage.info(upload)).as("envio apagado de uploads/").isEmpty();
        assertThat(storage.info(chave)).isPresent();
        assertThat(get(aula(estrutura.aulaPaga()) + "/arquivos", tokenAdmin()))
                .bodyJson()
                .extractingPath("$[*].id")
                .isEqualTo(List.of((int) id));

        matricularVigente();
        assertThat(get("/api/aluno/aulas/" + estrutura.aulaPaga() + "/arquivos", token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].titulo")
                .isEqualTo(List.of("Código-fonte"));
        MvcTestResult link = download(id);
        assertThat(link).hasStatusOk();
        assertThat(link.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        String url = JsonPath.read(CursoITHelper.corpo(link), "$.url");
        assertThat(url).startsWith(armazenamento.endpointPublico()).contains("X-Amz-Expires=300");
        HttpResponse<byte[]> baixado =
                http.send(HttpRequest.newBuilder(URI.create(url)).build(), BodyHandlers.ofByteArray());
        assertThat(baixado.statusCode()).isEqualTo(200);
        assertThat(baixado.body()).isEqualTo(ZIP);
        assertThat(baixado.headers().firstValue("Content-Disposition"))
                .contains("attachment; filename=\"Codigo-da-aula-1.zip\"");

        // Aula com arquivo não pode ser excluída
        assertThat(helper.adm().enviar("DELETE", aula(estrutura.aulaPaga()), null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A aula tem vídeo ou arquivos de apoio. Remova-os ou inative a aula.");

        assertThat(helper.adm().enviar("DELETE", "/api/adm/arquivos/" + id, null))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(storage.info(chave)).isEmpty();
        assertThat(get("/api/aluno/aulas/" + estrutura.aulaPaga() + "/arquivos", token))
                .bodyJson()
                .extractingPath("$")
                .isEqualTo(List.of());
        assertThat(download(id))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Arquivo não encontrado.");
    }

    private String tokenAdmin() {
        return login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
    }

    static Stream<Arguments> semMatriculaVigente() {
        return Stream.of(
                Arguments.of("sem matrícula"),
                Arguments.of("vencida"),
                Arguments.of("cancelada"),
                Arguments.of("ainda não iniciada"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("semMatriculaVigente")
    @DisplayName("sem matrícula vigente: 403 na lista e no download do arquivo da aula paga e do módulo")
    void semAcesso(String situacao) throws Exception {
        long daAula = envio.publicar(aula(estrutura.aulaPaga()), ZIP, "application/zip", "Código", "c.zip");
        long doModulo = envio.publicar(modulo(estrutura.modulo()), PDF, "application/pdf", "Apostila", "a.pdf");
        Instant agora = Instant.now();
        switch (situacao) {
            case "vencida" ->
                helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA.multipliedBy(2)), agora.minus(DIA));
            case "cancelada" ->
                helper.cancelar(helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), null));
            case "ainda não iniciada" -> helper.matricula(estrutura.turma(), aluno.email(), agora.plus(DIA), null);
            default -> {}
        }

        assertThat(get("/api/aluno/aulas/" + estrutura.aulaPaga() + "/arquivos", token))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(download(daAula))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
        assertThat(get("/api/aluno/modulos/" + estrutura.modulo() + "/arquivos", token))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(download(doModulo))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
    }

    @Test
    @DisplayName("aula gratuita: arquivo liberado sem matrícula; o do módulo continua exigindo matrícula")
    void gratuita() throws Exception {
        long daGratuita = envio.publicar(aula(estrutura.aulaGratuita()), ZIP, "application/zip", "Código", "c.zip");
        long doModulo = envio.publicar(modulo(estrutura.modulo()), PDF, "application/pdf", "Apostila", "a.pdf");

        assertThat(get("/api/aluno/aulas/" + estrutura.aulaGratuita() + "/arquivos", token))
                .hasStatusOk();
        assertThat(download(daGratuita)).hasStatusOk();
        assertThat(download(doModulo)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("módulo: matriculado baixa; módulo inativo some; módulo só com arquivos não é excluído")
    void arquivoDoModulo() throws Exception {
        long id = envio.publicar(modulo(estrutura.modulo()), PDF, "application/pdf", "Apostila", "Apostila.pdf");
        matricularVigente();

        assertThat(get("/api/aluno/modulos/" + estrutura.modulo() + "/arquivos", token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].nomeArquivo")
                .isEqualTo("Apostila.pdf");
        assertThat(download(id)).hasStatusOk();

        helper.adm().status("/api/adm/modulos/" + estrutura.modulo() + "/status", "INATIVO");
        assertThat(get("/api/aluno/modulos/" + estrutura.modulo() + "/arquivos", token))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Módulo não encontrado.");

        long vazio = helper.adm().criarModulo(estrutura.turma(), "Só materiais");
        envio.publicar(modulo(vazio), PDF, "application/pdf", "Extra", "extra.pdf");
        assertThat(helper.adm().enviar("DELETE", modulo(vazio), null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O módulo tem arquivos de apoio. Remova os arquivos ou inative o módulo.");
    }

    @Test
    @DisplayName("conteúdo que não é ZIP enviado como ZIP (extensão falsa) é recusado e apagado")
    void extensaoFalsa() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        String upload = envio.enviar(aula(estrutura.aulaPaga()), html, "application/zip");

        assertThat(envio.confirmar(aula(estrutura.aulaPaga()), upload, "Falso", "falso.zip"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O conteúdo do arquivo não confere com o tipo informado.");
        assertThat(storage.info(upload)).isEmpty();
        assertThat(arquivos.findByAulaIdOrderById(estrutura.aulaPaga())).isEmpty();
    }

    @Test
    @DisplayName("recusas: tipo não aceito, chave de outra aula, envio não feito, aula inexistente")
    void recusas() throws Exception {
        assertThat(helper.adm()
                        .enviar(
                                "POST",
                                aula(estrutura.aulaPaga()) + "/arquivos/envio",
                                "{\"contentType\":\"application/x-msdownload\",\"tamanho\":10}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envie um arquivo ZIP, PDF ou TXT.");

        String daGratuita = envio.enviar(aula(estrutura.aulaGratuita()), ZIP, "application/zip");
        assertThat(envio.confirmar(aula(estrutura.aulaPaga()), daGratuita, "Outro", "o.zip"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envio de arquivo inválido. Comece um novo envio.");
        assertThat(storage.info(daGratuita))
                .as("o envio da outra aula continua lá")
                .isPresent();

        String naoEnviado = JsonPath.read(
                CursoITHelper.corpo(helper.adm()
                        .enviar(
                                "POST",
                                aula(estrutura.aulaPaga()) + "/arquivos/envio",
                                "{\"contentType\":\"text/plain\",\"tamanho\":10}")),
                "$.chave");
        assertThat(envio.confirmar(aula(estrutura.aulaPaga()), naoEnviado, "T", "t.txt"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O arquivo não foi enviado. Comece um novo envio.");

        assertThat(helper.adm()
                        .enviar(
                                "POST",
                                "/api/adm/aulas/999999999/arquivos/envio",
                                "{\"contentType\":\"text/plain\",\"tamanho\":10}"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Aula não encontrada.");
    }

    @Test
    @DisplayName("rotas do aluno exigem token")
    void anonimo() throws Exception {
        long id = envio.publicar(aula(estrutura.aulaGratuita()), ZIP, "application/zip", "Código", "c.zip");

        assertThat(get("/api/aluno/aulas/" + estrutura.aulaGratuita() + "/arquivos", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/aluno/modulos/" + estrutura.modulo() + "/arquivos", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/aluno/arquivos/" + id + "/download", null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
