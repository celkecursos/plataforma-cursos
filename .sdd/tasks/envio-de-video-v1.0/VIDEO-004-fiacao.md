# VIDEO-004 — Fiação do vídeo

- **Spec:** .sdd/specs/envio-de-video.spec.md (v1.0) · atende RF-09
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** VIDEO-002, VIDEO-003

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V8__criar_videos.sql`
- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`
- `backend/Dockerfile` — target `worker` com ffmpeg
- `.github/workflows/pipeline.yml` — instala o ffmpeg

## Descrição técnica

Tabela `videos` com FK `RESTRICT` na aula; `stop_grace_period: 30s` no worker.

## Critérios de aceite

- [x] `./mvnw verify` verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.

## Armadilhas

Worker sem ffmpeg na imagem.
