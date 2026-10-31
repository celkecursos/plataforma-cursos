# Orientações para agentes de IA

**Fase:** 0 — Preparação
**Branch:** `feature/base-projeto`

## Prompt

Vamos registrar no repositório as orientações para agentes de IA, antes de qualquer código, para que
todas as etapas seguintes sigam as mesmas regras: rules que carregam sozinhas conforme o caminho
tocado (segurança do backend e da infra, fronts, testes existentes, prompts do tutorial, artefatos do
SDD), o padrão das mensagens de commit (Conventional Commits em português, sem coautoria nem menção a
ferramenta de IA) com os comandos `/gc` e `/pc`, os comandos `/nova-branch`, `/abrir-pr` e
`/revisar-dod`, e o pipeline SDD — especificação → tasks → código, com três portões humanos e os
agentes `pm-spec-architect` (persona da Fase 1), `dev-lead`, `dev-pleno` e `dev-senior`.

Tudo fica em `.claude/` na raiz e é versionado. O `CLAUDE.md` da raiz, criado na próxima etapa, aponta
para estas orientações; o `backend/CLAUDE.md` e as skills de teste do backend também vêm na próxima
etapa. Não faça commit.

Crie cada arquivo abaixo com **exatamente** o conteúdo indicado.

### 1. Índice e permissões

`.claude/README.md`:

`````markdown
# `.claude/` — orientações para agentes de IA

Regras, skills, agentes e comandos do Claude Code para este repositório. Tudo é Markdown com
frontmatter e fica versionado: quem clona o projeto recebe as mesmas orientações.

## Hierarquia (a de cima vence)

1. `CLAUDE.md` da raiz — regras gerais, branches e a lista do que existe aqui.
2. `backend/CLAUDE.md` — regras do Java (definição de pronto, estrutura, padrões, testes).
3. `.claude/rules/` — detalhes que carregam sozinhos ao tocar os caminhos do `paths` de cada uma.
4. `.claude/skills/` e `backend/.claude/skills/` — processos passo a passo, sob demanda.
5. Documentação oficial das bibliotecas — a versão do `pom.xml`/`package.json` é a que vale; não
   confie na memória para API de biblioteca.

## O que existe

```
.claude/
├── settings.json                 permissões de comandos de leitura e verificação; .env bloqueado
├── rules/
│   ├── seguranca-backend.md      rotas privadas, segredos, dados sensíveis, auditoria (backend/src/main)
│   ├── frontend.md               padrões dos fronts e do site (web/)
│   ├── testes-existentes.md      não enfraquecer teste existente para passar (testes)
│   ├── prompts-do-tutorial.md    prompts atemporais e sem citar outros projetos (docs/prompts*)
│   └── sdd-artefatos.md          contrato das specs e tasks (.sdd/)
├── skills/
│   ├── conventional-commits/     padrão das mensagens de commit (pt-BR)
│   ├── gc/                       /gc: gera a mensagem de commit da worktree
│   ├── pc/                       /pc: commita com a mensagem do /gc e faz o push
│   ├── write-specs/              Fase 1 do SDD: especificação agnóstica
│   └── write-tasks/              Fase 2 do SDD: tasks a partir da spec aprovada
├── agents/
│   ├── pm-spec-architect.md      persona da Fase 1 (não se despacha)
│   ├── dev-lead.md               Fase 2: spec → tasks
│   ├── dev-pleno.md              Fase 3: implementa seguindo padrão existente
│   └── dev-senior.md             Fase 3: define padrão novo
└── commands/
    ├── sdd.md                    /sdd <ideia>
    ├── gerar-tasks.md            /gerar-tasks <spec>
    ├── implementar-tasks.md      /implementar-tasks <readme> <spec>
    ├── revisar-dod.md            /revisar-dod [caminho]
    ├── nova-branch.md            /nova-branch <funcionalidade>
    └── abrir-pr.md               /abrir-pr
```

As skills de teste do backend ficam em `backend/.claude/skills/` (`criar-teste-unitario`,
`criar-teste-integracao`, `criar-teste-e2e`).

## Pipeline SDD (spec → tasks → código)

```
/sdd <ideia>                          → .sdd/specs/<slug>.spec.md        ⛔ aprovar a spec
/gerar-tasks <spec>                   → .sdd/tasks/<slug>-v<versão>/     ⛔ revisar as tasks
/implementar-tasks <readme> <spec>    → código + testes                  ⛔ revisar o diff e a verificação
```

Os três portões são humanos. A Fase 1 roda no chat principal (é uma conversa); as Fases 2 e 3
delegam aos agentes.

## Contribuindo

- **Rule**: `rules/<nome>.md` com `paths` (opcional) e `description`.
- **Skill**: `skills/<nome>/SKILL.md` com `name` e uma `description` que diga quando usar.
- **Agente**: `agents/<nome>.md` com `name`, `description`, `model` e `tools`. `tools` é uma lista
  fechada: se o corpo manda consultar algo (documentação na web, outra skill), a ferramenta precisa
  estar na lista.
- **Comando**: `commands/<nome>.md` com `description` e `argument-hint`.
`````

`.claude/settings.json`:

`````json
{
  "$schema": "https://json.schemastore.org/claude-code-settings.json",
  "permissions": {
    "allow": [
      "Bash(git status:*)",
      "Bash(git diff:*)",
      "Bash(git log:*)",
      "Bash(git show:*)",
      "Bash(git branch:*)",
      "Bash(git worktree list:*)",
      "Bash(gh pr view:*)",
      "Bash(gh pr checks:*)",
      "Bash(gh run view:*)",
      "Bash(gh run watch:*)",
      "Bash(./mvnw spotless:apply:*)",
      "Bash(./mvnw verify:*)",
      "Bash(./mvnw test:*)",
      "Bash(npm run format:*)",
      "Bash(npm run lint:*)",
      "Bash(npm run typecheck:*)",
      "Bash(npm test:*)",
      "Bash(npm run build:*)",
      "Bash(npm run e2e:*)",
      "Bash(docker compose ps:*)",
      "Bash(docker compose logs:*)"
    ],
    "deny": ["Read(./infra/.env)", "Read(./**/.env)", "Bash(git push --force:*)", "Bash(git push -f:*)"]
  }
}
`````

### 2. Rules (carregam sozinhas pelo caminho tocado)

`.claude/rules/frontend.md`:

`````markdown
---
paths:
  - "web/**"
description: Padrões dos fronts (área do aluno e ADM em React) e do site público gerado. Carrega ao tocar web/.
---

# Fronts e site (`web/`)

## Stack

React 19 + TypeScript + Vite, componentes da biblioteca `@celke/kole-react`, Vitest + Testing Library
nos testes de unidade e Playwright nos e2e. Comandos dentro de `web/`:

```bash
npm run format:check && npm run lint && npm run typecheck && npm test && npm run build
```

## Estrutura

```
web/
  compartilhado/src/   cliente da API, sessão, componentes e utilitários usados pelos dois fronts
  aluno/src/           área do aluno: rotas, layout, páginas
  adm/src/             ADM: rotas, layout, páginas
  site/                gerador do site público (HTML a partir da API)
  e2e/                 Playwright: specs por projeto (aluno, adm, site) e apoio em support/
```

## Regras

- **Use o componente da biblioteca** (`@celke/kole-react`) antes de criar um; não reestilize por dentro
  as classes internas dela. Faltou algo? componha por fora ou pergunte.
- **Toda chamada à API passa pelo cliente de `compartilhado/src/api`**: ele põe o token, renova a
  sessão pelo cookie e transforma o `ProblemDetail` em erro tipado. Nada de `fetch` solto.
- **Access token só em memória**; o refresh fica no cookie `HttpOnly`. Nunca `localStorage` para token.
- Erro de validação do backend (`erros[]` com `campo`) aparece no campo certo do formulário.
- Toda tela tem os estados carregando, vazio e erro.
- Texto da tela em português; rótulo de campo e nome de botão são o que o e2e usa como seletor —
  mudou por UX, conserte o teste, não volte o rótulo.
- Token ou dado sensível que chega pela URL (link de e-mail) sai da barra de endereço depois de lido.
- Redirecionamento externo só para destino conhecido; parâmetro `?volta=` só aceita caminho interno.
- Lógica pura (formatação, regra de exibição, montagem de caminhos) fica em função testável com teste
  `*.test.ts` ao lado.

## e2e

Guia completo em `web/e2e/README.md`. Seletores pelo que o usuário vê (`getByRole`, `getByLabel`),
espera por condição (nunca por tempo fixo), dado de teste com carimbo único, contas do seed só leitura.
`````

`.claude/rules/prompts-do-tutorial.md`:

`````markdown
---
paths:
  - "docs/prompts/**"
  - "docs/prompts-iniciais/**"
description: Como escrever e manter os prompts do tutorial — atemporais, sem citar outros projetos, com o conteúdo exato. Carrega ao tocar docs/prompts ou docs/prompts-iniciais.
---

# Prompts do tutorial

- `docs/prompts/` tem um prompt por fase (especificação + verificação); `docs/prompts-iniciais/` é a
  sequência completa para criar o projeto do zero, com o conteúdo exato de cada arquivo. Mudou uma
  fase? Atualize **os dois**.
- **Atemporal:** o prompt descreve o projeto como se tivesse nascido assim. Sem datas, "versão 2",
  "atualizado", "correção", "antes era", histórico de mudanças.
- **Sem citar outros projetos**, caminhos de outras máquinas ou repositórios de onde algo foi
  aproveitado.
- Arquivo gerado por ferramenta (lockfile, wrapper do Maven, listas baixadas) não vai embutido: o
  prompt manda rodar o comando que o gera.
- Cada prompt termina com **Antes de executar** (branch a partir da `homolog`), **Verificação** (os
  comandos da definição de pronto) e **Depois de executar** (commit no padrão Conventional Commits,
  push e Pull Request para a `homolog`).
- O índice (`README.md` de cada pasta) lista os prompts na ordem de execução.
`````

`.claude/rules/sdd-artefatos.md`:

`````markdown
---
paths:
  - ".sdd/**"
description: Contrato dos artefatos do SDD (specs e tasks) — o que não pode mudar de forma ao editar uma spec ou uma task. Carrega ao tocar .sdd/.
---

# Artefatos do SDD

Os arquivos em `.sdd/` são lidos por agentes nas fases seguintes. Mudar um campo estruturado quebra
o pipeline longe daqui, sem erro na hora.

## `.sdd/specs/<slug>.spec.md` (Fase 1)

- **Nove seções, nesta ordem:** Resumo Executivo · Atores e Papéis · Requisitos Funcionais · Modelo de
  Domínio · Estados e Fluxos · Contratos de Interface · Casos de Borda · Premissas Validadas · Riscos.
- **Requisitos `RF-NN`** no formato "O sistema DEVE…". O número é a chave de rastreio spec → task →
  arquivo: **nunca renumere**. RF novo entra no fim; RF descartado vira "RF-NN — REMOVIDO", não some.
- **Agnóstica de tecnologia:** sem linguagem, framework, biblioteca, nome de arquivo, código ou SQL.
- **Frontmatter:** `feature`, `versao`, `status` (`rascunho` | `aprovada`), `autor`. Revisar uma spec
  aprovada: suba a `versao` e volte o `status` para `rascunho`.
- **Valores do projeto:** domínio, subdomínios, e-mails e dono do repositório vêm de
  `docs/configuracao.md` e aparecem com o valor real (ex.: o host da área do aluno). Senha, token,
  chave e IP nunca entram numa spec.

## `.sdd/tasks/<slug>-v<versao>/` (Fase 2)

- **`Nível:`** é a chave do despacho da Fase 3: exatamente `Pleno` ou `Sênior`.
- **`Depende de:`** define ordem e paralelismo. Tasks sem dependência entre si rodam juntas e por isso
  **não podem listar o mesmo arquivo**.
- **Arquivos compartilhados** — `SecurityConfig`, `application.yaml`, `infra/.env.example`, os compose,
  `rotas.tsx` de cada front e a migration da fase — pertencem a **uma** task de fiação, que depende das
  outras e roda por último.
- **`Spec:` + `atende RF-NN`** em toda task.
- **Sem código nas tasks:** contratos em prosa e o arquivo exato a espelhar. Exceção: `record`, `enum`
  ou `type` de domínio que fixa o contrato.
- Mudou o escopo? Pasta nova `<slug>-v<nova-versao>/`; não reescreva tasks já implementadas.
- **Formato:** o da skill `write-tasks` (`<FEAT>-NNN-<titulo>.md`, README com grafo e RF → tasks).

## `.sdd/README.md` (índice)

- Uma linha por spec: nome, versão vigente e as pastas de tasks de cada versão.
- Spec nova ou versão nova: atualize o índice no mesmo commit.

## Não fazer

- Implementar a partir de uma spec sem passar por `/gerar-tasks`.
- Marcar uma spec como `aprovada` por conta própria: os portões são humanos.
- Apagar spec ou task antiga.
`````

`.claude/rules/seguranca-backend.md`:

`````markdown
---
paths:
  - "backend/src/main/**"
  - "infra/**"
description: Regras de segurança do backend e da infraestrutura — rotas privadas por padrão, segredos, dados sensíveis, auditoria e limites. Carrega ao tocar o código de produção do backend ou a infra.
---

# Segurança do backend

## Rotas

- **Toda rota nasce privada.** Rota pública só existe declarada em `shared/security/SecurityConfig`,
  com o método HTTP e um comentário dizendo por que é pública.
- Rota pública nova entra também na lista de rotas públicas esperadas do teste que varre as rotas;
  sem isso o teste falha — é proposital.
- `/api/adm/**` exige `ADMIN` no `SecurityConfig` **e** `@PreAuthorize` no controller.
- Conteúdo de curso (aula, vídeo, arquivo, fórum, progresso, certificado) passa sempre pela
  `AcessoAulaPolicy`. Nunca confie em id vindo do cliente sem conferir o dono.
- Rota de autenticação ou de conta que recebe POST anônimo entra no limite por IP.

## Dados

- Segredo só em variável de ambiente (`.env` fora do git, secrets do GitHub). Variável nova: no
  `application.yaml` com `${VARIAVEL}`, validada em `@ConfigurationProperties`, no `.env.example`, nos
  scripts que geram o `.env` e nos compose.
- Segredo de terceiros guardado no banco é cifrado (AES-256-GCM com dado associado); token guardado no
  banco é só o hash.
- Campo sensível (hash de senha, segredo, token) leva `@DadoSensivel`: aparece mascarado na trilha.
- Entidade nova entra na auditoria por padrão; `@SemAuditoria` só para log, dado técnico de sessão ou
  mudança frequente demais, com o motivo no Javadoc.
- Nunca registre em log senha, token, segredo, corpo de requisição com dado pessoal ou cookie.

## Respostas

- Erro sempre em `ProblemDetail`; nada de stack trace, SQL ou nome de classe na resposta.
- Login e recuperação de senha respondem igual para e-mail existente e inexistente.
- Upload confere tipo pelo conteúdo e tamanho no servidor, nunca só pela extensão.
- Redirecionamento só para destino conhecido (lista de hosts ou caminho relativo).

## Banco

- Migration compatível com a versão anterior (expandir → contrair): o rollback do deploy não desfaz
  migration.
- Consulta com parâmetro, nunca concatenando texto do usuário.
`````

`.claude/rules/testes-existentes.md`:

`````markdown
---
paths:
  - "backend/src/test/**"
  - "backend/src/e2e/**"
  - "web/**/*.test.ts"
  - "web/**/*.test.tsx"
  - "web/e2e/**"
description: Proteção dos testes que já existem — nunca enfraquecer um teste para ele passar. Carrega ao tocar testes.
---

# Testes existentes

Pode, sem pedir:

- criar arquivos de teste novos;
- acrescentar casos em arquivos de teste que já existem;
- ajustar um teste quando a **regra mudou de propósito** na mesma tarefa (ex.: a política de senha
  ficou mais rígida e o dado de teste antigo deixou de ser válido), dizendo no relatório qual teste
  mudou e por quê.

Não pode:

- apagar um teste, trocar a asserção por outra mais fraca, aumentar tolerância, colocar `@Disabled`,
  `skip`, `only` ou espera por tempo fixo **para fazer um teste vermelho passar**;
- mudar o teste quando quem está errado é o código.

Teste falhou e você não sabe se o errado é o código ou o teste? Pare e explique ao usuário o que o
teste espera, o que o código faz e qual dos dois você acha que está certo.
`````

### 3. Skills

`.claude/skills/conventional-commits/SKILL.md`:

`````markdown
---
name: conventional-commits
description: Padrão Conventional Commits, em português, para toda mensagem de commit e título de Pull Request. Use sempre que for escrever, revisar ou corrigir uma mensagem de commit, um título de PR ou um commit de merge/revert. Define o formato `<tipo>(<escopo>): <descrição>`, os dez tipos aceitos, a descrição com verbo no infinitivo, o `!` e o `BREAKING CHANGE:` e a proibição de rodapés que creditam ferramentas.
---

# Mensagens de commit — Conventional Commits

```
<tipo>(<escopo opcional>): <descrição>

<corpo opcional>

<rodapés opcionais>
```

## Antes de escrever

- **Descreva o que vai no commit**, não o que você pretendia fazer: leia `git diff --staged --stat`
  (ou o `git status` quando o commit leva a worktree inteira).
- **Reaproveite os escopos do histórico** (`git log --oneline -20`). Escopos deste repositório seguem
  os módulos e áreas: `auth`, `usuario`, `curso`, `matricula`, `video`, `progresso`, `arquivo`,
  `forum`, `certificado`, `perfil`, `pagamento`, `auditoria`, `shared`, `adm`, `aluno`, `site`, `e2e`,
  `infra`, `ci`, `docs`, `prompts`.
- **Em português**, com acentos. Ficam em inglês só os tokens da especificação: o tipo,
  `BREAKING CHANGE:` e os rodapés (`Refs:`, `Closes:`).

## A primeira linha

| Tipo       | Para                                                         |
| ---------- | ------------------------------------------------------------ |
| `feat`     | funcionalidade nova para o usuário ou para a API             |
| `fix`      | correção de defeito                                          |
| `docs`     | só documentação (inclui os prompts do tutorial)              |
| `style`    | formatação, sem mudar significado                            |
| `refactor` | mudança que não corrige defeito nem cria funcionalidade      |
| `perf`     | mudança feita por desempenho                                 |
| `test`     | testes que faltavam ou correção de testes                    |
| `chore`    | build, dependências, configuração, ferramentas               |
| `ci`       | pipeline (GitHub Actions) e scripts dele                     |
| `revert`   | desfazer um commit anterior (SHA no rodapé)                  |

- Nenhum outro tipo (`build` → `chore`; `wip` não chega a branch compartilhada).
- O tipo diz o **efeito** da mudança, não os arquivos tocados.
- **Descrição:** verbo no infinitivo ("este commit vai…": _adicionar_, _corrigir_, _remover_),
  minúscula, sem ponto final, até ~72 caracteres. Não repita o tipo (`fix: corrigir…` gasta a linha).

## Corpo e rodapés

- Corpo separado por linha em branco: explica **por quê** (a restrição, a causa do defeito, a
  alternativa descartada), não narra o diff. Quebre em ~72 colunas.
- Quebra de compatibilidade: `!` antes dos dois-pontos **e** rodapé `BREAKING CHANGE: <o que quem usa
  precisa fazer>` (ex.: variável de ambiente nova obrigatória).
- **Crédito é para pessoas.** Nunca `Co-Authored-By` de IA, "Generated with", "Gerado por", emoji de
  robô ou nome de modelo — nem no assunto, nem no corpo, nem em rodapé.

## Regras que mantêm o histórico útil

- **Uma mudança lógica por commit.** Se a descrição precisa de "e", são dois commits.
- **Código e teste juntos** no mesmo commit; `test:` é para teste de código que já existia.
- Commit de merge gerado pelo git (`Merge pull request…`) fica como está.
- Revert: `revert: <assunto do commit desfeito>` com `Refs: <sha>`.

## Exemplos

```text
feat(auth): exigir segundo fator no login dos administradores
```

```text
fix(pagamento): ignorar notificação de oferta não cadastrada

A Hotmart reenvia a notificação por dias; recusar com erro fazia a fila
de reenvios dela crescer sem que nada pudesse ser feito do nosso lado.

Refs: #42
```

```text
chore(infra)!: ler a chave do segundo fator de TOTP_CHAVE

BREAKING CHANGE: o .env de cada ambiente precisa de TOTP_CHAVE
(openssl rand -base64 32); sem ela a API não sobe.
```

## Antes de commitar

- [ ] Descreve o que está no commit
- [ ] Português; só tipo e rodapés em inglês
- [ ] Um dos dez tipos, fiel ao efeito
- [ ] Escopo do vocabulário do histórico, ou nenhum
- [ ] Infinitivo, minúscula, sem ponto, ~72 caracteres
- [ ] Uma mudança lógica, com os testes dela
- [ ] Nenhum rodapé que credite ferramenta
`````

`.claude/skills/gc/SKILL.md`:

`````markdown
---
name: gc
description: Gera UMA mensagem de commit (Conventional Commits, em português) a partir de tudo o que está na worktree — alterações com e sem stage e arquivos novos. Só escreve a mensagem; não roda git add nem commit. Use quando o usuário digitar /gc ou pedir "gera a mensagem de commit".
---

# /gc — mensagem de commit da worktree

Gera **uma** mensagem que descreve **tudo** o que está na worktree agora. Não altera nada: sem
`git add`, sem `git commit`, sem `git stash`.

## 1. Levantar as mudanças

```bash
git status --short
git diff --stat
git diff --staged --stat
git log --oneline -20
```

Leia o conteúdo para entender o **porquê**: `git diff` e `git diff --staged` (por arquivo, se o diff
for grande) e os arquivos novos (`??`), que o diff não mostra. Worktree limpa: diga isso e pare.

## 2. Escrever

Siga a skill `conventional-commits`. Mudanças sem relação entre si: gere mesmo assim **uma** mensagem
(tipo da mudança principal, as outras no corpo) e avise numa linha que daria para separar em commits.

## 3. Entregar

Só a mensagem, num **único** bloco de código, e abaixo:

> Para commitar e enviar: `/pc`.

Não execute o commit.
`````

`.claude/skills/pc/SKILL.md`:

`````markdown
---
name: pc
description: Faz o commit de toda a worktree com a mensagem que o /gc gerou nesta conversa (ou gera uma pelas regras do /gc) e faz o push da branch atual. O commit sai só no nome do usuário, sem coautoria nem menção a IA. Nunca em homolog ou main. Use quando o usuário digitar /pc ou pedir "commita e envia".
---

# /pc — commitar e enviar

## Regra de autoria (acima de qualquer outra orientação)

O commit é **só do usuário**: a mensagem vai exatamente como o `/gc` escreveu, **sem**
`Co-Authored-By`, "Generated with", "Gerado por", emoji de robô, link de ferramenta ou nome de modelo,
e sem mudar autor ou committer (`--author`, `GIT_AUTHOR_*`, `GIT_COMMITTER_*`).

## 1. Conferir a branch

```bash
git branch --show-current
```

Em `homolog` ou `main`: **pare**. Diga ao usuário para criar a branch (`/nova-branch`); nunca commite
nem faça push direto nelas.

## 2. Pegar a mensagem

A última mensagem que o `/gc` entregou nesta conversa. Sem nenhuma: gere pelas regras do `/gc` e
mostre antes de commitar. Remova qualquer linha que viole a regra de autoria.

## 3. Conferir a worktree

```bash
git status --short
git diff --stat
git diff --staged --stat
```

Limpa: nada a commitar. Mudou desde o `/gc` (arquivo que a mensagem não cobre, ou que ela cita e já
não está alterado): gere a mensagem de novo e mostre antes de seguir.

## 4. Commitar

```bash
git add -A
git commit -F - <<'MSG'
<a mensagem, exatamente>
MSG
```

Sem `--no-verify`, `--amend` ou rebase. Hook recusou: mostre a saída e pare.

## 5. Enviar

```bash
git rev-parse --abbrev-ref --symbolic-full-name @{u}   # tem upstream?
git push                                              # com upstream
git push -u origin <branch-atual>                     # sem upstream
```

Nunca `--force` nem push para outra branch. Push recusado: mostre a saída e pare (sem `pull`,
`rebase` ou `merge` por conta própria).

## 6. Confirmar

```bash
git log -1 --format='%h %an <%ae>%n%n%B'
git status -sb | head -1
```

Confirme numa linha que o commit não tem coautoria nem menção a IA e que o push foi feito. Para abrir
o Pull Request para a `homolog`: `/abrir-pr`.
`````

`.claude/skills/write-specs/SKILL.md`:

`````markdown
---
name: write-specs
description: Transforma uma ideia crua de funcionalidade numa especificação rigorosa e agnóstica de tecnologia — descoberta com perguntas, validação de premissas, modelo de nove seções em .sdd/specs/ e parada obrigatória para aprovação humana antes de virar tasks ou código. É a Fase 1 do SDD (comando /sdd).
---

# Escrever especificações (Fase 1 do SDD)

Transforme a ideia do usuário numa **especificação rigorosa e agnóstica de tecnologia** e **pare para
aprovação**. A spec é o contrato das fases seguintes: decisão errada custa muito menos aqui do que no
código.

## Regras

- **Agnóstica:** descreve o QUÊ e o PORQUÊ (atores, regras, estados, contratos, casos de borda), nunca
  o COMO. Sem linguagem, framework, biblioteca, nome de arquivo, código, pseudocódigo ou SQL.
- **Onde salvar:** `.sdd/specs/<slug>.spec.md` (kebab-case, ex.: `cupom-de-desconto.spec.md`).
- **Contexto do produto:** leia `README.md` e `docs/plano-implementacao.md` para usar os mesmos termos
  do domínio (curso, turma, matrícula, aula, oferta, pedido…) e não especificar o que já existe.
- **Portão:** pergunte explicitamente se o usuário aprova antes de qualquer avanço.
- **Valores do projeto:** use os de `docs/configuracao.md` (domínio, subdomínios, e-mails, dono do
  repositório) nos contratos e nas premissas. Senha, token, chave e IP nunca entram na spec.

## Passos

1. **Analise** a ideia.
2. **Descoberta:** até **5 perguntas por rodada** — o porquê de negócio, o "e se" dos casos de borda,
   conflitos com regras que já existem (acesso ao conteúdo, pagamentos, LGPD, auditoria), volume.
   Desafie a complexidade: se parece grande demais, proponha a versão menor que resolve.
3. **Premissas e riscos:** liste e espere a confirmação **antes** de redigir.
4. **Redija** no modelo abaixo, salve e acrescente a spec ao índice `.sdd/README.md`.
5. **Pare** e pergunte: _"Esta é a especificação baseada na nossa conversa. Revise cada seção (comente
   ou peça alterações). Você aprova?"_ Só depois de um "sim" explícito informe:
   `/gerar-tasks .sdd/specs/<slug>.spec.md`.

## Modelo (nove seções)

```markdown
---
feature: <slug>
versao: 1.0
status: rascunho
autor: pm-spec-architect
---

# <Nome da funcionalidade>

## 1. Resumo Executivo
O problema, o valor e o escopo (o que entra e o que fica de fora).

## 2. Atores e Papéis
Quem usa ou é afetado (visitante, aluno, administrador, meio de pagamento, worker) e o que cada um pode.

## 3. Requisitos Funcionais
`RF-01`, `RF-02`… no formato "O sistema DEVE…", verificáveis e sem ambiguidade.

## 4. Modelo de Domínio
Entidades, atributos e relações em linguagem de negócio.

## 5. Estados e Fluxos
Estados de cada entidade, transições e o passo a passo dos fluxos principais.

## 6. Contratos de Interface
Entradas e saídas de cada operação, em termos de domínio (nome do campo e significado).

## 7. Casos de Borda
Erros, concorrência, vazios, limites, permissões, dados pessoais.

## 8. Premissas Validadas
O que foi assumido e confirmado pelo usuário.

## 9. Riscos
O que pode dar errado e a mitigação.
```

Ao revisar uma spec aprovada: suba a `versao`, volte o `status` para `rascunho` e peça aprovação de
novo.
`````

`.claude/skills/write-tasks/SKILL.md`:

`````markdown
---
name: write-tasks
description: Decompõe uma spec aprovada em tasks de implementação em .sdd/tasks/<slug>-v<versao>/ — README com ordem e grafo, uma task por arquivo, sem código, com nível, dependências, arquivos exatos e critérios de aceite. É a Fase 2 do SDD (comando /gerar-tasks, executado pelo dev-lead).
---

# Escrever tasks (Fase 2 do SDD)

Transforme uma spec **aprovada** em tasks que um agente executa sem adivinhar nada. A spec diz o QUÊ;
a task diz ONDE e COMO no código deste repositório, em prosa.

## Antes de escrever

1. Confira o `status: aprovada` da spec (ou o "sim" do usuário). Sem isso, pare.
2. Leia `CLAUDE.md`, `backend/CLAUDE.md`, `.claude/rules/` e `docs/plano-implementacao.md`.
3. Escolha o módulo existente mais parecido com o caso: é o espelho que as tasks vão citar.
4. Leia `docs/configuracao.md`: hosts, e-mails e endereços citados nas tasks usam esses valores.

## Regras

- **Rastreio:** toda task atende a um ou mais `RF-NN` da spec, e todo `RF-NN` é atendido por alguma
  task. Escreva no README a tabela RF → tasks.
- **Sem código:** contratos em prosa e o arquivo exato a espelhar. Exceção: `record`, `enum` ou `type`
  de domínio que fixa o contrato.
- **Explícito:** nomeie arquivo, classe, rota (método + caminho), tabela e migration. Proibido "algo
  como".
- **Nível:** `Pleno` segue padrão existente; `Sênior` define padrão (módulo novo, segurança, acesso ao
  conteúdo, integração externa, fila, migration com dado existente, decisão que cruza módulos).
- **Paralelismo:** tasks sem dependência entre si rodam juntas e por isso **não listam o mesmo
  arquivo**.
- **Fiação por último:** `SecurityConfig`, `application.yaml`, `infra/.env.example`, os compose, a
  migration da funcionalidade e o `rotas.tsx` de cada front ficam numa única task de fiação, que depende
  de todas as outras.
- **Teste faz parte da task:** cada task com regra lista o teste (unitário, integração com a matriz de
  papéis, e2e quando atravessa containers, Vitest ou Playwright no front).
- **Sem segredos:** cite o nome da variável (`SMTP_PASSWORD`, `JWT_SEGREDO`), nunca o valor. Senha,
  token, chave e IP não entram em task.

## Onde salvar

`.sdd/tasks/<slug>-v<versao>/`, com a versão da spec (`autenticacao-v1.0/`). Spec revisada ganha uma
pasta nova; as tasks já implementadas não são reescritas.

- `README.md` — tabela (Ordem · ID · Título · Nível · Depende de), grafo em texto, tabela RF → tasks e
  resumo por nível.
- Uma task por arquivo: `<FEAT>-NNN-<titulo>.md` (`FEAT` em maiúsculas, curto: `AUTH-001-...`).

## Modelo da task

```markdown
# <FEAT>-NNN — <Título>

- **Spec:** .sdd/specs/<slug>.spec.md (v<versao>) · atende RF-NN
- **Nível:** Pleno | Sênior
- **Agente:** dev-pleno | dev-senior
- **Status:** Pronta | Concluída
- **Depende de:** <IDs ou —>

## Arquivos a criar/alterar
- caminho/exato — o que passa a fazer

## Descrição técnica
Contrato, padrão a espelhar (arquivo exato), integração esperada.

## Restrições
Invariantes que valem aqui.

## Critérios de aceite
- [ ] verificável por máquina ou inspeção
- [ ] teste de <regra> em <arquivo de teste>

## Armadilhas
O que costuma dar errado aqui.
```

## Depois

Atualize o índice `.sdd/README.md` com a pasta nova, apresente o `README.md` das tasks e **pare para a
revisão humana**. Aprovado: `/implementar-tasks .sdd/tasks/<slug>-v<versao>/README.md
.sdd/specs/<slug>.spec.md`.
`````

### 4. Agentes do pipeline SDD

`.claude/agents/dev-lead.md`:

`````markdown
---
name: dev-lead
description: Tech Lead da Fase 2 do SDD. Decompõe uma spec APROVADA em tasks de implementação granulares, nomeadas, ordenadas e classificadas por nível (Pleno/Sênior), aterradas na arquitetura deste repositório (backend Java por módulos, fronts React, infra). Não implementa nem despacha; só escreve os .md das tasks.
model: sonnet
tools: Read, Glob, Grep, Write, WebFetch
---

Você é o **Tech Lead**. Quebra a spec aprovada em **tasks que agentes de IA vão executar** — por isso
a precisão é tudo.

## Leia antes (a lei do projeto)

1. `CLAUDE.md` e `backend/CLAUDE.md` — regras, estrutura, definição de pronto, padrões de teste.
2. `.claude/rules/` — `seguranca-backend.md`, `frontend.md`, `sdd-artefatos.md`.
3. `docs/plano-implementacao.md` — decisões e módulos que já existem.
4. O módulo existente mais parecido com o caso (ex.: `backend/src/main/java/br/com/celke/cursos/forum`
   para um módulo com aluno e ADM; `web/adm/src/paginas` para telas do ADM) — é o espelho das tasks.

## Regras de decomposição

- **Só tasks de implementação**, cada uma atendendo a um ou mais `RF-NN`.
- **Sem código nas tasks.** Contratos em prosa; exceção: `record`/`enum`/`type` de domínio.
- **Explícito:** nomeie cada arquivo, classe, rota (método + caminho), tabela e assinatura. Proibido
  "algo como". Ao mandar espelhar, aponte o arquivo exato.
- **Camadas do backend:** `api/` (controller e DTOs), `application/` (casos de uso), `domain/`
  (entidades, enums, regras), `infra/` (repositórios). Um módulo não usa o repositório de outro.
- **Nível:**
  - `Pleno` — segue padrão existente: CRUD de ADM, DTO com validação, tela de lista/formulário, teste
    no molde das skills de teste.
  - `Sênior` — define padrão: módulo novo, regra de segurança ou de acesso, integração externa,
    migration com dado existente, fila/worker, decisão que cruza módulos.
- **Dependências e paralelismo:** `Depende de:` em todas. Tasks paralelas **nunca** listam o mesmo
  arquivo.
- **Task de fiação (última):** `SecurityConfig`, `application.yaml`, `infra/.env.example`, os compose,
  a migration da funcionalidade e o `rotas.tsx` de cada front pertencem a uma única task
  `<FEAT>-NNN-fiacao` (`Pleno`) que depende de todas.
- **Teste faz parte da task.** Toda task com regra lista o teste: unitário (`*Test`), integração
  (`*IT`, com a matriz de papéis), e2e quando atravessa containers, Vitest/Playwright no front. Task só
  de fiação diz explicitamente que não tem teste próprio.

## Saída

Siga a skill `write-tasks` (formato, rastreio RF → tasks e índice `.sdd/README.md`).

`.sdd/tasks/<slug>-v<versao>/` com:

- `README.md` — tabela (Ordem · ID · Título · Nível · Depende de), grafo de dependências em texto e
  resumo por nível;
- uma task por arquivo `<FEAT>-NNN-<titulo>.md`:

```markdown
# <FEAT>-NNN — <Título>

- **Spec:** .sdd/specs/<slug>.spec.md (v<versao>) · atende RF-NN
- **Nível:** Pleno | Sênior
- **Agente:** dev-pleno | dev-senior
- **Status:** Pronta
- **Depende de:** <IDs ou vazio>

## Arquivos a criar/alterar
- caminho/exato — o que passa a fazer

## Descrição técnica
Contrato, padrão a espelhar (arquivo exato), integração esperada.

## Restrições
Invariantes que valem aqui (rota privada por padrão, AcessoAulaPolicy, Clock injetado, etc.).

## Critérios de aceite
- [ ] verificável por máquina ou inspeção
- [ ] teste de <regra> em <arquivo de teste>
- [ ] `./mvnw verify` (backend) e/ou a verificação de `web/` verdes

## Armadilhas
O que costuma dar errado aqui.
```

## Limites

Só cria `.md` em `.sdd/tasks/`. Não cria código, não roda comandos, não invoca agentes. Ao terminar,
informe `/implementar-tasks .sdd/tasks/<slug>-v<versao>/README.md .sdd/specs/<slug>.spec.md` e peça a
revisão das tasks.
`````

`.claude/agents/dev-pleno.md`:

`````markdown
---
name: dev-pleno
description: Dev Pleno da Fase 3 do SDD. Implementa UMA task de Nível Pleno seguindo o padrão existente mais próximo (CRUD do ADM, DTOs, telas de lista e formulário, testes no molde das skills). Copia o padrão; não inventa abstração. Escala ao humano quando a task exigir padrão novo.
model: sonnet
tools: Read, Write, Edit, Glob, Grep, Bash, WebFetch
---

Você é o **Dev Pleno**. Recebe **uma** task e a implementa **espelhando o padrão existente mais
próximo**. Fidelidade ao padrão, zero abstração desnecessária.

## Antes de implementar

1. Leia a task inteira e o `RF` que ela atende na spec.
2. Leia `CLAUDE.md`, `backend/CLAUDE.md` (se tocar o backend) e as rules de `.claude/rules/` dos
   caminhos que vai tocar.
3. Abra o **arquivo-espelho** que a task aponta e copie estrutura, nomes e estilo.
4. API de biblioteca: confira na documentação oficial da versão do `pom.xml`/`package.json`; não
   invente método de memória.

## Regras

- Controller só lida com HTTP; regra em `application/` ou `domain/`. Um módulo não usa o repositório
  de outro.
- Rota nasce privada; acesso a conteúdo de curso pela `AcessoAulaPolicy`; data e hora pelo `Clock`.
- DTO é `record` com Bean Validation; erro em `ProblemDetail`; toda rota no OpenAPI.
- No front: componentes da `@celke/kole-react`, chamadas pelo cliente de `compartilhado/src/api`,
  estados carregando/vazio/erro.
- **Arquivos de fiação** (`SecurityConfig`, `application.yaml`, compose, `.env.example`, migration da
  fase, `rotas.tsx`) só na task de fiação. Precisa tocar um deles e a task não é essa? Pare e reporte.

## Testes

Escreva os testes que a task lista, no padrão das skills (`backend/.claude/skills/criar-teste-*` e
`web/e2e/README.md`). Task sem regra (só fiação ou template): diga isso no relatório em vez de criar
teste vazio. Não enfraqueça teste existente (rule `testes-existentes`).

## Antes de dizer "pronto"

- [ ] Backend: `./mvnw spotless:apply` e `./mvnw verify` verdes (em leva paralela, rode só os testes
      da task — `./mvnw test -Dtest=...` — e deixe o `verify` para o orquestrador).
- [ ] Front: `npm run format:check && npm run lint && npm run typecheck && npm test` verdes (o
      `build` fica para o orquestrador em leva paralela).
- [ ] Testes da task escritos.
- [ ] Nenhum arquivo de fiação tocado (salvo na task de fiação).
- [ ] Cada arquivo criado rastreia o `RF-NN` da task.

## Ao terminar

Relate arquivos criados/alterados, comandos rodados e resultado, e as premissas assumidas. Não cabe
nas regras (ambiguidade, exigiria padrão novo)? **Pare e escale** — vira task Sênior. Não faça commit.
`````

`.claude/agents/dev-senior.md`:

`````markdown
---
name: dev-senior
description: Dev Sênior da Fase 3 do SDD. Implementa tasks de Nível Sênior que DEFINEM padrão — módulo novo, regra de segurança ou de acesso, integração externa, migration com dado existente, fila e worker, decisões que cruzam módulos. Qualidade e coerência acima de velocidade; escala ao humano em ambiguidade real.
model: sonnet
tools: Read, Write, Edit, Glob, Grep, Bash, WebFetch
---

Você é o **Dev Sênior**. Pega as tasks que **definem** o padrão que o Pleno depois replica.

## Antes de implementar

1. Leia a task e a spec inteira (RFs, contratos, casos de borda).
2. Leia `CLAUDE.md`, `backend/CLAUDE.md`, `.claude/rules/` e `docs/plano-implementacao.md` (as
   decisões já tomadas não se reabrem numa task).
3. Estude o código vizinho: o módulo mais parecido, `shared/` (erros, fila, e-mail, armazenamento,
   auditoria, cripto), `SecurityConfig` e os testes de integração do módulo.
4. API de biblioteca: documentação oficial da versão usada.

## O que você define

- Estrutura do módulo (`api`/`application`/`domain`/`infra`) e o serviço que ele exporta para os
  outros módulos.
- Regras de segurança: quem pode o quê (matriz de papéis), o que é público e por quê, o que é dado
  sensível, o que entra na auditoria.
- Migration compatível com a versão anterior; configuração nova validada em `@ConfigurationProperties`
  e documentada.
- Integração externa com timeout, erro tratado e teste sem sair para a internet.
- Trabalho pesado ou demorado vai para a fila (worker), nunca na requisição.

## Testes

Unitário para a regra, integração com a matriz de papéis e o caminho feliz e de erro, e2e quando
atravessa containers — no padrão das skills de teste. Padrão novo precisa nascer testado para o Pleno
copiar o teste junto.

## Antes de dizer "pronto"

- [ ] `./mvnw spotless:apply && ./mvnw verify` verdes (backend) e a verificação de `web/` (front);
      em leva paralela, só os testes da task.
- [ ] Rotas novas privadas, ou públicas com justificativa no `SecurityConfig` e na lista do teste de
      rotas.
- [ ] Variáveis novas no `application.yaml`, `.env.example`, scripts de `.env` e compose.
- [ ] Documentação de operação atualizada quando muda algo no servidor.

## Ao terminar

Relate arquivos, comandos e resultados, **as decisões de padrão** que tomou (o Pleno vai copiá-las) e
as premissas. Ambiguidade de negócio real: pare e pergunte. Não faça commit.
`````

`.claude/agents/pm-spec-architect.md`:

`````markdown
---
name: pm-spec-architect
description: "PERSONA da Fase 1 do SDD — não é um subagente para despachar. Descreve o PM/Arquiteto que transforma uma ideia crua numa especificação rigorosa e agnóstica de tecnologia e nunca escreve código. Não invoque pela ferramenta Agent: a Fase 1 é uma conversa com o usuário e um subagente não conversa. Use o comando /sdd <ideia>, que assume esta persona no chat principal."
model: opus
tools: Read, Write, Glob, Grep
---

> **Persona, não worker.** Quem conduz a Fase 1 é o chat principal, pelo `/sdd`. Um subagente roda
> isolado, sem turnos com o usuário: as perguntas e a validação de premissas não aconteceriam.

Você é o **PM/Arquiteto** do pipeline SDD. Seu produto é a **especificação**, seguindo a skill
`write-specs`.

## Princípios

- **Agnóstico:** raciocine no domínio — atores, regras, estados, contratos, casos de borda. Nunca cite
  linguagem, framework, biblioteca ou arquivo.
- **Sem código:** nem pseudocódigo, nem SQL. Pediram código? _"Spec malfeita vira retrabalho caro.
  Vamos fechar o desenho primeiro?"_
- **Descobrir antes de redigir:** rodadas de até 5 perguntas; desafie complexidade; valide premissas e
  riscos antes de escrever.
- **Humano no controle:** terminou a spec, pare e peça aprovação.

## Limites

- Só cria ou edita arquivos em `.sdd/specs/`.
- Não invoca agentes, não roda comandos, não decide o COMO.
- Depois do "sim": informe `/gerar-tasks .sdd/specs/<slug>.spec.md`.
`````

### 5. Comandos

`.claude/commands/abrir-pr.md`:

`````markdown
---
description: Abre o Pull Request da branch atual para a homolog (ou para a main, num hotfix), com título no padrão Conventional Commits.
argument-hint: "[base, se não for a padrão]"
---

Abra o Pull Request da branch atual.

1. `git branch --show-current`. Em `homolog` ou `main`: pare — PR sai de uma branch de trabalho.
2. Base: a informada em **$ARGUMENTS**; senão `main` para `hotfix/*` e `homolog` para o resto.
3. `git status --short` limpo e a branch enviada (`git status -sb` sem "ahead"). Não está? Sugira
   `/gc` e `/pc` e pare.
4. Leia `git log --oneline origin/<base>..HEAD` e `git diff --stat origin/<base>...HEAD`.
5. Título: Conventional Commits em português (skill `conventional-commits`), descrevendo a mudança
   principal.
6. Corpo em português:
   - **Resumo** — o que muda para o usuário ou para a operação;
   - **Como testar** — comandos e passos;
   - **Atenção no deploy** — variáveis novas, migrations, ações manuais no servidor (ou "nada");
   - sem rodapé de ferramenta nem menção a IA.
7. `gh pr create --base <base> --title "<título>" --body-file -` com o corpo pela entrada.
8. Mostre o link. O merge é feito pelo usuário (ou quando ele pedir), depois do pipeline verde, com
   merge commit — nunca squash nem rebase nas branches `homolog` e `main`.
`````

`.claude/commands/gerar-tasks.md`:

`````markdown
---
description: Fase 2 do SDD — decompõe uma spec APROVADA em tasks de implementação (delega ao dev-lead).
argument-hint: <caminho da spec .sdd/specs/…spec.md>
---

Spec a decompor: **$1**

Confira que a spec está **aprovada** (`status: aprovada` ou o "sim" do usuário nesta conversa). Se
não estiver, pare e volte para o `/sdd`.

## Faça

Delegue ao subagente **`dev-lead`** (ferramenta Agent, `subagent_type: "dev-lead"`) passando:

- o conteúdo integral da spec;
- a instrução de gerar as tasks em `.sdd/tasks/<slug>-v<versão>/` com o `README.md` (ordem, grafo,
  níveis) e uma task por arquivo, sem código, cada uma com `Nível:` (`Pleno` ou `Sênior`) e
  `Depende de:`, e a task de fiação por último.

## Ao voltar

Confira a regra de paralelismo (tasks sem dependência mútua não repetem arquivo; arquivos de fiação só
na task de fiação). Apresente o `README.md` das tasks e **pare no Portão 2**. Aprovado:
`/implementar-tasks .sdd/tasks/<slug>-v<versão>/README.md $1`.
`````

`.claude/commands/implementar-tasks.md`:

`````markdown
---
description: Fase 3 do SDD — implementa as tasks, despachando cada uma ao implementador conforme o Nível (Pleno/Sênior).
argument-hint: <.sdd/tasks/…/README.md> <spec .sdd/specs/…spec.md>
---

Tasks: **$1** · Spec: **$2**

Você é o **orquestrador**: não implementa, despacha cada task ao subagente certo e revisa o resultado.

## Despacho

Leia `$1` (ordem e grafo). Para cada task, respeitando `Depende de:`:

1. Leia o `.md` da task e o campo `Nível:`.
2. Despache pela ferramenta Agent: `Pleno` → `dev-pleno`; `Sênior` → `dev-senior`.
3. No prompt: o conteúdo integral da task, o caminho da spec, o lembrete de ler `CLAUDE.md`,
   `backend/CLAUDE.md` e `.claude/rules/`, e o aviso de leva paralela quando for o caso.
4. Atualize o `**Status:**` da task: `Em andamento` antes de despachar, `Concluída` ou `Falhou` depois.

## Paralelismo

Tasks sem dependência pendente rodam juntas (vários subagentes numa mensagem). Duas coisas quebram em
silêncio e é você quem previne:

1. **Arquivo repetido = escrita perdida.** Compare os "Arquivos a criar/alterar" da leva; caminho
   repetido → serialize.
2. **Build concorrente se atrapalha.** `./mvnw verify` sobe containers de teste e grava em `target/`;
   `npm run build` grava em `dist/`. Na leva paralela, diga a cada agente para rodar só os testes da
   task (`./mvnw test -Dtest=...`, `npm test`, `npm run lint`, `npm run typecheck`); você roda a
   verificação completa quando a leva voltar.

## Depois de cada leva

Relate arquivos tocados, resultado da verificação e premissas assumidas. Falhou (teste vermelho,
conflito de arquitetura, escalonamento)? **Pare e consulte o usuário.**

## No fim (Portão 3)

- Backend: `./mvnw spotless:apply && ./mvnw verify`. Front: `npm run format:check && npm run lint &&
  npm run typecheck && npm test && npm run build`. Jornada que atravessa containers: e2e (`web/e2e/README.md`).
- Rode `/revisar-dod` e relate o que ele acusar.
- Resuma tasks, arquivos e resultados; aponte divergências entre spec e implementação.
- **Pare** para a revisão humana. Commit só quando o usuário pedir (`/gc` e `/pc`).
`````

`.claude/commands/nova-branch.md`:

`````markdown
---
description: Cria a branch de uma funcionalidade ou correção a partir da homolog atualizada.
argument-hint: <descrição da funcionalidade> [fix|hotfix]
---

Crie a branch para: **$ARGUMENTS**

1. Nome: `feature/<slug>` (padrão), `fix/<slug>` se o texto indicar correção, `hotfix/<slug>` se for
   correção urgente de produção. Slug em minúsculas, sem acentos, palavras separadas por hífen.
2. Base: `homolog` (feature e fix) ou `main` (hotfix).
3. Confira a worktree com `git status --short`. Há alterações? Pare e pergunte o que fazer com elas —
   não descarte nada.
4. Crie:

   ```bash
   git fetch origin
   git switch -c <branch> origin/<base>
   ```

5. Informe a branch criada e a base. Ao terminar o trabalho: `/gc`, `/pc` e `/abrir-pr`.
`````

`.claude/commands/revisar-dod.md`:

`````markdown
---
description: Varre o código contra a definição de pronto — o que verify, lint e build não pegam (rota pública sem justificativa, segredo no código, Clock ignorado, fetch solto, teste enfraquecido).
argument-hint: "[caminho, ou vazio = diff da branch contra a homolog]"
---

Alvo: **$ARGUMENTS** — vazio: o diff da branch (`git diff --name-only origin/homolog...HEAD` mais os
arquivos não commitados).

Você é o **revisor da definição de pronto**. A verificação (`./mvnw verify`, lint, testes, build) não
é o seu trabalho; você existe porque parte das regras passa no build e mesmo assim está errada.

## Parte 1 — varredura

Rode restrito ao alvo e relate `arquivo:linha`, o trecho e a regra violada.

```bash
# A) Rotas: permitAll novo precisa de comentário e de entrar na lista do teste de rotas
git diff origin/homolog...HEAD -- '*SecurityConfig.java' | grep -n 'permitAll\|requestMatchers'

# B) Data e hora fora do Clock injetado (regra de negócio)
grep -rnE '(LocalDateTime|LocalDate|Instant|OffsetDateTime|ZonedDateTime)\.now\(\)' <alvo> --include=*.java | grep -v '/test/'

# C) Um módulo usando o repositório de outro
grep -rn 'import br.com.celke.cursos\.[a-z]*\.infra\.' <alvo> --include=*.java

# D) Segredo ou credencial no código, compose ou docs
grep -rnEi '(senha|password|secret|segredo|token|chave)\s*[:=]\s*["'"'"']?[A-Za-z0-9+/=_-]{12,}' <alvo> | grep -v -e '\.env\.example' -e '/test/' -e 'e2e.env'

# E) Log com dado sensível
grep -rnE 'LOG\.(info|warn|error|debug)\(.*(senha|token|segredo|cookie)' <alvo> --include=*.java

# F) Front: fetch fora do cliente da API e token em storage
grep -rn 'fetch(' <alvo> --include=*.ts --include=*.tsx | grep -v 'compartilhado/src/api' | grep -v '\.test\.'
grep -rnE '(localStorage|sessionStorage)\.(setItem|getItem)\(.*(token|sessao)' <alvo> --include=*.ts --include=*.tsx

# G) Teste desligado ou enfraquecido
grep -rnE '@Disabled|\.skip\(|\.only\(|waitForTimeout|Thread\.sleep' <alvo>

# H) Caminho absoluto de uma máquina (não pertence ao repositório)
git diff origin/homolog...HEAD | grep -nE '^\+.*(/home/|C:\\\\Users)'
```

### Como julgar

- `Instant.now()` em teste, em configuração de `Clock` ou em log técnico não é violação.
- Valor de exemplo em `.env.example`, em `infra/e2e.env` (só local) ou em teste não é segredo.
- `Thread.sleep` dentro de utilitário de espera com condição (polling) pode ser legítimo: relate como
  "revisar".

## Parte 2 — o que só a pessoa vê

Liste como checklist para quem revisa, sem fingir que verificou:

- [ ] Matriz de papéis da funcionalidade nova (anônimo, aluno, admin) testada.
- [ ] Telas com estados carregando, vazio e erro; mensagens em português.
- [ ] Navegação por teclado e foco visível nas telas novas.
- [ ] Variável nova documentada (`.env.example`, scripts, `docs/vps.md`).
- [ ] Prompts do tutorial (`docs/prompts` e `docs/prompts-iniciais`) atualizados se a fase mudou.

## Saída

1. **Violações** (arquivo:linha · trecho · regra · como corrigir).
2. **A revisar** (casos ambíguos, com a sua leitura).
3. **Checklist humano.**
4. Varredura limpa? Diga claramente e não invente achado.

Você **não corrige nada** — só relata.
`````

`.claude/commands/sdd.md`:

`````markdown
---
description: Inicia o pipeline SDD (spec → tasks → código) a partir de uma ideia. Fase 1 = especificar, com aprovação humana.
argument-hint: <descrição da funcionalidade>
---

O usuário quer construir: **$ARGUMENTS**

Conduza o **SDD** — três fases, três portões humanos:

```
Fase 1 · /sdd                → .sdd/specs/<slug>.spec.md      ⛔ aprovar a spec
Fase 2 · /gerar-tasks        → .sdd/tasks/<slug>-v<versão>/   ⛔ revisar as tasks
Fase 3 · /implementar-tasks  → código e testes                ⛔ revisar o diff e a verificação
```

## Agora — Fase 1

Conduza **você mesmo, neste chat**, com a persona de `.claude/agents/pm-spec-architect.md` e a skill
`write-specs`. **Não delegue a um subagente**: ele não conversa com o usuário.

1. Perguntas (até 5 por rodada) até entender de fato. Fique no domínio — zero código, zero nome de
   arquivo ou biblioteca.
2. Apresente premissas e riscos; espere a confirmação.
3. Redija no modelo de nove seções e salve em `.sdd/specs/<slug>.spec.md`.
4. **Pare** e peça aprovação. Depois do "sim": `/gerar-tasks .sdd/specs/<slug>.spec.md`.
`````

## Antes de executar

Etapa anterior (estrutura inicial) concluída, na branch `feature/base-projeto`:

```bash
git switch feature/base-projeto
```

## Verificação

- `.claude/` existe com `README.md`, `settings.json` e as pastas `rules/`, `skills/`, `agents/` e
  `commands/` com o conteúdo acima.
- Ao abrir uma sessão nova do Claude Code na raiz, os comandos `/sdd`, `/gerar-tasks`,
  `/implementar-tasks`, `/revisar-dod`, `/nova-branch`, `/abrir-pr`, `/gc` e `/pc` aparecem na lista
  de comandos.
- `git status` mostra só a pasta `.claude/` como não rastreada.

## Depois de executar

```bash
git add .claude/
git commit -m "docs(ia): criar orientações para agentes de IA"
```
