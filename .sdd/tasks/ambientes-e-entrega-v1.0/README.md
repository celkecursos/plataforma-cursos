# Tasks — Ambientes e entrega (v1.0)

Spec: [`.sdd/specs/ambientes-e-entrega.spec.md`](../../specs/ambientes-e-entrega.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f01-07-e2e-e-pipeline.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [ENTREGA-001](ENTREGA-001-e2e-e-pipeline.md) | Stack de e2e e pipeline | Sênior | — |
| 2 | [ENTREGA-002](ENTREGA-002-vps.md) | Preparação da VPS e dos dois ambientes | Sênior | ENTREGA-001 |
| 3 | [ENTREGA-003](ENTREGA-003-deploy.md) | Deploy automático com volta | Sênior | ENTREGA-002 |

## Grafo

```
ENTREGA-001 (início)
ENTREGA-001 → ENTREGA-002
ENTREGA-002 → ENTREGA-003
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | ENTREGA-001 |
| RF-02 | ENTREGA-001 |
| RF-03 | ENTREGA-003 |
| RF-04 | ENTREGA-003 |
| RF-05 | ENTREGA-003 |
| RF-06 | ENTREGA-002 |
| RF-07 | ENTREGA-002 |
| RF-08 | ENTREGA-002 |
| RF-09 | ENTREGA-002 |

## Resumo por nível

- Sênior: 3
