# Tasks — Catálogo de cursos (v1.0)

Spec: [`.sdd/specs/catalogo-de-cursos.spec.md`](../../specs/catalogo-de-cursos.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f02-01-cursos-e-catalogo.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [CURSO-001](CURSO-001-dominio.md) | Domínio de cursos, turmas, módulos e aulas | Sênior | — |
| 2 | [CURSO-002](CURSO-002-gestao-adm.md) | Gestão pelo administrador | Pleno | CURSO-001 |
| 3 | [CURSO-003](CURSO-003-capa.md) | Capa do curso | Sênior | CURSO-001 |
| 4 | [CURSO-004](CURSO-004-catalogo-publico.md) | Catálogo público | Pleno | CURSO-001 |
| 5 | [CURSO-005](CURSO-005-fiacao.md) | Fiação do catálogo | Pleno | CURSO-002, CURSO-003, CURSO-004 |

## Grafo

```
CURSO-001 (início)
CURSO-001 → CURSO-002
CURSO-001 → CURSO-003
CURSO-001 → CURSO-004
CURSO-002 + CURSO-003 + CURSO-004 → CURSO-005
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | CURSO-001, CURSO-002 |
| RF-02 | CURSO-001, CURSO-002 |
| RF-03 | CURSO-001, CURSO-002 |
| RF-04 | CURSO-001, CURSO-002 |
| RF-05 | CURSO-002 |
| RF-06 | CURSO-003 |
| RF-07 | CURSO-004, CURSO-005 |
| RF-08 | CURSO-004 |
| RF-09 | CURSO-003, CURSO-004 |

## Resumo por nível

- Pleno: 3
- Sênior: 2
