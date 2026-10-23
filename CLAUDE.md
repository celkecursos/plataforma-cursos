# Guia para agentes de IA e desenvolvedores

Convenções gerais deste repositório. Cada projeto tem o próprio guia com as regras do seu stack:

| Pasta | Projeto | Guia |
|---|---|---|
| `backend/` | API e worker (Java 25, Spring Boot 4.1) | `backend/CLAUDE.md` |
| `web/` | Área do aluno, ADM e site público (React, Vite) | `.claude/rules/frontend.md` |
| `infra/` | Docker Compose, Caddy, scripts de deploy | `.claude/rules/seguranca-backend.md` |
| `docs/` | Plano de implementação, roteiro de capítulos e prompts do tutorial | `.claude/rules/prompts-do-tutorial.md` |

## Antes de começar

- Leia `README.md` e `docs/plano-implementacao.md` (decisões, fases e estrutura). O conteúdo de cada
  capítulo do tutorial está em `docs/capitulos.md`.

## Branches

- `main` = produção, `homolog` = homologação.
- Trabalhe sempre numa branch própria criada a partir de `homolog` (`feature/nome` ou `fix/nome`) e entregue por Pull Request para `homolog`. Correção urgente: `hotfix/nome` a partir da `main`.
- **Nunca** faça commit ou push diretamente em `homolog` ou `main`. A promoção `homolog → main` é feita por Pull Request.
- Commit, push e Pull Request só quando o desenvolvedor pedir (`/gc`, `/pc`, `/abrir-pr`). Mensagens no
  padrão Conventional Commits em português, **sem** coautoria nem menção a ferramenta de IA.

## Regras gerais

- Mensagens ao usuário, comentários e documentação em português.
- Nunca coloque segredos no código, no compose ou na documentação: só em `.env` (fora do git) e nos secrets do GitHub.
- Funcionalidade nova vem com os testes no mesmo PR, seguindo o guia do projeto.
- Mudou algo que um prompt do tutorial cria? Atualize o prompt em `docs/prompts/` e em
  `docs/prompts-iniciais/` no mesmo PR: os prompts recriam o projeto do zero.

## Orientações para agentes de IA (`.claude/`)

Regras, skills, agentes e comandos versionados — índice em `.claude/README.md`. Hierarquia: este
arquivo > `backend/CLAUDE.md` > `.claude/rules/` > skills > documentação oficial das bibliotecas.

- **Rules** (carregam sozinhas pelos caminhos tocados): `seguranca-backend`, `frontend`,
  `testes-existentes`, `prompts-do-tutorial`, `sdd-artefatos`.
- **Skills**: `conventional-commits`, `gc` e `pc` (commits), `write-specs` (especificação); de testes
  do backend em `backend/.claude/skills/`.
- **Comandos**: `/nova-branch`, `/gc`, `/pc`, `/abrir-pr`, `/revisar-dod` e o pipeline SDD.
- **Pipeline SDD** (três portões humanos): `/sdd <ideia>` → spec em `.sdd/specs/` ⛔ →
  `/gerar-tasks <spec>` → tasks em `.sdd/tasks/` ⛔ → `/implementar-tasks <readme> <spec>` → código ⛔.
  A Fase 1 roda no chat (é uma conversa); as Fases 2 e 3 delegam aos agentes `dev-lead`, `dev-pleno` e
  `dev-senior`.
