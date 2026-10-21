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
