# VIDEO-002 — Conversão no worker

- **Spec:** .sdd/specs/envio-de-video.spec.md (v1.0) · atende RF-04, RF-05, RF-06
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** VIDEO-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/video/application/TranscodificacaoManipulador.java`
- `backend/src/main/java/br/com/celke/cursos/video/infra/Ffmpeg.java`
- `backend/src/main/java/br/com/celke/cursos/video/infra/ComandosFfmpeg.java`
- `backend/src/main/java/br/com/celke/cursos/video/infra/AnaliseVideo.java`
- `backend/src/main/java/br/com/celke/cursos/video/domain/Variante.java`
- `backend/src/main/java/br/com/celke/cursos/video/domain/ResultadoTranscodificacao.java`

## Descrição técnica

`ffprobe` e ffmpeg por lista de argumentos com `nice -n 10`, numa passada: H.264 CRF 24, AAC 128k, segmentos de 6 s, 720p e 1080p sem aumentar a origem. Lease renovado a cada minuto.

## Restrições

Publica em `videos/<aula>/<job>/` e só então apaga original e versão anterior.

## Critérios de aceite

- [x] `ComandosFfmpegTest`, `AnaliseVideoTest`, `VarianteTest` verdes
- [x] `VideoEnvioIT` com ffmpeg real

## Armadilhas

Montar o comando como string abre injeção.
