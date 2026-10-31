# Tasks — Acesso pelas telas (v1.0)

Spec: [`.sdd/specs/acesso-web.spec.md`](../../specs/acesso-web.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f11-01-base-dos-fronts.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [WEB-001](WEB-001-cliente-e-sessao.md) | Cliente HTTP e sessão | Sênior | — |
| 2 | [WEB-002](WEB-002-telas-aluno.md) | Telas de conta do aluno | Pleno | WEB-001 |
| 3 | [WEB-003](WEB-003-painel-base.md) | Entrada e moldura do painel | Pleno | WEB-001 |
| 4 | [WEB-004](WEB-004-fiacao.md) | Fiação dos fronts | Pleno | WEB-002, WEB-003 |

## Grafo

```
WEB-001 (início)
WEB-001 → WEB-002
WEB-001 → WEB-003
WEB-002 + WEB-003 → WEB-004
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | WEB-002 |
| RF-02 | WEB-003 |
| RF-03 | WEB-001 |
| RF-04 | WEB-001 |
| RF-05 | WEB-001 |
| RF-06 | WEB-004 |
| RF-07 | WEB-004 |

## Resumo por nível

- Pleno: 3
- Sênior: 1
