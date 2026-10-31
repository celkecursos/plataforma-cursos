# Tasks — Fórum de dúvidas (v1.0)

Spec: [`.sdd/specs/forum.spec.md`](../../specs/forum.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f07-01-forum.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [FORUM-001](FORUM-001-dominio-e-markdown.md) | Tópicos, respostas e Markdown seguro | Sênior | — |
| 2 | [FORUM-002](FORUM-002-aluno.md) | Fórum do aluno com imagens e limite | Pleno | FORUM-001 |
| 3 | [FORUM-003](FORUM-003-moderacao.md) | Moderação | Pleno | FORUM-001 |
| 4 | [FORUM-004](FORUM-004-fiacao.md) | Fiação do fórum | Pleno | FORUM-002, FORUM-003 |

## Grafo

```
FORUM-001 (início)
FORUM-001 → FORUM-002
FORUM-001 → FORUM-003
FORUM-002 + FORUM-003 → FORUM-004
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | FORUM-001 |
| RF-02 | FORUM-002 |
| RF-03 | FORUM-002 |
| RF-04 | FORUM-001 |
| RF-05 | FORUM-002 |
| RF-06 | FORUM-003 |
| RF-07 | FORUM-001 |
| RF-08 | FORUM-002, FORUM-004 |

## Resumo por nível

- Pleno: 3
- Sênior: 1
