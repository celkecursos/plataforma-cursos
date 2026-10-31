# SEG-004 — Fiação da segurança

- **Spec:** .sdd/specs/seguranca-de-contas.spec.md (v1.0) · atende RF-11, RF-12
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** SEG-002, SEG-003

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`
- `backend/src/main/resources/application.yaml`
- `infra/.env.example`
- `infra/scripts/gerar-env-servidor.sh` — `TOTP_CHAVE`
- `.github/dependabot.yml`
- `.github/workflows/pipeline.yml` — `npm audit`

## Critérios de aceite

- [x] `RotasProtegidasIT` verde (só as públicas declaradas sem login)
- [x] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
