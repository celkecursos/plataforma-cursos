# Tasks — Certificado (v1.0)

Spec: [`.sdd/specs/certificado.spec.md`](../../specs/certificado.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f08-01-certificado.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [CERT-001](CERT-001-dominio.md) | Regra de conclusão e código | Sênior | — |
| 2 | [CERT-002](CERT-002-emissao.md) | Situação, emissão e PDF | Sênior | CERT-001 |
| 3 | [CERT-003](CERT-003-fiacao.md) | Fiação do certificado | Pleno | CERT-002 |

## Grafo

```
CERT-001 (início)
CERT-001 → CERT-002
CERT-002 → CERT-003
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | CERT-001, CERT-003 |
| RF-02 | CERT-002 |
| RF-03 | CERT-001 |
| RF-04 | CERT-002 |
| RF-05 | CERT-002 |
| RF-06 | CERT-001 |
| RF-07 | CERT-002 |

## Resumo por nível

- Pleno: 1
- Sênior: 2
