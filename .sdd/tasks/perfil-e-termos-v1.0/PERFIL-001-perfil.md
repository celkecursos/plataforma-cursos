# PERFIL-001 — Perfil, senha e foto

- **Spec:** .sdd/specs/perfil-e-termos.spec.md (v1.0) · atende RF-01, RF-02, RF-03
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/perfil/api/PerfilController.java`
- `backend/src/main/java/br/com/celke/cursos/perfil/application/PerfilService.java`

## Descrição técnica

Troca de senha encerra as sessões e limpa o cookie; foto regravada até 400 × 400.

## Restrições

Chave da foto só do próprio usuário.

## Critérios de aceite

- [x] `PerfilServiceTest` verde
- [x] `PerfilIT` verde

## Armadilhas

Esquecer de limpar o cookie de renovação.
