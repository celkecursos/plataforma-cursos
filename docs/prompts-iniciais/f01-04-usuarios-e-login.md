# Usuários e login com JWT e refresh em cookie

**Fase:** 1 — etapa 1.4
**Branch:** `feature/base-projeto`

## Prompt

Vamos criar os módulos `usuario` e `auth` do backend, seguindo o `backend/CLAUDE.md` e o padrão das
skills de teste. Não faça commit.

O que esta etapa entrega:

- **Usuários e papéis** (ALUNO, ADMIN): e-mail único, gravado sempre em minúsculas (o banco também
  exige), usuário ativo por padrão.
- **Senhas em Argon2** (`DelegatingPasswordEncoder` com `{argon2}`, BouncyCastle). Hashes BCrypt sem
  prefixo, como os `$2y$` do Laravel, ainda são aceitos e são regravados em Argon2 no próximo login.
  Política de senha por tamanho: mínimo 12 para admin, máximo 128.
- **Comando `criar-admin <email> "<nome>"`**: sobe a aplicação sem servidor HTTP (perfil `comando`),
  pede a senha duas vezes no terminal sem eco e termina. O primeiro admin nunca é criado por migration.
- **Access token JWT** emitido pelo Spring Security (Nimbus): HS256 com a chave de `JWT_SEGREDO` (Base64,
  pelo menos 32 bytes, validada ao subir), `sub` = id do usuário, claim `papeis`, validade de 10 minutos.
  O Resource Server aceita só HS256, confere emissor e validade sem tolerância e transforma `papeis` em
  `ROLE_*`. Token inválido recebe o 401 em ProblemDetail.
- **Refresh token opaco** (32 bytes aleatórios em Base64 URL) com só o hash SHA-256 no banco. Cada login
  abre uma família; cada uso marca o token como usado e emite outro na mesma família (leitura com
  `SELECT ... FOR UPDATE`). Token usado que reaparece revoga a família inteira — e essa revogação não
  pode ser desfeita por rollback quando a sessão é recusada.
- **Cookie do refresh**: `refresh_token`, `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/auth`,
  `Max-Age` igual à validade do refresh (14 dias) e **sem `Domain`** (outras aplicações usam o mesmo
  domínio).
- **Rotas**: `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout` (públicas, com o
  método declarado), `POST /api/auth/sair-de-todos` e `GET /api/auth/me` (privadas). O login responde a
  mesma mensagem para e-mail inexistente, senha errada e usuário inativo, e confere a senha contra um
  hash de uma senha aleatória quando o e-mail não existe, para levar o mesmo tempo.
- **Testes**: seed do profile `test` (admin e aluno, só leitura), `login()`, `bearer()` e
  `criarUsuario()` no `IntegrationTest` para os fluxos que alteram a sessão.

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
  auth:
    # Segredo do JWT em Base64, com pelo menos 32 bytes (openssl rand -base64 48)
    jwt-segredo: ${JWT_SEGREDO:}
    emissor: plataforma-cursos
    validade-acesso: PT10M
    validade-refresh: P14D
    cookie-seguro: true
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

`backend/src/main/java/br/com/celke/cursos/PlataformaCursosApplication.java`:

`````java
package br.com.celke.cursos;

import br.com.celke.cursos.usuario.api.ComandoCriarAdmin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlataformaCursosApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PlataformaCursosApplication.class);
        if (ComandoCriarAdmin.foiPedido(args)) {
            // Comando de linha: sem servidor HTTP; termina ao fim do comando
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setAdditionalProfiles(ComandoCriarAdmin.PERFIL);
            System.exit(SpringApplication.exit(app.run(args)));
        }
        app.run(args);
    }
}
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Segurança da API: tudo exige autenticação, exceto as rotas públicas declaradas aqui com o
 * método HTTP. A API é stateless: o access token (JWT) vem no cabeçalho {@code Authorization}, sem
 * sessão, formulário ou HTTP Basic. Só existe na aplicação web: o worker não atende HTTP.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfig {

    /** Claim do JWT com os papéis do usuário (ALUNO, ADMIN), convertidos em ROLE_ALUNO, ROLE_ADMIN. */
    public static final String CLAIM_PAPEIS = "papeis";

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
                .oauth2ResourceServer(recurso -> recurso.jwt(jwt -> jwt.jwtAuthenticationConverter(conversorPapeis()))
                        .authenticationEntryPoint(respostas)
                        .accessDeniedHandler(respostas))
                .authorizeHttpRequests(rotas -> rotas
                        // Health check e probes (Docker e Caddy)
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // Documentação: só responde quando DOCS_ENABLED=true
                        .requestMatchers(
                                HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // Autenticação: login e renovação/saída pelo cookie de refresh
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                        .permitAll()
                        .anyRequest()
                        .authenticated());
        return http.build();
    }

    private static JwtAuthenticationConverter conversorPapeis() {
        JwtGrantedAuthoritiesConverter papeis = new JwtGrantedAuthoritiesConverter();
        papeis.setAuthoritiesClaimName(CLAIM_PAPEIS);
        papeis.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(papeis);
        return conversor;
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/support/IntegrationTest.java`:

`````java
package br.com.celke.cursos.support;

import static org.assertj.core.api.Assertions.assertThat;

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

    protected static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    /**
     * Cria um usuário próprio do teste (e-mail com {@code stamp}), para fluxos que alteram a sessão
     * ou os dados do usuário — os usuários do seed são só leitura.
     */
    protected UsuarioTeste criarUsuario(Papel... papeis) {
        String email = stamp() + "@teste.celke.uno";
        var usuario = usuarioService.criar("Usuário " + email, email, SENHA_TESTE, Set.of(papeis));
        return new UsuarioTeste(usuario.getId(), email, SENHA_TESTE);
    }

    protected record UsuarioTeste(Long id, String email, String senha) {}
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
  auth:
    # Chave só dos testes (48 bytes em Base64)
    jwt-segredo: dGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUt
  armazenamento:
    cors-origens: http://localhost:5173
  email:
    remetente: nao-responda@teste.celke.uno
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

`infra/scripts/gerar-env.sh`:

`````bash
#!/usr/bin/env bash
# Cria infra/.env a partir do infra/.env.example, preenchendo os segredos com valores aleatórios.
# Não sobrescreve um infra/.env existente.
set -euo pipefail

INFRA="$(cd "$(dirname "$0")/.." && pwd)"
DESTINO="$INFRA/.env"

if [[ -e "$DESTINO" ]]; then
  echo "infra/.env já existe; nada foi alterado." >&2
  exit 1
fi

hex() { openssl rand -hex "$1"; }

sed -E \
  -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(hex 24)|" \
  -e "s|^JWT_SEGREDO=.*|JWT_SEGREDO=$(openssl rand -base64 48)|" \
  -e "s|^GARAGE_RPC_SECRET=.*|GARAGE_RPC_SECRET=$(hex 32)|" \
  -e "s|^GARAGE_ADMIN_TOKEN=.*|GARAGE_ADMIN_TOKEN=$(hex 32)|" \
  -e "s|^S3_ACCESS_KEY=.*|S3_ACCESS_KEY=GK$(hex 12)|" \
  -e "s|^S3_SECRET_KEY=.*|S3_SECRET_KEY=$(hex 32)|" \
  "$INFRA/.env.example" > "$DESTINO"

chmod 600 "$DESTINO"
echo "infra/.env criado com segredos aleatórios."
`````

### 2. Migrations

`backend/src/main/resources/db/migration/V2__criar_usuarios.sql`:

`````sql
CREATE TABLE usuarios (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome          VARCHAR(120) NOT NULL,
    -- Gravado sempre em minúsculas, sem espaços nas pontas
    email         VARCHAR(254) NOT NULL,
    senha_hash    VARCHAR(255) NOT NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_email_minusculo CHECK (email = LOWER(email))
);

CREATE TABLE usuario_papeis (
    usuario_id BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    papel      VARCHAR(20) NOT NULL,
    PRIMARY KEY (usuario_id, papel),
    CONSTRAINT ck_usuario_papeis_papel CHECK (papel IN ('ALUNO', 'ADMIN'))
);
`````

`backend/src/main/resources/db/migration/V3__criar_refresh_tokens.sql`:

`````sql
-- Refresh tokens: só o hash SHA-256 é gravado. Cada login abre uma família; cada uso gera um novo
-- token na mesma família e marca o anterior como usado. Um token usado que reaparece revoga a família.
CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    familia     UUID        NOT NULL,
    hash        VARCHAR(64) NOT NULL,
    expira_em   TIMESTAMPTZ NOT NULL,
    usado_em    TIMESTAMPTZ,
    revogado_em TIMESTAMPTZ,
    criado_em   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (hash)
);

CREATE INDEX ix_refresh_tokens_familia ON refresh_tokens (familia);
CREATE INDEX ix_refresh_tokens_usuario ON refresh_tokens (usuario_id);
`````

### 3. Módulo usuario

`backend/src/main/java/br/com/celke/cursos/usuario/domain/Papel.java`:

`````java
package br.com.celke.cursos.usuario.domain;

public enum Papel {
    ALUNO,
    ADMIN
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/domain/Usuario.java`:

`````java
package br.com.celke.cursos.usuario.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private boolean ativo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_papeis", joinColumns = @JoinColumn(name = "usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "papel", length = 20)
    private Set<Papel> papeis = EnumSet.noneOf(Papel.class);

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Usuario() {}

    public Usuario(String nome, String email, String senhaHash, Set<Papel> papeis, Instant agora) {
        this.nome = nome.strip();
        this.email = normalizarEmail(email);
        this.senhaHash = senhaHash;
        this.ativo = true;
        this.papeis = EnumSet.copyOf(papeis);
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** E-mail sempre em minúsculas e sem espaços nas pontas (o banco também exige minúsculas). */
    public static String normalizarEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public void trocarHashSenha(String novoHash, Instant agora) {
        this.senhaHash = novoHash;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Set<Papel> getPapeis() {
        return Set.copyOf(papeis);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/domain/PoliticaSenha.java`:

`````java
package br.com.celke.cursos.usuario.domain;

/**
 * Regras de senha: tamanho mínimo e máximo (o máximo evita hash de textos enormes). Não exige
 * mistura de caracteres: comprimento protege mais e incomoda menos.
 */
public final class PoliticaSenha {

    public static final int MINIMO_ADMIN = 12;
    public static final int MAXIMO = 128;

    private PoliticaSenha() {}

    /** Devolve a mensagem do problema, ou {@code null} se a senha atende à política. */
    public static String validar(String senha, int minimo) {
        if (senha == null || senha.isBlank()) {
            return "A senha é obrigatória.";
        }
        if (senha.length() < minimo) {
            return "A senha deve ter pelo menos " + minimo + " caracteres.";
        }
        if (senha.length() > MAXIMO) {
            return "A senha deve ter no máximo " + MAXIMO + " caracteres.";
        }
        return null;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/infra/UsuarioRepository.java`:

`````java
package br.com.celke.cursos.usuario.infra;

import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/application/SenhaConfig.java`:

`````java
package br.com.celke.cursos.usuario.application;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hash de senha: novas senhas em Argon2 ({@code {argon2}...}). Hashes BCrypt sem prefixo, como os
 * {@code $2y$} do Laravel, ainda são aceitos e regravados em Argon2 no próximo login.
 */
@Configuration
public class SenhaConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder(
                "argon2",
                Map.of(
                        "argon2",
                        Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                        "bcrypt",
                        new BCryptPasswordEncoder()));
        encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return encoder;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/application/UsuarioService.java`:

`````java
package br.com.celke.cursos.usuario.application;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import br.com.celke.cursos.usuario.infra.UsuarioRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operações de usuário usadas pelos outros módulos (eles nunca acessam o repositório direto). */
@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Cria o usuário com a senha em hash. A senha já deve ter passado pela {@code PoliticaSenha}. */
    @Transactional
    public Usuario criar(String nome, String email, String senha, Set<Papel> papeis) {
        String emailNormalizado = Usuario.normalizarEmail(email);
        if (repository.existsByEmail(emailNormalizado)) {
            throw new ConflitoException("Já existe um usuário com este e-mail.");
        }
        return repository.save(
                new Usuario(nome, emailNormalizado, passwordEncoder.encode(senha), papeis, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorEmail(String email) {
        return repository.findByEmail(Usuario.normalizarEmail(email));
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorId(Long id) {
        return repository.findById(id);
    }

    /** Regrava o hash quando ele usa um algoritmo antigo (ex.: BCrypt vindo do sistema legado). */
    @Transactional
    public void atualizarHashSeNecessario(Usuario usuario, String senha) {
        if (passwordEncoder.upgradeEncoding(usuario.getSenhaHash())) {
            usuario.trocarHashSenha(passwordEncoder.encode(senha), clock.instant());
            repository.save(usuario);
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/application/CriarAdminService.java`:

`````java
package br.com.celke.cursos.usuario.application;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Cria um administrador. Usado pelo comando {@code criar-admin}, nunca por migration. */
@Service
public class CriarAdminService {

    private final UsuarioService usuarioService;

    public CriarAdminService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    public Usuario criar(String nome, String email, String senha) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome é obrigatório.");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
        String problemaSenha = PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ADMIN);
        if (problemaSenha != null) {
            throw new IllegalArgumentException(problemaSenha);
        }
        return usuarioService.criar(nome, email, senha, Set.of(Papel.ADMIN));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/api/ComandoCriarAdmin.java`:

`````java
package br.com.celke.cursos.usuario.api;

import br.com.celke.cursos.usuario.application.CriarAdminService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.io.Console;
import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Comando {@code criar-admin <email> <nome>}: pede a senha duas vezes no terminal, sem eco.
 *
 * <pre>docker compose run --rm api criar-admin admin@celke.uno "Nome do Admin"</pre>
 */
@Component
@Profile(ComandoCriarAdmin.PERFIL)
public class ComandoCriarAdmin implements ApplicationRunner {

    public static final String NOME = "criar-admin";
    public static final String PERFIL = "comando";

    private final CriarAdminService criarAdminService;

    public ComandoCriarAdmin(CriarAdminService criarAdminService) {
        this.criarAdminService = criarAdminService;
    }

    /** O comando é o primeiro argumento da linha de comando. */
    public static boolean foiPedido(String[] args) {
        return args.length > 0 && NOME.equals(args[0]);
    }

    @Override
    public void run(ApplicationArguments args) {
        var argumentos = args.getNonOptionArgs();
        if (argumentos.size() != 3) {
            throw new IllegalArgumentException("Uso: criar-admin <email> \"<nome>\"");
        }
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("O comando precisa de um terminal (docker compose run com TTY).");
        }
        char[] senha = console.readPassword("Senha do administrador: ");
        char[] confirmacao = console.readPassword("Repita a senha: ");
        try {
            if (!Arrays.equals(senha, confirmacao)) {
                throw new IllegalArgumentException("As senhas não conferem.");
            }
            Usuario admin = criarAdminService.criar(argumentos.get(2), argumentos.get(1), new String(senha));
            console.printf("Administrador %s criado (id %d).%n", admin.getEmail(), admin.getId());
        } finally {
            Arrays.fill(senha, '\0');
            Arrays.fill(confirmacao, '\0');
        }
    }
}
`````

### 4. Módulo auth

`backend/src/main/java/br/com/celke/cursos/auth/application/AuthProperties.java`:

`````java
package br.com.celke.cursos.auth.application;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração da autenticação.
 *
 * @param jwtSegredo chave HMAC do JWT em Base64, com pelo menos 32 bytes
 * @param validadeAcesso validade do access token
 * @param validadeRefresh validade do refresh token (renovada a cada uso)
 * @param cookieSeguro marca o cookie de refresh como {@code Secure}
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @NotBlank String jwtSegredo,
        @NotBlank String emissor,
        @NotNull Duration validadeAcesso,
        @NotNull Duration validadeRefresh,
        boolean cookieSeguro) {

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

`backend/src/main/java/br/com/celke/cursos/auth/application/JwtConfig.java`:

`````java
package br.com.celke.cursos.auth.application;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Assinatura e validação do access token: HMAC-SHA256 com a chave de {@code app.auth.jwt-segredo}.
 * O decoder aceita só HS256 (token com {@code alg} trocado ou {@code none} é recusado), confere o
 * emissor e a validade sem tolerância.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfig {

    @Bean
    JwtEncoder jwtEncoder(AuthProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave(props)));
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties props, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator validade = new JwtTimestampValidator(Duration.ZERO);
        validade.setClock(clock);
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(List.of(validade, new JwtIssuerValidator(props.emissor()))));
        return decoder;
    }

    private static SecretKey chave(AuthProperties props) {
        return new SecretKeySpec(props.chaveJwt(), "HmacSHA256");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/TokenAcessoService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.security.SecurityConfig;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Emite o access token (JWT): {@code sub} = id do usuário e {@code papeis} = papéis dele. */
@Service
public class TokenAcessoService {

    private final JwtEncoder encoder;
    private final AuthProperties props;
    private final Clock clock;

    public TokenAcessoService(JwtEncoder encoder, AuthProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public TokenAcesso emitir(Usuario usuario) {
        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(props.validadeAcesso());
        List<String> papeis =
                usuario.getPapeis().stream().map(Enum::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.emissor())
                .subject(usuario.getId().toString())
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .claim(SecurityConfig.CLAIM_PAPEIS, papeis)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenAcesso(token, expiraEm);
    }

    public record TokenAcesso(String valor, Instant expiraEm) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/RefreshToken.java`:

`````java
package br.com.celke.cursos.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Refresh token gravado só pelo hash. Um token pertence a uma família (uma sessão de login). */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private UUID familia;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected RefreshToken() {}

    public RefreshToken(Long usuarioId, UUID familia, String hash, Instant expiraEm, Instant agora) {
        this.usuarioId = usuarioId;
        this.familia = familia;
        this.hash = hash;
        this.expiraEm = expiraEm;
        this.criadoEm = agora;
    }

    public boolean foiUsado() {
        return usadoEm != null;
    }

    public boolean estaRevogado() {
        return revogadoEm != null;
    }

    /** Expira no instante exato de {@code expiraEm}. */
    public boolean estaExpirado(Instant agora) {
        return !agora.isBefore(expiraEm);
    }

    public void marcarUsado(Instant agora) {
        this.usadoEm = agora;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public UUID getFamilia() {
        return familia;
    }

    public String getHash() {
        return hash;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/infra/RefreshTokenRepository.java`:

`````java
package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Trava a linha: dois refresh simultâneos com o mesmo token não passam juntos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByHash(String hash);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revogadoEm = :agora WHERE t.familia = :familia AND t.revogadoEm IS NULL")
    int revogarFamilia(UUID familia, Instant agora);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revogadoEm = :agora WHERE t.usuarioId = :usuarioId AND t.revogadoEm IS NULL")
    int revogarTodasDoUsuario(Long usuarioId, Instant agora);
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/RefreshTokenService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.RefreshToken;
import br.com.celke.cursos.auth.infra.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens opacos com rotação: cada uso marca o token como usado e emite outro na mesma
 * família. Um token já usado que reaparece indica roubo; a família inteira é revogada.
 */
@Service
public class RefreshTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final AuthProperties props;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /** Abre uma família nova (login). Devolve o token em texto, que só existe no cookie. */
    @Transactional
    public RefreshEmitido emitir(Long usuarioId) {
        return emitirNaFamilia(usuarioId, UUID.randomUUID(), clock.instant());
    }

    /**
     * Troca o token por um novo da mesma família. Vazio quando o token é desconhecido, expirado,
     * revogado ou reutilizado (neste caso a família é revogada).
     */
    @Transactional
    public Optional<RefreshEmitido> rotacionar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant agora = clock.instant();
        Optional<RefreshToken> encontrado = repository.findByHash(hash(token));
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken atual = encontrado.get();
        if (atual.estaRevogado() || atual.estaExpirado(agora)) {
            return Optional.empty();
        }
        if (atual.foiUsado()) {
            LOG.warn(
                    "Refresh token reutilizado: família {} do usuário {} revogada",
                    atual.getFamilia(),
                    atual.getUsuarioId());
            repository.revogarFamilia(atual.getFamilia(), agora);
            return Optional.empty();
        }
        atual.marcarUsado(agora);
        return Optional.of(emitirNaFamilia(atual.getUsuarioId(), atual.getFamilia(), agora));
    }

    /** Logout: revoga a família do token (se ele existir). */
    @Transactional
    public void revogarFamiliaDoToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        repository.findByHash(hash(token)).ifPresent(t -> repository.revogarFamilia(t.getFamilia(), clock.instant()));
    }

    /** "Sair de todos os dispositivos": revoga todas as famílias do usuário. */
    @Transactional
    public void revogarTodas(Long usuarioId) {
        repository.revogarTodasDoUsuario(usuarioId, clock.instant());
    }

    private RefreshEmitido emitirNaFamilia(Long usuarioId, UUID familia, Instant agora) {
        byte[] aleatorio = new byte[32];
        ALEATORIO.nextBytes(aleatorio);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        Instant expiraEm = agora.plus(props.validadeRefresh());
        repository.save(new RefreshToken(usuarioId, familia, hash(token), expiraEm, agora));
        return new RefreshEmitido(token, usuarioId, expiraEm);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record RefreshEmitido(String token, Long usuarioId, Instant expiraEm) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/CredenciaisInvalidasException.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Mesma mensagem para e-mail inexistente, senha errada e usuário inativo: não revela o motivo. */
public class CredenciaisInvalidasException extends RegraNegocioException {

    public CredenciaisInvalidasException() {
        super(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/SessaoInvalidaException.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Refresh token ausente, expirado, revogado ou reutilizado: é preciso entrar de novo. */
public class SessaoInvalidaException extends RegraNegocioException {

    public SessaoInvalidaException() {
        super(HttpStatus.UNAUTHORIZED, "Sessão expirada. Entre novamente.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/AuthService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login, renovação e saída. */
@Service
public class AuthService {

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final TokenAcessoService tokenAcessoService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Hash de uma senha aleatória: conferido quando o e-mail não existe, para o login levar o mesmo
     * tempo nos dois casos e não revelar quais e-mails estão cadastrados.
     */
    private final String hashFicticio;

    public AuthService(
            UsuarioService usuarioService,
            PasswordEncoder passwordEncoder,
            TokenAcessoService tokenAcessoService,
            RefreshTokenService refreshTokenService) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.tokenAcessoService = tokenAcessoService;
        this.refreshTokenService = refreshTokenService;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public Sessao entrar(String email, String senha) {
        Optional<Usuario> encontrado = usuarioService.buscarPorEmail(email);
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio);
        boolean senhaConfere = passwordEncoder.matches(senha, hash);
        if (encontrado.isEmpty() || !senhaConfere || !encontrado.get().isAtivo()) {
            throw new CredenciaisInvalidasException();
        }
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

`backend/src/main/java/br/com/celke/cursos/auth/api/LoginRequest.java`:

`````java
package br.com.celke.cursos.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 128) String senha) {}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/UsuarioResponse.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.List;

/** Dados públicos do usuário: nunca inclui senha, hash ou tokens. */
public record UsuarioResponse(Long id, String nome, String email, List<Papel> papeis) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPapeis().stream().sorted().toList());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/SessaoResponse.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.AuthService.Sessao;
import java.time.Instant;

/**
 * Resposta do login e do refresh. O access token vai no corpo e o front o guarda só em memória;
 * o refresh token vai apenas no cookie {@code HttpOnly}.
 */
public record SessaoResponse(String accessToken, String tipo, Instant expiraEm, UsuarioResponse usuario) {

    public static SessaoResponse de(Sessao sessao) {
        return new SessaoResponse(
                sessao.acesso().valor(), "Bearer", sessao.acesso().expiraEm(), UsuarioResponse.de(sessao.usuario()));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/CookieRefresh.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.AuthProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie do refresh token: {@code HttpOnly} (JavaScript não lê), {@code SameSite=Strict}, enviado
 * só para {@code /api/auth} e <strong>sem</strong> {@code Domain}, para ficar preso ao host da API e
 * não ir para as outras aplicações do mesmo domínio.
 */
@Component
public class CookieRefresh {

    public static final String NOME = "refresh_token";
    static final String CAMINHO = "/api/auth";

    private final AuthProperties props;

    public CookieRefresh(AuthProperties props) {
        this.props = props;
    }

    /** Cookie de um refresh token recém-emitido: vale o mesmo que o token. */
    public ResponseCookie criar(String token) {
        return base(token, props.validadeRefresh());
    }

    public ResponseCookie limpar() {
        return base("", Duration.ZERO);
    }

    private ResponseCookie base(String valor, Duration maxAge) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(props.cookieSeguro())
                .sameSite("Strict")
                .path(CAMINHO)
                .maxAge(maxAge)
                .build();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/AuthController.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.AuthService;
import br.com.celke.cursos.auth.application.AuthService.Sessao;
import br.com.celke.cursos.auth.application.SessaoInvalidaException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public AuthController(AuthService authService, UsuarioService usuarioService, CookieRefresh cookieRefresh) {
        this.authService = authService;
        this.usuarioService = usuarioService;
        this.cookieRefresh = cookieRefresh;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Entrar com e-mail e senha",
            description = "Devolve o access token e grava o refresh token em cookie.")
    public ResponseEntity<SessaoResponse> login(@Valid @RequestBody LoginRequest request) {
        return comCookie(authService.entrar(request.email(), request.senha()));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Renovar a sessão",
            description = "Troca o refresh token do cookie por um novo e emite outro access token.")
    public ResponseEntity<SessaoResponse> refresh(
            @CookieValue(name = CookieRefresh.NOME, required = false) String refreshToken) {
        return comCookie(authService.renovar(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sair", description = "Revoga a sessão do cookie e apaga o cookie.")
    public ResponseEntity<Void> logout(@CookieValue(name = CookieRefresh.NOME, required = false) String refreshToken) {
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

### 5. Infraestrutura de testes

`backend/src/test/java/br/com/celke/cursos/support/UsuariosSeed.java`:

`````java
package br.com.celke.cursos.support;

/**
 * Usuários criados pelo seed do profile {@code test}. São só leitura: nunca troque a senha, faça
 * logout ou revogue as sessões deles — isso quebraria as outras classes de teste.
 */
public final class UsuariosSeed {

    public static final String ADMIN_EMAIL = "admin.seed@teste.celke.uno";
    public static final String ALUNO_EMAIL = "aluno.seed@teste.celke.uno";
    public static final String SENHA = "senha-do-seed-de-teste";

    private UsuariosSeed() {}
}
`````

`backend/src/test/java/br/com/celke/cursos/support/SeedTeste.java`:

`````java
package br.com.celke.cursos.support;

import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Seed do profile {@code test}: um admin e um aluno, com as credenciais de {@link UsuariosSeed}. */
@Component
@Profile("test")
class SeedTeste implements ApplicationRunner {

    private final UsuarioService usuarioService;

    SeedTeste(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public void run(ApplicationArguments args) {
        criarSeNaoExiste("Admin do Seed", UsuariosSeed.ADMIN_EMAIL, Papel.ADMIN);
        criarSeNaoExiste("Aluno do Seed", UsuariosSeed.ALUNO_EMAIL, Papel.ALUNO);
    }

    private void criarSeNaoExiste(String nome, String email, Papel papel) {
        if (usuarioService.buscarPorEmail(email).isEmpty()) {
            usuarioService.criar(nome, email, UsuariosSeed.SENHA, Set.of(papel));
        }
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/support/fixtures/UsuarioFixture.java`:

`````java
package br.com.celke.cursos.support.fixtures;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Instant;
import java.util.Set;
import org.springframework.test.util.ReflectionTestUtils;

/** Fábrica de usuários para testes unitários (sem banco: o id é preenchido aqui). */
public final class UsuarioFixture {

    public static final Instant CRIADO_EM = Instant.parse("2026-01-01T00:00:00Z");

    private UsuarioFixture() {}

    public static Usuario usuario(long id, String email, String senhaHash, Papel... papeis) {
        Usuario usuario = new Usuario("Usuário " + id, email, senhaHash, Set.of(papeis), CRIADO_EM);
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }

    public static Usuario inativo(Usuario usuario) {
        ReflectionTestUtils.setField(usuario, "ativo", false);
        return usuario;
    }
}
`````

### 6. Testes unitários

`backend/src/test/java/br/com/celke/cursos/usuario/domain/PoliticaSenhaTest.java`:

`````java
package br.com.celke.cursos.usuario.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PoliticaSenhaTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "um caractere abaixo do mínimo, 11,  'A senha deve ter pelo menos 12 caracteres.'",
        "exatamente o mínimo,           12,  ",
        "exatamente o máximo,           128, ",
        "um caractere acima do máximo,  129, 'A senha deve ter no máximo 128 caracteres.'",
    })
    void validaTamanho(String caso, int tamanho, String esperado) {
        assertThat(PoliticaSenha.validar("a".repeat(tamanho), PoliticaSenha.MINIMO_ADMIN))
                .isEqualTo(esperado);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"             "})
    void senhaObrigatoria(String senha) {
        assertThat(PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ADMIN)).isEqualTo("A senha é obrigatória.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/usuario/domain/UsuarioTest.java`:

`````java
package br.com.celke.cursos.usuario.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UsuarioTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

    @ParameterizedTest(name = "[{0}]")
    @CsvSource({
        "ana@celke.uno,          ana@celke.uno",
        "Ana@Celke.UNO,          ana@celke.uno",
        "'  ana@celke.uno  ',    ana@celke.uno",
    })
    @DisplayName("normaliza o e-mail para minúsculas e sem espaços nas pontas")
    void normalizaEmail(String recebido, String esperado) {
        assertThat(Usuario.normalizarEmail(recebido)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("novo usuário nasce ativo, com nome sem espaços nas pontas e e-mail normalizado")
    void novoUsuario() {
        Usuario usuario = new Usuario("  Ana  ", " Ana@Celke.uno ", "{argon2}hash", Set.of(Papel.ALUNO), AGORA);

        assertThat(usuario.getNome()).isEqualTo("Ana");
        assertThat(usuario.getEmail()).isEqualTo("ana@celke.uno");
        assertThat(usuario.isAtivo()).isTrue();
        assertThat(usuario.getPapeis()).containsExactly(Papel.ALUNO);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/usuario/application/UsuarioServiceTest.java`:

`````java
package br.com.celke.cursos.usuario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import br.com.celke.cursos.usuario.infra.UsuarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

    @Mock
    private UsuarioRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioService service;

    @BeforeEach
    void criar() {
        service = new UsuarioService(repository, passwordEncoder, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("cria o usuário com o e-mail normalizado e a senha em hash")
    void cria() {
        when(repository.existsByEmail("ana@celke.uno")).thenReturn(false);
        when(passwordEncoder.encode("senha-da-ana-123")).thenReturn("{argon2}hash");
        when(repository.save(any(Usuario.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Usuario criado = service.criar("Ana", " Ana@Celke.uno ", "senha-da-ana-123", Set.of(Papel.ALUNO));

        assertThat(criado.getEmail()).isEqualTo("ana@celke.uno");
        assertThat(criado.getSenhaHash()).isEqualTo("{argon2}hash");
        assertThat(criado.getPapeis()).containsExactly(Papel.ALUNO);
    }

    @Test
    @DisplayName("rejeita e-mail já cadastrado sem gravar nem calcular hash")
    void emailDuplicado() {
        when(repository.existsByEmail("ana@celke.uno")).thenReturn(true);

        assertThatThrownBy(() -> service.criar("Ana", "ANA@celke.uno", "senha-da-ana-123", Set.of(Papel.ALUNO)))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Já existe um usuário com este e-mail.");
        verify(repository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("busca por e-mail usa o e-mail normalizado")
    void buscaPorEmail() {
        service.buscarPorEmail(" Ana@Celke.uno ");

        verify(repository).findByEmail("ana@celke.uno");
    }

    @Test
    @DisplayName("regrava o hash quando ele usa algoritmo antigo")
    void regravaHashAntigo() {
        Usuario usuario = UsuarioFixture.usuario(1, "ana@celke.uno", "$2y$10$antigo", Papel.ALUNO);
        when(passwordEncoder.upgradeEncoding("$2y$10$antigo")).thenReturn(true);
        when(passwordEncoder.encode("senha-da-ana-123")).thenReturn("{argon2}novo");

        service.atualizarHashSeNecessario(usuario, "senha-da-ana-123");

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getSenhaHash()).isEqualTo("{argon2}novo");
    }

    @Test
    @DisplayName("não regrava o hash que já está no algoritmo atual")
    void mantemHashAtual() {
        Usuario usuario = UsuarioFixture.usuario(1, "ana@celke.uno", "{argon2}atual", Papel.ALUNO);
        when(passwordEncoder.upgradeEncoding("{argon2}atual")).thenReturn(false);

        service.atualizarHashSeNecessario(usuario, "senha-da-ana-123");

        verify(repository, never()).save(any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/usuario/application/CriarAdminServiceTest.java`:

`````java
package br.com.celke.cursos.usuario.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.celke.cursos.usuario.domain.Papel;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CriarAdminServiceTest {

    @Mock
    private UsuarioService usuarioService;

    private CriarAdminService service;

    @BeforeEach
    void criar() {
        service = new CriarAdminService(usuarioService);
    }

    @Test
    @DisplayName("cria o usuário só com o papel ADMIN")
    void criaAdmin() {
        service.criar("Admin", "admin@celke.uno", "senha-longa-do-admin");

        verify(usuarioService).criar("Admin", "admin@celke.uno", "senha-longa-do-admin", Set.of(Papel.ADMIN));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "nome em branco,      '  ',  admin@celke.uno, senha-longa-do-admin, O nome é obrigatório.",
        "e-mail sem arroba,   Admin, admin.celke.uno, senha-longa-do-admin, E-mail inválido.",
        "senha curta,         Admin, admin@celke.uno, curta-11chr,          A senha deve ter pelo menos 12 caracteres.",
    })
    void rejeitaDadosInvalidos(String caso, String nome, String email, String senha, String mensagem) {
        assertThatThrownBy(() -> service.criar(nome, email, senha))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(mensagem);
        verify(usuarioService, never()).criar(any(), any(), any(), any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/usuario/api/ComandoCriarAdminTest.java`:

`````java
package br.com.celke.cursos.usuario.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.celke.cursos.usuario.application.CriarAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class ComandoCriarAdminTest {

    @Mock
    private CriarAdminService criarAdminService;

    @Test
    @DisplayName("reconhece o comando só como primeiro argumento")
    void reconheceComando() {
        assertThat(ComandoCriarAdmin.foiPedido(new String[] {"criar-admin", "a@b.c", "Nome"}))
                .isTrue();
        assertThat(ComandoCriarAdmin.foiPedido(new String[] {})).isFalse();
        assertThat(ComandoCriarAdmin.foiPedido(new String[] {"--debug", "criar-admin"}))
                .isFalse();
    }

    @Test
    @DisplayName("sem e-mail e nome mostra o uso e não cria nada")
    void argumentosFaltando() {
        var comando = new ComandoCriarAdmin(criarAdminService);

        assertThatThrownBy(() -> comando.run(new DefaultApplicationArguments("criar-admin", "admin@celke.uno")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Uso: criar-admin <email> \"<nome>\"");
        verify(criarAdminService, never()).criar(any(), any(), any());
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
        var props =
                new AuthProperties("segredo", "plataforma-cursos", Duration.ofMinutes(10), Duration.ofDays(14), true);
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

`backend/src/test/java/br/com/celke/cursos/auth/application/AuthServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
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

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenAcessoService tokenAcessoService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService service;

    @BeforeEach
    void criar() {
        // O construtor calcula o hash fictício usado quando o e-mail não existe
        when(passwordEncoder.encode(anyString())).thenReturn(HASH_FICTICIO);
        service = new AuthService(usuarioService, passwordEncoder, tokenAcessoService, refreshTokenService);
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "{argon2}hash-da-ana", Papel.ALUNO);
    }

    @Test
    @DisplayName("login correto emite access token e refresh token, e regrava hash antigo se preciso")
    void loginCorreto() {
        Usuario ana = ana();
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);
        when(refreshTokenService.emitir(7L)).thenReturn(new RefreshEmitido("refresh", 7L, EXPIRA));
        when(tokenAcessoService.emitir(ana)).thenReturn(new TokenAcesso("jwt", EXPIRA));

        var sessao = service.entrar("ana@celke.uno", "senha-certa");

        assertThat(sessao.acesso().valor()).isEqualTo("jwt");
        assertThat(sessao.refresh().token()).isEqualTo("refresh");
        verify(usuarioService).atualizarHashSeNecessario(ana, "senha-certa");
    }

    @Test
    @DisplayName("senha errada é recusada sem emitir tokens")
    void senhaErrada() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana()));
        when(passwordEncoder.matches("senha-errada", "{argon2}hash-da-ana")).thenReturn(false);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-errada"))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(refreshTokenService, never()).emitir(anyLong());
        verify(usuarioService, never()).atualizarHashSeNecessario(any(), any());
    }

    @Test
    @DisplayName("e-mail inexistente confere a senha contra o hash fictício e recebe a mesma mensagem")
    void emailInexistente() {
        when(usuarioService.buscarPorEmail("ninguem@celke.uno")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrar("ninguem@celke.uno", "qualquer"))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(passwordEncoder).matches("qualquer", HASH_FICTICIO);
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("usuário inativo é recusado mesmo com a senha certa")
    void usuarioInativo() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-certa"))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verify(refreshTokenService, never()).emitir(anyLong());
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
            true);

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

`backend/src/test/java/br/com/celke/cursos/auth/application/AuthPropertiesTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Base64;
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
        return new AuthProperties(segredo, "plataforma-cursos", Duration.ofMinutes(10), Duration.ofDays(14), true);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/api/LoginRequestTest.java`:

`````java
package br.com.celke.cursos.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LoginRequestTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(LoginRequest request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("payload válido não tem erros")
    void payloadValido() {
        assertThat(camposInvalidos(new LoginRequest("ana@celke.uno", "senha"))).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "e-mail vazio,            '',            senha, email",
        "e-mail sem formato,      ana.celke.uno, senha, email",
        "senha vazia,             ana@celke.uno, '',    senha",
    })
    void camposObrigatorios(String caso, String email, String senha, String campo) {
        assertThat(camposInvalidos(new LoginRequest(email, senha))).containsExactly(campo);
    }

    @Test
    @DisplayName("senha acima de 128 caracteres é recusada antes de calcular o hash")
    void senhaMuitoLonga() {
        assertThat(camposInvalidos(new LoginRequest("ana@celke.uno", "a".repeat(129))))
                .containsExactly("senha");
        assertThat(camposInvalidos(new LoginRequest("ana@celke.uno", "a".repeat(128))))
                .isEmpty();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/api/CookieRefreshTest.java`:

`````java
package br.com.celke.cursos.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.auth.application.AuthProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CookieRefreshTest {

    private static CookieRefresh cookie(boolean seguro) {
        return new CookieRefresh(new AuthProperties(
                "segredo", "plataforma-cursos", Duration.ofMinutes(10), Duration.ofDays(14), seguro));
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

### 7. Testes de integração

`backend/src/test/java/br/com/celke/cursos/auth/AuthLoginIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.application.CriarAdminService;
import br.com.celke.cursos.usuario.domain.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: login (corpo, cookie de refresh, mensagens iguais para e-mail inexistente e senha errada),
 * validação, /api/auth/me, admin criado pelo serviço do comando e hash legado regravado em Argon2.
 * Não cobre: validade e assinatura do JWT (AuthTokenIT) e renovação (AuthRefreshIT).
 * Dado criado: usuários it-<stamp>@teste.celke.uno.
 */
class AuthLoginIT extends IntegrationTest {

    @Autowired
    private CriarAdminService criarAdminService;

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("login devolve access token, dados públicos do usuário e o cookie de refresh")
    void loginComSucesso() throws Exception {
        MvcTestResult resposta = entrar(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);

        assertThat(resposta).bodyJson().isLenientlyEqualTo("""
                        {
                          "tipo": "Bearer",
                          "usuario": {"email": "aluno.seed@teste.celke.uno", "nome": "Aluno do Seed", "papeis": ["ALUNO"]}
                        }
                        """);
        assertThat(resposta)
                .bodyJson()
                .extractingPath("$.accessToken")
                .asString()
                .isNotBlank();
        assertThat(resposta).bodyJson().extractingPath("$.expiraEm").asString().isNotBlank();
        String corpo = resposta.getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("senha", "hash", "refresh");

        String cookie = resposta.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie)
                .startsWith("refresh_token=")
                .contains("Path=/api/auth", "HttpOnly", "Secure", "SameSite=Strict", "Max-Age=1209600")
                .doesNotContainIgnoringCase("Domain=");
    }

    @Test
    @DisplayName("e-mail é aceito com letras maiúsculas")
    void emailComMaiusculas() {
        entrar("Aluno.Seed@Teste.Celke.UNO", UsuariosSeed.SENHA);
    }

    @Test
    @DisplayName("senha errada e e-mail inexistente recebem exatamente a mesma resposta")
    void credenciaisInvalidas() throws Exception {
        MvcTestResult senhaErrada = tentarLogin(UsuariosSeed.ALUNO_EMAIL, "senha-errada");
        MvcTestResult emailInexistente = tentarLogin(stamp() + "@teste.celke.uno", "senha-errada");

        for (MvcTestResult resposta : new MvcTestResult[] {senhaErrada, emailInexistente}) {
            assertThat(resposta)
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .extractingPath("$.detail")
                    .isEqualTo("E-mail ou senha inválidos.");
            assertThat(resposta.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        }
    }

    @Test
    @DisplayName("payload inválido devolve 400 com os campos")
    void validacao() {
        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-e-email\",\"senha\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .asArray()
                .containsExactlyInAnyOrder("email", "senha");
    }

    @Test
    @DisplayName("/api/auth/me devolve o usuário do token, sem dados sensíveis")
    void me() throws Exception {
        String token = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);

        MvcTestResult resposta = mvc.get()
                .uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();

        assertThat(resposta).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"email": "admin.seed@teste.celke.uno", "papeis": ["ADMIN"]}
                        """);
        assertThat(resposta.getResponse().getContentAsString()).doesNotContain("senha", "hash");
    }

    @Test
    @DisplayName("admin criado pelo serviço do comando criar-admin entra com o papel ADMIN")
    void adminCriadoPeloComando() {
        String email = stamp() + "@teste.celke.uno";
        criarAdminService.criar("Admin do Comando", email, "senha-longa-do-admin");

        assertThat(entrar(email, "senha-longa-do-admin"))
                .bodyJson()
                .extractingPath("$.usuario.papeis")
                .asArray()
                .containsExactly("ADMIN");
    }

    @Test
    @DisplayName("hash BCrypt do sistema legado ($2y$) entra e é regravado em Argon2")
    void hashLegado() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String hashLaravel =
                new BCryptPasswordEncoder().encode("senha-do-legado").replaceFirst("^\\$2a\\$", "\\$2y\\$");
        jdbc.sql("UPDATE usuarios SET senha_hash = :hash WHERE id = :id")
                .param("hash", hashLaravel)
                .param("id", usuario.id())
                .update();

        entrar(usuario.email(), "senha-do-legado");

        assertThat(senhaHash(usuario.id())).startsWith("{argon2}");
        entrar(usuario.email(), "senha-do-legado");
    }

    @Test
    @DisplayName("senha nova é gravada em Argon2, nunca em texto")
    void senhaEmArgon2() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);

        assertThat(senhaHash(usuario.id())).startsWith("{argon2}$argon2id$").doesNotContain(usuario.senha());
    }

    private MvcTestResult tentarLogin(String email, String senha) {
        return mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .exchange();
    }

    private String senhaHash(Long id) {
        return jdbc.sql("SELECT senha_hash FROM usuarios WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/AuthTokenIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/**
 * Cobre: o que a API aceita como access token — assinatura, algoritmo, emissor, validade e papéis
 * (401 para token inválido, 403 para papel insuficiente).
 * Não cobre: emissão no login (AuthLoginIT).
 * Dado criado: nenhum.
 */
class AuthTokenIT extends IntegrationTest {

    private static final String ROTA_ADMIN = "/teste/erros/admin";

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    @DisplayName("token válido de admin acessa rota de admin")
    void tokenValidoAdmin() {
        assertThat(get(ROTA_ADMIN, login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)))
                .hasStatusOk();
    }

    @Test
    @DisplayName("token válido de aluno em rota de admin recebe 403")
    void tokenAlunoEmRotaAdmin() {
        assertThat(get(ROTA_ADMIN, login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA)))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Acesso negado.");
    }

    @Test
    @DisplayName("token adulterado (papel trocado no payload) é recusado")
    void tokenAdulterado() {
        String[] partes = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replace("\"ALUNO\"", "\"ADMIN\"");
        String adulterado = partes[0] + "." + base64Url(payload) + "." + partes[2];

        assertNaoAutenticado(get(ROTA_ADMIN, adulterado));
    }

    @Test
    @DisplayName("token expirado é recusado")
    void tokenExpirado() {
        Instant agora = Instant.now();
        String token = assinar(
                jwtEncoder,
                MacAlgorithm.HS256,
                claims(agora.minus(Duration.ofMinutes(20)), agora.minusSeconds(1), "plataforma-cursos"));

        assertNaoAutenticado(get("/api/auth/me", token));
    }

    @Test
    @DisplayName("token assinado com outra chave é recusado")
    void outraChave() {
        var outraChave = new SecretKeySpec(
                "outra-chave-com-mais-de-trinta-e-dois-bytes!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String token =
                assinar(new NimbusJwtEncoder(new ImmutableSecret<>(outraChave)), MacAlgorithm.HS256, claimsValidos());

        assertNaoAutenticado(get("/api/auth/me", token));
    }

    @Test
    @DisplayName("token com algoritmo diferente de HS256 é recusado, mesmo assinado com a chave certa")
    void outroAlgoritmo() {
        String token = assinar(jwtEncoder, MacAlgorithm.HS384, claimsValidos());

        assertNaoAutenticado(get("/api/auth/me", token));
    }

    @Test
    @DisplayName("token sem assinatura (alg none) é recusado")
    void algNone() {
        String payload = "{\"iss\":\"plataforma-cursos\",\"sub\":\"1\",\"papeis\":[\"ADMIN\"],\"exp\":%d}"
                .formatted(Instant.now().plusSeconds(600).getEpochSecond());
        String token = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + base64Url(payload) + ".";

        assertNaoAutenticado(get(ROTA_ADMIN, token));
    }

    @Test
    @DisplayName("token de outro emissor é recusado")
    void outroEmissor() {
        Instant agora = Instant.now();
        String token = assinar(jwtEncoder, MacAlgorithm.HS256, claims(agora, agora.plusSeconds(600), "outro-sistema"));

        assertNaoAutenticado(get("/api/auth/me", token));
    }

    @Test
    @DisplayName("cabeçalho Authorization malformado é recusado")
    void cabecalhoMalformado() {
        assertNaoAutenticado(mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer nao-e-um-jwt"));
    }

    private MockMvcRequestBuilder get(String rota, String token) {
        return mvc.get().uri(rota).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private void assertNaoAutenticado(MockMvcRequestBuilder requisicao) {
        assertThat(requisicao)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Autenticação necessária.");
    }

    private static JwtClaimsSet claimsValidos() {
        Instant agora = Instant.now();
        return claims(agora, agora.plusSeconds(600), "plataforma-cursos");
    }

    private static JwtClaimsSet claims(Instant emitidoEm, Instant expiraEm, String emissor) {
        return JwtClaimsSet.builder()
                .issuer(emissor)
                .subject("1")
                .issuedAt(emitidoEm)
                .expiresAt(expiraEm)
                .claim("papeis", List.of("ADMIN"))
                .build();
    }

    private static String assinar(JwtEncoder encoder, MacAlgorithm algoritmo, JwtClaimsSet claims) {
        return encoder.encode(
                        JwtEncoderParameters.from(JwsHeader.with(algoritmo).build(), claims))
                .getTokenValue();
    }

    private static String base64Url(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/AuthRefreshIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.usuario.domain.Papel;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: renovação com rotação do refresh token, detecção de reutilização (revoga a família),
 * logout e "sair de todos os dispositivos".
 * Não cobre: regras de expiração caso a caso (RefreshTokenServiceTest).
 * Dado criado: usuários it-<stamp>@teste.celke.uno (cada teste usa o seu).
 */
class AuthRefreshIT extends IntegrationTest {

    @Test
    @DisplayName("refresh troca o cookie por um novo e emite outro access token")
    void rotacao() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String cookie1 = cookieRefresh(entrar(usuario.email(), usuario.senha()));

        MvcTestResult renovado = refresh(cookie1);

        assertThat(renovado)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.usuario.email")
                .isEqualTo(usuario.email());
        String cookie2 = cookieRefresh(renovado);
        assertThat(cookie2).isNotEqualTo(cookie1);
        assertThat(mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken(renovado))))
                .hasStatusOk();
        assertThat(refresh(cookie2)).hasStatusOk();
    }

    @Test
    @DisplayName("refresh já usado que reaparece revoga a família inteira")
    void reutilizacaoRevogaFamilia() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String cookie1 = cookieRefresh(entrar(usuario.email(), usuario.senha()));
        String cookie2 = cookieRefresh(refresh(cookie1));

        // Alguém reapresenta o token antigo (roubado): recusado e a família revogada
        assertSessaoInvalida(refresh(cookie1));
        // O token novo, que era legítimo, também deixa de valer
        assertSessaoInvalida(refresh(cookie2));
    }

    @Test
    @DisplayName("reutilização revoga só a família afetada, não as outras sessões do usuário")
    void reutilizacaoNaoAfetaOutraSessao() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String sessaoA = cookieRefresh(entrar(usuario.email(), usuario.senha()));
        String sessaoB = cookieRefresh(entrar(usuario.email(), usuario.senha()));
        refresh(sessaoA);

        assertSessaoInvalida(refresh(sessaoA));

        assertThat(refresh(sessaoB)).hasStatusOk();
    }

    @Test
    @DisplayName("logout revoga a sessão e apaga o cookie")
    void logout() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String cookie = cookieRefresh(entrar(usuario.email(), usuario.senha()));

        MvcTestResult saida = mvc.post()
                .uri("/api/auth/logout")
                .cookie(new Cookie("refresh_token", cookie))
                .exchange();

        assertThat(saida).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(saida.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("refresh_token=;")
                .contains("Max-Age=0", "Path=/api/auth");
        assertSessaoInvalida(refresh(cookie));
    }

    @Test
    @DisplayName("logout sem cookie responde 204 sem erro")
    void logoutSemCookie() {
        assertThat(mvc.post().uri("/api/auth/logout")).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("sair de todos os dispositivos revoga todas as sessões do usuário")
    void sairDeTodos() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        MvcTestResult sessaoA = entrar(usuario.email(), usuario.senha());
        String cookieB = cookieRefresh(entrar(usuario.email(), usuario.senha()));

        assertThat(mvc.post()
                        .uri("/api/auth/sair-de-todos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken(sessaoA))))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertSessaoInvalida(refresh(cookieRefresh(sessaoA)));
        assertSessaoInvalida(refresh(cookieB));
    }

    @Test
    @DisplayName("sair de todos não afeta as sessões de outro usuário")
    void sairDeTodosNaoAfetaOutroUsuario() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        UsuarioTeste outro = criarUsuario(Papel.ALUNO);
        String token = login(usuario.email(), usuario.senha());
        String cookieOutro = cookieRefresh(entrar(outro.email(), outro.senha()));

        mvc.post()
                .uri("/api/auth/sair-de-todos")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();

        assertThat(refresh(cookieOutro)).hasStatusOk();
    }

    @Test
    @DisplayName("refresh sem cookie ou com cookie desconhecido é recusado")
    void cookieAusenteOuDesconhecido() {
        assertSessaoInvalida(mvc.post().uri("/api/auth/refresh").exchange());
        assertSessaoInvalida(refresh("token-que-nunca-existiu"));
    }

    private MvcTestResult refresh(String cookie) {
        return mvc.post()
                .uri("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", cookie))
                .exchange();
    }

    private static String cookieRefresh(MvcTestResult resposta) {
        Cookie cookie = resposta.getResponse().getCookie("refresh_token");
        assertThat(cookie).as("cookie refresh_token na resposta").isNotNull();
        return cookie.getValue();
    }

    private static void assertSessaoInvalida(MvcTestResult resposta) {
        assertThat(resposta)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Sessão expirada. Entre novamente.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/AuthAcessoIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Cobre: matriz de papéis das rotas de autenticação — públicas (login, refresh, logout) e privadas
 * (me, sair-de-todos) — para anônimo, ALUNO e ADMIN.
 * Não cobre: comportamento de cada rota (AuthLoginIT, AuthRefreshIT).
 * Dado criado: usuários it-<stamp>@teste.celke.uno para sair-de-todos (o seed é só leitura).
 */
class AuthAcessoIT extends IntegrationTest {

    static Stream<Arguments> rotasPublicas() {
        // Sem token não pode dar 401 da segurança: o resultado vem da própria rota
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/auth/login", HttpStatus.BAD_REQUEST),
                Arguments.of(HttpMethod.POST, "/api/auth/logout", HttpStatus.NO_CONTENT));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasPublicas")
    @DisplayName("rotas públicas respondem sem token")
    void rotasPublicasSemToken(HttpMethod metodo, String rota, HttpStatus esperado) {
        assertThat(mvc.method(metodo)
                        .uri(rota)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .hasStatus(esperado);
    }

    @Test
    @DisplayName("refresh sem token responde com a regra da sessão, não com o bloqueio da segurança")
    void refreshPublico() {
        assertThat(mvc.post().uri("/api/auth/refresh"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Sessão expirada. Entre novamente.");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasPrivadas")
    @DisplayName("rotas privadas exigem token")
    void rotasPrivadasSemToken(HttpMethod metodo, String rota) {
        assertThat(mvc.method(metodo).uri(rota))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Autenticação necessária.");
    }

    static Stream<Arguments> rotasPrivadas() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/auth/me"), Arguments.of(HttpMethod.POST, "/api/auth/sair-de-todos"));
    }

    @Test
    @DisplayName("métodos não declarados nas rotas públicas exigem token")
    void metodoNaoDeclarado() {
        assertThat(mvc.get().uri("/api/auth/login")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.delete().uri("/api/auth/refresh")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("usuariosSeed")
    @DisplayName("/api/auth/me responde para ALUNO e ADMIN")
    void mePorPapel(String email) {
        assertThat(mvc.get()
                        .uri("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(email, UsuariosSeed.SENHA))))
                .hasStatusOk();
    }

    static Stream<String> usuariosSeed() {
        return Stream.of(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.ADMIN_EMAIL);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("papeis")
    @DisplayName("sair-de-todos responde para ALUNO e ADMIN")
    void sairDeTodosPorPapel(Papel papel) {
        UsuarioTeste usuario = criarUsuario(papel);

        assertThat(mvc.post()
                        .uri("/api/auth/sair-de-todos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(usuario.email(), usuario.senha()))))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    static Stream<Papel> papeis() {
        return Stream.of(Papel.ALUNO, Papel.ADMIN);
    }
}
`````

## Antes de executar

Etapas 1.1 a 1.3 concluídas. Acrescente ao `infra/.env` a chave do JWT:

```bash
printf '\nJWT_SEGREDO=%s\n' "$(openssl rand -base64 48)" >> infra/.env
```

## Verificação

Dentro de `backend/`:

```bash
./mvnw spotless:apply
./mvnw verify
```

- `BUILD SUCCESS` com 107 testes unitários e 77 de integração, e a cobertura mínima atendida.

Na raiz do repositório:

```bash
docker compose -f infra/compose.yml up -d --build --wait
docker compose -f infra/compose.yml run --rm api criar-admin admin@celke.uno "Admin Local"
```

- O comando pede a senha duas vezes (mínimo 12 caracteres) e mostra `Administrador admin@celke.uno criado`.
- No Swagger (`http://localhost:8080/swagger-ui/index.html`), `POST /api/auth/login` com esse e-mail e
  senha devolve o `accessToken` e o cookie `refresh_token`; `GET /api/auth/me` com o token devolve o
  admin; `POST /api/auth/refresh` troca o cookie; reapresentar o cookie antigo devolve 401
  `Sessão expirada. Entre novamente.` e derruba também o novo.

## Depois de executar

```bash
git add backend/ infra/
git commit -m "Usuários e login com JWT e refresh em cookie"
```
