# Tasks — Matrícula e acesso ao conteúdo (v1.0)

Spec: [`.sdd/specs/matricula-e-acesso.spec.md`](../../specs/matricula-e-acesso.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f03-01-matricula-e-acesso.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [MAT-001](MAT-001-dominio-e-politica.md) | Matrícula e política única de acesso | Sênior | — |
| 2 | [MAT-002](MAT-002-gestao-adm.md) | Matrículas pelo administrador | Pleno | MAT-001 |
| 3 | [MAT-003](MAT-003-aluno.md) | Meus cursos do aluno | Pleno | MAT-001 |
| 4 | [MAT-004](MAT-004-fiacao.md) | Fiação da matrícula | Pleno | MAT-002, MAT-003 |

## Grafo

```
MAT-001 (início)
MAT-001 → MAT-002
MAT-001 → MAT-003
MAT-002 + MAT-003 → MAT-004
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | MAT-002 |
| RF-02 | MAT-002, MAT-004 |
| RF-03 | MAT-001 |
| RF-04 | MAT-001 |
| RF-05 | MAT-001 |
| RF-06 | MAT-003 |
| RF-07 | MAT-003 |
| RF-08 | MAT-003 |
| RF-09 | MAT-002, MAT-004 |

## Resumo por nível

- Pleno: 3
- Sênior: 1
