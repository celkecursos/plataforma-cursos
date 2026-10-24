# ENTREGA-003 — Deploy automático com volta

- **Spec:** .sdd/specs/ambientes-e-entrega.spec.md (v1.0) · atende RF-03, RF-04, RF-05
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** ENTREGA-002

## Arquivos a criar/alterar

- `infra/scripts/deploy.sh` — `<producao|homolog> <sha>`, backup, `up --wait`, rollback, `flock`
- `.github/workflows/deploy.yml` — um deploy por ambiente, nunca cancelado

## Descrição técnica

Push em `homolog` implanta a homologação; push em `main` implanta a produção com `DEPLOY_ATIVO=true`. A chave do deploy fica presa ao script no `authorized_keys`.

## Restrições

Prompt: `f01-09-deploy-automatico.md`. Secrets `VPS_*` só no GitHub.

## Critérios de aceite

- [x] `shellcheck` e `actionlint` sem avisos
- [x] simulação local: versão boa sobe; versão quebrada volta

## Armadilhas

Deploy cancelado no meio deixa o ambiente entre duas versões.
