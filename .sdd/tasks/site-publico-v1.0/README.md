# Tasks — Site público (v1.0)

Spec: [`.sdd/specs/site-publico.spec.md`](../../specs/site-publico.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f14-01-site-publico.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [SITE-001](SITE-001-paginas-e-seo.md) | Páginas e SEO | Pleno | — |
| 2 | [SITE-002](SITE-002-geracao.md) | Geração periódica com troca atômica | Sênior | SITE-001 |
| 3 | [SITE-003](SITE-003-fiacao.md) | Fiação do site | Pleno | SITE-002 |

## Grafo

```
SITE-001 (início)
SITE-001 → SITE-002
SITE-002 → SITE-003
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | SITE-001 |
| RF-02 | SITE-002 |
| RF-03 | SITE-001 |
| RF-04 | SITE-001 |
| RF-05 | SITE-003 |
| RF-06 | SITE-001 |

## Resumo por nível

- Pleno: 2
- Sênior: 1
