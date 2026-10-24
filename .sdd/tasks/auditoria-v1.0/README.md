# Tasks — Auditoria (v1.0)

Spec: [`.sdd/specs/auditoria.spec.md`](../../specs/auditoria.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f17-01-auditoria-e-logs.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [AUD-001](AUD-001-trilha.md) | Trilha pelos eventos do Hibernate | Sênior | — |
| 2 | [AUD-002](AUD-002-consulta-e-retencao.md) | Consulta no painel e retenção | Pleno | AUD-001 |
| 3 | [AUD-003](AUD-003-fiacao.md) | Fiação da auditoria e dos logs | Pleno | AUD-001, AUD-002 |

## Grafo

```
AUD-001 (início)
AUD-001 → AUD-002
AUD-001 + AUD-002 → AUD-003
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | AUD-001 |
| RF-02 | AUD-001 |
| RF-03 | AUD-001, AUD-003 |
| RF-04 | AUD-002 |
| RF-05 | AUD-002 |
| RF-06 | AUD-003 |

## Resumo por nível

- Pleno: 2
- Sênior: 1
