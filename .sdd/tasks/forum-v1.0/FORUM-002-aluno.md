# FORUM-002 — Fórum do aluno com imagens e limite

- **Spec:** .sdd/specs/forum.spec.md (v1.0) · atende RF-02, RF-03, RF-05, RF-08
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** FORUM-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/forum/api/ForumAlunoController.java`
- `backend/src/main/java/br/com/celke/cursos/forum/application/ForumAlunoService.java`
- `backend/src/main/java/br/com/celke/cursos/forum/application/AnexoForumService.java`
- `backend/src/main/java/br/com/celke/cursos/forum/application/LimiteForum.java`
- `backend/src/main/java/br/com/celke/cursos/forum/application/ForumProperties.java`
- `backend/src/main/java/br/com/celke/cursos/forum/api/ForumRequests.java`
- `backend/src/main/java/br/com/celke/cursos/forum/api/ForumResponses.java`

## Descrição técnica

Imagens em `uploads/forum/<usuario>/<uuid>`, regravadas em `forum/anexos/`; limite de 20 por hora contado no banco.

## Restrições

Chave de imagem só do próprio usuário (IDOR).

## Critérios de aceite

- [x] `ForumAlunoServiceTest`, `AnexoForumServiceTest`, `LimiteForumTest` verdes
- [x] `ForumIT` verde

## Armadilhas

Contar o limite em memória perde a contagem a cada reinício.
