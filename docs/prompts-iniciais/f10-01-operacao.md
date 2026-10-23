# Operação: backup, alertas e segurança

**Fase:** 10
**Branch:** `feature/operacao`

## Prompt

Vamos preparar a operação antes de entrarem dados reais: backup fora da VPS com restauração testada,
alertas, rotação de logs e a revisão de segurança. Siga o `backend/CLAUDE.md`. Não faça commit.

O que esta etapa entrega:

- **Backup** (`infra/scripts/backup.sh <ambiente>`): restic num repositório S3 externo (R2/B2), com o
  dump do PostgreSQL por stdin e os arquivos do Garage depois de `garage meta snapshot` (o SQLite em uso
  fica de fora); retenção 7 diários, 4 semanais e 6 mensais. Configuração em
  `/opt/plataforma-cursos/backup.env`; `PROJETO`, `ENV_BACKUP` e `RESTIC_EXTRA` permitem testar na stack
  local.
- **Restauração** (`infra/scripts/restaurar.sh`): para API e worker, recria o banco do dump, troca os
  arquivos do Garage e copia o snapshot dos metadados para `db.sqlite`; falha no meio deixa API e worker
  parados de propósito. Filtros de tag do restic com vírgula (`--tag ambiente,postgres` é E; duas `--tag`
  são OU).
- **Alertas do worker** (`shared/operacao`, `app.alertas`, `ALERTA_EMAIL`): a cada 15 minutos, jobs que
  foram para ERRO (`FilaService.falhasDesde`, sem os de e-mail) e disco abaixo de 15% livre (repete a cada
  6 horas), por e-mail com o template `alerta-operacao`.
- **Logs**: rotação `json-file` (5 × 10 MB) em todos os serviços da produção, da homologação e do Caddy.
- **Segurança**: Trivy no pipeline (críticos com correção quebram o build); o `pom.xml` fixa Tomcat
  11.0.25 e Jackson 3.1.7 / 2.21.7 até o Spring Boot trazê-los; `Cross-Origin-Resource-Policy: same-site`
  no Caddy (aviso do OWASP ZAP), conferido no `ProxyE2E`.
- **`docs/operacao.md`**: backup, restauração e teste, monitoramento, logs e limpeza, horas de vídeo no
  disco, saída para R2/B2 e a revisão ASVS nível 1 com as varreduras.

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
		<!--
			Correções de segurança à frente do Spring Boot 4.1.1 (apontadas pelo Trivy no pipeline):
			Tomcat 11.0.25 (CVE-2026-65182, CVE-2026-65905, CVE-2026-68525) e Jackson 3.1.7 / 2.21.7
			(CVE-2026-89407, CVE-2026-68497). Remover ao atualizar o Spring Boot para uma versão que já as traga.
		-->
		<tomcat.version>11.0.25</tomcat.version>
		<jackson-bom.version>3.1.7</jackson-bom.version>
		<jackson-2-bom.version>2.21.7</jackson-2-bom.version>
		<springdoc.version>3.1.1</springdoc.version>
		<aws-sdk.version>2.57.1</aws-sdk.version>
		<bouncycastle.version>1.86</bouncycastle.version>
		<bucket4j.version>8.21.0</bucket4j.version>
		<commonmark.version>0.30.0</commonmark.version>
		<owasp-html-sanitizer.version>20260924.2</owasp-html-sanitizer.version>
		<openhtmltopdf.version>1.1.87</openhtmltopdf.version>
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
		<!-- Certificado: HTML (Thymeleaf) para PDF -->
		<dependency>
			<groupId>io.github.openhtmltopdf</groupId>
			<artifactId>openhtmltopdf-pdfbox</artifactId>
			<version>${openhtmltopdf.version}</version>
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
  forum:
    # Perguntas e respostas por aluno dentro da janela (o instrutor não tem limite)
    mensagens-por-janela: 20
    janela: PT1H
    anexos-por-mensagem: 3
  alertas:
    # Destinatário dos alertas do worker (jobs com erro, disco); em branco, desligados
    email: ${ALERTA_EMAIL:}
    disco-minimo-percentual: 15
    diretorio-disco: /
    repeticao-disco: PT6H
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/FilaService.java`:

`````java
package br.com.celke.cursos.shared.fila;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
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

    /**
     * Estende o lease de um job em processamento: um job longo (como uma transcodificação) chama a
     * cada minuto para não ser pego por outro worker. Se o worker cair, o lease vence e o job volta.
     */
    public void renovar(Job job, Instant agora) {
        jdbc.sql("""
                        UPDATE jobs SET reservado_ate = :reservadoAte, atualizado_em = :agora
                         WHERE id = :id AND status = 'PROCESSANDO'
                        """)
                .param("id", job.id())
                .param("reservadoAte", utc(agora.plus(props.lease())))
                .param("agora", utc(agora))
                .update();
    }

    /** Falha com nova tentativa depois do backoff; sem tentativas restantes, o job vai para ERRO. */
    public void falhar(Job job, String erro, Instant agora) {
        if (job.ultimaTentativa()) {
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

    /** Jobs que foram para ERRO depois do instante, menos os do tipo excluído (alerta de operação). */
    public List<FalhaJob> falhasDesde(Instant desde, String tipoExcluido) {
        return jdbc.sql("""
                        SELECT id, tipo, ultimo_erro, atualizado_em FROM jobs
                         WHERE status = 'ERRO' AND atualizado_em > :desde AND tipo <> :excluido
                         ORDER BY atualizado_em, id
                        """)
                .param("desde", utc(desde))
                .param("excluido", tipoExcluido)
                .query((rs, n) -> new FalhaJob(
                        rs.getLong("id"),
                        rs.getString("tipo"),
                        rs.getString("ultimo_erro"),
                        rs.getObject("atualizado_em", OffsetDateTime.class).toInstant()))
                .list();
    }

    public record FalhaJob(long id, String tipo, String erro, Instant em) {}

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
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

    public static final String TIPO_JOB = "EMAIL";

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

`infra/compose.prod.yml`:

`````yaml
# Stack de um ambiente dos servidores (produção ou homologação): API, worker, PostgreSQL e Garage.
# Imagens publicadas no GHCR com a tag do commit (TAG); nenhuma porta publicada. O Caddy (projeto
# próprio, em caddy/) chega à API e ao Garage pela rede plataforma-cursos-<AMBIENTE>, pelos apelidos
# <AMBIENTE>-api e <AMBIENTE>-garage. Todas as variáveis vêm do .env do ambiente.
#
#   Produção:    docker compose -p plataforma-cursos-producao -f compose.prod.yml up -d --wait
#   Homologação: docker compose -p plataforma-cursos-homolog -f compose.prod.yml -f compose.homolog.yml up -d --wait

# Configuração compartilhada pela API e pelo worker
x-app: &app
  DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:?defina POSTGRES_DB}
  DB_USERNAME: ${POSTGRES_USER:?defina POSTGRES_USER}
  DB_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD}
  JWT_SEGREDO: ${JWT_SEGREDO:?defina JWT_SEGREDO}
  AUTH_ORIGENS: ${AUTH_ORIGENS:?defina AUTH_ORIGENS}
  FRONT_ALUNO_URL: ${FRONT_ALUNO_URL:?defina FRONT_ALUNO_URL}
  S3_ENDPOINT: http://garage:3900
  S3_ENDPOINT_PUBLICO: ${S3_ENDPOINT_PUBLICO:?defina S3_ENDPOINT_PUBLICO}
  S3_BUCKET: ${S3_BUCKET:?defina S3_BUCKET}
  S3_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY}
  S3_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY}
  S3_CORS_ORIGENS: ${S3_CORS_ORIGENS:?defina S3_CORS_ORIGENS}
  SMTP_HOST: ${SMTP_HOST:?defina SMTP_HOST}
  SMTP_PORT: ${SMTP_PORT:-587}
  SMTP_USERNAME: ${SMTP_USERNAME:-}
  SMTP_PASSWORD: ${SMTP_PASSWORD:-}
  SMTP_AUTH: ${SMTP_AUTH:-true}
  SMTP_STARTTLS: ${SMTP_STARTTLS:-true}
  EMAIL_REMETENTE: ${EMAIL_REMETENTE:?defina EMAIL_REMETENTE}
  LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs
  # Alertas do worker por e-mail (jobs com erro, disco quase cheio); em branco, desligados
  ALERTA_EMAIL: ${ALERTA_EMAIL:-}

# Logs do Docker com rotação: no máximo 5 arquivos de 10 MB por container
x-logging: &logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "5"

services:
  api:
    logging: *logging
    image: ${IMAGEM_API:-ghcr.io/celkecursos/plataforma-cursos-api}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      <<: *app
      JAVA_TOOL_OPTIONS: -Xmx512m
    depends_on:
      postgres:
        condition: service_healthy
      garage:
        condition: service_healthy
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-api
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 5s
      timeout: 3s
      start_period: 30s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 1G

  worker:
    logging: *logging
    image: ${IMAGEM_WORKER:-ghcr.io/celkecursos/plataforma-cursos-worker}:${TAG:?defina TAG}
    restart: unless-stopped
    # No deploy, o job em andamento tem 30 s para terminar; uma conversão interrompida volta para a fila
    # quando o lease vence (o worker o renova a cada minuto enquanto converte)
    stop_grace_period: 30s
    environment:
      <<: *app
      JAVA_TOOL_OPTIONS: -Xmx768m
    depends_on:
      # As migrations são aplicadas pela API; o worker sobe depois dela
      api:
        condition: service_healthy
    healthcheck:
      # O worker regrava este arquivo a cada 10 s (não tem servidor HTTP para health check)
      test: ["CMD-SHELL", "find /tmp/worker-batimento -mmin -1 | grep -q ."]
      interval: 10s
      timeout: 3s
      start_period: 30s
      retries: 3
    deploy:
      resources:
        limits:
          # A transcodificação (ffmpeg) não pode tomar a CPU toda da VPS
          cpus: "1.5"
          memory: 2G

  postgres:
    logging: *logging
    image: postgres:18.6-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB:?defina POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER:?defina POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD}
    volumes:
      - postgres:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 1G

  garage:
    logging: *logging
    image: dxflrs/garage:v2.4.1
    restart: unless-stopped
    command: ["/garage", "server", "--single-node", "--default-bucket"]
    environment:
      GARAGE_RPC_SECRET: ${GARAGE_RPC_SECRET:?defina GARAGE_RPC_SECRET}
      GARAGE_ADMIN_TOKEN: ${GARAGE_ADMIN_TOKEN:?defina GARAGE_ADMIN_TOKEN}
      GARAGE_DEFAULT_BUCKET: ${S3_BUCKET:?defina S3_BUCKET}
      GARAGE_DEFAULT_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY}
      GARAGE_DEFAULT_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY}
    volumes:
      - ./garage/garage.toml:/etc/garage.toml:ro
      - garage:/var/lib/garage
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-garage
    healthcheck:
      test: ["CMD", "/garage", "status"]
      interval: 5s
      timeout: 5s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 768M

networks:
  default:
    name: plataforma-cursos-${AMBIENTE:?defina AMBIENTE}

volumes:
  postgres:
  garage:
`````

`infra/caddy/compose.yml`:

`````yaml
# Caddy da VPS: único processo nas portas 80/443, ligado às redes da produção e da homologação.
# Sobe depois das duas stacks (as redes são criadas por elas).
#   docker compose up -d
name: plataforma-cursos-caddy

services:
  caddy:
    image: caddy:2.11.7-alpine
    restart: unless-stopped
    environment:
      ACME_EMAIL: ${ACME_EMAIL:?defina ACME_EMAIL}
      PRODUCAO_HOST_ALUNO: ${PRODUCAO_HOST_ALUNO:?defina PRODUCAO_HOST_ALUNO}
      PRODUCAO_HOST_ADM: ${PRODUCAO_HOST_ADM:?defina PRODUCAO_HOST_ADM}
      PRODUCAO_HOST_MEDIA: ${PRODUCAO_HOST_MEDIA:?defina PRODUCAO_HOST_MEDIA}
      HOMOLOG_HOST_ALUNO: ${HOMOLOG_HOST_ALUNO:?defina HOMOLOG_HOST_ALUNO}
      HOMOLOG_HOST_ADM: ${HOMOLOG_HOST_ADM:?defina HOMOLOG_HOST_ADM}
      HOMOLOG_HOST_MEDIA: ${HOMOLOG_HOST_MEDIA:?defina HOMOLOG_HOST_MEDIA}
      ADM_IPS: ${ADM_IPS:?defina ADM_IPS}
      HOMOLOG_IPS: ${HOMOLOG_IPS:?defina HOMOLOG_IPS}
    ports:
      - "80:80"
      - "443:443"
      - "443:443/udp"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - ./sites:/etc/caddy/sites:ro
      - dados:/data
      - config:/config
    networks:
      - producao
      - homolog
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "5"
    deploy:
      resources:
        limits:
          memory: 128M

networks:
  producao:
    external: true
    name: plataforma-cursos-producao
  homolog:
    external: true
    name: plataforma-cursos-homolog

volumes:
  dados:
  config:
`````

`infra/caddy/Caddyfile`:

`````caddyfile
# Porta de entrada da plataforma. Um Caddy só atende os dois ambientes da VPS: cada site está em
# sites/<ambiente>.caddy e chega à API e ao Garage do seu ambiente pelos apelidos <ambiente>-api e
# <ambiente>-garage. Com "https://..." o Caddy emite o certificado sozinho; com "http://..." atende
# sem TLS (e2e).
{
	admin off
	email {$ACME_EMAIL:nao-configurado@celke.uno}
}

# Headers de segurança em todas as respostas
(seguranca) {
	header {
		X-Content-Type-Options nosniff
		Referrer-Policy strict-origin-when-cross-origin
		# Respostas só podem ser carregadas por páginas do mesmo site (aluno, adm e mídia são subdomínios dele)
		Cross-Origin-Resource-Policy same-site
		Strict-Transport-Security "max-age=31536000"
		-Server
	}
}

# Só a API e o health check passam; o resto (Swagger, outros endpoints do Actuator) não sai daqui.
# O X-Forwarded-For que o cliente mandar é substituído pelo IP real da conexão.
# Argumento: o ambiente (producao, homolog, e2e).
(api) {
	@api path /api/* /actuator/health /actuator/health/*
	handle @api {
		reverse_proxy {args[0]}-api:8080 {
			# Durante o deploy a API reinicia: segura a requisição em vez de responder 502
			lb_try_duration 30s
		}
	}
	handle {
		respond 404
	}
}

# Como (api), mas também com a documentação (Swagger): só na homologação
(api_com_docs) {
	@api path /api/* /actuator/health /actuator/health/* /v3/api-docs /v3/api-docs/* /swagger-ui.html /swagger-ui/*
	handle @api {
		reverse_proxy {args[0]}-api:8080 {
			lb_try_duration 30s
		}
	}
	handle {
		respond 404
	}
}

# Arquivos do bucket por URL pré-assinada (o host faz parte da assinatura e é repassado como veio)
(midia) {
	reverse_proxy {args[0]}-garage:3900
}

# Painel administrativo: só os IPs de ADM_IPS (separados por espaço)
(somente_ips_adm) {
	@fora not remote_ip {$ADM_IPS}
	respond @fora "Acesso restrito." 403
}

# Homologação: só os IPs de HOMOLOG_IPS e fora dos buscadores
(homolog) {
	header X-Robots-Tag "noindex, nofollow"
	@fora not remote_ip {$HOMOLOG_IPS}
	respond @fora "Acesso restrito." 403
}

import sites/*.caddy
`````

`infra/scripts/gerar-env-servidor.sh`:

`````bash
#!/usr/bin/env bash
# Gera o .env de um ambiente da VPS com segredos aleatórios. Hosts seguem os subdomínios de
# celke.uno (homolog-* na homologação). SMTP e remetente ficam para preencher com os dados da Iagente.
# Não sobrescreve um arquivo existente.
#
#   gerar-env-servidor.sh producao /opt/plataforma-cursos/producao/.env
#   gerar-env-servidor.sh homolog  /opt/plataforma-cursos/homolog/.env
set -euo pipefail

AMBIENTE=${1:?uso: gerar-env-servidor.sh <producao|homolog> <arquivo>}
DESTINO=${2:?uso: gerar-env-servidor.sh <producao|homolog> <arquivo>}

case "$AMBIENTE" in
  producao) PREFIXO="" ;;
  homolog) PREFIXO="homolog-" ;;
  *) echo "Ambiente inválido: $AMBIENTE (use producao ou homolog)" >&2; exit 1 ;;
esac

if [[ -e "$DESTINO" ]]; then
  echo "$DESTINO já existe; nada foi alterado." >&2
  exit 1
fi

hex() { openssl rand -hex "$1"; }

ALUNO="https://${PREFIXO}aluno.celke.uno"
ADM="https://${PREFIXO}adm.celke.uno"
MEDIA="https://${PREFIXO}media.celke.uno"

umask 077
cat > "$DESTINO" <<ENV
# Ambiente: $AMBIENTE
AMBIENTE=$AMBIENTE
# Tag (SHA do commit) das imagens; o deploy grava a tag de cada versão
TAG=

POSTGRES_DB=plataforma_cursos
POSTGRES_USER=plataforma_cursos
POSTGRES_PASSWORD=$(hex 24)

JWT_SEGREDO=$(openssl rand -base64 48)
AUTH_ORIGENS=$ALUNO,$ADM
FRONT_ALUNO_URL=$ALUNO

GARAGE_RPC_SECRET=$(hex 32)
GARAGE_ADMIN_TOKEN=$(hex 32)
S3_BUCKET=midia
S3_ACCESS_KEY=GK$(hex 12)
S3_SECRET_KEY=$(hex 32)
S3_ENDPOINT_PUBLICO=$MEDIA
S3_CORS_ORIGENS=$ALUNO,$ADM

# SMTP da Iagente (dados do painel)
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_AUTH=true
SMTP_STARTTLS=true
EMAIL_REMETENTE=nao-responda@celke.uno

# Alertas do worker (jobs com erro, disco quase cheio); em branco, desligados
ALERTA_EMAIL=
ENV

chmod 600 "$DESTINO"
echo "$DESTINO criado. Preencha SMTP_HOST, SMTP_USERNAME e SMTP_PASSWORD."
`````

`.github/workflows/pipeline.yml`:

`````yaml
# Testes, build das imagens e e2e em todo PR para homolog e main. Depois do merge (push), refaz tudo
# com o código exato do commit, publica no GHCR as imagens que passaram no e2e (tag = SHA) e implanta:
# push na homolog → homologação; push na main → produção. Os deploys só rodam com a variável do
# repositório DEPLOY_ATIVO=true (criada quando a VPS estiver pronta); sem ela, são pulados.
name: Pipeline

on:
  pull_request:
    branches: [homolog, main]
  push:
    branches: [homolog, main]
  workflow_dispatch:

concurrency:
  group: pipeline-${{ github.ref }}
  # Só PRs cancelam execuções antigas: um deploy nunca é interrompido no meio
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

permissions:
  contents: read

env:
  COMPOSE_E2E: docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env

jobs:
  testes:
    name: Testes (unitários e integração)
    runs-on: ubuntu-latest
    timeout-minutes: 20
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "25"
          cache: maven

      # A transcodificação de vídeo é testada com o ffmpeg de verdade
      - name: Instalar ffmpeg
        run: sudo apt-get update -q && sudo apt-get install -y -q --no-install-recommends ffmpeg

      # Unitários, integração (Testcontainers), cobertura mínima e formatação
      - name: Verificar
        run: ./mvnw -B verify

      - name: Relatórios dos testes
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: relatorios-testes
          path: |
            backend/target/surefire-reports
            backend/target/failsafe-reports

      - name: Relatório de cobertura
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: cobertura
          path: backend/target/site/jacoco
          if-no-files-found: ignore

  e2e:
    name: e2e (stack completa)
    needs: testes
    runs-on: ubuntu-latest
    timeout-minutes: 30
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "25"
          cache: maven

      - uses: docker/setup-buildx-action@v4

      # As mesmas imagens que vão para os servidores, com cache de camadas entre execuções
      - name: Imagem da API
        uses: docker/build-push-action@v7
        with:
          context: backend
          target: api
          tags: plataforma-cursos-api:e2e
          load: true
          # Liga o pacote do GHCR ao repositório (mesmas permissões)
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=api
          cache-to: type=gha,mode=max,scope=api

      - name: Imagem do worker
        uses: docker/build-push-action@v7
        with:
          context: backend
          target: worker
          tags: plataforma-cursos-worker:e2e
          load: true
          # Liga o pacote do GHCR ao repositório (mesmas permissões)
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=worker
          cache-to: type=gha,mode=max,scope=worker

      # Vulnerabilidades críticas com correção disponível nas imagens (sistema e dependências Java)
      - name: Varredura da imagem da API (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-api:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Varredura da imagem do worker (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-worker:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Subir a stack de e2e
        run: $COMPOSE_E2E up -d --no-build --wait

      - name: Jornadas e2e
        working-directory: backend
        run: ./mvnw -B verify -Pe2e

      # Só depois do merge, e só se o e2e passou: as imagens publicadas são exatamente as testadas
      - name: Publicar imagens no GHCR
        if: github.event_name == 'push'
        env:
          REGISTRO: ghcr.io/${{ github.repository_owner }}
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u "${{ github.actor }}" --password-stdin
          for imagem in api worker; do
            docker tag "plataforma-cursos-$imagem:e2e" "$REGISTRO/plataforma-cursos-$imagem:$GITHUB_SHA"
            docker push "$REGISTRO/plataforma-cursos-$imagem:$GITHUB_SHA"
          done

      - name: Logs da stack
        if: failure()
        run: $COMPOSE_E2E logs --no-color > stack-e2e.log

      - name: Relatórios do e2e
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: e2e-falha
          path: |
            stack-e2e.log
            backend/target/failsafe-reports

      - name: Derrubar a stack de e2e
        if: always()
        run: $COMPOSE_E2E down -v

  deploy-homolog:
    name: Homologação
    needs: e2e
    if: github.event_name == 'push' && github.ref == 'refs/heads/homolog' && vars.DEPLOY_ATIVO == 'true'
    uses: ./.github/workflows/deploy.yml
    with:
      ambiente: homolog
    secrets: inherit

  deploy-producao:
    name: Produção
    needs: e2e
    if: github.event_name == 'push' && github.ref == 'refs/heads/main' && vars.DEPLOY_ATIVO == 'true'
    uses: ./.github/workflows/deploy.yml
    with:
      ambiente: producao
    secrets: inherit
`````

`README.md`:

`````markdown
# Plataforma de Cursos

Plataforma de cursos online com área do aluno, painel administrativo e site, desenvolvida passo a passo
como tutorial.

- **Backend:** Java 25, Spring Boot 4.1, PostgreSQL 18
- **Vídeos:** armazenamento S3 (Garage) com conversão para HLS via ffmpeg
- **Frontend:** React com [`@celke/kole-react`](https://github.com/celkecursos/kole-react)
- **Infraestrutura:** Docker, Caddy, GitHub Actions, deploy em VPS

## Branches

| Branch | Uso |
|---|---|
| `main` | Produção |
| `homolog` | Homologação |
| `feature/nome` | Nova funcionalidade (PR para `homolog`) |
| `fix/nome` | Correção (PR para `homolog`) |
| `hotfix/nome` | Correção urgente a partir da `main` |

## Documentação

- [Plano de implementação](docs/plano-implementacao.md)
- [Roteiro da VPS](docs/vps.md)
- [Operação: backup, alertas e segurança](docs/operacao.md)
- [Roteiro do tutorial (capítulos)](docs/capitulos.md)
- [Prompts para recriar o projeto passo a passo](docs/prompts/README.md)
`````

### 2. Alertas do worker

`backend/src/main/java/br/com/celke/cursos/shared/operacao/AlertasProperties.java`:

`````java
package br.com.celke.cursos.shared.operacao;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Alertas de operação enviados pelo worker.
 *
 * @param email destinatário; em branco, os alertas ficam desligados
 * @param discoMinimoPercentual espaço livre abaixo do qual o disco gera alerta
 * @param diretorioDisco caminho cujo sistema de arquivos é medido (no container, o disco da VPS)
 * @param repeticaoDisco intervalo mínimo entre dois alertas de disco enquanto o espaço continua baixo
 */
@Validated
@ConfigurationProperties("app.alertas")
public record AlertasProperties(
        String email,
        @Min(1) @Max(99) int discoMinimoPercentual,
        @NotNull Path diretorioDisco,
        @NotNull Duration repeticaoDisco) {

    public boolean ligados() {
        return email != null && !email.isBlank();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/operacao/MedidorDisco.java`:

`````java
package br.com.celke.cursos.shared.operacao;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Espaço do sistema de arquivos de um caminho (separado para os alertas serem testáveis sem disco cheio). */
@Component
public class MedidorDisco {

    public Espaco medir(Path caminho) {
        try {
            FileStore loja = Files.getFileStore(caminho);
            return new Espaco(loja.getUsableSpace(), loja.getTotalSpace());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record Espaco(long livre, long total) {

        /** Percentual livre, arredondado para baixo; disco de tamanho zero conta como cheio. */
        public int percentualLivre() {
            return total <= 0 ? 0 : (int) (livre * 100 / total);
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/operacao/AlertasOperacao.java`:

`````java
package br.com.celke.cursos.shared.operacao;

import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.fila.FilaService;
import br.com.celke.cursos.shared.fila.FilaService.FalhaJob;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Alertas por e-mail enviados pelo worker a cada 15 minutos: jobs que foram para ERRO (uma conversão de
 * vídeo que desistiu, por exemplo) e disco com pouco espaço livre. Falha de envio de e-mail não gera
 * alerta por e-mail (iria pela mesma fila que falhou): fica só no log.
 */
@Component
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
@EnableConfigurationProperties(AlertasProperties.class)
public class AlertasOperacao {

    static final Duration INTERVALO = Duration.ofMinutes(15);
    private static final Logger LOG = LoggerFactory.getLogger(AlertasOperacao.class);

    private final FilaService fila;
    private final EmailService emailService;
    private final MedidorDisco medidorDisco;
    private final AlertasProperties props;
    private final Clock clock;

    private Instant ultimaVerificacao;
    private Instant ultimoAlertaDisco;

    public AlertasOperacao(
            FilaService fila,
            EmailService emailService,
            MedidorDisco medidorDisco,
            AlertasProperties props,
            Clock clock) {
        this.fila = fila;
        this.emailService = emailService;
        this.medidorDisco = medidorDisco;
        this.props = props;
        this.clock = clock;
        this.ultimaVerificacao = clock.instant().minus(INTERVALO);
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    public void verificar() {
        if (!props.ligados()) {
            return;
        }
        Instant agora = clock.instant();
        verificarFalhas(agora);
        verificarDisco(agora);
        ultimaVerificacao = agora;
    }

    private void verificarFalhas(Instant agora) {
        List<FalhaJob> falhas = fila.falhasDesde(ultimaVerificacao, EmailService.TIPO_JOB);
        if (falhas.isEmpty()) {
            return;
        }
        String detalhes = falhas.stream()
                .map(f -> "Job %d (%s): %s".formatted(f.id(), f.tipo(), resumo(f.erro())))
                .collect(Collectors.joining("\n"));
        LOG.warn("{} job(s) foram para ERRO desde {}", falhas.size(), ultimaVerificacao);
        enviar("%d job(s) com erro no worker".formatted(falhas.size()), detalhes);
    }

    private void verificarDisco(Instant agora) {
        MedidorDisco.Espaco espaco = medidorDisco.medir(props.diretorioDisco());
        if (espaco.percentualLivre() >= props.discoMinimoPercentual()) {
            ultimoAlertaDisco = null;
            return;
        }
        if (ultimoAlertaDisco != null
                && ultimoAlertaDisco.plus(props.repeticaoDisco()).isAfter(agora)) {
            return;
        }
        ultimoAlertaDisco = agora;
        LOG.warn("Disco com {}% livre", espaco.percentualLivre());
        enviar(
                "Disco com %d%% livre".formatted(espaco.percentualLivre()),
                "Livre: %d MB de %d MB (alerta abaixo de %d%%)."
                        .formatted(
                                espaco.livre() / (1024 * 1024),
                                espaco.total() / (1024 * 1024),
                                props.discoMinimoPercentual()));
    }

    private void enviar(String titulo, String detalhes) {
        emailService.enviar(
                props.email(),
                "[Plataforma de Cursos] " + titulo,
                "alerta-operacao",
                Map.of("titulo", titulo, "detalhes", detalhes));
    }

    private static String resumo(String erro) {
        if (erro == null) {
            return "(sem mensagem)";
        }
        String linha = erro.strip().replaceAll("\\s+", " ");
        return linha.length() <= 300 ? linha : linha.substring(0, 300) + "…";
    }
}
`````

`backend/src/main/resources/templates/email/alerta-operacao.html`:

`````html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <title>Alerta de operação</title>
</head>
<body style="margin:0;padding:24px;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
<div style="max-width:640px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px;">
    <h1 style="margin:0 0 16px;font-size:20px;color:#b91c1c;" th:text="${titulo}">Alerta</h1>
    <p style="white-space:pre-line;font-family:monospace;font-size:13px;" th:text="${detalhes}">Detalhes</p>
    <p style="color:#6b7280;font-size:13px;">Enviado pelo worker da Plataforma de Cursos. Veja os logs com
        <code>docker compose logs worker</code> no servidor.</p>
</div>
</body>
</html>
`````

### 3. Backup e restauração

`infra/scripts/backup.sh`:

`````bash
#!/usr/bin/env bash
# Backup diário de um ambiente para fora da VPS, com restic (repositório S3 externo: Cloudflare R2 ou
# Backblaze B2): dump do PostgreSQL e os arquivos do Garage, com a cópia consistente dos metadados.
# Retenção: 7 diários, 4 semanais e 6 mensais.
#
#   backup.sh producao
#   backup.sh homolog
#
# Configuração em /opt/plataforma-cursos/backup.env (permissão 600): RESTIC_REPOSITORY,
# RESTIC_PASSWORD, AWS_ACCESS_KEY_ID e AWS_SECRET_ACCESS_KEY. Guarde a RESTIC_PASSWORD também fora da
# VPS: sem ela o backup não abre.
# Teste local: ENV_BACKUP=<arquivo> PROJETO=<projeto compose> RESTIC_EXTRA="-v <volume>:/repo".
set -euo pipefail

AMBIENTE="${1:?uso: backup.sh <producao|homolog>}"
BASE="${BASE:-/opt/plataforma-cursos}"
ENV_BACKUP="${ENV_BACKUP:-$BASE/backup.env}"
PROJETO="${PROJETO:-plataforma-cursos-$AMBIENTE}"
RESTIC_IMAGEM="restic/restic:0.19.1"
read -r -a RESTIC_EXTRA <<< "${RESTIC_EXTRA:-}"

[[ -r "$ENV_BACKUP" ]] || { echo "Arquivo de configuração não encontrado: $ENV_BACKUP" >&2; exit 1; }

container() {
    local id
    id=$(docker ps -q --filter "label=com.docker.compose.project=$PROJETO" --filter "label=com.docker.compose.service=$1")
    [[ -n "$id" ]] || { echo "Serviço $1 do projeto $PROJETO não está rodando." >&2; exit 1; }
    echo "$id"
}

# restic num container; volumes extras antes de "--"
restic() {
    local volumes=()
    while [[ $# -gt 0 && "$1" != "--" ]]; do volumes+=("$1"); shift; done
    shift
    docker run --rm -i --env-file "$ENV_BACKUP" --hostname "$AMBIENTE" \
        -v plataforma-cursos-restic-cache:/root/.cache/restic "${RESTIC_EXTRA[@]}" "${volumes[@]}" \
        "$RESTIC_IMAGEM" "$@"
}

POSTGRES=$(container postgres)
GARAGE=$(container garage)
VOLUME_GARAGE="${PROJETO}_garage"

echo "==> Repositório"
restic -- cat config >/dev/null 2>&1 || restic -- init

echo "==> PostgreSQL ($AMBIENTE)"
docker exec "$POSTGRES" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
    | restic -- backup --stdin --stdin-filename postgres.dump --tag "$AMBIENTE" --tag postgres

echo "==> Garage ($AMBIENTE): snapshot dos metadados e arquivos"
docker exec "$GARAGE" /garage meta snapshot >/dev/null
# Só o snapshot mais recente vai para o backup (os anteriores ocupariam espaço à toa)
docker run --rm -v "$VOLUME_GARAGE:/garage" alpine:3.22 sh -c \
    'cd /garage/meta/snapshots && ls -1 | sort | head -n -1 | xargs -r rm -rf'
restic -v "$VOLUME_GARAGE:/garage:ro" -- backup /garage \
    --exclude '/garage/meta/db.sqlite' --exclude '/garage/meta/db.sqlite-*' \
    --tag "$AMBIENTE" --tag garage

echo "==> Retenção"
restic -- forget --tag "$AMBIENTE" --group-by host,tags \
    --keep-daily 7 --keep-weekly 4 --keep-monthly 6 --prune

echo "Backup de $AMBIENTE concluído."
`````

`infra/scripts/restaurar.sh`:

`````bash
#!/usr/bin/env bash
# Restaura um ambiente a partir do backup restic (o mais recente, ou o snapshot informado de cada
# parte). Para a API e o worker, recria o banco a partir do dump, troca os arquivos do Garage pelos do
# backup (com os metadados do snapshot) e sobe tudo de novo. Apaga os dados atuais do ambiente.
#
#   restaurar.sh homolog
#   restaurar.sh producao <snapshot-postgres> <snapshot-garage>
#
# Mesma configuração do backup.sh (backup.env, PROJETO e RESTIC_EXTRA para teste local).
set -euo pipefail

AMBIENTE="${1:?uso: restaurar.sh <producao|homolog> [snapshot-postgres] [snapshot-garage]}"
SNAP_POSTGRES="${2:-latest}"
SNAP_GARAGE="${3:-latest}"
BASE="${BASE:-/opt/plataforma-cursos}"
ENV_BACKUP="${ENV_BACKUP:-$BASE/backup.env}"
PROJETO="${PROJETO:-plataforma-cursos-$AMBIENTE}"
RESTIC_IMAGEM="restic/restic:0.19.1"
read -r -a RESTIC_EXTRA <<< "${RESTIC_EXTRA:-}"

if [[ "${CONFIRMAR:-}" != "sim" ]]; then
    read -r -p "Isto apaga os dados atuais de $AMBIENTE. Digite o nome do ambiente para continuar: " resposta
    [[ "$resposta" == "$AMBIENTE" ]] || { echo "Cancelado."; exit 1; }
fi

container() {
    docker ps -aq --filter "label=com.docker.compose.project=$PROJETO" --filter "label=com.docker.compose.service=$1"
}

restic() {
    local volumes=()
    while [[ $# -gt 0 && "$1" != "--" ]]; do volumes+=("$1"); shift; done
    shift
    docker run --rm -i --env-file "$ENV_BACKUP" --hostname "$AMBIENTE" \
        -v plataforma-cursos-restic-cache:/root/.cache/restic "${RESTIC_EXTRA[@]}" "${volumes[@]}" \
        "$RESTIC_IMAGEM" "$@"
}

POSTGRES=$(container postgres)
GARAGE=$(container garage)
API=$(container api)
WORKER=$(container worker)
VOLUME_GARAGE="${PROJETO}_garage"
[[ -n "$POSTGRES" && -n "$GARAGE" ]] || { echo "PostgreSQL ou Garage do projeto $PROJETO não encontrado." >&2; exit 1; }

# Se algo falhar no meio, API e worker ficam parados de propósito: subir com o banco pela metade é pior
trap 'echo "Restauração interrompida. API e worker continuam parados; corrija e rode de novo." >&2' ERR

echo "==> Parando API e worker"
docker stop $API $WORKER >/dev/null

echo "==> PostgreSQL: recriando a partir do dump ($SNAP_POSTGRES)"
docker exec "$POSTGRES" sh -c 'dropdb -U "$POSTGRES_USER" --force --if-exists "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
if [[ "$SNAP_POSTGRES" == "latest" ]]; then
    restic -- dump --tag "$AMBIENTE,postgres" latest /postgres.dump
else
    restic -- dump "$SNAP_POSTGRES" /postgres.dump
fi | docker exec -i "$POSTGRES" sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error'

echo "==> Garage: trocando os arquivos pelos do backup ($SNAP_GARAGE)"
docker stop "$GARAGE" >/dev/null
docker run --rm -v "$VOLUME_GARAGE:/garage" alpine:3.22 sh -c 'rm -rf /garage/meta /garage/data'
if [[ "$SNAP_GARAGE" == "latest" ]]; then
    restic -v "$VOLUME_GARAGE:/garage" -- restore latest --tag "$AMBIENTE,garage" --target /
else
    restic -v "$VOLUME_GARAGE:/garage" -- restore "$SNAP_GARAGE" --target /
fi
# Os metadados voltam do snapshot consistente gravado pelo backup
docker run --rm -v "$VOLUME_GARAGE:/garage" alpine:3.22 sh -c \
    'ultimo=$(ls -1 /garage/meta/snapshots | sort | tail -n 1) && cp "/garage/meta/snapshots/$ultimo/db.sqlite" /garage/meta/db.sqlite'
docker start "$GARAGE" >/dev/null

echo "==> Subindo API e worker"
docker start $API $WORKER >/dev/null
echo "Restauração de $AMBIENTE concluída. Confira o health check e uma aula com vídeo."
`````

### 4. Documentação de operação

`docs/operacao.md`:

`````markdown
# Operação

Rotina da plataforma depois que a VPS está no ar ([roteiro da VPS](vps.md)): backup e restauração,
monitoramento e alertas, logs, espaço em disco, saída para um S3 externo e a revisão de segurança.

## 1. Backup diário

`infra/scripts/backup.sh <ambiente>` guarda, com o [restic](https://restic.net), num repositório S3
**fora da VPS** (Cloudflare R2 ou Backblaze B2):

- o dump do PostgreSQL (`pg_dump --format=custom`), por stdin, sem arquivo temporário no disco;
- os arquivos do Garage, com o snapshot consistente dos metadados (`garage meta snapshot`); o SQLite em
  uso fica de fora.

Retenção: 7 diários, 4 semanais e 6 mensais por ambiente. O restic cifra tudo com a `RESTIC_PASSWORD`.

### Configuração (uma vez, na VPS)

1. Crie um bucket privado no R2 (ou B2) só para os backups e uma chave com acesso apenas a ele.
2. Na VPS, como `deploy`:

   ```bash
   sudo install -o root -g root -m 755 /tmp/backup.sh /usr/local/bin/plataforma-cursos-backup
   install -m 600 /dev/null /opt/plataforma-cursos/backup.env
   nano /opt/plataforma-cursos/backup.env
   ```

   ```bash
   RESTIC_REPOSITORY=s3:https://<conta>.r2.cloudflarestorage.com/plataforma-cursos-backup
   RESTIC_PASSWORD=          # openssl rand -base64 48 — guarde também no gerenciador de senhas
   AWS_ACCESS_KEY_ID=
   AWS_SECRET_ACCESS_KEY=
   ```

3. Agendamento (`crontab -e` do `deploy`), fora do horário de aula:

   ```cron
   30 3 * * * /usr/local/bin/plataforma-cursos-backup producao >> /opt/plataforma-cursos/backup.log 2>&1
   45 3 * * * /usr/local/bin/plataforma-cursos-backup homolog  >> /opt/plataforma-cursos/backup.log 2>&1
   ```

> Sem a `RESTIC_PASSWORD` o backup não abre. Ela fica em dois lugares: no `backup.env` da VPS e no
> gerenciador de senhas do responsável.

## 2. Restauração

`infra/scripts/restaurar.sh <ambiente> [snapshot-postgres] [snapshot-garage]` para a API e o worker,
recria o banco a partir do dump, troca os arquivos do Garage pelos do backup (com os metadados do
snapshot) e sobe tudo de novo. Pede o nome do ambiente para confirmar (ou `CONFIRMAR=sim`).

```bash
docker run --rm --env-file /opt/plataforma-cursos/backup.env restic/restic:0.19.1 snapshots   # escolher
restaurar.sh homolog                                                                         # o mais recente
```

Se algo falhar no meio, a API e o worker **continuam parados de propósito** (subir com o banco pela
metade é pior): corrija e rode de novo.

### Teste de restauração (feito e repetido a cada trimestre)

Testado na stack de e2e, com um repositório restic local:

```bash
export MSYS_NO_PATHCONV=1 ENV_BACKUP=<backup.env com RESTIC_REPOSITORY=/repo> \
       PROJETO=plataforma-cursos-e2e RESTIC_EXTRA="-v restic-teste:/repo"
infra/scripts/backup.sh e2e
# estrago: UPDATE cursos SET titulo = 'ESTRAGADO'; e apagar /garage/data do volume
CONFIRMAR=sim infra/scripts/restaurar.sh e2e
```

Resultado: os títulos dos 7 cursos voltaram, os 11 objetos do bucket baixaram pela API S3 e a API e o
worker subiram saudáveis. O procedimento trimestral repete o teste na **homologação** com o backup da
produção mais recente.

## 3. Monitoramento e alertas

| O quê | Como |
|---|---|
| Site fora do ar | Monitor externo (UptimeRobot ou Better Stack, plano gratuito) a cada 5 minutos em `https://aluno.celke.uno/actuator/health` e `https://cursos.celke.uno`, com aviso por e-mail e celular |
| Job que desistiu (conversão de vídeo, por exemplo) | O worker verifica a cada 15 minutos os jobs que foram para `ERRO` e envia um e-mail para `ALERTA_EMAIL` com o resumo |
| Disco quase cheio | O mesmo verificador do worker mede o disco da VPS (o sistema de arquivos do container é o do host) e alerta abaixo de 15% livre, repetindo a cada 6 horas enquanto não melhorar |
| Container parado | `restart: unless-stopped` e o healthcheck de cada serviço; o deploy só conclui com tudo saudável |

`ALERTA_EMAIL` fica no `.env` de cada ambiente; em branco, os alertas do worker ficam desligados.
Falha no envio de e-mail não gera alerta por e-mail (iria pela mesma fila): aparece no log do worker.

## 4. Logs e limpeza

- **Logs do Docker** com rotação em todos os serviços (`json-file`, 5 arquivos de 10 MB por container):
  no máximo ~250 MB por ambiente. Os logs da API e do worker são JSON (ECS) com o id de correlação.
- **Uploads abandonados** (`uploads/` do bucket): a regra de expiração do bucket apaga em 2 dias, e os
  uploads em partes não concluídos em 1 dia. Os envios aceitos saem de `uploads/` na confirmação.
- **Log de acesso aos vídeos**: o worker apaga todo dia o que passou de 180 dias.
- **Dados da homologação**: quando acumular lixo de testes,
  `docker compose -p plataforma-cursos-homolog -f compose.prod.yml -f compose.homolog.yml down -v`
  e o próximo deploy da `homolog` recria tudo vazio (crie o admin de novo com `criar-admin`).

## 5. Quanto vídeo cabe no disco

VPS de 100 GB NVMe. Reservas: sistema e imagens Docker (~15 GB), PostgreSQL e logs (~5 GB), uploads em
andamento (~10 GB) e os 15% livres do alerta (15 GB). Sobram **~55 GB para o HLS** (produção e
homologação juntas).

Cada hora de aula guarda as duas variantes (720p e 1080p) mais o áudio de cada uma:

| Conteúdo | Taxa média (720p + 1080p + áudio) | GB por hora | Horas em 55 GB |
|---|---|---|---|
| Tela de código, slides (o típico do curso) | ~1,1 Mbps | ~0,5 | ~110 h |
| Câmera e tela misturadas | ~3,9 Mbps | ~1,8 | ~30 h |
| Pior caso (sempre no teto: 2,8 + 5 Mbps) | ~8,1 Mbps | ~3,6 | ~15 h |

A conta do capítulo de limites da VPS usa o `segundosProcessamentoPorMinuto` e o `tamanhoFinal` que o
ADM mostra para cada vídeo: medir com os vídeos reais e atualizar esta tabela.

## 6. Saída para um S3 externo

Quando o HLS passar de ~40 GB, ou se a banda da VPS virar gargalo, os vídeos vão para o Cloudflare R2
(sem taxa de saída) ou o Backblaze B2. Como a aplicação só fala S3 pelo AWS SDK:

1. Criar o bucket no R2, a chave da aplicação e o CORS para `aluno.celke.uno` e `adm.celke.uno`.
2. Copiar o conteúdo: `rclone sync garage:midia r2:midia` (com as duas origens no `rclone.conf`).
3. No `.env` da produção, trocar `S3_ENDPOINT` e `S3_ENDPOINT_PUBLICO` (endpoint do R2 ou domínio
   próprio `media.celke.uno` apontado para o bucket), `S3_BUCKET`, `S3_ACCESS_KEY` e `S3_SECRET_KEY`.
4. Rodar o `rclone sync` de novo (o que entrou durante a cópia) e reiniciar a API e o worker.
5. Depois de uma semana sem problema, tirar o Garage da produção e o host de mídia do Caddy.

## 7. Segurança

### OWASP ASVS nível 1 — revisão da API

| Capítulo | Situação |
|---|---|
| V2 Autenticação | Senha mínima de 10 (aluno) / 12 (admin), máximo 128, Argon2; bloqueio após falhas por e-mail e por e-mail + IP; limite por IP nas rotas de conta; recuperação com token de uso único e resposta igual para e-mail existente ou não |
| V3 Sessão | Access token de 10 minutos; refresh opaco em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, sem `Domain`; rotação com detecção de reuso (revoga a família); logout e "sair de todos"; troca de senha encerra todas as sessões |
| V4 Controle de acesso | Tudo negado por padrão; rotas públicas declaradas com método; papel no filtro e no `@PreAuthorize`; acesso ao conteúdo só pela `AcessoAulaPolicy`; recursos de outro usuário respondem 404; chaves de upload com o id do dono (IDOR) |
| V5 Entrada e saída | Bean Validation em todo corpo; Markdown com HTML escapado e sanitizado (lista fechada); conteúdo dos arquivos conferido pela assinatura real; imagens regravadas sem metadados; ffmpeg por lista de argumentos |
| V7 Erros e logs | ProblemDetail sem stack trace nem causa; id de correlação; auditoria de login; log de acesso aos vídeos com retenção |
| V8 Dados | Bucket privado, só URLs pré-assinadas curtas; certificado validado sem e-mail nem ids; segredos só nos `.env` e nos secrets |
| V9 Comunicação | HTTPS automático pelo Caddy, HSTS; CORS do bucket com as origens exatas |
| V10 Código malicioso / V14 Configuração | Trivy no pipeline (críticos com correção quebram o build); versões fixas de imagens e dependências; usuário não-root nos containers; Swagger e Actuator fora da produção |

### Varreduras

- **Trivy** (a cada PR, imagens da API e do worker): a primeira varredura apontou o Tomcat 11.0.24
  (3 CVEs críticos) e o Jackson 3.1.5 / 2.21.5; o `pom.xml` fixa as versões corrigidas até o Spring Boot
  trazê-las.
- **OWASP ZAP baseline** na stack local de e2e (rodar também na homologação a cada versão):
  0 falhas, 65 regras sem achado; o aviso de `Cross-Origin-Resource-Policy` foi resolvido no Caddy
  (`same-site`). "Non-Storable Content" é esperado: a API responde sem cache.

  ```bash
  docker run --rm ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t https://homolog-aluno.celke.uno/api/cursos -I
  ```
`````

### 5. Testes

`backend/src/test/java/br/com/celke/cursos/shared/operacao/AlertasOperacaoTest.java`:

`````java
package br.com.celke.cursos.shared.operacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.fila.FilaService;
import br.com.celke.cursos.shared.fila.FilaService.FalhaJob;
import br.com.celke.cursos.shared.operacao.MedidorDisco.Espaco;
import br.com.celke.cursos.support.fixtures.RelogioAjustavel;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertasOperacaoTest {

    private static final Instant T0 = Instant.parse("2026-03-10T12:00:00Z");
    private static final long GB = 1024L * 1024 * 1024;

    @Mock
    private FilaService fila;

    @Mock
    private EmailService emailService;

    @Mock
    private MedidorDisco medidorDisco;

    private RelogioAjustavel relogio;

    @BeforeEach
    void criarRelogio() {
        relogio = new RelogioAjustavel(T0);
    }

    private AlertasOperacao alertas(String email) {
        return new AlertasOperacao(
                fila,
                emailService,
                medidorDisco,
                new AlertasProperties(email, 15, Path.of("/"), Duration.ofHours(6)),
                relogio);
    }

    private void disco(long livreGb) {
        when(medidorDisco.medir(Path.of("/"))).thenReturn(new Espaco(livreGb * GB, 100 * GB));
    }

    @Test
    @DisplayName("sem e-mail configurado, nada é verificado")
    void desligado() {
        alertas(" ").verificar();

        verify(fila, never()).falhasDesde(any(), anyString());
        verify(medidorDisco, never()).medir(any());
    }

    @Test
    @DisplayName("jobs com erro desde a última verificação geram um e-mail com o resumo; o de e-mail fica de fora")
    @SuppressWarnings("unchecked")
    void falhas() {
        disco(50);
        when(fila.falhasDesde(T0.minus(Duration.ofMinutes(15)), EmailService.TIPO_JOB))
                .thenReturn(List.of(new FalhaJob(9, "video.transcodificar", "ffmpeg\n  falhou", T0)));

        alertas("ops@celke.uno").verificar();

        ArgumentCaptor<Map<String, String>> variaveis = ArgumentCaptor.forClass(Map.class);
        verify(emailService)
                .enviar(
                        eq("ops@celke.uno"),
                        eq("[Plataforma de Cursos] 1 job(s) com erro no worker"),
                        eq("alerta-operacao"),
                        variaveis.capture());
        assertThat(variaveis.getValue().get("detalhes")).isEqualTo("Job 9 (video.transcodificar): ffmpeg falhou");
    }

    @Test
    @DisplayName("a verificação seguinte procura falhas só depois da anterior")
    void janela() {
        disco(50);
        when(fila.falhasDesde(any(), anyString())).thenReturn(List.of());
        AlertasOperacao alertas = alertas("ops@celke.uno");

        alertas.verificar();
        relogio.avancar(Duration.ofMinutes(15));
        alertas.verificar();

        verify(fila).falhasDesde(T0, EmailService.TIPO_JOB);
        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), anyMap());
    }

    @ParameterizedTest(name = "{0} GB livres de 100 → alerta: {1}")
    @CsvSource({"14, true", "15, false", "50, false", "0, true"})
    @DisplayName("disco abaixo de 15% livre gera alerta; no limite, não")
    void disco(long livre, boolean alerta) {
        disco(livre);
        when(fila.falhasDesde(any(), anyString())).thenReturn(List.of());

        alertas("ops@celke.uno").verificar();

        verify(emailService, times(alerta ? 1 : 0))
                .enviar(
                        eq("ops@celke.uno"),
                        eq("[Plataforma de Cursos] Disco com %d%% livre".formatted(livre)),
                        eq("alerta-operacao"),
                        anyMap());
    }

    @Test
    @DisplayName("disco baixo repete o alerta só depois de 6 horas; recuperado, o próximo alerta sai na hora")
    void repeticaoDoDisco() {
        when(fila.falhasDesde(any(), anyString())).thenReturn(List.of());
        when(medidorDisco.medir(Path.of("/")))
                .thenReturn(new Espaco(10 * GB, 100 * GB))
                .thenReturn(new Espaco(10 * GB, 100 * GB))
                .thenReturn(new Espaco(10 * GB, 100 * GB))
                .thenReturn(new Espaco(50 * GB, 100 * GB))
                .thenReturn(new Espaco(10 * GB, 100 * GB));
        AlertasOperacao alertas = alertas("ops@celke.uno");

        alertas.verificar(); // alerta
        relogio.avancar(Duration.ofHours(1));
        alertas.verificar(); // silêncio
        relogio.avancar(Duration.ofHours(5));
        alertas.verificar(); // 6 h depois: alerta
        relogio.avancar(Duration.ofMinutes(15));
        alertas.verificar(); // recuperou
        relogio.avancar(Duration.ofMinutes(15));
        alertas.verificar(); // caiu de novo: alerta

        verify(emailService, times(3)).enviar(anyString(), anyString(), eq("alerta-operacao"), anyMap());
    }

    @Test
    @DisplayName("percentual livre arredondado para baixo; disco de tamanho zero conta como cheio")
    void percentual() {
        assertThat(new Espaco(149, 1000).percentualLivre()).isEqualTo(14);
        assertThat(new Espaco(0, 0).percentualLivre()).isZero();
        assertThat(new MedidorDisco().medir(Path.of(".")).total()).isPositive();
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
    @DisplayName("renovar o lease mantém o job com o worker; sem renovação ele volta para a fila")
    void renovacaoDoLease() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();

        // Renovado aos 9 min: o lease vai até os 19 min
        fila.renovar(job, T0.plus(Duration.ofMinutes(9)));
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofMinutes(10)).plusSeconds(1)))
                .isEmpty();
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofMinutes(19)).plusSeconds(1)))
                .hasValueSatisfying(j -> assertThat(j.id()).isEqualTo(id));
    }

    @Test
    @DisplayName("renovar um job já concluído não o devolve para processamento")
    void renovacaoDeJobConcluido() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();
        fila.concluir(job, T0);

        fila.renovar(job, T0.plusSeconds(30));

        assertThat(status(id)).isEqualTo(StatusJob.CONCLUIDO);
    }

    @Test
    @DisplayName("falhas desde um instante: só os jobs em ERRO depois dele e fora do tipo excluído")
    void falhasDesde() {
        String tipo = stamp();
        String excluido = stamp();
        Job antigo = reservar(tipo, T0);
        fila.falharDefinitivo(antigo, "antes", T0);
        Job novo = reservar(tipo, T0);
        fila.falharDefinitivo(novo, "depois", T0.plusSeconds(60));
        fila.falharDefinitivo(reservar(excluido, T0), "e-mail", T0.plusSeconds(60));
        fila.concluir(reservar(tipo, T0), T0.plusSeconds(60));

        assertThat(fila.falhasDesde(T0.plusSeconds(30), excluido))
                .filteredOn(f -> f.tipo().equals(tipo) || f.tipo().equals(excluido))
                .extracting(FilaService.FalhaJob::id, FilaService.FalhaJob::erro)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(novo.id(), "depois"));
    }

    private Job reservar(String tipo, Instant agora) {
        fila.enfileirar(tipo, Map.of(), agora);
        return fila.reservar(Set.of(tipo), agora).orElseThrow();
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

`backend/src/e2e/java/br/com/celke/cursos/e2e/proxy/ProxyE2E.java`:

`````java
package br.com.celke.cursos.e2e.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Dados;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** O que o Caddy garante na frente da API: headers, rotas expostas e o IP real do cliente. */
class ProxyE2E {

    @Test
    @DisplayName("health check passa pelo Caddy com os headers de segurança e sem identificar o servidor")
    void headersDeSeguranca() {
        var resposta =
                Api.aluno().get("/actuator/health").then().statusCode(200).body("status", equalTo("UP"));

        resposta.header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "strict-origin-when-cross-origin")
                .header("Cross-Origin-Resource-Policy", "same-site")
                .header("Strict-Transport-Security", "max-age=31536000");
        assertThat(resposta.extract().header("Server")).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/v3/api-docs", "/swagger-ui/index.html", "/actuator/env", "/actuator/metrics", "/"})
    @DisplayName("fora de /api e do health check, nada chega à API")
    void rotasForaDaApi(String rota) {
        Api.aluno().get(rota).then().statusCode(404);
        Api.adm().get(rota).then().statusCode(404);
    }

    @Test
    @DisplayName("X-Forwarded-For forjado pelo cliente não muda o IP visto pela API")
    void xForwardedForForjado() {
        // Cada requisição finge vir de um IP diferente. Se a API acreditasse, cada uma cairia num
        // balde novo do limite por IP; como o Caddy substitui o cabeçalho, todas gastam o mesmo balde.
        int primeira = restanteNoLimite("203.0.113.1");
        restanteNoLimite("203.0.113.2");
        int terceira = restanteNoLimite("203.0.113.3");

        assertThat(terceira).isLessThan(primeira);
    }

    private static int restanteNoLimite(String ipForjado) {
        String restante = Api.aluno()
                .header("X-Forwarded-For", ipForjado)
                .body("{\"email\":\"%s\",\"senha\":\"qualquer-senha\"}".formatted(Dados.email()))
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .extract()
                .header("X-RateLimit-Remaining");
        return Integer.parseInt(restante);
    }
}
`````

## Antes de executar

Fase 9 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/operacao
```


## Orientações para o usuário

Esta etapa tem passos que só o usuário faz, fora do código (no navegador, no painel da Hostinger, no DNS, no GitHub ou na VPS). Depois de gravar os arquivos e passar na **Verificação**, **mostre no terminal**, para o usuário, cada bloco abaixo **exatamente como está**, um de cada vez, na ordem. Depois de cada bloco, pare e espere o usuário confirmar que concluiu antes de mostrar o próximo. Quando ele informar um valor (o IP da VPS, por exemplo), use-o nos blocos seguintes no lugar de `<ip-da-vps>`.

### 1. Backup fora da VPS

```text
==> OPERAÇÃO — ETAPA 1 DE 2: BACKUP FORA DA VPS (docs/operacao.md, seção 1)

1. Crie um bucket PRIVADO no Cloudflare R2 (ou Backblaze B2) só para os backups e uma chave com
   acesso apenas a ele.
2. Copie o script e, na VPS, como deploy:

     scp infra/scripts/backup.sh <seu-usuario>@<ip-da-vps>:/tmp/        # do seu computador
     sudo install -o root -g root -m 755 /tmp/backup.sh /usr/local/bin/plataforma-cursos-backup
     install -m 600 /dev/null /opt/plataforma-cursos/backup.env
     nano /opt/plataforma-cursos/backup.env   # RESTIC_REPOSITORY, RESTIC_PASSWORD, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY

   Guarde a RESTIC_PASSWORD também no seu gerenciador de senhas: sem ela o backup não abre.
3. Agende (crontab -e do deploy):

     30 3 * * * /usr/local/bin/plataforma-cursos-backup producao >> /opt/plataforma-cursos/backup.log 2>&1
     45 3 * * * /usr/local/bin/plataforma-cursos-backup homolog  >> /opt/plataforma-cursos/backup.log 2>&1

4. Faça o teste de restauração da seção 2 do docs/operacao.md.
```

### 2. Alertas e monitoramento

```text
==> OPERAÇÃO — ETAPA 2 DE 2: ALERTAS (docs/operacao.md, seção 3)

1. Em /opt/plataforma-cursos/producao/.env e homolog/.env, preencha ALERTA_EMAIL (quem recebe os
   alertas de jobs com erro e de disco quase cheio). Vale no próximo deploy.
2. Crie um monitor externo gratuito (UptimeRobot ou Better Stack), a cada 5 minutos, em
   https://aluno.celke.uno/actuator/health, com aviso por e-mail e celular.
```

## Verificação

Dentro de `backend/` (Docker rodando e `ffmpeg` no PATH):

```bash
./mvnw spotless:apply
./mvnw verify
```

- `BUILD SUCCESS` com 729 testes unitários e 354 de integração, e a cobertura mínima atendida.

Na raiz do repositório, as jornadas e2e com a stack completa:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
(cd backend && ./mvnw verify -Pe2e)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- 18 jornadas passam, com o `ProxyE2E` conferindo o `Cross-Origin-Resource-Policy`.

Teste de backup e restauração na stack de e2e no ar (seção 2 de `docs/operacao.md`): faça o backup,
estrague um título de curso e apague `/garage/data` do volume, restaure e confira os dados e os objetos
do bucket.

## Depois de executar

```bash
git add backend/ infra/ docs/ .github/ README.md
git commit -m "feat(infra): adicionar backup, alertas e segurança da operação"
git push -u origin feature/operacao
```

Abra o Pull Request de `feature/operacao` para `homolog`.
