# Tasks — Proteção de acesso (v1.0)

Spec: [`.sdd/specs/protecao-de-acesso.spec.md`](../../specs/protecao-de-acesso.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f01-05-protecoes.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [PROT-001](PROT-001-limite-e-bloqueio.md) | Limite por IP e bloqueio por falhas | Sênior | — |
| 2 | [PROT-002](PROT-002-origem-e-auditoria.md) | Conferência de origem e auditoria de login | Sênior | — |
| 3 | [PROT-003](PROT-003-erros-e-papeis.md) | Erros padronizados e matriz de papéis | Pleno | — |
| 4 | [PROT-004](PROT-004-fiacao.md) | Fiação das proteções | Pleno | PROT-001, PROT-002, PROT-003 |

## Grafo

```
PROT-001 (início)
PROT-002 (início)
PROT-003 (início)
PROT-001 + PROT-002 + PROT-003 → PROT-004
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | PROT-003, PROT-004 |
| RF-02 | PROT-001, PROT-004 |
| RF-03 | PROT-001 |
| RF-04 | PROT-001 |
| RF-05 | PROT-002, PROT-004 |
| RF-06 | PROT-002 |
| RF-07 | PROT-002, PROT-004 |
| RF-08 | PROT-003 |

## Resumo por nível

- Pleno: 2
- Sênior: 2
