# Esqueleto do backend e padrão de testes

**Fase:** 1 — etapa 1.1
**Branch:** `feature/base-projeto`

## Prompt

Vamos criar o projeto Java do backend da Plataforma de Cursos em `backend/` e registrar as
convenções do repositório e o padrão de testes, para que todas as próximas etapas sigam as
mesmas regras. Não faça commit.

### 1. Gerar o projeto no Spring Initializr

Na raiz do repositório, gere o projeto com Java 25 (a versão do Spring Boot, 4.1.1, fica fixada no
`pom.xml` do passo 2):

```bash
curl -s https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d javaVersion=25 \
  -d groupId=br.com.celke -d artifactId=plataforma-cursos -d name=plataforma-cursos \
  -d description="API da Plataforma de Cursos" -d packageName=br.com.celke.cursos -d packaging=jar \
  -d dependencies=web,security,data-jpa,validation,flyway,actuator,postgresql,testcontainers,springdoc-openapi \
  -o /tmp/backend.zip
mkdir -p backend && unzip -q /tmp/backend.zip -d backend
```

Do que foi gerado, **mantenha** apenas `mvnw`, `mvnw.cmd`, `.mvn/`, `.gitattributes` e
`src/main/java/br/com/celke/cursos/PlataformaCursosApplication.java`. **Apague**
`HELP.md`, o `.gitignore` do backend (o da raiz já cobre), `src/main/resources/application.properties`
e todas as classes geradas em `src/test/java/br/com/celke/cursos/`.

### 2. Criar os arquivos

Crie cada arquivo abaixo com **exatamente** o conteúdo indicado (o `pom.xml` substitui o gerado).

#### 2.1. Orientações do repositório e do backend

`CLAUDE.md`:

`````markdown
# Guia para agentes de IA e desenvolvedores

Convenções gerais deste repositório. Cada projeto tem o próprio guia com as regras do seu stack:

| Pasta | Projeto | Guia |
|---|---|---|
| `backend/` | API e worker (Java 25, Spring Boot 4.1) | `backend/CLAUDE.md` |
| `infra/` | Docker Compose, Caddy, scripts de deploy | — |
| `docs/` | Plano de implementação e prompts do tutorial | — |

## Antes de começar

- Leia `README.md` e `docs/plano-implementacao.md` (decisões, fases e estrutura).

## Branches

- `main` = produção, `homolog` = homologação.
- Trabalhe sempre numa branch própria criada a partir de `homolog` (`feature/nome` ou `fix/nome`) e entregue por Pull Request para `homolog`. Correção urgente: `hotfix/nome` a partir da `main`.
- **Nunca** faça commit ou push diretamente em `homolog` ou `main`. A promoção `homolog → main` é feita por Pull Request.
- Não faça commit: deixe as alterações sem stage para o desenvolvedor revisar e commitar.

## Regras gerais

- Mensagens ao usuário, comentários e documentação em português.
- Nunca coloque segredos no código, no compose ou na documentação: só em `.env` (fora do git) e nos secrets do GitHub.
- Funcionalidade nova vem com os testes no mesmo PR, seguindo o guia do projeto.
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
./mvnw verify -Pe2e        # e2e, com a stack de e2e no ar (a partir da etapa de e2e)
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

#### 2.2. Padrão de testes (skills do backend)

`backend/.claude/skills/criar-teste-unitario/SKILL.md`:

`````markdown
---
name: criar-teste-unitario
description: Cria ou amplia os testes unitários (JUnit 5 + AssertJ + Mockito) de um módulo do backend Java — teste no mesmo pacote, no molde certo (serviço com dependências, classe pura ou DTO). Use quando pedirem "teste unitário", "cobrir o serviço/DTO/policy" ou "/criar-teste-unitario <modulo>".
argument-hint: <modulo> [classe]
---

# Criar teste unitário

Argumento: `$ARGUMENTS` — o módulo (pacote em `br.com.celke.cursos`) e, opcionalmente, a
classe (ex.: `curso CriarCursoService`, `matricula AcessoAulaPolicy`). Sem classe: todos os
serviços de `application/`, as classes de `domain/` com regra e os DTOs de `api/` do módulo,
um por vez.

O **padrão** (moldes, exceções, tabela, o que mockar) está em [padrao.md](padrao.md) — leia
antes de escrever. Esta skill é o **processo**. Comandos rodam dentro de `backend/`.

## 1. Listar os ramos do código

Para cada classe alvo, leia o código e o teste existente (se houver) e liste:

- cada método público e cada **ramo** dele (`if`, `switch`, ternário, `Optional`,
  `try/catch`, retorno antecipado);
- cada `throw` — tipo da exceção, mensagem e o que **não** deve ser gravado/enviado nesse caso;
- cada regra numérica ou de data (percentual, limite, período, expiração) e suas bordas;
- no DTO: cada anotação de validação, cada normalização e os campos opcionais;
- as dependências injetadas — são elas (e só elas) que serão mockadas; `Clock` entra como
  `Clock.fixed`.

Marque o que o teste atual já cobre. Para o mapa real, rode o `./mvnw verify` e abra o
relatório do JaCoCo da classe: as linhas não cobertas apontam os ramos sem caso.

## 2. Propor a tabela de casos — antes de escrever

Apresente ao usuário, por classe, o molde escolhido e a tabela:

| Método / campo | Caso | Entrada (mínima) | Esperado | Já coberto? |
| --- | --- | --- | --- | --- |
| `criar` | slug duplicado | `existsBySlug` → `true` | `SlugDuplicadoException`, `save` não chamado | não |
| `podeAcessar` | um segundo após o fim | `Clock` no fim + 1 s | `false` | não |

Inclua todas as exceções, as bordas obrigatórias (zero, negativo, limite ±1,
arredondamento, lista vazia, instante antes/exato/depois) e o que fica **fora** (rota,
segurança, constraint do banco → integração). Espere o ok do usuário, ou ajuste, antes do
passo 3.

## 3. Escrever no molde certo

- Serviço com dependências → `MockitoExtension`, `@Mock` nas dependências, serviço real,
  `Clock.fixed`, fábricas no topo ou em `support/fixtures/`.
- Classe pura → `new Classe(...)`, regras numéricas e de data em `@ParameterizedTest`.
- DTO → `Validator` único na classe + `camposInvalidos(dto)` + `payloadValido()`.
- Teste já existe? **Amplie** a classe mantendo a organização dela; não crie uma segunda
  classe de teste para a mesma classe.
- Achou um ramo que só se testa espionando a própria classe ou mockando algo interno? Pare
  e avise: provavelmente a regra está no lugar errado — não force o teste.

## 4. Rodar

```bash
./mvnw test -Dtest=<Classe>Test
./mvnw spotless:apply
./mvnw verify
```

Confira no relatório do JaCoCo que as linhas da tabela foram exercitadas; linha descoberta
que sobrar precisa de caso ou de justificativa no resumo. Teste que falha por **bug real** do
código não se ajusta para passar — avise o usuário com o caso, o esperado e o obtido.

Por fim, resuma: classes criadas/ampliadas, casos novos e a cobertura antes/depois de cada
classe.
`````

`backend/.claude/skills/criar-teste-unitario/padrao.md`:

`````markdown
# Testes unitários — o padrão do projeto

Os unitários rodam no Surefire (`./mvnw test`), **sem Spring, sem banco e sem HTTP**. Cobrem
a **regra**: ramos dos casos de uso, cálculo, políticas de acesso, validação de DTO. O que
depende de rota, segurança, Flyway, PostgreSQL, Garage ou SMTP é teste de integração
(`../criar-teste-integracao/padrao.md`).

## Onde cada coisa vive

- A classe de teste fica no **mesmo pacote** do código, em `src/test/java`, com o mesmo nome
  + `Test`:
  `curso/application/CriarCursoService.java` → `curso/application/CriarCursoServiceTest.java`.
- Uma classe de teste por classe testada; `@Nested` por método quando o método tem mais de
  um caso.
- Nome do método de teste em português, em camelCase, dizendo a **regra**
  (`rejeitaSlugJaCadastrado`), com `@DisplayName("rejeita slug já cadastrado")`. Nunca
  descreva a implementação ("chamaFindBySlug").
- Fábricas de objetos de teste compartilhadas entre módulos vivem em
  `src/test/java/br/com/celke/cursos/support/fixtures/`. Não recrie à mão, em cada teste,
  uma entidade que já tem fábrica.

## Os três moldes — escolha um e copie a referência

| O que é testado | Molde | Referência |
| --- | --- | --- |
| Serviço com dependências (repositório, outro serviço, `StorageService`, `EmailService`) | `@ExtendWith(MockitoExtension.class)`, dependências com `@Mock`, serviço **real** com `@InjectMocks` (ou construtor no `@BeforeEach`); `Clock.fixed(...)` real, não mock | `auth/application/LoginServiceTest.java` |
| Classe pura (política, cálculo, enum com regra) | `new Classe(...)` direto, sem Mockito | `matricula/domain/AcessoAulaPolicyTest.java` |
| DTO (`record` com Bean Validation) | `Validator` de `Validation.buildDefaultValidatorFactory()` criado uma vez na classe; helper local `camposInvalidos(dto)` que devolve o `Set` de nomes dos campos com erro; um `payloadValido()` e variações | `auth/api/CadastroRequestTest.java` |

Se o arquivo de referência ainda não existir, o primeiro teste do molde segue este padrão e
passa a ser a referência.

- Fábrica no topo da classe (`cursoExistente(...)`) ou em `support/fixtures/` em vez de
  objetos montados repetidamente.
- DTO: confira **quais campos** falharam (`assertThat(camposInvalidos(dto)).containsExactlyInAnyOrder("email")`),
  não só que falhou; confira também a normalização (`trim`, minúsculas) quando o DTO a faz.

## Toda exceção é testada

Para **cada** `throw` do código (e cada exceção que ele deixa passar de propósito):

1. O **tipo** — `assertThatThrownBy(() -> ...).isInstanceOf(SlugDuplicadoException.class)` —
   e a **mensagem exata** quando ela é o contrato com o usuário (`.hasMessage("Já existe um curso com este slug.")`).
2. **Nada foi gravado nem enviado** — `verify(cursoRepository, never()).save(any())` (e
   `delete`, `storageService`, `emailService`, `filaService` — o que alteraria estado).

## Regra numérica ou de data: tabela com `@ParameterizedTest`

Cálculo, percentual, arredondamento, limites, paginação e **períodos de data** (matrícula
ativa, token expirado, URL assinada) vão numa tabela — `@CsvSource` quando os valores são
simples, `@MethodSource` quando precisam de objetos — uma linha por caso, com rótulo:

```java
@ParameterizedTest(name = "{0}")
@CsvSource({
    "um segundo antes do início,   2026-03-01T09:59:59Z, false",
    "exatamente no início,         2026-03-01T10:00:00Z, true",
    "exatamente no fim,            2026-06-01T10:00:00Z, true",
    "um segundo depois do fim,     2026-06-01T10:00:01Z, false",
})
void acessoPeloPeriodoDaMatricula(String caso, Instant agora, boolean esperado) {
    var policy = new AcessoAulaPolicy(Clock.fixed(agora, ZoneOffset.UTC));
    assertThat(policy.podeAcessar(matriculaAtiva(), aulaPaga())).isEqualTo(esperado);
}
```

Bordas **obrigatórias**: **zero**, **negativo**, o **limite exato** e um acima/abaixo dele,
**arredondamento** e lista **vazia** quando houver lista. Em data: um instante antes, o
instante exato e um depois de cada limite. Decimal com `BigDecimal` se compara com
`isEqualByComparingTo`.

## Mocke só a fronteira

- Mock **apenas** o que está fora da unidade: repositórios, serviços de outros módulos,
  `StorageService`, `EmailService`, `FilaService`, `PasswordEncoder`, geradores de token.
- O relógio **não** é mockado: use `Clock.fixed(...)`.
- **Nunca** mocke, espione (`spy`) ou substitua método da **própria** classe sob teste:
  monte o retorno das dependências para que o método real siga o ramo desejado.
- Stub devolve o **mínimo** que o ramo precisa. Sem `lenient()`: stub não usado é sinal de
  teste errado (o Mockito estrito acusa).
- `verify(...)` com argumentos só quando o efeito **é** a regra (hash gravado, e-mail
  enfileirado, família de refresh revogada) — não para reproduzir a implementação.
  Para conferir o objeto gravado, use `ArgumentCaptor`.

## Verificação

```bash
./mvnw test -Dtest=CriarCursoServiceTest
./mvnw test
./mvnw spotless:apply
```

O relatório de cobertura do JaCoCo (`target/site/jacoco/index.html`, gerado no `verify`)
aponta os ramos sem caso: linha não coberta precisa de caso ou de justificativa.
`````

`backend/.claude/skills/criar-teste-integracao/SKILL.md`:

`````markdown
---
name: criar-teste-integracao
description: Cria ou amplia os testes de integração (Spring Boot + Testcontainers + MockMvcTester) de um módulo do backend Java — matriz de papéis, matriz de acesso ao conteúdo e fluxos por arquivo. Use quando pedirem "teste de integração", "testar as rotas/endpoints", "matriz de permissão" ou "/criar-teste-integracao <modulo>".
argument-hint: <modulo> [fluxo]
---

# Criar teste de integração de um módulo

Argumento: `$ARGUMENTS` — o módulo (pacote em `br.com.celke.cursos`) e, opcionalmente, o
fluxo.

O **padrão** (infraestrutura compartilhada, independência, matrizes, dado de teste) está em
[padrao.md](padrao.md) — leia antes de escrever. Esta skill é o **processo**. Comandos rodam
dentro de `backend/`.

## 1. Entender o módulo antes de escrever

Leia, nesta ordem:

1. `<modulo>/api/*Controller.java` — cada rota: método, caminho, `@PreAuthorize`, status de
   sucesso, parâmetros. Monte a lista de rotas: é a base da matriz de papéis.
2. `shared/security/SecurityConfig.java` — quais rotas do módulo são públicas e com qual
   método HTTP.
3. `<modulo>/api/` (DTOs) — campos obrigatórios, normalizações e formato de resposta.
4. `<modulo>/application/` — erros de negócio com a mensagem exata, efeitos colaterais
   (e-mail, fila, S3, revogação de tokens) e se a rota passa pela `AcessoAulaPolicy`.
5. As migrations do módulo — constraints (único, FK, check) que só o banco garante.
6. O seed do profile `test` — o que já existe para o módulo (só leitura).

## 2. Propor os arquivos

Antes de escrever, apresente ao usuário a lista, por exemplo:

- `<modulo>/<Modulo>AcessoIT.java` — matriz anônimo / ALUNO / ADMIN de **todas** as rotas.
- `<modulo>/<Modulo>ConteudoIT.java` — matriz de acesso ao conteúdo (ativa, sem matrícula,
  vencida, cancelada, aula gratuita) das rotas que usam a `AcessoAulaPolicy`.
- `<modulo>/<Modulo>CrudIT.java` — ciclo completo num teste: cria, busca pelo `stamp`, erro
  de negócio (409/404), edita, exclui.
- fluxos específicos (`AuthRefreshIT`, `VideoUploadIT`, `ForumModeracaoIT`...).

Diga também o que fica **fora** (regra caso a caso, validação campo a campo → unitário;
jornada com Caddy/worker → e2e). Espere o ok do usuário antes de escrever.

## 3. Completar `support/` quando faltar algo

Só infraestrutura, nenhum teste. O que já existe não é alterado para atender um teste; o que
falta é acrescentado:

- `Containers.java` — novo container como `@Bean`; PostgreSQL com `@ServiceConnection`,
  os demais com um bean `DynamicPropertyRegistrar`.
- `IntegrationTest.java` — `login(email, senha)` que confere 200 e devolve o access token,
  `bearer(token)` (a partir da etapa de autenticação).
- `UsuariosSeed.java` — e-mails e senha dos usuários do seed do profile `test`.
- `Mailpit.java` — `aguardarEmailPara(email)` com Awaitility e extração de links.

Esqueleto de uma classe:

```java
/**
 * Cobre: CRUD de cursos pelo ADM.
 * Não cobre: validação campo a campo (CriarCursoRequestTest).
 * Dado criado: cursos com slug it-<stamp>.
 */
class CursoCrudIT extends IntegrationTest {

    private final String stamp = stamp();
    private String tokenAdmin;

    @BeforeEach
    void entrar() {
        tokenAdmin = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
    }

    @Test
    @DisplayName("admin cria, edita e exclui um curso")
    void cicloCompleto() {
        // status E corpo em cada passo
    }
}
```

## 4. Escrever

- Escreva os arquivos propostos seguindo o padrão.
- Em toda rota com `{id}`, os casos 401/403 usam um id real criado pela classe.
- Confira status **e** corpo, `ProblemDetail` nos erros, ausência de dados sensíveis.
- Não use `@MockitoBean`, `@DirtiesContext` nem `@TestPropertySource`.

## 5. Verificar

```bash
./mvnw verify -Dskip.unit=true -Dit.test='<Modulo>*IT'   # o módulo
./mvnw spotless:apply
./mvnw verify                                           # a suíte inteira
```

O Docker precisa estar rodando. Se um teste falhar, leia a resposta antes de mexer: pode ser
**bug real** da API. Bug real não se contorna no teste — avise o usuário com a rota, o
esperado e o obtido.

Por fim, resuma: arquivos criados, o que cada um cobre e os achados.
`````

`backend/.claude/skills/criar-teste-integracao/padrao.md`:

`````markdown
# Testes de integração — o padrão do projeto

Os testes de integração sobem a aplicação Spring de verdade e chamam as rotas pelo
`MockMvcTester`, contra **PostgreSQL, Garage e Mailpit reais** em Testcontainers. Não há
mock de repositório, de banco ou de S3. Rodam no Failsafe (`./mvnw verify`), depois dos
unitários. Aqui fica **como escrever** um teste.

## Infraestrutura compartilhada

- Os containers são beans de `support/Containers.java` (`@TestConfiguration`). PostgreSQL
  usa `@ServiceConnection`; containers sem conexão automática (Garage, Mailpit) publicam
  suas propriedades com um bean `DynamicPropertyRegistrar` na mesma classe.
- Todas as classes estendem `support/IntegrationTest.java` e, por isso, compartilham **o
  mesmo contexto Spring**: cada container sobe uma vez por execução e o Flyway migra uma vez.
  O seed do profile `test` cria os usuários e o curso de referência.
- **Não** use `@MockitoBean`, `@DirtiesContext`, `@TestPropertySource` nem outro profile numa
  classe: cada variação cria outro contexto e deixa a suíte lenta.
- **Não mexa** em `Containers.java` nem em `IntegrationTest.java` para atender um teste de
  funcionalidade. Se um teste precisa de outro estado inicial, ele cria esse estado pela API.

## Onde cada coisa vive

```
src/test/java/br/com/celke/cursos/
  support/                   # base dos ITs — containers, login, dados únicos, Mailpit
    Containers.java
    IntegrationTest.java     # @SpringBootTest + MockMvcTester + login(), bearer(), stamp()
    UsuariosSeed.java        # e-mails e senha do seed do profile test
    Mailpit.java             # buscar e-mail recebido, extrair link
    fixtures/                # fábricas (também usadas pelos unitários)
  <modulo>/
    <Modulo>AcessoIT.java    # matriz de papéis de todas as rotas do módulo
    <Modulo><Fluxo>IT.java   # CursoCrudIT, AuthRefreshIT, VideoUploadIT...
```

- `<modulo>` é o **pacote do módulo** (`auth`, `curso`, `matricula`, `video`...) ou o pacote
  de `shared` testado (`shared/security/SegurancaPadraoIT.java`). O teste fica no pacote do
  módulo, não em `api/`.
- Um arquivo por fluxo, não um arquivo gigante por módulo.
- Helper do módulo (`CursoITHelper`) nasce **quando 2+ classes repetem** o mesmo trecho.
- Referência de estrutura: `shared/security/SegurancaPadraoIT.java`.

## Cada classe é independente

- Cada classe faz o próprio login no `@BeforeEach` com `login(...)` de `IntegrationTest`.
- **Nenhuma** classe depende de outra, da ordem de execução ou de dado criado por outra.
  Dentro da classe, um teste também não depende de outro: estado comum nasce no
  `@BeforeEach`. Fluxo que é uma sequência real (cria → edita → exclui) fica num teste só.
- Credenciais do seed vêm de `UsuariosSeed` — nunca senha literal espalhada.

## Matriz de papéis — obrigatória

**Toda rota** tem, no mínimo, os casos **anônimo**, **ALUNO** e **ADMIN**:

| Rota | anônimo | ALUNO | ADMIN |
| --- | --- | --- | --- |
| privada | 401 | sucesso | sucesso |
| `/api/adm/**` (`@PreAuthorize` ADMIN) | 401 | **403** | sucesso |
| pública | sucesso | mesmo resultado | idem |

- O lugar natural é `<Modulo>AcessoIT` com `@ParameterizedTest` + `@MethodSource` sobre as
  rotas do módulo.
- Método HTTP não declarado numa rota pública também é testado (ex.: `POST` numa rota
  pública só de `GET` → 401).
- Rota com `{id}` nos casos 401/403 usa um id **real** criado pela própria classe — senão um
  404 esconde a falta de proteção.

## Matriz de acesso ao conteúdo — obrigatória nas rotas da `AcessoAulaPolicy`

Rota de aula, vídeo, playlist, chave HLS, arquivo, fórum, progresso ou certificado testa,
com um aluno criado pela própria classe:

| Situação do aluno | Esperado |
| --- | --- |
| matrícula ativa, dentro do período | sucesso |
| sem matrícula | 403 |
| matrícula vencida | 403 |
| matrícula cancelada | 403 |
| sem matrícula, aula gratuita | sucesso (quando a rota aceita aula gratuita) |

**Recurso de outro aluno** (progresso, anexo, dados de certificado) responde **404**, para
não revelar que existe.

## Dado de teste

- Identificador **único em runtime** com `stamp()` (prefixo `it-`): `<stamp>@teste.celke.uno`,
  curso `IT Curso <stamp>`, slug `<stamp>`.
- **Nunca** conte valores absolutos de tabelas compartilhadas (total da listagem sem
  filtro, contadores): outras classes criam dados no mesmo banco. Use **delta** (lê antes,
  age, lê depois) ou filtre pelo `stamp` da própria classe.
- Dado do seed é **só leitura**.
- Não precisa de limpeza: o banco nasce do zero a cada execução. Exclusão só entra quando é
  parte do fluxo testado.

## Usuários do seed — intocáveis

**Nunca** troque a senha, faça logout, revogue sessões, altere papel/status ou edite os
usuários do seed: isso revoga os refresh tokens deles e quebra as outras classes. Fluxo de
logout, troca de senha, perfil, termos ou bloqueio **cria o próprio usuário** (com `stamp`)
e faz login com ele.

## Rate limit

No profile `test`, o limite **por IP** é alto (todas as requisições da suíte saem do mesmo
IP) e o limite **por e-mail** é o real. O teste que **prova** o rate limit usa um e-mail
próprio (`stamp`), então nunca depende de quantas requisições outras classes fizeram.

## O que conferir: status E corpo

- Status sozinho não basta. Sucesso: confira os campos relevantes do JSON
  (`assertThat(resposta).hasStatusOk().bodyJson().extractingPath("$.slug").isEqualTo(...)`),
  incluindo as normalizações que a API faz.
- Erro: o formato `ProblemDetail` — `type`, `title`, `status`, `detail`, `instance`. Erro de
  negócio confere o `detail` exato em português; erro de validação (400) confere a lista
  `erros` com os campos esperados.
- Toda resposta que carrega usuário confere a **ausência** de `senha`, hash e tokens — em
  listagens, em **cada** item.
- Cookie de refresh: confira `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/auth` e
  **ausência** de `Domain`.
- Asserção negativa precisa de âncora: antes de `doesNotContain(...)` numa lista, prove que
  ela veio com o item da própria classe.
- Efeito fora do banco também se confere: arquivo no Garage (`StorageService`), e-mail no
  Mailpit (`Mailpit.aguardarEmailPara(email)`), job na fila. Espera assíncrona com
  **Awaitility**, nunca `Thread.sleep`.

## O que NÃO é teste de integração

Cálculo, política de acesso caso a caso e validação **campo a campo** de DTO ficam no
**unitário**. O teste de integração prova uma vez que a validação está ligada (um payload
inválido → 400 com `erros`) e cobre o que só ele alcança — segurança, contrato HTTP,
`ProblemDetail`, constraints do PostgreSQL, migrations, S3, e-mail e fila. Jornada que
precisa do Caddy, do worker com ffmpeg ou de vários containers juntos é **e2e**
(`../criar-teste-e2e/padrao.md`).

## Verificação

```bash
./mvnw verify -Dskip.unit=true -Dit.test=CursoCrudIT   # uma classe
./mvnw verify                                          # tudo (prova a independência)
```

O Docker precisa estar rodando (Testcontainers). Se um teste falhar, leia a resposta antes
de mexer: pode ser **bug real** da API (rota sem proteção, status errado, senha vazando).
Bug real não se contorna no teste — avise com a rota, o esperado e o obtido.
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

1. `infra/compose.prod.yml` e `infra/compose.e2e.yml` — serviços, hosts, healthchecks,
   variáveis do profile `e2e`.
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
  - `E2eConfig.java` — URLs base de `aluno`, `adm` e `media` por propriedade do sistema
    (padrão `http://aluno.localhost`, `http://adm.localhost`, `http://media.localhost`).
  - `Api.java` — `RequestSpecification` por host, `login(email, senha)`, `bearer(token)`,
    credenciais do seed do profile `e2e`.
  - `Mailpit.java` — buscar o último e-mail de um destinatário e extrair links.
  - `Dados.java` — `stamp()` com prefixo `e2e-`.
  - `Arquivos.java` — leitura das amostras em `src/e2e/resources/amostras/`.

## 5. Escrever e verificar

Na raiz do repositório:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml up -d --build --wait
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

`backend/.claude/skills/criar-teste-e2e/padrao.md`:

`````markdown
# Testes e2e — o padrão do projeto

Os testes e2e tratam o sistema como **caixa-preta**: a stack completa sobe com
`infra/compose.prod.yml` + `infra/compose.e2e.yml` (Caddy, API, worker, PostgreSQL, Garage,
Mailpit) usando **as mesmas imagens que vão para produção**, e os testes chamam o sistema só
por HTTP, como um cliente faria. Não há acesso ao banco, ao código da aplicação nem a mocks.

Ficam no próprio projeto Java, num source set separado (`src/e2e/java`), e só rodam com o
perfil Maven `e2e`.

O e2e é **pouco e caro**: cobre só o que nenhum teste de integração alcança. Regra, papel e
acesso caso a caso já estão nos níveis abaixo.

## O que é jornada e2e

Entra no e2e o que depende de **vários containers juntos** ou da infraestrutura:

- roteamento do Caddy por host (`aluno.localhost`, `adm.localhost`, `media.localhost`) e
  proxy de `/api`;
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
    E2eConfig.java         # URLs base por host, vindas de propriedades do sistema
    Api.java               # RequestSpecification por host, login(), bearer()
    Mailpit.java           # buscar e-mail, extrair link
    Dados.java             # stamp() com prefixo e2e-
    Arquivos.java          # leitura das amostras
  <jornada>/
    <Jornada>E2E.java      # CadastroE2E, SessaoE2E, VideoE2E, ProxyE2E...
src/e2e/resources/
  amostras/video-3s.mp4    # vídeo de amostra de 3 segundos
```

- Nome da classe termina em `E2E`. Um arquivo por jornada.
- Bibliotecas: JUnit 5, REST Assured, Awaitility, AssertJ (escopo de teste, usadas só pelo
  perfil `e2e`).
- Os usuários iniciais vêm do seed do profile `e2e` da API; as credenciais ficam em
  `support/Api.java`.

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
- O que o Caddy acrescenta: `Strict-Transport-Security` (quando HTTPS), `X-Content-Type-Options`,
  `Referrer-Policy`, `Cache-Control` dos assets e do `index.html`.
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
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml up -d --build --wait
```

Dentro de `backend/`:

```bash
./mvnw verify -Pe2e                          # todas as jornadas
./mvnw verify -Pe2e -Dit.test=VideoE2E       # uma jornada
```

Depois, na raiz: `docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml down -v`.

Se uma jornada falhar por **bug real**, não ajuste o teste para passar — avise com a
jornada, o passo, o esperado e o obtido.
`````

#### 2.3. Build e configuração

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
		<spotless.version>3.10.4</spotless.version>
		<palantir-java-format.version>2.102.0</palantir-java-format.version>
		<jacoco.version>0.8.15</jacoco.version>
		<!-- Cobertura mínima de linhas (unitários + integração) -->
		<jacoco.cobertura.minima>0.80</jacoco.cobertura.minima>
		<!-- -Dskip.unit=true roda só os testes de integração -->
		<skip.unit>false</skip.unit>
	</properties>

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
			<artifactId>spring-boot-starter-security</artifactId>
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
					<skipTests>${skip.unit}</skipTests>
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

# Swagger: ligado só no desenvolvimento e na homologação (DOCS_ENABLED=true)
springdoc:
  api-docs:
    enabled: ${DOCS_ENABLED:false}
  swagger-ui:
    enabled: ${DOCS_ENABLED:false}
`````

`backend/src/main/resources/application-worker.yaml`:

`````yaml
# Worker: processa a fila (transcodificação de vídeo, e-mails) e não atende HTTP
spring:
  main:
    web-application-type: none
`````

#### 2.4. Segurança padrão

`backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`:

`````java
package br.com.celke.cursos.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Segurança da API: tudo exige autenticação, exceto as rotas públicas declaradas aqui com o
 * método HTTP. A API é stateless (token no cabeçalho), sem sessão, formulário ou HTTP Basic.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        erros -> erros.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
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

#### 2.5. Base dos testes de integração e primeiro teste

`backend/src/test/java/br/com/celke/cursos/support/Containers.java`:

`````java
package br.com.celke.cursos.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Containers dos testes de integração. Como todas as classes de teste compartilham o mesmo
 * contexto Spring, cada container sobe uma vez por execução.
 */
@TestConfiguration(proxyBeanMethods = false)
public class Containers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/support/IntegrationTest.java`:

`````java
package br.com.celke.cursos.support;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Base de todos os testes de integração (*IT). Não altere para atender um teste específico:
 * qualquer variação cria outro contexto Spring e deixa a suíte lenta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(Containers.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvcTester mvc;

    /** Identificador único em runtime para os dados criados pelo teste (prefixo {@code it-}). */
    protected static String stamp() {
        return "it-" + Long.toString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), 36);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/security/SegurancaPadraoIT.java`:

`````java
package br.com.celke.cursos.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/**
 * Cobre: rotas públicas de infraestrutura e o bloqueio padrão de qualquer outra rota.
 * Não cobre: autenticação por token (fase de autenticação).
 * Dado criado: nenhum.
 */
class SegurancaPadraoIT extends IntegrationTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    @DisplayName("health e probes são públicos e respondem UP")
    void healthPublico(String rota) {
        assertThat(mvc.get().uri(rota))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    @DisplayName("health não expõe detalhes dos componentes")
    void healthSemDetalhes() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk().bodyJson().doesNotHavePath("$.components");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/qualquer-rota", "/actuator/env", "/actuator/beans", "/actuator/metrics"})
    @DisplayName("qualquer outra rota exige autenticação")
    void rotaPrivadaSemToken(String rota) {
        assertThat(mvc.get().uri(rota)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST em rota pública só de leitura também exige autenticação")
    void metodoNaoDeclaradoExigeAutenticacao() {
        assertThat(mvc.post().uri("/actuator/health")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/v3/api-docs", "/swagger-ui.html"})
    @DisplayName("documentação desligada por padrão")
    void documentacaoDesligada(String rota) {
        assertThat(mvc.get().uri(rota)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
`````

#### 2.6. Imagens Docker

`backend/Dockerfile`:

`````dockerfile
# syntax=docker/dockerfile:1

# ---- Build ----
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Dspotless.check.skip=true \
    && java -Djarmode=tools -jar target/plataforma-cursos-*.jar extract --layers --launcher --destination target/extracted

# ---- Base de execução (sem root) ----
FROM eclipse-temurin:25-jre-noble AS runtime
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app

# ---- API ----
FROM runtime AS api
COPY --from=build /app/target/extracted/dependencies/ ./
COPY --from=build /app/target/extracted/spring-boot-loader/ ./
COPY --from=build /app/target/extracted/snapshot-dependencies/ ./
COPY --from=build /app/target/extracted/application/ ./
USER app
ENV SPRING_PROFILES_ACTIVE=api
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# ---- Worker (com ffmpeg) ----
FROM runtime AS worker
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/extracted/dependencies/ ./
COPY --from=build /app/target/extracted/spring-boot-loader/ ./
COPY --from=build /app/target/extracted/snapshot-dependencies/ ./
COPY --from=build /app/target/extracted/application/ ./
USER app
ENV SPRING_PROFILES_ACTIVE=worker
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
`````

`backend/.dockerignore`:

`````text
target/
.claude/
CLAUDE.md
.idea/
.vscode/
*.iml
`````

### 3. Formatar e verificar

Dentro de `backend/`:

```bash
./mvnw spotless:apply
./mvnw verify
```

O `verify` precisa do Docker rodando (o Testcontainers sobe um PostgreSQL 18). Depois, confira
as imagens:

```bash
docker build --target api -t plataforma-cursos-api:local .
docker build --target worker -t plataforma-cursos-worker:local .
docker run --rm --entrypoint ffmpeg plataforma-cursos-worker:local -version
```

## Antes de executar

- Repositório com o commit inicial e a branch `feature/base-projeto` criada a partir da `homolog`.
- JDK 25 instalado (para o `./mvnw`) e Docker rodando.

## Verificação

- `./mvnw verify` termina com `BUILD SUCCESS`: 11 testes em `SegurancaPadraoIT`, cobertura
  mínima atendida e nenhuma pendência do Spotless.
- O log da aplicação nos testes **não** mostra `Using generated security password`.
- As imagens `api` e `worker` são construídas; o worker tem ffmpeg; as duas rodam com o usuário `app`
  (`docker run --rm --entrypoint id plataforma-cursos-api:local`).
- Numa nova sessão do Claude Code, ao abrir um arquivo de `backend/`, as skills
  `/criar-teste-unitario`, `/criar-teste-integracao` e `/criar-teste-e2e` ficam disponíveis.

## Depois de executar

```bash
git add CLAUDE.md backend/
git commit -m "Esqueleto do backend e padrão de testes"
```
