# Tasks — Pagamentos (v1.0)

Spec: [`.sdd/specs/pagamentos.spec.md`](../../specs/pagamentos.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f16-01-pagamentos-hotmart.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [PAG-001](PAG-001-nucleo.md) | Porta de meio de pagamento e pedido | Sênior | — |
| 2 | [PAG-002](PAG-002-configuracao.md) | Configuração pelo painel e ofertas | Sênior | PAG-001 |
| 3 | [PAG-003](PAG-003-webhook.md) | Notificações e processamento | Sênior | PAG-001 |
| 4 | [PAG-004](PAG-004-checkout-e-painel.md) | Checkout, reconciliação e painel | Pleno | PAG-002, PAG-003 |
| 5 | [PAG-005](PAG-005-fiacao.md) | Fiação dos pagamentos | Pleno | PAG-004 |

## Grafo

```
PAG-001 (início)
PAG-001 → PAG-002
PAG-001 → PAG-003
PAG-002 + PAG-003 → PAG-004
PAG-004 → PAG-005
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | PAG-001 |
| RF-02 | PAG-002 |
| RF-03 | PAG-002 |
| RF-04 | PAG-004 |
| RF-05 | PAG-003, PAG-005 |
| RF-06 | PAG-001, PAG-003 |
| RF-07 | PAG-003 |
| RF-08 | PAG-003 |
| RF-09 | PAG-003 |
| RF-10 | PAG-004 |
| RF-11 | PAG-004 |

## Resumo por nível

- Pleno: 2
- Sênior: 3
