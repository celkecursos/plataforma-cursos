# Tasks — Painel administrativo (v1.0)

Spec: [`.sdd/specs/painel-administrativo.spec.md`](../../specs/painel-administrativo.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f12-01-telas-do-adm.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [ADM-001](ADM-001-conteudo.md) | Cursos, turmas, módulos e aulas | Pleno | — |
| 2 | [ADM-002](ADM-002-video-e-arquivos.md) | Vídeo, capa e arquivos | Sênior | — |
| 3 | [ADM-003](ADM-003-turma.md) | Matrículas, fórum, certificado e termos | Pleno | — |
| 4 | [ADM-004](ADM-004-fiacao.md) | Fiação do painel | Pleno | ADM-001, ADM-002, ADM-003 |

## Grafo

```
ADM-001 (início)
ADM-002 (início)
ADM-003 (início)
ADM-001 + ADM-002 + ADM-003 → ADM-004
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | ADM-001, ADM-004 |
| RF-02 | ADM-001 |
| RF-03 | ADM-002 |
| RF-04 | ADM-002 |
| RF-05 | ADM-003 |
| RF-06 | ADM-003 |
| RF-07 | ADM-003 |
| RF-08 | ADM-001 |

## Resumo por nível

- Pleno: 3
- Sênior: 1
