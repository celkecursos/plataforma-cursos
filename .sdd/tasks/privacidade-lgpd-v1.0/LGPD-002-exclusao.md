# LGPD-002 — Cópia dos dados e exclusão da conta

- **Spec:** .sdd/specs/privacidade-lgpd.spec.md (v1.0) · atende RF-02, RF-03, RF-04, RF-05
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** LGPD-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/perfil/api/PrivacidadeController.java`
- `backend/src/main/java/br/com/celke/cursos/perfil/application/PrivacidadeService.java`
- `backend/src/main/java/br/com/celke/cursos/auth/application/LimpezaAuditoriaLogin.java`
- `backend/src/main/resources/templates/email/conta-excluida.html`
- `web/aluno/src/paginas/perfil/Privacidade.tsx`

## Descrição técnica

Senha + confirmação; administrador não se exclui por aqui.

## Critérios de aceite

- [x] `web/e2e/aluno/privacidade.spec.ts` verde

## Armadilhas

Apagar o que tem prazo legal.
