# SEG-001 — Segundo fator dos administradores

- **Spec:** .sdd/specs/seguranca-de-contas.spec.md (v1.0) · atende RF-01, RF-02, RF-03, RF-04
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/auth/domain/Totp.java`
- `backend/src/main/java/br/com/celke/cursos/auth/domain/SegundoFator.java`
- `backend/src/main/java/br/com/celke/cursos/auth/domain/CodigoRecuperacao.java`
- `backend/src/main/java/br/com/celke/cursos/auth/application/SegundoFatorService.java`
- `backend/src/main/java/br/com/celke/cursos/auth/api/ComandoRedefinirSegundoFator.java`
- `web/compartilhado/src/sessao/SegundoFator.tsx`

## Descrição técnica

TOTP (RFC 6238) com segredo cifrado por `TOTP_CHAVE`; código sem reuso; códigos de recuperação.

## Critérios de aceite

- [x] `TotpTest` verde
- [x] `AuthSegundoFatorIT` verde

## Armadilhas

Aceitar o mesmo código duas vezes na janela.
