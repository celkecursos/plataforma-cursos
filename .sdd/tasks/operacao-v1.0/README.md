# Tasks — Operação (v1.0)

Spec: [`.sdd/specs/operacao.spec.md`](../../specs/operacao.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f10-01-operacao.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [OPER-001](OPER-001-backup.md) | Backup e restauração | Sênior | — |
| 2 | [OPER-002](OPER-002-alertas.md) | Alertas por e-mail | Pleno | — |
| 3 | [OPER-003](OPER-003-seguranca-e-logs.md) | Revisão de segurança, Trivy e logs | Pleno | OPER-001 |
| 4 | [OPER-004](OPER-004-fiacao.md) | Fiação da operação | Pleno | OPER-001, OPER-002, OPER-003 |

## Grafo

```
OPER-001 (início)
OPER-002 (início)
OPER-001 → OPER-003
OPER-001 + OPER-002 + OPER-003 → OPER-004
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | OPER-001 |
| RF-02 | OPER-001 |
| RF-03 | OPER-002, OPER-004 |
| RF-04 | OPER-003 |
| RF-05 | OPER-003 |
| RF-06 | OPER-003 |
| RF-07 | OPER-003 |

## Resumo por nível

- Pleno: 3
- Sênior: 1
