# OPER-001 — Backup e restauração

- **Spec:** .sdd/specs/operacao.spec.md (v1.0) · atende RF-01, RF-02
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `infra/scripts/backup.sh` — restic, dump por stdin, retenção 7/4/6
- `infra/scripts/restaurar.sh`
- `docs/operacao.md`

## Descrição técnica

Restauração testada na stack de e2e: banco e objetos do bucket de volta.

## Restrições

Destino e senha do restic só no servidor.

## Critérios de aceite

- [x] `shellcheck` sem avisos
- [x] restauração testada

## Armadilhas

Copiar os arquivos do Garage sem o snapshot dos metadados.
