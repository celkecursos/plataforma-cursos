# AUTH-004 — Comando de terminal para criar administrador

- **Spec:** .sdd/specs/autenticacao.spec.md (v1.0) · atende RF-09
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** AUTH-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/usuario/api/ComandoCriarAdmin.java` — `criar-admin <email> "<nome>"` sem servidor HTTP
- `backend/src/main/java/br/com/celke/cursos/usuario/application/CriarAdminService.java`

## Descrição técnica

Lê a senha duas vezes do console sem eco e cria o administrador já confirmado.

## Restrições

A senha nunca aparece em log nem em argumento de linha de comando.

## Critérios de aceite

- [x] `ComandoCriarAdminTest` e `CriarAdminServiceTest` verdes

## Armadilhas

Ler a senha por argumento deixa a senha no histórico do shell.
