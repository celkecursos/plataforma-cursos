# PLAY-003 — Registro de acesso ao vídeo

- **Spec:** .sdd/specs/reproducao-e-progresso.spec.md (v1.0) · atende RF-06
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** PLAY-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/video/application/LogAcessoVideoService.java`
- `backend/src/main/java/br/com/celke/cursos/video/application/LimpezaLogAcessoVideo.java`
- `backend/src/main/java/br/com/celke/cursos/video/domain/AcessoVideo.java`
- `backend/src/main/java/br/com/celke/cursos/video/infra/AcessoVideoRepository.java`

## Descrição técnica

Cada abertura da master grava usuário, aula, IP e navegador; o worker apaga após 180 dias.

## Critérios de aceite

- [x] `LogAcessoVideoServiceTest` verde

## Armadilhas

Registrar cada variante em vez da abertura.
