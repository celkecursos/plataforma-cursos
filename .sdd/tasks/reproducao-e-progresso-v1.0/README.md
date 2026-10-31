# Tasks — Reprodução de vídeo e progresso (v1.0)

Spec: [`.sdd/specs/reproducao-e-progresso.spec.md`](../../specs/reproducao-e-progresso.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f05-01-playlist-e-progresso.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [PLAY-001](PLAY-001-playlist.md) | Listas de reprodução assinadas | Sênior | — |
| 2 | [PLAY-002](PLAY-002-progresso.md) | Progresso da aula e da turma | Pleno | — |
| 3 | [PLAY-003](PLAY-003-log-acesso.md) | Registro de acesso ao vídeo | Pleno | PLAY-001 |
| 4 | [PLAY-004](PLAY-004-fiacao.md) | Fiação da reprodução | Pleno | PLAY-001, PLAY-002, PLAY-003 |

## Grafo

```
PLAY-001 (início)
PLAY-002 (início)
PLAY-001 → PLAY-003
PLAY-001 + PLAY-002 + PLAY-003 → PLAY-004
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | PLAY-001, PLAY-004 |
| RF-02 | PLAY-001 |
| RF-03 | PLAY-001 |
| RF-04 | PLAY-002 |
| RF-05 | PLAY-002 |
| RF-06 | PLAY-003 |

## Resumo por nível

- Pleno: 3
- Sênior: 1
