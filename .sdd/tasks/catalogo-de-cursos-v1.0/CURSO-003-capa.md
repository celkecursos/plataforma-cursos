# CURSO-003 — Capa do curso

- **Spec:** .sdd/specs/catalogo-de-cursos.spec.md (v1.0) · atende RF-06, RF-09
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** CURSO-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/curso/application/CapaCursoService.java`
- `backend/src/main/java/br/com/celke/cursos/shared/imagem/ProcessadorImagem.java`

## Descrição técnica

URL pré-assinada em `uploads/capas/<curso>/<uuid>`; a confirmação confere chave, tamanho e lado e regrava em JPEG até 1280 × 720 em `capas/<curso>/<uuid>.jpg`.

## Restrições

Imagens servidas por `media.celke.uno`.

## Critérios de aceite

- [x] `CapaCursoServiceTest`, `ProcessadorImagemTest` verdes
- [x] `CapaCursoIT` com o Garage real

## Armadilhas

Decodificar antes de conferir o lado deixa uma imagem gigante derrubar a API.
