# Tasks — Segurança das contas (v1.0)

Spec: [`.sdd/specs/seguranca-de-contas.spec.md`](../../specs/seguranca-de-contas.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f19-01-seguranca.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [SEG-001](SEG-001-segundo-fator.md) | Segundo fator dos administradores | Sênior | — |
| 2 | [SEG-002](SEG-002-senhas-e-banco.md) | Política de senha e menor privilégio | Sênior | — |
| 3 | [SEG-003](SEG-003-usuarios-e-limites.md) | Gestão de usuários e limites por usuário | Pleno | SEG-001 |
| 4 | [SEG-004](SEG-004-fiacao.md) | Fiação da segurança | Pleno | SEG-002, SEG-003 |

## Grafo

```
SEG-001 (início)
SEG-002 (início)
SEG-001 → SEG-003
SEG-002 + SEG-003 → SEG-004
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | SEG-001 |
| RF-02 | SEG-001 |
| RF-03 | SEG-001 |
| RF-04 | SEG-001 |
| RF-05 | SEG-002 |
| RF-06 | SEG-002 |
| RF-07 | SEG-003 |
| RF-08 | SEG-003 |
| RF-09 | SEG-003 |
| RF-10 | SEG-003 |
| RF-11 | SEG-004 |
| RF-12 | SEG-004 |

## Resumo por nível

- Pleno: 2
- Sênior: 2
