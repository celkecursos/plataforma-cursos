# Guia para agentes de IA e desenvolvedores

Convenções gerais deste repositório. Cada projeto tem o próprio guia com as regras do seu stack:

| Pasta | Projeto | Guia |
|---|---|---|
| `backend/` | API e worker (Java 25, Spring Boot 4.1) | `backend/CLAUDE.md` |
| `infra/` | Docker Compose, Caddy, scripts de deploy | — |
| `docs/` | Plano de implementação, roteiro de capítulos e prompts do tutorial | — |

## Antes de começar

- Leia `README.md` e `docs/plano-implementacao.md` (decisões, fases e estrutura). O conteúdo de cada
  capítulo do tutorial está em `docs/capitulos.md`.

## Branches

- `main` = produção, `homolog` = homologação.
- Trabalhe sempre numa branch própria criada a partir de `homolog` (`feature/nome` ou `fix/nome`) e entregue por Pull Request para `homolog`. Correção urgente: `hotfix/nome` a partir da `main`.
- **Nunca** faça commit ou push diretamente em `homolog` ou `main`. A promoção `homolog → main` é feita por Pull Request.
- Não faça commit: deixe as alterações sem stage para o desenvolvedor revisar e commitar.

## Regras gerais

- Mensagens ao usuário, comentários e documentação em português.
- Nunca coloque segredos no código, no compose ou na documentação: só em `.env` (fora do git) e nos secrets do GitHub.
- Funcionalidade nova vem com os testes no mesmo PR, seguindo o guia do projeto.
