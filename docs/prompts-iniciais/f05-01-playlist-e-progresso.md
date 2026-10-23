# Playlist assinada, progresso e log de acesso

**Fase:** 5
**Branch:** `feature/entrega-video`

## Prompt

Vamos entregar o vídeo ao aluno sem abrir o bucket e guardar o progresso dele. Siga o
`backend/CLAUDE.md` e o padrão das skills de teste. Não faça commit.

O que esta etapa entrega:

- **Playlist assinada** (módulo `video`): `GET /api/aulas/{id}/video.m3u8` e
  `GET /api/aulas/{id}/video/{variante}.m3u8` passam pela `AcessoAulaService` (matrícula vigente ou aula
  gratuita). A master aponta para as variantes servidas pela API (`video/720p.m3u8`); cada variante
  traz os segmentos por URLs pré-assinadas válidas pela duração do vídeo + `app.video.margem-playlist`
  (2 h). A reescrita só aceita os nomes que o worker gera (playlist adulterada falha). Resposta
  `application/vnd.apple.mpegurl` com `Cache-Control: no-store`. Aula sem vídeo publicado e variante
  inexistente respondem 404.
- **Log de acesso** (migration `V10` `acessos_video`): cada abertura da master registra usuário, aula,
  IP e navegador; o worker apaga todo dia, às 4h30, o que passa de `app.video.retencao-log-acesso`
  (180 dias, o prazo do Marco Civil para registros de acesso a aplicações).
- **Progresso** (módulo `progresso`, migration `V9` `progresso_aulas`, um registro por aluno e aula,
  apagado junto com a aula): `GET` e `PATCH /api/aluno/aulas/{id}/progresso` (posição, vista, revisar;
  só os campos enviados mudam; marcar como vista guarda quando) e `GET /api/aluno/turmas/{id}/progresso`
  (percentual das aulas ativas vistas, arredondado para baixo, com o progresso de cada aula). Sempre no
  escopo do token; a turma exige matrícula vigente pela nova `AcessoAulaService.exigirAcessoTurma`.
- O `MatriculaITHelper` passa a ser público (os testes de vídeo e progresso montam matrículas).
- Na stack de e2e a margem da playlist é de 5 s, para a jornada ver a URL do segmento vencer.
- A criptografia AES-128 do HLS (capítulo 23, opcional) fica de fora.

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### 1. Arquivos alterados (substitua o conteúdo inteiro)

`backend/src/main/java/br/com/celke/cursos/matricula/application/AcessoAulaService.java`:

`````java
package br.com.celke.cursos.matricula.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaContexto;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.matricula.domain.AcessoAulaPolicy;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Porta de entrada da {@link AcessoAulaPolicy} para os outros módulos: toda rota de aula, vídeo,
 * arquivo, fórum ou progresso chama {@link #exigirAcesso} (ou {@link #exigirAcessoTurma}) antes de responder.
 */
@Service
public class AcessoAulaService {

    private final ConteudoTurmaService conteudoTurmaService;
    private final MatriculaRepository matriculas;
    private final AcessoAulaPolicy policy;

    public AcessoAulaService(
            ConteudoTurmaService conteudoTurmaService, MatriculaRepository matriculas, AcessoAulaPolicy policy) {
        this.conteudoTurmaService = conteudoTurmaService;
        this.matriculas = matriculas;
        this.policy = policy;
    }

    /**
     * Devolve a aula se o usuário pode acessá-la. Aula inexistente ou inativa responde 404; sem
     * matrícula vigente (e sem ser aula aberta), 403.
     */
    @Transactional(readOnly = true)
    public AulaContexto exigirAcesso(Long usuarioId, Long aulaId) {
        AulaContexto aula = conteudoTurmaService
                .aulaAtiva(aulaId)
                .orElseThrow(() -> new NaoEncontradoException("Aula não encontrada."));
        var matricula = matriculas
                .findByUsuarioIdAndTurmaIdAndStatus(usuarioId, aula.turmaId(), StatusMatricula.ATIVA)
                .orElse(null);
        if (!policy.podeAcessarAula(matricula, aula.aberta())) {
            throw new AcessoNegadoException("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
        }
        return aula;
    }

    /**
     * Devolve a turma se o usuário tem matrícula vigente nela (conteúdo completo: lista de aulas,
     * progresso, certificado). Turma inexistente responde 404; sem matrícula vigente, 403.
     */
    @Transactional(readOnly = true)
    public TurmaResumo exigirAcessoTurma(Long usuarioId, Long turmaId) {
        TurmaResumo turma = conteudoTurmaService
                .turma(turmaId)
                .orElseThrow(() -> new NaoEncontradoException("Turma não encontrada."));
        var matricula = matriculas
                .findByUsuarioIdAndTurmaIdAndStatus(usuarioId, turmaId, StatusMatricula.ATIVA)
                .orElse(null);
        if (!policy.podeAcessarTurma(matricula)) {
            throw new AcessoNegadoException("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
        }
        return turma;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/application/VideoProperties.java`:

`````java
package br.com.celke.cursos.video.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do envio e da conversão de vídeos.
 *
 * @param tamanhoMaximo maior arquivo aceito no envio
 * @param validadeEnvio validade das URLs de envio das partes
 * @param ffmpeg executável do ffmpeg (no PATH ou caminho completo)
 * @param ffprobe executável do ffprobe
 * @param prefixoComando programa e argumentos colocados antes do ffmpeg (no worker, {@code nice -n 10}
 *     para a conversão ter prioridade baixa)
 * @param preset preset do x264: mais lento comprime melhor e gasta mais CPU
 * @param crf qualidade constante do x264 (menor = melhor e maior)
 * @param diretorioTrabalho onde o worker baixa o original e gera o HLS (apagado no fim de cada job)
 * @param margemPlaylist somada à duração do vídeo na validade das URLs dos segmentos (pausas, avanço)
 * @param retencaoLogAcesso por quanto tempo o log de acesso aos vídeos fica guardado
 */
@Validated
@ConfigurationProperties("app.video")
public record VideoProperties(
        @NotNull DataSize tamanhoMaximo,
        @NotNull Duration validadeEnvio,
        @NotBlank String ffmpeg,
        @NotBlank String ffprobe,
        List<String> prefixoComando,
        @NotBlank String preset,
        @Min(18) @Max(30) int crf,
        @NotNull Path diretorioTrabalho,
        @NotNull Duration margemPlaylist,
        @NotNull Duration retencaoLogAcesso) {

    public VideoProperties {
        prefixoComando = prefixoComando == null
                ? List.of()
                : prefixoComando.stream()
                        .map(String::strip)
                        .filter(p -> !p.isEmpty())
                        .toList();
    }
}
`````

`backend/src/main/resources/application.yaml`:

`````yaml
spring:
  application:
    name: plataforma-cursos
  threads:
    virtual:
      enabled: true
  # Sem usuário embutido: a autenticação é feita pelos usuários do banco
  autoconfigure:
    exclude: org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
  # Sem valor padrão: sem as credenciais a aplicação não sobe (o compose exige as variáveis)
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
  data:
    web:
      pageable:
        default-page-size: 20
        max-page-size: 100
  web:
    # Mensagens de validação em português
    locale: pt_BR
    locale-resolver: fixed
  mail:
    host: ${SMTP_HOST:}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail.smtp.auth: ${SMTP_AUTH:true}
      mail.smtp.starttls.enable: ${SMTP_STARTTLS:true}
      mail.smtp.starttls.required: ${SMTP_STARTTLS:true}
      mail.smtp.connectiontimeout: 10000
      mail.smtp.timeout: 10000
      mail.smtp.writetimeout: 10000

logging:
  # Horário de Brasília nos logs: o @timestamp do JSON (ECS) e o texto do console do desenvolvimento
  structured:
    json:
      customizer: br.com.celke.cursos.shared.config.FusoHorarioLogs
  pattern:
    dateformat: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX, America/Sao_Paulo"

server:
  shutdown: graceful
  # A API roda atrás do Caddy. Com "native", o Tomcat só aceita X-Forwarded-* vindo de proxy interno
  # (rede privada) e lê o X-Forwarded-For da direita para a esquerda: IP forjado pelo cliente é ignorado
  forward-headers-strategy: native
  error:
    include-stacktrace: never
    include-message: never

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
  health:
    # O SMTP fora do ar não deve tirar a API do ar: o envio é pela fila, com novas tentativas
    mail:
      enabled: false

# Swagger: ligado só no desenvolvimento e na homologação (DOCS_ENABLED=true)
springdoc:
  api-docs:
    enabled: ${DOCS_ENABLED:false}
  swagger-ui:
    enabled: ${DOCS_ENABLED:false}

app:
  auth:
    # Segredo do JWT em Base64, com pelo menos 32 bytes (openssl rand -base64 48)
    jwt-segredo: ${JWT_SEGREDO:}
    emissor: plataforma-cursos
    validade-acesso: PT10M
    validade-refresh: P14D
    cookie-seguro: true
    # Origens do front que podem renovar e encerrar a sessão (separadas por vírgula)
    origens-permitidas: ${AUTH_ORIGENS:}
  conta:
    # Área do aluno: base dos links enviados por e-mail
    url-aluno: ${FRONT_ALUNO_URL:}
    validade-confirmacao: PT48H
    validade-recuperacao: PT1H
    emails-por-hora: 3
  limites:
    # Rotas de autenticação e conta: requisições por IP a cada minuto
    requisicoes-por-ip-por-minuto: 30
    # Falhas de login seguidas que bloqueiam o par e-mail + IP, e o e-mail vindo de qualquer IP
    falhas-por-email-e-ip: 5
    falhas-por-email: 20
    # Contadores de falha zeram depois de uma hora sem novas falhas
    janela-falhas: PT1H
    bloqueio: PT15M
  armazenamento:
    # Endereço usado pela aplicação (rede interna)
    endpoint: ${S3_ENDPOINT:}
    # Endereço que vai nas URLs pré-assinadas (o que o navegador acessa)
    endpoint-publico: ${S3_ENDPOINT_PUBLICO:}
    regiao: ${S3_REGIAO:garage}
    bucket: ${S3_BUCKET:}
    access-key: ${S3_ACCESS_KEY:}
    secret-key: ${S3_SECRET_KEY:}
    cors-origens: ${S3_CORS_ORIGENS:}
    dias-uploads-abandonados: 2
  email:
    remetente: ${EMAIL_REMETENTE:}
    nome-remetente: Plataforma de Cursos
  fila:
    max-tentativas: 5
    lease: PT10M
    backoff-base: PT30S
    backoff-maximo: PT1H
    intervalo: PT2S
    consumidor-ativo: false
  video:
    tamanho-maximo: 5GB
    # Validade das URLs de envio das partes (um vídeo grande numa conexão lenta)
    validade-envio: PT6H
    ffmpeg: ffmpeg
    ffprobe: ffprobe
    # veryfast: na VPS (1,5 vCPU para o worker) o ganho de compressão dos presets lentos não compensa o tempo
    preset: veryfast
    crf: 24
    diretorio-trabalho: ${java.io.tmpdir}/plataforma-cursos-videos
    # Validade das URLs dos segmentos = duração do vídeo + margem (pausas e idas e voltas no player)
    margem-playlist: PT2H
    # Log de acesso aos vídeos: 6 meses (prazo do Marco Civil para registros de acesso a aplicações)
    retencao-log-acesso: P180D
`````

`infra/compose.e2e.yml`:

`````yaml
# e2e: a stack de um ambiente dos servidores (compose.prod.yml) com o Caddy dos servidores, HTTP em
# *.localhost (sites de caddy/e2e.caddy), Mailpit no lugar do SMTP, imagens construídas localmente e o
# seed do profile e2e. Projeto separado do ambiente local.
#
#   docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
#   docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
name: plataforma-cursos-e2e

services:
  caddy:
    image: caddy:2.11.7-alpine
    ports:
      - "127.0.0.1:${E2E_PORTA:?defina E2E_PORTA}:80"
    volumes:
      - ./caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - ./caddy/e2e.caddy:/etc/caddy/sites/e2e.caddy:ro
    depends_on:
      api:
        condition: service_healthy

  api:
    image: plataforma-cursos-api:e2e
    build:
      context: ../backend
      target: api
    environment:
      SPRING_PROFILES_ACTIVE: api,e2e
      SEED_E2E_SENHA: ${SEED_E2E_SENHA:?defina SEED_E2E_SENHA}
      # Todas as jornadas saem do mesmo IP
      APP_LIMITES_REQUISICOES_POR_IP_POR_MINUTO: "300"
      # URLs dos segmentos vencem em segundos: a jornada de entrega confere a expiração
      APP_VIDEO_MARGEM_PLAYLIST: PT5S

  worker:
    image: plataforma-cursos-worker:e2e
    build:
      context: ../backend
      target: worker
    depends_on:
      mailpit:
        condition: service_healthy

  mailpit:
    image: axllent/mailpit:v1.31.6
    ports:
      - "127.0.0.1:${E2E_MAILPIT_PORTA:?defina E2E_MAILPIT_PORTA}:8025"
    healthcheck:
      test: ["CMD", "/mailpit", "readyz"]
      interval: 5s
      timeout: 3s
      retries: 20
`````

`backend/src/test/java/br/com/celke/cursos/matricula/MatriculaITHelper.java`:

`````java
package br.com.celke.cursos.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Monta curso publicado com conteúdo e matrículas pela API do ADM, para os testes dos módulos que dependem do acesso ao conteúdo. */
public final class MatriculaITHelper {

    private final CursoITHelper adm;

    public MatriculaITHelper(CursoITHelper adm) {
        this.adm = adm;
    }

    public CursoITHelper adm() {
        return adm;
    }

    /** Curso publicado com turma ativa e um módulo com uma aula paga e uma gratuita. */
    public Estrutura cursoPublicado(String slug) {
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        long turma = adm.criarTurma(curso, "Turma");
        adm.ativarTurma(turma);
        long modulo = adm.criarModulo(turma, "Módulo");
        long paga = adm.criarAula(modulo, "Aula paga", false);
        long gratuita = adm.criarAula(modulo, "Aula gratuita", true);
        adm.publicar(curso);
        return new Estrutura(curso, turma, modulo, paga, gratuita);
    }

    public MvcTestResult matricular(long turma, String email, Instant inicio, Instant fim) {
        return adm.enviar(
                "POST",
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"MANUAL\"%s%s}"
                        .formatted(
                                email,
                                inicio == null ? "" : ",\"inicio\":\"" + inicio + "\"",
                                fim == null ? "" : ",\"fim\":\"" + fim + "\""));
    }

    /** Matricula e devolve o id da matrícula (confere o 201). */
    public long matricula(long turma, String email, Instant inicio, Instant fim) {
        return CursoITHelper.idCriado(matricular(turma, email, inicio, fim));
    }

    public void cancelar(long matricula) {
        assertThat(adm.enviar("POST", "/api/adm/matriculas/" + matricula + "/cancelamento", null))
                .hasStatus(HttpStatus.OK);
    }

    public record Estrutura(long curso, long turma, long modulo, long aulaPaga, long aulaGratuita) {}
}
`````

`backend/src/test/java/br/com/celke/cursos/matricula/application/AcessoAulaServiceTest.java`:

`````java
package br.com.celke.cursos.matricula.application;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaContexto;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.matricula.domain.AcessoAulaPolicy;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcessoAulaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private MatriculaRepository matriculas;

    private AcessoAulaService service;

    @BeforeEach
    void setUp() {
        service = new AcessoAulaService(
                conteudoTurmaService, matriculas, new AcessoAulaPolicy(Clock.fixed(AGORA, ZoneOffset.UTC)));
    }

    private static AulaContexto aula(boolean gratuita) {
        return new AulaContexto(100L, "Aula", null, gratuita, 5L, StatusTurma.ATIVA, StatusCurso.PUBLICADO);
    }

    @Test
    @DisplayName("matrícula vigente na turma da aula libera o acesso")
    void comMatricula() {
        when(conteudoTurmaService.aulaAtiva(100L)).thenReturn(Optional.of(aula(false)));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                .thenReturn(Optional.of(matricula(1, 7, 5, AGORA.minusSeconds(60), null)));

        assertThat(service.exigirAcesso(7L, 100L).id()).isEqualTo(100L);
    }

    @Test
    @DisplayName("aula aberta libera sem matrícula")
    void aulaAberta() {
        when(conteudoTurmaService.aulaAtiva(100L)).thenReturn(Optional.of(aula(true)));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                .thenReturn(Optional.empty());

        assertThat(service.exigirAcesso(7L, 100L).gratuita()).isTrue();
    }

    @Test
    @DisplayName("sem matrícula vigente e aula paga responde 403")
    void semAcesso() {
        when(conteudoTurmaService.aulaAtiva(100L)).thenReturn(Optional.of(aula(false)));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exigirAcesso(7L, 100L))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
    }

    @Test
    @DisplayName("aula inexistente ou inativa responde 404 sem consultar a matrícula")
    void aulaInexistente() {
        when(conteudoTurmaService.aulaAtiva(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exigirAcesso(7L, 9L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Aula não encontrada.");
        verify(matriculas, never()).findByUsuarioIdAndTurmaIdAndStatus(any(), any(), any());
    }

    private static TurmaResumo turma() {
        return new TurmaResumo(5L, "Turma", StatusTurma.ATIVA, 1L, "Java", "java", StatusCurso.PUBLICADO, null);
    }

    @Test
    @DisplayName("turma: matrícula vigente libera e devolve o resumo")
    void turmaComMatricula() {
        when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma()));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                .thenReturn(Optional.of(matricula(1, 7, 5, AGORA.minusSeconds(60), null)));

        assertThat(service.exigirAcessoTurma(7L, 5L)).isEqualTo(turma());
    }

    @Test
    @DisplayName("turma: sem matrícula vigente responde 403, mesmo com aula gratuita na turma")
    void turmaSemMatricula() {
        when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma()));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exigirAcessoTurma(7L, 5L))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
    }

    @Test
    @DisplayName("turma inexistente responde 404")
    void turmaInexistente() {
        when(conteudoTurmaService.turma(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exigirAcessoTurma(7L, 9L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Turma não encontrada.");
        verify(matriculas, never()).findByUsuarioIdAndTurmaIdAndStatus(any(), any(), any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/application/EnvioVideoServiceTest.java`:

`````java
package br.com.celke.cursos.video.application;

import static br.com.celke.cursos.support.fixtures.VideoFixture.UPLOAD;
import static br.com.celke.cursos.support.fixtures.VideoFixture.aguardandoEnvio;
import static br.com.celke.cursos.support.fixtures.VideoFixture.comErro;
import static br.com.celke.cursos.support.fixtures.VideoFixture.pronto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.armazenamento.StorageService.ParteEnviada;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.video.application.VideoAdmService.EnvioAnterior;
import br.com.celke.cursos.video.application.VideoAdmService.EnvioRegistrado;
import br.com.celke.cursos.video.domain.Video;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class EnvioVideoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Duration VALIDADE = Duration.ofHours(6);
    private static final byte[] MP4 = HexFormat.of().parseHex("000000206674797069736f6d");
    private static final List<ParteEnviada> PARTES = List.of(new ParteEnviada(1, "e1"));

    @Mock
    private VideoAdmService videoAdmService;

    @Mock
    private StorageService storage;

    private EnvioVideoService service;

    @BeforeEach
    void criar() {
        var props = new VideoProperties(
                DataSize.ofGigabytes(5),
                VALIDADE,
                "ffmpeg",
                "ffprobe",
                null,
                "veryfast",
                24,
                Path.of("/tmp/videos"),
                Duration.ofHours(2),
                Duration.ofDays(180));
        service = new EnvioVideoService(videoAdmService, storage, props, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static URL url(String caminho) throws Exception {
        return URI.create("http://media.localhost/" + caminho).toURL();
    }

    @Nested
    class Iniciar {

        @Test
        @DisplayName("abre o upload em partes, registra o envio e devolve uma URL por parte")
        void inicia() throws Exception {
            Video video = aguardandoEnvio(3);
            when(storage.iniciarMultipart(startsWith("uploads/videos/9/"), eq("video/mp4")))
                    .thenReturn("upload-x");
            when(videoAdmService.registrarEnvio(
                            eq(9L),
                            startsWith("uploads/videos/9/"),
                            eq("upload-x"),
                            eq("aula.mp4"),
                            eq(40L * 1024 * 1024)))
                    .thenReturn(new EnvioRegistrado(video, Optional.empty()));
            when(storage.urlParte(startsWith("uploads/videos/9/"), eq("upload-x"), anyInt(), eq(VALIDADE)))
                    .thenAnswer(i -> url("parte-" + i.getArgument(2)));

            var envio = service.iniciar(9L, " aula.mp4 ", "video/mp4", 40L * 1024 * 1024);

            verify(videoAdmService).exigirEnvioPermitido(9L);
            assertThat(envio.tamanhoParte()).isEqualTo(16L * 1024 * 1024);
            assertThat(envio.partes())
                    .extracting(EnvioVideoService.UrlParte::numero)
                    .containsExactly(1, 2, 3);
            assertThat(envio.partes().getLast().url().toString()).endsWith("parte-3");
            assertThat(envio.expiraEm()).isEqualTo(AGORA.plus(VALIDADE));
            assertThat(envio.video()).isSameAs(video);
            verify(storage, never()).abortarMultipart(anyString(), anyString());
        }

        @Test
        @DisplayName("envio anterior ainda aberto é abortado; falha ao abortar não atrapalha")
        void abortaAnterior() throws Exception {
            when(storage.iniciarMultipart(anyString(), eq("video/webm"))).thenReturn("upload-x");
            when(videoAdmService.registrarEnvio(eq(9L), anyString(), eq("upload-x"), eq("a.webm"), eq(10L)))
                    .thenReturn(new EnvioRegistrado(
                            aguardandoEnvio(3), Optional.of(new EnvioAnterior("velha", "upload-velho"))));
            doThrow(S3Exception.builder().message("NoSuchUpload").build())
                    .when(storage)
                    .abortarMultipart("velha", "upload-velho");
            when(storage.urlParte(anyString(), eq("upload-x"), eq(1), eq(VALIDADE)))
                    .thenReturn(url("p1"));

            assertThat(service.iniciar(9L, "a.webm", "video/webm", 10).partes()).hasSize(1);
            verify(storage).abortarMultipart("velha", "upload-velho");
        }

        @Test
        @DisplayName("tipo fora da lista é recusado antes de qualquer coisa")
        void tipoRecusado() {
            assertThatThrownBy(() -> service.iniciar(9L, "a.avi", "video/x-msvideo", 10))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("Envie um vídeo MP4, MOV, MKV ou WebM.");
            verify(storage, never()).iniciarMultipart(anyString(), anyString());
            verify(videoAdmService, never()).exigirEnvioPermitido(any());
        }

        @Test
        @DisplayName("acima do tamanho máximo é recusado; exatamente no limite passa")
        void tamanhoMaximo() throws Exception {
            long limite = DataSize.ofGigabytes(5).toBytes();
            assertThatThrownBy(() -> service.iniciar(9L, "a.mp4", "video/mp4", limite + 1))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("O vídeo pode ter no máximo 5 GB.");
            verify(storage, never()).iniciarMultipart(anyString(), anyString());

            when(storage.iniciarMultipart(anyString(), anyString())).thenReturn("u");
            when(videoAdmService.registrarEnvio(eq(9L), anyString(), eq("u"), eq("a.mp4"), eq(limite)))
                    .thenReturn(new EnvioRegistrado(aguardandoEnvio(3), Optional.empty()));
            when(storage.urlParte(anyString(), eq("u"), anyInt(), eq(VALIDADE))).thenReturn(url("p"));
            assertThat(service.iniciar(9L, "a.mp4", "video/mp4", limite).partes())
                    .hasSize(320);
        }

        @Test
        @DisplayName("aula inexistente ou vídeo em processamento: nenhum upload é aberto")
        void bloqueado() {
            doThrow(VideoAdmService.emProcessamento()).when(videoAdmService).exigirEnvioPermitido(9L);

            assertThatThrownBy(() -> service.iniciar(9L, "a.mp4", "video/mp4", 10))
                    .isInstanceOf(ConflitoException.class);
            verify(storage, never()).iniciarMultipart(anyString(), anyString());
        }

        @Test
        @DisplayName("registro recusado (corrida) aborta o upload recém-aberto")
        void registroRecusado() {
            when(storage.iniciarMultipart(anyString(), anyString())).thenReturn("upload-x");
            when(videoAdmService.registrarEnvio(eq(9L), anyString(), eq("upload-x"), anyString(), anyLong()))
                    .thenThrow(VideoAdmService.emProcessamento());

            assertThatThrownBy(() -> service.iniciar(9L, "a.mp4", "video/mp4", 10))
                    .isInstanceOf(ConflitoException.class);
            verify(storage).abortarMultipart(startsWith("uploads/videos/9/"), eq("upload-x"));
        }
    }

    @Nested
    class Concluir {

        @Test
        @DisplayName("junta as partes, confere tamanho e assinatura e coloca na fila")
        void conclui() {
            Video video = aguardandoEnvio(3);
            Video naFila = aguardandoEnvio(3);
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(video);
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1000, "video/mp4")));
            when(storage.baixarInicio(UPLOAD, 12)).thenReturn(MP4);
            when(videoAdmService.enfileirar(3L, 1000)).thenReturn(naFila);

            assertThat(service.concluir(9L, PARTES)).isSameAs(naFila);
            verify(storage).concluirMultipart(UPLOAD, "upload-1", PARTES);
            verify(storage, never()).excluir(anyString());
        }

        @Test
        @DisplayName("parte repetida é recusada antes de juntar")
        void parteRepetida() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));

            assertThatThrownBy(() -> service.concluir(9L, List.of(new ParteEnviada(1, "a"), new ParteEnviada(1, "b"))))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("Cada parte pode ser informada uma vez só.");
            verify(storage, never()).concluirMultipart(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("falha ao juntar (parte faltando ou ETag errado) mantém o envio para nova tentativa")
        void falhaAoJuntar() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            doThrow(S3Exception.builder().message("InvalidPart").build())
                    .when(storage)
                    .concluirMultipart(UPLOAD, "upload-1", PARTES);

            assertThatThrownBy(() -> service.concluir(9L, PARTES))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("O envio não foi concluído. Confira se todas as partes foram enviadas.");
            verify(videoAdmService, never()).recusarEnvio(any(), anyString());
            verify(videoAdmService, never()).enfileirar(any(), anyLong());
        }

        @Test
        @DisplayName("arquivo que sumiu depois de juntado")
        void arquivoSumiu() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.concluir(9L, PARTES))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("O vídeo não foi enviado. Comece um novo envio.");
            verify(videoAdmService, never()).enfileirar(any(), anyLong());
        }

        @Test
        @DisplayName("tamanho real diferente do informado: apaga o arquivo e deixa o vídeo com erro")
        void tamanhoDiferente() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1001, "video/mp4")));

            String motivo = "O tamanho do arquivo enviado não confere com o informado. Comece um novo envio.";
            assertThatThrownBy(() -> service.concluir(9L, PARTES))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage(motivo);
            verify(storage).excluir(UPLOAD);
            verify(videoAdmService).recusarEnvio(3L, motivo);
            verify(videoAdmService, never()).enfileirar(any(), anyLong());
        }

        @Test
        @DisplayName("conteúdo sem a assinatura do formato (extensão falsa) é apagado e recusado")
        void assinaturaFalsa() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1000, "video/mp4")));
            when(storage.baixarInicio(UPLOAD, 12)).thenReturn("<html><body>".getBytes());

            assertThatThrownBy(() -> service.concluir(9L, PARTES))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("O arquivo não é um vídeo MP4, MOV, MKV ou WebM.");
            verify(storage).excluir(UPLOAD);
            verify(videoAdmService).recusarEnvio(3L, "O arquivo não é um vídeo MP4, MOV, MKV ou WebM.");
            verify(videoAdmService, never()).enfileirar(any(), anyLong());
        }

        @Test
        @DisplayName("tipo gravado no bucket fora da lista também é recusado")
        void tipoGravadoInvalido() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1000, "text/html")));

            assertThatThrownBy(() -> service.concluir(9L, PARTES)).isInstanceOf(VideoInvalidoException.class);
            verify(storage).excluir(UPLOAD);
            verify(storage, never()).baixarInicio(anyString(), anyInt());
        }
    }

    @Nested
    class Reprocessar {

        @Test
        @DisplayName("com o original no bucket, volta para a fila")
        void reprocessa() {
            Video video = comErro(3);
            when(videoAdmService.buscarPorAula(9L)).thenReturn(video);
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1000, "video/mp4")));
            when(videoAdmService.reprocessar(9L)).thenReturn(video);

            assertThat(service.reprocessar(9L)).isSameAs(video);
        }

        @Test
        @DisplayName("original que expirou no bucket: esquece a referência e pede um envio novo")
        void originalExpirou() {
            when(videoAdmService.buscarPorAula(9L)).thenReturn(comErro(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reprocessar(9L))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("O arquivo original não está mais disponível. Envie o vídeo de novo.");
            verify(videoAdmService).descartarOriginal(3L);
            verify(videoAdmService, never()).reprocessar(any());
        }

        @Test
        @DisplayName("sem original (já apagado) a regra fica com o VideoAdmService")
        void semOriginal() {
            Video video = pronto(3);
            when(videoAdmService.buscarPorAula(9L)).thenReturn(video);
            when(videoAdmService.reprocessar(9L))
                    .thenThrow(new ConflitoException("Só um vídeo com erro pode ser reprocessado."));

            assertThatThrownBy(() -> service.reprocessar(9L)).isInstanceOf(ConflitoException.class);
            verify(storage, never()).info(anyString());
        }
    }

    @Nested
    class Excluir {

        @Test
        @DisplayName("vídeo pronto: apaga o HLS inteiro pelo prefixo")
        void videoPronto() {
            when(videoAdmService.excluir(9L)).thenReturn(pronto(3));

            service.excluir(9L);

            verify(storage).excluirPrefixo("videos/9/1/");
            verify(storage, never()).excluir(anyString());
            verify(storage, never()).abortarMultipart(anyString(), anyString());
        }

        @Test
        @DisplayName("envio aberto é abortado; original com erro é apagado")
        void envioEOriginal() {
            when(videoAdmService.excluir(9L)).thenReturn(aguardandoEnvio(3)).thenReturn(comErro(3));

            service.excluir(9L);
            service.excluir(9L);

            verify(storage).abortarMultipart(UPLOAD, "upload-1");
            verify(storage).excluir(UPLOAD);
            verify(storage, never()).excluirPrefixo(anyString());
        }
    }

    @Test
    @DisplayName("thumbnail: URL temporária só com vídeo convertido")
    void thumbnail() throws Exception {
        when(storage.urlDownload("videos/9/1/thumbnail.jpg", Duration.ofHours(1)))
                .thenReturn(url("thumb"));

        assertThat(service.urlThumbnail(pronto(3))).contains(url("thumb"));
        assertThat(service.urlThumbnail(aguardandoEnvio(3))).isEmpty();
    }

    @Test
    @DisplayName("tamanho máximo legível em GB ou MB")
    void legivel() {
        assertThat(EnvioVideoService.legivel(DataSize.ofGigabytes(5))).isEqualTo("5 GB");
        assertThat(EnvioVideoService.legivel(DataSize.ofMegabytes(500))).isEqualTo("500 MB");
        assertThat(EnvioVideoService.prefixoHls("videos/9/1/master.m3u8")).isEqualTo("videos/9/1/");
    }
}
`````

### 2. Migrations

`backend/src/main/resources/db/migration/V9__criar_progresso_aulas.sql`:

`````sql
-- Progresso do aluno em cada aula: onde parou no vídeo, se já viu e se marcou para revisar.
-- Aula excluída leva o progresso junto (é dado derivado; não deve impedir a exclusão).
CREATE TABLE progresso_aulas (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id        BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    aula_id           BIGINT       NOT NULL REFERENCES aulas (id) ON DELETE CASCADE,
    posicao_segundos  INT          NOT NULL DEFAULT 0,
    vista             BOOLEAN      NOT NULL DEFAULT FALSE,
    vista_em          TIMESTAMPTZ,
    revisar           BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em         TIMESTAMPTZ  NOT NULL,
    atualizado_em     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_progresso_aulas UNIQUE (usuario_id, aula_id),
    CONSTRAINT ck_progresso_aulas_posicao CHECK (posicao_segundos >= 0)
);

CREATE INDEX ix_progresso_aulas_aula ON progresso_aulas (aula_id);
`````

`backend/src/main/resources/db/migration/V10__criar_acessos_video.sql`:

`````sql
-- Registro de acesso aos vídeos (quem abriu a playlist, de onde e quando). Retenção limitada (LGPD):
-- o worker apaga diariamente o que passou de app.video.retencao-log-acesso (6 meses, o prazo do
-- Marco Civil da Internet para registros de acesso a aplicações).
CREATE TABLE acessos_video (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id   BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    aula_id      BIGINT       NOT NULL REFERENCES aulas (id) ON DELETE CASCADE,
    ip           VARCHAR(45)  NOT NULL,
    user_agent   VARCHAR(255),
    acessado_em  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_acessos_video_acessado_em ON acessos_video (acessado_em);
CREATE INDEX ix_acessos_video_usuario ON acessos_video (usuario_id, acessado_em);
`````

### 3. Playlist assinada e log de acesso

`backend/src/main/java/br/com/celke/cursos/video/domain/ReescritaPlaylist.java`:

`````java
package br.com.celke.cursos.video.domain;

import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reescreve as playlists HLS geradas pelo worker para entrega ao aluno. O bucket é privado: a master
 * aponta para as playlists das variantes servidas pela API, e cada variante aponta para os segmentos
 * por URLs pré-assinadas. Só nomes no formato gerado pelo worker são aceitos; qualquer outra referência
 * indica playlist adulterada.
 */
public final class ReescritaPlaylist {

    private static final Pattern VARIANTE = Pattern.compile("(\\d{1,4}p)/index\\.m3u8");
    private static final Pattern SEGMENTO = Pattern.compile("seg_\\d{4,6}\\.ts");

    private ReescritaPlaylist() {}

    /**
     * Master: {@code 720p/index.m3u8} vira {@code video/720p.m3u8}, relativo ao endereço da master na
     * API ({@code /api/aulas/<id>/video.m3u8}).
     */
    public static String master(String conteudo, Set<String> variantes) {
        return reescrever(conteudo, uri -> {
            Matcher m = VARIANTE.matcher(uri);
            if (!m.matches() || !variantes.contains(m.group(1))) {
                throw new IllegalStateException("Referência inesperada na master playlist: " + uri);
            }
            return "video/" + m.group(1) + ".m3u8";
        });
    }

    /** Variante: cada segmento vira a URL que {@code assinar} devolve para o nome dele. */
    public static String variante(String conteudo, UnaryOperator<String> assinar) {
        return reescrever(conteudo, uri -> {
            if (!SEGMENTO.matcher(uri).matches()) {
                throw new IllegalStateException("Referência inesperada na playlist da variante: " + uri);
            }
            return assinar.apply(uri);
        });
    }

    /** Linhas vazias e de tag ({@code #...}) ficam como estão; as outras são URIs. */
    private static String reescrever(String conteudo, UnaryOperator<String> uri) {
        return conteudo.lines()
                        .map(linha -> linha.isBlank() || linha.startsWith("#") ? linha : uri.apply(linha.strip()))
                        .collect(Collectors.joining("\n"))
                + "\n";
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/domain/AcessoVideo.java`:

`````java
package br.com.celke.cursos.video.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Abertura do vídeo de uma aula por um usuário (log de acesso, com retenção limitada). */
@Entity
@Table(name = "acessos_video")
public class AcessoVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "aula_id", nullable = false, updatable = false)
    private Long aulaId;

    @Column(nullable = false, length = 45, updatable = false)
    private String ip;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Column(name = "acessado_em", nullable = false, updatable = false)
    private Instant acessadoEm;

    protected AcessoVideo() {}

    public AcessoVideo(Long usuarioId, Long aulaId, String ip, String userAgent, Instant acessadoEm) {
        this.usuarioId = usuarioId;
        this.aulaId = aulaId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.acessadoEm = acessadoEm;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getAulaId() {
        return aulaId;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getAcessadoEm() {
        return acessadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/infra/AcessoVideoRepository.java`:

`````java
package br.com.celke.cursos.video.infra;

import br.com.celke.cursos.video.domain.AcessoVideo;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AcessoVideoRepository extends JpaRepository<AcessoVideo, Long> {

    List<AcessoVideo> findByUsuarioIdAndAulaIdOrderByAcessadoEm(Long usuarioId, Long aulaId);

    /** Apaga os registros anteriores ao limite; devolve quantos saíram. */
    @Modifying
    @Query("delete from AcessoVideo a where a.acessadoEm < :limite")
    int apagarAnteriores(Instant limite);
}
`````

`backend/src/main/java/br/com/celke/cursos/video/application/LogAcessoVideoService.java`:

`````java
package br.com.celke.cursos.video.application;

import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.video.domain.AcessoVideo;
import br.com.celke.cursos.video.infra.AcessoVideoRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Log de acesso aos vídeos: cada abertura da playlist de uma aula fica registrada com o IP e o
 * navegador, e sai do banco quando passa da retenção configurada (LGPD: o dado não fica para sempre).
 */
@Service
public class LogAcessoVideoService {

    private static final Logger LOG = LoggerFactory.getLogger(LogAcessoVideoService.class);

    private final AcessoVideoRepository acessos;
    private final VideoProperties props;
    private final Clock clock;

    public LogAcessoVideoService(AcessoVideoRepository acessos, VideoProperties props, Clock clock) {
        this.acessos = acessos;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public void registrar(Long usuarioId, Long aulaId, OrigemRequisicao origem) {
        acessos.save(new AcessoVideo(usuarioId, aulaId, origem.ip(), origem.userAgent(), clock.instant()));
    }

    /** Apaga o que passou da retenção; devolve quantos registros saíram. */
    @Transactional
    public int apagarVencidos() {
        Instant limite = clock.instant().minus(props.retencaoLogAcesso());
        int apagados = acessos.apagarAnteriores(limite);
        LOG.info("Log de acesso aos vídeos: {} registro(s) anteriores a {} apagados", apagados, limite);
        return apagados;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/application/LimpezaLogAcessoVideo.java`:

`````java
package br.com.celke.cursos.video.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Aplica a retenção do log de acesso uma vez por dia. Só no worker, que tem o agendamento ligado. */
@Component
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
public class LimpezaLogAcessoVideo {

    private final LogAcessoVideoService logAcessoVideoService;

    public LimpezaLogAcessoVideo(LogAcessoVideoService logAcessoVideoService) {
        this.logAcessoVideoService = logAcessoVideoService;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "America/Sao_Paulo")
    public void limpar() {
        logAcessoVideoService.apagarVencidos();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/application/PlaylistService.java`:

`````java
package br.com.celke.cursos.video.application;

import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.video.domain.ReescritaPlaylist;
import br.com.celke.cursos.video.domain.Video;
import br.com.celke.cursos.video.infra.VideoRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Entrega do vídeo ao aluno sem abrir o bucket: a API confere o acesso à aula ({@link AcessoAulaService})
 * a cada playlist e devolve os segmentos por URLs pré-assinadas, válidas pela duração do vídeo mais
 * uma margem. Substitui os cookies assinados de CDN, que esta hospedagem não tem.
 */
@Service
public class PlaylistService {

    private final AcessoAulaService acessoAulaService;
    private final VideoRepository videos;
    private final StorageService storage;
    private final LogAcessoVideoService logAcessoVideoService;
    private final VideoProperties props;

    public PlaylistService(
            AcessoAulaService acessoAulaService,
            VideoRepository videos,
            StorageService storage,
            LogAcessoVideoService logAcessoVideoService,
            VideoProperties props) {
        this.acessoAulaService = acessoAulaService;
        this.videos = videos;
        this.storage = storage;
        this.logAcessoVideoService = logAcessoVideoService;
        this.props = props;
    }

    /** Master playlist com as variantes servidas pela API; registra o acesso no log. */
    public String master(Long usuarioId, Long aulaId, OrigemRequisicao origem) {
        Video video = publicado(usuarioId, aulaId);
        String master = ReescritaPlaylist.master(texto(video.getPlaylistChave()), variantes(video));
        logAcessoVideoService.registrar(usuarioId, aulaId, origem);
        return master;
    }

    /** Playlist de uma variante com cada segmento por URL pré-assinada. */
    public String variante(Long usuarioId, Long aulaId, String variante) {
        Video video = publicado(usuarioId, aulaId);
        if (!variantes(video).contains(variante)) {
            throw new NaoEncontradoException("Variante não encontrada.");
        }
        String pasta = prefixo(video) + variante + "/";
        Duration validade = validade(video);
        return ReescritaPlaylist.variante(
                texto(pasta + "index.m3u8"),
                segmento -> storage.urlDownload(pasta + segmento, validade).toString());
    }

    /** Duração do vídeo mais a margem: dá para assistir inteiro, com pausas, sem a URL vencer. */
    Duration validade(Video video) {
        int duracao = video.getDuracaoSegundos() == null ? 0 : video.getDuracaoSegundos();
        return Duration.ofSeconds(duracao).plus(props.margemPlaylist());
    }

    /** Acesso à aula primeiro (404/403 da política), depois o vídeo publicado (404 sem vídeo). */
    private Video publicado(Long usuarioId, Long aulaId) {
        acessoAulaService.exigirAcesso(usuarioId, aulaId);
        return videos.findByAulaId(aulaId)
                .filter(v -> v.getPlaylistChave() != null)
                .orElseThrow(VideoAdmService::semVideo);
    }

    private String texto(String chave) {
        return new String(storage.baixar(chave), StandardCharsets.UTF_8);
    }

    private static Set<String> variantes(Video video) {
        return Set.of(video.getVariantes().split(","));
    }

    private static String prefixo(Video video) {
        return EnvioVideoService.prefixoHls(video.getPlaylistChave());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/api/AulaVideoController.java`:

`````java
package br.com.celke.cursos.video.api;

import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.video.application.PlaylistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Playlists HLS da aula para o player (hls.js com o token no cabeçalho). Nunca vão para cache: as URLs
 * dos segmentos são pessoais e vencem.
 */
@RestController
@RequestMapping("/api/aulas/{aulaId}")
@Tag(name = "Vídeo da aula")
public class AulaVideoController {

    static final MediaType HLS = MediaType.parseMediaType("application/vnd.apple.mpegurl");

    private final PlaylistService playlistService;

    public AulaVideoController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @GetMapping("/video.m3u8")
    @Operation(
            summary = "Master playlist do vídeo",
            description = "Exige acesso à aula (matrícula vigente ou aula gratuita). Lista as variantes; o acesso"
                    + " fica registrado no log.")
    public ResponseEntity<String> master(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long aulaId, HttpServletRequest request) {
        return hls(playlistService.master(usuario(jwt), aulaId, OrigemRequisicao.de(request)));
    }

    @GetMapping("/video/{variante:[0-9]+p}.m3u8")
    @Operation(
            summary = "Playlist de uma variante",
            description = "Segmentos por URLs pré-assinadas, válidas pela duração do vídeo mais uma margem.")
    public ResponseEntity<String> variante(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long aulaId, @PathVariable String variante) {
        return hls(playlistService.variante(usuario(jwt), aulaId, variante));
    }

    private static ResponseEntity<String> hls(String playlist) {
        return ResponseEntity.ok()
                .contentType(HLS)
                .cacheControl(CacheControl.noStore())
                .body(playlist);
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
`````

### 4. Progresso

`backend/src/main/java/br/com/celke/cursos/progresso/domain/ProgressoAula.java`:

`````java
package br.com.celke.cursos.progresso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Progresso de um aluno numa aula. Guarda só os ids, sem relacionamento JPA com outros módulos. */
@Entity
@Table(name = "progresso_aulas")
public class ProgressoAula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "aula_id", nullable = false, updatable = false)
    private Long aulaId;

    /** Onde o aluno parou no vídeo. */
    @Column(name = "posicao_segundos", nullable = false)
    private int posicaoSegundos;

    @Column(nullable = false)
    private boolean vista;

    @Column(name = "vista_em")
    private Instant vistaEm;

    /** Marcada pelo aluno para ver de novo. */
    @Column(nullable = false)
    private boolean revisar;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected ProgressoAula() {}

    public ProgressoAula(Long usuarioId, Long aulaId, Instant agora) {
        this.usuarioId = usuarioId;
        this.aulaId = aulaId;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Aplica só o que veio preenchido; desmarcar a aula vista apaga a data. */
    public void atualizar(Integer posicaoSegundos, Boolean vista, Boolean revisar, Instant agora) {
        if (posicaoSegundos != null) {
            if (posicaoSegundos < 0) {
                throw new IllegalArgumentException("A posição não pode ser negativa.");
            }
            this.posicaoSegundos = posicaoSegundos;
        }
        if (vista != null && vista != this.vista) {
            this.vista = vista;
            this.vistaEm = vista ? agora : null;
        }
        if (revisar != null) {
            this.revisar = revisar;
        }
        this.atualizadoEm = agora;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getAulaId() {
        return aulaId;
    }

    public int getPosicaoSegundos() {
        return posicaoSegundos;
    }

    public boolean isVista() {
        return vista;
    }

    public Instant getVistaEm() {
        return vistaEm;
    }

    public boolean isRevisar() {
        return revisar;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/progresso/domain/PercentualConclusao.java`:

`````java
package br.com.celke.cursos.progresso.domain;

/** Percentual de aulas vistas na turma, arredondado para baixo: 100% só com todas as aulas vistas. */
public final class PercentualConclusao {

    private PercentualConclusao() {}

    /** Turma sem aulas fica em 0%. */
    public static int de(int vistas, int total) {
        if (vistas < 0 || total < 0 || vistas > total) {
            throw new IllegalArgumentException("Contagem inválida: %d de %d.".formatted(vistas, total));
        }
        return total == 0 ? 0 : (int) ((long) vistas * 100 / total);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/progresso/infra/ProgressoAulaRepository.java`:

`````java
package br.com.celke.cursos.progresso.infra;

import br.com.celke.cursos.progresso.domain.ProgressoAula;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressoAulaRepository extends JpaRepository<ProgressoAula, Long> {

    Optional<ProgressoAula> findByUsuarioIdAndAulaId(Long usuarioId, Long aulaId);

    /** Progresso do aluno em várias aulas numa consulta só (turma). */
    List<ProgressoAula> findByUsuarioIdAndAulaIdIn(Long usuarioId, Collection<Long> aulaIds);
}
`````

`backend/src/main/java/br/com/celke/cursos/progresso/application/ProgressoService.java`:

`````java
package br.com.celke.cursos.progresso.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaConteudo;
import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.progresso.domain.PercentualConclusao;
import br.com.celke.cursos.progresso.domain.ProgressoAula;
import br.com.celke.cursos.progresso.infra.ProgressoAulaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Progresso do aluno: posição no vídeo, aula vista e marcada para revisar, e o percentual da turma.
 * Sempre no escopo do usuário do token e pela {@link AcessoAulaService}: sem acesso à aula, não há
 * progresso para ler nem gravar.
 */
@Service
public class ProgressoService {

    private final ProgressoAulaRepository progressos;
    private final AcessoAulaService acessoAulaService;
    private final ConteudoTurmaService conteudoTurmaService;
    private final TransactionTemplate transacao;
    private final Clock clock;

    public ProgressoService(
            ProgressoAulaRepository progressos,
            AcessoAulaService acessoAulaService,
            ConteudoTurmaService conteudoTurmaService,
            TransactionTemplate transacao,
            Clock clock) {
        this.progressos = progressos;
        this.acessoAulaService = acessoAulaService;
        this.conteudoTurmaService = conteudoTurmaService;
        this.transacao = transacao;
        this.clock = clock;
    }

    /** Progresso na aula; aula nunca aberta volta zerada. */
    @Transactional(readOnly = true)
    public Progresso daAula(Long usuarioId, Long aulaId) {
        acessoAulaService.exigirAcesso(usuarioId, aulaId);
        return progressos
                .findByUsuarioIdAndAulaId(usuarioId, aulaId)
                .map(Progresso::de)
                .orElse(Progresso.vazio(aulaId));
    }

    /**
     * Grava os campos preenchidos (os nulos ficam como estão). O primeiro registro da aula pode
     * chegar em duas requisições ao mesmo tempo (duas abas): a restrição única recusa uma, que é
     * refeita sobre o registro que ficou.
     */
    public Progresso atualizar(Long usuarioId, Long aulaId, Integer posicaoSegundos, Boolean vista, Boolean revisar) {
        acessoAulaService.exigirAcesso(usuarioId, aulaId);
        try {
            return gravar(usuarioId, aulaId, posicaoSegundos, vista, revisar);
        } catch (DataIntegrityViolationException e) {
            return gravar(usuarioId, aulaId, posicaoSegundos, vista, revisar);
        }
    }

    private Progresso gravar(Long usuarioId, Long aulaId, Integer posicaoSegundos, Boolean vista, Boolean revisar) {
        return transacao.execute(status -> {
            Instant agora = clock.instant();
            ProgressoAula progresso = progressos
                    .findByUsuarioIdAndAulaId(usuarioId, aulaId)
                    .orElseGet(() -> new ProgressoAula(usuarioId, aulaId, agora));
            progresso.atualizar(posicaoSegundos, vista, revisar, agora);
            return Progresso.de(progressos.saveAndFlush(progresso));
        });
    }

    /** Percentual de aulas vistas entre as aulas ativas da turma, com o progresso de cada uma. */
    @Transactional(readOnly = true)
    public ProgressoTurma daTurma(Long usuarioId, Long turmaId) {
        acessoAulaService.exigirAcessoTurma(usuarioId, turmaId);
        List<Long> aulaIds = conteudoTurmaService.conteudoAtivo(turmaId).stream()
                .flatMap(modulo -> modulo.aulas().stream())
                .map(AulaConteudo::id)
                .toList();
        Map<Long, ProgressoAula> porAula = aulaIds.isEmpty()
                ? Map.of()
                : progressos.findByUsuarioIdAndAulaIdIn(usuarioId, aulaIds).stream()
                        .collect(Collectors.toMap(ProgressoAula::getAulaId, Function.identity()));
        List<Progresso> aulas = aulaIds.stream()
                .map(id -> porAula.containsKey(id) ? Progresso.de(porAula.get(id)) : Progresso.vazio(id))
                .toList();
        int vistas = (int) aulas.stream().filter(Progresso::vista).count();
        return new ProgressoTurma(turmaId, aulas.size(), vistas, PercentualConclusao.de(vistas, aulas.size()), aulas);
    }

    public record Progresso(Long aulaId, int posicaoSegundos, boolean vista, Instant vistaEm, boolean revisar) {

        static Progresso de(ProgressoAula p) {
            return new Progresso(p.getAulaId(), p.getPosicaoSegundos(), p.isVista(), p.getVistaEm(), p.isRevisar());
        }

        static Progresso vazio(Long aulaId) {
            return new Progresso(aulaId, 0, false, null, false);
        }
    }

    /** Aulas na ordem do curso (módulos e aulas ativos). */
    public record ProgressoTurma(
            Long turmaId, int totalAulas, int aulasVistas, int percentual, List<Progresso> aulas) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/progresso/api/ProgressoRequests.java`:

`````java
package br.com.celke.cursos.progresso.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Corpos das rotas de progresso do aluno. */
public final class ProgressoRequests {

    /** Maior posição aceita: 24 horas de vídeo. */
    public static final int POSICAO_MAXIMA = 86_400;

    private ProgressoRequests() {}

    /**
     * Só os campos enviados mudam: o player manda a posição de tempos em tempos; a tela da aula, vista e
     * revisar.
     */
    public record Atualizar(@Min(0) @Max(POSICAO_MAXIMA) Integer posicaoSegundos, Boolean vista, Boolean revisar) {

        @JsonIgnore
        @Schema(hidden = true)
        @AssertTrue(message = "informe a posição, vista ou revisar")
        public boolean isAlgumCampo() {
            return posicaoSegundos != null || vista != null || revisar != null;
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/progresso/api/ProgressoController.java`:

`````java
package br.com.celke.cursos.progresso.api;

import br.com.celke.cursos.progresso.application.ProgressoService;
import br.com.celke.cursos.progresso.application.ProgressoService.Progresso;
import br.com.celke.cursos.progresso.application.ProgressoService.ProgressoTurma;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Progresso do aluno, sempre do usuário do token (o id nunca vem do cliente). */
@RestController
@RequestMapping("/api/aluno")
@Tag(name = "Área do aluno — Progresso")
public class ProgressoController {

    private final ProgressoService progressoService;

    public ProgressoController(ProgressoService progressoService) {
        this.progressoService = progressoService;
    }

    @GetMapping("/aulas/{aulaId}/progresso")
    @Operation(summary = "Progresso na aula", description = "Posição no vídeo, vista e revisar; zerado se nunca abriu.")
    public Progresso daAula(@AuthenticationPrincipal Jwt jwt, @PathVariable Long aulaId) {
        return progressoService.daAula(usuario(jwt), aulaId);
    }

    @PatchMapping("/aulas/{aulaId}/progresso")
    @Operation(
            summary = "Atualizar o progresso na aula",
            description = "Só os campos enviados mudam (posição a cada poucos segundos, vista, revisar).")
    public Progresso atualizar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long aulaId,
            @Valid @RequestBody ProgressoRequests.Atualizar request) {
        return progressoService.atualizar(
                usuario(jwt), aulaId, request.posicaoSegundos(), request.vista(), request.revisar());
    }

    @GetMapping("/turmas/{turmaId}/progresso")
    @Operation(
            summary = "Progresso na turma",
            description = "Percentual de aulas vistas e o progresso de cada aula ativa. Exige matrícula vigente.")
    public ProgressoTurma daTurma(@AuthenticationPrincipal Jwt jwt, @PathVariable Long turmaId) {
        return progressoService.daTurma(usuario(jwt), turmaId);
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
`````

### 5. Testes unitários

`backend/src/test/java/br/com/celke/cursos/video/domain/ReescritaPlaylistTest.java`:

`````java
package br.com.celke.cursos.video.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReescritaPlaylistTest {

    /** Master como o ffmpeg do worker gera. */
    private static final String MASTER = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-STREAM-INF:BANDWIDTH=266709,AVERAGE-BANDWIDTH=266709,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2"
            720p/index.m3u8

            #EXT-X-STREAM-INF:BANDWIDTH=510559,AVERAGE-BANDWIDTH=510559,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            1080p/index.m3u8
            """;

    private static final String VARIANTE = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXT-X-INDEPENDENT-SEGMENTS
            #EXTINF:6.000000,
            seg_0000.ts
            #EXTINF:2.500000,
            seg_0001.ts
            #EXT-X-ENDLIST
            """;

    @Test
    @DisplayName("master: as variantes passam a apontar para a API, relativas à master")
    void master() {
        String reescrita = ReescritaPlaylist.master(MASTER, Set.of("720p", "1080p"));

        assertThat(reescrita)
                .contains("\nvideo/720p.m3u8\n")
                .contains("\nvideo/1080p.m3u8\n")
                .doesNotContain("index.m3u8")
                .contains("RESOLUTION=1920x1080")
                .startsWith("#EXTM3U\n")
                .endsWith("\n");
    }

    @Test
    @DisplayName("master com variante que o vídeo não tem é recusada")
    void masterComVarianteDesconhecida() {
        assertThatThrownBy(() -> ReescritaPlaylist.master(MASTER, Set.of("720p")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Referência inesperada na master playlist: 1080p/index.m3u8");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://outro.site/x.m3u8", "../../segredo/index.m3u8", "720p/../index.m3u8"})
    @DisplayName("master com referência fora do formato do worker é recusada")
    void masterAdulterada(String uri) {
        String adulterada = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n" + uri + "\n";

        assertThatThrownBy(() -> ReescritaPlaylist.master(adulterada, Set.of("720p")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("variante: cada segmento vira a URL assinada; as tags ficam como estão")
    void variante() {
        String reescrita = ReescritaPlaylist.variante(VARIANTE, seg -> "https://media.celke.uno/assinado/" + seg);

        assertThat(reescrita.lines())
                .contains(
                        "https://media.celke.uno/assinado/seg_0000.ts",
                        "https://media.celke.uno/assinado/seg_0001.ts",
                        "#EXTINF:2.500000,",
                        "#EXT-X-ENDLIST")
                .doesNotContain("seg_0000.ts");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outro/seg_0000.ts", "seg_0000.mp4", "https://x/seg_0000.ts", "seg_00.ts"})
    @DisplayName("variante com segmento fora do formato do worker é recusada (sem assinar nada)")
    void varianteAdulterada(String uri) {
        String adulterada = "#EXTM3U\n#EXTINF:6.0,\n" + uri + "\n";

        assertThatThrownBy(() -> ReescritaPlaylist.variante(adulterada, seg -> {
                    throw new AssertionError("não deveria assinar " + seg);
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Referência inesperada na playlist da variante: " + uri);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/application/PlaylistServiceTest.java`:

`````java
package br.com.celke.cursos.video.application;

import static br.com.celke.cursos.support.fixtures.VideoFixture.PLAYLIST;
import static br.com.celke.cursos.support.fixtures.VideoFixture.aguardandoEnvio;
import static br.com.celke.cursos.support.fixtures.VideoFixture.pronto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.video.domain.Video;
import br.com.celke.cursos.video.infra.VideoRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    private static final OrigemRequisicao ORIGEM = new OrigemRequisicao("203.0.113.9", "hls.js");
    private static final Duration MARGEM = Duration.ofHours(2);

    @Mock
    private AcessoAulaService acessoAulaService;

    @Mock
    private VideoRepository videos;

    @Mock
    private StorageService storage;

    @Mock
    private LogAcessoVideoService logAcessoVideoService;

    private PlaylistService service;

    @BeforeEach
    void criar() {
        var props = new VideoProperties(
                DataSize.ofGigabytes(5),
                Duration.ofHours(6),
                "ffmpeg",
                "ffprobe",
                null,
                "veryfast",
                24,
                Path.of("/tmp/videos"),
                MARGEM,
                Duration.ofDays(180));
        service = new PlaylistService(acessoAulaService, videos, storage, logAcessoVideoService, props);
    }

    private static byte[] bytes(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("master: confere o acesso, reescreve as variantes e registra o acesso")
    void master() {
        when(videos.findByAulaId(9L)).thenReturn(Optional.of(pronto(3)));
        when(storage.baixar(PLAYLIST)).thenReturn(bytes("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n720p/index.m3u8\n"));

        assertThat(service.master(7L, 9L, ORIGEM)).contains("video/720p.m3u8");
        verify(acessoAulaService).exigirAcesso(7L, 9L);
        verify(logAcessoVideoService).registrar(7L, 9L, ORIGEM);
    }

    @Test
    @DisplayName("variante: segmentos assinados pela duração do vídeo mais a margem")
    void variante() throws Exception {
        when(videos.findByAulaId(9L)).thenReturn(Optional.of(pronto(3)));
        when(storage.baixar("videos/9/1/720p/index.m3u8")).thenReturn(bytes("#EXTINF:3.0,\nseg_0000.ts\n"));
        when(storage.urlDownload(
                        "videos/9/1/720p/seg_0000.ts", Duration.ofSeconds(3).plus(MARGEM)))
                .thenReturn(URI.create("http://media.localhost/assinado").toURL());

        assertThat(service.variante(7L, 9L, "720p")).contains("http://media.localhost/assinado");
        verify(logAcessoVideoService, never()).registrar(any(), any(), any());
    }

    @Test
    @DisplayName("variante que o vídeo não tem responde 404 sem ler o bucket")
    void varianteInexistente() {
        when(videos.findByAulaId(9L)).thenReturn(Optional.of(pronto(3)));

        assertThatThrownBy(() -> service.variante(7L, 9L, "1080p"))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Variante não encontrada.");
        verify(storage, never()).baixar(anyString());
    }

    @Test
    @DisplayName("sem acesso à aula: a política responde antes de qualquer leitura, e nada vai para o log")
    void semAcesso() {
        doThrow(new AcessoNegadoException("Você não tem acesso a esta aula."))
                .when(acessoAulaService)
                .exigirAcesso(7L, 9L);

        assertThatThrownBy(() -> service.master(7L, 9L, ORIGEM)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.variante(7L, 9L, "720p")).isInstanceOf(AcessoNegadoException.class);
        verify(videos, never()).findByAulaId(any());
        verify(logAcessoVideoService, never()).registrar(any(), any(), any());
    }

    @Test
    @DisplayName("aula sem vídeo ou com vídeo ainda não publicado responde 404")
    void semVideoPublicado() {
        when(videos.findByAulaId(9L)).thenReturn(Optional.empty()).thenReturn(Optional.of(aguardandoEnvio(3)));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.master(7L, 9L, ORIGEM))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("A aula não tem vídeo.");
        }
        verify(logAcessoVideoService, never()).registrar(any(), any(), any());
    }

    @Test
    @DisplayName("validade: duração + margem; sem duração conhecida, só a margem")
    void validade() {
        Video semDuracao = aguardandoEnvio(3);

        assertThat(service.validade(pronto(3))).isEqualTo(Duration.ofSeconds(3).plus(MARGEM));
        assertThat(service.validade(semDuracao)).isEqualTo(MARGEM);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/application/LogAcessoVideoServiceTest.java`:

`````java
package br.com.celke.cursos.video.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.video.domain.AcessoVideo;
import br.com.celke.cursos.video.infra.AcessoVideoRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class LogAcessoVideoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-07-01T12:00:00Z");

    @Mock
    private AcessoVideoRepository acessos;

    private LogAcessoVideoService service;

    @BeforeEach
    void criar() {
        var props = new VideoProperties(
                DataSize.ofGigabytes(5),
                Duration.ofHours(6),
                "ffmpeg",
                "ffprobe",
                null,
                "veryfast",
                24,
                Path.of("/tmp/videos"),
                Duration.ofHours(2),
                Duration.ofDays(180));
        service = new LogAcessoVideoService(acessos, props, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("registra usuário, aula, IP, navegador e o instante")
    void registra() {
        service.registrar(7L, 9L, new OrigemRequisicao("203.0.113.9", "hls.js"));

        ArgumentCaptor<AcessoVideo> captor = ArgumentCaptor.forClass(AcessoVideo.class);
        verify(acessos).save(captor.capture());
        AcessoVideo acesso = captor.getValue();
        assertThat(acesso.getUsuarioId()).isEqualTo(7L);
        assertThat(acesso.getAulaId()).isEqualTo(9L);
        assertThat(acesso.getIp()).isEqualTo("203.0.113.9");
        assertThat(acesso.getUserAgent()).isEqualTo("hls.js");
        assertThat(acesso.getAcessadoEm()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("apaga o que passou da retenção (180 dias antes de agora)")
    void apagaVencidos() {
        when(acessos.apagarAnteriores(AGORA.minus(Duration.ofDays(180)))).thenReturn(4);

        assertThat(service.apagarVencidos()).isEqualTo(4);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/progresso/domain/PercentualConclusaoTest.java`:

`````java
package br.com.celke.cursos.progresso.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PercentualConclusaoTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "turma sem aulas,                0, 0,   0",
        "nenhuma aula vista,             0, 10,  0",
        "todas as aulas vistas,          10, 10, 100",
        "uma de uma,                     1, 1,   100",
        "metade,                         5, 10,  50",
        "um terço arredonda para baixo,  1, 3,   33",
        "dois terços arredonda para baixo, 2, 3, 66",
        "falta uma de 200 não chega a 100, 199, 200, 99",
        "uma de 1000 fica em 0,          1, 1000, 0",
    })
    @DisplayName("percentual de aulas vistas, arredondado para baixo")
    void percentual(String caso, int vistas, int total, int esperado) {
        assertThat(PercentualConclusao.de(vistas, total)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0} de {1}")
    @CsvSource({"-1, 10", "1, -1", "11, 10", "1, 0"})
    @DisplayName("contagem negativa ou com mais vistas que aulas é recusada")
    void contagemInvalida(int vistas, int total) {
        assertThatThrownBy(() -> PercentualConclusao.de(vistas, total))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Contagem inválida: %d de %d.".formatted(vistas, total));
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/progresso/domain/ProgressoAulaTest.java`:

`````java
package br.com.celke.cursos.progresso.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProgressoAulaTest {

    private static final Instant T0 = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(600);

    @Test
    @DisplayName("progresso novo começa zerado")
    void novo() {
        ProgressoAula progresso = new ProgressoAula(7L, 9L, T0);

        assertThat(progresso.getPosicaoSegundos()).isZero();
        assertThat(progresso.isVista()).isFalse();
        assertThat(progresso.isRevisar()).isFalse();
        assertThat(progresso.getVistaEm()).isNull();
    }

    @Test
    @DisplayName("só os campos preenchidos mudam")
    void parcial() {
        ProgressoAula progresso = new ProgressoAula(7L, 9L, T0);
        progresso.atualizar(120, true, true, T0);

        progresso.atualizar(300, null, null, T1);

        assertThat(progresso.getPosicaoSegundos()).isEqualTo(300);
        assertThat(progresso.isVista()).isTrue();
        assertThat(progresso.isRevisar()).isTrue();
        assertThat(progresso.getVistaEm()).isEqualTo(T0);
        assertThat(progresso.getAtualizadoEm()).isEqualTo(T1);
    }

    @Test
    @DisplayName("marcar como vista guarda quando; marcar de novo mantém a data; desmarcar apaga")
    void vista() {
        ProgressoAula progresso = new ProgressoAula(7L, 9L, T0);

        progresso.atualizar(null, true, null, T0);
        assertThat(progresso.getVistaEm()).isEqualTo(T0);

        progresso.atualizar(null, true, null, T1);
        assertThat(progresso.getVistaEm()).isEqualTo(T0);

        progresso.atualizar(null, false, false, T1);
        assertThat(progresso.isVista()).isFalse();
        assertThat(progresso.getVistaEm()).isNull();
        assertThat(progresso.isRevisar()).isFalse();
    }

    @Test
    @DisplayName("posição zero é aceita; negativa é recusada sem mudar nada")
    void posicao() {
        ProgressoAula progresso = new ProgressoAula(7L, 9L, T0);
        progresso.atualizar(50, null, null, T0);

        progresso.atualizar(0, null, null, T0);
        assertThat(progresso.getPosicaoSegundos()).isZero();

        assertThatThrownBy(() -> progresso.atualizar(-1, true, null, T1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A posição não pode ser negativa.");
        assertThat(progresso.isVista()).isFalse();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/progresso/api/ProgressoRequestsTest.java`:

`````java
package br.com.celke.cursos.progresso.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.progresso.api.ProgressoRequests.Atualizar;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProgressoRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object dto) {
        return VALIDATOR.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("qualquer um dos campos sozinho basta")
    void umCampoBasta() {
        assertThat(camposInvalidos(new Atualizar(10, null, null))).isEmpty();
        assertThat(camposInvalidos(new Atualizar(null, false, null))).isEmpty();
        assertThat(camposInvalidos(new Atualizar(null, null, true))).isEmpty();
    }

    @Test
    @DisplayName("corpo sem nenhum campo é recusado")
    void nenhumCampo() {
        assertThat(camposInvalidos(new Atualizar(null, null, null))).containsExactly("algumCampo");
    }

    @ParameterizedTest(name = "posição {0} → válida: {1}")
    @CsvSource({"-1, false", "0, true", "86400, true", "86401, false"})
    @DisplayName("posição de 0 a 24 horas")
    void posicao(int posicao, boolean valida) {
        assertThat(camposInvalidos(new Atualizar(posicao, null, null)))
                .isEqualTo(valida ? Set.of() : Set.of("posicaoSegundos"));
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/progresso/application/ProgressoServiceTest.java`:

`````java
package br.com.celke.cursos.progresso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.progresso.application.ProgressoService.Progresso;
import br.com.celke.cursos.progresso.domain.ProgressoAula;
import br.com.celke.cursos.progresso.infra.ProgressoAulaRepository;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ProgressoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private ProgressoAulaRepository progressos;

    @Mock
    private AcessoAulaService acessoAulaService;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ProgressoService service;

    @BeforeEach
    void criar() {
        service = new ProgressoService(
                progressos,
                acessoAulaService,
                conteudoTurmaService,
                new TransactionTemplate(transactionManager),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void transacoes() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private static ProgressoAula progresso(long aulaId, int posicao, boolean vista) {
        ProgressoAula p = new ProgressoAula(7L, aulaId, AGORA);
        p.atualizar(posicao, vista, null, AGORA);
        return p;
    }

    @Nested
    class DaAula {

        @Test
        @DisplayName("aula nunca aberta volta zerada")
        void zerada() {
            when(progressos.findByUsuarioIdAndAulaId(7L, 9L)).thenReturn(Optional.empty());

            assertThat(service.daAula(7L, 9L)).isEqualTo(new Progresso(9L, 0, false, null, false));
            verify(acessoAulaService).exigirAcesso(7L, 9L);
        }

        @Test
        @DisplayName("devolve o progresso gravado")
        void gravado() {
            when(progressos.findByUsuarioIdAndAulaId(7L, 9L)).thenReturn(Optional.of(progresso(9L, 95, true)));

            assertThat(service.daAula(7L, 9L)).isEqualTo(new Progresso(9L, 95, true, AGORA, false));
        }

        @Test
        @DisplayName("sem acesso à aula, nada é lido")
        void semAcesso() {
            doThrow(new AcessoNegadoException("sem acesso"))
                    .when(acessoAulaService)
                    .exigirAcesso(7L, 9L);

            assertThatThrownBy(() -> service.daAula(7L, 9L)).isInstanceOf(AcessoNegadoException.class);
            verify(progressos, never()).findByUsuarioIdAndAulaId(any(), any());
        }
    }

    @Nested
    class Atualizar {

        @Test
        @DisplayName("primeiro registro da aula é criado com os campos enviados")
        void cria() {
            transacoes();
            when(progressos.findByUsuarioIdAndAulaId(7L, 9L)).thenReturn(Optional.empty());
            when(progressos.saveAndFlush(any(ProgressoAula.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(service.atualizar(7L, 9L, 42, null, true)).isEqualTo(new Progresso(9L, 42, false, null, true));
        }

        @Test
        @DisplayName("registro existente muda só nos campos enviados")
        void atualiza() {
            transacoes();
            when(progressos.findByUsuarioIdAndAulaId(7L, 9L)).thenReturn(Optional.of(progresso(9L, 95, false)));
            when(progressos.saveAndFlush(any(ProgressoAula.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(service.atualizar(7L, 9L, null, true, null))
                    .isEqualTo(new Progresso(9L, 95, true, AGORA, false));
        }

        @Test
        @DisplayName("duas abas criando ao mesmo tempo: a segunda é refeita sobre o registro que ficou")
        void corrida() {
            transacoes();
            ProgressoAula existente = progresso(9L, 10, false);
            when(progressos.findByUsuarioIdAndAulaId(7L, 9L))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(existente));
            when(progressos.saveAndFlush(any(ProgressoAula.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_progresso_aulas"))
                    .thenAnswer(i -> i.getArgument(0));

            assertThat(service.atualizar(7L, 9L, 60, null, null).posicaoSegundos())
                    .isEqualTo(60);
            assertThat(existente.getPosicaoSegundos()).isEqualTo(60);
        }

        @Test
        @DisplayName("sem acesso à aula, nada é gravado")
        void semAcesso() {
            doThrow(new AcessoNegadoException("sem acesso"))
                    .when(acessoAulaService)
                    .exigirAcesso(7L, 9L);

            assertThatThrownBy(() -> service.atualizar(7L, 9L, 10, null, null))
                    .isInstanceOf(AcessoNegadoException.class);
            verify(progressos, never()).saveAndFlush(any());
        }
    }

    @Nested
    class DaTurma {

        private void conteudo(List<ModuloConteudo> modulos) {
            when(conteudoTurmaService.conteudoAtivo(5L)).thenReturn(modulos);
        }

        @Test
        @DisplayName("percentual das aulas ativas vistas, com cada aula na ordem do curso")
        void percentual() {
            conteudo(List.of(
                    new ModuloConteudo(
                            1L, "M1", List.of(new AulaConteudo(10L, "A", false), new AulaConteudo(11L, "B", false))),
                    new ModuloConteudo(2L, "M2", List.of(new AulaConteudo(12L, "C", true)))));
            when(progressos.findByUsuarioIdAndAulaIdIn(7L, List.of(10L, 11L, 12L)))
                    .thenReturn(List.of(progresso(12L, 30, true), progresso(10L, 5, false)));

            var turma = service.daTurma(7L, 5L);

            verify(acessoAulaService).exigirAcessoTurma(7L, 5L);
            assertThat(turma.totalAulas()).isEqualTo(3);
            assertThat(turma.aulasVistas()).isEqualTo(1);
            assertThat(turma.percentual()).isEqualTo(33);
            assertThat(turma.aulas()).extracting(Progresso::aulaId).containsExactly(10L, 11L, 12L);
            assertThat(turma.aulas().get(1)).isEqualTo(new Progresso(11L, 0, false, null, false));
        }

        @Test
        @DisplayName("todas as aulas vistas: 100%")
        void todas() {
            conteudo(List.of(new ModuloConteudo(1L, "M1", List.of(new AulaConteudo(10L, "A", false)))));
            when(progressos.findByUsuarioIdAndAulaIdIn(7L, List.of(10L))).thenReturn(List.of(progresso(10L, 0, true)));

            assertThat(service.daTurma(7L, 5L).percentual()).isEqualTo(100);
        }

        @Test
        @DisplayName("turma sem aulas: 0%, sem consultar o progresso")
        void semAulas() {
            conteudo(List.of(new ModuloConteudo(1L, "Vazio", List.of())));

            var turma = service.daTurma(7L, 5L);

            assertThat(turma.percentual()).isZero();
            assertThat(turma.totalAulas()).isZero();
            assertThat(turma.aulas()).isEmpty();
            verify(progressos, never()).findByUsuarioIdAndAulaIdIn(any(), any());
        }

        @Test
        @DisplayName("sem acesso à turma, nada é lido")
        void semAcesso() {
            doThrow(new AcessoNegadoException("sem acesso"))
                    .when(acessoAulaService)
                    .exigirAcessoTurma(7L, 5L);

            assertThatThrownBy(() -> service.daTurma(7L, 5L)).isInstanceOf(AcessoNegadoException.class);
            verify(conteudoTurmaService, never()).conteudoAtivo(any());
        }
    }
}
`````

### 6. Testes de integração

`backend/src/test/java/br/com/celke/cursos/video/PlaylistIT.java`:

`````java
package br.com.celke.cursos.video;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.shared.armazenamento.ArmazenamentoProperties;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.video.application.LogAcessoVideoService;
import br.com.celke.cursos.video.domain.AcessoVideo;
import br.com.celke.cursos.video.infra.AcessoVideoRepository;
import com.jayway.jsonpath.JsonPath;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
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
 * Cobre: playlists HLS entregues ao aluno — matriz de acesso ao conteúdo (vigente, sem matrícula,
 * vencida, cancelada, ainda não iniciada, aula gratuita), anônimo, master apontando para a API,
 * variante com segmentos pré-assinados (validade = duração + margem) que baixam do Garage, aula sem
 * vídeo, variante inexistente, log de acesso e a retenção dele.
 * Não cobre: reescrita linha a linha e regras de validade (ReescritaPlaylistTest, PlaylistServiceTest);
 * a expiração real da URL (VideoE2E, com margem curta).
 * Dado criado: curso it-<stamp> publicado com o vídeo de amostra convertido na aula paga e um aluno
 * próprio. Exige ffmpeg no PATH.
 */
class PlaylistIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private AcessoVideoRepository acessos;

    @Autowired
    private LogAcessoVideoService logAcessoVideoService;

    @Autowired
    private ArmazenamentoProperties armazenamento;

    private MatriculaITHelper helper;
    private MatriculaITHelper.Estrutura estrutura;
    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarCursoComVideo() throws Exception {
        helper = new MatriculaITHelper(
                new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA))));
        estrutura = helper.cursoPublicado(stamp());
        enviarVideo(estrutura.aulaPaga());
        aluno = criarUsuario(Papel.ALUNO);
        token = login(aluno.email(), aluno.senha());
    }

    /** Envia o vídeo de amostra pela API do ADM (uma parte) e processa a fila: o vídeo fica pronto. */
    private void enviarVideo(long aula) throws Exception {
        byte[] video;
        try (InputStream entrada = getClass().getResourceAsStream("/amostras/video-3s.mp4")) {
            video = entrada.readAllBytes();
        }
        String rota = "/api/adm/aulas/" + aula + "/video";
        String corpo = CursoITHelper.corpo(helper.adm()
                .enviar(
                        "POST",
                        rota + "/envio",
                        "{\"nomeArquivo\":\"a.mp4\",\"contentType\":\"video/mp4\",\"tamanho\":%d}"
                                .formatted(video.length)));
        String url = JsonPath.read(corpo, "$.partes[0].url");
        var put = http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .PUT(BodyPublishers.ofByteArray(video))
                        .build(),
                BodyHandlers.discarding());
        String etag = put.headers().firstValue("ETag").orElseThrow().replace("\"", "\\\"");
        assertThat(helper.adm()
                        .enviar(
                                "POST",
                                rota + "/envio/conclusao",
                                "{\"partes\":[{\"numero\":1,\"etag\":\"%s\"}]}".formatted(etag)))
                .hasStatus(HttpStatus.ACCEPTED);
        processarFila();
        assertThat(helper.adm().enviar("GET", rota, null))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("PRONTO");
    }

    private MvcTestResult get(String uri, String tokenUsado) {
        var requisicao = mvc.get().uri(uri);
        if (tokenUsado != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(tokenUsado));
        }
        return requisicao.exchange();
    }

    private MvcTestResult master(long aula) {
        return get("/api/aulas/" + aula + "/video.m3u8", token);
    }

    private MvcTestResult variante(long aula, String variante) {
        return get("/api/aulas/" + aula + "/video/" + variante + ".m3u8", token);
    }

    private void matricularVigente() {
        Instant agora = Instant.now();
        helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), agora.plus(DIA));
    }

    private List<AcessoVideo> log(long aula) {
        return acessos.findByUsuarioIdAndAulaIdOrderByAcessadoEm(aluno.id(), aula);
    }

    @Test
    @DisplayName("matrícula vigente: master aponta para a API e a variante traz segmentos assinados que baixam")
    void vigente() throws Exception {
        matricularVigente();

        MvcTestResult master = master(estrutura.aulaPaga());
        assertThat(master).hasStatusOk();
        assertThat(master.getResponse().getContentType()).startsWith("application/vnd.apple.mpegurl");
        assertThat(master.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        String textoMaster = master.getResponse().getContentAsString();
        assertThat(textoMaster)
                .startsWith("#EXTM3U")
                .contains("\nvideo/720p.m3u8\n")
                .doesNotContain("index.m3u8");

        MvcTestResult variante = variante(estrutura.aulaPaga(), "720p");
        assertThat(variante).hasStatusOk();
        assertThat(variante.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        List<String> segmentos = variante.getResponse()
                .getContentAsString()
                .lines()
                .filter(l -> !l.startsWith("#") && !l.isBlank())
                .toList();
        assertThat(segmentos)
                .isNotEmpty()
                .allSatisfy(url -> assertThat(url)
                        .startsWith(armazenamento.endpointPublico())
                        .contains("/720p/seg_")
                        // 3 s de vídeo + 2 h de margem
                        .contains("X-Amz-Expires=7203"));

        HttpResponse<byte[]> segmento = http.send(
                HttpRequest.newBuilder(URI.create(segmentos.getFirst())).build(), BodyHandlers.ofByteArray());
        assertThat(segmento.statusCode()).isEqualTo(200);
        assertThat(segmento.body()).isNotEmpty();
        // Pacote MPEG-TS começa com o byte de sincronização 0x47
        assertThat(segmento.body()[0]).isEqualTo((byte) 0x47);

        // Só a abertura da master entra no log de acesso
        assertThat(log(estrutura.aulaPaga())).singleElement().satisfies(acesso -> {
            assertThat(acesso.getIp()).isNotBlank();
            assertThat(acesso.getAcessadoEm()).isAfter(Instant.now().minusSeconds(60));
        });
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
    @DisplayName("sem matrícula vigente: 403 na master e na variante, sem registro no log")
    void semAcesso(String situacao) {
        Instant agora = Instant.now();
        switch (situacao) {
            case "vencida" ->
                helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA.multipliedBy(2)), agora.minus(DIA));
            case "cancelada" ->
                helper.cancelar(helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), null));
            case "ainda não iniciada" -> helper.matricula(estrutura.turma(), aluno.email(), agora.plus(DIA), null);
            default -> {}
        }

        assertThat(master(estrutura.aulaPaga()))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
        assertThat(variante(estrutura.aulaPaga(), "720p")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(log(estrutura.aulaPaga())).isEmpty();
    }

    @Test
    @DisplayName("aula gratuita: assiste sem matrícula")
    void gratuita() throws Exception {
        enviarVideo(estrutura.aulaGratuita());

        assertThat(master(estrutura.aulaGratuita())).hasStatusOk();
        assertThat(variante(estrutura.aulaGratuita(), "720p")).hasStatusOk();
        assertThat(log(estrutura.aulaGratuita())).hasSize(1);
    }

    @Test
    @DisplayName("anônimo recebe 401")
    void anonimo() {
        assertThat(get("/api/aulas/" + estrutura.aulaPaga() + "/video.m3u8", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/aulas/" + estrutura.aulaPaga() + "/video/720p.m3u8", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("aula sem vídeo e variante que o vídeo não tem respondem 404")
    void naoEncontrados() {
        matricularVigente();

        assertThat(master(estrutura.aulaGratuita()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A aula não tem vídeo.");
        // A amostra é 720p: não há 1080p (nunca aumenta a resolução)
        assertThat(variante(estrutura.aulaPaga(), "1080p"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Variante não encontrada.");
        assertThat(get("/api/aulas/999999999/video.m3u8", token))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Aula não encontrada.");
    }

    @Test
    @DisplayName("retenção do log: registros com mais de 180 dias são apagados, os recentes ficam")
    void retencao() {
        Instant agora = Instant.now();
        acessos.save(new AcessoVideo(
                aluno.id(), estrutura.aulaPaga(), "203.0.113.1", "antigo", agora.minus(Duration.ofDays(181))));
        acessos.save(new AcessoVideo(
                aluno.id(), estrutura.aulaPaga(), "203.0.113.2", "recente", agora.minus(Duration.ofDays(179))));

        assertThat(logAcessoVideoService.apagarVencidos()).isPositive();

        assertThat(log(estrutura.aulaPaga()))
                .extracting(AcessoVideo::getUserAgent)
                .containsExactly("recente");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/progresso/ProgressoIT.java`:

`````java
package br.com.celke.cursos.progresso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.progresso.infra.ProgressoAulaRepository;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: progresso do aluno — gravação parcial (posição, vista, revisar), percentual da turma (nenhuma,
 * parte e todas as aulas; turma sem aulas), matriz de acesso ao conteúdo (vigente, sem matrícula,
 * vencida, cancelada, ainda não iniciada, aula gratuita), anônimo, aula inativa, validação e o
 * isolamento entre alunos (o progresso de um nunca aparece para outro).
 * Não cobre: arredondamento do percentual e regras da entidade (PercentualConclusaoTest,
 * ProgressoAulaTest, ProgressoServiceTest).
 * Dado criado: curso it-<stamp> publicado com uma aula paga e uma gratuita, e alunos próprios.
 */
class ProgressoIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);

    @Autowired
    private ProgressoAulaRepository progressos;

    private MatriculaITHelper helper;
    private MatriculaITHelper.Estrutura estrutura;
    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarCursoEAluno() {
        helper = new MatriculaITHelper(
                new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA))));
        estrutura = helper.cursoPublicado(stamp());
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

    private MvcTestResult progresso(long aula) {
        return chamar(HttpMethod.GET, "/api/aluno/aulas/" + aula + "/progresso", null, token);
    }

    private MvcTestResult atualizar(long aula, String corpo) {
        return chamar(HttpMethod.PATCH, "/api/aluno/aulas/" + aula + "/progresso", corpo, token);
    }

    private MvcTestResult turma(long turma, String tokenUsado) {
        return chamar(HttpMethod.GET, "/api/aluno/turmas/" + turma + "/progresso", null, tokenUsado);
    }

    private void matricularVigente(String email) {
        Instant agora = Instant.now();
        helper.matricula(estrutura.turma(), email, agora.minus(DIA), agora.plus(DIA));
    }

    @Test
    @DisplayName("grava posição, vista e revisar aos poucos e reflete no percentual da turma")
    void cicloDoProgresso() {
        matricularVigente(aluno.email());
        long paga = estrutura.aulaPaga();

        assertThat(progresso(paga)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"aulaId":%d,"posicaoSegundos":0,"vista":false,"vistaEm":null,"revisar":false}
                """.formatted(paga));
        assertThat(turma(estrutura.turma(), token)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"turmaId":%d,"totalAulas":2,"aulasVistas":0,"percentual":0}
                """.formatted(
                        estrutura.turma()));

        // O player manda a posição; depois a aula é marcada como vista, sem perder a posição
        assertThat(atualizar(paga, "{\"posicaoSegundos\":95}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.posicaoSegundos")
                .isEqualTo(95);
        var vista = assertThat(atualizar(paga, "{\"vista\":true,\"revisar\":true}"))
                .hasStatusOk()
                .bodyJson();
        vista.extractingPath("$.posicaoSegundos").isEqualTo(95);
        vista.extractingPath("$.vista").isEqualTo(true);
        vista.extractingPath("$.revisar").isEqualTo(true);
        vista.extractingPath("$.vistaEm").isNotNull();

        var metade = assertThat(turma(estrutura.turma(), token)).hasStatusOk().bodyJson();
        metade.extractingPath("$.aulasVistas").isEqualTo(1);
        metade.extractingPath("$.percentual").isEqualTo(50);
        metade.extractingPath("$.aulas[*].aulaId").isEqualTo(List.of((int) paga, (int) estrutura.aulaGratuita()));
        metade.extractingPath("$.aulas[0].posicaoSegundos").isEqualTo(95);

        assertThat(atualizar(estrutura.aulaGratuita(), "{\"vista\":true}")).hasStatusOk();
        assertThat(turma(estrutura.turma(), token))
                .bodyJson()
                .extractingPath("$.percentual")
                .isEqualTo(100);

        // Desmarcar volta o percentual; aula inativada sai da conta
        assertThat(atualizar(paga, "{\"vista\":false}"))
                .bodyJson()
                .extractingPath("$.vistaEm")
                .isNull();
        helper.adm().status("/api/adm/aulas/" + paga + "/status", "INATIVO");
        var semAInativa = assertThat(turma(estrutura.turma(), token)).bodyJson();
        semAInativa.extractingPath("$.totalAulas").isEqualTo(1);
        semAInativa.extractingPath("$.percentual").isEqualTo(100);
        assertThat(progresso(paga))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Aula não encontrada.");
    }

    @Test
    @DisplayName("o progresso de um aluno nunca aparece para outro aluno da mesma turma")
    void isolamentoEntreAlunos() {
        matricularVigente(aluno.email());
        UsuarioTeste outro = criarUsuario(Papel.ALUNO);
        matricularVigente(outro.email());
        String tokenOutro = login(outro.email(), outro.senha());
        assertThat(atualizar(estrutura.aulaPaga(), "{\"posicaoSegundos\":300,\"vista\":true}"))
                .hasStatusOk();

        assertThat(chamar(HttpMethod.GET, "/api/aluno/aulas/" + estrutura.aulaPaga() + "/progresso", null, tokenOutro))
                .bodyJson()
                .isLenientlyEqualTo("{\"posicaoSegundos\":0,\"vista\":false}");
        assertThat(turma(estrutura.turma(), tokenOutro))
                .bodyJson()
                .extractingPath("$.percentual")
                .isEqualTo(0);
        assertThat(turma(estrutura.turma(), token))
                .bodyJson()
                .extractingPath("$.percentual")
                .isEqualTo(50);
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
    @DisplayName("sem matrícula vigente: 403 na aula paga e na turma; nada é gravado")
    void semAcesso(String situacao) {
        Instant agora = Instant.now();
        switch (situacao) {
            case "vencida" ->
                helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA.multipliedBy(2)), agora.minus(DIA));
            case "cancelada" ->
                helper.cancelar(helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), null));
            case "ainda não iniciada" -> helper.matricula(estrutura.turma(), aluno.email(), agora.plus(DIA), null);
            default -> {}
        }

        assertThat(progresso(estrutura.aulaPaga())).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(atualizar(estrutura.aulaPaga(), "{\"vista\":true}"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
        assertThat(turma(estrutura.turma(), token))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
        assertThat(progressos.findByUsuarioIdAndAulaId(aluno.id(), estrutura.aulaPaga()))
                .isEmpty();
    }

    @Test
    @DisplayName("aula gratuita: progresso sem matrícula; a turma continua exigindo matrícula")
    void gratuita() {
        assertThat(atualizar(estrutura.aulaGratuita(), "{\"posicaoSegundos\":12}"))
                .hasStatusOk();
        assertThat(progresso(estrutura.aulaGratuita()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.posicaoSegundos")
                .isEqualTo(12);
        assertThat(turma(estrutura.turma(), token)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("turma sem aulas fica em 0%")
    void turmaSemAulas() {
        CursoITHelper adm = helper.adm();
        String slug = stamp();
        long curso = adm.criarCurso("IT Vazio " + slug, slug);
        long turma = adm.criarTurma(curso, "Turma vazia");
        adm.ativarTurma(turma);
        adm.criarModulo(turma, "Módulo sem aulas");
        adm.publicar(curso);
        helper.matricula(turma, aluno.email(), Instant.now().minus(DIA), null);

        assertThat(turma(turma, token)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"totalAulas":0,"aulasVistas":0,"percentual":0,"aulas":[]}
                """);
    }

    @Test
    @DisplayName("anônimo recebe 401 nas três rotas")
    void anonimo() {
        String aula = "/api/aluno/aulas/" + estrutura.aulaPaga() + "/progresso";
        assertThat(chamar(HttpMethod.GET, aula, null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.PATCH, aula, "{\"vista\":true}", null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(turma(estrutura.turma(), null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("corpo vazio ou posição negativa respondem 400 com os campos")
    void validacao() {
        matricularVigente(aluno.email());

        assertThat(atualizar(estrutura.aulaPaga(), "{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("algumCampo"));
        assertThat(atualizar(estrutura.aulaPaga(), "{\"posicaoSegundos\":-1}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("posicaoSegundos"));
    }
}
`````

### 7. Jornadas e2e

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/Videos.java`:

`````java
package br.com.celke.cursos.e2e.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Vídeo da aula pelo caminho real: envio em partes pelo Caddy direto ao Garage e conversão pelo worker. */
public final class Videos {

    private Videos() {}

    /**
     * Pede as URLs das partes, faz o PUT de cada parte no host de mídia guardando o ETag e conclui o
     * envio (202, vídeo na fila).
     */
    public static void enviar(Adm adm, long aula, byte[] video) {
        String rota = "/api/adm/aulas/" + aula + "/video";
        JsonPath envio = adm.requisicao()
                .body("{\"nomeArquivo\":\"aula.mp4\",\"contentType\":\"video/mp4\",\"tamanho\":%d}"
                        .formatted(video.length))
                .post(rota + "/envio")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();
        int tamanhoParte = envio.getInt("tamanhoParte");
        List<String> urls = envio.getList("partes.url", String.class);

        List<Map<String, Object>> partes = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            URI url = URI.create(urls.get(i));
            assertThat(url.getHost()).isEqualTo(Midia.HOST);
            int de = i * tamanhoParte;
            int ate = Math.min(video.length, de + tamanhoParte);
            Response put = Midia.requisicao()
                    .body(Arrays.copyOfRange(video, de, ate))
                    .put(Midia.caminho(url))
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();
            partes.add(Map.of("numero", i + 1, "etag", put.getHeader("ETag")));
        }

        adm.requisicao()
                .body(Map.of("partes", partes))
                .post(rota + "/envio/conclusao")
                .then()
                .statusCode(202)
                .body("status", equalTo("PENDENTE"));
    }

    /** Espera o worker terminar e devolve o vídeo da aula (pronto ou com erro). */
    public static JsonPath aguardarProcessamento(Adm adm, long aula) {
        return await("vídeo processado pelo worker")
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(2))
                .until(
                        () -> adm.requisicao()
                                .get("/api/adm/aulas/" + aula + "/video")
                                .then()
                                .statusCode(200)
                                .extract()
                                .jsonPath(),
                        json -> !List.of("PENDENTE", "PROCESSANDO").contains(json.getString("status")));
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/video/VideoE2E.java`:

`````java
package br.com.celke.cursos.e2e.video;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Arquivos;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Midia;
import br.com.celke.cursos.e2e.support.Videos;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: o ADM envia o vídeo de uma aula em partes, direto ao Garage pelo Caddy; o worker converte
 * com o ffmpeg e o vídeo fica pronto, com o tamanho antes e depois e a thumbnail.
 */
class VideoE2E {

    @Test
    @DisplayName("upload em partes de um vídeo curto até o status pronto")
    void uploadAtePronto() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();
        byte[] video = Arquivos.amostra("video-3s.mp4");

        // 1. Curso, turma, módulo e aula
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Jornada e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Módulo\"}");
        long aula = adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Aula em vídeo\"}");

        // 2. Partes enviadas direto ao Garage pelo Caddy; a conclusão coloca o vídeo na fila
        Videos.enviar(adm, aula, video);

        // 3. O worker converte: status pronto, com o tamanho antes e depois
        JsonPath pronto = Videos.aguardarProcessamento(adm, aula);
        assertThat(pronto.getString("status"))
                .as("erro: %s", pronto.getString("erro"))
                .isEqualTo("PRONTO");
        assertThat(pronto.getLong("tamanhoOriginal")).isEqualTo(video.length);
        assertThat(pronto.getLong("tamanhoFinal")).isPositive();
        assertThat(pronto.getInt("duracaoSegundos")).isEqualTo(3);
        assertThat(pronto.getList("variantes", String.class)).containsExactly("720p");

        // 4. A thumbnail sai pelo host de mídia
        URI thumbnail = URI.create(pronto.getString("thumbnailUrl"));
        assertThat(thumbnail.getHost()).isEqualTo(Midia.HOST);
        Response imagem = Midia.requisicao()
                .get(Midia.caminho(thumbnail))
                .then()
                .statusCode(200)
                .extract()
                .response();
        assertThat(imagem.getContentType()).isEqualTo("image/jpeg");
        assertThat(imagem.asByteArray()).startsWith((byte) 0xFF, (byte) 0xD8);
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/video/EntregaVideoE2E.java`:

`````java
package br.com.celke.cursos.e2e.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Arquivos;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Midia;
import br.com.celke.cursos.e2e.support.Videos;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: o aluno matriculado abre o vídeo da aula pela API (playlist assinada), baixa um segmento
 * direto do Garage pelo Caddy, a URL do segmento vence e é recusada (a stack de e2e usa margem de 5 s) e o progresso
 * marcado na aula aparece no percentual da turma.
 */
class EntregaVideoE2E {

    @Test
    @DisplayName("playlist assinada → segmento baixa → depois de vencer, recusado; progresso refletido na turma")
    void assistirEProgredir() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();

        // 1. Curso publicado, turma ativa, uma aula com vídeo convertido
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Entrega e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Módulo\"}");
        long aula = adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Aula em vídeo\"}");
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");
        Videos.enviar(adm, aula, Arquivos.amostra("video-3s.mp4"));
        assertThat(Videos.aguardarProcessamento(adm, aula).getString("status")).isEqualTo("PRONTO");

        // 2. Aluno sem matrícula não abre o vídeo
        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");
        player(token).get("/api/aulas/" + aula + "/video.m3u8").then().statusCode(403);

        // 3. Matriculado: master pela API, apontando para a variante também servida pela API
        adm.criar(
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"CORTESIA\",\"fim\":\"%s\"}"
                        .formatted(email, Instant.now().plus(Duration.ofDays(30))));
        Response master = player(token)
                .get("/api/aulas/" + aula + "/video.m3u8")
                .then()
                .statusCode(200)
                .header("Cache-Control", "no-store")
                .extract()
                .response();
        assertThat(master.getContentType()).startsWith("application/vnd.apple.mpegurl");
        assertThat(master.asString()).contains("\nvideo/720p.m3u8");

        // 4. Variante: segmentos com URLs pré-assinadas no host de mídia
        List<String> segmentos = player(token)
                .get("/api/aulas/" + aula + "/video/720p.m3u8")
                .then()
                .statusCode(200)
                .extract()
                .asString()
                .lines()
                .filter(linha -> !linha.startsWith("#") && !linha.isBlank())
                .toList();
        assertThat(segmentos).isNotEmpty();
        URI segmento = URI.create(segmentos.getFirst());
        assertThat(segmento.getHost()).isEqualTo(Midia.HOST);

        // 5. O segmento baixa direto do Garage pelo Caddy (MPEG-TS começa com 0x47)...
        byte[] bytes = Midia.requisicao()
                .get(Midia.caminho(segmento))
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0x47);

        // 6. ...e para de baixar quando a validade (3 s de vídeo + 5 s de margem) vence. O Garage recusa
        // com 400 "Date is too old" (a AWS responderia 403): a jornada aceita as duas recusas do S3
        Response vencida = await("URL do segmento vencida")
                .atMost(Duration.ofSeconds(40))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> Midia.requisicao().get(Midia.caminho(segmento)), r -> r.statusCode() != 200);
        assertThat(vencida.statusCode()).isIn(400, 403);
        assertThat(vencida.asString()).containsAnyOf("Date is too old", "Request has expired");

        // 7. Progresso: posição e aula vista aparecem na turma
        aluno(token)
                .body("{\"posicaoSegundos\":3,\"vista\":true}")
                .patch("/api/aluno/aulas/" + aula + "/progresso")
                .then()
                .statusCode(200)
                .body("vista", equalTo(true));
        aluno(token)
                .get("/api/aluno/turmas/" + turma + "/progresso")
                .then()
                .statusCode(200)
                .body("percentual", equalTo(100))
                .body("aulas[0].posicaoSegundos", equalTo(3));
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }

    /** Como o player (hls.js): aceita qualquer tipo, já que as playlists não são JSON. */
    private static RequestSpecification player(String token) {
        return aluno(token).accept("*/*");
    }
}
`````

## Antes de executar

Fase 4 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/entrega-video
```

## Verificação

Dentro de `backend/` (Docker rodando e `ffmpeg` no PATH):

```bash
./mvnw spotless:apply
./mvnw verify
```

- `BUILD SUCCESS` com 567 testes unitários e 287 de integração, e a cobertura mínima atendida.

Na raiz do repositório, as jornadas e2e com a stack completa:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
(cd backend && ./mvnw verify -Pe2e)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- 14 jornadas passam, entre elas a `EntregaVideoE2E`.

No ambiente local, pelo Swagger, com uma aula com vídeo pronto e um aluno matriculado:

- `GET /api/aulas/{id}/video.m3u8` com o token do aluno devolve a master apontando para
  `video/720p.m3u8`; a variante traz URLs de `http://localhost:3900/...` que baixam com `curl`.
- Sem matrícula, as duas rotas respondem 403.
- `PATCH /api/aluno/aulas/{id}/progresso` com `{"vista":true}` e `GET /api/aluno/turmas/{id}/progresso`
  mostra o percentual.

## Depois de executar

```bash
git add backend/ infra/ docs/
git commit -m "feat(video): adicionar playlist assinada, progresso e log de acesso"
git push -u origin feature/entrega-video
```

Abra o Pull Request de `feature/entrega-video` para `homolog`.
