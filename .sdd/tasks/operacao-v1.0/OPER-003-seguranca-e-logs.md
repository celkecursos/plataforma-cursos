# OPER-003 — Revisão de segurança, Trivy e logs

- **Spec:** .sdd/specs/operacao.spec.md (v1.0) · atende RF-04, RF-05, RF-06, RF-07
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** OPER-001

## Arquivos a criar/alterar

- `.github/workflows/pipeline.yml` — Trivy barra crítico com correção
- `docs/operacao.md` — ASVS, ZAP, disco e saída para R2/B2
- `infra/compose.prod.yml` — rotação `json-file` 5 × 10 MB

## Critérios de aceite

- [x] pipeline verde com o Trivy
