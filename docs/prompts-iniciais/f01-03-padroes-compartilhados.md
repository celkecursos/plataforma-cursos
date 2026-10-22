# Padrões compartilhados

**Fase:** 1 — etapa 1.3
**Branch:** `feature/base-projeto`

## Prompt

Vamos criar a base que todos os módulos do backend usam, em `shared/`, seguindo o `backend/CLAUDE.md`
e o padrão das skills de teste. Não faça commit.

O que esta etapa entrega:

- **Erros em ProblemDetail (RFC 9457)** para tudo: validação (lista `erros` com `campo` e `mensagem`,
  mensagens em português pelo locale fixo `pt_BR`), regra de negócio (`RegraNegocioException`,
  `NaoEncontradoException`, `ConflitoException`), erros do Spring MVC com mensagens em português
  (`messages.properties`) e erro inesperado, que só devolve o id da requisição. Todo ProblemDetail leva
  `instance` e `requestId`. O 401 e o 403 do Spring Security saem no mesmo formato
  (`RespostasSeguranca`), com `Content-Type: application/problem+json`.
- **Id de correlação** (`X-Request-Id`): filtro antes da segurança aceita o id recebido só se tiver até
  64 caracteres `[A-Za-z0-9._-]`, senão gera um UUID; vai para o MDC e para a resposta.
- **Paginação padrão**: `Pagina<T>` e `spring.data.web.pageable` (tamanho 20, máximo 100).
- **Relógio único** (`Clock` em UTC) para as regras de tempo.
- **Logs no horário de Brasília**: o `@timestamp` do JSON (ECS) sai com o deslocamento
  (`2026-10-22T10:33:20.123-03:00`) por um `StructuredLoggingJsonMembersCustomizer`, e o console de texto
  do desenvolvimento usa o mesmo fuso (`logging.pattern.dateformat`). Só a exibição muda: o banco e as
  regras continuam em instantes absolutos.
- **StorageService** com o AWS SDK v2 (path-style, checksums só quando exigidos): URLs pré-assinadas
  de PUT e GET, upload em partes, consulta e exclusão. O `S3Presigner` usa o endereço público, porque a
  assinatura inclui o host acessado pelo navegador; o `S3Client` usa a rede interna.
- **Configuração do bucket ao subir a API** (`ConfiguracaoBucket`, idempotente): CORS para as origens
  do front (expondo o `ETag`, necessário no upload em partes) e expiração de `uploads/` e de uploads em
  partes incompletos.
- **Fila no PostgreSQL** (tabela `jobs`): reserva com `FOR UPDATE SKIP LOCKED`, lease, backoff
  exponencial, `ErroPermanenteException` para falhar sem nova tentativa, processador por tipo de job e
  consumo agendado ligado só no worker (`app.fila.consumidor-ativo`). Todo método da fila recebe o
  instante atual.
- **E-mail pela fila**: `EmailService` valida destinatário e nome do template (só `[a-z0-9-]`) e
  enfileira; no worker, `EnvioEmailManipulador` monta o HTML com Thymeleaf (`templates/email/`) e envia
  pelo SMTP. O health check do e-mail fica desligado: SMTP fora do ar não derruba a API.
- **Configuração obrigatória** em `@ConfigurationProperties` validadas (`app.armazenamento`,
  `app.email`, `app.fila`): faltando valor, a aplicação não sobe.
- **Compose**: API e worker recebem S3, SMTP (Mailpit no desenvolvimento), remetente e logs em JSON
  (ECS) por um bloco compartilhado (`x-app`).

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
  # A API roda atrás do Caddy, que informa o host e o protocolo originais
  forward-headers-strategy: framework
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

`backend/src/main/resources/application-worker.yaml`:

`````yaml
# Worker: processa a fila (transcodificação de vídeo, e-mails) e não atende HTTP
spring:
  main:
    web-application-type: none
    # Sem servidor HTTP e com threads virtuais, nada segura a JVM: mantém o processo vivo
    keep-alive: true
  # As migrations são aplicadas pela API
  flyway:
    enabled: false

app:
  fila:
    consumidor-ativo: true
`````

`backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`:

`````java
package br.com.celke.cursos.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Segurança da API: tudo exige autenticação, exceto as rotas públicas declaradas aqui com o
 * método HTTP. A API é stateless (token no cabeçalho), sem sessão, formulário ou HTTP Basic. Só
 * existe na aplicação web: o worker não atende HTTP.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
        RespostasSeguranca respostas = new RespostasSeguranca(jsonMapper);
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 401 e 403 no formato ProblemDetail
                .exceptionHandling(
                        erros -> erros.authenticationEntryPoint(respostas).accessDeniedHandler(respostas))
                .authorizeHttpRequests(rotas -> rotas
                        // Health check e probes (Docker e Caddy)
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // Documentação: só responde quando DOCS_ENABLED=true
                        .requestMatchers(
                                HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated());
        return http.build();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/support/Containers.java`:

`````java
package br.com.celke.cursos.support;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Containers dos testes de integração. Como todas as classes de teste compartilham o mesmo
 * contexto Spring, cada container sobe uma vez por execução.
 */
@TestConfiguration(proxyBeanMethods = false)
public class Containers {

    public static final String S3_BUCKET = "midia";
    public static final String S3_ACCESS_KEY = "GK" + hex(12);
    public static final String S3_SECRET_KEY = hex(32);

    private static final String GARAGE_TOML = """
            metadata_dir = "/tmp/garage/meta"
            data_dir = "/tmp/garage/data"
            db_engine = "sqlite"
            replication_factor = 1
            rpc_bind_addr = "[::]:3901"
            rpc_public_addr = "127.0.0.1:3901"

            [s3_api]
            s3_region = "garage"
            api_bind_addr = "[::]:3900"
            """;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.6-alpine");
    }

    /** Garage em servidor único, com o bucket e a chave da aplicação criados na subida. */
    @Bean
    GenericContainer<?> garage() {
        return new GenericContainer<>("dxflrs/garage:v2.4.1")
                .withCopyToContainer(Transferable.of(GARAGE_TOML), "/etc/garage.toml")
                .withEnv("GARAGE_RPC_SECRET", hex(32))
                .withEnv("GARAGE_DEFAULT_BUCKET", S3_BUCKET)
                .withEnv("GARAGE_DEFAULT_ACCESS_KEY", S3_ACCESS_KEY)
                .withEnv("GARAGE_DEFAULT_SECRET_KEY", S3_SECRET_KEY)
                .withCommand("/garage", "server", "--single-node", "--default-bucket")
                .withExposedPorts(3900)
                // O bucket e a chave são criados antes de a API S3 abrir
                .waitingFor(Wait.forLogMessage(".*S3 API server listening.*", 1));
    }

    @Bean
    GenericContainer<?> mailpit() {
        return new GenericContainer<>("axllent/mailpit:v1.31.6")
                .withExposedPorts(1025, 8025)
                .waitingFor(Wait.forHttp("/readyz").forPort(8025));
    }

    @Bean
    Mailpit mailpitApi(GenericContainer<?> mailpit) {
        return new Mailpit("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025));
    }

    /** Endereços dos containers sem conexão automática do Spring Boot. */
    @Bean
    DynamicPropertyRegistrar propriedadesContainers(GenericContainer<?> garage, GenericContainer<?> mailpit) {
        return registro -> {
            String s3 = "http://" + garage.getHost() + ":" + garage.getMappedPort(3900);
            registro.add("app.armazenamento.endpoint", () -> s3);
            registro.add("app.armazenamento.endpoint-publico", () -> s3);
            registro.add("app.armazenamento.bucket", () -> S3_BUCKET);
            registro.add("app.armazenamento.access-key", () -> S3_ACCESS_KEY);
            registro.add("app.armazenamento.secret-key", () -> S3_SECRET_KEY);
            registro.add("spring.mail.host", mailpit::getHost);
            registro.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
        };
    }

    private static String hex(int bytes) {
        byte[] aleatorios = new byte[bytes];
        ThreadLocalRandom.current().nextBytes(aleatorios);
        return HexFormat.of().formatHex(aleatorios);
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

### 2. Erros em ProblemDetail

`backend/src/main/resources/messages.properties`:

`````properties
# Mensagens em português dos erros padrão do Spring MVC (campo "detail" do ProblemDetail)
problemDetail.org.springframework.web.HttpRequestMethodNotSupportedException=O método {0} não é aceito nesta rota.
problemDetail.org.springframework.web.HttpMediaTypeNotSupportedException=O tipo de conteúdo {0} não é aceito.
problemDetail.org.springframework.web.HttpMediaTypeNotAcceptableException=Não é possível responder no formato pedido.
problemDetail.org.springframework.web.servlet.resource.NoResourceFoundException=Recurso não encontrado.
problemDetail.org.springframework.http.converter.HttpMessageNotReadableException=O corpo da requisição é inválido.
problemDetail.org.springframework.web.bind.MissingServletRequestParameterException=O parâmetro {0} é obrigatório.
problemDetail.org.springframework.web.method.annotation.MethodArgumentTypeMismatchException=O valor do parâmetro {0} é inválido.
problemDetail.org.springframework.web.method.annotation.HandlerMethodValidationException=Dados inválidos.
`````

`backend/src/main/java/br/com/celke/cursos/shared/erro/RegraNegocioException.java`:

`````java
package br.com.celke.cursos.shared.erro;

import org.springframework.http.HttpStatus;

/**
 * Erro de negócio com mensagem para o usuário. A mensagem vai no {@code detail} do ProblemDetail,
 * por isso deve ser escrita em português e nunca conter dados internos.
 */
public class RegraNegocioException extends RuntimeException {

    private final HttpStatus status;

    public RegraNegocioException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/erro/NaoEncontradoException.java`:

`````java
package br.com.celke.cursos.shared.erro;

import org.springframework.http.HttpStatus;

/** Recurso inexistente ou de outro usuário (responder 404 nos dois casos não revela o que existe). */
public class NaoEncontradoException extends RegraNegocioException {

    public NaoEncontradoException(String mensagem) {
        super(HttpStatus.NOT_FOUND, mensagem);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/erro/ConflitoException.java`:

`````java
package br.com.celke.cursos.shared.erro;

import org.springframework.http.HttpStatus;

/** Operação que conflita com o estado atual (ex.: e-mail ou slug já cadastrado). */
public class ConflitoException extends RegraNegocioException {

    public ConflitoException(String mensagem) {
        super(HttpStatus.CONFLICT, mensagem);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/erro/ErroCampo.java`:

`````java
package br.com.celke.cursos.shared.erro;

/** Um campo inválido na lista {@code erros} do ProblemDetail de validação. */
public record ErroCampo(String campo, String mensagem) {}
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
        return handleExceptionInternal(ex, problema, new HttpHeaders(), ex.getStatus(), request);
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

import br.com.celke.cursos.shared.web.IdRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
        escrever(request, response, HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        escrever(request, response, HttpStatus.FORBIDDEN, "Acesso negado.");
    }

    private void escrever(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detalhe)
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

### 3. Relógio, id de correlação e paginação

`backend/src/main/java/br/com/celke/cursos/shared/config/RelogioConfig.java`:

`````java
package br.com.celke.cursos.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Relógio único da aplicação: regras de negócio recebem o {@link Clock}, nunca chamam {@code now()}. */
@Configuration
public class RelogioConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/config/FusoHorarioLogs.java`:

`````java
package br.com.celke.cursos.shared.config;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Logs em JSON (ECS) com o {@code @timestamp} no horário de Brasília, com o deslocamento explícito
 * ({@code 2026-10-22T10:33:20.123-03:00}): quem lê o log na VPS vê a hora local sem fazer conta, e o
 * valor continua sendo um instante ISO-8601 válido. Ligado em {@code logging.structured.json.customizer}.
 * Só a exibição muda: as regras de negócio e o banco continuam em instantes absolutos.
 */
public class FusoHorarioLogs implements StructuredLoggingJsonMembersCustomizer<Object> {

    public static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(BRASILIA);

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(
                JsonWriter.ValueProcessor.of(FusoHorarioLogs::emBrasilia).whenHasPath("@timestamp"));
    }

    static Object emBrasilia(Object valor) {
        return valor instanceof Instant instante ? FORMATO.format(instante) : valor;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/web/IdRequisicao.java`:

`````java
package br.com.celke.cursos.shared.web;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Id de correlação da requisição ({@code X-Request-Id}). Aceita o id recebido (do Caddy ou do
 * cliente) só se for curto e seguro para o log; senão gera um novo.
 */
public final class IdRequisicao {

    public static final String CABECALHO = "X-Request-Id";
    public static final String CHAVE_MDC = "requestId";

    private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private IdRequisicao() {}

    public static String normalizar(String recebido) {
        if (recebido != null && FORMATO_VALIDO.matcher(recebido).matches()) {
            return recebido;
        }
        return UUID.randomUUID().toString();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/web/IdRequisicaoFilter.java`:

`````java
package br.com.celke.cursos.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Primeiro filtro da requisição (antes da segurança): coloca o id de correlação no MDC, para todo
 * log da requisição carregar o mesmo id, e devolve o id no cabeçalho da resposta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class IdRequisicaoFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = IdRequisicao.normalizar(request.getHeader(IdRequisicao.CABECALHO));
        MDC.put(IdRequisicao.CHAVE_MDC, id);
        response.setHeader(IdRequisicao.CABECALHO, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(IdRequisicao.CHAVE_MDC);
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/web/Pagina.java`:

`````java
package br.com.celke.cursos.shared.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Formato padrão das listagens paginadas. A página começa em 0; tamanho padrão 20, máximo 100
 * ({@code spring.data.web.pageable}).
 */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total, int totalPaginas) {

    public static <T> Pagina<T> de(Page<T> page) {
        return new Pagina<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
`````

### 4. Armazenamento S3

`backend/src/main/java/br/com/celke/cursos/shared/armazenamento/ArmazenamentoProperties.java`:

`````java
package br.com.celke.cursos.shared.armazenamento;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do S3 (Garage). A aplicação não sobe se faltar algum valor obrigatório.
 *
 * @param endpoint endereço usado pela aplicação (rede interna, ex.: {@code http://garage:3900})
 * @param endpointPublico endereço que vai nas URLs pré-assinadas (o que o navegador acessa)
 * @param corsOrigens origens do front que podem enviar e baixar direto do bucket
 * @param diasUploadsAbandonados dias até o bucket apagar o que ficou em {@code uploads/}
 */
@Validated
@ConfigurationProperties("app.armazenamento")
public record ArmazenamentoProperties(
        @NotBlank String endpoint,
        @NotBlank String endpointPublico,
        @NotBlank String regiao,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        List<String> corsOrigens,
        @Min(1) int diasUploadsAbandonados) {

    public ArmazenamentoProperties {
        corsOrigens = corsOrigens == null
                ? List.of()
                : corsOrigens.stream()
                        .map(String::strip)
                        .filter(o -> !o.isEmpty())
                        .toList();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/armazenamento/ArmazenamentoConfig.java`:

`````java
package br.com.celke.cursos.shared.armazenamento;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Clientes do S3. O {@link S3Client} fala com o Garage pela rede interna; o {@link S3Presigner}
 * assina URLs com o endereço público, porque a assinatura inclui o host acessado pelo navegador.
 */
@Configuration
@EnableConfigurationProperties(ArmazenamentoProperties.class)
public class ArmazenamentoConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(ArmazenamentoProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.regiao()))
                .credentialsProvider(credenciais(props))
                .forcePathStyle(true)
                // Checksums só quando a operação exige: compatível com S3 que não é da AWS
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(ArmazenamentoProperties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.endpointPublico()))
                .region(Region.of(props.regiao()))
                .credentialsProvider(credenciais(props))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static StaticCredentialsProvider credenciais(ArmazenamentoProperties props) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/armazenamento/StorageService.java`:

`````java
package br.com.celke.cursos.shared.armazenamento;

import java.net.URL;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Acesso ao bucket da aplicação. O navegador envia e baixa arquivos direto do S3 por URLs
 * pré-assinadas; a API nunca recebe o conteúdo dos arquivos grandes.
 */
@Service
public class StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public StorageService(S3Client s3, S3Presigner presigner, ArmazenamentoProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = props.bucket();
    }

    /** URL para o navegador enviar um arquivo pequeno com um único PUT. */
    public URL urlUpload(String chave, String contentType, Duration validade) {
        return presigner
                .presignPutObject(p -> p.signatureDuration(validade)
                        .putObjectRequest(o -> o.bucket(bucket).key(chave).contentType(contentType)))
                .url();
    }

    /** URL para baixar um arquivo. */
    public URL urlDownload(String chave, Duration validade) {
        return presigner
                .presignGetObject(p -> p.signatureDuration(validade)
                        .getObjectRequest(o -> o.bucket(bucket).key(chave)))
                .url();
    }

    /** Inicia um upload em partes e devolve o id do upload. */
    public String iniciarMultipart(String chave, String contentType) {
        return s3.createMultipartUpload(r -> r.bucket(bucket).key(chave).contentType(contentType))
                .uploadId();
    }

    /** URL para o navegador enviar uma parte (a partir de 1). Toda parte, menos a última, tem ao menos 5 MB. */
    public URL urlParte(String chave, String uploadId, int numeroParte, Duration validade) {
        return presigner
                .presignUploadPart(p -> p.signatureDuration(validade)
                        .uploadPartRequest(u ->
                                u.bucket(bucket).key(chave).uploadId(uploadId).partNumber(numeroParte)))
                .url();
    }

    /** Junta as partes enviadas; o ETag de cada parte vem da resposta do PUT da parte. */
    public void concluirMultipart(String chave, String uploadId, List<ParteEnviada> partes) {
        List<CompletedPart> concluidas = partes.stream()
                .sorted(Comparator.comparingInt(ParteEnviada::numero))
                .map(p -> CompletedPart.builder()
                        .partNumber(p.numero())
                        .eTag(p.etag())
                        .build())
                .toList();
        s3.completeMultipartUpload(
                r -> r.bucket(bucket).key(chave).uploadId(uploadId).multipartUpload(m -> m.parts(concluidas)));
    }

    public void abortarMultipart(String chave, String uploadId) {
        s3.abortMultipartUpload(r -> r.bucket(bucket).key(chave).uploadId(uploadId));
    }

    /** Tamanho e tipo do arquivo, ou vazio se ele não existe. */
    public Optional<InfoArquivo> info(String chave) {
        try {
            HeadObjectResponse head = s3.headObject(r -> r.bucket(bucket).key(chave));
            return Optional.of(new InfoArquivo(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public void excluir(String chave) {
        s3.deleteObject(r -> r.bucket(bucket).key(chave));
    }

    public record ParteEnviada(int numero, String etag) {}

    public record InfoArquivo(long tamanho, String contentType) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/armazenamento/ConfiguracaoBucket.java`:

`````java
package br.com.celke.cursos.shared.armazenamento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.LifecycleRule;

/**
 * Aplica a configuração do bucket ao subir a API (idempotente): CORS para o front enviar e baixar
 * direto do bucket, e expiração de uploads abandonados. Fica no código para valer igual em todo
 * ambiente e em qualquer S3 compatível.
 */
@Component
@ConditionalOnWebApplication
public class ConfiguracaoBucket implements ApplicationRunner {

    static final String PREFIXO_UPLOADS = "uploads/";

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguracaoBucket.class);

    private final S3Client s3;
    private final ArmazenamentoProperties props;

    public ConfiguracaoBucket(S3Client s3, ArmazenamentoProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        aplicarCors();
        aplicarExpiracao();
    }

    private void aplicarCors() {
        if (props.corsOrigens().isEmpty()) {
            s3.deleteBucketCors(r -> r.bucket(props.bucket()));
            LOG.info("CORS do bucket {} removido (nenhuma origem configurada)", props.bucket());
            return;
        }
        CORSRule regra = CORSRule.builder()
                .allowedOrigins(props.corsOrigens())
                .allowedMethods("GET", "PUT", "HEAD")
                .allowedHeaders("*")
                // O ETag de cada parte é lido pelo navegador para concluir o upload em partes
                .exposeHeaders("ETag")
                .maxAgeSeconds(3600)
                .build();
        s3.putBucketCors(r -> r.bucket(props.bucket()).corsConfiguration(c -> c.corsRules(regra)));
        LOG.info("CORS do bucket {} aplicado para {}", props.bucket(), props.corsOrigens());
    }

    private void aplicarExpiracao() {
        LifecycleRule regra = LifecycleRule.builder()
                .id("expirar-uploads-abandonados")
                .status(ExpirationStatus.ENABLED)
                .filter(f -> f.prefix(PREFIXO_UPLOADS))
                .expiration(e -> e.days(props.diasUploadsAbandonados()))
                .abortIncompleteMultipartUpload(a -> a.daysAfterInitiation(1))
                .build();
        s3.putBucketLifecycleConfiguration(r -> r.bucket(props.bucket()).lifecycleConfiguration(l -> l.rules(regra)));
        LOG.info(
                "Uploads em {} expiram em {} dia(s) no bucket {}",
                PREFIXO_UPLOADS,
                props.diasUploadsAbandonados(),
                props.bucket());
    }
}
`````

### 5. Fila no PostgreSQL

`backend/src/main/resources/db/migration/V1__criar_jobs.sql`:

`````sql
-- Fila de processamento em segundo plano (e-mails, transcodificação de vídeo...)
CREATE TABLE jobs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo           VARCHAR(50)  NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    tentativas     INT          NOT NULL DEFAULT 0,
    max_tentativas INT          NOT NULL,
    disponivel_em  TIMESTAMPTZ  NOT NULL,
    reservado_ate  TIMESTAMPTZ,
    ultimo_erro    TEXT,
    criado_em      TIMESTAMPTZ  NOT NULL,
    atualizado_em  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_jobs_status CHECK (status IN ('PENDENTE', 'PROCESSANDO', 'CONCLUIDO', 'ERRO')),
    CONSTRAINT ck_jobs_tentativas CHECK (tentativas >= 0 AND max_tentativas > 0)
);

-- Só os jobs que ainda podem ser reservados entram no índice
CREATE INDEX ix_jobs_reservaveis ON jobs (tipo, disponivel_em)
    WHERE status IN ('PENDENTE', 'PROCESSANDO');
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/FilaProperties.java`:

`````java
package br.com.celke.cursos.shared.fila;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração da fila.
 *
 * @param maxTentativas tentativas antes de o job ir para ERRO
 * @param lease tempo que um job reservado fica com o worker; vencido, outro worker pode pegá-lo
 * @param backoffBase atraso depois da primeira falha (dobra a cada nova falha)
 * @param backoffMaximo atraso máximo entre tentativas
 * @param intervalo pausa do consumidor quando a fila está vazia
 * @param consumidorAtivo liga o consumo agendado (só no worker)
 */
@Validated
@ConfigurationProperties("app.fila")
public record FilaProperties(
        @Min(1) int maxTentativas,
        @NotNull Duration lease,
        @NotNull Duration backoffBase,
        @NotNull Duration backoffMaximo,
        @NotNull Duration intervalo,
        boolean consumidorAtivo) {}
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/Backoff.java`:

`````java
package br.com.celke.cursos.shared.fila;

import java.time.Duration;

/** Atraso exponencial entre tentativas: base, 2×base, 4×base... limitado ao máximo. */
public final class Backoff {

    private final Duration base;
    private final Duration maximo;

    public Backoff(Duration base, Duration maximo) {
        if (base.isNegative() || base.isZero() || maximo.compareTo(base) < 0) {
            throw new IllegalArgumentException("Backoff exige base positiva e máximo maior ou igual à base.");
        }
        this.base = base;
        this.maximo = maximo;
    }

    /** Atraso depois da falha da tentativa informada (a primeira tentativa é 1). */
    public Duration atraso(int tentativa) {
        if (tentativa < 1) {
            throw new IllegalArgumentException("A tentativa começa em 1.");
        }
        // A partir de 2^62 a multiplicação estoura; muito antes disso o máximo já foi atingido
        int expoente = Math.min(tentativa - 1, 62);
        long fator = 1L << expoente;
        long limite = maximo.toMillis() / base.toMillis();
        if (fator >= limite) {
            return maximo;
        }
        return base.multipliedBy(fator);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/Job.java`:

`````java
package br.com.celke.cursos.shared.fila;

/**
 * Job reservado para processamento.
 *
 * @param payload JSON com os dados do job, lido pelo manipulador do tipo
 * @param tentativas tentativas feitas, contando a atual
 */
public record Job(long id, String tipo, String payload, int tentativas, int maxTentativas) {

    /** Passou do limite: acontece quando o worker cai no meio do processamento várias vezes. */
    public boolean excedeuTentativas() {
        return tentativas > maxTentativas;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/StatusJob.java`:

`````java
package br.com.celke.cursos.shared.fila;

public enum StatusJob {
    PENDENTE,
    PROCESSANDO,
    CONCLUIDO,
    ERRO
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/ManipuladorJob.java`:

`````java
package br.com.celke.cursos.shared.fila;

/**
 * Processa os jobs de um tipo. Exceção lançada = falha com nova tentativa (até o limite);
 * {@link ErroPermanenteException} = falha sem nova tentativa.
 */
public interface ManipuladorJob {

    String tipo();

    void processar(Job job) throws Exception;
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/ErroPermanenteException.java`:

`````java
package br.com.celke.cursos.shared.fila;

/** Erro que não se resolve tentando de novo (ex.: dado inválido no payload). */
public class ErroPermanenteException extends RuntimeException {

    public ErroPermanenteException(String mensagem) {
        super(mensagem);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/FilaService.java`:

`````java
package br.com.celke.cursos.shared.fila;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fila de jobs no PostgreSQL. A reserva usa {@code FOR UPDATE SKIP LOCKED}: vários workers consomem
 * ao mesmo tempo sem pegar o mesmo job. Todo método recebe o instante atual, para as regras de tempo
 * serem testáveis.
 */
@Service
@EnableConfigurationProperties(FilaProperties.class)
public class FilaService {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final FilaProperties props;
    private final Backoff backoff;

    public FilaService(JdbcClient jdbc, JsonMapper jsonMapper, FilaProperties props) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.props = props;
        this.backoff = new Backoff(props.backoffBase(), props.backoffMaximo());
    }

    /** Coloca um job na fila; o payload é gravado como JSON. Devolve o id do job. */
    public long enfileirar(String tipo, Object payload, Instant agora) {
        return jdbc.sql("""
                        INSERT INTO jobs (tipo, payload, status, max_tentativas, disponivel_em, criado_em, atualizado_em)
                        VALUES (:tipo, CAST(:payload AS JSONB), 'PENDENTE', :max, :agora, :agora, :agora)
                        RETURNING id
                        """)
                .param("tipo", tipo)
                .param("payload", jsonMapper.writeValueAsString(payload))
                .param("max", props.maxTentativas())
                .param("agora", utc(agora))
                .query(Long.class)
                .single();
    }

    /**
     * Reserva o próximo job disponível de um dos tipos: pendente e no horário, ou em processamento
     * com o lease vencido (o worker anterior caiu).
     */
    @Transactional
    public Optional<Job> reservar(Collection<String> tipos, Instant agora) {
        if (tipos.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        UPDATE jobs
                           SET status = 'PROCESSANDO', tentativas = tentativas + 1,
                               reservado_ate = :reservadoAte, atualizado_em = :agora
                         WHERE id = (SELECT id FROM jobs
                                      WHERE tipo IN (:tipos)
                                        AND ((status = 'PENDENTE' AND disponivel_em <= :agora)
                                          OR (status = 'PROCESSANDO' AND reservado_ate < :agora))
                                      ORDER BY disponivel_em, id
                                      LIMIT 1
                                      FOR UPDATE SKIP LOCKED)
                        RETURNING id, tipo, CAST(payload AS TEXT) AS payload, tentativas, max_tentativas
                        """)
                .param("tipos", tipos)
                .param("agora", utc(agora))
                .param("reservadoAte", utc(agora.plus(props.lease())))
                .query((rs, n) -> new Job(
                        rs.getLong("id"),
                        rs.getString("tipo"),
                        rs.getString("payload"),
                        rs.getInt("tentativas"),
                        rs.getInt("max_tentativas")))
                .optional();
    }

    public void concluir(Job job, Instant agora) {
        jdbc.sql("""
                        UPDATE jobs SET status = 'CONCLUIDO', reservado_ate = NULL, ultimo_erro = NULL,
                                        atualizado_em = :agora
                         WHERE id = :id
                        """).param("id", job.id()).param("agora", utc(agora)).update();
    }

    /** Falha com nova tentativa depois do backoff; sem tentativas restantes, o job vai para ERRO. */
    public void falhar(Job job, String erro, Instant agora) {
        if (job.tentativas() >= job.maxTentativas()) {
            falharDefinitivo(job, erro, agora);
            return;
        }
        jdbc.sql("""
                        UPDATE jobs SET status = 'PENDENTE', reservado_ate = NULL, ultimo_erro = :erro,
                                        disponivel_em = :disponivelEm, atualizado_em = :agora
                         WHERE id = :id
                        """)
                .param("id", job.id())
                .param("erro", erro)
                .param("disponivelEm", utc(agora.plus(backoff.atraso(job.tentativas()))))
                .param("agora", utc(agora))
                .update();
    }

    /** Falha sem nova tentativa (erro permanente ou tentativas esgotadas). */
    public void falharDefinitivo(Job job, String erro, Instant agora) {
        jdbc.sql("""
                        UPDATE jobs SET status = 'ERRO', reservado_ate = NULL, ultimo_erro = :erro,
                                        atualizado_em = :agora
                         WHERE id = :id
                        """)
                .param("id", job.id())
                .param("erro", erro)
                .param("agora", utc(agora))
                .update();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/ProcessadorFila.java`:

`````java
package br.com.celke.cursos.shared.fila;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Reserva um job, entrega ao manipulador do tipo e registra o resultado na fila. */
@Component
public class ProcessadorFila {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessadorFila.class);

    private final FilaService fila;
    private final Map<String, ManipuladorJob> manipuladores;
    private final Clock clock;

    public ProcessadorFila(FilaService fila, List<ManipuladorJob> manipuladores, Clock clock) {
        this.fila = fila;
        this.manipuladores =
                manipuladores.stream().collect(Collectors.toUnmodifiableMap(ManipuladorJob::tipo, Function.identity()));
        this.clock = clock;
    }

    /** Processa um job; devolve {@code false} quando não havia job disponível. */
    public boolean processarProximo() {
        var reservado = fila.reservar(manipuladores.keySet(), clock.instant());
        if (reservado.isEmpty()) {
            return false;
        }
        Job job = reservado.get();
        if (job.excedeuTentativas()) {
            fila.falharDefinitivo(job, "Tentativas esgotadas (o processamento foi interrompido).", clock.instant());
            return true;
        }
        try {
            manipuladores.get(job.tipo()).processar(job);
            fila.concluir(job, clock.instant());
        } catch (ErroPermanenteException e) {
            LOG.error("Job {} ({}) falhou sem nova tentativa: {}", job.id(), job.tipo(), e.getMessage());
            fila.falharDefinitivo(job, e.getMessage(), clock.instant());
        } catch (Exception e) {
            LOG.warn("Job {} ({}) falhou na tentativa {}: {}", job.id(), job.tipo(), job.tentativas(), e.toString());
            fila.falhar(job, e.toString(), clock.instant());
        }
        return true;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/ConsumidorFila.java`:

`````java
package br.com.celke.cursos.shared.fila;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Consumo contínuo da fila. Só ligado no worker ({@code app.fila.consumidor-ativo=true}). */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
public class ConsumidorFila {

    private final ProcessadorFila processador;

    public ConsumidorFila(ProcessadorFila processador) {
        this.processador = processador;
    }

    /** Esvazia a fila e espera o intervalo antes de olhar de novo. */
    @Scheduled(fixedDelayString = "${app.fila.intervalo}")
    void consumir() {
        while (processador.processarProximo()) {
            // continua enquanto houver job disponível
        }
    }
}
`````

### 6. E-mail

`backend/src/main/java/br/com/celke/cursos/shared/email/EmailProperties.java`:

`````java
package br.com.celke.cursos.shared.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Remetente dos e-mails. O servidor SMTP é configurado em {@code spring.mail}. */
@Validated
@ConfigurationProperties("app.email")
public record EmailProperties(
        @NotBlank @Email String remetente, @NotBlank String nomeRemetente) {}
`````

`backend/src/main/java/br/com/celke/cursos/shared/email/MensagemEmail.java`:

`````java
package br.com.celke.cursos.shared.email;

import java.util.Map;

/**
 * Payload do job de e-mail.
 *
 * @param template nome do template em {@code templates/email/} (sem extensão)
 * @param variaveis valores usados no template
 */
public record MensagemEmail(String para, String assunto, String template, Map<String, String> variaveis) {}
`````

`backend/src/main/java/br/com/celke/cursos/shared/email/EmailService.java`:

`````java
package br.com.celke.cursos.shared.email;

import br.com.celke.cursos.shared.fila.FilaService;
import java.time.Clock;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Envio de e-mail pela fila: a requisição só grava o job e responde; o worker envia, com novas
 * tentativas se o SMTP falhar.
 */
@Service
@EnableConfigurationProperties(EmailProperties.class)
public class EmailService {

    static final String TIPO_JOB = "EMAIL";

    /** Só letras minúsculas, números e hífen: o nome do template nunca vira um caminho arbitrário. */
    static final Pattern NOME_TEMPLATE = Pattern.compile("[a-z0-9-]+");

    private final FilaService fila;
    private final Clock clock;

    public EmailService(FilaService fila, Clock clock) {
        this.fila = fila;
        this.clock = clock;
    }

    public void enviar(String para, String assunto, String template, Map<String, String> variaveis) {
        if (para == null || para.isBlank() || !para.contains("@")) {
            throw new IllegalArgumentException("Destinatário inválido.");
        }
        if (template == null || !NOME_TEMPLATE.matcher(template).matches()) {
            throw new IllegalArgumentException("Nome de template inválido.");
        }
        fila.enfileirar(TIPO_JOB, new MensagemEmail(para.strip(), assunto, template, variaveis), clock.instant());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/email/EnvioEmailManipulador.java`:

`````java
package br.com.celke.cursos.shared.email;

import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.shared.fila.Job;
import br.com.celke.cursos.shared.fila.ManipuladorJob;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import tools.jackson.databind.json.JsonMapper;

/** Monta o HTML do template com Thymeleaf e envia pelo SMTP configurado. Roda no worker. */
@Component
public class EnvioEmailManipulador implements ManipuladorJob {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final JsonMapper jsonMapper;
    private final EmailProperties props;

    public EnvioEmailManipulador(
            JavaMailSender mailSender, TemplateEngine templateEngine, JsonMapper jsonMapper, EmailProperties props) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.jsonMapper = jsonMapper;
        this.props = props;
    }

    @Override
    public String tipo() {
        return EmailService.TIPO_JOB;
    }

    @Override
    public void processar(Job job) throws Exception {
        MensagemEmail mensagem = jsonMapper.readValue(job.payload(), MensagemEmail.class);
        if (mensagem.template() == null
                || !EmailService.NOME_TEMPLATE.matcher(mensagem.template()).matches()) {
            throw new ErroPermanenteException("Nome de template inválido.");
        }
        Context contexto = new Context(PT_BR);
        if (mensagem.variaveis() != null) {
            mensagem.variaveis().forEach(contexto::setVariable);
        }
        String html = templateEngine.process("email/" + mensagem.template(), contexto);

        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
        helper.setFrom(props.remetente(), props.nomeRemetente());
        helper.setTo(mensagem.para());
        helper.setSubject(mensagem.assunto());
        helper.setText(html, true);
        mailSender.send(mime);
    }
}
`````

### 7. Infraestrutura de testes

`backend/src/test/java/br/com/celke/cursos/support/Mailpit.java`:

`````java
package br.com.celke.cursos.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import org.springframework.web.client.RestClient;

/** Consulta os e-mails capturados pelo Mailpit (API HTTP do container). */
public class Mailpit {

    private final RestClient http;

    public Mailpit(String urlBase) {
        this.http = RestClient.create(urlBase);
    }

    /** Espera chegar um e-mail para o destinatário e devolve o mais recente. */
    public EmailRecebido aguardarEmailPara(String destinatario) {
        String id = await("e-mail para " + destinatario)
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> idMaisRecente(destinatario), resultado -> resultado != null);
        return http.get().uri("/api/v1/message/{id}", id).retrieve().body(EmailRecebido.class);
    }

    private String idMaisRecente(String destinatario) {
        Busca busca = http.get()
                .uri(u -> u.path("/api/v1/search")
                        .queryParam("query", "to:\"" + destinatario + "\"")
                        .build())
                .retrieve()
                .body(Busca.class);
        return busca == null || busca.messages().isEmpty()
                ? null
                : busca.messages().getFirst().ID();
    }

    record Busca(List<Resumo> messages) {}

    record Resumo(String ID) {}

    public record Endereco(String Name, String Address) {}

    /** Campos do e-mail na API do Mailpit (nomes em PascalCase, como a API devolve). */
    public record EmailRecebido(String Subject, Endereco From, List<Endereco> To, String HTML, String Text) {}
}
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
  armazenamento:
    cors-origens: http://localhost:5173
  email:
    remetente: nao-responda@teste.celke.uno
`````

`backend/src/test/resources/templates/email/teste.html`:

`````html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="pt-BR">
<body>
<p>Olá, <span th:text="${nome}">nome</span>!</p>
<p>Código: <strong th:text="${codigo}">codigo</strong></p>
</body>
</html>
`````

`backend/src/test/java/br/com/celke/cursos/shared/erro/ErrosTesteController.java`:

`````java
package br.com.celke.cursos.shared.erro;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rotas que só existem nos testes, para provocar cada tipo de erro tratado pelo ManipuladorErros. */
@RestController
@RequestMapping("/teste/erros")
class ErrosTesteController {

    record Dados(
            @NotBlank String nome,
            @NotBlank @Email String email,
            @Min(1) int quantidade) {}

    @PostMapping("/validacao")
    Dados validacao(@Valid @RequestBody Dados dados) {
        return dados;
    }

    @GetMapping("/nao-encontrado")
    void naoEncontrado() {
        throw new NaoEncontradoException("Curso não encontrado.");
    }

    @GetMapping("/conflito")
    void conflito() {
        throw new ConflitoException("Já existe um curso com este slug.");
    }

    @GetMapping("/inesperado")
    void inesperado() {
        throw new IllegalStateException("detalhe interno que não pode vazar");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    String somenteAdmin() {
        return "ok";
    }
}
`````

### 8. Testes unitários

`backend/src/test/java/br/com/celke/cursos/shared/fila/BackoffTest.java`:

`````java
package br.com.celke.cursos.shared.fila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BackoffTest {

    // base 30 s e máximo de 1 h (3600 s)
    private final Backoff backoff = new Backoff(Duration.ofSeconds(30), Duration.ofHours(1));

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "primeira tentativa usa a base,          1,  30",
        "segunda tentativa dobra,                2,  60",
        "terceira tentativa quadruplica,         3,  120",
        "sétima tentativa ainda abaixo do máximo, 7,  1920",
        "oitava tentativa atinge o máximo,       8,  3600",
        "tentativa muito alta fica no máximo,    100, 3600",
        "tentativa no limite do int fica no máximo, 2147483647, 3600",
    })
    void atrasoPorTentativa(String caso, int tentativa, long segundosEsperados) {
        assertThat(backoff.atraso(tentativa)).isEqualTo(Duration.ofSeconds(segundosEsperados));
    }

    @Test
    @DisplayName("máximo igual à base devolve sempre a base")
    void maximoIgualBase() {
        Backoff fixo = new Backoff(Duration.ofSeconds(10), Duration.ofSeconds(10));
        assertThat(fixo.atraso(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(fixo.atraso(5)).isEqualTo(Duration.ofSeconds(10));
    }

    @ParameterizedTest(name = "tentativa {0}")
    @CsvSource({"0", "-1"})
    @DisplayName("rejeita tentativa menor que 1")
    void rejeitaTentativaInvalida(int tentativa) {
        assertThatThrownBy(() -> backoff.atraso(tentativa))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A tentativa começa em 1.");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "base zero,            0,  60",
        "base negativa,        -1, 60",
        "máximo menor que base, 60, 30",
    })
    void rejeitaConfiguracaoInvalida(String caso, long baseSegundos, long maximoSegundos) {
        assertThatThrownBy(() -> new Backoff(Duration.ofSeconds(baseSegundos), Duration.ofSeconds(maximoSegundos)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Backoff exige base positiva e máximo maior ou igual à base.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/fila/ProcessadorFilaTest.java`:

`````java
package br.com.celke.cursos.shared.fila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessadorFilaTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

    @Mock
    private FilaService fila;

    @Mock
    private ManipuladorJob manipulador;

    private ProcessadorFila processador;

    @BeforeEach
    void criar() {
        when(manipulador.tipo()).thenReturn("EMAIL");
        processador = new ProcessadorFila(fila, List.of(manipulador), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static Job job(int tentativas) {
        return new Job(7, "EMAIL", "{}", tentativas, 5);
    }

    @Test
    @DisplayName("sem job disponível devolve false e não chama o manipulador")
    void filaVazia() throws Exception {
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.empty());

        assertThat(processador.processarProximo()).isFalse();
        verify(manipulador, never()).processar(any());
    }

    @Test
    @DisplayName("job processado com sucesso é concluído")
    void sucesso() throws Exception {
        Job job = job(1);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));

        assertThat(processador.processarProximo()).isTrue();

        verify(manipulador).processar(job);
        verify(fila).concluir(job, AGORA);
        verify(fila, never()).falhar(any(), anyString(), any());
    }

    @Test
    @DisplayName("falha comum agenda nova tentativa com o erro")
    void falhaComum() throws Exception {
        Job job = job(1);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("SMTP fora do ar")).when(manipulador).processar(job);

        assertThat(processador.processarProximo()).isTrue();

        verify(fila).falhar(job, "java.lang.IllegalStateException: SMTP fora do ar", AGORA);
        verify(fila, never()).concluir(any(), any());
    }

    @Test
    @DisplayName("erro permanente vai direto para ERRO, sem nova tentativa")
    void erroPermanente() throws Exception {
        Job job = job(1);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));
        doThrow(new ErroPermanenteException("Nome de template inválido."))
                .when(manipulador)
                .processar(job);

        processador.processarProximo();

        verify(fila).falharDefinitivo(job, "Nome de template inválido.", AGORA);
        verify(fila, never()).falhar(any(), anyString(), any());
    }

    @Test
    @DisplayName("job que passou do limite de tentativas vai para ERRO sem ser processado")
    void tentativasExcedidas() throws Exception {
        Job job = job(6);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));

        assertThat(processador.processarProximo()).isTrue();

        verify(manipulador, never()).processar(any());
        verify(fila)
                .falharDefinitivo(eq(job), eq("Tentativas esgotadas (o processamento foi interrompido)."), eq(AGORA));
    }

    @Test
    @DisplayName("job na última tentativa permitida ainda é processado")
    void ultimaTentativaPermitida() throws Exception {
        Job job = job(5);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));

        processador.processarProximo();

        verify(manipulador).processar(job);
        verify(fila).concluir(job, AGORA);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/config/FusoHorarioLogsTest.java`:

`````java
package br.com.celke.cursos.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.json.JsonWriter;

class FusoHorarioLogsTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "2026-10-22T13:33:20Z,     2026-10-22T10:33:20.000-03:00",
        "2026-10-22T02:59:59.123Z, 2026-10-21T23:59:59.123-03:00",
        "2026-01-15T00:00:00Z,     2026-01-14T21:00:00.000-03:00",
    })
    @DisplayName("instante do log no horário de Brasília, com o deslocamento")
    void emBrasilia(Instant instante, String esperado) {
        assertThat(FusoHorarioLogs.emBrasilia(instante)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("só o @timestamp muda; outros campos e outros tipos ficam como estão")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void soOTimestamp() {
        record Evento(Instant em, String mensagem) {}
        JsonWriter<Evento> escritor = JsonWriter.of(membros -> {
            membros.add("@timestamp", Evento::em);
            membros.add("outro", Evento::em);
            membros.add("message", Evento::mensagem);
            new FusoHorarioLogs().customize((JsonWriter.Members) membros);
        });

        String json = escritor.writeToString(new Evento(Instant.parse("2026-10-22T13:33:20Z"), "ok"));

        assertThat(json)
                .isEqualTo("{\"@timestamp\":\"2026-10-22T10:33:20.000-03:00\","
                        + "\"outro\":\"2026-10-22T13:33:20Z\",\"message\":\"ok\"}");
        assertThat(FusoHorarioLogs.emBrasilia("texto")).isEqualTo("texto");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/web/IdRequisicaoTest.java`:

`````java
package br.com.celke.cursos.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdRequisicaoTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {"a", "abc-123", "Req_1.2", "0123456789012345678901234567890123456789012345678901234567890123"})
    @DisplayName("mantém o id recebido quando é curto e só tem caracteres seguros")
    void mantemIdValido(String recebido) {
        assertThat(IdRequisicao.normalizar(recebido)).isEqualTo(recebido);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "com espaço",
                "quebra\nde linha",
                "<script>",
                "01234567890123456789012345678901234567890123456789012345678901234"
            })
    @DisplayName("gera um UUID quando o id recebido é vazio, inseguro ou passa de 64 caracteres")
    void geraIdQuandoInvalido(String recebido) {
        String id = IdRequisicao.normalizar(recebido);
        assertThat(id).isNotEqualTo(recebido);
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    @DisplayName("cada id gerado é diferente")
    void idsGeradosDiferentes() {
        assertThat(IdRequisicao.normalizar(null)).isNotEqualTo(IdRequisicao.normalizar(null));
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/web/PaginaTest.java`:

`````java
package br.com.celke.cursos.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PaginaTest {

    @Test
    @DisplayName("copia itens, página, tamanho, total e total de páginas")
    void deUmaPagina() {
        var page = new PageImpl<>(List.of("c", "d"), PageRequest.of(1, 2), 5);

        assertThat(Pagina.de(page)).isEqualTo(new Pagina<>(List.of("c", "d"), 1, 2, 5, 3));
    }

    @Test
    @DisplayName("página vazia tem total zero e nenhuma página")
    void paginaVazia() {
        var page = new PageImpl<String>(List.of(), PageRequest.of(0, 20), 0);

        assertThat(Pagina.de(page)).isEqualTo(new Pagina<>(List.of(), 0, 20, 0, 0));
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/email/EmailServiceTest.java`:

`````java
package br.com.celke.cursos.shared.email;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.celke.cursos.shared.fila.FilaService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

    @Mock
    private FilaService fila;

    private EmailService emailService;

    @BeforeEach
    void criar() {
        emailService = new EmailService(fila, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("enfileira o e-mail com o destinatário sem espaços nas pontas")
    void enfileira() {
        emailService.enviar(" ana@teste.celke.uno ", "Assunto", "confirmacao-email", Map.of("nome", "Ana"));

        verify(fila)
                .enfileirar(
                        "EMAIL",
                        new MensagemEmail("ana@teste.celke.uno", "Assunto", "confirmacao-email", Map.of("nome", "Ana")),
                        AGORA);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "sem-arroba"})
    @DisplayName("rejeita destinatário inválido sem enfileirar")
    void destinatarioInvalido(String para) {
        assertThatThrownBy(() -> emailService.enviar(para, "Assunto", "teste", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Destinatário inválido.");
        verify(fila, never()).enfileirar(anyString(), any(), any());
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"../segredo", "email/teste", "Teste", "teste.html", "teste com espaço"})
    @DisplayName("rejeita nome de template fora do padrão sem enfileirar")
    void templateInvalido(String template) {
        assertThatThrownBy(() -> emailService.enviar("ana@teste.celke.uno", "Assunto", template, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nome de template inválido.");
        verify(fila, never()).enfileirar(anyString(), any(), any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/email/EnvioEmailManipuladorTest.java`:

`````java
package br.com.celke.cursos.shared.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.shared.fila.Job;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EnvioEmailManipuladorTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    private EnvioEmailManipulador manipulador;

    @BeforeEach
    void criar() {
        manipulador = new EnvioEmailManipulador(
                mailSender,
                templateEngine,
                jsonMapper,
                new EmailProperties("nao-responda@celke.uno", "Plataforma de Cursos"));
    }

    private Job job(MensagemEmail mensagem) {
        return new Job(1, "EMAIL", jsonMapper.writeValueAsString(mensagem), 1, 5);
    }

    @Test
    @DisplayName("monta o HTML do template com as variáveis e envia com remetente e assunto")
    void envia() throws Exception {
        when(templateEngine.process(eq("email/teste"), any(IContext.class))).thenReturn("<p>Olá, Ana</p>");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        manipulador.processar(job(new MensagemEmail("ana@teste.celke.uno", "Assunto", "teste", Map.of("nome", "Ana"))));

        ArgumentCaptor<IContext> contexto = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("email/teste"), contexto.capture());
        assertThat(contexto.getValue().getVariable("nome")).isEqualTo("Ana");

        ArgumentCaptor<MimeMessage> enviado = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(enviado.capture());
        MimeMessage mensagem = enviado.getValue();
        assertThat(mensagem.getSubject()).isEqualTo("Assunto");
        assertThat(((InternetAddress) mensagem.getFrom()[0]).getAddress()).isEqualTo("nao-responda@celke.uno");
        assertThat(((InternetAddress) mensagem.getFrom()[0]).getPersonal()).isEqualTo("Plataforma de Cursos");
        assertThat(mensagem.getAllRecipients()).extracting(Object::toString).containsExactly("ana@teste.celke.uno");
    }

    @Test
    @DisplayName("aceita mensagem sem variáveis")
    void semVariaveis() throws Exception {
        when(templateEngine.process(eq("email/teste"), any(IContext.class))).thenReturn("<p>Olá</p>");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        manipulador.processar(job(new MensagemEmail("ana@teste.celke.uno", "Assunto", "teste", null)));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("template fora do padrão é erro permanente e nada é enviado")
    void templateInvalido() {
        Job job = job(new MensagemEmail("ana@teste.celke.uno", "Assunto", "../segredo", Map.of()));

        assertThatThrownBy(() -> manipulador.processar(job))
                .isInstanceOf(ErroPermanenteException.class)
                .hasMessage("Nome de template inválido.");
        verify(templateEngine, never()).process(anyString(), any(IContext.class));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("tipo do job é EMAIL")
    void tipo() {
        assertThat(manipulador.tipo()).isEqualTo("EMAIL");
    }
}
`````

### 9. Testes de integração

`backend/src/test/java/br/com/celke/cursos/shared/erro/ManipuladorErrosIT.java`:

`````java
package br.com.celke.cursos.shared.erro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import br.com.celke.cursos.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Cobre: formato ProblemDetail de todos os erros da API (validação, negócio, MVC, inesperado,
 * 401 e 403), sem vazar detalhes internos.
 * Não cobre: regras de cada módulo (testes dos módulos).
 * Dado criado: nenhum.
 */
class ManipuladorErrosIT extends IntegrationTest {

    private static final RequestPostProcessor ALUNO = user("aluno").roles("ALUNO");

    @Test
    @DisplayName("validação devolve 400 com a lista de campos inválidos em português")
    void validacao() {
        assertThat(mvc.post()
                        .uri("/teste/erros/validacao")
                        .with(ALUNO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\" \",\"email\":\"invalido\",\"quantidade\":0}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "status": 400,
                          "detail": "Dados inválidos.",
                          "instance": "/teste/erros/validacao",
                          "erros": [
                            {"campo": "email", "mensagem": "deve ser um endereço de e-mail bem formado"},
                            {"campo": "nome", "mensagem": "não deve estar em branco"},
                            {"campo": "quantidade", "mensagem": "deve ser maior que ou igual à 1"}
                          ]
                        }
                        """);
    }

    @Test
    @DisplayName("JSON malformado devolve 400 sem detalhes do parser")
    void jsonMalformado() {
        assertThat(mvc.post()
                        .uri("/teste/erros/validacao")
                        .with(ALUNO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O corpo da requisição é inválido.");
    }

    @Test
    @DisplayName("recurso não encontrado devolve 404 com a mensagem do negócio")
    void naoEncontrado() {
        assertThat(mvc.get().uri("/teste/erros/nao-encontrado").with(ALUNO))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 404, "detail": "Curso não encontrado.", "instance": "/teste/erros/nao-encontrado"}
                        """);
    }

    @Test
    @DisplayName("conflito devolve 409 com a mensagem do negócio")
    void conflito() {
        assertThat(mvc.get().uri("/teste/erros/conflito").with(ALUNO))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Já existe um curso com este slug.");
    }

    @Test
    @DisplayName("erro inesperado devolve 500 sem a mensagem interna, só o id da requisição")
    void inesperado() throws Exception {
        var resposta = mvc.get()
                .uri("/teste/erros/inesperado")
                .with(ALUNO)
                .header("X-Request-Id", "id-do-teste")
                .exchange();
        assertThat(resposta).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resposta)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Erro interno. Se o problema continuar, informe o código id-do-teste.");
        assertThat(resposta.getResponse().getContentAsString()).doesNotContain("detalhe interno", "IllegalState");
    }

    @Test
    @DisplayName("rota inexistente devolve 404 em ProblemDetail")
    void rotaInexistente() {
        assertThat(mvc.get().uri("/teste/nao-existe").with(ALUNO))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Recurso não encontrado.");
    }

    @Test
    @DisplayName("método não aceito devolve 405 em ProblemDetail")
    void metodoNaoAceito() {
        assertThat(mvc.delete().uri("/teste/erros/conflito").with(ALUNO))
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O método DELETE não é aceito nesta rota.");
    }

    @Test
    @DisplayName("sem autenticação devolve 401 em ProblemDetail")
    void semAutenticacao() {
        assertThat(mvc.get().uri("/teste/erros/conflito").header("X-Request-Id", "id-401"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "status": 401,
                          "detail": "Autenticação necessária.",
                          "instance": "/teste/erros/conflito",
                          "requestId": "id-401"
                        }
                        """);
    }

    @Test
    @DisplayName("sem o papel exigido devolve 403 em ProblemDetail")
    void semPapel() {
        assertThat(mvc.get().uri("/teste/erros/admin").with(ALUNO))
                .hasStatus(HttpStatus.FORBIDDEN)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 403, "detail": "Acesso negado.", "instance": "/teste/erros/admin"}
                        """);
    }

    @Test
    @DisplayName("com o papel exigido a rota responde normalmente")
    void comPapel() {
        assertThat(mvc.get().uri("/teste/erros/admin").with(user("admin").roles("ADMIN")))
                .hasStatusOk()
                .hasBodyTextEqualTo("ok");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/web/IdRequisicaoIT.java`:

`````java
package br.com.celke.cursos.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Cobre: o cabeçalho X-Request-Id em toda resposta, inclusive as barradas pela segurança.
 * Não cobre: regras de formato do id (IdRequisicaoTest).
 * Dado criado: nenhum.
 */
class IdRequisicaoIT extends IntegrationTest {

    @Test
    @DisplayName("devolve o id recebido quando ele é válido")
    void devolveIdRecebido() {
        assertThat(mvc.get().uri("/actuator/health").header("X-Request-Id", "abc-123"))
                .hasStatusOk()
                .hasHeader("X-Request-Id", "abc-123");
    }

    @Test
    @DisplayName("gera um id quando a requisição não traz um")
    void geraId() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk().headers().containsHeader("X-Request-Id");
    }

    @Test
    @DisplayName("troca um id com caracteres inseguros por um gerado")
    void trocaIdInseguro() {
        var resposta = mvc.get()
                .uri("/actuator/health")
                .header("X-Request-Id", "id com espaço\r\nX-Falso: 1")
                .exchange();
        assertThat(resposta.getResponse().getHeader("X-Request-Id"))
                .isNotBlank()
                .doesNotContain(" ", "\n", "X-Falso");
    }

    @Test
    @DisplayName("resposta 401 da segurança também traz o id")
    void idNoBloqueioDaSeguranca() {
        assertThat(mvc.get().uri("/api/qualquer").header("X-Request-Id", "id-bloqueado"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasHeader("X-Request-Id", "id-bloqueado");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/armazenamento/StorageServiceIT.java`:

`````java
package br.com.celke.cursos.shared.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.armazenamento.StorageService.ParteEnviada;
import br.com.celke.cursos.support.Containers;
import br.com.celke.cursos.support.IntegrationTest;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.LifecycleRule;

/**
 * Cobre: upload e download por URL pré-assinada, upload em partes, consulta e exclusão no Garage
 * real, e a configuração do bucket aplicada pela API ao subir (CORS e expiração de uploads).
 * Não cobre: regras de quem pode enviar ou baixar (módulos de vídeo e arquivos).
 * Dado criado: arquivos em uploads/it-<stamp>/.
 */
class StorageServiceIT extends IntegrationTest {

    private static final Duration VALIDADE = Duration.ofMinutes(5);
    private static final int CINCO_MB = 5 * 1024 * 1024;

    private final HttpClient http = HttpClient.newHttpClient();
    private final String pasta = "uploads/" + stamp() + "/";

    @Autowired
    private StorageService storage;

    @Autowired
    private S3Client s3;

    @Autowired
    private ArmazenamentoProperties props;

    @Test
    @DisplayName("envia por PUT pré-assinado, consulta, baixa por GET pré-assinado e exclui")
    void cicloDeUmArquivo() throws Exception {
        String chave = pasta + "texto.txt";
        byte[] conteudo = "conteúdo do arquivo".getBytes(StandardCharsets.UTF_8);

        URL urlUpload = storage.urlUpload(chave, "text/plain", VALIDADE);
        assertThat(urlUpload.toString()).startsWith(props.endpointPublico());
        assertThat(put(urlUpload, conteudo, "text/plain").statusCode()).isEqualTo(200);

        assertThat(storage.info(chave)).contains(new InfoArquivo(conteudo.length, "text/plain"));

        HttpResponse<byte[]> download = http.send(
                HttpRequest.newBuilder(storage.urlDownload(chave, VALIDADE).toURI())
                        .build(),
                BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(conteudo);

        storage.excluir(chave);
        assertThat(storage.info(chave)).isEmpty();
    }

    @Test
    @DisplayName("URL de upload não aceita conteúdo com tipo diferente do assinado")
    void uploadComTipoDiferente() throws Exception {
        URL url = storage.urlUpload(pasta + "imagem.png", "image/png", VALIDADE);
        assertThat(put(url, new byte[] {1, 2, 3}, "text/html").statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("upload em partes junta as partes na ordem certa")
    void uploadEmPartes() throws Exception {
        String chave = pasta + "video.mp4";
        byte[] parte1 = new byte[CINCO_MB];
        Arrays.fill(parte1, (byte) 1);
        byte[] parte2 = "fim".getBytes(StandardCharsets.UTF_8);

        String uploadId = storage.iniciarMultipart(chave, "video/mp4");
        // Envia fora de ordem: a conclusão ordena pelo número da parte
        String etag2 = put(storage.urlParte(chave, uploadId, 2, VALIDADE), parte2, null)
                .headers()
                .firstValue("ETag")
                .orElseThrow();
        String etag1 = put(storage.urlParte(chave, uploadId, 1, VALIDADE), parte1, null)
                .headers()
                .firstValue("ETag")
                .orElseThrow();
        storage.concluirMultipart(chave, uploadId, List.of(new ParteEnviada(2, etag2), new ParteEnviada(1, etag1)));

        assertThat(storage.info(chave)).hasValueSatisfying(info -> {
            assertThat(info.tamanho()).isEqualTo(CINCO_MB + parte2.length);
            assertThat(info.contentType()).isEqualTo("video/mp4");
        });
    }

    @Test
    @DisplayName("upload em partes abortado não deixa arquivo")
    void uploadEmPartesAbortado() throws Exception {
        String chave = pasta + "abortado.mp4";
        String uploadId = storage.iniciarMultipart(chave, "video/mp4");
        put(storage.urlParte(chave, uploadId, 1, VALIDADE), "parte".getBytes(StandardCharsets.UTF_8), null);

        storage.abortarMultipart(chave, uploadId);

        assertThat(storage.info(chave)).isEmpty();
    }

    @Test
    @DisplayName("consulta de arquivo inexistente devolve vazio")
    void arquivoInexistente() {
        assertThat(storage.info(pasta + "nao-existe.txt")).isEmpty();
    }

    @Test
    @DisplayName("bucket fica com CORS para o front, expondo o ETag")
    void corsDoBucket() {
        List<CORSRule> regras =
                s3.getBucketCors(r -> r.bucket(Containers.S3_BUCKET)).corsRules();
        assertThat(regras).singleElement().satisfies(regra -> {
            assertThat(regra.allowedOrigins()).containsExactly("http://localhost:5173");
            assertThat(regra.allowedMethods()).containsExactlyInAnyOrder("GET", "PUT", "HEAD");
            assertThat(regra.exposeHeaders()).contains("ETag");
        });
    }

    @Test
    @DisplayName("preflight do navegador é aceito só para a origem configurada")
    void preflightCors() throws Exception {
        URI url = storage.urlUpload(pasta + "cors.txt", "text/plain", VALIDADE).toURI();

        HttpResponse<Void> permitida = preflight(url, "http://localhost:5173");
        assertThat(permitida.statusCode()).isEqualTo(200);
        assertThat(permitida.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:5173");
        assertThat(permitida.headers().firstValue("Access-Control-Allow-Methods"))
                .hasValueSatisfying(metodos -> assertThat(metodos).contains("PUT"));

        // Origem não configurada: o preflight é recusado e o navegador não envia o PUT. O Garage
        // põe "Access-Control-Allow-Origin: *" só para o navegador conseguir ler a resposta de erro.
        HttpResponse<Void> negada = preflight(url, "https://site-estranho.com");
        assertThat(negada.statusCode()).isEqualTo(403);
        assertThat(negada.headers().firstValue("Access-Control-Allow-Methods")).isEmpty();
    }

    @Test
    @DisplayName("bucket expira o que fica em uploads/ e os uploads em partes incompletos")
    void expiracaoDeUploads() {
        List<LifecycleRule> regras = s3.getBucketLifecycleConfiguration(r -> r.bucket(Containers.S3_BUCKET))
                .rules();
        assertThat(regras).singleElement().satisfies(regra -> {
            assertThat(regra.filter().prefix()).isEqualTo("uploads/");
            assertThat(regra.expiration().days()).isEqualTo(2);
            assertThat(regra.abortIncompleteMultipartUpload().daysAfterInitiation())
                    .isEqualTo(1);
        });
    }

    private HttpResponse<Void> put(URL url, byte[] corpo, String contentType) throws Exception {
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(url.toURI()).PUT(BodyPublishers.ofByteArray(corpo));
        if (contentType != null) {
            requisicao.header("Content-Type", contentType);
        }
        return http.send(requisicao.build(), BodyHandlers.discarding());
    }

    private HttpResponse<Void> preflight(URI url, String origem) throws Exception {
        return http.send(
                HttpRequest.newBuilder(url)
                        .method("OPTIONS", BodyPublishers.noBody())
                        .header("Origin", origem)
                        .header("Access-Control-Request-Method", "PUT")
                        .build(),
                BodyHandlers.discarding());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/fila/FilaServiceIT.java`:

`````java
package br.com.celke.cursos.shared.fila;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Cobre: enfileirar, reservar, concluir e falhar no PostgreSQL real — backoff, tentativas
 * esgotadas, lease vencido e reserva concorrente sem duplicidade.
 * Não cobre: cálculo do backoff (BackoffTest) e despacho para manipuladores (ProcessadorFilaTest).
 * Dado criado: jobs com tipo it-<stamp> (cada teste usa um tipo próprio).
 */
class FilaServiceIT extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-10T12:00:00Z");

    @Autowired
    private FilaService fila;

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("reserva o job, entrega o payload e conclui")
    void cicloCompleto() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of("para", "ana@teste.celke.uno"), T0);

        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();
        assertThat(job.id()).isEqualTo(id);
        assertThat(job.tentativas()).isEqualTo(1);
        assertThat(job.maxTentativas()).isEqualTo(5);
        assertThat(job.payload()).isEqualTo("{\"para\": \"ana@teste.celke.uno\"}");
        assertThat(status(id)).isEqualTo(StatusJob.PROCESSANDO);

        fila.concluir(job, T0);

        assertThat(status(id)).isEqualTo(StatusJob.CONCLUIDO);
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofDays(1)))).isEmpty();
    }

    @Test
    @DisplayName("só reserva jobs dos tipos pedidos")
    void filtraPorTipo() {
        String tipo = stamp();
        fila.enfileirar(tipo, Map.of(), T0);

        assertThat(fila.reservar(Set.of(stamp()), T0)).isEmpty();
        assertThat(fila.reservar(Set.of(), T0)).isEmpty();
        assertThat(fila.reservar(Set.of(tipo), T0)).isPresent();
    }

    @Test
    @DisplayName("não reserva job agendado para o futuro")
    void respeitaHorario() {
        String tipo = stamp();
        fila.enfileirar(tipo, Map.of(), T0);

        assertThat(fila.reservar(Set.of(tipo), T0.minusSeconds(1))).isEmpty();
    }

    @Test
    @DisplayName("falha volta o job para a fila depois do backoff, com o erro registrado")
    void falhaComBackoff() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();

        fila.falhar(job, "SMTP fora do ar", T0);

        assertThat(status(id)).isEqualTo(StatusJob.PENDENTE);
        assertThat(ultimoErro(id)).isEqualTo("SMTP fora do ar");
        // backoff-base de 30 s: um segundo antes não reserva, no instante exato reserva
        assertThat(fila.reservar(Set.of(tipo), T0.plusSeconds(29))).isEmpty();
        assertThat(fila.reservar(Set.of(tipo), T0.plusSeconds(30)))
                .hasValueSatisfying(j -> assertThat(j.tentativas()).isEqualTo(2));
    }

    @Test
    @DisplayName("na última tentativa a falha leva o job para ERRO")
    void tentativasEsgotadas() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Instant agora = T0;
        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            Job job = fila.reservar(Set.of(tipo), agora).orElseThrow();
            assertThat(job.tentativas()).isEqualTo(tentativa);
            fila.falhar(job, "falha " + tentativa, agora);
            agora = agora.plus(Duration.ofDays(1));
        }

        assertThat(status(id)).isEqualTo(StatusJob.ERRO);
        assertThat(ultimoErro(id)).isEqualTo("falha 5");
        assertThat(fila.reservar(Set.of(tipo), agora)).isEmpty();
    }

    @Test
    @DisplayName("falha definitiva leva o job direto para ERRO")
    void falhaDefinitiva() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();

        fila.falharDefinitivo(job, "template inexistente", T0);

        assertThat(status(id)).isEqualTo(StatusJob.ERRO);
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofDays(1)))).isEmpty();
    }

    @Test
    @DisplayName("job com lease vencido volta a ser reservado (o worker anterior caiu)")
    void leaseVencido() {
        String tipo = stamp();
        fila.enfileirar(tipo, Map.of(), T0);
        fila.reservar(Set.of(tipo), T0).orElseThrow();

        // lease de 10 min: ainda com o worker anterior no instante exato, livre um segundo depois
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofMinutes(10)))).isEmpty();
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofMinutes(10)).plusSeconds(1)))
                .hasValueSatisfying(j -> assertThat(j.tentativas()).isEqualTo(2));
    }

    @Test
    @DisplayName("consumidores concorrentes nunca reservam o mesmo job")
    void reservaConcorrente() throws Exception {
        String tipo = stamp();
        int total = 40;
        for (int i = 0; i < total; i++) {
            fila.enfileirar(tipo, Map.of("i", i), T0);
        }
        List<Long> reservados = Collections.synchronizedList(new ArrayList<>());
        Callable<Void> consumidor = () -> {
            while (true) {
                var job = fila.reservar(Set.of(tipo), T0);
                if (job.isEmpty()) {
                    return null;
                }
                reservados.add(job.get().id());
            }
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            for (var futuro : executor.invokeAll(List.of(consumidor, consumidor, consumidor, consumidor))) {
                futuro.get();
            }
        }

        assertThat(reservados).hasSize(total).doesNotHaveDuplicates();
    }

    private StatusJob status(long id) {
        return StatusJob.valueOf(jdbc.sql("SELECT status FROM jobs WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single());
    }

    private String ultimoErro(long id) {
        return jdbc.sql("SELECT ultimo_erro FROM jobs WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/email/EmailIT.java`:

`````java
package br.com.celke.cursos.shared.email;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.shared.fila.ProcessadorFila;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.Mailpit;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Cobre: e-mail enfileirado, processado pela fila, montado pelo template e entregue ao SMTP real
 * (Mailpit), com as variáveis escapadas no HTML.
 * Não cobre: validação de destinatário e template (EmailServiceTest, EnvioEmailManipuladorTest).
 * Dado criado: e-mail para it-<stamp>@teste.celke.uno.
 */
class EmailIT extends IntegrationTest {

    @Autowired
    private EmailService emailService;

    @Autowired
    private ProcessadorFila processador;

    @Autowired
    private Mailpit mailpit;

    @Test
    @DisplayName("e-mail sai pela fila com remetente, assunto e template preenchido")
    void enviaPelaFila() {
        String destinatario = stamp() + "@teste.celke.uno";

        emailService.enviar(
                destinatario, "Bem-vinda à plataforma", "teste", Map.of("nome", "Ana", "codigo", "<b>123</b>"));
        while (processador.processarProximo()) {
            // processa todos os e-mails pendentes
        }

        Mailpit.EmailRecebido email = mailpit.aguardarEmailPara(destinatario);
        assertThat(email.Subject()).isEqualTo("Bem-vinda à plataforma");
        assertThat(email.From().Address()).isEqualTo("nao-responda@teste.celke.uno");
        assertThat(email.From().Name()).isEqualTo("Plataforma de Cursos");
        assertThat(email.To()).extracting(Mailpit.Endereco::Address).containsExactly(destinatario);
        assertThat(email.HTML())
                .contains("Olá, <span>Ana</span>!")
                // variável com HTML chega escapada: o template não injeta marcação
                .contains("&lt;b&gt;123&lt;/b&gt;")
                .doesNotContain("<b>123</b>");
    }
}
`````

## Antes de executar

Etapas 1.1 e 1.2 concluídas e `infra/.env` criado pelo `gerar-env.sh`. Acrescente ao `infra/.env`
as variáveis novas do `.env.example`:

```bash
printf '\nS3_CORS_ORIGENS=http://localhost:5173\nEMAIL_REMETENTE=nao-responda@celke.uno\n' >> infra/.env
```

## Verificação

Dentro de `backend/`:

```bash
./mvnw spotless:apply
./mvnw verify
```

- `BUILD SUCCESS` com 48 testes unitários e 42 de integração, e a cobertura mínima atendida.
- `./mvnw verify -Dskip.unit=true` roda só os 42 testes de integração.

Na raiz do repositório:

```bash
docker compose -f infra/compose.yml up -d --build --wait
docker compose -f infra/compose.yml logs api | grep -E "CORS|expiram"
```

- Os logs da API saem em JSON e mostram o CORS aplicado para `http://localhost:5173` e a expiração de
  `uploads/` em 2 dias.
- `curl -i localhost:8080/api/x` responde 401 com `Content-Type: application/problem+json`, corpo com
  `"detail":"Autenticação necessária."` e o cabeçalho `X-Request-Id`.
- O `worker` continua `Up`.

## Depois de executar

```bash
git add backend/ infra/
git commit -m "Padrões compartilhados: erros, logs, armazenamento, fila e e-mail"
```
