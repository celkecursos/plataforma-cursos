# Fórum por aula e por turma

**Fase:** 7
**Branch:** `feature/forum`

## Prompt

Vamos criar o módulo `forum` do backend: perguntas e respostas da turma (gerais ou de uma aula), com
Markdown convertido em HTML filtrado, anexos de imagem, moderação pelo ADM e limite de mensagens. Siga o
`backend/CLAUDE.md` e o padrão das skills de teste. Não faça commit.

O que esta etapa entrega:

- **Dependências** no `pom.xml`: `org.commonmark:commonmark` 0.30.0 e
  `com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer` 20260924.2.
- **Fórum** (migration `V12`): `forum_topicos` (turma obrigatória, aula opcional com `ON DELETE SET NULL`),
  `forum_respostas` (marca de instrutor) e `forum_anexos` (de um tópico **ou** de uma resposta). O
  Markdown é guardado junto com o HTML já filtrado; só o HTML sai nas respostas.
- **`MarkdownSeguro`**: commonmark com `escapeHtml(true)` e `sanitizeUrls(true)`, depois o sanitizador
  OWASP com lista fechada (formatação, blocos, tabelas, `pre`, `code`, links `http`/`https`/`mailto` com
  `rel="nofollow noopener noreferrer"`); nada de evento, estilo, imagem ou iframe.
- **Aluno** (`/api/aluno`, sempre com matrícula vigente na turma via `exigirAcessoTurma`):
  `GET` e `POST /turmas/{id}/forum` (lista paginada com fixados primeiro, filtro `aulaId`, contagem de
  respostas visíveis; pergunta numa aula exige aula ativa da mesma turma), `GET /forum/topicos/{id}`,
  `POST /forum/topicos/{id}/respostas` e `POST /forum/anexos/envio`. Tópico ou resposta oculta não
  aparece (404 no tópico).
- **Anexos**: JPEG ou PNG até 5 MB, enviados direto ao bucket em `uploads/forum/<usuario>/<uuid>`; ao
  publicar, a chave precisa ser do próprio usuário (contra IDOR), a imagem é regravada em JPEG sem
  metadados (até 1600 × 1600) em `forum/anexos/` e o envio é apagado; no máximo 3 por mensagem, com URL
  temporária na leitura.
- **Moderação** (`/api/adm`): lista e tópico com os ocultos, `PATCH` do tópico (oculto, fixado), `PATCH`
  da resposta (oculto) e resposta do instrutor (o tópico fica marcado como respondido).
- **Limite** (`app.forum`): 20 perguntas e respostas por aluno por hora, contadas no banco; acima disso,
  429 com `Retry-After` até a mais antiga sair da janela. No profile `test` o limite é 5.

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
		<commonmark.version>0.30.0</commonmark.version>
		<owasp-html-sanitizer.version>20260924.2</owasp-html-sanitizer.version>
		<rest-assured.version>6.2.0</rest-assured.version>
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
		<!-- Fórum: Markdown para HTML e filtro do HTML gerado (contra XSS) -->
		<dependency>
			<groupId>org.commonmark</groupId>
			<artifactId>commonmark</artifactId>
			<version>${commonmark.version}</version>
		</dependency>
		<dependency>
			<groupId>com.googlecode.owasp-java-html-sanitizer</groupId>
			<artifactId>owasp-java-html-sanitizer</artifactId>
			<version>${owasp-html-sanitizer.version}</version>
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
						<!-- Fontes de produção, testes e e2e -->
						<includes>
							<include>src/*/java/**/*.java</include>
						</includes>
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

	<profiles>
		<!--
			e2e: roda só as jornadas caixa-preta (src/e2e/java, *E2E) contra a stack de e2e no ar.
			./mvnw verify -Pe2e
		-->
		<profile>
			<id>e2e</id>
			<properties>
				<skip.unit>true</skip.unit>
				<jacoco.skip>true</jacoco.skip>
			</properties>
			<dependencies>
				<dependency>
					<groupId>io.rest-assured</groupId>
					<artifactId>rest-assured</artifactId>
					<version>${rest-assured.version}</version>
					<scope>test</scope>
				</dependency>
			</dependencies>
			<build>
				<plugins>
					<plugin>
						<groupId>org.codehaus.mojo</groupId>
						<artifactId>build-helper-maven-plugin</artifactId>
						<executions>
							<execution>
								<id>fontes-e2e</id>
								<phase>generate-test-sources</phase>
								<goals>
									<goal>add-test-source</goal>
								</goals>
								<configuration>
									<sources>
										<source>src/e2e/java</source>
									</sources>
								</configuration>
							</execution>
							<execution>
								<id>recursos-e2e</id>
								<phase>generate-test-resources</phase>
								<goals>
									<goal>add-test-resource</goal>
								</goals>
								<configuration>
									<resources>
										<resource>
											<directory>src/e2e/resources</directory>
										</resource>
									</resources>
								</configuration>
							</execution>
						</executions>
					</plugin>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-failsafe-plugin</artifactId>
						<configuration>
							<includes>
								<include>**/*E2E.java</include>
							</includes>
						</configuration>
					</plugin>
				</plugins>
			</build>
		</profile>
	</profiles>

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
  forum:
    # Perguntas e respostas por aluno dentro da janela (o instrutor não tem limite)
    mensagens-por-janela: 20
    janela: PT1H
    anexos-por-mensagem: 3
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
  conta:
    url-aluno: http://localhost:5173
  limites:
    # Todas as requisições da suíte saem do mesmo IP: o limite por IP fica fora do caminho
    requisicoes-por-ip-por-minuto: 100000
  armazenamento:
    cors-origens: http://localhost:5173
  email:
    remetente: nao-responda@teste.celke.uno
  forum:
    # Limite baixo para o teste do 429 (cada teste usa um aluno próprio)
    mensagens-por-janela: 5
`````

### 2. Migration

`backend/src/main/resources/db/migration/V12__criar_forum.sql`:

`````sql
-- Fórum da turma: tópicos (da turma ou de uma aula), respostas e anexos de imagem. O Markdown escrito pelo
-- usuário é guardado junto com o HTML já filtrado (contra XSS) gerado a partir dele.
CREATE TABLE forum_topicos (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turma_id               BIGINT        NOT NULL REFERENCES turmas (id) ON DELETE RESTRICT,
    -- Nulo: tópico geral da turma. Aula excluída: o tópico continua, como tópico da turma
    aula_id                BIGINT        REFERENCES aulas (id) ON DELETE SET NULL,
    autor_id               BIGINT        NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    titulo                 VARCHAR(150)  NOT NULL,
    corpo_markdown         TEXT          NOT NULL,
    corpo_html             TEXT          NOT NULL,
    oculto                 BOOLEAN       NOT NULL DEFAULT FALSE,
    fixado                 BOOLEAN       NOT NULL DEFAULT FALSE,
    respondido_instrutor   BOOLEAN       NOT NULL DEFAULT FALSE,
    criado_em              TIMESTAMPTZ   NOT NULL,
    atualizado_em          TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_forum_topicos_turma ON forum_topicos (turma_id, fixado DESC, criado_em DESC);
CREATE INDEX ix_forum_topicos_aula ON forum_topicos (aula_id);
CREATE INDEX ix_forum_topicos_autor ON forum_topicos (autor_id, criado_em);

CREATE TABLE forum_respostas (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topico_id       BIGINT       NOT NULL REFERENCES forum_topicos (id) ON DELETE CASCADE,
    autor_id        BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    corpo_markdown  TEXT         NOT NULL,
    corpo_html      TEXT         NOT NULL,
    -- Resposta do instrutor (ADM)
    instrutor       BOOLEAN      NOT NULL DEFAULT FALSE,
    oculto          BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em       TIMESTAMPTZ  NOT NULL,
    atualizado_em   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_forum_respostas_topico ON forum_respostas (topico_id, criado_em);
CREATE INDEX ix_forum_respostas_autor ON forum_respostas (autor_id, criado_em);

-- Imagem anexada a um tópico ou a uma resposta (exatamente um dos dois), já processada (JPEG sem EXIF)
CREATE TABLE forum_anexos (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topico_id    BIGINT        REFERENCES forum_topicos (id) ON DELETE CASCADE,
    resposta_id  BIGINT        REFERENCES forum_respostas (id) ON DELETE CASCADE,
    autor_id     BIGINT        NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    chave        VARCHAR(200)  NOT NULL,
    tamanho      BIGINT        NOT NULL,
    criado_em    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_forum_anexos_dono CHECK (num_nonnulls(topico_id, resposta_id) = 1),
    CONSTRAINT uq_forum_anexos_chave UNIQUE (chave)
);

CREATE INDEX ix_forum_anexos_topico ON forum_anexos (topico_id);
CREATE INDEX ix_forum_anexos_resposta ON forum_anexos (resposta_id);
`````

### 3. Domínio

`backend/src/main/java/br/com/celke/cursos/forum/domain/MarkdownSeguro.java`:

`````java
package br.com.celke.cursos.forum.domain;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * Converte o Markdown do fórum em HTML seguro, em duas camadas: o commonmark escapa todo HTML escrito
 * pelo usuário e descarta links com esquema perigoso ({@code javascript:}); o sanitizador OWASP deixa
 * passar só uma lista fechada de elementos (texto, listas, citações, código, links e tabelas), sem
 * atributos de evento nem estilo. Links ganham {@code rel="nofollow noopener noreferrer"}.
 */
public final class MarkdownSeguro {

    private static final Parser PARSER = Parser.builder().build();

    private static final HtmlRenderer RENDERER =
            HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build();

    private static final PolicyFactory POLITICA = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.TABLES)
            .and(new HtmlPolicyBuilder()
                    .allowElements("pre", "code", "hr", "br")
                    .allowElements("a")
                    .allowAttributes("href")
                    .onElements("a")
                    .allowUrlProtocols("http", "https", "mailto")
                    .requireRelsOnLinks("nofollow", "noopener", "noreferrer")
                    .toFactory());

    private MarkdownSeguro() {}

    public static String paraHtml(String markdown) {
        String html = RENDERER.render(PARSER.parse(markdown == null ? "" : markdown));
        return POLITICA.sanitize(html).strip();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/domain/Topico.java`:

`````java
package br.com.celke.cursos.forum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Pergunta no fórum da turma, geral ou de uma aula. Guarda o Markdown escrito e o HTML já filtrado;
 * só o HTML filtrado é entregue a quem lê.
 */
@Entity
@Table(name = "forum_topicos")
public class Topico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turma_id", nullable = false, updatable = false)
    private Long turmaId;

    @Column(name = "aula_id", updatable = false)
    private Long aulaId;

    @Column(name = "autor_id", nullable = false, updatable = false)
    private Long autorId;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(name = "corpo_markdown", nullable = false, columnDefinition = "TEXT")
    private String corpoMarkdown;

    @Column(name = "corpo_html", nullable = false, columnDefinition = "TEXT")
    private String corpoHtml;

    @Column(nullable = false)
    private boolean oculto;

    @Column(nullable = false)
    private boolean fixado;

    /** Já tem resposta do instrutor (destaque na lista). */
    @Column(name = "respondido_instrutor", nullable = false)
    private boolean respondidoInstrutor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Topico() {}

    public Topico(Long turmaId, Long aulaId, Long autorId, String titulo, String corpoMarkdown, Instant agora) {
        this.turmaId = turmaId;
        this.aulaId = aulaId;
        this.autorId = autorId;
        this.titulo = titulo.strip();
        this.corpoMarkdown = corpoMarkdown.strip();
        this.corpoHtml = MarkdownSeguro.paraHtml(this.corpoMarkdown);
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Moderação: só os campos preenchidos mudam. */
    public void moderar(Boolean oculto, Boolean fixado, Instant agora) {
        if (oculto != null) {
            this.oculto = oculto;
        }
        if (fixado != null) {
            this.fixado = fixado;
        }
        this.atualizadoEm = agora;
    }

    public void registrarRespostaInstrutor(Instant agora) {
        this.respondidoInstrutor = true;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public Long getTurmaId() {
        return turmaId;
    }

    public Long getAulaId() {
        return aulaId;
    }

    public Long getAutorId() {
        return autorId;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getCorpoMarkdown() {
        return corpoMarkdown;
    }

    public String getCorpoHtml() {
        return corpoHtml;
    }

    public boolean isOculto() {
        return oculto;
    }

    public boolean isFixado() {
        return fixado;
    }

    public boolean isRespondidoInstrutor() {
        return respondidoInstrutor;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/domain/Resposta.java`:

`````java
package br.com.celke.cursos.forum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Resposta a um tópico, de aluno ou do instrutor (ADM). */
@Entity
@Table(name = "forum_respostas")
public class Resposta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topico_id", nullable = false, updatable = false)
    private Long topicoId;

    @Column(name = "autor_id", nullable = false, updatable = false)
    private Long autorId;

    @Column(name = "corpo_markdown", nullable = false, columnDefinition = "TEXT")
    private String corpoMarkdown;

    @Column(name = "corpo_html", nullable = false, columnDefinition = "TEXT")
    private String corpoHtml;

    @Column(nullable = false, updatable = false)
    private boolean instrutor;

    @Column(nullable = false)
    private boolean oculto;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Resposta() {}

    public Resposta(Long topicoId, Long autorId, String corpoMarkdown, boolean instrutor, Instant agora) {
        this.topicoId = topicoId;
        this.autorId = autorId;
        this.corpoMarkdown = corpoMarkdown.strip();
        this.corpoHtml = MarkdownSeguro.paraHtml(this.corpoMarkdown);
        this.instrutor = instrutor;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void moderar(boolean oculto, Instant agora) {
        this.oculto = oculto;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public Long getTopicoId() {
        return topicoId;
    }

    public Long getAutorId() {
        return autorId;
    }

    public String getCorpoMarkdown() {
        return corpoMarkdown;
    }

    public String getCorpoHtml() {
        return corpoHtml;
    }

    public boolean isInstrutor() {
        return instrutor;
    }

    public boolean isOculto() {
        return oculto;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/domain/Anexo.java`:

`````java
package br.com.celke.cursos.forum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Imagem anexada a um tópico ou a uma resposta, já reprocessada (JPEG sem metadados). */
@Entity
@Table(name = "forum_anexos")
public class Anexo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topico_id", updatable = false)
    private Long topicoId;

    @Column(name = "resposta_id", updatable = false)
    private Long respostaId;

    @Column(name = "autor_id", nullable = false, updatable = false)
    private Long autorId;

    @Column(nullable = false, length = 200, updatable = false)
    private String chave;

    @Column(nullable = false, updatable = false)
    private long tamanho;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Anexo() {}

    private Anexo(Long topicoId, Long respostaId, Long autorId, String chave, long tamanho, Instant agora) {
        this.topicoId = topicoId;
        this.respostaId = respostaId;
        this.autorId = autorId;
        this.chave = chave;
        this.tamanho = tamanho;
        this.criadoEm = agora;
    }

    public static Anexo doTopico(Long topicoId, Long autorId, String chave, long tamanho, Instant agora) {
        return new Anexo(topicoId, null, autorId, chave, tamanho, agora);
    }

    public static Anexo daResposta(Long respostaId, Long autorId, String chave, long tamanho, Instant agora) {
        return new Anexo(null, respostaId, autorId, chave, tamanho, agora);
    }

    public Long getId() {
        return id;
    }

    public Long getTopicoId() {
        return topicoId;
    }

    public Long getRespostaId() {
        return respostaId;
    }

    public Long getAutorId() {
        return autorId;
    }

    public String getChave() {
        return chave;
    }

    public long getTamanho() {
        return tamanho;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/infra/TopicoRepository.java`:

`````java
package br.com.celke.cursos.forum.infra;

import br.com.celke.cursos.forum.domain.Topico;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TopicoRepository extends JpaRepository<Topico, Long> {

    /** Tópicos visíveis da turma (todos ou só os de uma aula): fixados primeiro, depois os mais recentes. */
    @Query("""
            select t from Topico t
             where t.turmaId = :turmaId and t.oculto = false and (:aulaId is null or t.aulaId = :aulaId)
             order by t.fixado desc, t.criadoEm desc, t.id desc
            """)
    Page<Topico> visiveis(Long turmaId, Long aulaId, Pageable pagina);

    /** Todos os tópicos da turma, ocultos inclusive (moderação). */
    @Query("""
            select t from Topico t where t.turmaId = :turmaId
             order by t.fixado desc, t.criadoEm desc, t.id desc
            """)
    Page<Topico> daTurma(Long turmaId, Pageable pagina);

    Optional<Topico> findByIdAndOcultoFalse(Long id);

    long countByAutorIdAndCriadoEmAfter(Long autorId, Instant desde);

    Optional<Topico> findFirstByAutorIdAndCriadoEmAfterOrderByCriadoEm(Long autorId, Instant desde);
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/infra/RespostaRepository.java`:

`````java
package br.com.celke.cursos.forum.infra;

import br.com.celke.cursos.forum.domain.Resposta;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RespostaRepository extends JpaRepository<Resposta, Long> {

    List<Resposta> findByTopicoIdOrderByCriadoEmAscIdAsc(Long topicoId);

    List<Resposta> findByTopicoIdAndOcultoFalseOrderByCriadoEmAscIdAsc(Long topicoId);

    /** Respostas visíveis por tópico, numa consulta só (lista de tópicos). */
    @Query("""
            select r.topicoId, count(r) from Resposta r
             where r.topicoId in :topicos and r.oculto = false
             group by r.topicoId
            """)
    List<Object[]> contarVisiveis(Collection<Long> topicos);

    long countByAutorIdAndCriadoEmAfter(Long autorId, Instant desde);

    Optional<Resposta> findFirstByAutorIdAndCriadoEmAfterOrderByCriadoEm(Long autorId, Instant desde);
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/infra/AnexoRepository.java`:

`````java
package br.com.celke.cursos.forum.infra;

import br.com.celke.cursos.forum.domain.Anexo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnexoRepository extends JpaRepository<Anexo, Long> {

    List<Anexo> findByTopicoIdOrderById(Long topicoId);

    List<Anexo> findByRespostaIdInOrderById(Collection<Long> respostaIds);
}
`````

### 4. Casos de uso

`backend/src/main/java/br/com/celke/cursos/forum/application/ForumProperties.java`:

`````java
package br.com.celke.cursos.forum.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do fórum.
 *
 * @param mensagensPorJanela perguntas e respostas que um aluno pode publicar dentro da janela
 * @param janela período do limite de mensagens
 * @param anexosPorMensagem imagens por pergunta ou resposta
 */
@Validated
@ConfigurationProperties("app.forum")
public record ForumProperties(
        @Min(1) int mensagensPorJanela,
        @NotNull Duration janela,
        @Min(0) @Max(10) int anexosPorMensagem) {}
`````

`backend/src/main/java/br/com/celke/cursos/forum/application/LimiteForum.java`:

`````java
package br.com.celke.cursos.forum.application;

import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.forum.infra.TopicoRepository;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Limite de perguntas e respostas por aluno numa janela de tempo, contado no banco (vale para todas as
 * instâncias da API). Passou do limite: 429 com o tempo até a mensagem mais antiga da janela sair dela.
 */
@Component
public class LimiteForum {

    private final TopicoRepository topicos;
    private final RespostaRepository respostas;
    private final ForumProperties props;
    private final Clock clock;

    public LimiteForum(TopicoRepository topicos, RespostaRepository respostas, ForumProperties props, Clock clock) {
        this.topicos = topicos;
        this.respostas = respostas;
        this.props = props;
        this.clock = clock;
    }

    public void exigirDentroDoLimite(Long usuarioId) {
        Instant agora = clock.instant();
        Instant desde = agora.minus(props.janela());
        long publicadas = topicos.countByAutorIdAndCriadoEmAfter(usuarioId, desde)
                + respostas.countByAutorIdAndCriadoEmAfter(usuarioId, desde);
        if (publicadas < props.mensagensPorJanela()) {
            return;
        }
        Instant maisAntiga = Stream.concat(
                        topicos.findFirstByAutorIdAndCriadoEmAfterOrderByCriadoEm(usuarioId, desde).stream()
                                .map(Topico::getCriadoEm),
                        respostas.findFirstByAutorIdAndCriadoEmAfterOrderByCriadoEm(usuarioId, desde).stream()
                                .map(Resposta::getCriadoEm))
                .min(Instant::compareTo)
                .orElse(agora);
        throw new MuitasTentativasException(Duration.between(agora, maisAntiga.plus(props.janela())));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/application/AnexoForumService.java`:

`````java
package br.com.celke.cursos.forum.application;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.imagem.ImagemInvalidaException;
import br.com.celke.cursos.shared.imagem.ProcessadorImagem;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Imagens anexadas a perguntas e respostas, no mesmo esquema da capa do curso: o navegador envia direto
 * ao bucket em {@code uploads/forum/<usuario>/<uuid>} e, ao publicar a mensagem, a API confere e regrava a
 * imagem (JPEG sem metadados, até 1600 × 1600) em {@code forum/anexos/}. Cada usuário só anexa o que ele
 * mesmo enviou: a chave traz o id dele (contra IDOR).
 */
@Service
@EnableConfigurationProperties(ForumProperties.class)
public class AnexoForumService {

    public static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;
    static final Set<String> TIPOS = Set.of("image/jpeg", "image/png");
    static final int LADO_MAXIMO = 1600;
    static final Duration VALIDADE_UPLOAD = Duration.ofMinutes(10);
    static final Duration VALIDADE_DOWNLOAD = Duration.ofMinutes(10);

    private static final Pattern UUID_FORMATO =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final StorageService storage;
    private final ProcessadorImagem processador;
    private final ForumProperties props;
    private final Clock clock;

    public AnexoForumService(
            StorageService storage, ProcessadorImagem processador, ForumProperties props, Clock clock) {
        this.storage = storage;
        this.processador = processador;
        this.props = props;
        this.clock = clock;
    }

    /** URL para o navegador enviar a imagem, com o mesmo Content-Type informado aqui. */
    public EnvioAnexo iniciarEnvio(Long usuarioId, String contentType, long tamanho) {
        if (!TIPOS.contains(contentType)) {
            throw new ImagemInvalidaException("Anexe uma imagem JPEG ou PNG.");
        }
        if (tamanho > TAMANHO_MAXIMO) {
            throw tamanhoExcedido();
        }
        String chave = prefixo(usuarioId) + UUID.randomUUID();
        URL url = storage.urlUpload(chave, contentType, VALIDADE_UPLOAD);
        return new EnvioAnexo(chave, url, contentType, clock.instant().plus(VALIDADE_UPLOAD));
    }

    /**
     * Confere e processa as imagens enviadas pelo usuário; devolve onde ficaram. Qualquer recusa desfaz
     * as imagens já gravadas desta mensagem. Os envios originais são sempre apagados.
     */
    public List<AnexoGravado> processar(Long usuarioId, List<String> chaves) {
        if (chaves == null || chaves.isEmpty()) {
            return List.of();
        }
        if (chaves.size() > props.anexosPorMensagem()) {
            throw new ImagemInvalidaException("Anexe no máximo %d imagens.".formatted(props.anexosPorMensagem()));
        }
        if (new HashSet<>(chaves).size() != chaves.size()) {
            throw new ImagemInvalidaException("A mesma imagem foi anexada duas vezes.");
        }
        String prefixo = prefixo(usuarioId);
        for (String chave : chaves) {
            if (chave == null
                    || !chave.startsWith(prefixo)
                    || !UUID_FORMATO.matcher(chave.substring(prefixo.length())).matches()) {
                throw new ImagemInvalidaException("Anexo inválido. Envie a imagem de novo.");
            }
        }
        List<AnexoGravado> gravados = new ArrayList<>();
        try {
            for (String chave : chaves) {
                gravados.add(gravar(chave));
            }
            return gravados;
        } catch (RuntimeException e) {
            gravados.forEach(g -> storage.excluir(g.chave()));
            throw e;
        }
    }

    /** Apaga imagens já gravadas (a mensagem não chegou a ser salva). */
    public void descartar(List<AnexoGravado> gravados) {
        gravados.forEach(g -> storage.excluir(g.chave()));
    }

    public URL url(String chave) {
        return storage.urlDownload(chave, VALIDADE_DOWNLOAD);
    }

    private AnexoGravado gravar(String upload) {
        var info = storage.info(upload)
                .orElseThrow(() -> new ImagemInvalidaException("A imagem não foi enviada. Envie a imagem de novo."));
        byte[] processada;
        try {
            if (info.tamanho() > TAMANHO_MAXIMO) {
                throw tamanhoExcedido();
            }
            processada = processador.normalizar(storage.baixar(upload), LADO_MAXIMO, LADO_MAXIMO);
        } finally {
            storage.excluir(upload);
        }
        String chave = "forum/anexos/%s.jpg".formatted(UUID.randomUUID());
        storage.gravar(chave, processada, "image/jpeg");
        return new AnexoGravado(chave, processada.length);
    }

    private static String prefixo(Long usuarioId) {
        return "uploads/forum/%d/".formatted(usuarioId);
    }

    private static ImagemInvalidaException tamanhoExcedido() {
        return new ImagemInvalidaException("A imagem pode ter no máximo 5 MB.");
    }

    public record EnvioAnexo(String chave, URL url, String contentType, Instant expiraEm) {}

    public record AnexoGravado(String chave, long tamanho) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/application/LeituraForum.java`:

`````java
package br.com.celke.cursos.forum.application;

import br.com.celke.cursos.forum.domain.Anexo;
import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.AnexoRepository;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Monta as visões do fórum com o nome dos autores, a contagem de respostas e os anexos (com URL
 * temporária). Quem chama decide se as mensagens ocultas entram (só na moderação).
 */
@Component
public class LeituraForum {

    private final RespostaRepository respostas;
    private final AnexoRepository anexos;
    private final UsuarioService usuarioService;
    private final AnexoForumService anexoForumService;

    public LeituraForum(
            RespostaRepository respostas,
            AnexoRepository anexos,
            UsuarioService usuarioService,
            AnexoForumService anexoForumService) {
        this.respostas = respostas;
        this.anexos = anexos;
        this.usuarioService = usuarioService;
        this.anexoForumService = anexoForumService;
    }

    public Page<TopicoResumo> resumos(Page<Topico> pagina) {
        List<Long> ids = pagina.getContent().stream().map(Topico::getId).toList();
        Map<Long, Long> contagem = ids.isEmpty()
                ? Map.of()
                : respostas.contarVisiveis(ids).stream()
                        .collect(Collectors.toMap(linha -> (Long) linha[0], linha -> (Long) linha[1]));
        Map<Long, Usuario> autores =
                autores(pagina.getContent().stream().map(Topico::getAutorId).toList());
        return pagina.map(
                t -> new TopicoResumo(t, nome(autores, t.getAutorId()), contagem.getOrDefault(t.getId(), 0L)));
    }

    public TopicoDetalhe detalhe(Topico topico, boolean incluirOcultas) {
        List<Resposta> lista = incluirOcultas
                ? respostas.findByTopicoIdOrderByCriadoEmAscIdAsc(topico.getId())
                : respostas.findByTopicoIdAndOcultoFalseOrderByCriadoEmAscIdAsc(topico.getId());
        Set<Long> autorIds = new HashSet<>();
        autorIds.add(topico.getAutorId());
        lista.forEach(r -> autorIds.add(r.getAutorId()));
        Map<Long, Usuario> autores = autores(autorIds);
        Map<Long, List<Anexo>> anexosPorResposta = lista.isEmpty()
                ? Map.of()
                : anexos
                        .findByRespostaIdInOrderById(
                                lista.stream().map(Resposta::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(Anexo::getRespostaId));
        return new TopicoDetalhe(
                topico,
                nome(autores, topico.getAutorId()),
                visoes(anexos.findByTopicoIdOrderById(topico.getId())),
                lista.stream()
                        .map(r -> new RespostaDetalhe(
                                r,
                                nome(autores, r.getAutorId()),
                                visoes(anexosPorResposta.getOrDefault(r.getId(), List.of()))))
                        .toList());
    }

    private List<AnexoVisao> visoes(List<Anexo> lista) {
        return lista.stream()
                .map(a -> new AnexoVisao(
                        a.getId(), anexoForumService.url(a.getChave()).toString()))
                .toList();
    }

    private Map<Long, Usuario> autores(Collection<Long> ids) {
        return usuarioService.buscarPorIds(ids);
    }

    private static String nome(Map<Long, Usuario> autores, Long id) {
        Usuario usuario = autores.get(id);
        return usuario == null ? null : usuario.getNome();
    }

    public record TopicoResumo(Topico topico, String autorNome, long respostas) {}

    public record TopicoDetalhe(
            Topico topico, String autorNome, List<AnexoVisao> anexos, List<RespostaDetalhe> respostas) {}

    public record RespostaDetalhe(Resposta resposta, String autorNome, List<AnexoVisao> anexos) {}

    /** Anexo com URL temporária (vale 10 minutos). */
    public record AnexoVisao(Long id, String url) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/application/ForumAlunoService.java`:

`````java
package br.com.celke.cursos.forum.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.forum.application.AnexoForumService.AnexoGravado;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoDetalhe;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoResumo;
import br.com.celke.cursos.forum.domain.Anexo;
import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.AnexoRepository;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.forum.infra.TopicoRepository;
import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fórum na área do aluno: só com matrícula vigente na turma ({@link AcessoAulaService#exigirAcessoTurma}).
 * Mensagens ocultas pela moderação não aparecem; publicar respeita o {@link LimiteForum}.
 */
@Service
public class ForumAlunoService {

    private final TopicoRepository topicos;
    private final RespostaRepository respostas;
    private final AnexoRepository anexos;
    private final AcessoAulaService acessoAulaService;
    private final ConteudoTurmaService conteudoTurmaService;
    private final AnexoForumService anexoForumService;
    private final LimiteForum limite;
    private final LeituraForum leitura;
    private final TransactionTemplate transacao;
    private final Clock clock;

    public ForumAlunoService(
            TopicoRepository topicos,
            RespostaRepository respostas,
            AnexoRepository anexos,
            AcessoAulaService acessoAulaService,
            ConteudoTurmaService conteudoTurmaService,
            AnexoForumService anexoForumService,
            LimiteForum limite,
            LeituraForum leitura,
            TransactionTemplate transacao,
            Clock clock) {
        this.topicos = topicos;
        this.respostas = respostas;
        this.anexos = anexos;
        this.acessoAulaService = acessoAulaService;
        this.conteudoTurmaService = conteudoTurmaService;
        this.anexoForumService = anexoForumService;
        this.limite = limite;
        this.leitura = leitura;
        this.transacao = transacao;
        this.clock = clock;
    }

    /** Tópicos visíveis da turma (ou só os de uma aula), fixados primeiro. */
    @Transactional(readOnly = true)
    public Page<TopicoResumo> listar(Long usuarioId, Long turmaId, Long aulaId, Pageable pagina) {
        acessoAulaService.exigirAcessoTurma(usuarioId, turmaId);
        return leitura.resumos(
                topicos.visiveis(turmaId, aulaId, PageRequest.of(pagina.getPageNumber(), pagina.getPageSize())));
    }

    /** Pergunta nova na turma ou numa aula ativa da turma. */
    public TopicoDetalhe perguntar(
            Long usuarioId, Long turmaId, Long aulaId, String titulo, String corpo, List<String> chavesAnexos) {
        acessoAulaService.exigirAcessoTurma(usuarioId, turmaId);
        if (aulaId != null
                && conteudoTurmaService
                        .aulaAtiva(aulaId)
                        .filter(aula -> aula.turmaId().equals(turmaId))
                        .isEmpty()) {
            throw new NaoEncontradoException("Aula não encontrada.");
        }
        limite.exigirDentroDoLimite(usuarioId);
        List<AnexoGravado> gravados = anexoForumService.processar(usuarioId, chavesAnexos);
        try {
            return transacao.execute(status -> {
                Instant agora = clock.instant();
                Topico topico = topicos.save(new Topico(turmaId, aulaId, usuarioId, titulo, corpo, agora));
                gravados.forEach(
                        g -> anexos.save(Anexo.doTopico(topico.getId(), usuarioId, g.chave(), g.tamanho(), agora)));
                return leitura.detalhe(topico, false);
            });
        } catch (RuntimeException e) {
            anexoForumService.descartar(gravados);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public TopicoDetalhe topico(Long usuarioId, Long topicoId) {
        Topico topico = visivel(topicoId);
        acessoAulaService.exigirAcessoTurma(usuarioId, topico.getTurmaId());
        return leitura.detalhe(topico, false);
    }

    /** Resposta de aluno a um tópico visível da turma dele. */
    public TopicoDetalhe responder(Long usuarioId, Long topicoId, String corpo, List<String> chavesAnexos) {
        Topico topico = visivel(topicoId);
        acessoAulaService.exigirAcessoTurma(usuarioId, topico.getTurmaId());
        limite.exigirDentroDoLimite(usuarioId);
        List<AnexoGravado> gravados = anexoForumService.processar(usuarioId, chavesAnexos);
        try {
            return transacao.execute(status -> {
                Instant agora = clock.instant();
                Resposta resposta = respostas.save(new Resposta(topicoId, usuarioId, corpo, false, agora));
                gravados.forEach(
                        g -> anexos.save(Anexo.daResposta(resposta.getId(), usuarioId, g.chave(), g.tamanho(), agora)));
                return leitura.detalhe(topico, false);
            });
        } catch (RuntimeException e) {
            anexoForumService.descartar(gravados);
            throw e;
        }
    }

    private Topico visivel(Long topicoId) {
        return topicos.findByIdAndOcultoFalse(topicoId).orElseThrow(ForumAlunoService::naoEncontrado);
    }

    static NaoEncontradoException naoEncontrado() {
        return new NaoEncontradoException("Tópico não encontrado.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/application/ForumAdmService.java`:

`````java
package br.com.celke.cursos.forum.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoDetalhe;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoResumo;
import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.forum.infra.TopicoRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moderação do fórum pelo ADM: vê tudo (ocultos inclusive), oculta, fixa e responde como instrutor. */
@Service
public class ForumAdmService {

    private final TopicoRepository topicos;
    private final RespostaRepository respostas;
    private final ConteudoTurmaService conteudoTurmaService;
    private final LeituraForum leitura;
    private final Clock clock;

    public ForumAdmService(
            TopicoRepository topicos,
            RespostaRepository respostas,
            ConteudoTurmaService conteudoTurmaService,
            LeituraForum leitura,
            Clock clock) {
        this.topicos = topicos;
        this.respostas = respostas;
        this.conteudoTurmaService = conteudoTurmaService;
        this.leitura = leitura;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<TopicoResumo> listar(Long turmaId, Pageable pagina) {
        if (conteudoTurmaService.turma(turmaId).isEmpty()) {
            throw new NaoEncontradoException("Turma não encontrada.");
        }
        return leitura.resumos(topicos.daTurma(turmaId, PageRequest.of(pagina.getPageNumber(), pagina.getPageSize())));
    }

    @Transactional(readOnly = true)
    public TopicoDetalhe topico(Long topicoId) {
        return leitura.detalhe(buscar(topicoId), true);
    }

    /** Oculta/mostra e fixa/solta; só os campos preenchidos mudam. */
    @Transactional
    public TopicoDetalhe moderarTopico(Long topicoId, Boolean oculto, Boolean fixado) {
        Topico topico = buscar(topicoId);
        topico.moderar(oculto, fixado, clock.instant());
        return leitura.detalhe(topico, true);
    }

    @Transactional
    public TopicoDetalhe moderarResposta(Long respostaId, boolean oculto) {
        Resposta resposta = respostas
                .findById(respostaId)
                .orElseThrow(() -> new NaoEncontradoException("Resposta não encontrada."));
        resposta.moderar(oculto, clock.instant());
        return leitura.detalhe(buscar(resposta.getTopicoId()), true);
    }

    /** Resposta do instrutor: marcada como tal e sem limite de mensagens. */
    @Transactional
    public TopicoDetalhe responder(Long adminId, Long topicoId, String corpo) {
        Topico topico = buscar(topicoId);
        Instant agora = clock.instant();
        respostas.save(new Resposta(topicoId, adminId, corpo, true, agora));
        topico.registrarRespostaInstrutor(agora);
        return leitura.detalhe(topico, true);
    }

    private Topico buscar(Long topicoId) {
        return topicos.findById(topicoId).orElseThrow(ForumAlunoService::naoEncontrado);
    }
}
`````

### 5. API

`backend/src/main/java/br/com/celke/cursos/forum/api/ForumRequests.java`:

`````java
package br.com.celke.cursos.forum.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Corpos das rotas do fórum. O corpo das mensagens é Markdown; o HTML é gerado e filtrado pela API. */
public final class ForumRequests {

    /** Tamanho máximo do Markdown de uma mensagem. */
    public static final int CORPO_MAXIMO = 10_000;

    private ForumRequests() {}

    /**
     * @param aulaId aula da turma (opcional): sem ela, o tópico é geral da turma
     * @param anexos chaves das imagens já enviadas pelo próprio usuário
     */
    public record Perguntar(
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = CORPO_MAXIMO) String corpo,
            Long aulaId,
            @Size(max = 10) List<@NotBlank @Size(max = 200) String> anexos) {}

    public record Responder(
            @NotBlank @Size(max = CORPO_MAXIMO) String corpo,
            @Size(max = 10) List<@NotBlank @Size(max = 200) String> anexos) {}

    public record ResponderInstrutor(
            @NotBlank @Size(max = CORPO_MAXIMO) String corpo) {}

    public record IniciarEnvioAnexo(
            @NotBlank String contentType, @Positive long tamanho) {}

    /** Só os campos enviados mudam. */
    public record ModerarTopico(Boolean oculto, Boolean fixado) {}

    public record ModerarResposta(@NotNull Boolean oculto) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/api/ForumResponses.java`:

`````java
package br.com.celke.cursos.forum.api;

import br.com.celke.cursos.forum.application.AnexoForumService.EnvioAnexo;
import br.com.celke.cursos.forum.application.LeituraForum.AnexoVisao;
import br.com.celke.cursos.forum.application.LeituraForum.RespostaDetalhe;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoDetalhe;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoResumo;
import br.com.celke.cursos.forum.domain.Topico;
import java.time.Instant;
import java.util.List;

/**
 * Respostas do fórum. O texto sai só como HTML filtrado ({@code corpoHtml}), nunca o Markdown cru.
 * {@code oculto} só vem preenchido na moderação (na área do aluno, mensagem oculta não aparece).
 */
public final class ForumResponses {

    private ForumResponses() {}

    public record TopicoResumoResponse(
            Long id,
            Long turmaId,
            Long aulaId,
            String titulo,
            String autorNome,
            boolean fixado,
            boolean respondidoInstrutor,
            long respostas,
            Boolean oculto,
            Instant criadoEm) {

        public static TopicoResumoResponse aluno(TopicoResumo r) {
            return de(r, null);
        }

        public static TopicoResumoResponse moderacao(TopicoResumo r) {
            return de(r, r.topico().isOculto());
        }

        private static TopicoResumoResponse de(TopicoResumo r, Boolean oculto) {
            Topico t = r.topico();
            return new TopicoResumoResponse(
                    t.getId(),
                    t.getTurmaId(),
                    t.getAulaId(),
                    t.getTitulo(),
                    r.autorNome(),
                    t.isFixado(),
                    t.isRespondidoInstrutor(),
                    r.respostas(),
                    oculto,
                    t.getCriadoEm());
        }
    }

    public record TopicoResponse(
            Long id,
            Long turmaId,
            Long aulaId,
            String titulo,
            String corpoHtml,
            String autorNome,
            boolean fixado,
            boolean respondidoInstrutor,
            Boolean oculto,
            Instant criadoEm,
            List<AnexoVisao> anexos,
            List<RespostaResponse> respostas) {

        public static TopicoResponse aluno(TopicoDetalhe d) {
            return de(d, false);
        }

        public static TopicoResponse moderacao(TopicoDetalhe d) {
            return de(d, true);
        }

        private static TopicoResponse de(TopicoDetalhe d, boolean moderacao) {
            Topico t = d.topico();
            return new TopicoResponse(
                    t.getId(),
                    t.getTurmaId(),
                    t.getAulaId(),
                    t.getTitulo(),
                    t.getCorpoHtml(),
                    d.autorNome(),
                    t.isFixado(),
                    t.isRespondidoInstrutor(),
                    moderacao ? t.isOculto() : null,
                    t.getCriadoEm(),
                    d.anexos(),
                    d.respostas().stream()
                            .map(r -> RespostaResponse.de(r, moderacao))
                            .toList());
        }
    }

    public record RespostaResponse(
            Long id,
            String corpoHtml,
            String autorNome,
            boolean instrutor,
            Boolean oculto,
            Instant criadoEm,
            List<AnexoVisao> anexos) {

        static RespostaResponse de(RespostaDetalhe d, boolean moderacao) {
            var r = d.resposta();
            return new RespostaResponse(
                    r.getId(),
                    r.getCorpoHtml(),
                    d.autorNome(),
                    r.isInstrutor(),
                    moderacao ? r.isOculto() : null,
                    r.getCriadoEm(),
                    d.anexos());
        }
    }

    /** Onde enviar a imagem: PUT na {@code url} com o {@code Content-Type} informado; depois, a chave vai em {@code anexos}. */
    public record EnvioAnexoResponse(String chave, String url, String contentType, Instant expiraEm) {

        public static EnvioAnexoResponse de(EnvioAnexo envio) {
            return new EnvioAnexoResponse(envio.chave(), envio.url().toString(), envio.contentType(), envio.expiraEm());
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/api/ForumAlunoController.java`:

`````java
package br.com.celke.cursos.forum.api;

import br.com.celke.cursos.forum.api.ForumResponses.EnvioAnexoResponse;
import br.com.celke.cursos.forum.api.ForumResponses.TopicoResponse;
import br.com.celke.cursos.forum.api.ForumResponses.TopicoResumoResponse;
import br.com.celke.cursos.forum.application.AnexoForumService;
import br.com.celke.cursos.forum.application.ForumAlunoService;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fórum na área do aluno, sempre no escopo do usuário do token e com matrícula vigente na turma. */
@RestController
@RequestMapping("/api/aluno")
@Tag(name = "Área do aluno — Fórum")
public class ForumAlunoController {

    private final ForumAlunoService forumAlunoService;
    private final AnexoForumService anexoForumService;

    public ForumAlunoController(ForumAlunoService forumAlunoService, AnexoForumService anexoForumService) {
        this.forumAlunoService = forumAlunoService;
        this.anexoForumService = anexoForumService;
    }

    @GetMapping("/turmas/{turmaId}/forum")
    @Operation(summary = "Tópicos da turma", description = "Fixados primeiro; filtro opcional pela aula.")
    public Pagina<TopicoResumoResponse> listar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long turmaId,
            @RequestParam(required = false) Long aulaId,
            @ParameterObject Pageable pagina) {
        return Pagina.de(
                forumAlunoService.listar(usuario(jwt), turmaId, aulaId, pagina).map(TopicoResumoResponse::aluno));
    }

    @PostMapping("/turmas/{turmaId}/forum")
    @Operation(
            summary = "Perguntar",
            description = "Corpo em Markdown (o HTML é filtrado pela API); imagens anexadas pelas chaves enviadas.")
    public ResponseEntity<TopicoResponse> perguntar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long turmaId,
            @Valid @RequestBody ForumRequests.Perguntar request) {
        TopicoResponse topico = TopicoResponse.aluno(forumAlunoService.perguntar(
                usuario(jwt), turmaId, request.aulaId(), request.titulo(), request.corpo(), request.anexos()));
        return ResponseEntity.created(URI.create("/api/aluno/forum/topicos/" + topico.id()))
                .body(topico);
    }

    @GetMapping("/forum/topicos/{topicoId}")
    @Operation(summary = "Tópico com as respostas")
    public TopicoResponse topico(@AuthenticationPrincipal Jwt jwt, @PathVariable Long topicoId) {
        return TopicoResponse.aluno(forumAlunoService.topico(usuario(jwt), topicoId));
    }

    @PostMapping("/forum/topicos/{topicoId}/respostas")
    @Operation(summary = "Responder", description = "Corpo em Markdown; devolve o tópico atualizado.")
    public TopicoResponse responder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long topicoId,
            @Valid @RequestBody ForumRequests.Responder request) {
        return TopicoResponse.aluno(
                forumAlunoService.responder(usuario(jwt), topicoId, request.corpo(), request.anexos()));
    }

    @PostMapping("/forum/anexos/envio")
    @Operation(
            summary = "Iniciar o envio de uma imagem",
            description = "JPEG ou PNG até 5 MB. Devolve a URL para enviar direto ao armazenamento e a chave para"
                    + " anexar na pergunta ou resposta.")
    public EnvioAnexoResponse iniciarEnvioAnexo(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ForumRequests.IniciarEnvioAnexo request) {
        return EnvioAnexoResponse.de(
                anexoForumService.iniciarEnvio(usuario(jwt), request.contentType(), request.tamanho()));
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/api/ForumAdmController.java`:

`````java
package br.com.celke.cursos.forum.api;

import br.com.celke.cursos.forum.api.ForumResponses.TopicoResponse;
import br.com.celke.cursos.forum.api.ForumResponses.TopicoResumoResponse;
import br.com.celke.cursos.forum.application.ForumAdmService;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Fórum")
public class ForumAdmController {

    private final ForumAdmService forumAdmService;

    public ForumAdmController(ForumAdmService forumAdmService) {
        this.forumAdmService = forumAdmService;
    }

    @GetMapping("/turmas/{turmaId}/forum")
    @Operation(summary = "Tópicos da turma", description = "Inclui os ocultos, para moderação.")
    public Pagina<TopicoResumoResponse> listar(@PathVariable Long turmaId, @ParameterObject Pageable pagina) {
        return Pagina.de(forumAdmService.listar(turmaId, pagina).map(TopicoResumoResponse::moderacao));
    }

    @GetMapping("/forum/topicos/{topicoId}")
    @Operation(summary = "Tópico com todas as respostas", description = "Inclui as ocultas.")
    public TopicoResponse topico(@PathVariable Long topicoId) {
        return TopicoResponse.moderacao(forumAdmService.topico(topicoId));
    }

    @PatchMapping("/forum/topicos/{topicoId}")
    @Operation(summary = "Moderar tópico", description = "Ocultar/mostrar e fixar/soltar.")
    public TopicoResponse moderarTopico(
            @PathVariable Long topicoId, @Valid @RequestBody ForumRequests.ModerarTopico request) {
        return TopicoResponse.moderacao(forumAdmService.moderarTopico(topicoId, request.oculto(), request.fixado()));
    }

    @PatchMapping("/forum/respostas/{respostaId}")
    @Operation(summary = "Moderar resposta", description = "Ocultar ou mostrar.")
    public TopicoResponse moderarResposta(
            @PathVariable Long respostaId, @Valid @RequestBody ForumRequests.ModerarResposta request) {
        return TopicoResponse.moderacao(forumAdmService.moderarResposta(respostaId, request.oculto()));
    }

    @PostMapping("/forum/topicos/{topicoId}/respostas")
    @Operation(summary = "Responder como instrutor", description = "O tópico fica marcado como respondido.")
    public TopicoResponse responder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long topicoId,
            @Valid @RequestBody ForumRequests.ResponderInstrutor request) {
        return TopicoResponse.moderacao(
                forumAdmService.responder(Long.valueOf(jwt.getSubject()), topicoId, request.corpo()));
    }
}
`````

### 6. Testes unitários

`backend/src/test/java/br/com/celke/cursos/forum/domain/MarkdownSeguroTest.java`:

`````java
package br.com.celke.cursos.forum.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MarkdownSeguroTest {

    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Test
    @DisplayName("Markdown comum vira HTML: ênfase, listas, código, citação e tabela")
    void markdownComum() {
        String html = MarkdownSeguro.paraHtml("""
                Olá **turma**, veja o *exemplo*:

                - item 1
                - item 2

                ```
                int x = 1;
                ```

                > citação
                """);

        assertThat(html)
                .contains("<strong>turma</strong>")
                .contains("<em>exemplo</em>")
                .contains("<li>item 1</li>")
                .contains("<pre><code>int x")
                .contains("<blockquote>");
    }

    @Test
    @DisplayName("link http vira <a> com rel nofollow noopener noreferrer")
    void link() {
        assertThat(MarkdownSeguro.paraHtml("[docs](https://docs.oracle.com)"))
                .contains("href=\"https://docs.oracle.com\"")
                .contains("rel=\"nofollow noopener noreferrer\"");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<script>alert(1)</script>",
                "<img src=x onerror=alert(1)>",
                "<a href=\"javascript:alert(1)\">clique</a>",
                "[clique](javascript:alert(1))",
                "[clique](JaVaScRiPt:alert(1))",
                "<iframe src=\"https://mal.example\"></iframe>",
                "<svg onload=alert(1)>",
                "<p style=\"background:url(javascript:alert(1))\">x</p>",
                "![x](javascript:alert(1))",
                "<div onclick=\"alert(1)\">x</div>",
                "[x](data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==)",
            })
    @DisplayName("XSS: nenhum script, evento, iframe, estilo ou esquema perigoso sobrevive")
    void xss(String ataque) {
        String html = MarkdownSeguro.paraHtml(ataque);

        // O que o usuário escreveu pode aparecer escapado, como texto; o que importa são as tags geradas
        List<String> tags =
                TAG.matcher(html).results().map(m -> m.group().toLowerCase()).toList();
        assertThat(tags)
                .noneMatch(tag -> tag.matches("<(script|img|iframe|svg|object|embed|style)\\b.*"))
                .noneMatch(tag -> tag.matches(".*\\son\\w+\\s*=.*"))
                .noneMatch(tag -> tag.contains("style="))
                .noneMatch(tag -> tag.contains("javascript:"))
                .noneMatch(tag -> tag.contains("data:"));
    }

    @Test
    @DisplayName("HTML escrito pelo usuário aparece como texto, escapado")
    void htmlViraTexto() {
        assertThat(MarkdownSeguro.paraHtml("<b>negrito?</b>")).contains("&lt;b&gt;negrito?&lt;/b&gt;");
    }

    @Test
    @DisplayName("vazio ou nulo vira texto vazio")
    void vazio() {
        assertThat(MarkdownSeguro.paraHtml(null)).isEmpty();
        assertThat(MarkdownSeguro.paraHtml("   ")).isEmpty();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/forum/domain/TopicoTest.java`:

`````java
package br.com.celke.cursos.forum.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TopicoTest {

    private static final Instant T0 = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    @Test
    @DisplayName("tópico novo: texto aparado, HTML gerado e filtrado, visível, não fixado")
    void novo() {
        Topico topico = new Topico(5L, 9L, 7L, "  Dúvida  ", "  **Ajuda** <script>x</script> ", T0);

        assertThat(topico.getTitulo()).isEqualTo("Dúvida");
        assertThat(topico.getCorpoMarkdown()).isEqualTo("**Ajuda** <script>x</script>");
        assertThat(topico.getCorpoHtml()).contains("<strong>Ajuda</strong>").doesNotContain("<script");
        assertThat(topico.isOculto()).isFalse();
        assertThat(topico.isFixado()).isFalse();
        assertThat(topico.isRespondidoInstrutor()).isFalse();
        assertThat(topico.getCriadoEm()).isEqualTo(T0);
    }

    @Test
    @DisplayName("moderação muda só os campos enviados")
    void moderar() {
        Topico topico = new Topico(5L, null, 7L, "T", "C", T0);

        topico.moderar(null, true, T1);
        assertThat(topico.isFixado()).isTrue();
        assertThat(topico.isOculto()).isFalse();

        topico.moderar(true, null, T1);
        assertThat(topico.isFixado()).isTrue();
        assertThat(topico.isOculto()).isTrue();
        assertThat(topico.getAtualizadoEm()).isEqualTo(T1);
    }

    @Test
    @DisplayName("resposta do instrutor marca o tópico como respondido")
    void respondido() {
        Topico topico = new Topico(5L, null, 7L, "T", "C", T0);

        topico.registrarRespostaInstrutor(T1);

        assertThat(topico.isRespondidoInstrutor()).isTrue();
    }

    @Test
    @DisplayName("resposta: HTML filtrado, instrutor e moderação")
    void resposta() {
        Resposta resposta = new Resposta(1L, 2L, " [x](javascript:alert(1)) ", true, T0);

        assertThat(resposta.getCorpoHtml()).doesNotContain("javascript:");
        assertThat(resposta.isInstrutor()).isTrue();
        resposta.moderar(true, T1);
        assertThat(resposta.isOculto()).isTrue();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/forum/application/LimiteForumTest.java`:

`````java
package br.com.celke.cursos.forum.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.forum.infra.TopicoRepository;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimiteForumTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant DESDE = AGORA.minus(Duration.ofHours(1));

    @Mock
    private TopicoRepository topicos;

    @Mock
    private RespostaRepository respostas;

    private LimiteForum limite;

    @BeforeEach
    void criar() {
        limite = new LimiteForum(
                topicos, respostas, new ForumProperties(5, Duration.ofHours(1), 3), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void publicadas(long topicosNaJanela, long respostasNaJanela) {
        when(topicos.countByAutorIdAndCriadoEmAfter(7L, DESDE)).thenReturn(topicosNaJanela);
        when(respostas.countByAutorIdAndCriadoEmAfter(7L, DESDE)).thenReturn(respostasNaJanela);
    }

    @ParameterizedTest(name = "{0} tópicos + {1} respostas")
    @CsvSource({"0, 0", "4, 0", "0, 4", "2, 2"})
    @DisplayName("abaixo de 5 mensagens na última hora, publica")
    void abaixoDoLimite(long t, long r) {
        publicadas(t, r);

        limite.exigirDentroDoLimite(7L);
    }

    @ParameterizedTest(name = "{0} tópicos + {1} respostas")
    @CsvSource({"5, 0", "3, 2", "0, 9"})
    @DisplayName("no limite (tópicos e respostas somados): 429 com a espera até a mais antiga sair da janela")
    void noLimite(long t, long r) {
        publicadas(t, r);
        Topico antigo = new Topico(5L, null, 7L, "T", "C", AGORA.minus(Duration.ofMinutes(50)));
        Resposta maisAntiga = new Resposta(1L, 7L, "C", false, AGORA.minus(Duration.ofMinutes(55)));
        when(topicos.findFirstByAutorIdAndCriadoEmAfterOrderByCriadoEm(7L, DESDE))
                .thenReturn(Optional.of(antigo));
        when(respostas.findFirstByAutorIdAndCriadoEmAfterOrderByCriadoEm(7L, DESDE))
                .thenReturn(Optional.of(maisAntiga));

        assertThatThrownBy(() -> limite.exigirDentroDoLimite(7L))
                .isInstanceOfSatisfying(
                        MuitasTentativasException.class,
                        e -> assertThat(e.getEspera()).isEqualTo(Duration.ofMinutes(5)))
                .hasMessage("Muitas tentativas. Tente novamente em 5 minutos.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/forum/application/AnexoForumServiceTest.java`:

`````java
package br.com.celke.cursos.forum.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.imagem.ImagemInvalidaException;
import br.com.celke.cursos.shared.imagem.ProcessadorImagem;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnexoForumServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String U1 = "uploads/forum/7/0b6b6b6e-1c1d-4e4f-8a8b-9c9d0e0f1a1b";
    private static final String U2 = "uploads/forum/7/1c7c7c7f-2d2e-4f5a-9b9c-0d0e1f2a3b4c";

    @Mock
    private StorageService storage;

    @Mock
    private ProcessadorImagem processador;

    private AnexoForumService service;

    @BeforeEach
    void criar() {
        service = new AnexoForumService(
                storage,
                processador,
                new ForumProperties(5, Duration.ofHours(1), 3),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("envio: URL em uploads/forum/<usuario>/<uuid> para JPEG ou PNG até 5 MB")
    void iniciarEnvio() throws Exception {
        when(storage.urlUpload(startsWith("uploads/forum/7/"), eq("image/png"), eq(Duration.ofMinutes(10))))
                .thenReturn(URI.create("http://media.localhost/put").toURL());

        var envio = service.iniciarEnvio(7L, "image/png", AnexoForumService.TAMANHO_MAXIMO);

        assertThat(envio.chave()).matches("uploads/forum/7/[0-9a-f-]{36}");
        assertThat(envio.expiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("envio recusado: tipo que não é imagem aceita e tamanho acima de 5 MB")
    void envioRecusado() {
        assertThatThrownBy(() -> service.iniciarEnvio(7L, "image/svg+xml", 10))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("Anexe uma imagem JPEG ou PNG.");
        assertThatThrownBy(() -> service.iniciarEnvio(7L, "image/png", AnexoForumService.TAMANHO_MAXIMO + 1))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("A imagem pode ter no máximo 5 MB.");
        verify(storage, never()).urlUpload(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("processa: regrava em JPEG em forum/anexos/ e apaga o envio")
    void processa() {
        when(storage.info(U1)).thenReturn(Optional.of(new InfoArquivo(100, "image/png")));
        when(storage.baixar(U1)).thenReturn(new byte[] {1});
        when(processador.normalizar(new byte[] {1}, 1600, 1600)).thenReturn(new byte[] {9, 9});

        var gravados = service.processar(7L, List.of(U1));

        assertThat(gravados).singleElement().satisfies(g -> {
            assertThat(g.chave()).matches("forum/anexos/[0-9a-f-]{36}\\.jpg");
            assertThat(g.tamanho()).isEqualTo(2);
        });
        verify(storage).gravar(startsWith("forum/anexos/"), eq(new byte[] {9, 9}), eq("image/jpeg"));
        verify(storage).excluir(U1);
    }

    @Test
    @DisplayName("IDOR: chave enviada por outro usuário (ou fora do formato) é recusada sem tocar no bucket")
    void chaveDeOutroUsuario() {
        for (String chave : List.of(
                "uploads/forum/8/0b6b6b6e-1c1d-4e4f-8a8b-9c9d0e0f1a1b",
                "forum/anexos/x.jpg",
                "uploads/forum/7/../8/x")) {
            assertThatThrownBy(() -> service.processar(7L, List.of(chave)))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("Anexo inválido. Envie a imagem de novo.");
        }
        verify(storage, never()).info(anyString());
    }

    @Test
    @DisplayName("mais anexos que o permitido ou a mesma imagem duas vezes são recusados")
    void quantidade() {
        assertThatThrownBy(() -> service.processar(7L, List.of(U1, U2, U1 + "a", U2 + "b")))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("Anexe no máximo 3 imagens.");
        assertThatThrownBy(() -> service.processar(7L, List.of(U1, U1)))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("A mesma imagem foi anexada duas vezes.");
        assertThat(service.processar(7L, null)).isEmpty();
        assertThat(service.processar(7L, List.of())).isEmpty();
    }

    @Test
    @DisplayName("falha na segunda imagem desfaz a primeira já gravada")
    void falhaDesfaz() {
        when(storage.info(U1)).thenReturn(Optional.of(new InfoArquivo(100, "image/png")));
        when(storage.baixar(U1)).thenReturn(new byte[] {1});
        when(processador.normalizar(new byte[] {1}, 1600, 1600)).thenReturn(new byte[] {9});
        when(storage.info(U2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processar(7L, List.of(U1, U2)))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("A imagem não foi enviada. Envie a imagem de novo.");
        verify(storage).excluir(startsWith("forum/anexos/"));
    }

    @Test
    @DisplayName("imagem acima de 5 MB enviada mesmo assim é recusada e o envio apagado")
    void grandeDemais() {
        when(storage.info(U1))
                .thenReturn(Optional.of(new InfoArquivo(AnexoForumService.TAMANHO_MAXIMO + 1, "image/png")));

        assertThatThrownBy(() -> service.processar(7L, List.of(U1))).hasMessage("A imagem pode ter no máximo 5 MB.");
        verify(storage).excluir(U1);
        verify(storage, never()).gravar(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("descartar apaga as imagens gravadas")
    void descartar() {
        service.descartar(List.of(new AnexoForumService.AnexoGravado("forum/anexos/a.jpg", 1)));

        verify(storage).excluir("forum/anexos/a.jpg");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/forum/application/ForumAlunoServiceTest.java`:

`````java
package br.com.celke.cursos.forum.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaContexto;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.forum.application.AnexoForumService.AnexoGravado;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoDetalhe;
import br.com.celke.cursos.forum.domain.Anexo;
import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.AnexoRepository;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.forum.infra.TopicoRepository;
import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ForumAlunoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final List<AnexoGravado> GRAVADOS = List.of(new AnexoGravado("forum/anexos/a.jpg", 10));

    @Mock
    private TopicoRepository topicos;

    @Mock
    private RespostaRepository respostas;

    @Mock
    private AnexoRepository anexos;

    @Mock
    private AcessoAulaService acessoAulaService;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private AnexoForumService anexoForumService;

    @Mock
    private LimiteForum limite;

    @Mock
    private LeituraForum leitura;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ForumAlunoService service;

    @BeforeEach
    void criar() {
        service = new ForumAlunoService(
                topicos,
                respostas,
                anexos,
                acessoAulaService,
                conteudoTurmaService,
                anexoForumService,
                limite,
                leitura,
                new TransactionTemplate(transactionManager),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void transacoes() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private static AulaContexto aula(long turmaId) {
        return new AulaContexto(9L, "Aula", null, false, turmaId, StatusTurma.ATIVA, StatusCurso.PUBLICADO);
    }

    private static TopicoDetalhe detalhe(Topico topico) {
        return new TopicoDetalhe(topico, "Ana", List.of(), List.of());
    }

    @Test
    @DisplayName("pergunta numa aula da turma: confere acesso e limite, grava tópico e anexos")
    void perguntar() {
        transacoes();
        when(conteudoTurmaService.aulaAtiva(9L)).thenReturn(Optional.of(aula(5L)));
        when(anexoForumService.processar(7L, List.of("k"))).thenReturn(GRAVADOS);
        when(topicos.save(any(Topico.class))).thenAnswer(i -> i.getArgument(0));
        when(leitura.detalhe(any(Topico.class), eq(false))).thenAnswer(i -> detalhe(i.getArgument(0)));

        TopicoDetalhe d = service.perguntar(7L, 5L, 9L, "Dúvida", "Corpo", List.of("k"));

        verify(acessoAulaService).exigirAcessoTurma(7L, 5L);
        verify(limite).exigirDentroDoLimite(7L);
        verify(anexos).save(any(Anexo.class));
        assertThat(d.topico().getTurmaId()).isEqualTo(5L);
        assertThat(d.topico().getAulaId()).isEqualTo(9L);
        assertThat(d.topico().getAutorId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("aula de outra turma (ou inexistente) responde 404 antes do limite e dos anexos")
    void aulaDeOutraTurma() {
        when(conteudoTurmaService.aulaAtiva(9L)).thenReturn(Optional.of(aula(6L)));

        assertThatThrownBy(() -> service.perguntar(7L, 5L, 9L, "T", "C", List.of("k")))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Aula não encontrada.");
        verify(limite, never()).exigirDentroDoLimite(any());
        verify(anexoForumService, never()).processar(any(), anyList());
    }

    @Test
    @DisplayName("sem matrícula vigente: nada é gravado")
    void semAcesso() {
        doThrow(new AcessoNegadoException("sem acesso")).when(acessoAulaService).exigirAcessoTurma(7L, 5L);

        assertThatThrownBy(() -> service.perguntar(7L, 5L, null, "T", "C", null))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.listar(7L, 5L, null, org.springframework.data.domain.Pageable.ofSize(10)))
                .isInstanceOf(AcessoNegadoException.class);
        verify(topicos, never()).save(any());
    }

    @Test
    @DisplayName("limite atingido: 429 sem processar anexos")
    void limiteAtingido() {
        doThrow(new MuitasTentativasException(Duration.ofMinutes(5)))
                .when(limite)
                .exigirDentroDoLimite(7L);

        assertThatThrownBy(() -> service.perguntar(7L, 5L, null, "T", "C", List.of("k")))
                .isInstanceOf(MuitasTentativasException.class);
        verify(anexoForumService, never()).processar(any(), anyList());
    }

    @Test
    @DisplayName("falha ao gravar a pergunta descarta as imagens já processadas")
    void falhaDescartaAnexos() {
        transacoes();
        when(anexoForumService.processar(7L, List.of("k"))).thenReturn(GRAVADOS);
        when(topicos.save(any(Topico.class))).thenThrow(new IllegalStateException("banco"));

        assertThatThrownBy(() -> service.perguntar(7L, 5L, null, "T", "C", List.of("k")))
                .isInstanceOf(IllegalStateException.class);
        verify(anexoForumService).descartar(GRAVADOS);
    }

    @Test
    @DisplayName("tópico oculto ou inexistente responde 404 para o aluno, na leitura e na resposta")
    void ocultoOuInexistente() {
        when(topicos.findByIdAndOcultoFalse(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.topico(7L, 3L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Tópico não encontrado.");
        assertThatThrownBy(() -> service.responder(7L, 3L, "C", null)).isInstanceOf(NaoEncontradoException.class);
        verify(respostas, never()).save(any());
    }

    @Test
    @DisplayName("resposta de aluno: confere a turma do tópico e o limite; não é de instrutor")
    void responder() {
        transacoes();
        Topico topico = new Topico(5L, null, 8L, "T", "C", AGORA);
        when(topicos.findByIdAndOcultoFalse(3L)).thenReturn(Optional.of(topico));
        when(anexoForumService.processar(7L, null)).thenReturn(List.of());
        when(respostas.save(any(Resposta.class))).thenAnswer(i -> i.getArgument(0));
        when(leitura.detalhe(topico, false)).thenReturn(detalhe(topico));

        service.responder(7L, 3L, "Resposta", null);

        verify(acessoAulaService).exigirAcessoTurma(7L, 5L);
        verify(limite).exigirDentroDoLimite(7L);
        verify(respostas).save(org.mockito.ArgumentMatchers.argThat(r -> !r.isInstrutor() && r.getAutorId() == 7L));
        assertThat(topico.isRespondidoInstrutor()).isFalse();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/forum/application/ForumAdmServiceTest.java`:

`````java
package br.com.celke.cursos.forum.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoDetalhe;
import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.forum.infra.TopicoRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ForumAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private TopicoRepository topicos;

    @Mock
    private RespostaRepository respostas;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private LeituraForum leitura;

    private ForumAdmService service;

    @BeforeEach
    void criar() {
        service = new ForumAdmService(
                topicos, respostas, conteudoTurmaService, leitura, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static Topico topico() {
        return new Topico(5L, null, 8L, "T", "C", AGORA.minusSeconds(60));
    }

    @Test
    @DisplayName("turma inexistente responde 404 na listagem")
    void turmaInexistente() {
        when(conteudoTurmaService.turma(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(5L, Pageable.ofSize(10)))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Turma não encontrada.");
        verify(topicos, never()).daTurma(any(), any());
    }

    @Test
    @DisplayName("moderar tópico aplica oculto e fixado e devolve a visão completa")
    void moderarTopico() {
        Topico topico = topico();
        when(topicos.findById(3L)).thenReturn(Optional.of(topico));
        when(leitura.detalhe(topico, true)).thenReturn(new TopicoDetalhe(topico, "Ana", List.of(), List.of()));

        service.moderarTopico(3L, true, true);

        assertThat(topico.isOculto()).isTrue();
        assertThat(topico.isFixado()).isTrue();
    }

    @Test
    @DisplayName("moderar resposta inexistente responde 404")
    void respostaInexistente() {
        when(respostas.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moderarResposta(4L, true))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Resposta não encontrada.");
    }

    @Test
    @DisplayName("moderar resposta oculta só ela")
    void moderarResposta() {
        Topico topico = topico();
        Resposta resposta = new Resposta(3L, 8L, "R", false, AGORA);
        when(respostas.findById(4L)).thenReturn(Optional.of(resposta));
        when(topicos.findById(3L)).thenReturn(Optional.of(topico));

        service.moderarResposta(4L, true);

        assertThat(resposta.isOculto()).isTrue();
        assertThat(topico.isOculto()).isFalse();
    }

    @Test
    @DisplayName("resposta do instrutor é marcada como tal e o tópico fica respondido")
    void responder() {
        Topico topico = topico();
        when(topicos.findById(3L)).thenReturn(Optional.of(topico));

        service.responder(1L, 3L, "Resposta oficial");

        ArgumentCaptor<Resposta> captor = ArgumentCaptor.forClass(Resposta.class);
        verify(respostas).save(captor.capture());
        assertThat(captor.getValue().isInstrutor()).isTrue();
        assertThat(captor.getValue().getAutorId()).isEqualTo(1L);
        assertThat(topico.isRespondidoInstrutor()).isTrue();
    }

    @Test
    @DisplayName("tópico inexistente responde 404")
    void topicoInexistente() {
        when(topicos.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.topico(3L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Tópico não encontrado.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/forum/api/ForumRequestsTest.java`:

`````java
package br.com.celke.cursos.forum.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.forum.api.ForumRequests.ModerarResposta;
import br.com.celke.cursos.forum.api.ForumRequests.Perguntar;
import br.com.celke.cursos.forum.api.ForumRequests.Responder;
import br.com.celke.cursos.forum.api.ForumRequests.ResponderInstrutor;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForumRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object dto) {
        return VALIDATOR.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("pergunta: título até 150, corpo até 10.000, anexos opcionais")
    void perguntar() {
        assertThat(camposInvalidos(new Perguntar("Dúvida", "Corpo", null, null)))
                .isEmpty();
        assertThat(camposInvalidos(new Perguntar("t".repeat(150), "c".repeat(10_000), 9L, List.of("k"))))
                .isEmpty();
        assertThat(camposInvalidos(new Perguntar(" ", "", null, null))).containsExactlyInAnyOrder("titulo", "corpo");
        assertThat(camposInvalidos(new Perguntar("t".repeat(151), "c".repeat(10_001), null, null)))
                .containsExactlyInAnyOrder("titulo", "corpo");
        assertThat(camposInvalidos(new Perguntar("T", "C", null, Collections.nCopies(11, "k"))))
                .containsExactly("anexos");
        assertThat(camposInvalidos(new Perguntar("T", "C", null, List.of(" "))))
                .containsExactly("anexos[0].<list element>");
    }

    @Test
    @DisplayName("resposta e resposta do instrutor: corpo obrigatório")
    void responder() {
        assertThat(camposInvalidos(new Responder("ok", null))).isEmpty();
        assertThat(camposInvalidos(new Responder(" ", null))).containsExactly("corpo");
        assertThat(camposInvalidos(new ResponderInstrutor(null))).containsExactly("corpo");
    }

    @Test
    @DisplayName("moderar resposta exige o oculto")
    void moderarResposta() {
        assertThat(camposInvalidos(new ModerarResposta(null))).containsExactly("oculto");
        assertThat(camposInvalidos(new ModerarResposta(true))).isEmpty();
    }
}
`````

### 7. Testes de integração

`backend/src/test/java/br/com/celke/cursos/forum/ForumAcessoIT.java`:

`````java
package br.com.celke.cursos.forum;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: matriz de papéis das rotas de moderação do fórum ({@code /api/adm}: 401 anônimo, 403 ALUNO —
 * mesmo o autor do tópico —, ADMIN passa pela segurança).
 * Não cobre: o comportamento das rotas (ForumIT).
 * Dado criado: a cada caso, curso it-<stamp> publicado e um aluno próprio matriculado, com um tópico e
 * uma resposta (ids reais nas rotas).
 */
class ForumAcessoIT extends IntegrationTest {

    private String tokenAdmin;
    private String tokenAluno;
    private long turma;
    private long topico;
    private Object resposta;

    @BeforeEach
    void criarTopico() {
        tokenAdmin = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
        MatriculaITHelper helper = new MatriculaITHelper(new CursoITHelper(mvc, bearer(tokenAdmin)));
        var estrutura = helper.cursoPublicado(stamp());
        turma = estrutura.turma();
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        helper.matricula(turma, aluno.email(), Instant.now().minus(Duration.ofDays(1)), null);
        tokenAluno = login(aluno.email(), aluno.senha());
        topico = CursoITHelper.idCriado(chamar(
                HttpMethod.POST,
                "/api/aluno/turmas/" + turma + "/forum",
                "{\"titulo\":\"T\",\"corpo\":\"C\"}",
                tokenAluno));
        resposta = JsonPath.read(
                CursoITHelper.corpo(chamar(
                        HttpMethod.POST,
                        "/api/aluno/forum/topicos/" + topico + "/respostas",
                        "{\"corpo\":\"R\"}",
                        tokenAluno)),
                "$.respostas[0].id");
    }

    static Stream<Arguments> rotas() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/adm/turmas/{t}/forum", null),
                Arguments.of(HttpMethod.GET, "/api/adm/forum/topicos/{p}", null),
                Arguments.of(HttpMethod.PATCH, "/api/adm/forum/topicos/{p}", "{\"fixado\":true}"),
                Arguments.of(HttpMethod.PATCH, "/api/adm/forum/respostas/{r}", "{\"oculto\":false}"),
                Arguments.of(HttpMethod.POST, "/api/adm/forum/topicos/{p}/respostas", "{\"corpo\":\"Oficial\"}"));
    }

    private MvcTestResult chamar(HttpMethod metodo, String rota, String corpo, String token) {
        String uri = rota.replace("{t}", String.valueOf(turma))
                .replace("{p}", String.valueOf(topico))
                .replace("{r}", String.valueOf(resposta));
        var requisicao = mvc.method(metodo).uri(uri);
        if (corpo != null) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        if (token != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao.exchange();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotas")
    @DisplayName("anônimo recebe 401")
    void anonimo(HttpMethod metodo, String rota, String corpo) {
        assertThat(chamar(metodo, rota, corpo, null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotas")
    @DisplayName("ALUNO (autor do tópico) recebe 403")
    void aluno(HttpMethod metodo, String rota, String corpo) {
        assertThat(chamar(metodo, rota, corpo, tokenAluno))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Acesso negado.");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotas")
    @DisplayName("ADMIN passa pela segurança")
    void admin(HttpMethod metodo, String rota, String corpo) {
        assertThat(chamar(metodo, rota, corpo, tokenAdmin)).hasStatusOk();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/forum/ForumIT.java`:

`````java
package br.com.celke.cursos.forum;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.support.fixtures.ImagemFixture;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: fórum da turma — pergunta e resposta do instrutor, HTML filtrado (XSS guardado não volta),
 * anexo de imagem (processado, sem EXIF) e IDOR de anexo, aluno sem matrícula, moderação (ocultar,
 * fixar, ocultar resposta), aula de outra turma, limite de mensagens (429) e validação.
 * Não cobre: o filtro de Markdown caso a caso (MarkdownSeguroTest) e a matriz de papéis do ADM
 * (ForumAcessoIT).
 * Dado criado: curso it-<stamp> publicado e alunos próprios, matriculados na turma.
 */
class ForumIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private StorageService storage;

    private MatriculaITHelper helper;
    private MatriculaITHelper.Estrutura estrutura;
    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarTurmaComAluno() {
        helper = new MatriculaITHelper(
                new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA))));
        estrutura = helper.cursoPublicado(stamp());
        aluno = matriculado();
        token = login(aluno.email(), aluno.senha());
    }

    private UsuarioTeste matriculado() {
        UsuarioTeste novo = criarUsuario(Papel.ALUNO);
        Instant agora = Instant.now();
        helper.matricula(estrutura.turma(), novo.email(), agora.minus(DIA), agora.plus(DIA));
        return novo;
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

    private String forum() {
        return "/api/aluno/turmas/" + estrutura.turma() + "/forum";
    }

    private MvcTestResult perguntar(String corpoJson, String tokenUsado) {
        return chamar(HttpMethod.POST, forum(), corpoJson, tokenUsado);
    }

    private long perguntaSimples(String titulo) {
        return CursoITHelper.idCriado(perguntar("{\"titulo\":\"%s\",\"corpo\":\"Corpo\"}".formatted(titulo), token));
    }

    private MvcTestResult adm(String metodo, String uri, String corpo) {
        return helper.adm().enviar(metodo, uri, corpo);
    }

    /** Envia uma imagem PNG pela URL pré-assinada do fórum e devolve a chave. */
    private String enviarImagem(String tokenUsado) throws Exception {
        byte[] png = ImagemFixture.png(40, 30);
        String corpo = CursoITHelper.corpo(chamar(
                HttpMethod.POST,
                "/api/aluno/forum/anexos/envio",
                "{\"contentType\":\"image/png\",\"tamanho\":%d}".formatted(png.length),
                tokenUsado));
        var put = http.send(
                HttpRequest.newBuilder(URI.create(JsonPath.read(corpo, "$.url")))
                        .header("Content-Type", "image/png")
                        .PUT(BodyPublishers.ofByteArray(png))
                        .build(),
                BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);
        return JsonPath.read(corpo, "$.chave");
    }

    @Test
    @DisplayName("aluno pergunta na aula (com XSS filtrado), instrutor responde, aluno vê a resposta")
    void perguntaERespostaDoInstrutor() {
        MvcTestResult pergunta = perguntar("""
                {"titulo":"Erro no exemplo","aulaId":%d,
                 "corpo":"Recebo **erro**: <script>alert(1)</script> [veja](javascript:alert(2))"}
                """.formatted(estrutura.aulaPaga()), token);
        assertThat(pergunta).hasStatus(HttpStatus.CREATED);
        long topico = CursoITHelper.idCriado(pergunta);
        var json = assertThat(pergunta).bodyJson();
        json.extractingPath("$.aulaId").isEqualTo((int) estrutura.aulaPaga());
        json.extractingPath("$.corpoHtml")
                .asString()
                .contains("<strong>erro</strong>")
                .doesNotContain("<script")
                .doesNotContain("javascript:");
        json.extractingPath("$.respondidoInstrutor").isEqualTo(false);
        assertThat(CursoITHelper.corpo(pergunta))
                .doesNotContain("corpoMarkdown")
                .doesNotContain("\"oculto\":false");

        assertThat(chamar(HttpMethod.GET, forum() + "?aulaId=" + estrutura.aulaPaga(), null, token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.itens[*].id")
                .isEqualTo(List.of((int) topico));

        assertThat(adm("POST", "/api/adm/forum/topicos/" + topico + "/respostas", "{\"corpo\":\"Use `final`.\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.respondidoInstrutor")
                .isEqualTo(true);

        var visto = assertThat(chamar(HttpMethod.GET, "/api/aluno/forum/topicos/" + topico, null, token))
                .hasStatusOk()
                .bodyJson();
        visto.extractingPath("$.respostas[0].instrutor").isEqualTo(true);
        visto.extractingPath("$.respostas[0].corpoHtml").asString().contains("<code>final</code>");
        visto.extractingPath("$.respostas[0].autorNome").isNotNull();

        // Outro aluno da turma responde; a lista mostra 2 respostas
        UsuarioTeste colega = matriculado();
        assertThat(chamar(
                        HttpMethod.POST,
                        "/api/aluno/forum/topicos/" + topico + "/respostas",
                        "{\"corpo\":\"Também tive.\"}",
                        login(colega.email(), colega.senha())))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.respostas[1].instrutor")
                .isEqualTo(false);
        assertThat(chamar(HttpMethod.GET, forum(), null, token))
                .bodyJson()
                .extractingPath("$.itens[0].respostas")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("anexo: imagem do próprio aluno vira JPEG sem EXIF; a de outro aluno é recusada (IDOR)")
    void anexoEIdor() throws Exception {
        String minha = enviarImagem(token);
        MvcTestResult pergunta = perguntar(
                "{\"titulo\":\"Print do erro\",\"corpo\":\"Segue\",\"anexos\":[\"%s\"]}".formatted(minha), token);
        assertThat(pergunta).hasStatus(HttpStatus.CREATED);
        assertThat(storage.info(minha)).as("envio original apagado").isEmpty();
        String url = JsonPath.read(CursoITHelper.corpo(pergunta), "$.anexos[0].url");
        HttpResponse<byte[]> imagem =
                http.send(HttpRequest.newBuilder(URI.create(url)).build(), BodyHandlers.ofByteArray());
        assertThat(imagem.statusCode()).isEqualTo(200);
        assertThat(imagem.headers().firstValue("Content-Type")).contains("image/jpeg");
        assertThat(imagem.body()).startsWith((byte) 0xFF, (byte) 0xD8);

        // Outro aluno tenta anexar a imagem enviada por este aluno
        String alheia = enviarImagem(token);
        UsuarioTeste outro = matriculado();
        assertThat(perguntar(
                        "{\"titulo\":\"Roubo\",\"corpo\":\"x\",\"anexos\":[\"%s\"]}".formatted(alheia),
                        login(outro.email(), outro.senha())))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Anexo inválido. Envie a imagem de novo.");
        assertThat(storage.info(alheia)).as("o envio do dono continua lá").isPresent();
    }

    @Test
    @DisplayName("aluno sem matrícula vigente: 403 para listar, perguntar, ler e responder")
    void semMatricula() {
        long topico = perguntaSimples("Dúvida");
        UsuarioTeste semMatricula = criarUsuario(Papel.ALUNO);
        String outro = login(semMatricula.email(), semMatricula.senha());

        assertThat(chamar(HttpMethod.GET, forum(), null, outro)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(perguntar("{\"titulo\":\"T\",\"corpo\":\"C\"}", outro))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
        assertThat(chamar(HttpMethod.GET, "/api/aluno/forum/topicos/" + topico, null, outro))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(chamar(
                        HttpMethod.POST,
                        "/api/aluno/forum/topicos/" + topico + "/respostas",
                        "{\"corpo\":\"x\"}",
                        outro))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("moderação: tópico oculto some para o aluno, fixado vai para o topo, resposta oculta some")
    void moderacao() {
        long primeiro = perguntaSimples("Primeiro");
        long segundo = perguntaSimples("Segundo");
        long terceiro = perguntaSimples("Terceiro");
        String respostaId = JsonPath.read(
                        CursoITHelper.corpo(chamar(
                                HttpMethod.POST,
                                "/api/aluno/forum/topicos/" + segundo + "/respostas",
                                "{\"corpo\":\"Resposta indevida\"}",
                                token)),
                        "$.respostas[0].id")
                .toString();

        assertThat(adm("PATCH", "/api/adm/forum/topicos/" + primeiro, "{\"fixado\":true}"))
                .hasStatusOk();
        assertThat(adm("PATCH", "/api/adm/forum/topicos/" + terceiro, "{\"oculto\":true}"))
                .bodyJson()
                .extractingPath("$.oculto")
                .isEqualTo(true);
        assertThat(adm("PATCH", "/api/adm/forum/respostas/" + respostaId, "{\"oculto\":true}"))
                .hasStatusOk();

        assertThat(chamar(HttpMethod.GET, forum(), null, token))
                .bodyJson()
                .extractingPath("$.itens[*].id")
                .isEqualTo(List.of((int) primeiro, (int) segundo));
        assertThat(chamar(HttpMethod.GET, "/api/aluno/forum/topicos/" + terceiro, null, token))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Tópico não encontrado.");
        assertThat(chamar(HttpMethod.GET, "/api/aluno/forum/topicos/" + segundo, null, token))
                .bodyJson()
                .extractingPath("$.respostas")
                .isEqualTo(List.of());

        // A moderação continua vendo tudo
        assertThat(adm("GET", "/api/adm/turmas/" + estrutura.turma() + "/forum", null))
                .bodyJson()
                .extractingPath("$.itens[*].oculto")
                .isEqualTo(List.of(false, true, false));
        assertThat(adm("GET", "/api/adm/forum/topicos/" + segundo, null))
                .bodyJson()
                .extractingPath("$.respostas[0].oculto")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("limite: a sexta mensagem na janela responde 429 com Retry-After")
    void limiteDeMensagens() {
        for (int i = 1; i <= 5; i++) {
            perguntaSimples("Pergunta " + i);
        }

        MvcTestResult excedeu = perguntar("{\"titulo\":\"Sexta\",\"corpo\":\"C\"}", token);

        assertThat(excedeu).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(excedeu.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }

    @Test
    @DisplayName("aula de outra turma: 404; corpo inválido: 400 com os campos")
    void recusas() {
        String outroSlug = stamp();
        var outra = helper.cursoPublicado(outroSlug);

        assertThat(perguntar("{\"titulo\":\"T\",\"corpo\":\"C\",\"aulaId\":%d}".formatted(outra.aulaPaga()), token))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Aula não encontrada.");
        assertThat(perguntar("{\"titulo\":\" \",\"corpo\":\"\"}", token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("corpo", "titulo"));
    }

    @Test
    @DisplayName("rotas do aluno exigem token")
    void anonimo() {
        long topico = perguntaSimples("Dúvida");

        assertThat(chamar(HttpMethod.GET, forum(), null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(perguntar("{\"titulo\":\"T\",\"corpo\":\"C\"}", null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.GET, "/api/aluno/forum/topicos/" + topico, null, null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.POST, "/api/aluno/forum/anexos/envio", "{}", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
`````

### 8. Jornada e2e

`backend/src/e2e/java/br/com/celke/cursos/e2e/forum/ForumE2E.java`:

`````java
package br.com.celke.cursos.e2e.forum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.Dados;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Jornada: o aluno matriculado pergunta no fórum da aula, o ADM responde como instrutor e o aluno vê a resposta. */
class ForumE2E {

    @Test
    @DisplayName("aluno pergunta, instrutor responde, aluno vê a resposta")
    void perguntaERespostaDoInstrutor() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();

        // 1. Curso publicado com turma ativa e uma aula; aluno novo matriculado
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Fórum e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Módulo\"}");
        long aula = adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Aula\"}");
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");
        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");
        adm.criar(
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"CORTESIA\",\"fim\":\"%s\"}"
                        .formatted(email, Instant.now().plus(Duration.ofDays(30))));

        // 2. Pergunta em Markdown, com HTML malicioso que não pode voltar
        JsonPath pergunta = aluno(token)
                .body(
                        "{\"titulo\":\"Erro ao compilar\",\"aulaId\":%d,\"corpo\":\"Aparece **erro** <script>alert(1)</script>\"}"
                                .formatted(aula))
                .post("/api/aluno/turmas/" + turma + "/forum")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath();
        long topico = pergunta.getLong("id");
        assertThat(pergunta.getString("corpoHtml"))
                .contains("<strong>erro</strong>")
                .doesNotContain("<script");

        // 3. O ADM vê a pergunta na moderação e responde como instrutor
        adm.requisicao()
                .get("/api/adm/turmas/" + turma + "/forum")
                .then()
                .statusCode(200)
                .body("itens[0].id", equalTo((int) topico));
        adm.requisicao()
                .body("{\"corpo\":\"Confira o import da classe.\"}")
                .post("/api/adm/forum/topicos/" + topico + "/respostas")
                .then()
                .statusCode(200)
                .body("respondidoInstrutor", equalTo(true));

        // 4. O aluno vê a resposta do instrutor
        aluno(token)
                .get("/api/aluno/forum/topicos/" + topico)
                .then()
                .statusCode(200)
                .body("respostas[0].instrutor", equalTo(true))
                .body("respostas[0].corpoHtml", equalTo("<p>Confira o import da classe.</p>"));
        aluno(token)
                .get("/api/aluno/turmas/" + turma + "/forum")
                .then()
                .statusCode(200)
                .body("itens[0].respondidoInstrutor", equalTo(true))
                .body("itens[0].respostas", equalTo(1));
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }
}
`````

## Antes de executar

Fase 6 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/forum
```

## Verificação

Dentro de `backend/` (Docker rodando e `ffmpeg` no PATH):

```bash
./mvnw spotless:apply
./mvnw verify
```

- `BUILD SUCCESS` com 661 testes unitários e 340 de integração, e a cobertura mínima atendida.

Na raiz do repositório, as jornadas e2e com a stack completa:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
(cd backend && ./mvnw verify -Pe2e)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- 16 jornadas passam, entre elas a `ForumE2E`.

No ambiente local, pelo Swagger, com um aluno matriculado numa turma:

- Como aluno, `POST /api/aluno/turmas/{id}/forum` com `<script>alert(1)</script>` no corpo: o `corpoHtml`
  devolvido traz o texto escapado, sem tag `script`.
- Como admin, responda em `POST /api/adm/forum/topicos/{id}/respostas`; o aluno vê a resposta com
  `instrutor: true`.
- Oculte o tópico com `PATCH /api/adm/forum/topicos/{id}` e `{"oculto":true}`: para o aluno ele responde 404.

## Depois de executar

```bash
git add backend/ infra/ docs/
git commit -m "Fórum por aula e por turma"
git push -u origin feature/forum
```

Abra o Pull Request de `feature/forum` para `homolog`.
