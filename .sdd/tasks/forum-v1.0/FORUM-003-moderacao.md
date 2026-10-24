# FORUM-003 — Moderação

- **Spec:** .sdd/specs/forum.spec.md (v1.0) · atende RF-06
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** FORUM-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/forum/api/ForumAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/forum/application/ForumAdmService.java`

## Descrição técnica

Ocultar, mostrar, fixar e responder como instrutor (marca o tópico como respondido).

## Critérios de aceite

- [x] `ForumAdmServiceTest` verde
- [x] `ForumAcessoIT` (matriz de papéis) verde
- [x] `ForumE2E` verde
