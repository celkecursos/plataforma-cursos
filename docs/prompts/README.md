# Prompts

Prompts para recriar a Plataforma de Cursos do zero com o Claude Code. Execute-os **na ordem da
tabela**, cada um numa sessão do Claude Code aberta na raiz do repositório. Cada prompt parte do
estado deixado pelos anteriores.

Ao final de cada prompt, confira a seção **Verificação** dele antes de seguir para o próximo.

| Ordem | Prompt | Fase do plano | Branch |
|---|---|---|---|
| 1 | [Estrutura inicial do repositório](f00-01-estrutura-inicial.md) | 0 | `main` |
| 2 | [Esqueleto do backend e padrão de testes](f01-01-esqueleto-backend-e-testes.md) | 1.1 | `feature/base-projeto` |
| 3 | [Ambiente local com Docker](f01-02-ambiente-local.md) | 1.2 | `feature/base-projeto` |
| 4 | [Padrões compartilhados](f01-03-padroes-compartilhados.md) | 1.3 | `feature/base-projeto` |
| 5 | [Usuários e login com JWT e refresh em cookie](f01-04-usuarios-e-login.md) | 1.4 | `feature/base-projeto` |
