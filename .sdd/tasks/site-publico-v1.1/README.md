# Tasks — Site público (v1.1)

Spec: [`.sdd/specs/site-publico.spec.md`](../../specs/site-publico.spec.md) · versão 1.1 · prompt do tutorial `docs/prompts-iniciais/f21-01-site-react-ssr.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [SITE-004](SITE-004-ssr.md) | Renderização no servidor | Sênior | — |
| 2 | [SITE-005](SITE-005-layout.md) | Layout, busca e tema escuro | Pleno | SITE-004 |
| 3 | [SITE-006](SITE-006-fiacao.md) | Fiação do site no servidor | Pleno | SITE-004, SITE-005 |

## Grafo

```
SITE-004 (início)
SITE-004 → SITE-005
SITE-004 + SITE-005 → SITE-006
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-07 | SITE-004, SITE-006 |
| RF-08 | SITE-004 |
| RF-09 | SITE-004 |
| RF-10 | SITE-005 |
| RF-11 | SITE-005 |
| RF-12 | SITE-005 |

## Resumo por nível

- Pleno: 2
- Sênior: 1
