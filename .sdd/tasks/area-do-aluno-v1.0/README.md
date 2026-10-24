# Tasks — Área do aluno (v1.0)

Spec: [`.sdd/specs/area-do-aluno.spec.md`](../../specs/area-do-aluno.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f13-01-area-do-aluno.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [ALUNO-001](ALUNO-001-cursos-e-turma.md) | Meus cursos e turma | Pleno | — |
| 2 | [ALUNO-002](ALUNO-002-aula-e-player.md) | Aula e player | Sênior | — |
| 3 | [ALUNO-003](ALUNO-003-certificado-e-perfil.md) | Certificados e perfil | Pleno | — |
| 4 | [ALUNO-004](ALUNO-004-fiacao.md) | Fiação da área do aluno | Pleno | ALUNO-001, ALUNO-002, ALUNO-003 |

## Grafo

```
ALUNO-001 (início)
ALUNO-002 (início)
ALUNO-003 (início)
ALUNO-001 + ALUNO-002 + ALUNO-003 → ALUNO-004
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | ALUNO-001 |
| RF-02 | ALUNO-001 |
| RF-03 | ALUNO-002 |
| RF-04 | ALUNO-002 |
| RF-05 | ALUNO-002 |
| RF-06 | ALUNO-003 |
| RF-07 | ALUNO-003 |
| RF-08 | ALUNO-001, ALUNO-004 |

## Resumo por nível

- Pleno: 3
- Sênior: 1
