# VIDEO-003 — Status, reprocessar e remover

- **Spec:** .sdd/specs/envio-de-video.spec.md (v1.0) · atende RF-07, RF-08
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** VIDEO-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/video/api/VideoAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/video/api/VideoRequests.java`
- `backend/src/main/java/br/com/celke/cursos/video/api/VideoResponses.java`
- `backend/src/main/java/br/com/celke/cursos/video/application/VideoAdmService.java`

## Descrição técnica

Status com tamanhos, redução, variantes, segundos por minuto e miniatura.

## Restrições

Reprocessar só com erro e com original.

## Critérios de aceite

- [x] `VideoAdmServiceTest` verde
- [x] `VideoE2E` verde

## Armadilhas

Remover durante o processamento.
