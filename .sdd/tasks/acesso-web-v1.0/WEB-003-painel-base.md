# WEB-003 — Entrada e moldura do painel

- **Spec:** .sdd/specs/acesso-web.spec.md (v1.0) · atende RF-02
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** WEB-001

## Arquivos a criar/alterar

- `web/adm/src/paginas/EntrarPagina.tsx`
- `web/adm/src/paginas/PainelPagina.tsx`

## Descrição técnica

Moldura `Estrutura` da `@celke/kole-react`.

## Restrições

Aluno barrado no painel.

## Critérios de aceite

- [x] `web/e2e/adm/acesso.spec.ts` verde
