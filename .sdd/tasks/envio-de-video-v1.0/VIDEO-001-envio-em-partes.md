# VIDEO-001 — Envio em partes e conclusão

- **Spec:** .sdd/specs/envio-de-video.spec.md (v1.0) · atende RF-01, RF-02, RF-03, RF-09
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/video/domain/Video.java`
- `backend/src/main/java/br/com/celke/cursos/video/domain/StatusVideo.java`
- `backend/src/main/java/br/com/celke/cursos/video/domain/FormatoVideo.java`
- `backend/src/main/java/br/com/celke/cursos/video/domain/DivisaoPartes.java`
- `backend/src/main/java/br/com/celke/cursos/video/application/EnvioVideoService.java`
- `backend/src/main/java/br/com/celke/cursos/video/infra/VideoRepository.java`

## Descrição técnica

Partes de 16 MiB em `uploads/videos/<aula>/<uuid>`; conclusão confere tamanho real e assinatura (`ftyp` ou EBML) e enfileira na mesma transação.

## Restrições

Partes enviadas direto a `media.celke.uno`.

## Critérios de aceite

- [x] `FormatoVideoTest`, `DivisaoPartesTest`, `EnvioVideoServiceTest` verdes
- [x] `VideoEnvioIT` verde

## Armadilhas

Confiar na extensão em vez do conteúdo.
