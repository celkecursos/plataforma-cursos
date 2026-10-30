# MAT-002 — Matrículas pelo administrador

- **Spec:** .sdd/specs/matricula-e-acesso.spec.md (v1.0) · atende RF-01, RF-02, RF-09
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** MAT-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/matricula/api/MatriculaAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/matricula/api/MatriculaRequests.java`
- `backend/src/main/java/br/com/celke/cursos/matricula/api/MatriculaResponses.java`
- `backend/src/main/java/br/com/celke/cursos/matricula/application/MatriculaAdmService.java`

## Descrição técnica

Listar, matricular por e-mail, alterar período e cancelar.

## Restrições

Usa o `UsuarioService`, nunca o repositório de usuário.

## Critérios de aceite

- [x] `MatriculaAdmServiceTest` verde
- [x] `MatriculaAdmIT` verde

## Armadilhas

Permitir duas matrículas ativas.
