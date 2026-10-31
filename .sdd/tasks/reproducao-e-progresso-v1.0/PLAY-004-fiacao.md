# PLAY-004 — Fiação da reprodução

- **Spec:** .sdd/specs/reproducao-e-progresso.spec.md (v1.0) · atende RF-01
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** PLAY-001, PLAY-002, PLAY-003

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V9__criar_progresso_aulas.sql`
- `backend/src/main/resources/db/migration/V10__criar_acessos_video.sql`
- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`
- `backend/src/main/resources/application.yaml`

## Descrição técnica

Rotas de playlist autenticadas; `app.video.retencao-log-acesso: 180d`.

## Critérios de aceite

- [x] `./mvnw verify` verde
- [x] `EntregaVideoE2E` verde
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.

## Armadilhas

Margem curta demais corta o vídeo no meio.
