# Autorização e proteções

**Fase:** 1 — etapa 1.5
**Branch:** `feature/base-projeto`

## Prompt

Vamos proteger a autenticação contra força bruta, abuso e CSRF, seguindo o `backend/CLAUDE.md` e o
padrão das skills de teste. A autorização por papéis já existe (rotas negadas por padrão, públicas com o
método declarado, `@PreAuthorize`). Não faça commit.

O que esta etapa entrega:

- **Limite por IP** (Bucket4j, token bucket) em `POST /api/auth/login` e `POST /api/auth/refresh`: um
  filtro que roda antes da segurança e do Spring MVC, conta toda requisição (até as inválidas) e responde
  429 em ProblemDetail com `Retry-After` e `X-RateLimit-Remaining`. Baldes guardados com Caffeine
  (memória limitada); relógio do Bucket4j vindo do `Clock` da aplicação.
- **Bloqueio temporário por falhas seguidas**: 5 falhas por e-mail + IP (o dono da conta ainda entra de
  outro lugar) e 20 por e-mail vindas de qualquer IP; bloqueio de 15 minutos; contadores zeram com um
  login correto ou após 1 hora sem falhas. O bloqueio é verificado antes de buscar o usuário ou calcular
  o hash e responde 429 com `Retry-After` e a espera em minutos.
- **Auditoria de login** (`auditoria_login`): resultado (`SUCESSO`, `EMAIL_DESCONHECIDO`,
  `SENHA_INVALIDA`, `USUARIO_INATIVO`, `BLOQUEADO`), IP e user-agent de toda tentativa, numa transação
  própria (`REQUIRES_NEW`), para ficar gravada mesmo quando o login falha. O cliente continua recebendo
  só "E-mail ou senha inválidos.".
- **Proteção CSRF no refresh e no logout**: `Sec-Fetch-Site: cross-site` e `Origin` fora da lista
  (`app.auth.origens-permitidas`, variável `AUTH_ORIGENS`) recebem 403 "Origem não permitida."; sem
  `Origin` (cliente que não é navegador) passa. O `SameSite=Strict` não basta porque outras aplicações em
  subdomínios do mesmo domínio contam como o mesmo site.
- **IP real atrás do proxy**: `server.forward-headers-strategy: native`, para o Tomcat aceitar
  `X-Forwarded-For` só de proxy interno e ler a cadeia da direita para a esquerda.
- **`ProblemDetailJson`**: erros que nascem em filtros (segurança, limite) no mesmo formato do handler
  global; `Retry-After` também nos 429 do handler.

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### 1. Arquivos alterados (substitua o conteúdo inteiro)

`backend/pom.xml`:

`````xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>4.1.1</version>
		<relativePath/>
	</parent>
	<groupId>br.com.celke</groupId>
	<artifactId>plataforma-cursos</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name>plataforma-cursos</name>
	<description>API da Plataforma de Cursos</description>

	<properties>
		<java.version>25</java.version>
		<springdoc.version>3.1.1</springdoc.version>
		<aws-sdk.version>2.57.1</aws-sdk.version>
		<bouncycastle.version>1.86</bouncycastle.version>
		<bucket4j.version>8.21.0</bucket4j.version>
		<spotless.version>3.10.4</spotless.version>
		<palantir-java-format.version>2.102.0</palantir-java-format.version>
		<jacoco.version>0.8.15</jacoco.version>
		<!-- Cobertura mínima de linhas (unitários + integração) -->
		<jacoco.cobertura.minima>0.80</jacoco.cobertura.minima>
		<!-- -Dskip.unit=true roda só os testes de integração -->
		<skip.unit>false</skip.unit>
	</properties>

	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>software.amazon.awssdk</groupId>
				<artifactId>bom</artifactId>
				<version>${aws-sdk.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>

	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-flyway</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-mail</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-thymeleaf</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc</artifactId>
		</dependency>
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-database-postgresql</artifactId>
		</dependency>
		<dependency>
			<groupId>software.amazon.awssdk</groupId>
			<artifactId>s3</artifactId>
		</dependency>
		<!-- Limite de requisições por IP (token bucket) -->
		<dependency>
			<groupId>com.bucket4j</groupId>
			<artifactId>bucket4j_jdk17-core</artifactId>
			<version>${bucket4j.version}</version>
		</dependency>
		<dependency>
			<groupId>com.github.ben-manes.caffeine</groupId>
			<artifactId>caffeine</artifactId>
		</dependency>
		<!-- Argon2 para o hash das senhas -->
		<dependency>
			<groupId>org.bouncycastle</groupId>
			<artifactId>bcprov-jdk18on</artifactId>
			<version>${bouncycastle.version}</version>
		</dependency>
		<dependency>
			<groupId>org.springdoc</groupId>
			<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
			<version>${springdoc.version}</version>
		</dependency>
		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-flyway-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-testcontainers</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>testcontainers-junit-jupiter</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>testcontainers-postgresql</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.awaitility</groupId>
			<artifactId>awaitility</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>

			<!-- Unitários: *Test.java, sem Spring -->
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-surefire-plugin</artifactId>
				<configuration>
					<skip>${skip.unit}</skip>
				</configuration>
			</plugin>

			<!-- Integração: *IT.java, Spring + Testcontainers -->
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-failsafe-plugin</artifactId>
				<executions>
					<execution>
						<goals>
							<goal>integration-test</goal>
							<goal>verify</goal>
						</goals>
					</execution>
				</executions>
			</plugin>

			<!-- Cobertura somando unitários e integração -->
			<plugin>
				<groupId>org.jacoco</groupId>
				<artifactId>jacoco-maven-plugin</artifactId>
				<version>${jacoco.version}</version>
				<configuration>
					<excludes>
						<exclude>br/com/celke/cursos/PlataformaCursosApplication.class</exclude>
					</excludes>
				</configuration>
				<executions>
					<execution>
						<id>preparar-agente</id>
						<goals>
							<goal>prepare-agent</goal>
						</goals>
					</execution>
					<execution>
						<id>relatorio</id>
						<phase>verify</phase>
						<goals>
							<goal>report</goal>
						</goals>
					</execution>
					<execution>
						<id>cobertura-minima</id>
						<phase>verify</phase>
						<goals>
							<goal>check</goal>
						</goals>
						<configuration>
							<rules>
								<rule>
									<element>BUNDLE</element>
									<limits>
										<limit>
											<counter>LINE</counter>
											<value>COVEREDRATIO</value>
											<minimum>${jacoco.cobertura.minima}</minimum>
										</limit>
									</limits>
								</rule>
							</rules>
						</configuration>
					</execution>
				</executions>
			</plugin>

			<!-- Formatação: spotless:apply corrige, spotless:check falha o build -->
			<plugin>
				<groupId>com.diffplug.spotless</groupId>
				<artifactId>spotless-maven-plugin</artifactId>
				<version>${spotless.version}</version>
				<configuration>
					<java>
						<palantirJavaFormat>
							<version>${palantir-java-format.version}</version>
						</palantirJavaFormat>
						<removeUnusedImports/>
						<trimTrailingWhitespace/>
						<endWithNewline/>
					</java>
				</configuration>
				<executions>
					<execution>
						<phase>verify</phase>
						<goals>
							<goal>check</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>

</project>
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
  limites:
    # Login e refresh: requisições por IP a cada minuto
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
`````

`backend/src/test/resources/application-test.yaml`:

`````yaml
# Profile dos testes de integração. Endereços dos containers vêm de support/Containers.
spring:
  mail:
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false
      mail.smtp.starttls.required: false

app:
  auth:
    # Chave só dos testes (48 bytes em Base64)
    jwt-segredo: dGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUt
    origens-permitidas: http://localhost:5173
  limites:
    # Todas as requisições da suíte saem do mesmo IP: o limite por IP fica fora do caminho
    requisicoes-por-ip-por-minuto: 100000
  armazenamento:
    cors-origens: http://localhost:5173
  email:
    remetente: nao-responda@teste.celke.uno
`````

`backend/src/main/java/br/com/celke/cursos/shared/erro/ManipuladorErros.java`:

`````java
package br.com.celke.cursos.shared.erro;

import br.com.celke.cursos.shared.web.IdRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte toda exceção em ProblemDetail (RFC 9457). Erros do Spring MVC (404, 405, JSON inválido...)
 * vêm da classe base; aqui ficam validação, regra de negócio e o erro inesperado, que nunca expõe a
 * causa ao cliente.
 */
@RestControllerAdvice
public class ManipuladorErros extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManipuladorErros.class);

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErroCampo> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> new ErroCampo(erro.getField(), erro.getDefaultMessage()))
                .sorted(Comparator.comparing(ErroCampo::campo).thenComparing(ErroCampo::mensagem))
                .toList();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos.");
        problema.setProperty("erros", erros);
        return handleExceptionInternal(ex, problema, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(RegraNegocioException.class)
    ResponseEntity<Object> regraNegocio(RegraNegocioException ex, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof MuitasTentativasException limite) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(limite.segundosDeEspera()));
        }
        return handleExceptionInternal(ex, problema, headers, ex.getStatus(), request);
    }

    /**
     * Erro inesperado: registra a causa no log e devolve só o id da requisição. Exceções de
     * segurança voltam para o Spring Security, que decide entre 401 e 403.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> inesperado(Exception ex, WebRequest request) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        LOG.error("Erro inesperado", ex);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno. Se o problema continuar, informe o código " + MDC.get(IdRequisicao.CHAVE_MDC) + ".");
        return handleExceptionInternal(ex, problema, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /** Completa todo ProblemDetail com o caminho da requisição e o id de correlação. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problema) {
            if (request instanceof ServletWebRequest servlet) {
                HttpServletRequest http = servlet.getRequest();
                problema.setInstance(URI.create(http.getRequestURI()));
            }
            problema.setProperty("requestId", MDC.get(IdRequisicao.CHAVE_MDC));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/security/RespostasSeguranca.java`:

`````java
package br.com.celke.cursos.shared.security;

import br.com.celke.cursos.shared.erro.ProblemDetailJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Respostas 401 e 403 do Spring Security no mesmo formato ProblemDetail do resto da API. Essas
 * respostas nascem nos filtros, antes do Spring MVC, por isso são escritas aqui.
 */
public class RespostasSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public RespostasSeguranca(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ProblemDetailJson.escrever(jsonMapper, request, response, HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        ProblemDetailJson.escrever(jsonMapper, request, response, HttpStatus.FORBIDDEN, "Acesso negado.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/AuthProperties.java`:

`````java
package br.com.celke.cursos.auth.application;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração da autenticação.
 *
 * @param jwtSegredo chave HMAC do JWT em Base64, com pelo menos 32 bytes
 * @param validadeAcesso validade do access token
 * @param validadeRefresh validade do refresh token (renovada a cada uso)
 * @param cookieSeguro marca o cookie de refresh como {@code Secure}
 * @param origensPermitidas origens do front aceitas no refresh e no logout (ex.: {@code https://aluno.celke.uno})
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @NotBlank String jwtSegredo,
        @NotBlank String emissor,
        @NotNull Duration validadeAcesso,
        @NotNull Duration validadeRefresh,
        boolean cookieSeguro,
        List<String> origensPermitidas) {

    public AuthProperties {
        origensPermitidas = origensPermitidas == null
                ? List.of()
                : origensPermitidas.stream()
                        .map(AuthProperties::normalizarOrigem)
                        .filter(o -> !o.isEmpty())
                        .toList();
    }

    /** Origem em minúsculas e sem barra no fim, para comparar com o cabeçalho {@code Origin}. */
    public static String normalizarOrigem(String origem) {
        String normalizada = origem.strip().toLowerCase(Locale.ROOT);
        return normalizada.endsWith("/") ? normalizada.substring(0, normalizada.length() - 1) : normalizada;
    }

    public byte[] chaveJwt() {
        return Base64.getDecoder().decode(jwtSegredo);
    }

    @AssertTrue(message = "app.auth.jwt-segredo deve estar em Base64 e ter pelo menos 32 bytes")
    public boolean isSegredoValido() {
        if (jwtSegredo == null || jwtSegredo.isBlank()) {
            return true; // @NotBlank já acusa
        }
        try {
            return chaveJwt().length >= 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/AuthService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login (com bloqueio por falhas seguidas e auditoria), renovação e saída. */
@Service
public class AuthService {

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final TokenAcessoService tokenAcessoService;
    private final RefreshTokenService refreshTokenService;
    private final BloqueioLoginService bloqueioLoginService;
    private final AuditoriaLoginService auditoriaLoginService;

    /**
     * Hash de uma senha aleatória: conferido quando o e-mail não existe, para o login levar o mesmo
     * tempo nos dois casos e não revelar quais e-mails estão cadastrados.
     */
    private final String hashFicticio;

    public AuthService(
            UsuarioService usuarioService,
            PasswordEncoder passwordEncoder,
            TokenAcessoService tokenAcessoService,
            RefreshTokenService refreshTokenService,
            BloqueioLoginService bloqueioLoginService,
            AuditoriaLoginService auditoriaLoginService) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.tokenAcessoService = tokenAcessoService;
        this.refreshTokenService = refreshTokenService;
        this.bloqueioLoginService = bloqueioLoginService;
        this.auditoriaLoginService = auditoriaLoginService;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Confere o bloqueio antes de tudo (sem calcular hash), depois a senha. Falha e sucesso
     * atualizam os contadores de bloqueio e ficam na auditoria; o cliente recebe sempre a mesma
     * mensagem, sem saber o motivo.
     */
    @Transactional
    public Sessao entrar(String email, String senha, OrigemRequisicao origem) {
        try {
            bloqueioLoginService.verificar(email, origem.ip());
        } catch (MuitasTentativasException e) {
            auditoriaLoginService.registrar(email, null, ResultadoLogin.BLOQUEADO, origem);
            throw e;
        }
        Optional<Usuario> encontrado = usuarioService.buscarPorEmail(email);
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio);
        boolean senhaConfere = passwordEncoder.matches(senha, hash);
        ResultadoLogin resultado = encontrado.isEmpty()
                ? ResultadoLogin.EMAIL_DESCONHECIDO
                : !senhaConfere
                        ? ResultadoLogin.SENHA_INVALIDA
                        : !encontrado.get().isAtivo() ? ResultadoLogin.USUARIO_INATIVO : ResultadoLogin.SUCESSO;
        auditoriaLoginService.registrar(email, encontrado.orElse(null), resultado, origem);
        if (resultado != ResultadoLogin.SUCESSO) {
            bloqueioLoginService.registrarFalha(email, origem.ip());
            throw new CredenciaisInvalidasException();
        }
        bloqueioLoginService.registrarSucesso(email, origem.ip());
        Usuario usuario = encontrado.get();
        usuarioService.atualizarHashSeNecessario(usuario, senha);
        return abrirSessao(usuario, refreshTokenService.emitir(usuario.getId()));
    }

    /**
     * Troca o refresh token por um novo e emite outro access token. Sem transação própria: a
     * revogação de uma família por reutilização precisa ficar gravada mesmo com a sessão recusada.
     */
    public Sessao renovar(String refreshToken) {
        RefreshEmitido novo = refreshTokenService.rotacionar(refreshToken).orElseThrow(SessaoInvalidaException::new);
        Usuario usuario = usuarioService
                .buscarPorId(novo.usuarioId())
                .filter(Usuario::isAtivo)
                .orElseThrow(SessaoInvalidaException::new);
        return abrirSessao(usuario, novo);
    }

    public void sair(String refreshToken) {
        refreshTokenService.revogarFamiliaDoToken(refreshToken);
    }

    public void sairDeTodos(Long usuarioId) {
        refreshTokenService.revogarTodas(usuarioId);
    }

    private Sessao abrirSessao(Usuario usuario, RefreshEmitido refresh) {
        return new Sessao(usuario, tokenAcessoService.emitir(usuario), refresh);
    }

    public record Sessao(Usuario usuario, TokenAcesso acesso, RefreshEmitido refresh) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/AuthController.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.AuthService;
import br.com.celke.cursos.auth.application.AuthService.Sessao;
import br.com.celke.cursos.auth.application.SessaoInvalidaException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.usuario.application.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação")
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;
    private final CookieRefresh cookieRefresh;
    private final VerificadorOrigem verificadorOrigem;

    public AuthController(
            AuthService authService,
            UsuarioService usuarioService,
            CookieRefresh cookieRefresh,
            VerificadorOrigem verificadorOrigem) {
        this.authService = authService;
        this.usuarioService = usuarioService;
        this.cookieRefresh = cookieRefresh;
        this.verificadorOrigem = verificadorOrigem;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Entrar com e-mail e senha",
            description = "Devolve o access token e grava o refresh token em cookie.")
    public ResponseEntity<SessaoResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return comCookie(authService.entrar(request.email(), request.senha(), OrigemRequisicao.de(http)));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Renovar a sessão",
            description = "Troca o refresh token do cookie por um novo e emite outro access token.")
    public ResponseEntity<SessaoResponse> refresh(
            @CookieValue(name = CookieRefresh.NOME, required = false) String refreshToken, HttpServletRequest http) {
        verificadorOrigem.verificar(http);
        return comCookie(authService.renovar(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sair", description = "Revoga a sessão do cookie e apaga o cookie.")
    public ResponseEntity<Void> logout(
            @CookieValue(name = CookieRefresh.NOME, required = false) String refreshToken, HttpServletRequest http) {
        verificadorOrigem.verificar(http);
        authService.sair(refreshToken);
        return semCookie();
    }

    @PostMapping("/sair-de-todos")
    @Operation(summary = "Sair de todos os dispositivos", description = "Revoga todas as sessões do usuário.")
    public ResponseEntity<Void> sairDeTodos(@AuthenticationPrincipal Jwt jwt) {
        authService.sairDeTodos(Long.valueOf(jwt.getSubject()));
        return semCookie();
    }

    @GetMapping("/me")
    @Operation(summary = "Usuário da sessão")
    public UsuarioResponse me(@AuthenticationPrincipal Jwt jwt) {
        return usuarioService
                .buscarPorId(Long.valueOf(jwt.getSubject()))
                .map(UsuarioResponse::de)
                .orElseThrow(SessaoInvalidaException::new);
    }

    private ResponseEntity<SessaoResponse> comCookie(Sessao sessao) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookieRefresh.criar(sessao.refresh().token()).toString())
                .body(SessaoResponse.de(sessao));
    }

    private ResponseEntity<Void> semCookie() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh.limpar().toString())
                .build();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/AuthServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant EXPIRA = Instant.parse("2026-01-10T12:10:00Z");
    private static final String HASH_FICTICIO = "{argon2}ficticio";
    private static final OrigemRequisicao ORIGEM = new OrigemRequisicao("203.0.113.7", "Navegador/1.0");

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenAcessoService tokenAcessoService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private BloqueioLoginService bloqueioLoginService;

    @Mock
    private AuditoriaLoginService auditoriaLoginService;

    private AuthService service;

    @BeforeEach
    void criar() {
        // O construtor calcula o hash fictício usado quando o e-mail não existe
        when(passwordEncoder.encode(anyString())).thenReturn(HASH_FICTICIO);
        service = new AuthService(
                usuarioService,
                passwordEncoder,
                tokenAcessoService,
                refreshTokenService,
                bloqueioLoginService,
                auditoriaLoginService);
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "{argon2}hash-da-ana", Papel.ALUNO);
    }

    @Test
    @DisplayName("login correto emite os tokens, zera o bloqueio, audita SUCESSO e regrava hash antigo se preciso")
    void loginCorreto() {
        Usuario ana = ana();
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);
        when(refreshTokenService.emitir(7L)).thenReturn(new RefreshEmitido("refresh", 7L, EXPIRA));
        when(tokenAcessoService.emitir(ana)).thenReturn(new TokenAcesso("jwt", EXPIRA));

        var sessao = service.entrar("ana@celke.uno", "senha-certa", ORIGEM);

        assertThat(sessao.acesso().valor()).isEqualTo("jwt");
        assertThat(sessao.refresh().token()).isEqualTo("refresh");
        verify(auditoriaLoginService).registrar("ana@celke.uno", ana, ResultadoLogin.SUCESSO, ORIGEM);
        verify(bloqueioLoginService).registrarSucesso("ana@celke.uno", "203.0.113.7");
        verify(bloqueioLoginService, never()).registrarFalha(anyString(), anyString());
        verify(usuarioService).atualizarHashSeNecessario(ana, "senha-certa");
    }

    @Test
    @DisplayName("senha errada conta falha, audita SENHA_INVALIDA e não emite tokens")
    void senhaErrada() {
        Usuario ana = ana();
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("senha-errada", "{argon2}hash-da-ana")).thenReturn(false);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-errada", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(auditoriaLoginService).registrar("ana@celke.uno", ana, ResultadoLogin.SENHA_INVALIDA, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("ana@celke.uno", "203.0.113.7");
        verify(refreshTokenService, never()).emitir(anyLong());
        verify(usuarioService, never()).atualizarHashSeNecessario(any(), any());
    }

    @Test
    @DisplayName("e-mail inexistente confere a senha contra o hash fictício, conta falha e audita EMAIL_DESCONHECIDO")
    void emailInexistente() {
        when(usuarioService.buscarPorEmail("ninguem@celke.uno")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrar("ninguem@celke.uno", "qualquer", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(passwordEncoder).matches("qualquer", HASH_FICTICIO);
        verify(auditoriaLoginService).registrar("ninguem@celke.uno", null, ResultadoLogin.EMAIL_DESCONHECIDO, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("ninguem@celke.uno", "203.0.113.7");
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("usuário inativo com a senha certa é recusado e auditado como USUARIO_INATIVO")
    void usuarioInativo() {
        Usuario inativa = UsuarioFixture.inativo(ana());
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(inativa));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-certa", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verify(auditoriaLoginService).registrar("ana@celke.uno", inativa, ResultadoLogin.USUARIO_INATIVO, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("ana@celke.uno", "203.0.113.7");
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("login bloqueado é recusado antes de buscar o usuário ou calcular hash, e auditado como BLOQUEADO")
    void bloqueado() {
        doThrow(new MuitasTentativasException(Duration.ofMinutes(15)))
                .when(bloqueioLoginService)
                .verificar("ana@celke.uno", "203.0.113.7");

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-certa", ORIGEM))
                .isInstanceOf(MuitasTentativasException.class)
                .hasMessage("Muitas tentativas. Tente novamente em 15 minutos.");
        verify(auditoriaLoginService).registrar("ana@celke.uno", null, ResultadoLogin.BLOQUEADO, ORIGEM);
        verify(usuarioService, never()).buscarPorEmail(any());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(bloqueioLoginService, never()).registrarFalha(anyString(), anyString());
    }

    @Test
    @DisplayName("renovar emite novo access token para o dono do refresh")
    void renovar() {
        Usuario ana = ana();
        when(refreshTokenService.rotacionar("refresh")).thenReturn(Optional.of(new RefreshEmitido("novo", 7L, EXPIRA)));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana));
        when(tokenAcessoService.emitir(ana)).thenReturn(new TokenAcesso("jwt", EXPIRA));

        var sessao = service.renovar("refresh");

        assertThat(sessao.refresh().token()).isEqualTo("novo");
        assertThat(sessao.acesso().valor()).isEqualTo("jwt");
    }

    @Test
    @DisplayName("refresh recusado vira sessão inválida")
    void refreshRecusado() {
        when(refreshTokenService.rotacionar("refresh")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renovar("refresh"))
                .isInstanceOf(SessaoInvalidaException.class)
                .hasMessage("Sessão expirada. Entre novamente.");
        verify(tokenAcessoService, never()).emitir(any());
    }

    @Test
    @DisplayName("refresh de usuário desativado vira sessão inválida")
    void renovarUsuarioInativo() {
        when(refreshTokenService.rotacionar("refresh")).thenReturn(Optional.of(new RefreshEmitido("novo", 7L, EXPIRA)));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));

        assertThatThrownBy(() -> service.renovar("refresh")).isInstanceOf(SessaoInvalidaException.class);
        verify(tokenAcessoService, never()).emitir(any());
    }

    @Test
    @DisplayName("sair e sair de todos delegam a revogação")
    void sair() {
        service.sair("refresh");
        service.sairDeTodos(7L);

        verify(refreshTokenService).revogarFamiliaDoToken("refresh");
        verify(refreshTokenService).revogarTodas(7L);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/AuthPropertiesTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AuthPropertiesTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "31 bytes é curto demais,  31, false",
        "exatamente 32 bytes,      32, true",
        "48 bytes,                 48, true",
    })
    void tamanhoDaChave(String caso, int bytes, boolean valido) {
        String segredo = Base64.getEncoder().encodeToString(new byte[bytes]);

        assertThat(props(segredo).isSegredoValido()).isEqualTo(valido);
    }

    @ParameterizedTest(name = "[{0}]")
    @CsvSource({"isto não é base64!, false", "'', true"})
    void formatoDaChave(String segredo, boolean valido) {
        // Vazio fica a cargo do @NotBlank; aqui só o formato inválido é acusado
        assertThat(props(segredo).isSegredoValido()).isEqualTo(valido);
    }

    private static AuthProperties props(String segredo) {
        return new AuthProperties(
                segredo, "plataforma-cursos", Duration.ofMinutes(10), Duration.ofDays(14), true, List.of());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/RefreshTokenServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.domain.RefreshToken;
import br.com.celke.cursos.auth.infra.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");
    private static final UUID FAMILIA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TOKEN = "token-do-cookie";

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;

    @BeforeEach
    void criar() {
        var props = new AuthProperties(
                "segredo", "plataforma-cursos", Duration.ofMinutes(10), Duration.ofDays(14), true, List.of());
        service = new RefreshTokenService(repository, props, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static RefreshToken tokenGravado(Instant expiraEm) {
        return new RefreshToken(7L, FAMILIA, RefreshTokenService.hash(TOKEN), expiraEm, AGORA.minusSeconds(60));
    }

    @Test
    @DisplayName("hash é o SHA-256 em hexadecimal")
    void hashSha256() {
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("emitir grava só o hash, numa família nova, com validade de 14 dias")
    void emitir() {
        var emitido = service.emitir(7L);

        ArgumentCaptor<RefreshToken> salvo = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getHash())
                .isEqualTo(RefreshTokenService.hash(emitido.token()))
                .isNotEqualTo(emitido.token());
        assertThat(salvo.getValue().getUsuarioId()).isEqualTo(7L);
        assertThat(emitido.expiraEm()).isEqualTo(AGORA.plus(Duration.ofDays(14)));
        assertThat(emitido.token()).hasSize(43); // 32 bytes em Base64 URL sem padding
    }

    @Test
    @DisplayName("cada emissão gera um token diferente")
    void tokensDiferentes() {
        assertThat(service.emitir(7L).token()).isNotEqualTo(service.emitir(7L).token());
    }

    @Test
    @DisplayName("rotacionar marca o token como usado e emite outro na mesma família")
    void rotacionar() {
        RefreshToken atual = tokenGravado(AGORA.plusSeconds(1));
        when(repository.findByHash(RefreshTokenService.hash(TOKEN))).thenReturn(Optional.of(atual));

        var novo = service.rotacionar(TOKEN);

        assertThat(novo).isPresent();
        assertThat(atual.foiUsado()).isTrue();
        ArgumentCaptor<RefreshToken> salvo = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getFamilia()).isEqualTo(FAMILIA);
        assertThat(salvo.getValue().getUsuarioId()).isEqualTo(7L);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("token ausente não consulta o banco")
    void tokenAusente(String token) {
        assertThat(service.rotacionar(token)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("token desconhecido é recusado")
    void tokenDesconhecido() {
        when(repository.findByHash(anyString())).thenReturn(Optional.empty());

        assertThat(service.rotacionar(TOKEN)).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("token expira no instante exato da validade")
    void expiradoNoInstanteExato() {
        when(repository.findByHash(anyString())).thenReturn(Optional.of(tokenGravado(AGORA)));

        assertThat(service.rotacionar(TOKEN)).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("token revogado é recusado sem revogar de novo")
    void revogado() {
        RefreshToken revogado = tokenGravado(AGORA.plusSeconds(60));
        ReflectionTestUtils.setField(revogado, "revogadoEm", AGORA.minusSeconds(1));
        when(repository.findByHash(anyString())).thenReturn(Optional.of(revogado));

        assertThat(service.rotacionar(TOKEN)).isEmpty();
        verify(repository, never()).save(any());
        verify(repository, never()).revogarFamilia(any(), any());
    }

    @Test
    @DisplayName("token já usado revoga a família inteira e não emite outro")
    void reutilizado() {
        RefreshToken usado = tokenGravado(AGORA.plusSeconds(60));
        usado.marcarUsado(AGORA.minusSeconds(5));
        when(repository.findByHash(anyString())).thenReturn(Optional.of(usado));

        assertThat(service.rotacionar(TOKEN)).isEmpty();
        verify(repository).revogarFamilia(FAMILIA, AGORA);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("logout revoga a família do token")
    void logout() {
        when(repository.findByHash(RefreshTokenService.hash(TOKEN)))
                .thenReturn(Optional.of(tokenGravado(AGORA.plusSeconds(60))));

        service.revogarFamiliaDoToken(TOKEN);

        verify(repository).revogarFamilia(FAMILIA, AGORA);
    }

    @Test
    @DisplayName("logout sem token não consulta o banco")
    void logoutSemToken() {
        service.revogarFamiliaDoToken(null);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("sair de todos revoga todas as famílias do usuário")
    void sairDeTodos() {
        service.revogarTodas(7L);

        verify(repository).revogarTodasDoUsuario(7L, AGORA);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/TokenAcessoServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.domain.Papel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/** Emissão com o encoder real e conferência com o decoder real da {@link JwtConfig}. */
class TokenAcessoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");
    private static final AuthProperties PROPS = new AuthProperties(
            "dGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUt",
            "plataforma-cursos",
            Duration.ofMinutes(10),
            Duration.ofDays(14),
            true,
            List.of());

    private final JwtConfig config = new JwtConfig();
    private final TokenAcessoService service =
            new TokenAcessoService(config.jwtEncoder(PROPS), PROPS, Clock.fixed(AGORA, ZoneOffset.UTC));

    @Test
    @DisplayName("token leva emissor, id do usuário, papéis ordenados e validade de 10 minutos")
    void claims() {
        var usuario = UsuarioFixture.usuario(42, "ana@celke.uno", "hash", Papel.ADMIN, Papel.ALUNO);

        var token = service.emitir(usuario);
        Jwt jwt = decoderEm(AGORA.plusSeconds(1)).decode(token.valor());

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("plataforma-cursos");
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsStringList("papeis")).containsExactly("ADMIN", "ALUNO");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getIssuedAt()).isEqualTo(AGORA);
        assertThat(jwt.getExpiresAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(10)));
        assertThat(token.expiraEm()).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    @DisplayName("token vale até um segundo antes do fim e é recusado no instante em que expira")
    void validade() {
        var token = service.emitir(UsuarioFixture.usuario(42, "ana@celke.uno", "hash", Papel.ALUNO));
        Instant expira = AGORA.plus(Duration.ofMinutes(10));

        assertThat(decoderEm(expira.minusSeconds(1)).decode(token.valor())).isNotNull();
        assertThatThrownBy(() -> decoderEm(expira.plusSeconds(1)).decode(token.valor()))
                .isInstanceOf(JwtValidationException.class);
    }

    private org.springframework.security.oauth2.jwt.JwtDecoder decoderEm(Instant instante) {
        return config.jwtDecoder(PROPS, Clock.fixed(instante, ZoneOffset.UTC));
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/api/CookieRefreshTest.java`:

`````java
package br.com.celke.cursos.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.auth.application.AuthProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CookieRefreshTest {

    private static CookieRefresh cookie(boolean seguro) {
        return new CookieRefresh(new AuthProperties(
                "segredo", "plataforma-cursos", Duration.ofMinutes(10), Duration.ofDays(14), seguro, List.of()));
    }

    @Test
    @DisplayName("cookie é HttpOnly, Secure, SameSite=Strict, só em /api/auth, sem Domain e vale 14 dias")
    void atributos() {
        assertThat(cookie(true).criar("abc").toString())
                .startsWith("refresh_token=abc;")
                .contains("Path=/api/auth", "Max-Age=1209600", "Secure", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Domain");
    }

    @Test
    @DisplayName("limpar zera o cookie com os mesmos atributos")
    void limpar() {
        assertThat(cookie(true).limpar().toString())
                .startsWith("refresh_token=;")
                .contains("Max-Age=0", "Path=/api/auth", "HttpOnly", "SameSite=Strict");
    }

    @Test
    @DisplayName("Secure pode ser desligado por configuração")
    void semSecure() {
        assertThat(cookie(false).criar("abc").toString()).doesNotContain("Secure");
    }
}
`````

`infra/compose.yml`:

`````yaml
# Ambiente local. Uso, na raiz do repositório:
#   ./infra/scripts/gerar-env.sh          (uma vez: cria infra/.env com segredos aleatórios)
#   docker compose -f infra/compose.yml up -d --build --wait
name: plataforma-cursos

# Configuração compartilhada pela API e pelo worker
x-app: &app
  DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:?defina POSTGRES_DB no infra/.env}
  DB_USERNAME: ${POSTGRES_USER:?defina POSTGRES_USER no infra/.env}
  DB_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD no infra/.env}
  JWT_SEGREDO: ${JWT_SEGREDO:?defina JWT_SEGREDO no infra/.env}
  # Origens aceitas no refresh e no logout: o Swagger da própria API e o front de desenvolvimento
  AUTH_ORIGENS: ${AUTH_ORIGENS:-http://localhost:${API_PORTA:-8080},http://localhost:5173}
  S3_ENDPOINT: http://garage:3900
  # Endereço que o navegador usa nas URLs pré-assinadas
  S3_ENDPOINT_PUBLICO: http://localhost:${S3_PORTA:-3900}
  S3_BUCKET: ${S3_BUCKET:?defina S3_BUCKET no infra/.env}
  S3_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY no infra/.env}
  S3_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY no infra/.env}
  S3_CORS_ORIGENS: ${S3_CORS_ORIGENS:-}
  # No desenvolvimento os e-mails ficam no Mailpit (http://localhost:8025)
  SMTP_HOST: mailpit
  SMTP_PORT: "1025"
  SMTP_AUTH: "false"
  SMTP_STARTTLS: "false"
  EMAIL_REMETENTE: ${EMAIL_REMETENTE:?defina EMAIL_REMETENTE no infra/.env}
  # Logs em JSON (ECS), com o id de correlação de cada requisição
  LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs

services:
  postgres:
    image: postgres:18.6-alpine
    environment:
      POSTGRES_DB: ${POSTGRES_DB:?defina POSTGRES_DB no infra/.env}
      POSTGRES_USER: ${POSTGRES_USER:?defina POSTGRES_USER no infra/.env}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD no infra/.env}
    volumes:
      # A partir da versão 18 os dados ficam em /var/lib/postgresql/18/docker
      - postgres:/var/lib/postgresql
    ports:
      - "127.0.0.1:${POSTGRES_PORTA:-5432}:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 20

  garage:
    image: dxflrs/garage:v2.4.1
    # Na primeira subida cria o layout de nó único, o bucket e a chave da aplicação
    command: ["/garage", "server", "--single-node", "--default-bucket"]
    environment:
      GARAGE_RPC_SECRET: ${GARAGE_RPC_SECRET:?defina GARAGE_RPC_SECRET no infra/.env}
      GARAGE_ADMIN_TOKEN: ${GARAGE_ADMIN_TOKEN:?defina GARAGE_ADMIN_TOKEN no infra/.env}
      GARAGE_DEFAULT_BUCKET: ${S3_BUCKET:?defina S3_BUCKET no infra/.env}
      GARAGE_DEFAULT_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY no infra/.env}
      GARAGE_DEFAULT_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY no infra/.env}
    volumes:
      - ./garage/garage.toml:/etc/garage.toml:ro
      - garage:/var/lib/garage
    ports:
      - "127.0.0.1:${S3_PORTA:-3900}:3900"
    healthcheck:
      test: ["CMD", "/garage", "status"]
      interval: 5s
      timeout: 5s
      retries: 20

  mailpit:
    image: axllent/mailpit:v1.31.6
    ports:
      - "127.0.0.1:${MAILPIT_PORTA:-8025}:8025"   # painel web
    healthcheck:
      test: ["CMD", "/mailpit", "readyz"]
      interval: 5s
      timeout: 3s
      retries: 20

  api:
    build:
      context: ../backend
      target: api
    environment:
      <<: *app
      DOCS_ENABLED: "true"
    ports:
      - "127.0.0.1:${API_PORTA:-8080}:8080"
    depends_on:
      postgres:
        condition: service_healthy
      garage:
        condition: service_healthy
      mailpit:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 5s
      timeout: 3s
      start_period: 30s
      retries: 20

  worker:
    build:
      context: ../backend
      target: worker
    environment:
      <<: *app
    depends_on:
      # As migrations são aplicadas pela API; o worker sobe depois dela
      api:
        condition: service_healthy

volumes:
  postgres:
  garage:
`````

`infra/.env.example`:

`````dotenv
# Copie para infra/.env (ou rode ./infra/scripts/gerar-env.sh, que gera os segredos).
# Nunca faça commit do infra/.env.

# PostgreSQL
POSTGRES_DB=plataforma_cursos
POSTGRES_USER=plataforma_cursos
POSTGRES_PASSWORD=                 # openssl rand -hex 24

# Autenticação: chave do JWT em Base64, com pelo menos 32 bytes
JWT_SEGREDO=                       # openssl rand -base64 48
# Origens aceitas no refresh e no logout (padrão: a própria API e http://localhost:5173)
# AUTH_ORIGENS=http://localhost:8080,http://localhost:5173

# Garage (S3)
GARAGE_RPC_SECRET=                 # openssl rand -hex 32
GARAGE_ADMIN_TOKEN=                # openssl rand -hex 32
S3_BUCKET=midia
S3_ACCESS_KEY=                     # GK + openssl rand -hex 12
S3_SECRET_KEY=                     # openssl rand -hex 32
# Origens do front que podem enviar e baixar direto do bucket (separadas por vírgula)
S3_CORS_ORIGENS=http://localhost:5173

# E-mail
EMAIL_REMETENTE=nao-responda@celke.uno

# Portas no host (opcional; só mude se a porta padrão já estiver em uso)
# API_PORTA=8080
# POSTGRES_PORTA=5432
# S3_PORTA=3900
# MAILPIT_PORTA=8025
`````

### 2. Peças compartilhadas

`backend/src/main/java/br/com/celke/cursos/shared/erro/ProblemDetailJson.java`:

`````java
package br.com.celke.cursos.shared.erro;

import br.com.celke.cursos.shared.web.IdRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escreve um ProblemDetail direto na resposta, para erros que nascem em filtros, antes do Spring
 * MVC (segurança, limite de requisições). Mesmo formato do {@link ManipuladorErros}.
 */
public final class ProblemDetailJson {

    private ProblemDetailJson() {}

    public static void escrever(
            JsonMapper jsonMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String detalhe)
            throws IOException {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("title", status.getReasonPhrase());
        corpo.put("status", status.value());
        corpo.put("detail", detalhe);
        corpo.put("instance", request.getRequestURI());
        corpo.put("requestId", MDC.get(IdRequisicao.CHAVE_MDC));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), corpo);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/erro/MuitasTentativasException.java`:

`````java
package br.com.celke.cursos.shared.erro;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/** Limite de tentativas atingido (429). A espera vai no cabeçalho {@code Retry-After}. */
public class MuitasTentativasException extends RegraNegocioException {

    private final Duration espera;

    public MuitasTentativasException(Duration espera) {
        super(HttpStatus.TOO_MANY_REQUESTS, mensagem(espera));
        this.espera = espera;
    }

    public Duration getEspera() {
        return espera;
    }

    /** Segundos para o {@code Retry-After}, arredondados para cima e nunca menos de 1. */
    public long segundosDeEspera() {
        return Math.max(1, (espera.toMillis() + 999) / 1000);
    }

    /** Minutos arredondados para cima: "1 minuto", "15 minutos". */
    public static String mensagem(Duration espera) {
        long minutos = Math.max(1, (espera.toSeconds() + 59) / 60);
        return "Muitas tentativas. Tente novamente em " + minutos + (minutos == 1 ? " minuto." : " minutos.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/web/OrigemRequisicao.java`:

`````java
package br.com.celke.cursos.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP e user-agent de quem fez a requisição, para auditoria e limites. O IP já vem corrigido pelo
 * {@code server.forward-headers-strategy}: atrás do Caddy é o IP do cliente, não o do proxy.
 */
public record OrigemRequisicao(String ip, String userAgent) {

    public static final int TAMANHO_MAXIMO_USER_AGENT = 255;

    public OrigemRequisicao {
        if (userAgent != null && userAgent.length() > TAMANHO_MAXIMO_USER_AGENT) {
            userAgent = userAgent.substring(0, TAMANHO_MAXIMO_USER_AGENT);
        }
    }

    public static OrigemRequisicao de(HttpServletRequest request) {
        return new OrigemRequisicao(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
`````

### 3. Limites e bloqueio

`backend/src/main/java/br/com/celke/cursos/auth/application/LimitesProperties.java`:

`````java
package br.com.celke.cursos.auth.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limites contra força bruta.
 *
 * @param requisicoesPorIpPorMinuto requisições a login e refresh por IP a cada minuto
 * @param falhasPorEmailEIp falhas de login seguidas que bloqueiam o par e-mail + IP
 * @param falhasPorEmail falhas de login seguidas, de qualquer IP, que bloqueiam o e-mail
 * @param janelaFalhas tempo sem novas falhas para o contador zerar
 * @param bloqueio duração do bloqueio
 */
@Validated
@ConfigurationProperties("app.limites")
public record LimitesProperties(
        @Min(1) int requisicoesPorIpPorMinuto,
        @Min(1) int falhasPorEmailEIp,
        @Min(1) int falhasPorEmail,
        @NotNull Duration janelaFalhas,
        @NotNull Duration bloqueio) {}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/BloqueioLoginService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Bloqueio temporário depois de falhas de login seguidas, em dois níveis:
 *
 * <ul>
 *   <li>e-mail + IP: barra rápido quem tenta adivinhar a senha, sem impedir o dono da conta de
 *       entrar de outro lugar;
 *   <li>e-mail, de qualquer IP: limite maior, contra tentativas distribuídas em muitos IPs.
 * </ul>
 *
 * Os contadores ficam em memória (a API roda numa instância só) e zeram com um login correto ou
 * depois da janela sem novas falhas.
 */
@Service
@EnableConfigurationProperties(LimitesProperties.class)
public class BloqueioLoginService {

    private static final Logger LOG = LoggerFactory.getLogger(BloqueioLoginService.class);

    private final LimitesProperties props;
    private final Clock clock;
    private final Cache<String, Contador> contadores;

    public BloqueioLoginService(LimitesProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        // Só limita a memória; a validade de cada contador é decidida pelo relógio da aplicação
        this.contadores = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(props.janelaFalhas().plus(props.bloqueio()))
                .build();
    }

    /** Recusa a tentativa se o par e-mail + IP ou o e-mail estiver bloqueado. */
    public void verificar(String email, String ip) {
        Instant agora = clock.instant();
        Duration espera = maior(esperaDe(chaveEmailIp(email, ip), agora), esperaDe(chaveEmail(email), agora));
        if (!espera.isZero()) {
            throw new MuitasTentativasException(espera);
        }
    }

    public void registrarFalha(String email, String ip) {
        Instant agora = clock.instant();
        contar(chaveEmailIp(email, ip), props.falhasPorEmailEIp(), agora);
        contar(chaveEmail(email), props.falhasPorEmail(), agora);
    }

    public void registrarSucesso(String email, String ip) {
        contadores.invalidate(chaveEmailIp(email, ip));
        contadores.invalidate(chaveEmail(email));
    }

    private void contar(String chave, int limite, Instant agora) {
        Contador contador = contadores.asMap().compute(chave, (k, atual) -> {
            Contador base = atual == null || atual.expirou(agora, props.janelaFalhas()) ? Contador.NOVO : atual;
            int falhas = base.falhas() + 1;
            if (falhas >= limite) {
                return new Contador(0, agora, agora.plus(props.bloqueio()));
            }
            return new Contador(falhas, agora, base.bloqueadoAte());
        });
        // O contador só volta a zero quando o limite é atingido e o bloqueio começa
        if (contador.falhas() == 0) {
            LOG.warn("Login bloqueado por {} ({} falhas seguidas)", props.bloqueio(), limite);
        }
    }

    private Duration esperaDe(String chave, Instant agora) {
        Contador contador = contadores.getIfPresent(chave);
        if (contador == null || contador.bloqueadoAte() == null || !agora.isBefore(contador.bloqueadoAte())) {
            return Duration.ZERO;
        }
        return Duration.between(agora, contador.bloqueadoAte());
    }

    private static Duration maior(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static String chaveEmail(String email) {
        return "email:" + email.strip().toLowerCase(Locale.ROOT);
    }

    private static String chaveEmailIp(String email, String ip) {
        return chaveEmail(email) + "|ip:" + ip;
    }

    /** Falhas desde o último bloqueio ou sucesso; {@code bloqueadoAte} nulo quando não há bloqueio. */
    private record Contador(int falhas, Instant ultimaFalha, Instant bloqueadoAte) {

        static final Contador NOVO = new Contador(0, null, null);

        /** Sem falhas recentes e sem bloqueio em vigor: o contador recomeça. */
        boolean expirou(Instant agora, Duration janela) {
            boolean semFalhaRecente = ultimaFalha == null || !agora.isBefore(ultimaFalha.plus(janela));
            boolean semBloqueio = bloqueadoAte == null || !agora.isBefore(bloqueadoAte);
            return semFalhaRecente && semBloqueio;
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/LimitePorIpFilter.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.LimitesProperties;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.erro.ProblemDetailJson;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limite de requisições por IP nas rotas de autenticação (token bucket do Bucket4j). Conta toda
 * requisição, até as inválidas, e roda antes da segurança e do Spring MVC: o excesso é barrado
 * sem calcular hash nem consultar o banco.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LimitePorIpFilter extends OncePerRequestFilter {

    /** Rotas limitadas (sempre POST). */
    static final Set<String> ROTAS = Set.of("/api/auth/login", "/api/auth/refresh");

    static final String CABECALHO_RESTANTE = "X-RateLimit-Remaining";

    private static final Logger LOG = LoggerFactory.getLogger(LimitePorIpFilter.class);

    private final int limitePorMinuto;
    private final JsonMapper jsonMapper;
    private final TimeMeter relogio;
    private final Cache<String, Bucket> baldes = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public LimitePorIpFilter(LimitesProperties props, JsonMapper jsonMapper, Clock clock) {
        this.limitePorMinuto = props.requisicoesPorIpPorMinuto();
        this.jsonMapper = jsonMapper;
        this.relogio = new RelogioBucket(clock);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !ROTAS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        ConsumptionProbe consumo = baldes.get(ip, chave -> novoBalde()).tryConsumeAndReturnRemaining(1);
        response.setHeader(CABECALHO_RESTANTE, String.valueOf(consumo.getRemainingTokens()));
        if (consumo.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        MuitasTentativasException limite =
                new MuitasTentativasException(Duration.ofNanos(consumo.getNanosToWaitForRefill()));
        LOG.warn("Limite de requisições por IP atingido em {}", request.getRequestURI());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(limite.segundosDeEspera()));
        ProblemDetailJson.escrever(jsonMapper, request, response, HttpStatus.TOO_MANY_REQUESTS, limite.getMessage());
    }

    private Bucket novoBalde() {
        return Bucket.builder()
                .addLimit(
                        limite -> limite.capacity(limitePorMinuto).refillGreedy(limitePorMinuto, Duration.ofMinutes(1)))
                .withCustomTimePrecision(relogio)
                .build();
    }

    /** Relógio do Bucket4j a partir do {@link Clock} da aplicação (testável). */
    record RelogioBucket(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            return Duration.ofMillis(clock.millis()).toNanos();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
`````

### 4. Auditoria de login

`backend/src/main/resources/db/migration/V4__criar_auditoria_login.sql`:

`````sql
-- Toda tentativa de login: quem tentou, de onde e o resultado
CREATE TABLE auditoria_login (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email       VARCHAR(254) NOT NULL,
    usuario_id  BIGINT       REFERENCES usuarios (id) ON DELETE SET NULL,
    resultado   VARCHAR(30)  NOT NULL,
    ip          VARCHAR(45)  NOT NULL,
    user_agent  VARCHAR(255),
    criado_em   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_auditoria_login_resultado
        CHECK (resultado IN ('SUCESSO', 'EMAIL_DESCONHECIDO', 'SENHA_INVALIDA', 'USUARIO_INATIVO', 'BLOQUEADO'))
);

CREATE INDEX ix_auditoria_login_email ON auditoria_login (email, criado_em);
CREATE INDEX ix_auditoria_login_usuario ON auditoria_login (usuario_id);
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/ResultadoLogin.java`:

`````java
package br.com.celke.cursos.auth.domain;

/** Resultado registrado na auditoria. O cliente só recebe "e-mail ou senha inválidos". */
public enum ResultadoLogin {
    SUCESSO,
    EMAIL_DESCONHECIDO,
    SENHA_INVALIDA,
    USUARIO_INATIVO,
    BLOQUEADO
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/AuditoriaLogin.java`:

`````java
package br.com.celke.cursos.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "auditoria_login")
public class AuditoriaLogin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResultadoLogin resultado;

    @Column(nullable = false, length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected AuditoriaLogin() {}

    public AuditoriaLogin(
            String email, Long usuarioId, ResultadoLogin resultado, String ip, String userAgent, Instant agora) {
        this.email = email;
        this.usuarioId = usuarioId;
        this.resultado = resultado;
        this.ip = ip;
        this.userAgent = userAgent;
        this.criadoEm = agora;
    }

    public String getEmail() {
        return email;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public ResultadoLogin getResultado() {
        return resultado;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/infra/AuditoriaLoginRepository.java`:

`````java
package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.auth.domain.AuditoriaLogin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaLoginRepository extends JpaRepository<AuditoriaLogin, Long> {}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/AuditoriaLoginService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.AuditoriaLogin;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.auth.infra.AuditoriaLoginRepository;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra cada tentativa de login numa transação própria: o registro fica gravado mesmo quando o
 * login falha e a transação dele é desfeita.
 */
@Service
public class AuditoriaLoginService {

    private final AuditoriaLoginRepository repository;
    private final Clock clock;

    public AuditoriaLoginService(AuditoriaLoginRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String email, Usuario usuario, ResultadoLogin resultado, OrigemRequisicao origem) {
        repository.save(new AuditoriaLogin(
                Usuario.normalizarEmail(email),
                usuario == null ? null : usuario.getId(),
                resultado,
                origem.ip(),
                origem.userAgent(),
                clock.instant()));
    }
}
`````

### 5. Verificação de origem (CSRF)

`backend/src/main/java/br/com/celke/cursos/auth/api/OrigemNaoPermitidaException.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Requisição de navegador vinda de uma origem fora da lista permitida. */
public class OrigemNaoPermitidaException extends RegraNegocioException {

    public OrigemNaoPermitidaException() {
        super(HttpStatus.FORBIDDEN, "Origem não permitida.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/VerificadorOrigem.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Proteção contra CSRF nas rotas que agem só pelo cookie (refresh e logout). O {@code SameSite=Strict}
 * não basta: outras aplicações em subdomínios do mesmo domínio contam como o mesmo site para o
 * navegador. Por isso a origem também é conferida:
 *
 * <ul>
 *   <li>{@code Sec-Fetch-Site: cross-site} é recusado;
 *   <li>{@code Origin} presente precisa estar na lista de origens permitidas ({@code null} é recusado);
 *   <li>sem {@code Origin}, a requisição não veio de um navegador (que sempre envia em POST) e passa.
 * </ul>
 */
@Component
public class VerificadorOrigem {

    private static final Logger LOG = LoggerFactory.getLogger(VerificadorOrigem.class);

    private final AuthProperties props;

    public VerificadorOrigem(AuthProperties props) {
        this.props = props;
    }

    public void verificar(HttpServletRequest request) {
        String site = request.getHeader("Sec-Fetch-Site");
        if ("cross-site".equalsIgnoreCase(site)) {
            recusar(request, "Sec-Fetch-Site: cross-site");
        }
        String origem = request.getHeader("Origin");
        if (origem == null) {
            return;
        }
        if (!props.origensPermitidas().contains(AuthProperties.normalizarOrigem(origem))) {
            recusar(request, "Origin: " + origem);
        }
    }

    private static void recusar(HttpServletRequest request, String motivo) {
        LOG.warn("Requisição recusada em {} ({})", request.getRequestURI(), motivo);
        throw new OrigemNaoPermitidaException();
    }
}
`````

### 6. Testes unitários

`backend/src/test/java/br/com/celke/cursos/support/fixtures/RelogioAjustavel.java`:

`````java
package br.com.celke.cursos.support.fixtures;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Relógio que só anda quando o teste manda: regras de tempo testadas sem esperar de verdade. */
public final class RelogioAjustavel extends Clock {

    private Instant agora;

    public RelogioAjustavel(Instant inicio) {
        this.agora = inicio;
    }

    public void avancar(Duration duracao) {
        agora = agora.plus(duracao);
    }

    @Override
    public Instant instant() {
        return agora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/erro/MuitasTentativasExceptionTest.java`:

`````java
package br.com.celke.cursos.shared.erro;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MuitasTentativasExceptionTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "zero vira 1 minuto,        0,    'Muitas tentativas. Tente novamente em 1 minuto.'",
        "1 segundo vira 1 minuto,   1,    'Muitas tentativas. Tente novamente em 1 minuto.'",
        "exatamente 60 segundos,    60,   'Muitas tentativas. Tente novamente em 1 minuto.'",
        "61 segundos arredonda,     61,   'Muitas tentativas. Tente novamente em 2 minutos.'",
        "15 minutos,                900,  'Muitas tentativas. Tente novamente em 15 minutos.'",
    })
    void mensagemEmMinutosArredondadaParaCima(String caso, long segundos, String esperada) {
        assertThat(new MuitasTentativasException(Duration.ofSeconds(segundos)).getMessage())
                .isEqualTo(esperada);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "zero vira 1 segundo,            0,    1",
        "1 milissegundo vira 1 segundo,  1,    1",
        "exatamente 1 segundo,           1000, 1",
        "1001 ms arredonda para 2,       1001, 2",
    })
    void retryAfterEmSegundosArredondadoParaCima(String caso, long milissegundos, long esperado) {
        assertThat(new MuitasTentativasException(Duration.ofMillis(milissegundos)).segundosDeEspera())
                .isEqualTo(esperado);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/web/OrigemRequisicaoTest.java`:

`````java
package br.com.celke.cursos.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class OrigemRequisicaoTest {

    @Test
    @DisplayName("lê o IP e o user-agent da requisição")
    void daRequisicao() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "Navegador/1.0");

        assertThat(OrigemRequisicao.de(request)).isEqualTo(new OrigemRequisicao("203.0.113.7", "Navegador/1.0"));
    }

    @Test
    @DisplayName("corta o user-agent em 255 caracteres e aceita a ausência dele")
    void userAgent() {
        assertThat(new OrigemRequisicao("ip", "a".repeat(255)).userAgent()).hasSize(255);
        assertThat(new OrigemRequisicao("ip", "a".repeat(256)).userAgent()).hasSize(255);
        assertThat(new OrigemRequisicao("ip", null).userAgent()).isNull();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/BloqueioLoginServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.support.fixtures.RelogioAjustavel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BloqueioLoginServiceTest {

    private static final String EMAIL = "ana@celke.uno";
    private static final String IP = "203.0.113.7";

    // 5 falhas por e-mail + IP, 20 por e-mail, janela de 1 h, bloqueio de 15 min
    private final LimitesProperties props =
            new LimitesProperties(30, 5, 20, Duration.ofHours(1), Duration.ofMinutes(15));

    private RelogioAjustavel relogio;
    private BloqueioLoginService service;

    @BeforeEach
    void criar() {
        relogio = new RelogioAjustavel(Instant.parse("2026-01-10T12:00:00Z"));
        service = new BloqueioLoginService(props, relogio);
    }

    private void falhar(String email, String ip, int vezes) {
        for (int i = 0; i < vezes; i++) {
            service.registrarFalha(email, ip);
        }
    }

    private void assertLiberado(String email, String ip) {
        assertThatCode(() -> service.verificar(email, ip)).doesNotThrowAnyException();
    }

    private void assertBloqueado(String email, String ip) {
        assertThatThrownBy(() -> service.verificar(email, ip)).isInstanceOf(MuitasTentativasException.class);
    }

    @Test
    @DisplayName("quatro falhas não bloqueiam; a quinta bloqueia o par e-mail + IP")
    void limitePorEmailEIp() {
        falhar(EMAIL, IP, 4);
        assertLiberado(EMAIL, IP);

        falhar(EMAIL, IP, 1);
        assertBloqueado(EMAIL, IP);
    }

    @Test
    @DisplayName("o bloqueio dura 15 minutos: ainda vale um segundo antes, acaba no instante exato")
    void duracaoDoBloqueio() {
        falhar(EMAIL, IP, 5);

        relogio.avancar(Duration.ofMinutes(15).minusSeconds(1));
        assertThatThrownBy(() -> service.verificar(EMAIL, IP))
                .isInstanceOf(MuitasTentativasException.class)
                .hasMessage("Muitas tentativas. Tente novamente em 1 minuto.");

        relogio.avancar(Duration.ofSeconds(1));
        assertLiberado(EMAIL, IP);
    }

    @Test
    @DisplayName("bloqueio do par e-mail + IP não impede o mesmo e-mail em outro IP")
    void outroIpLiberado() {
        falhar(EMAIL, IP, 5);

        assertLiberado(EMAIL, "198.51.100.9");
    }

    @Test
    @DisplayName("e-mail é comparado sem diferenciar maiúsculas e espaços nas pontas")
    void emailNormalizado() {
        falhar(" ANA@Celke.uno ", IP, 5);

        assertBloqueado(EMAIL, IP);
    }

    @Test
    @DisplayName("vinte falhas espalhadas por vários IPs bloqueiam o e-mail em qualquer IP")
    void limitePorEmail() {
        for (int i = 0; i < 19; i++) {
            service.registrarFalha(EMAIL, "10.0.0." + i);
        }
        assertLiberado(EMAIL, "10.0.0.250");

        service.registrarFalha(EMAIL, "10.0.0.19");
        assertBloqueado(EMAIL, "10.0.0.250");
    }

    @Test
    @DisplayName("login correto zera os contadores")
    void sucessoZera() {
        falhar(EMAIL, IP, 4);

        service.registrarSucesso(EMAIL, IP);
        falhar(EMAIL, IP, 4);

        assertLiberado(EMAIL, IP);
    }

    @Test
    @DisplayName("falhas antigas deixam de contar depois de uma hora sem novas falhas")
    void janelaExpira() {
        falhar(EMAIL, IP, 4);

        relogio.avancar(Duration.ofHours(1));
        falhar(EMAIL, IP, 4);

        assertLiberado(EMAIL, IP);
    }

    @Test
    @DisplayName("falhas dentro da janela continuam somando")
    void janelaAinda() {
        falhar(EMAIL, IP, 4);

        relogio.avancar(Duration.ofHours(1).minusSeconds(1));
        falhar(EMAIL, IP, 1);

        assertBloqueado(EMAIL, IP);
    }

    @Test
    @DisplayName("depois do bloqueio, mais cinco falhas bloqueiam de novo")
    void bloqueiaDeNovo() {
        falhar(EMAIL, IP, 5);
        relogio.avancar(Duration.ofMinutes(15));

        falhar(EMAIL, IP, 4);
        assertLiberado(EMAIL, IP);
        falhar(EMAIL, IP, 1);
        assertBloqueado(EMAIL, IP);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/api/VerificadorOrigemTest.java`:

`````java
package br.com.celke.cursos.auth.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.auth.application.AuthProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class VerificadorOrigemTest {

    private final VerificadorOrigem verificador = new VerificadorOrigem(new AuthProperties(
            "segredo",
            "plataforma-cursos",
            Duration.ofMinutes(10),
            Duration.ofDays(14),
            true,
            List.of("https://aluno.celke.uno", "https://ADM.celke.uno/ ")));

    private static MockHttpServletRequest requisicao(String origin, String secFetchSite) {
        var request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        if (secFetchSite != null) {
            request.addHeader("Sec-Fetch-Site", secFetchSite);
        }
        return request;
    }

    @ParameterizedTest(name = "[{0}] [{1}]")
    @CsvSource({
        "https://aluno.celke.uno,  same-origin",
        "https://ALUNO.celke.uno/, ",
        "https://adm.celke.uno,    same-site",
        ",                          ",
        ",                          same-origin",
        ",                          none",
    })
    @DisplayName("aceita origem da lista (sem diferenciar maiúsculas e barra final) e requisição sem Origin")
    void aceita(String origin, String secFetchSite) {
        assertThatCode(() -> verificador.verificar(requisicao(origin, secFetchSite)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(
            strings = {
                "https://outra-app.celke.uno",
                "http://aluno.celke.uno",
                "https://aluno.celke.uno:8443",
                "https://aluno.celke.uno.site-estranho.com",
                "null"
            })
    @DisplayName("recusa origem fora da lista, inclusive outro subdomínio do mesmo site e Origin null")
    void recusaOrigem(String origin) {
        assertThatThrownBy(() -> verificador.verificar(requisicao(origin, null)))
                .isInstanceOf(OrigemNaoPermitidaException.class)
                .hasMessage("Origem não permitida.");
    }

    @Test
    @DisplayName("recusa requisição marcada pelo navegador como cross-site, mesmo sem Origin")
    void recusaCrossSite() {
        assertThatThrownBy(() -> verificador.verificar(requisicao(null, "cross-site")))
                .isInstanceOf(OrigemNaoPermitidaException.class);
    }

    @Test
    @DisplayName("sem origens configuradas, toda requisição de navegador é recusada")
    void semOrigensConfiguradas() {
        var semLista = new VerificadorOrigem(new AuthProperties(
                "segredo", "plataforma-cursos", Duration.ofMinutes(10), Duration.ofDays(14), true, List.of()));

        assertThatThrownBy(() -> semLista.verificar(requisicao("https://aluno.celke.uno", null)))
                .isInstanceOf(OrigemNaoPermitidaException.class);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/api/LimitePorIpFilterTest.java`:

`````java
package br.com.celke.cursos.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.auth.application.LimitesProperties;
import br.com.celke.cursos.support.fixtures.RelogioAjustavel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** Filtro testado direto, com limite pequeno: 3 requisições por minuto. */
class LimitePorIpFilterTest {

    private RelogioAjustavel relogio;
    private LimitePorIpFilter filtro;

    @BeforeEach
    void criar() {
        relogio = new RelogioAjustavel(Instant.parse("2026-01-10T12:00:00Z"));
        filtro = new LimitePorIpFilter(
                new LimitesProperties(3, 5, 20, Duration.ofHours(1), Duration.ofMinutes(15)),
                JsonMapper.builder().build(),
                relogio);
    }

    private MockHttpServletResponse enviar(String metodo, String rota, String ip) throws Exception {
        var request = new MockHttpServletRequest(metodo, rota);
        request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    @DisplayName("três requisições passam; a quarta recebe 429 com Retry-After e ProblemDetail")
    void excedeLimite() throws Exception {
        for (int restante = 2; restante >= 0; restante--) {
            MockHttpServletResponse ok = enviar("POST", "/api/auth/login", "203.0.113.7");
            assertThat(ok.getStatus()).isEqualTo(200);
            assertThat(ok.getHeader("X-RateLimit-Remaining")).isEqualTo(String.valueOf(restante));
        }

        MockHttpServletResponse barrada = enviar("POST", "/api/auth/login", "203.0.113.7");

        assertThat(barrada.getStatus()).isEqualTo(429);
        assertThat(barrada.getContentType()).isEqualTo("application/problem+json");
        assertThat(barrada.getHeader("Retry-After")).isEqualTo("20");
        assertThat(barrada.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(barrada.getContentAsString())
                .contains("\"status\":429", "\"detail\":\"Muitas tentativas. Tente novamente em 1 minuto.\"");
    }

    @Test
    @DisplayName("o balde se recompõe com o tempo: 20 segundos devolvem uma requisição")
    void recompoe() throws Exception {
        for (int i = 0; i < 3; i++) {
            enviar("POST", "/api/auth/login", "203.0.113.7");
        }

        relogio.avancar(Duration.ofSeconds(19));
        assertThat(enviar("POST", "/api/auth/login", "203.0.113.7").getStatus()).isEqualTo(429);

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(enviar("POST", "/api/auth/login", "203.0.113.7").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("cada IP tem o próprio limite")
    void porIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            enviar("POST", "/api/auth/login", "203.0.113.7");
        }

        assertThat(enviar("POST", "/api/auth/login", "198.51.100.9").getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("login e refresh dividem o mesmo limite")
    void loginERefreshJuntos() throws Exception {
        enviar("POST", "/api/auth/login", "203.0.113.7");
        enviar("POST", "/api/auth/refresh", "203.0.113.7");
        enviar("POST", "/api/auth/login", "203.0.113.7");

        assertThat(enviar("POST", "/api/auth/refresh", "203.0.113.7").getStatus())
                .isEqualTo(429);
    }

    @Test
    @DisplayName("outras rotas e outros métodos não são limitados nem contam")
    void foraDoLimite() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse me = enviar("GET", "/api/auth/me", "203.0.113.7");
            assertThat(me.getStatus()).isEqualTo(200);
            assertThat(me.getHeader("X-RateLimit-Remaining")).isNull();
            enviar("GET", "/api/auth/login", "203.0.113.7");
        }

        assertThat(enviar("POST", "/api/auth/login", "203.0.113.7").getHeader("X-RateLimit-Remaining"))
                .isEqualTo("2");
    }
}
`````

### 7. Testes de integração

`backend/src/test/java/br/com/celke/cursos/auth/AuthBloqueioIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.usuario.domain.Papel;
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
 * Cobre: bloqueio do login depois de falhas seguidas (429 com Retry-After), liberação em outro IP,
 * auditoria de cada tentativa e o limite por IP ligado nas rotas de autenticação.
 * Não cobre: regras de tempo e contagem caso a caso (BloqueioLoginServiceTest, LimitePorIpFilterTest).
 * Dado criado: usuários it-<stamp>@teste.celke.uno e as tentativas deles na auditoria.
 */
class AuthBloqueioIT extends IntegrationTest {

    private static final String IP_ATACANTE = "203.0.113.50";
    private static final String IP_DONO = "198.51.100.20";

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("cinco senhas erradas bloqueiam o e-mail naquele IP, mesmo com a senha certa depois")
    void bloqueiaDepoisDeCincoFalhas() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        for (int i = 0; i < 5; i++) {
            assertThat(tentar(usuario.email(), "senha-errada", IP_ATACANTE)).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        MvcTestResult bloqueado = tentar(usuario.email(), usuario.senha(), IP_ATACANTE);

        assertThat(bloqueado)
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .hasHeader(HttpHeaders.RETRY_AFTER, "900")
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Muitas tentativas. Tente novamente em 15 minutos.");
    }

    @Test
    @DisplayName("o dono da conta entra de outro IP enquanto o IP do atacante está bloqueado")
    void outroIpEntra() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        for (int i = 0; i < 5; i++) {
            tentar(usuario.email(), "senha-errada", IP_ATACANTE);
        }

        assertThat(tentar(usuario.email(), usuario.senha(), IP_DONO)).hasStatusOk();
    }

    @Test
    @DisplayName("toda tentativa fica na auditoria com resultado, IP e user-agent")
    void auditoria() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String desconhecido = stamp() + "@teste.celke.uno";

        tentar(usuario.email(), "senha-errada", IP_ATACANTE);
        tentar(usuario.email(), usuario.senha(), IP_DONO);
        tentar(desconhecido, "qualquer", IP_ATACANTE);

        assertThat(auditoria(usuario.email()))
                .containsExactly(
                        new Tentativa("SENHA_INVALIDA", usuario.id(), IP_ATACANTE, "Teste/1.0"),
                        new Tentativa("SUCESSO", usuario.id(), IP_DONO, "Teste/1.0"));
        assertThat(auditoria(desconhecido))
                .containsExactly(new Tentativa("EMAIL_DESCONHECIDO", null, IP_ATACANTE, "Teste/1.0"));
    }

    @Test
    @DisplayName("tentativa barrada pelo bloqueio também fica na auditoria")
    void auditoriaDoBloqueio() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        for (int i = 0; i < 6; i++) {
            tentar(usuario.email(), "senha-errada", IP_ATACANTE);
        }

        assertThat(auditoria(usuario.email()))
                .extracting(Tentativa::resultado)
                .containsExactly(
                        "SENHA_INVALIDA",
                        "SENHA_INVALIDA",
                        "SENHA_INVALIDA",
                        "SENHA_INVALIDA",
                        "SENHA_INVALIDA",
                        "BLOQUEADO");
    }

    @Test
    @DisplayName("o limite por IP vale para login e refresh, e não para as outras rotas")
    void limitePorIpLigado() {
        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .headers()
                .containsHeader("X-RateLimit-Remaining");
        assertThat(mvc.post().uri("/api/auth/refresh")).headers().containsHeader("X-RateLimit-Remaining");
        assertThat(mvc.post().uri("/api/auth/logout")).headers().doesNotContainHeader("X-RateLimit-Remaining");
    }

    private MvcTestResult tentar(String email, String senha, String ip) {
        return mvc.post()
                .uri("/api/auth/login")
                .with(requisicao -> {
                    requisicao.setRemoteAddr(ip);
                    return requisicao;
                })
                .header(HttpHeaders.USER_AGENT, "Teste/1.0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .exchange();
    }

    private List<Tentativa> auditoria(String email) {
        return jdbc.sql("""
                        SELECT resultado, usuario_id, ip, user_agent FROM auditoria_login
                         WHERE email = :email ORDER BY id
                        """)
                .param("email", email)
                .query((rs, n) -> new Tentativa(
                        rs.getString("resultado"),
                        (Long) rs.getObject("usuario_id"),
                        rs.getString("ip"),
                        rs.getString("user_agent")))
                .list();
    }

    private record Tentativa(String resultado, Long usuarioId, String ip, String userAgent) {}
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/AuthOrigemIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.usuario.domain.Papel;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: proteção contra CSRF no refresh e no logout — origem fora da lista e Sec-Fetch-Site
 * cross-site recusados sem tocar na sessão; origem permitida aceita.
 * Não cobre: regras de comparação caso a caso (VerificadorOrigemTest).
 * Dado criado: usuários it-<stamp>@teste.celke.uno.
 */
class AuthOrigemIT extends IntegrationTest {

    private static final String ORIGEM_PERMITIDA = "http://localhost:5173";
    private static final String OUTRA_APP_DO_DOMINIO = "https://outra-app.celke.uno";

    @Test
    @DisplayName("refresh vindo de outra origem é recusado e a sessão continua valendo")
    void refreshDeOutraOrigem() {
        String cookie = cookieDeLogin();

        assertOrigemRecusada(refresh(cookie, OUTRA_APP_DO_DOMINIO, null));

        assertThat(refresh(cookie, ORIGEM_PERMITIDA, "same-origin")).hasStatusOk();
    }

    @Test
    @DisplayName("refresh marcado como cross-site é recusado")
    void refreshCrossSite() {
        assertOrigemRecusada(refresh(cookieDeLogin(), null, "cross-site"));
    }

    @Test
    @DisplayName("logout vindo de outra origem é recusado e não encerra a sessão")
    void logoutDeOutraOrigem() {
        String cookie = cookieDeLogin();

        assertOrigemRecusada(mvc.post()
                .uri("/api/auth/logout")
                .header("Origin", OUTRA_APP_DO_DOMINIO)
                .cookie(new Cookie("refresh_token", cookie))
                .exchange());

        assertThat(refresh(cookie, null, null)).hasStatusOk();
    }

    @Test
    @DisplayName("logout da origem permitida encerra a sessão")
    void logoutDaOrigemPermitida() {
        String cookie = cookieDeLogin();

        assertThat(mvc.post()
                        .uri("/api/auth/logout")
                        .header("Origin", ORIGEM_PERMITIDA)
                        .cookie(new Cookie("refresh_token", cookie)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(refresh(cookie, null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private String cookieDeLogin() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        return entrar(usuario.email(), usuario.senha())
                .getResponse()
                .getCookie("refresh_token")
                .getValue();
    }

    private MvcTestResult refresh(String cookie, String origem, String secFetchSite) {
        var requisicao = mvc.post().uri("/api/auth/refresh").cookie(new Cookie("refresh_token", cookie));
        if (origem != null) {
            requisicao.header("Origin", origem);
        }
        if (secFetchSite != null) {
            requisicao.header("Sec-Fetch-Site", secFetchSite);
        }
        return requisicao.exchange();
    }

    private static void assertOrigemRecusada(MvcTestResult resposta) {
        assertThat(resposta)
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Origem não permitida.");
    }
}
`````

## Antes de executar

Etapas 1.1 a 1.4 concluídas.

## Verificação

Dentro de `backend/`:

```bash
./mvnw spotless:apply
./mvnw verify
```

- `BUILD SUCCESS` com 146 testes unitários e 86 de integração, e a cobertura mínima atendida.

Na raiz do repositório, com a stack no ar (`docker compose -f infra/compose.yml up -d --build --wait`):

```bash
for i in 1 2 3 4 5 6; do
  curl -s -o /dev/null -w "%{http_code} " -H 'Content-Type: application/json' \
    -d '{"email":"invasor@exemplo.com","senha":"chute"}' localhost:8080/api/auth/login
done
```

- Saída `401 401 401 401 401 429`; a resposta 429 traz `Retry-After: 900` e
  `"detail":"Muitas tentativas. Tente novamente em 15 minutos."`.
- `POST /api/auth/refresh` com o cookie de um login e `-H 'Origin: https://outra-app.celke.uno'`
  responde 403 "Origem não permitida."; com `Origin: http://localhost:8080` responde 200.
- A tabela `auditoria_login` mostra as tentativas com resultado, IP e user-agent.

## Depois de executar

```bash
git add backend/ infra/
git commit -m "feat(auth): adicionar limite por IP, bloqueio, CSRF e auditoria de login"
```
