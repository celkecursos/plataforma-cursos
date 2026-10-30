# Tasks — Privacidade (LGPD) (v1.0)

Spec: [`.sdd/specs/privacidade-lgpd.spec.md`](../../specs/privacidade-lgpd.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f20-01-lgpd.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [LGPD-001](LGPD-001-dados-pessoais.md) | Dados pessoais por módulo | Sênior | — |
| 2 | [LGPD-002](LGPD-002-exclusao.md) | Cópia dos dados e exclusão da conta | Sênior | LGPD-001 |
| 3 | [LGPD-003](LGPD-003-politica.md) | Modelo da política de privacidade | Pleno | — |

## Grafo

```
LGPD-001 (início)
LGPD-001 → LGPD-002
LGPD-003 (início)
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | LGPD-001 |
| RF-02 | LGPD-002 |
| RF-03 | LGPD-002 |
| RF-04 | LGPD-002 |
| RF-05 | LGPD-002 |
| RF-06 | LGPD-003 |

## Resumo por nível

- Pleno: 1
- Sênior: 2
