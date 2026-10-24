# SEG-003 — Gestão de usuários e limites por usuário

- **Spec:** .sdd/specs/seguranca-de-contas.spec.md (v1.0) · atende RF-07, RF-08, RF-09, RF-10
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** SEG-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/auth/api/UsuariosAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/auth/application/UsuariosAdmService.java`
- `backend/src/main/java/br/com/celke/cursos/shared/security/LimitePorUsuarioFilter.java`
- `web/adm/src/paginas/usuarios/UsuariosPagina.tsx`
- `web/compartilhado/src/ui/useTokenDaUrl.ts`

## Critérios de aceite

- [x] `UsuariosAdmIT`, `LimitePorUsuarioFilterTest` verdes
- [x] `web/e2e/adm/usuarios.spec.ts` verde
