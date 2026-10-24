# Tasks — Cadastro e recuperação de senha (v1.0)

Spec: [`.sdd/specs/cadastro-e-recuperacao-de-senha.spec.md`](../../specs/cadastro-e-recuperacao-de-senha.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f01-06-cadastro-e-recuperacao.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [CONTA-001](CONTA-001-tokens-uso-unico.md) | Links de uso único | Sênior | — |
| 2 | [CONTA-002](CONTA-002-cadastro.md) | Cadastro e confirmação de e-mail | Pleno | CONTA-001 |
| 3 | [CONTA-003](CONTA-003-recuperacao.md) | Recuperação e redefinição de senha | Pleno | CONTA-001 |
| 4 | [CONTA-004](CONTA-004-fiacao.md) | Fiação do cadastro | Pleno | CONTA-002, CONTA-003 |

## Grafo

```
CONTA-001 (início)
CONTA-001 → CONTA-002
CONTA-001 → CONTA-003
CONTA-002 + CONTA-003 → CONTA-004
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | CONTA-002 |
| RF-02 | CONTA-002 |
| RF-03 | CONTA-002 |
| RF-04 | CONTA-002 |
| RF-05 | CONTA-003 |
| RF-06 | CONTA-003 |
| RF-07 | CONTA-001 |
| RF-08 | CONTA-001 |
| RF-09 | CONTA-002 |
| RF-10 | CONTA-004 |

## Resumo por nível

- Pleno: 3
- Sênior: 1
