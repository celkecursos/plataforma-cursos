# Tasks — Perfil e termos (v1.0)

Spec: [`.sdd/specs/perfil-e-termos.spec.md`](../../specs/perfil-e-termos.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f09-01-perfil-e-termos.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [PERFIL-001](PERFIL-001-perfil.md) | Perfil, senha e foto | Pleno | — |
| 2 | [PERFIL-002](PERFIL-002-termos.md) | Termos versionados e aceite | Sênior | — |
| 3 | [PERFIL-003](PERFIL-003-fiacao.md) | Fiação do perfil e dos termos | Pleno | PERFIL-001, PERFIL-002 |

## Grafo

```
PERFIL-001 (início)
PERFIL-002 (início)
PERFIL-001 + PERFIL-002 → PERFIL-003
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | PERFIL-001 |
| RF-02 | PERFIL-001 |
| RF-03 | PERFIL-001 |
| RF-04 | PERFIL-002 |
| RF-05 | PERFIL-002, PERFIL-003 |
| RF-06 | PERFIL-002 |
| RF-07 | PERFIL-002 |

## Resumo por nível

- Pleno: 2
- Sênior: 1
