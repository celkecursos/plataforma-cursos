# Tasks — Envio de vídeo (v1.0)

Spec: [`.sdd/specs/envio-de-video.spec.md`](../../specs/envio-de-video.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f04-01-upload-de-video.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [VIDEO-001](VIDEO-001-envio-em-partes.md) | Envio em partes e conclusão | Sênior | — |
| 2 | [VIDEO-002](VIDEO-002-transcodificacao.md) | Conversão no worker | Sênior | VIDEO-001 |
| 3 | [VIDEO-003](VIDEO-003-adm.md) | Status, reprocessar e remover | Pleno | VIDEO-001 |
| 4 | [VIDEO-004](VIDEO-004-fiacao.md) | Fiação do vídeo | Pleno | VIDEO-002, VIDEO-003 |

## Grafo

```
VIDEO-001 (início)
VIDEO-001 → VIDEO-002
VIDEO-001 → VIDEO-003
VIDEO-002 + VIDEO-003 → VIDEO-004
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | VIDEO-001 |
| RF-02 | VIDEO-001 |
| RF-03 | VIDEO-001 |
| RF-04 | VIDEO-002 |
| RF-05 | VIDEO-002 |
| RF-06 | VIDEO-002 |
| RF-07 | VIDEO-003 |
| RF-08 | VIDEO-003 |
| RF-09 | VIDEO-001, VIDEO-004 |

## Resumo por nível

- Pleno: 2
- Sênior: 2
