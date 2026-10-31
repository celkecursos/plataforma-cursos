# Tasks — Arquivos de apoio (v1.0)

Spec: [`.sdd/specs/arquivos-de-apoio.spec.md`](../../specs/arquivos-de-apoio.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f06-01-arquivos-de-apoio.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [ARQ-001](ARQ-001-envio-adm.md) | Envio pelo administrador | Pleno | — |
| 2 | [ARQ-002](ARQ-002-download-aluno.md) | Lista e download do aluno | Pleno | ARQ-001 |
| 3 | [ARQ-003](ARQ-003-fiacao.md) | Fiação dos arquivos | Pleno | ARQ-001, ARQ-002 |

## Grafo

```
ARQ-001 (início)
ARQ-001 → ARQ-002
ARQ-001 + ARQ-002 → ARQ-003
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | ARQ-001 |
| RF-02 | ARQ-001 |
| RF-03 | ARQ-001 |
| RF-04 | ARQ-002 |
| RF-05 | ARQ-002 |
| RF-06 | ARQ-001, ARQ-003 |

## Resumo por nível

- Pleno: 3
