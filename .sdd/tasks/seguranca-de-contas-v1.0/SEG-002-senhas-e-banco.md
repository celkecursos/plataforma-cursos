# SEG-002 — Política de senha e menor privilégio

- **Spec:** .sdd/specs/seguranca-de-contas.spec.md (v1.0) · atende RF-05, RF-06
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/usuario/domain/PoliticaSenha.java` — comuns, vazadas, nome, e-mail, sequências
- `backend/src/main/resources/db/migration/V17__seguranca.sql`
- `infra/compose.prod.yml` — serviço `migracao` (comando `migrar`)

## Descrição técnica

API e worker usam um usuário só de dados; as migrations rodam com o dono do schema.

## Critérios de aceite

- [x] `PoliticaSenhaTest` verde
- [x] `UsuarioAplicacaoBancoIT` verde

## Armadilhas

Lista de senhas embutida em vez de gerada.
