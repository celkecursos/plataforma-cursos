# SITE-002 — Geração periódica com troca atômica

- **Spec:** .sdd/specs/site-publico.spec.md (v1.0) · atende RF-02
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** SITE-001

## Arquivos a criar/alterar

- `web/site/` — gerador a partir da API do ambiente
- `infra/compose.prod.yml` — serviço `site`

## Descrição técnica

Gera ao subir e a cada `SITE_INTERVALO` (5 min), trocando a versão inteira.

## Critérios de aceite

- [x] site gerado na stack de e2e

## Armadilhas

Trocar arquivo por arquivo serve páginas misturadas.
