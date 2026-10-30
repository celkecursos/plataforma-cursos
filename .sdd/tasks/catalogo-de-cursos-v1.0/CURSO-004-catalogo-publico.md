# CURSO-004 — Catálogo público

- **Spec:** .sdd/specs/catalogo-de-cursos.spec.md (v1.0) · atende RF-07, RF-08, RF-09
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** CURSO-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/curso/api/CatalogoController.java` — `GET /api/cursos`, `/api/cursos/{slug}`, `/api/cursos/{slug}/capa`
- `backend/src/main/java/br/com/celke/cursos/curso/application/CatalogoService.java`
- `backend/src/main/java/br/com/celke/cursos/curso/application/ConteudoTurmaService.java`

## Descrição técnica

Só publicados; conteúdo ativo da turma ativa mais recente; capa por redirecionamento com cache de 5 minutos.

## Restrições

Rascunho e arquivado respondem 404.

## Critérios de aceite

- [x] `CatalogoServiceTest`, `ConteudoTurmaServiceTest` verdes
- [x] `CatalogoIT` verde
- [x] `CatalogoE2E` verde

## Armadilhas

Expor ids internos e status no catálogo público.
