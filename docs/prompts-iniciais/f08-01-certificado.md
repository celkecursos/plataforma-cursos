# Certificado em PDF

**Fase:** 8
**Branch:** `feature/certificado`

## Prompt

Vamos criar o módulo `certificado` do backend: regra de conclusão por turma, emissão do certificado em
PDF com o nome confirmado pelo aluno e validação pública pelo código. Siga o `backend/CLAUDE.md` e o
padrão das skills de teste. Não faça commit.

O que esta etapa entrega:

- **Dependência** no `pom.xml`: `io.github.openhtmltopdf:openhtmltopdf-pdfbox` 1.1.87.
- **Regra por turma** (migration `V13`): `percentual_conclusao` (1 a 100, padrão 100) e
  `carga_horaria_horas` opcional, definidos em `PUT /api/adm/turmas/{id}/certificado`; a resposta da
  turma passa a trazer os dois campos. O `ConteudoTurmaService` exporta `regraCertificado`. Turma sem aulas
  nunca é concluída.
- **Certificado** (tabela `certificados`, um por aluno e turma): dados impressos congelados na emissão
  (nome, curso, turma, carga horária), código público e chave do PDF.
- **Aluno**: `GET /api/aluno/turmas/{id}/certificado` (percentual visto pelo `ProgressoService`, exigido,
  concluiu e o certificado se já emitido) e `POST` com `nomeCompleto` (letras, com sobrenome; 201 na
  emissão, 200 se já existia; 409 sem concluir), ambos com matrícula vigente; `GET /api/aluno/certificados`
  e `GET /api/aluno/certificados/{id}/download` (URL de 5 minutos; de outro aluno, 404).
- **PDF**: template Thymeleaf `certificado/certificado.html` (A4 paisagem) convertido pelo openhtmltopdf,
  gerado uma vez e guardado em `certificados/<turma>/<codigo>.pdf`.
- **Validação pública**: código de 12 caracteres sem letras ambíguas (`K7QM-2XRP-H9TD`, 60 bits
  aleatórios) e `GET /api/certificados/{codigo}` liberado no `SecurityConfig`, sem e-mail nem ids
  internos.

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
                        // Conta: cadastro, confirmação de e-mail e recuperação de senha
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/cadastro",
                                "/api/auth/confirmar-email",
                                "/api/auth/reenviar-confirmacao",
                                "/api/auth/recuperar-senha",
                                "/api/auth/redefinir-senha")
                        .permitAll()
                        // Catálogo: cursos publicados, detalhe e capa
                        .requestMatchers(HttpMethod.GET, "/api/cursos", "/api/cursos/*", "/api/cursos/*/capa")
                        .permitAll()
                        // Validação pública do certificado pelo código (página "validar certificado" do site)
                        .requestMatchers(HttpMethod.GET, "/api/certificados/*")
                        .permitAll()
                        // Área do ADM: o papel é exigido aqui e repetido com @PreAuthorize nos controllers
                        .requestMatchers("/api/adm/**")
                        .hasRole("ADMIN")
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

`backend/src/main/java/br/com/celke/cursos/curso/domain/Turma.java`:

`````java
package br.com.celke.cursos.curso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Oferta de um curso, com os próprios módulos e aulas (e, adiante, as matrículas). */
@Entity
@Table(name = "turmas")
public class Turma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTurma status;

    /** Percentual de aulas vistas que dá direito ao certificado (1 a 100). */
    @Column(name = "percentual_conclusao", nullable = false)
    private int percentualConclusao = 100;

    /** Carga horária impressa no certificado; nula, o certificado sai sem ela. */
    @Column(name = "carga_horaria_horas")
    private Integer cargaHorariaHoras;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Turma() {}

    /** Turma nova nasce em preparação. */
    public Turma(Curso curso, String nome, Instant agora) {
        this.curso = curso;
        this.nome = nome.strip();
        this.status = StatusTurma.PREPARACAO;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void renomear(String nome, Instant agora) {
        this.nome = nome.strip();
        this.atualizadoEm = agora;
    }

    public void alterarStatus(StatusTurma novo, Instant agora) {
        this.status = novo;
        this.atualizadoEm = agora;
    }

    /** Regra do certificado: percentual mínimo de aulas vistas e carga horária. */
    public void definirCertificado(int percentualConclusao, Integer cargaHorariaHoras, Instant agora) {
        if (percentualConclusao < 1 || percentualConclusao > 100) {
            throw new IllegalArgumentException("O percentual de conclusão vai de 1 a 100.");
        }
        if (cargaHorariaHoras != null && cargaHorariaHoras < 1) {
            throw new IllegalArgumentException("A carga horária precisa ser positiva.");
        }
        this.percentualConclusao = percentualConclusao;
        this.cargaHorariaHoras = cargaHorariaHoras;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public Curso getCurso() {
        return curso;
    }

    public String getNome() {
        return nome;
    }

    public StatusTurma getStatus() {
        return status;
    }

    public int getPercentualConclusao() {
        return percentualConclusao;
    }

    public Integer getCargaHorariaHoras() {
        return cargaHorariaHoras;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/application/TurmaAdmService.java`:

`````java
package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão das turmas de um curso pelo ADM. */
@Service
public class TurmaAdmService {

    private final CursoRepository cursos;
    private final TurmaRepository turmas;
    private final ModuloRepository modulos;
    private final Clock clock;

    public TurmaAdmService(CursoRepository cursos, TurmaRepository turmas, ModuloRepository modulos, Clock clock) {
        this.cursos = cursos;
        this.turmas = turmas;
        this.modulos = modulos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Turma> listar(Long cursoId) {
        if (!cursos.existsById(cursoId)) {
            throw CursoAdmService.naoEncontrado();
        }
        return turmas.findByCursoIdOrderById(cursoId);
    }

    @Transactional
    public Turma criar(Long cursoId, String nome) {
        var curso = cursos.findById(cursoId).orElseThrow(CursoAdmService::naoEncontrado);
        return turmas.save(new Turma(curso, nome, clock.instant()));
    }

    @Transactional
    public Turma renomear(Long id, String nome) {
        Turma turma = buscar(id);
        turma.renomear(nome, clock.instant());
        return turma;
    }

    @Transactional
    public Turma alterarStatus(Long id, StatusTurma status) {
        Turma turma = buscar(id);
        turma.alterarStatus(status, clock.instant());
        return turma;
    }

    /** Exclui uma turma sem módulos e sem matrículas; as outras são encerradas. */
    @Transactional
    public void excluir(Long id) {
        Turma turma = buscar(id);
        if (modulos.existsByTurmaId(id)) {
            throw new ConflitoException("A turma tem módulos. Exclua os módulos ou encerre a turma.");
        }
        try {
            turmas.delete(turma);
            turmas.flush();
        } catch (DataIntegrityViolationException e) {
            // A chave estrangeira das matrículas impede a exclusão
            throw new ConflitoException("A turma tem matrículas. Encerre a turma.");
        }
    }

    /** Regra de conclusão e carga horária do certificado da turma. */
    @Transactional
    public Turma definirCertificado(Long id, int percentualConclusao, Integer cargaHorariaHoras) {
        Turma turma = buscar(id);
        turma.definirCertificado(percentualConclusao, cargaHorariaHoras, clock.instant());
        return turma;
    }

    private Turma buscar(Long id) {
        return turmas.findById(id).orElseThrow(TurmaAdmService::naoEncontrada);
    }

    static NaoEncontradoException naoEncontrada() {
        return new NaoEncontradoException("Turma não encontrada.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/application/ConteudoTurmaService.java`:

`````java
package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas ao conteúdo exportadas para os outros módulos (matrícula, vídeo, progresso...), que não
 * acessam os repositórios do curso. Devolve só dados prontos, sem entidades com carregamento tardio.
 * O conteúdo visível é sempre o ativo: módulo e aula {@code ATIVO}, na ordem.
 */
@Service
public class ConteudoTurmaService {

    private final TurmaRepository turmas;
    private final ModuloRepository modulos;
    private final AulaRepository aulas;
    private final CapaCursoService capaCursoService;

    public ConteudoTurmaService(
            TurmaRepository turmas, ModuloRepository modulos, AulaRepository aulas, CapaCursoService capaCursoService) {
        this.turmas = turmas;
        this.modulos = modulos;
        this.aulas = aulas;
        this.capaCursoService = capaCursoService;
    }

    @Transactional(readOnly = true)
    public Optional<TurmaResumo> turma(Long turmaId) {
        return turmas.comCurso(List.of(turmaId)).stream().findFirst().map(this::resumo);
    }

    /** Turmas indexadas pelo id, numa consulta só; ids inexistentes ficam fora do mapa. */
    @Transactional(readOnly = true)
    public Map<Long, TurmaResumo> turmas(Collection<Long> turmaIds) {
        return turmas.comCurso(turmaIds).stream()
                .map(this::resumo)
                .collect(Collectors.toMap(TurmaResumo::turmaId, Function.identity()));
    }

    /** Módulos ativos da turma, cada um com as aulas ativas, na ordem. */
    @Transactional(readOnly = true)
    public List<ModuloConteudo> conteudoAtivo(Long turmaId) {
        List<Modulo> ativos = modulos.findByTurmaIdAndStatusOrderByOrdem(turmaId, StatusConteudo.ATIVO);
        Map<Long, List<Aula>> aulasPorModulo = aulas
                .findByModuloIdInAndStatusOrderByOrdem(
                        ativos.stream().map(Modulo::getId).toList(), StatusConteudo.ATIVO)
                .stream()
                .collect(Collectors.groupingBy(aula -> aula.getModulo().getId()));
        return ativos.stream()
                .map(modulo -> new ModuloConteudo(
                        modulo.getId(),
                        modulo.getTitulo(),
                        aulasPorModulo.getOrDefault(modulo.getId(), List.of()).stream()
                                .map(aula -> new AulaConteudo(aula.getId(), aula.getTitulo(), aula.isGratuita()))
                                .toList()))
                .toList();
    }

    /** Regra do certificado da turma (percentual mínimo de aulas vistas e carga horária). */
    @Transactional(readOnly = true)
    public Optional<RegraCertificado> regraCertificado(Long turmaId) {
        return turmas.findById(turmaId)
                .map(t -> new RegraCertificado(t.getPercentualConclusao(), t.getCargaHorariaHoras()));
    }

    /** O módulo existe, em qualquer status (gestão dos arquivos pelo ADM). */
    @Transactional(readOnly = true)
    public boolean moduloExiste(Long moduloId) {
        return modulos.existsById(moduloId);
    }

    /** Módulo ativo com a turma dele (arquivos do módulo); vazio se não existe ou está inativo. */
    @Transactional(readOnly = true)
    public Optional<ModuloContexto> moduloAtivo(Long moduloId) {
        return modulos.findById(moduloId)
                .filter(modulo -> modulo.getStatus() == StatusConteudo.ATIVO)
                .map(modulo -> new ModuloContexto(
                        modulo.getId(), modulo.getTitulo(), modulo.getTurma().getId()));
    }

    /** A aula existe, em qualquer status (gestão do vídeo e dos arquivos pelo ADM). */
    @Transactional(readOnly = true)
    public boolean aulaExiste(Long aulaId) {
        return aulas.existsById(aulaId);
    }

    /** Aula ativa de módulo ativo, com o que a política de acesso precisa; vazio se não existe ou está inativa. */
    @Transactional(readOnly = true)
    public Optional<AulaContexto> aulaAtiva(Long aulaId) {
        return aulas.comHierarquia(aulaId)
                .filter(aula -> aula.getStatus() == StatusConteudo.ATIVO
                        && aula.getModulo().getStatus() == StatusConteudo.ATIVO)
                .map(aula -> {
                    Turma turma = aula.getModulo().getTurma();
                    return new AulaContexto(
                            aula.getId(),
                            aula.getTitulo(),
                            aula.getDescricao(),
                            aula.isGratuita(),
                            turma.getId(),
                            turma.getStatus(),
                            turma.getCurso().getStatus());
                });
    }

    private TurmaResumo resumo(Turma turma) {
        Curso curso = turma.getCurso();
        return new TurmaResumo(
                turma.getId(),
                turma.getNome(),
                turma.getStatus(),
                curso.getId(),
                curso.getTitulo(),
                curso.getSlug(),
                curso.getStatus(),
                capaCursoService.url(curso).map(Object::toString).orElse(null));
    }

    /** Turma com os dados do curso; {@code capaUrl} é temporária (vale uma hora). */
    public record TurmaResumo(
            Long turmaId,
            String turmaNome,
            StatusTurma turmaStatus,
            Long cursoId,
            String cursoTitulo,
            String cursoSlug,
            StatusCurso cursoStatus,
            String capaUrl) {}

    public record ModuloConteudo(Long id, String titulo, List<AulaConteudo> aulas) {}

    public record AulaConteudo(Long id, String titulo, boolean gratuita) {}

    public record ModuloContexto(Long id, String titulo, Long turmaId) {}

    public record RegraCertificado(int percentualConclusao, Integer cargaHorariaHoras) {}

    public record AulaContexto(
            Long id,
            String titulo,
            String descricao,
            boolean gratuita,
            Long turmaId,
            StatusTurma turmaStatus,
            StatusCurso cursoStatus) {

        /** Aula de degustação: gratuita, de turma ativa e de curso publicado (rascunho não vaza). */
        public boolean aberta() {
            return gratuita && turmaStatus == StatusTurma.ATIVA && cursoStatus == StatusCurso.PUBLICADO;
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/api/CursoRequests.java`:

`````java
package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.application.DadosAula;
import br.com.celke.cursos.curso.application.DadosCurso;
import br.com.celke.cursos.curso.domain.Slug;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Corpos das rotas de gestão de cursos ({@code /api/adm}). */
public final class CursoRequests {

    private CursoRequests() {}

    /** Sem slug, ele é gerado a partir do título. */
    public record Curso(
            @NotBlank @Size(max = 120) String titulo,

            @Size(max = Slug.TAMANHO_MAXIMO)
            @Pattern(regexp = Slug.FORMATO, message = "deve ter só letras minúsculas, números e hífens")
            String slug,

            @NotBlank @Size(max = 300) String resumo,
            @Size(max = 20_000) String descricao) {

        public DadosCurso dados() {
            return new DadosCurso(titulo, slug, resumo, descricao);
        }
    }

    public record AlterarStatusCurso(@NotNull StatusCurso status) {}

    public record Turma(@NotBlank @Size(max = 120) String nome) {}

    public record AlterarStatusTurma(@NotNull StatusTurma status) {}

    /** Sem carga horária, o certificado sai sem ela. */
    public record CertificadoTurma(
            @Min(1) @Max(100) int percentualConclusao,
            @Positive @Max(10_000) Integer cargaHorariaHoras) {}

    public record Modulo(@NotBlank @Size(max = 150) String titulo) {}

    /** Sem {@code gratuita}, a aula é paga (só para matriculados). */
    public record Aula(
            @NotBlank @Size(max = 150) String titulo,
            @Size(max = 20_000) String descricao,
            Boolean gratuita) {

        public DadosAula dados() {
            return new DadosAula(titulo, descricao, Boolean.TRUE.equals(gratuita));
        }
    }

    public record AlterarStatusConteudo(@NotNull StatusConteudo status) {}

    /** Ids de todos os itens do pai, na nova ordem. */
    public record Ordem(@NotEmpty @Size(max = 500) List<@NotNull Long> ids) {}

    public record IniciarEnvioCapa(
            @NotBlank String contentType, @Positive long tamanho) {}

    public record ConfirmarCapa(@NotBlank @Size(max = 200) String chave) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/api/CursoResponses.java`:

`````java
package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.application.CapaCursoService.EnvioCapa;
import br.com.celke.cursos.curso.application.CatalogoService.CursoDetalhe;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Respostas das rotas de cursos: gestão ({@code /api/adm}) e catálogo público ({@code /api/cursos}). */
public final class CursoResponses {

    private CursoResponses() {}

    /** Curso na gestão; {@code capaUrl} é temporária (vale uma hora). */
    public record CursoAdm(
            Long id,
            String titulo,
            String slug,
            String resumo,
            String descricao,
            StatusCurso status,
            String capaUrl,
            Instant criadoEm,
            Instant atualizadoEm) {

        public static CursoAdm de(br.com.celke.cursos.curso.domain.Curso curso, Optional<URL> capa) {
            return new CursoAdm(
                    curso.getId(),
                    curso.getTitulo(),
                    curso.getSlug(),
                    curso.getResumo(),
                    curso.getDescricao(),
                    curso.getStatus(),
                    capa.map(URL::toString).orElse(null),
                    curso.getCriadoEm(),
                    curso.getAtualizadoEm());
        }
    }

    public record Turma(
            Long id,
            Long cursoId,
            String nome,
            StatusTurma status,
            int percentualConclusao,
            Integer cargaHorariaHoras) {

        public static Turma de(br.com.celke.cursos.curso.domain.Turma turma) {
            return new Turma(
                    turma.getId(),
                    turma.getCurso().getId(),
                    turma.getNome(),
                    turma.getStatus(),
                    turma.getPercentualConclusao(),
                    turma.getCargaHorariaHoras());
        }
    }

    public record Modulo(Long id, Long turmaId, String titulo, int ordem, StatusConteudo status) {

        public static Modulo de(br.com.celke.cursos.curso.domain.Modulo modulo) {
            return new Modulo(
                    modulo.getId(),
                    modulo.getTurma().getId(),
                    modulo.getTitulo(),
                    modulo.getOrdem(),
                    modulo.getStatus());
        }
    }

    public record Aula(
            Long id,
            Long moduloId,
            String titulo,
            String descricao,
            int ordem,
            boolean gratuita,
            StatusConteudo status) {

        public static Aula de(br.com.celke.cursos.curso.domain.Aula aula) {
            return new Aula(
                    aula.getId(),
                    aula.getModulo().getId(),
                    aula.getTitulo(),
                    aula.getDescricao(),
                    aula.getOrdem(),
                    aula.isGratuita(),
                    aula.getStatus());
        }
    }

    /** Onde e como enviar a imagem: PUT na {@code url} com o cabeçalho {@code Content-Type} informado. */
    public record EnvioCapaResponse(String chave, String url, String contentType, Instant expiraEm) {

        public static EnvioCapaResponse de(EnvioCapa envio) {
            return new EnvioCapaResponse(envio.chave(), envio.url().toString(), envio.contentType(), envio.expiraEm());
        }
    }

    /**
     * Curso no catálogo. {@code capaUrl} é um endereço estável da API (relativo), que redireciona para
     * a imagem: pode ir para HTML em cache ou pré-renderizado sem vencer.
     */
    public record CursoCatalogo(String slug, String titulo, String resumo, String capaUrl) {

        public static CursoCatalogo de(br.com.celke.cursos.curso.domain.Curso curso) {
            return new CursoCatalogo(curso.getSlug(), curso.getTitulo(), curso.getResumo(), enderecoCapa(curso));
        }
    }

    public record CursoCatalogoDetalhe(
            String slug, String titulo, String resumo, String descricao, String capaUrl, List<ModuloCatalogo> modulos) {

        public static CursoCatalogoDetalhe de(CursoDetalhe detalhe) {
            var curso = detalhe.curso();
            return new CursoCatalogoDetalhe(
                    curso.getSlug(),
                    curso.getTitulo(),
                    curso.getResumo(),
                    curso.getDescricao(),
                    enderecoCapa(curso),
                    detalhe.modulos().stream().map(ModuloCatalogo::de).toList());
        }
    }

    public record ModuloCatalogo(String titulo, List<AulaCatalogo> aulas) {

        static ModuloCatalogo de(ModuloConteudo modulo) {
            return new ModuloCatalogo(
                    modulo.titulo(),
                    modulo.aulas().stream().map(AulaCatalogo::de).toList());
        }
    }

    public record AulaCatalogo(Long id, String titulo, boolean gratuita) {

        static AulaCatalogo de(AulaConteudo aula) {
            return new AulaCatalogo(aula.id(), aula.titulo(), aula.gratuita());
        }
    }

    private static String enderecoCapa(br.com.celke.cursos.curso.domain.Curso curso) {
        return curso.getCapaChave() == null ? null : "/api/cursos/%s/capa".formatted(curso.getSlug());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/api/TurmaAdmController.java`:

`````java
package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.api.CursoResponses.Turma;
import br.com.celke.cursos.curso.application.TurmaAdmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Turmas")
public class TurmaAdmController {

    private final TurmaAdmService turmaAdmService;

    public TurmaAdmController(TurmaAdmService turmaAdmService) {
        this.turmaAdmService = turmaAdmService;
    }

    @GetMapping("/cursos/{cursoId}/turmas")
    @Operation(summary = "Listar as turmas do curso")
    public List<Turma> listar(@PathVariable Long cursoId) {
        return turmaAdmService.listar(cursoId).stream().map(Turma::de).toList();
    }

    @PostMapping("/cursos/{cursoId}/turmas")
    @Operation(summary = "Criar turma", description = "Nasce em preparação.")
    public ResponseEntity<Turma> criar(@PathVariable Long cursoId, @Valid @RequestBody CursoRequests.Turma request) {
        Turma turma = Turma.de(turmaAdmService.criar(cursoId, request.nome()));
        return ResponseEntity.created(URI.create("/api/adm/turmas/" + turma.id()))
                .body(turma);
    }

    @PutMapping("/turmas/{id}")
    @Operation(summary = "Renomear turma")
    public Turma renomear(@PathVariable Long id, @Valid @RequestBody CursoRequests.Turma request) {
        return Turma.de(turmaAdmService.renomear(id, request.nome()));
    }

    @PatchMapping("/turmas/{id}/status")
    @Operation(summary = "Ativar ou encerrar turma", description = "O catálogo mostra a turma ativa mais recente.")
    public Turma alterarStatus(@PathVariable Long id, @Valid @RequestBody CursoRequests.AlterarStatusTurma request) {
        return Turma.de(turmaAdmService.alterarStatus(id, request.status()));
    }

    @PutMapping("/turmas/{id}/certificado")
    @Operation(
            summary = "Regra do certificado",
            description = "Percentual de aulas vistas que dá direito ao certificado (1 a 100) e carga horária.")
    public Turma definirCertificado(@PathVariable Long id, @Valid @RequestBody CursoRequests.CertificadoTurma request) {
        return Turma.de(
                turmaAdmService.definirCertificado(id, request.percentualConclusao(), request.cargaHorariaHoras()));
    }

    @DeleteMapping("/turmas/{id}")
    @Operation(summary = "Excluir turma", description = "Só sem módulos; turma com conteúdo é encerrada.")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        turmaAdmService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
`````

### 2. Migration

`backend/src/main/resources/db/migration/V13__criar_certificados.sql`:

`````sql
-- Regra do certificado por turma: percentual mínimo de aulas vistas e carga horária (opcional)
ALTER TABLE turmas ADD COLUMN percentual_conclusao INT NOT NULL DEFAULT 100;
ALTER TABLE turmas ADD COLUMN carga_horaria_horas INT;
ALTER TABLE turmas ADD CONSTRAINT ck_turmas_percentual_conclusao CHECK (percentual_conclusao BETWEEN 1 AND 100);
ALTER TABLE turmas ADD CONSTRAINT ck_turmas_carga_horaria CHECK (carga_horaria_horas IS NULL OR carga_horaria_horas > 0);

-- Certificado emitido: um por aluno e turma. Os dados impressos ficam congelados na emissão (mudar o
-- título do curso depois não altera um certificado já entregue); o código é público e valida o documento.
CREATE TABLE certificados (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id            BIGINT        NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    turma_id              BIGINT        NOT NULL REFERENCES turmas (id) ON DELETE RESTRICT,
    codigo                VARCHAR(20)   NOT NULL,
    nome_completo         VARCHAR(150)  NOT NULL,
    curso_titulo          VARCHAR(120)  NOT NULL,
    turma_nome            VARCHAR(120)  NOT NULL,
    carga_horaria_horas   INT,
    percentual_concluido  INT           NOT NULL,
    pdf_chave             VARCHAR(200)  NOT NULL,
    emitido_em            TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_certificados_codigo UNIQUE (codigo),
    CONSTRAINT uq_certificados_aluno_turma UNIQUE (usuario_id, turma_id)
);
`````

### 3. Domínio

`backend/src/main/java/br/com/celke/cursos/certificado/domain/RegraConclusao.java`:

`````java
package br.com.celke.cursos.certificado.domain;

/** O aluno concluiu a turma quando o percentual de aulas vistas alcança o mínimo da turma. */
public final class RegraConclusao {

    private RegraConclusao() {}

    /**
     * @param percentualVisto aulas vistas, de 0 a 100 (arredondado para baixo)
     * @param minimo percentual exigido pela turma, de 1 a 100
     * @param totalAulas aulas ativas na turma: turma sem aulas nunca é concluída
     */
    public static boolean concluiu(int percentualVisto, int minimo, int totalAulas) {
        return totalAulas > 0 && percentualVisto >= minimo;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/certificado/domain/CodigoValidacao.java`:

`````java
package br.com.celke.cursos.certificado.domain;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Código público que valida o certificado: 12 caracteres aleatórios em três grupos ({@code K7QM-2XRP-H9TD}),
 * sem letras e números que se confundem (0/O, 1/I/L, U). São 60 bits de acaso: não dá para adivinhar um
 * código válido.
 */
public final class CodigoValidacao {

    static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final Pattern FORMATO =
            Pattern.compile("[" + ALFABETO + "]{4}-[" + ALFABETO + "]{4}-[" + ALFABETO + "]{4}");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private CodigoValidacao() {}

    public static String gerar() {
        StringBuilder codigo = new StringBuilder(14);
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                codigo.append('-');
            }
            codigo.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }

    /** Código digitado normalizado (maiúsculas, sem espaços), ou nulo se não tem o formato. */
    public static String normalizar(String digitado) {
        if (digitado == null) {
            return null;
        }
        String codigo = digitado.strip().toUpperCase(Locale.ROOT);
        return FORMATO.matcher(codigo).matches() ? codigo : null;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/certificado/domain/NomeCertificado.java`:

`````java
package br.com.celke.cursos.certificado.domain;

import java.util.regex.Pattern;

/**
 * Nome completo confirmado pelo aluno para o certificado: letras (acentuadas inclusive), espaço, hífen e
 * apóstrofo, com pelo menos nome e sobrenome. Espaços repetidos viram um só.
 */
public final class NomeCertificado {

    private static final Pattern PERMITIDO = Pattern.compile("[\\p{L}' -]+");

    private NomeCertificado() {}

    /** Nome normalizado, ou nulo se não é um nome completo aceitável. */
    public static String normalizar(String informado) {
        if (informado == null) {
            return null;
        }
        String nome = informado.strip().replaceAll("\\s+", " ");
        if (nome.length() < 5 || nome.length() > 150 || !PERMITIDO.matcher(nome).matches() || !nome.contains(" ")) {
            return null;
        }
        return nome;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/certificado/domain/Certificado.java`:

`````java
package br.com.celke.cursos.certificado.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Certificado emitido para um aluno numa turma, com os dados impressos congelados na emissão. */
@Entity
@Table(name = "certificados")
public class Certificado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "turma_id", nullable = false, updatable = false)
    private Long turmaId;

    @Column(nullable = false, length = 20, updatable = false)
    private String codigo;

    @Column(name = "nome_completo", nullable = false, length = 150, updatable = false)
    private String nomeCompleto;

    @Column(name = "curso_titulo", nullable = false, length = 120, updatable = false)
    private String cursoTitulo;

    @Column(name = "turma_nome", nullable = false, length = 120, updatable = false)
    private String turmaNome;

    @Column(name = "carga_horaria_horas", updatable = false)
    private Integer cargaHorariaHoras;

    @Column(name = "percentual_concluido", nullable = false, updatable = false)
    private int percentualConcluido;

    @Column(name = "pdf_chave", nullable = false, length = 200, updatable = false)
    private String pdfChave;

    @Column(name = "emitido_em", nullable = false, updatable = false)
    private Instant emitidoEm;

    protected Certificado() {}

    public Certificado(
            Long usuarioId,
            Long turmaId,
            String codigo,
            DadosImpressos dados,
            int percentualConcluido,
            String pdfChave,
            Instant emitidoEm) {
        this.usuarioId = usuarioId;
        this.turmaId = turmaId;
        this.codigo = codigo;
        this.nomeCompleto = dados.nomeCompleto();
        this.cursoTitulo = dados.cursoTitulo();
        this.turmaNome = dados.turmaNome();
        this.cargaHorariaHoras = dados.cargaHorariaHoras();
        this.percentualConcluido = percentualConcluido;
        this.pdfChave = pdfChave;
        this.emitidoEm = emitidoEm;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getTurmaId() {
        return turmaId;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public String getCursoTitulo() {
        return cursoTitulo;
    }

    public String getTurmaNome() {
        return turmaNome;
    }

    public Integer getCargaHorariaHoras() {
        return cargaHorariaHoras;
    }

    public int getPercentualConcluido() {
        return percentualConcluido;
    }

    public String getPdfChave() {
        return pdfChave;
    }

    public Instant getEmitidoEm() {
        return emitidoEm;
    }

    /** O que vai impresso no certificado. */
    public record DadosImpressos(
            String nomeCompleto, String cursoTitulo, String turmaNome, Integer cargaHorariaHoras) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/certificado/infra/CertificadoRepository.java`:

`````java
package br.com.celke.cursos.certificado.infra;

import br.com.celke.cursos.certificado.domain.Certificado;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificadoRepository extends JpaRepository<Certificado, Long> {

    Optional<Certificado> findByUsuarioIdAndTurmaId(Long usuarioId, Long turmaId);

    Optional<Certificado> findByCodigo(String codigo);

    Optional<Certificado> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Certificado> findByUsuarioIdOrderByEmitidoEmDesc(Long usuarioId);
}
`````

### 4. PDF

`backend/src/main/resources/templates/certificado/certificado.html`:

`````html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="pt-BR">
<head>
<meta charset="UTF-8" />
<title>Certificado</title>
<style>
    @page { size: A4 landscape; margin: 18mm; }
    body { font-family: serif; color: #1f2933; text-align: center; }
    .moldura { border: 3px double #3b4a5a; padding: 22mm 18mm; height: 130mm; }
    .titulo { font-size: 34pt; letter-spacing: 6pt; margin: 0 0 10mm 0; color: #3b4a5a; }
    .texto { font-size: 15pt; line-height: 1.6; margin: 0; }
    .nome { font-size: 26pt; font-weight: bold; margin: 8mm 0; }
    .curso { font-weight: bold; }
    .rodape { margin-top: 14mm; font-size: 10pt; color: #52606d; }
    .codigo { font-family: monospace; font-size: 12pt; letter-spacing: 2pt; color: #1f2933; }
</style>
</head>
<body>
<div class="moldura">
    <p class="titulo">CERTIFICADO</p>
    <p class="texto">Certificamos que</p>
    <p class="nome" th:text="${nome}">Nome do aluno</p>
    <p class="texto">
        concluiu o curso <span class="curso" th:text="${curso}">Curso</span>
        (turma <span th:text="${turma}">Turma</span>)<span th:if="${cargaHoraria != null}">, com carga horária de
        <span th:text="${cargaHoraria}">40</span> horas</span>,
        em <span th:text="${emitidoEm}">1º de janeiro de 2026</span>.
    </p>
    <p class="rodape">
        Código de validação: <span class="codigo" th:text="${codigo}">XXXX-XXXX-XXXX</span><br />
        Confira a autenticidade informando o código na página de validação de certificados da plataforma.
    </p>
</div>
</body>
</html>
`````

`backend/src/main/java/br/com/celke/cursos/certificado/infra/GeradorPdf.java`:

`````java
package br.com.celke.cursos.certificado.infra;

import br.com.celke.cursos.certificado.domain.Certificado.DadosImpressos;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Gera o PDF do certificado: o template Thymeleaf {@code certificado/certificado} (HTML bem formado, com os
 * dados escapados pelo Thymeleaf) é convertido pelo openhtmltopdf, sem acesso a rede nem a arquivos externos.
 */
@Component
public class GeradorPdf {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PT_BR);

    private final TemplateEngine templateEngine;

    public GeradorPdf(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public byte[] gerar(DadosImpressos dados, String codigo, Instant emitidoEm) {
        Context contexto = new Context(PT_BR);
        contexto.setVariable("nome", dados.nomeCompleto());
        contexto.setVariable("curso", dados.cursoTitulo());
        contexto.setVariable("turma", dados.turmaNome());
        contexto.setVariable("cargaHoraria", dados.cargaHorariaHoras());
        contexto.setVariable("emitidoEm", DATA.format(emitidoEm.atZone(BRASILIA)));
        contexto.setVariable("codigo", codigo);
        String html = templateEngine.process("certificado/certificado", contexto);
        try (ByteArrayOutputStream pdf = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null)
                    .toStream(pdf)
                    .run();
            return pdf.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gerar o PDF do certificado.", e);
        }
    }
}
`````

### 5. Caso de uso e API

`backend/src/main/java/br/com/celke/cursos/certificado/application/CertificadoService.java`:

`````java
package br.com.celke.cursos.certificado.application;

import br.com.celke.cursos.certificado.domain.Certificado;
import br.com.celke.cursos.certificado.domain.Certificado.DadosImpressos;
import br.com.celke.cursos.certificado.domain.CodigoValidacao;
import br.com.celke.cursos.certificado.domain.NomeCertificado;
import br.com.celke.cursos.certificado.domain.RegraConclusao;
import br.com.celke.cursos.certificado.infra.CertificadoRepository;
import br.com.celke.cursos.certificado.infra.GeradorPdf;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.RegraCertificado;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.progresso.application.ProgressoService;
import br.com.celke.cursos.progresso.application.ProgressoService.ProgressoTurma;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Certificado da turma: o aluno com matrícula vigente que viu o percentual mínimo de aulas da turma
 * confirma o nome completo e recebe o PDF, gerado uma vez e guardado no bucket. O código impresso valida
 * o certificado publicamente. Emitir de novo devolve o mesmo certificado.
 */
@Service
public class CertificadoService {

    static final Duration VALIDADE_DOWNLOAD = Duration.ofMinutes(5);

    private final CertificadoRepository certificados;
    private final ProgressoService progressoService;
    private final ConteudoTurmaService conteudoTurmaService;
    private final GeradorPdf geradorPdf;
    private final StorageService storage;
    private final Clock clock;

    public CertificadoService(
            CertificadoRepository certificados,
            ProgressoService progressoService,
            ConteudoTurmaService conteudoTurmaService,
            GeradorPdf geradorPdf,
            StorageService storage,
            Clock clock) {
        this.certificados = certificados;
        this.progressoService = progressoService;
        this.conteudoTurmaService = conteudoTurmaService;
        this.geradorPdf = geradorPdf;
        this.storage = storage;
        this.clock = clock;
    }

    /** Quanto falta para o certificado e, se já emitido, o certificado (exige matrícula vigente). */
    public Situacao situacao(Long usuarioId, Long turmaId) {
        ProgressoTurma progresso = progressoService.daTurma(usuarioId, turmaId);
        RegraCertificado regra = regra(turmaId);
        return new Situacao(
                progresso.percentual(),
                regra.percentualConclusao(),
                RegraConclusao.concluiu(progresso.percentual(), regra.percentualConclusao(), progresso.totalAulas()),
                certificados.findByUsuarioIdAndTurmaId(usuarioId, turmaId));
    }

    /**
     * Emite o certificado com o nome confirmado pelo aluno. Já emitido: devolve o existente (o nome não muda).
     * Sem atingir o mínimo de aulas vistas: 409.
     */
    public Emissao emitir(Long usuarioId, Long turmaId, String nomeInformado) {
        Situacao situacao = situacao(usuarioId, turmaId);
        if (situacao.certificado().isPresent()) {
            return new Emissao(situacao.certificado().get(), false);
        }
        String nome = NomeCertificado.normalizar(nomeInformado);
        if (nome == null) {
            throw new RegraNegocioException(
                    HttpStatus.BAD_REQUEST, "Informe o nome completo como deve sair no certificado.");
        }
        if (!situacao.concluiu()) {
            throw new ConflitoException("Você ainda não concluiu a turma: %d%% das aulas vistas, de %d%% exigidos."
                    .formatted(situacao.percentualVisto(), situacao.percentualExigido()));
        }
        TurmaResumo turma = conteudoTurmaService
                .turma(turmaId)
                .orElseThrow(() -> new NaoEncontradoException("Turma não encontrada."));
        DadosImpressos dados = new DadosImpressos(
                nome, turma.cursoTitulo(), turma.turmaNome(), regra(turmaId).cargaHorariaHoras());
        Instant agora = clock.instant();
        String codigo = CodigoValidacao.gerar();
        String chave = "certificados/%d/%s.pdf".formatted(turmaId, codigo);
        storage.gravar(chave, geradorPdf.gerar(dados, codigo, agora), "application/pdf");
        try {
            return new Emissao(
                    certificados.saveAndFlush(new Certificado(
                            usuarioId, turmaId, codigo, dados, situacao.percentualVisto(), chave, agora)),
                    true);
        } catch (DataIntegrityViolationException e) {
            // Duas emissões ao mesmo tempo: fica a que gravou primeiro
            storage.excluir(chave);
            return new Emissao(
                    certificados.findByUsuarioIdAndTurmaId(usuarioId, turmaId).orElseThrow(() -> e), false);
        }
    }

    public List<Certificado> meus(Long usuarioId) {
        return certificados.findByUsuarioIdOrderByEmitidoEmDesc(usuarioId);
    }

    /** URL do PDF do próprio aluno (de outro aluno, 404), válida por 5 minutos. */
    public Download download(Long usuarioId, Long certificadoId) {
        Certificado certificado = certificados
                .findByIdAndUsuarioId(certificadoId, usuarioId)
                .orElseThrow(() -> new NaoEncontradoException("Certificado não encontrado."));
        URL url = storage.urlDownload(
                certificado.getPdfChave(), VALIDADE_DOWNLOAD, "certificado-%s.pdf".formatted(certificado.getCodigo()));
        return new Download(url, clock.instant().plus(VALIDADE_DOWNLOAD));
    }

    /** Validação pública pelo código; formato inválido ou código inexistente: 404. */
    public Certificado validar(String codigo) {
        String normalizado = CodigoValidacao.normalizar(codigo);
        return Optional.ofNullable(normalizado)
                .flatMap(certificados::findByCodigo)
                .orElseThrow(() -> new NaoEncontradoException("Certificado não encontrado."));
    }

    private RegraCertificado regra(Long turmaId) {
        return conteudoTurmaService
                .regraCertificado(turmaId)
                .orElseThrow(() -> new NaoEncontradoException("Turma não encontrada."));
    }

    public record Situacao(
            int percentualVisto, int percentualExigido, boolean concluiu, Optional<Certificado> certificado) {}

    public record Download(URL url, Instant expiraEm) {}

    /** @param novo {@code false} quando o certificado já existia */
    public record Emissao(Certificado certificado, boolean novo) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/certificado/api/CertificadoResponses.java`:

`````java
package br.com.celke.cursos.certificado.api;

import br.com.celke.cursos.certificado.application.CertificadoService.Situacao;
import br.com.celke.cursos.certificado.domain.Certificado;
import java.time.Instant;

/** Respostas das rotas de certificado. A validação pública não expõe e-mail nem ids internos. */
public final class CertificadoResponses {

    private CertificadoResponses() {}

    public record CertificadoAluno(
            Long id,
            Long turmaId,
            String codigo,
            String nomeCompleto,
            String cursoTitulo,
            String turmaNome,
            Integer cargaHorariaHoras,
            Instant emitidoEm) {

        public static CertificadoAluno de(Certificado c) {
            return new CertificadoAluno(
                    c.getId(),
                    c.getTurmaId(),
                    c.getCodigo(),
                    c.getNomeCompleto(),
                    c.getCursoTitulo(),
                    c.getTurmaNome(),
                    c.getCargaHorariaHoras(),
                    c.getEmitidoEm());
        }
    }

    /** {@code certificado} só vem quando já foi emitido. */
    public record SituacaoResponse(
            int percentualVisto, int percentualExigido, boolean concluiu, CertificadoAluno certificado) {

        public static SituacaoResponse de(Situacao s) {
            return new SituacaoResponse(
                    s.percentualVisto(),
                    s.percentualExigido(),
                    s.concluiu(),
                    s.certificado().map(CertificadoAluno::de).orElse(null));
        }
    }

    /** O que a página "validar certificado" mostra. */
    public record Validacao(
            String codigo,
            String nomeCompleto,
            String cursoTitulo,
            String turmaNome,
            Integer cargaHorariaHoras,
            Instant emitidoEm) {

        public static Validacao de(Certificado c) {
            return new Validacao(
                    c.getCodigo(),
                    c.getNomeCompleto(),
                    c.getCursoTitulo(),
                    c.getTurmaNome(),
                    c.getCargaHorariaHoras(),
                    c.getEmitidoEm());
        }
    }

    public record DownloadResponse(String url, Instant expiraEm) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/certificado/api/CertificadoController.java`:

`````java
package br.com.celke.cursos.certificado.api;

import br.com.celke.cursos.certificado.api.CertificadoResponses.CertificadoAluno;
import br.com.celke.cursos.certificado.api.CertificadoResponses.DownloadResponse;
import br.com.celke.cursos.certificado.api.CertificadoResponses.SituacaoResponse;
import br.com.celke.cursos.certificado.api.CertificadoResponses.Validacao;
import br.com.celke.cursos.certificado.application.CertificadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Certificados")
public class CertificadoController {

    private final CertificadoService certificadoService;

    public CertificadoController(CertificadoService certificadoService) {
        this.certificadoService = certificadoService;
    }

    @GetMapping("/api/aluno/turmas/{turmaId}/certificado")
    @Operation(
            summary = "Situação do certificado na turma",
            description = "Percentual visto e exigido; traz o certificado se já emitido. Exige matrícula vigente.")
    public SituacaoResponse situacao(@AuthenticationPrincipal Jwt jwt, @PathVariable Long turmaId) {
        return SituacaoResponse.de(certificadoService.situacao(usuario(jwt), turmaId));
    }

    @PostMapping("/api/aluno/turmas/{turmaId}/certificado")
    @Operation(
            summary = "Emitir o certificado",
            description = "Com o nome completo confirmado pelo aluno. 201 na emissão; 200 se já existia.")
    public ResponseEntity<CertificadoAluno> emitir(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long turmaId, @Valid @RequestBody Emitir request) {
        var emissao = certificadoService.emitir(usuario(jwt), turmaId, request.nomeCompleto());
        CertificadoAluno certificado = CertificadoAluno.de(emissao.certificado());
        return emissao.novo()
                ? ResponseEntity.created(URI.create("/api/certificados/" + certificado.codigo()))
                        .body(certificado)
                : ResponseEntity.ok(certificado);
    }

    @GetMapping("/api/aluno/certificados")
    @Operation(summary = "Meus certificados", description = "Também os de matrículas já encerradas.")
    public List<CertificadoAluno> meus(@AuthenticationPrincipal Jwt jwt) {
        return certificadoService.meus(usuario(jwt)).stream()
                .map(CertificadoAluno::de)
                .toList();
    }

    @GetMapping("/api/aluno/certificados/{id}/download")
    @Operation(summary = "Baixar o PDF", description = "URL válida por 5 minutos; certificado de outro aluno, 404.")
    public ResponseEntity<DownloadResponse> download(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        var download = certificadoService.download(usuario(jwt), id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new DownloadResponse(download.url().toString(), download.expiraEm()));
    }

    @GetMapping("/api/certificados/{codigo}")
    @Operation(
            summary = "Validar certificado",
            description = "Público: confere o código impresso no certificado e devolve os dados dele.")
    public Validacao validar(@PathVariable String codigo) {
        return Validacao.de(certificadoService.validar(codigo));
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    public record Emitir(@NotBlank @Size(max = 150) String nomeCompleto) {}
}
`````

### 6. Testes unitários

`backend/src/test/java/br/com/celke/cursos/certificado/domain/CertificadoDominioTest.java`:

`````java
package br.com.celke.cursos.certificado.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CertificadoDominioTest {

    @Nested
    class Regra {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "um ponto abaixo do mínimo,      79, 80,  10, false",
            "exatamente o mínimo,            80, 80,  10, true",
            "acima do mínimo,                95, 80,  10, true",
            "mínimo de 100 com 99,           99, 100, 10, false",
            "mínimo de 100 com todas,        100, 100, 10, true",
            "mínimo de 1 sem nenhuma vista,  0, 1,    10, false",
            "turma sem aulas nunca conclui,  0, 1,    0,  false",
        })
        @DisplayName("concluiu quando as aulas vistas alcançam o mínimo da turma")
        void conclusao(String caso, int visto, int minimo, int total, boolean esperado) {
            assertThat(RegraConclusao.concluiu(visto, minimo, total)).isEqualTo(esperado);
        }
    }

    @Nested
    class Codigo {

        @Test
        @DisplayName("12 caracteres do alfabeto sem ambíguos, em três grupos, diferentes a cada geração")
        void gerar() {
            Set<String> gerados = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                String codigo = CodigoValidacao.gerar();
                assertThat(codigo)
                        .matches("[23456789ABCDEFGHJKMNPQRSTVWXYZ]{4}(-[23456789ABCDEFGHJKMNPQRSTVWXYZ]{4}){2}");
                gerados.add(codigo);
            }
            assertThat(gerados).hasSize(1000);
        }

        @Test
        @DisplayName("normaliza o código digitado: espaços nas pontas e minúsculas")
        void normalizar() {
            assertThat(CodigoValidacao.normalizar("  k7qm-2xrp-h9td ")).isEqualTo("K7QM-2XRP-H9TD");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "K7QM2XRPH9TD", "K7QM-2XRP-H9T", "K7QM-2XRP-H9TO", "K7QM-2XRP-H9T1", "' OR 1=1 --"})
        @DisplayName("formato inválido (tamanho, sem hífens, letras ambíguas, injeção) vira nulo")
        void invalido(String digitado) {
            assertThat(CodigoValidacao.normalizar(digitado)).isNull();
        }
    }

    @Nested
    class Nome {

        @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
        @CsvSource({
            "'Ana Souza',                     'Ana Souza'",
            "'  João   da  Silva  ',          'João da Silva'",
            "'Maria D''Ávila Gonçalves-Lima', 'Maria D''Ávila Gonçalves-Lima'",
        })
        @DisplayName("nome completo aceito, com espaços normalizados")
        void aceito(String informado, String esperado) {
            assertThat(NomeCertificado.normalizar(informado)).isEqualTo(esperado);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"Ana", "A B", "Ana Souza 2", "<b>Ana</b> Souza", "Ana_Souza Lima"})
        @DisplayName("sem sobrenome, curto demais, com número ou símbolos: recusado")
        void recusado(String informado) {
            assertThat(NomeCertificado.normalizar(informado)).isNull();
        }

        @Test
        @DisplayName("nome com mais de 150 caracteres é recusado")
        void longo() {
            assertThat(NomeCertificado.normalizar("Ana " + "a".repeat(147))).isNull();
            assertThat(NomeCertificado.normalizar("Ana " + "a".repeat(146))).isNotNull();
        }
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/certificado/application/CertificadoServiceTest.java`:

`````java
package br.com.celke.cursos.certificado.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.certificado.domain.Certificado;
import br.com.celke.cursos.certificado.domain.Certificado.DadosImpressos;
import br.com.celke.cursos.certificado.infra.CertificadoRepository;
import br.com.celke.cursos.certificado.infra.GeradorPdf;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.RegraCertificado;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.progresso.application.ProgressoService;
import br.com.celke.cursos.progresso.application.ProgressoService.ProgressoTurma;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CertificadoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private CertificadoRepository certificados;

    @Mock
    private ProgressoService progressoService;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private GeradorPdf geradorPdf;

    @Mock
    private StorageService storage;

    private CertificadoService service;

    @BeforeEach
    void criar() {
        service = new CertificadoService(
                certificados,
                progressoService,
                conteudoTurmaService,
                geradorPdf,
                storage,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void progresso(int percentual, int totalAulas) {
        when(progressoService.daTurma(7L, 5L)).thenReturn(new ProgressoTurma(5L, totalAulas, 0, percentual, List.of()));
    }

    private void regra(int minimo, Integer carga) {
        when(conteudoTurmaService.regraCertificado(5L)).thenReturn(Optional.of(new RegraCertificado(minimo, carga)));
    }

    private static Certificado existente() {
        return new Certificado(
                7L,
                5L,
                "K7QM-2XRP-H9TD",
                new DadosImpressos("Ana Souza", "Java", "T1", 40),
                100,
                "certificados/5/x.pdf",
                AGORA);
    }

    @Test
    @DisplayName("situação: percentual visto e exigido, concluído ou não")
    void situacao() {
        progresso(80, 10);
        regra(80, null);
        when(certificados.findByUsuarioIdAndTurmaId(7L, 5L)).thenReturn(Optional.empty());

        var situacao = service.situacao(7L, 5L);

        assertThat(situacao.percentualVisto()).isEqualTo(80);
        assertThat(situacao.percentualExigido()).isEqualTo(80);
        assertThat(situacao.concluiu()).isTrue();
        assertThat(situacao.certificado()).isEmpty();
    }

    @Test
    @DisplayName("emite: gera o PDF com os dados congelados, guarda no bucket e registra")
    void emite() {
        progresso(100, 3);
        regra(100, 40);
        when(certificados.findByUsuarioIdAndTurmaId(7L, 5L)).thenReturn(Optional.empty());
        when(conteudoTurmaService.turma(5L))
                .thenReturn(Optional.of(new TurmaResumo(
                        5L, "Turma 1", StatusTurma.ATIVA, 1L, "Java Moderno", "java", StatusCurso.PUBLICADO, null)));
        when(geradorPdf.gerar(any(), anyString(), eq(AGORA))).thenReturn(new byte[] {'%', 'P', 'D', 'F'});
        when(certificados.saveAndFlush(any(Certificado.class))).thenAnswer(i -> i.getArgument(0));

        var emissao = service.emitir(7L, 5L, "  Ana   Souza ");

        assertThat(emissao.novo()).isTrue();
        Certificado c = emissao.certificado();
        assertThat(c.getNomeCompleto()).isEqualTo("Ana Souza");
        assertThat(c.getCursoTitulo()).isEqualTo("Java Moderno");
        assertThat(c.getTurmaNome()).isEqualTo("Turma 1");
        assertThat(c.getCargaHorariaHoras()).isEqualTo(40);
        assertThat(c.getPercentualConcluido()).isEqualTo(100);
        assertThat(c.getEmitidoEm()).isEqualTo(AGORA);
        assertThat(c.getPdfChave()).isEqualTo("certificados/5/" + c.getCodigo() + ".pdf");
        verify(storage).gravar(c.getPdfChave(), new byte[] {'%', 'P', 'D', 'F'}, "application/pdf");
        ArgumentCaptor<DadosImpressos> dados = ArgumentCaptor.forClass(DadosImpressos.class);
        verify(geradorPdf).gerar(dados.capture(), eq(c.getCodigo()), eq(AGORA));
        assertThat(dados.getValue().nomeCompleto()).isEqualTo("Ana Souza");
    }

    @Test
    @DisplayName("já emitido: devolve o mesmo certificado, sem gerar outro PDF nem mudar o nome")
    void jaEmitido() {
        progresso(100, 3);
        regra(100, null);
        when(certificados.findByUsuarioIdAndTurmaId(7L, 5L)).thenReturn(Optional.of(existente()));

        var emissao = service.emitir(7L, 5L, "Outro Nome");

        assertThat(emissao.novo()).isFalse();
        assertThat(emissao.certificado().getNomeCompleto()).isEqualTo("Ana Souza");
        verify(geradorPdf, never()).gerar(any(), anyString(), any());
    }

    @Test
    @DisplayName("sem concluir: 409 com o percentual visto e o exigido")
    void naoConcluiu() {
        progresso(79, 10);
        regra(80, null);
        when(certificados.findByUsuarioIdAndTurmaId(7L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.emitir(7L, 5L, "Ana Souza"))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Você ainda não concluiu a turma: 79% das aulas vistas, de 80% exigidos.");
        verify(storage, never()).gravar(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("nome sem sobrenome: 400 antes de qualquer geração")
    void nomeInvalido() {
        progresso(100, 3);
        regra(100, null);
        when(certificados.findByUsuarioIdAndTurmaId(7L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.emitir(7L, 5L, "Ana"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Informe o nome completo como deve sair no certificado.");
        verify(geradorPdf, never()).gerar(any(), anyString(), any());
    }

    @Test
    @DisplayName("duas emissões ao mesmo tempo: fica a primeira e o PDF da segunda é apagado")
    void corrida() {
        progresso(100, 3);
        regra(100, null);
        when(certificados.findByUsuarioIdAndTurmaId(7L, 5L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existente()));
        when(conteudoTurmaService.turma(5L))
                .thenReturn(Optional.of(
                        new TurmaResumo(5L, "T", StatusTurma.ATIVA, 1L, "C", "c", StatusCurso.PUBLICADO, null)));
        when(geradorPdf.gerar(any(), anyString(), any())).thenReturn(new byte[] {1});
        when(certificados.saveAndFlush(any(Certificado.class))).thenThrow(new DataIntegrityViolationException("uq"));

        var emissao = service.emitir(7L, 5L, "Ana Souza");

        assertThat(emissao.novo()).isFalse();
        assertThat(emissao.certificado().getCodigo()).isEqualTo("K7QM-2XRP-H9TD");
        verify(storage).excluir(startsWith("certificados/5/"));
    }

    @Test
    @DisplayName("download só do próprio certificado, por 5 minutos e com nome de arquivo")
    void download() throws Exception {
        when(certificados.findByIdAndUsuarioId(1L, 7L)).thenReturn(Optional.of(existente()));
        when(certificados.findByIdAndUsuarioId(1L, 8L)).thenReturn(Optional.empty());
        when(storage.urlDownload("certificados/5/x.pdf", Duration.ofMinutes(5), "certificado-K7QM-2XRP-H9TD.pdf"))
                .thenReturn(URI.create("http://media.localhost/pdf").toURL());

        assertThat(service.download(7L, 1L).expiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(5)));
        assertThatThrownBy(() -> service.download(8L, 1L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Certificado não encontrado.");
    }

    @Test
    @DisplayName("validação pública: código normalizado; inválido ou inexistente responde 404 sem consultar à toa")
    void validar() {
        when(certificados.findByCodigo("K7QM-2XRP-H9TD")).thenReturn(Optional.of(existente()));

        assertThat(service.validar(" k7qm-2xrp-h9td ").getNomeCompleto()).isEqualTo("Ana Souza");
        assertThatThrownBy(() -> service.validar("nao-e-codigo"))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Certificado não encontrado.");
        verify(certificados, never()).findByCodigo("nao-e-codigo");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/domain/TurmaCertificadoTest.java`:

`````java
package br.com.celke.cursos.curso.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TurmaCertificadoTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    private static Turma turma() {
        return new Turma(new Curso("Java", "java", "Resumo", null, AGORA), "Turma", AGORA);
    }

    @Test
    @DisplayName("turma nova exige todas as aulas e não tem carga horária")
    void padrao() {
        assertThat(turma().getPercentualConclusao()).isEqualTo(100);
        assertThat(turma().getCargaHorariaHoras()).isNull();
    }

    @Test
    @DisplayName("define o percentual mínimo e a carga horária; os limites 1 e 100 são aceitos")
    void define() {
        Turma turma = turma();

        turma.definirCertificado(1, 40, AGORA);
        assertThat(turma.getPercentualConclusao()).isEqualTo(1);
        assertThat(turma.getCargaHorariaHoras()).isEqualTo(40);

        turma.definirCertificado(100, null, AGORA);
        assertThat(turma.getPercentualConclusao()).isEqualTo(100);
        assertThat(turma.getCargaHorariaHoras()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    @DisplayName("percentual fora de 1 a 100 é recusado")
    void percentualInvalido(int percentual) {
        assertThatThrownBy(() -> turma().definirCertificado(percentual, null, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O percentual de conclusão vai de 1 a 100.");
    }

    @Test
    @DisplayName("carga horária zero é recusada")
    void cargaInvalida() {
        assertThatThrownBy(() -> turma().definirCertificado(80, 0, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A carga horária precisa ser positiva.");
    }
}
`````

### 7. Testes de integração

`backend/src/test/java/br/com/celke/cursos/certificado/CertificadoIT.java`:

`````java
package br.com.celke.cursos.certificado;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.certificado.infra.CertificadoRepository;
import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
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
 * Cobre: certificado — situação e regra de conclusão da turma (padrão 100% e configurada pelo ADM),
 * emissão com o nome confirmado, PDF no Garage, emissão repetida devolvendo o mesmo, validação pública
 * pelo código (sem dados internos), download só do próprio aluno, aluno sem matrícula, matriz de papéis
 * da regra no ADM e validação do corpo.
 * Não cobre: bordas da regra e do nome (CertificadoDominioTest, CertificadoServiceTest).
 * Dado criado: curso it-<stamp> publicado com duas aulas e alunos próprios.
 */
class CertificadoIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private CertificadoRepository certificados;

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
        aluno = criarUsuario(Papel.ALUNO);
        Instant agora = Instant.now();
        helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), agora.plus(DIA));
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

    private String rota() {
        return "/api/aluno/turmas/" + estrutura.turma() + "/certificado";
    }

    private MvcTestResult emitir(String nome, String tokenUsado) {
        return chamar(HttpMethod.POST, rota(), "{\"nomeCompleto\":\"%s\"}".formatted(nome), tokenUsado);
    }

    private void marcarVista(long aula) {
        assertThat(chamar(HttpMethod.PATCH, "/api/aluno/aulas/" + aula + "/progresso", "{\"vista\":true}", token))
                .hasStatusOk();
    }

    @Test
    @DisplayName("conclui a turma, emite com o nome confirmado, valida pelo código e baixa o PDF")
    void cicloDoCertificado() throws Exception {
        assertThat(chamar(HttpMethod.GET, rota(), null, token))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                {"percentualVisto":0,"percentualExigido":100,"concluiu":false,"certificado":null}
                """);
        assertThat(emitir("Ana Souza", token))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você ainda não concluiu a turma: 0% das aulas vistas, de 100% exigidos.");

        marcarVista(estrutura.aulaPaga());
        assertThat(emitir("Ana Souza", token)).hasStatus(HttpStatus.CONFLICT);
        marcarVista(estrutura.aulaGratuita());

        MvcTestResult emitido = emitir("  Ana   Souza  ", token);
        assertThat(emitido).hasStatus(HttpStatus.CREATED);
        String codigo = JsonPath.read(CursoITHelper.corpo(emitido), "$.codigo");
        assertThat(codigo).matches("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}");
        assertThat(emitido.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/certificados/" + codigo);
        assertThat(emitido).bodyJson().extractingPath("$.nomeCompleto").isEqualTo("Ana Souza");

        // PDF guardado no bucket
        var certificado = certificados.findByCodigo(codigo).orElseThrow();
        byte[] pdf = storage.baixar(certificado.getPdfChave());
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        // Validação pública, sem token e sem dados internos
        MvcTestResult validacao = chamar(HttpMethod.GET, "/api/certificados/" + codigo.toLowerCase(), null, null);
        assertThat(validacao).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"codigo":"%s","nomeCompleto":"Ana Souza","turmaNome":"Turma"}
                """.formatted(codigo));
        assertThat(CursoITHelper.corpo(validacao))
                .doesNotContain("email")
                .doesNotContain("usuarioId")
                .doesNotContain("pdf");

        // Emitir de novo devolve o mesmo, sem trocar o nome
        assertThat(emitir("Outro Nome", token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.codigo")
                .isEqualTo(codigo);
        assertThat(chamar(HttpMethod.GET, rota(), null, token))
                .bodyJson()
                .extractingPath("$.certificado.codigo")
                .isEqualTo(codigo);

        // Meus certificados e download
        assertThat(chamar(HttpMethod.GET, "/api/aluno/certificados", null, token))
                .bodyJson()
                .extractingPath("$[*].codigo")
                .isEqualTo(List.of(codigo));
        long id = certificado.getId();
        String url = JsonPath.read(
                CursoITHelper.corpo(chamar(HttpMethod.GET, "/api/aluno/certificados/" + id + "/download", null, token)),
                "$.url");
        HttpResponse<byte[]> baixado =
                http.send(HttpRequest.newBuilder(URI.create(url)).build(), BodyHandlers.ofByteArray());
        assertThat(baixado.statusCode()).isEqualTo(200);
        assertThat(baixado.body()).isEqualTo(pdf);
        assertThat(baixado.headers().firstValue("Content-Disposition"))
                .contains("attachment; filename=\"certificado-" + codigo + ".pdf\"");

        // Outro aluno não baixa o certificado deste
        UsuarioTeste outro = criarUsuario(Papel.ALUNO);
        assertThat(chamar(
                        HttpMethod.GET,
                        "/api/aluno/certificados/" + id + "/download",
                        null,
                        login(outro.email(), outro.senha())))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("regra configurada pelo ADM: 50% e carga horária impressa")
    void regraDaTurma() {
        assertThat(helper.adm()
                        .enviar(
                                "PUT",
                                "/api/adm/turmas/" + estrutura.turma() + "/certificado",
                                "{\"percentualConclusao\":50,\"cargaHorariaHoras\":12}"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"percentualConclusao\":50,\"cargaHorariaHoras\":12}");

        marcarVista(estrutura.aulaPaga());

        assertThat(chamar(HttpMethod.GET, rota(), null, token))
                .bodyJson()
                .isLenientlyEqualTo("{\"percentualVisto\":50,\"percentualExigido\":50,\"concluiu\":true}");
        assertThat(emitir("Bruno Lima", token))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.cargaHorariaHoras")
                .isEqualTo(12);
    }

    @Test
    @DisplayName("regra no ADM: 401 anônimo, 403 aluno, 400 fora de 1 a 100")
    void regraAcesso() {
        String uri = "/api/adm/turmas/" + estrutura.turma() + "/certificado";
        String corpo = "{\"percentualConclusao\":80}";

        assertThat(chamar(HttpMethod.PUT, uri, corpo, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.PUT, uri, corpo, token)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(helper.adm().enviar("PUT", uri, "{\"percentualConclusao\":0}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("percentualConclusao"));
    }

    @Test
    @DisplayName("sem matrícula vigente: 403 na situação e na emissão; anônimo 401")
    void semMatricula() {
        UsuarioTeste semMatricula = criarUsuario(Papel.ALUNO);
        String outro = login(semMatricula.email(), semMatricula.senha());

        assertThat(chamar(HttpMethod.GET, rota(), null, outro)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(emitir("Ana Souza", outro)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.GET, rota(), null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.GET, "/api/aluno/certificados", null, null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("validação: nome em branco 400; nome sem sobrenome 400; código inexistente 404")
    void validacoes() {
        marcarVista(estrutura.aulaPaga());
        marcarVista(estrutura.aulaGratuita());

        assertThat(chamar(HttpMethod.POST, rota(), "{}", token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("nomeCompleto"));
        assertThat(emitir("Ana", token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Informe o nome completo como deve sair no certificado.");
        assertThat(chamar(HttpMethod.GET, "/api/certificados/ZZZZ-ZZZZ-ZZZZ", null, null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Certificado não encontrado.");
        assertThat(chamar(HttpMethod.POST, "/api/certificados/ZZZZ-ZZZZ-ZZZZ", "{}", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
`````

### 8. Jornada e2e

`backend/src/e2e/java/br/com/celke/cursos/e2e/certificado/CertificadoE2E.java`:

`````java
package br.com.celke.cursos.e2e.certificado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Midia;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Jornada: o aluno conclui a turma, emite o certificado, baixa o PDF e qualquer pessoa valida o código. */
class CertificadoE2E {

    @Test
    @DisplayName("aluno conclui → gera certificado → código valida")
    void concluirEmitirValidar() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();

        // 1. Curso publicado com uma aula; certificado exige 100% e informa 8 horas
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Certificado e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        adm.requisicao()
                .body("{\"percentualConclusao\":100,\"cargaHorariaHoras\":8}")
                .put("/api/adm/turmas/" + turma + "/certificado")
                .then()
                .statusCode(200);
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Módulo\"}");
        long aula = adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Única aula\"}");
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");
        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");
        adm.criar(
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"CORTESIA\",\"fim\":\"%s\"}"
                        .formatted(email, Instant.now().plus(Duration.ofDays(30))));

        // 2. Conclui: a única aula vista
        aluno(token)
                .body("{\"vista\":true}")
                .patch("/api/aluno/aulas/" + aula + "/progresso")
                .then()
                .statusCode(200);
        aluno(token)
                .get("/api/aluno/turmas/" + turma + "/certificado")
                .then()
                .statusCode(200)
                .body("concluiu", equalTo(true));

        // 3. Emite com o nome confirmado
        Response emitido = aluno(token)
                .body("{\"nomeCompleto\":\"Aluna da Jornada\"}")
                .post("/api/aluno/turmas/" + turma + "/certificado")
                .then()
                .statusCode(201)
                .extract()
                .response();
        String codigo = emitido.jsonPath().getString("codigo");
        long id = emitido.jsonPath().getLong("id");

        // 4. Baixa o PDF pelo host de mídia
        URI url = URI.create(aluno(token)
                .get("/api/aluno/certificados/" + id + "/download")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("url"));
        byte[] pdf = Midia.requisicao()
                .get(Midia.caminho(url))
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // 5. Qualquer pessoa valida o código, sem login, pelo site
        Api.site("aluno.localhost")
                .get("/api/certificados/" + codigo)
                .then()
                .statusCode(200)
                .body("nomeCompleto", equalTo("Aluna da Jornada"))
                .body("cursoTitulo", equalTo("Curso " + slug))
                .body("cargaHorariaHoras", equalTo(8));
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }
}
`````

## Antes de executar

Fase 7 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/certificado
```

## Verificação

Dentro de `backend/` (Docker rodando e `ffmpeg` no PATH):

```bash
./mvnw spotless:apply
./mvnw verify
```

- `BUILD SUCCESS` com 701 testes unitários e 345 de integração, e a cobertura mínima atendida.

Na raiz do repositório, as jornadas e2e com a stack completa:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
(cd backend && ./mvnw verify -Pe2e)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- 17 jornadas passam, entre elas a `CertificadoE2E`.

No ambiente local, pelo Swagger, com um aluno matriculado numa turma:

- Marque todas as aulas como vistas (`PATCH /api/aluno/aulas/{id}/progresso` com `{"vista":true}`) e
  emita em `POST /api/aluno/turmas/{id}/certificado` com o nome completo.
- Baixe o PDF pela URL de `GET /api/aluno/certificados/{id}/download`.
- Sem token, `GET /api/certificados/{codigo}` confirma os dados impressos.

## Depois de executar

```bash
git add backend/ infra/ docs/
git commit -m "Certificado em PDF"
git push -u origin feature/certificado
```

Abra o Pull Request de `feature/certificado` para `homolog`.
