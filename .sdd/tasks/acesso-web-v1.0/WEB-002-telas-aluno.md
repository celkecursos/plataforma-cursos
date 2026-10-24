# WEB-002 — Telas de conta do aluno

- **Spec:** .sdd/specs/acesso-web.spec.md (v1.0) · atende RF-01
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** WEB-001

## Arquivos a criar/alterar

- `web/aluno/src/paginas/EntrarPagina.tsx`
- `web/aluno/src/paginas/CadastroPagina.tsx`
- `web/aluno/src/paginas/ConfirmarEmailPagina.tsx`
- `web/aluno/src/paginas/RecuperarSenhaPagina.tsx`
- `web/aluno/src/paginas/RedefinirSenhaPagina.tsx`
- `web/compartilhado/src/ui/validacao.ts`

## Descrição técnica

Telas com a `TelaLogin` da `@celke/kole-react`, validação na tela e mensagens em português.

## Critérios de aceite

- [x] `web/e2e/aluno/entrar.spec.ts`, `cadastro.spec.ts`, `senha.spec.ts`, `sessao.spec.ts` verdes
