# PLAY-001 — Listas de reprodução assinadas

- **Spec:** .sdd/specs/reproducao-e-progresso.spec.md (v1.0) · atende RF-01, RF-02, RF-03
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/video/api/AulaVideoController.java` — `GET /api/aulas/{id}/video.m3u8` e `/video/{variante}.m3u8`
- `backend/src/main/java/br/com/celke/cursos/video/application/PlaylistService.java`
- `backend/src/main/java/br/com/celke/cursos/video/domain/ReescritaPlaylist.java`
- `backend/src/main/java/br/com/celke/cursos/video/application/VideoProperties.java` — `app.video.margem-playlist`

## Descrição técnica

A master aponta para as variantes da API; cada variante traz trechos pré-assinados válidos pela duração + 2 h; `Cache-Control: no-store`.

## Restrições

Passa pela `AcessoAulaService`.

## Critérios de aceite

- [x] `ReescritaPlaylistTest`, `PlaylistServiceTest` verdes
- [x] `PlaylistIT` e `VideoAcessoIT` verdes

## Armadilhas

Aceitar qualquer nome de trecho permite ler outros objetos do bucket.
