# Testes e2e e pipeline no GitHub Actions

**Fase:** 1 — etapa 1.7
**Branch:** `feature/base-projeto`

## Prompt

Vamos testar a plataforma como caixa-preta, com a stack inteira no ar e as mesmas imagens que vão
para os servidores, e ligar o pipeline do GitHub Actions. Siga o padrão da skill `criar-teste-e2e`.
Não faça commit.

O que esta etapa entrega:

- **`infra/compose.prod.yml`**: a stack dos servidores — Caddy na frente (portas 80 e 443), API e worker
  com imagens do GHCR pela tag do commit (`TAG`), PostgreSQL e Garage sem portas publicadas,
  `restart: unless-stopped`, healthchecks e todas as variáveis obrigatórias com `${VAR:?}`.
- **`infra/caddy/Caddyfile`**: hosts pelo ambiente (`HOST_ALUNO`, `HOST_ADM`, `HOST_MEDIA`; com
  `https://` o certificado é automático, com `http://` atende sem TLS). Só `/api/*` e `/actuator/health`
  chegam à API; o resto (Swagger, outros endpoints do Actuator) recebe 404. Headers
  `X-Content-Type-Options`, `Referrer-Policy`, `Strict-Transport-Security`, sem `Server`;
  `lb_try_duration` para o deploy não gerar 502. O Caddy substitui o `X-Forwarded-For` do cliente.
- **Stack de e2e**: `infra/compose.e2e.yml` sobre o `compose.prod.yml`, com `infra/e2e.env` (valores só
  de teste, versionados): Caddy em `127.0.0.1:8088` com `http://*.localhost`, imagens construídas
  localmente (`plataforma-cursos-api:e2e`, `plataforma-cursos-worker:e2e`), Mailpit no lugar do SMTP,
  profile `e2e` com o seed `SeedE2e` (admin e aluno, senha de `SEED_E2E_SENHA`) e limite por IP maior.
  Projeto Compose `plataforma-cursos-e2e`, separado do ambiente local.
- **Perfil Maven `e2e`**: fontes em `src/e2e/java`, REST Assured, só `*E2E` no Failsafe, sem unitários,
  integração ou JaCoCo. Spotless passa a formatar também as fontes e2e.
- **Jornadas**: `CadastroE2E` (cadastro pelo Caddy → e-mail entregue pelo worker → confirmação → login),
  `SessaoE2E` (login → refresh → logout pelo Caddy, cookie sem `Domain`, origem de outra aplicação
  recusada) e `ProxyE2E` (headers de segurança, rotas fora da API bloqueadas e `X-Forwarded-For` forjado
  sem efeito, provado pelo próprio limite por IP). Os sites são escolhidos pelo cabeçalho `Host` em
  `localhost:8088`.
- **Pipeline** (`.github/workflows/pipeline.yml`): job de testes (`./mvnw verify`) e job de e2e (imagens
  com cache `type=gha`, stack com `up --no-build --wait`, `./mvnw verify -Pe2e`), com relatórios e logs
  da stack anexados quando falha. Roda em PR e push para `homolog` e `main`.

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

`backend/CLAUDE.md`:

`````markdown
# Backend — guia do projeto Java

API e worker da Plataforma de Cursos: Java 25, Spring Boot 4.1, PostgreSQL 18, Flyway.
Os comandos abaixo rodam **dentro de `backend/`**.

## Definição de pronto

Toda tarefa só está concluída quando passa:

```bash
./mvnw spotless:apply      # formata (palantir-java-format)
./mvnw verify              # unitários + integração + cobertura mínima + spotless:check
./mvnw verify -Pe2e        # e2e, com a stack de e2e no ar (como subir: skill criar-teste-e2e)
```

O `verify` precisa do Docker rodando (Testcontainers).

## Estrutura

```
src/main/java/br/com/celke/cursos/
  shared/          # config, security, erros, storage S3, fila, e-mail
  <modulo>/        # auth, usuario, curso, matricula, video, progresso, arquivo, forum, certificado, perfil
    api/           # controllers e DTOs (record)
    application/   # casos de uso
    domain/        # entidades, enums, regras
    infra/         # repositórios, integrações
src/main/resources/
  application.yaml            # padrão (perfil api)
  application-worker.yaml     # worker: sem servidor HTTP
  db/migration/               # Flyway
src/test/java/                # unitários (*Test) e integração (*IT)
src/e2e/java/                 # e2e caixa-preta (*E2E), perfil Maven e2e
Dockerfile                    # targets api e worker (com ffmpeg)
```

## Padrões de código

- **Idioma:** domínio em português (`Curso`, `Matricula`, `criarCurso`), sufixos técnicos em inglês (`Controller`, `Service`, `Repository`, `Policy`, `Test`, `IT`).
- **Controllers** só lidam com HTTP. Toda regra fica em `application/` ou `domain/`.
- **Um módulo não acessa o repositório de outro** — usa o serviço de `application/` exportado pelo outro módulo.
- **Rotas são privadas por padrão.** Rota pública é declarada em `shared/security/SecurityConfig` com o método HTTP; restrição por papel com `@PreAuthorize`.
- **Acesso a conteúdo do curso** (aula, vídeo, arquivo, fórum, progresso, certificado) passa **sempre** pela `AcessoAulaPolicy`.
- **Erros** no formato `ProblemDetail` (RFC 9457) pelo handler global; erro de validação traz a lista `erros` com `campo` e `mensagem`.
- **Data e hora** vêm de um `java.time.Clock` injetado — nunca `LocalDateTime.now()` direto em regra de negócio.
- **Status** são enums, nunca números soltos.
- **DTOs** são `record` com Bean Validation; toda rota documentada no OpenAPI.
- **Configuração:** variável nova → `application.yaml` com `${VARIAVEL}`, validação em `@ConfigurationProperties` quando a aplicação tiver propriedades próprias, e `infra/.env.example`.
- **Migrations** Flyway compatíveis com a versão anterior (expandir → depois contrair).

## Testes

Três níveis. **Antes de criar ou alterar um teste, leia o padrão do nível** — ele vale mesmo
quando a skill não é usada:

| Nível | O que cobre | Onde | Padrão | Skill |
|---|---|---|---|---|
| Unitário | regra de negócio, cálculo, política, validação de DTO — sem Spring, sem banco | `src/test/java/**/*Test.java` | `.claude/skills/criar-teste-unitario/padrao.md` | `/criar-teste-unitario <modulo>` |
| Integração | rotas, segurança, persistência, S3, fila, e-mail — Spring + Testcontainers | `src/test/java/**/*IT.java` | `.claude/skills/criar-teste-integracao/padrao.md` | `/criar-teste-integracao <modulo>` |
| e2e | jornadas que atravessam os containers (Caddy, worker, Garage, e-mail) — caixa-preta | `src/e2e/java/**/*E2E.java` | `.claude/skills/criar-teste-e2e/padrao.md` | `/criar-teste-e2e <jornada>` |

Funcionalidade nova vem com os níveis que se aplicam **no mesmo PR**.
`````

`backend/.claude/skills/criar-teste-e2e/padrao.md`:

`````markdown
# Testes e2e — o padrão do projeto

Os testes e2e tratam o sistema como **caixa-preta**: a stack completa sobe com
`infra/compose.prod.yml` + `infra/compose.e2e.yml` + `infra/e2e.env` (Caddy, API, worker,
PostgreSQL, Garage, Mailpit) usando **as mesmas imagens que vão para produção**, e os testes chamam
o sistema só por HTTP, como um cliente faria. Não há acesso ao banco, ao código da aplicação nem a
mocks. A stack de e2e é um projeto Compose separado (`plataforma-cursos-e2e`): sobe e desce sem
mexer no ambiente local.

Ficam no próprio projeto Java, num source set separado (`src/e2e/java`), e só rodam com o
perfil Maven `e2e`.

O e2e é **pouco e caro**: cobre só o que nenhum teste de integração alcança. Regra, papel e
acesso caso a caso já estão nos níveis abaixo.

## O que é jornada e2e

Entra no e2e o que depende de **vários containers juntos** ou da infraestrutura:

- roteamento do Caddy por host (`aluno.localhost`, `adm.localhost`, `media.localhost`), proxy de
  `/api` e bloqueio do que não é API;
- IP real do cliente atrás do proxy (`X-Forwarded-For` forjado não muda nada);
- cookie de refresh atravessando o proxy (login → refresh → logout);
- e-mail real saindo da API e chegando ao Mailpit (cadastro → confirmação; recuperação de senha);
- URL pré-assinada usada **de fora**, direto no Garage (upload multipart, download);
- worker processando a fila com ffmpeg (upload de vídeo → status "pronto");
- playlist HLS com segmentos assinados (segmento baixa; depois de expirar, não baixa);
- headers de segurança e de cache entregues pelo Caddy.

**Não** entra: matriz de papéis completa, validação de DTO, erros de negócio um a um — isso
é integração ou unitário.

## Onde cada coisa vive

```
src/e2e/java/br/com/celke/cursos/e2e/
  support/                 # configuração e utilitários — nenhum teste aqui
    E2eConfig.java         # porta do Caddy, Mailpit, senha do seed (propriedades do sistema)
    Api.java               # aluno(), adm(): requisição pelo Caddy com o cabeçalho Host; login(), bearer()
    Mailpit.java           # aguardar(destinatario, assunto), token(email)
    Dados.java             # stamp() e email() com prefixo e2e-
    Contas.java            # criarConfirmada(): conta própria pelo caminho real (cadastro → e-mail → link)
    Arquivos.java          # leitura das amostras (a partir das jornadas de vídeo)
  <jornada>/
    <Jornada>E2E.java      # CadastroE2E, SessaoE2E, VideoE2E, ProxyE2E...
src/e2e/resources/
  amostras/video-3s.mp4    # vídeo de amostra de 3 segundos
```

- Nome da classe termina em `E2E`. Um arquivo por jornada.
- Bibliotecas: JUnit 5, REST Assured, Awaitility, AssertJ (escopo de teste, usadas só pelo
  perfil `e2e`).
- Os usuários iniciais vêm do seed do profile `e2e` da API (`SeedE2e`, senha de
  `SEED_E2E_SENHA`); e-mails e senha ficam em `support/E2eConfig.java`.
- Todos os sites respondem em `localhost:8088`; o cabeçalho `Host` escolhe qual. As jornadas não
  dependem da resolução de `*.localhost` na máquina.

## Cada classe é independente

- Cada classe cria os próprios dados pela API e faz o próprio login.
- Nenhuma classe depende de outra ou da ordem de execução. Jornada é uma sequência real:
  fica num teste só, com passos comentados.
- Os usuários do seed são **só leitura**: logout, troca de senha e sessões são feitos com um
  usuário criado pela própria classe.
- Identificador único com prefixo `e2e-`: `e2e-<stamp>@teste.celke.uno`.

## Espera assíncrona

E-mail, fila e transcodificação são assíncronos. Use **Awaitility** com limite explícito e
mensagem (`await("vídeo processado").atMost(Duration.ofMinutes(2)).pollInterval(...)`),
nunca `Thread.sleep`. O vídeo de amostra é curto (3 s) para caber no runner do CI.

## O que conferir

- Status **e** corpo, como na integração; erros no formato `ProblemDetail`.
- O que o Caddy acrescenta: `Strict-Transport-Security`, `X-Content-Type-Options`,
  `Referrer-Policy`, ausência do cabeçalho `Server` e, com os fronts, `Cache-Control` dos assets e
  do `index.html`.
- Cookie de refresh com `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`, sem `Domain`.
- URLs devolvidas pela API apontam para o host de mídia esperado, nunca para o endereço
  interno do container.

## Falha no CI

Quando um e2e falha, o pipeline anexa os relatórios do Failsafe e o `docker compose logs`
de todos os serviços. Leia os logs antes de mexer no teste: a causa costuma estar no
container (variável faltando, healthcheck, rede).

## Verificação

Na raiz do repositório:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
```

Dentro de `backend/`:

```bash
./mvnw verify -Pe2e                          # todas as jornadas
./mvnw verify -Pe2e -Dit.test=VideoE2E       # uma jornada
```

Depois, na raiz: `docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v`.

Se uma jornada falhar por **bug real**, não ajuste o teste para passar — avise com a
jornada, o passo, o esperado e o obtido.
`````

`backend/.claude/skills/criar-teste-e2e/SKILL.md`:

`````markdown
---
name: criar-teste-e2e
description: Cria ou amplia os testes e2e caixa-preta (REST Assured + Awaitility) de uma jornada contra a stack Docker completa — Caddy, API, worker, PostgreSQL, Garage e Mailpit. Use quando pedirem "teste e2e", "teste ponta a ponta", "testar a jornada" ou "/criar-teste-e2e <jornada>".
argument-hint: <jornada>
---

# Criar teste e2e de uma jornada

Argumento: `$ARGUMENTS` — a jornada (ex.: `cadastro`, `sessao`, `video`, `proxy`).

O **padrão** (o que é jornada e2e, organização, espera assíncrona, o que conferir) está em
[padrao.md](padrao.md) — leia antes de escrever. Esta skill é o **processo**.

## 1. Confirmar que é e2e

Descreva a jornada passo a passo e aponte **qual container ou peça de infraestrutura** só o
e2e alcança (Caddy, worker com ffmpeg, URL pré-assinada usada de fora, e-mail real). Se
nenhum passo depende disso, a jornada é teste de integração — diga isso ao usuário e use
`/criar-teste-integracao`.

## 2. Entender a stack

Leia:

1. `infra/compose.prod.yml`, `infra/compose.e2e.yml` e `infra/e2e.env` — serviços, hosts,
   healthchecks, variáveis do profile `e2e`.
2. `infra/caddy/Caddyfile` — hosts, proxy de `/api`, headers.
3. Os controllers das rotas da jornada e o seed do profile `e2e`.
4. `backend/src/e2e/java/.../support/` e o perfil `e2e` do `backend/pom.xml` — o que já existe.

## 3. Propor

Apresente ao usuário: a classe (`<Jornada>E2E`), os passos da jornada, o que cada passo
confere e os tempos máximos de espera (Awaitility). Espere o ok antes de escrever.

## 4. Completar o que faltar

- Perfil Maven `e2e` no `backend/pom.xml`, se não existir: adiciona `src/e2e/java` e
  `src/e2e/resources` como fontes de teste, pula unitários e integração e roda só `*E2E`
  no Failsafe.
- `support/`:
  - `E2eConfig.java` — porta do Caddy (`e2e.porta`, padrão 8088), Mailpit (`e2e.mailpit`), senha
    e e-mails do seed do profile `e2e`.
  - `Api.java` — `aluno()`, `adm()`: requisição para `localhost:<porta>` com o cabeçalho `Host`
    do site; `login(email, senha)`, `bearer(token)`.
  - `Mailpit.java` — `aguardar(destinatario, assunto)` com Awaitility e `token(email)`.
  - `Dados.java` — `stamp()` e `email()` com prefixo `e2e-`.
  - `Contas.java` — `criarConfirmada()`: conta própria pelo caminho real.
  - `Arquivos.java` — leitura das amostras em `src/e2e/resources/amostras/`.

## 5. Escrever e verificar

Na raiz do repositório:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
```

Dentro de `backend/`:

```bash
./mvnw verify -Pe2e -Dit.test=<Jornada>E2E
./mvnw verify -Pe2e
./mvnw spotless:apply
```

Se falhar, leia `docker compose logs` antes de mexer no teste. Bug real não se contorna no
teste — avise o usuário com a jornada, o passo, o esperado e o obtido.

Por fim, resuma: classe criada, passos cobertos, tempo de execução e achados.
`````

### 2. Stack dos servidores

`infra/caddy/Caddyfile`:

`````caddyfile
# Porta de entrada da plataforma. Os hosts vêm do ambiente (HOST_ALUNO, HOST_ADM, HOST_MEDIA):
# com "https://..." o Caddy emite o certificado sozinho; com "http://..." (e2e) atende sem TLS.
{
	admin off
}

# Headers de segurança em todas as respostas
(seguranca) {
	header {
		X-Content-Type-Options nosniff
		Referrer-Policy strict-origin-when-cross-origin
		Strict-Transport-Security "max-age=31536000"
		-Server
	}
}

# Só a API e o health check passam; o resto (Swagger, outros endpoints do Actuator) não sai daqui.
# O X-Forwarded-For que o cliente mandar é substituído pelo IP real da conexão.
(api) {
	@api path /api/* /actuator/health /actuator/health/*
	handle @api {
		reverse_proxy api:8080 {
			# Durante o deploy a API reinicia: segura a requisição em vez de responder 502
			lb_try_duration 30s
		}
	}
	handle {
		respond 404
	}
}

{$HOST_ALUNO} {
	import seguranca
	import api
}

{$HOST_ADM} {
	import seguranca
	import api
}

# Arquivos do bucket por URL pré-assinada (o host faz parte da assinatura e é repassado como veio)
{$HOST_MEDIA} {
	import seguranca
	reverse_proxy garage:3900
}
`````

`infra/compose.prod.yml`:

`````yaml
# Stack dos servidores: Caddy na frente, imagens publicadas no GHCR com a tag do commit (TAG) e
# nenhuma porta publicada além das do Caddy. Todas as variáveis vêm do .env do ambiente.
name: plataforma-cursos

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

services:
  caddy:
    image: caddy:2.11.7-alpine
    restart: unless-stopped
    environment:
      HOST_ALUNO: ${HOST_ALUNO:?defina HOST_ALUNO}
      HOST_ADM: ${HOST_ADM:?defina HOST_ADM}
      HOST_MEDIA: ${HOST_MEDIA:?defina HOST_MEDIA}
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_dados:/data
      - caddy_config:/config
    depends_on:
      api:
        condition: service_healthy

  api:
    image: ${IMAGEM_API:-ghcr.io/celkecursos/plataforma-cursos-api}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      <<: *app
    depends_on:
      postgres:
        condition: service_healthy
      garage:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 5s
      timeout: 3s
      start_period: 30s
      retries: 20

  worker:
    image: ${IMAGEM_WORKER:-ghcr.io/celkecursos/plataforma-cursos-worker}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      <<: *app
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

  postgres:
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

  garage:
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
    healthcheck:
      test: ["CMD", "/garage", "status"]
      interval: 5s
      timeout: 5s
      retries: 20

volumes:
  postgres:
  garage:
  caddy_dados:
  caddy_config:
`````

### 3. Stack de e2e

`infra/compose.e2e.yml`:

`````yaml
# e2e: a stack dos servidores (compose.prod.yml) com HTTP em *.localhost, Mailpit no lugar do SMTP,
# imagens construídas localmente e o seed do profile e2e. Projeto separado do ambiente local.
#
#   docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
#   docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
name: plataforma-cursos-e2e

services:
  caddy:
    ports: !override
      - "127.0.0.1:${E2E_PORTA:?defina E2E_PORTA}:80"

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

`infra/e2e.env`:

`````dotenv
# Variáveis da stack de e2e. Valores só de teste (nenhum é usado em servidor): podem ficar no git.
TAG=e2e
E2E_PORTA=8088
E2E_MAILPIT_PORTA=8026

POSTGRES_DB=plataforma_cursos
POSTGRES_USER=plataforma_cursos
POSTGRES_PASSWORD=senha-do-banco-e2e

JWT_SEGREDO=l4jEHfbYeLV+W8Nf8kJv9QeHkLLCCPJdCLS7pqIJFk5eTx/DoYnOd476xZTrRgMX

GARAGE_RPC_SECRET=066fb7d1d0a13a90272747cd99d0474d26e520e2d6b214200236a01299618731
GARAGE_ADMIN_TOKEN=token-do-admin-e2e
S3_BUCKET=midia
S3_ACCESS_KEY=GK0a51c4026047d3d88c8c6435
S3_SECRET_KEY=292c9b355552db653bcf18f785fda32855125263d3d912ea8658305d5c00d206

HOST_ALUNO=http://aluno.localhost
HOST_ADM=http://adm.localhost
HOST_MEDIA=http://media.localhost
AUTH_ORIGENS=http://aluno.localhost:8088,http://adm.localhost:8088
FRONT_ALUNO_URL=http://aluno.localhost:8088
S3_ENDPOINT_PUBLICO=http://media.localhost:8088
S3_CORS_ORIGENS=http://aluno.localhost:8088,http://adm.localhost:8088

SMTP_HOST=mailpit
SMTP_PORT=1025
SMTP_AUTH=false
SMTP_STARTTLS=false
EMAIL_REMETENTE=nao-responda@teste.celke.uno

SEED_E2E_SENHA='123456A#b123'
`````

`backend/src/main/java/br/com/celke/cursos/usuario/application/SeedE2e.java`:

`````java
package br.com.celke.cursos.usuario.application;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seed da stack de e2e (profile {@code e2e}): um admin e um aluno confirmados, com a senha de
 * {@code SEED_E2E_SENHA}. Nunca ligado nos servidores.
 */
@Component
@Profile("e2e")
public class SeedE2e implements ApplicationRunner {

    public static final String ADMIN_EMAIL = "admin@celke.com.br";
    public static final String ALUNO_EMAIL = "aluno@celke.com.br";

    private final UsuarioService usuarioService;
    private final String senha;

    public SeedE2e(UsuarioService usuarioService, @Value("${SEED_E2E_SENHA}") String senha) {
        String problema = PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ADMIN);
        if (problema != null) {
            throw new IllegalArgumentException("SEED_E2E_SENHA inválida: " + problema);
        }
        this.usuarioService = usuarioService;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        criarSeNaoExiste("Admin do e2e", ADMIN_EMAIL, Papel.ADMIN);
        criarSeNaoExiste("Aluno do e2e", ALUNO_EMAIL, Papel.ALUNO);
    }

    private void criarSeNaoExiste(String nome, String email, Papel papel) {
        if (usuarioService.buscarPorEmail(email).isEmpty()) {
            usuarioService.criarConfirmado(nome, email, senha, Set.of(papel));
        }
    }
}
`````

### 4. Suporte das jornadas

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/E2eConfig.java`:

`````java
package br.com.celke.cursos.e2e.support;

/**
 * Endereços da stack de e2e. Padrões iguais aos de {@code infra/e2e.env}; podem ser trocados por
 * propriedades do sistema ({@code -De2e.porta=...}).
 */
public final class E2eConfig {

    /** Porta do Caddy no host. Os sites são escolhidos pelo cabeçalho Host. */
    public static final int PORTA = Integer.getInteger("e2e.porta", 8088);

    public static final String MAILPIT = System.getProperty("e2e.mailpit", "http://localhost:8026");

    public static final String SENHA_SEED = System.getProperty("e2e.senha-seed", "123456A#b123");

    public static final String HOST_ALUNO = "aluno.localhost";
    public static final String HOST_ADM = "adm.localhost";

    public static final String ADMIN_SEED = "admin@celke.com.br";
    public static final String ALUNO_SEED = "aluno@celke.com.br";

    private E2eConfig() {}

    /** Origem que o navegador envia para um host da plataforma ({@code http://aluno.localhost:8088}). */
    public static String origem(String host) {
        return "http://" + host + ":" + PORTA;
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/Api.java`:

`````java
package br.com.celke.cursos.e2e.support;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Requisições à plataforma pelo Caddy, como um navegador faria. Todos os sites respondem na mesma
 * porta; o cabeçalho Host escolhe qual (sem depender da resolução de {@code *.localhost}).
 */
public final class Api {

    private Api() {}

    public static RequestSpecification aluno() {
        return site(E2eConfig.HOST_ALUNO);
    }

    public static RequestSpecification adm() {
        return site(E2eConfig.HOST_ADM);
    }

    public static RequestSpecification site(String host) {
        return given().baseUri("http://localhost")
                .port(E2eConfig.PORTA)
                .header("Host", host + ":" + E2eConfig.PORTA)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    /** Login pela área do aluno; confere o 200 e devolve a resposta (access token e cookie). */
    public static Response login(String email, String senha) {
        return aluno().body("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .response();
    }

    public static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/Dados.java`:

`````java
package br.com.celke.cursos.e2e.support;

import java.util.concurrent.ThreadLocalRandom;

/** Identificadores únicos dos dados criados pelas jornadas (prefixo {@code e2e-}). */
public final class Dados {

    private Dados() {}

    public static String stamp() {
        return "e2e-" + Long.toString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), 36);
    }

    public static String email() {
        return stamp() + "@teste.celke.uno";
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/Mailpit.java`:

`````java
package br.com.celke.cursos.e2e.support;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import io.restassured.path.json.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** E-mails entregues ao Mailpit da stack de e2e (enviados de verdade pelo worker). */
public final class Mailpit {

    private static final Pattern TOKEN_NO_LINK = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

    private Mailpit() {}

    /** Espera o worker entregar um e-mail com o assunto para o destinatário e devolve o mais recente. */
    public static EmailRecebido aguardar(String destinatario, String assunto) {
        String id = await("e-mail \"" + assunto + "\" para " + destinatario)
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> idMaisRecente(destinatario, assunto), valor -> valor != null);
        JsonPath mensagem = given().baseUri(E2eConfig.MAILPIT)
                .get("/api/v1/message/{id}", id)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();
        return new EmailRecebido(
                mensagem.getString("Subject"), mensagem.getString("From.Address"), mensagem.getString("HTML"));
    }

    /** Token do link do e-mail ({@code ...?token=<valor>}). */
    public static String token(EmailRecebido email) {
        Matcher matcher = TOKEN_NO_LINK.matcher(email.html());
        if (!matcher.find()) {
            throw new AssertionError("O e-mail não tem link com token: " + email.assunto());
        }
        return matcher.group(1);
    }

    private static String idMaisRecente(String destinatario, String assunto) {
        List<String> ids = given().baseUri(E2eConfig.MAILPIT)
                .queryParam("query", "to:\"%s\" subject:\"%s\"".formatted(destinatario, assunto))
                .get("/api/v1/search")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("messages.ID");
        return ids == null || ids.isEmpty() ? null : ids.getFirst();
    }

    public record EmailRecebido(String assunto, String remetente, String html) {}
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/Contas.java`:

`````java
package br.com.celke.cursos.e2e.support;

/**
 * Conta própria de uma jornada, criada pelo caminho real (cadastro → e-mail → confirmação). Os
 * usuários do seed são só leitura: logout e sessões usam uma conta assim.
 */
public final class Contas {

    public static final String SENHA = "senha-da-conta-e2e";

    private Contas() {}

    public static String criarConfirmada() {
        String email = Dados.email();
        Api.aluno()
                .body("{\"nome\":\"Conta e2e\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA))
                .post("/api/auth/cadastro")
                .then()
                .statusCode(202);
        String token = Mailpit.token(Mailpit.aguardar(email, "Confirme o seu e-mail"));
        Api.aluno()
                .body("{\"token\":\"%s\"}".formatted(token))
                .post("/api/auth/confirmar-email")
                .then()
                .statusCode(200);
        return email;
    }
}
`````

### 5. Jornadas

`backend/src/e2e/java/br/com/celke/cursos/e2e/cadastro/CadastroE2E.java`:

`````java
package br.com.celke.cursos.e2e.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Mailpit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: cadastro pelo Caddy → e-mail enviado pelo worker de verdade → confirmação pelo link →
 * login. Alcança o que os testes de integração não alcançam: o proxy, a fila consumida pelo worker
 * e o SMTP real (Mailpit).
 */
class CadastroE2E {

    private static final String SENHA = "senha-do-cadastro-e2e";

    @Test
    @DisplayName("aluno se cadastra, recebe o e-mail do worker, confirma pelo link e entra")
    void cadastroConfirmacaoLogin() {
        String email = Dados.email();

        // 1. Cadastro pela área do aluno
        Api.aluno()
                .body("{\"nome\":\"Aluna e2e\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA))
                .post("/api/auth/cadastro")
                .then()
                .statusCode(202)
                .body("mensagem", equalTo("Cadastro recebido. Confira o seu e-mail para continuar."));

        // 2. O worker consome a fila e entrega o e-mail com o link para a área do aluno
        Mailpit.EmailRecebido recebido = Mailpit.aguardar(email, "Confirme o seu e-mail");
        assertThat(recebido.remetente()).isEqualTo("nao-responda@teste.celke.uno");
        assertThat(recebido.html()).contains("http://aluno.localhost:8088/confirmar-email?token=");

        // 3. Antes de confirmar, a senha certa recebe a orientação de confirmar
        Api.aluno()
                .body("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA))
                .post("/api/auth/login")
                .then()
                .statusCode(403);

        // 4. Confirmação pelo token do link e login
        Api.aluno()
                .body("{\"token\":\"%s\"}".formatted(Mailpit.token(recebido)))
                .post("/api/auth/confirmar-email")
                .then()
                .statusCode(200);
        assertThat(Api.login(email, SENHA).jsonPath().getList("usuario.papeis")).containsExactly("ALUNO");
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/sessao/SessaoE2E.java`:

`````java
package br.com.celke.cursos.e2e.sessao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.E2eConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: login → refresh → logout atravessando o Caddy, com o cookie de refresh e a verificação de
 * origem funcionando atrás do proxy.
 */
class SessaoE2E {

    @Test
    @DisplayName("login, refresh e logout pelo Caddy com o cookie preso ao host")
    void loginRefreshLogout() {
        String email = Contas.criarConfirmada();

        // 1. Login: access token no corpo, refresh token só no cookie (sem Domain)
        Response login = Api.login(email, Contas.SENHA);
        String setCookie = login.getHeader("Set-Cookie");
        assertThat(setCookie)
                .startsWith("refresh_token=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/auth")
                .doesNotContainIgnoringCase("Domain=");
        String token = login.jsonPath().getString("accessToken");
        String cookie = login.getCookie("refresh_token");

        // 2. O access token vale pelos dois hosts (mesma API atrás do Caddy)
        Api.adm()
                .header("Authorization", Api.bearer(token))
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .body("email", equalTo(email));

        // 3. Refresh vindo da área do aluno troca o cookie
        Response renovado = Api.aluno()
                .header("Origin", E2eConfig.origem(E2eConfig.HOST_ALUNO))
                .cookie("refresh_token", cookie)
                .post("/api/auth/refresh")
                .then()
                .statusCode(200)
                .extract()
                .response();
        String cookieNovo = renovado.getCookie("refresh_token");
        assertThat(cookieNovo).isNotEqualTo(cookie);

        // 4. Refresh vindo de outra aplicação do mesmo domínio é recusado
        Api.aluno()
                .header("Origin", "http://outra-app.localhost:" + E2eConfig.PORTA)
                .cookie("refresh_token", cookieNovo)
                .post("/api/auth/refresh")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Origem não permitida."));

        // 5. Logout encerra a sessão
        Api.aluno()
                .header("Origin", E2eConfig.origem(E2eConfig.HOST_ALUNO))
                .cookie("refresh_token", cookieNovo)
                .post("/api/auth/logout")
                .then()
                .statusCode(204);
        Api.aluno()
                .cookie("refresh_token", cookieNovo)
                .post("/api/auth/refresh")
                .then()
                .statusCode(401);
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

### 6. Pipeline

`.github/workflows/pipeline.yml`:

`````yaml
# Testes, build das imagens e e2e. Roda em todo PR para homolog e main, e de novo depois do merge
# (o push na main refaz o build e o e2e com o código exato que vai para produção).
name: Pipeline

on:
  pull_request:
    branches: [homolog, main]
  push:
    branches: [homolog, main]
  workflow_dispatch:

concurrency:
  group: pipeline-${{ github.ref }}
  cancel-in-progress: true

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
          cache-from: type=gha,scope=api
          cache-to: type=gha,mode=max,scope=api

      - name: Imagem do worker
        uses: docker/build-push-action@v7
        with:
          context: backend
          target: worker
          tags: plataforma-cursos-worker:e2e
          load: true
          cache-from: type=gha,scope=worker
          cache-to: type=gha,mode=max,scope=worker

      - name: Subir a stack de e2e
        run: $COMPOSE_E2E up -d --no-build --wait

      - name: Jornadas e2e
        working-directory: backend
        run: ./mvnw -B verify -Pe2e

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
`````

## Antes de executar

Etapas 1.1 a 1.6 concluídas. Portas 8088 e 8026 livres na máquina.

## Verificação

Na raiz do repositório:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
```

Dentro de `backend/`:

```bash
./mvnw spotless:apply
./mvnw verify -Pe2e
./mvnw verify
```

- `verify -Pe2e`: 9 testes (`CadastroE2E`, `SessaoE2E`, `ProxyE2E`) com `BUILD SUCCESS`.
- `verify`: continua com 192 testes unitários e 104 de integração.
- Depois, na raiz: `docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v`.
- No GitHub, ao abrir um PR para `homolog`, os jobs `Testes (unitários e integração)` e
  `e2e (stack completa)` rodam e ficam verdes.

## Depois de executar

```bash
git add .github/ backend/ infra/
git commit -m "ci: adicionar testes e2e e pipeline no GitHub Actions"
```

Com o pipeline verde, em Settings → Rules do repositório, crie uma regra para `homolog` e `main`:
só Pull Request, com os dois checks obrigatórios.
