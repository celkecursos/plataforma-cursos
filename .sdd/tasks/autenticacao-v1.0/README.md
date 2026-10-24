# Tasks — Autenticação (v1.0)

Spec: [`.sdd/specs/autenticacao.spec.md`](../../specs/autenticacao.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f01-04-usuarios-e-login.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [AUTH-001](AUTH-001-usuario-e-senha.md) | Usuário, papéis e hash de senha | Sênior | — |
| 2 | [AUTH-002](AUTH-002-tokens-e-sessao.md) | Credencial de acesso e renovação com troca | Sênior | AUTH-001 |
| 3 | [AUTH-003](AUTH-003-rotas-de-sessao.md) | Rotas de login, renovação, saída e quem sou eu | Pleno | AUTH-002 |
| 4 | [AUTH-004](AUTH-004-criar-admin.md) | Comando de terminal para criar administrador | Pleno | AUTH-001 |
| 5 | [AUTH-005](AUTH-005-fiacao.md) | Fiação da autenticação | Pleno | AUTH-003, AUTH-004 |

## Grafo

```
AUTH-001 (início)
AUTH-001 → AUTH-002
AUTH-002 → AUTH-003
AUTH-001 → AUTH-004
AUTH-003 + AUTH-004 → AUTH-005
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | AUTH-002, AUTH-003, AUTH-005 |
| RF-02 | AUTH-003 |
| RF-03 | AUTH-002, AUTH-003, AUTH-005 |
| RF-04 | AUTH-002 |
| RF-05 | AUTH-002, AUTH-003 |
| RF-06 | AUTH-003 |
| RF-07 | AUTH-001 |
| RF-08 | AUTH-002, AUTH-005 |
| RF-09 | AUTH-004 |
| RF-10 | AUTH-001 |

## Resumo por nível

- Pleno: 3
- Sênior: 2
