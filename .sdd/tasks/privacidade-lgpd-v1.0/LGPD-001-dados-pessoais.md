# LGPD-001 — Dados pessoais por módulo

- **Spec:** .sdd/specs/privacidade-lgpd.spec.md (v1.0) · atende RF-01
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/shared/lgpd/DadosPessoais.java`
- `backend/src/main/java/br/com/celke/cursos/auth/infra/AuthDadosPessoais.java`
- `backend/src/main/java/br/com/celke/cursos/matricula/infra/MatriculaDadosPessoais.java`
- `backend/src/main/java/br/com/celke/cursos/progresso/infra/ProgressoDadosPessoais.java`
- `backend/src/main/java/br/com/celke/cursos/forum/infra/ForumDadosPessoais.java`
- `backend/src/main/java/br/com/celke/cursos/certificado/infra/CertificadoDadosPessoais.java`
- `backend/src/main/java/br/com/celke/cursos/termo/infra/TermoDadosPessoais.java`
- `backend/src/main/java/br/com/celke/cursos/video/infra/VideoDadosPessoais.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/infra/PagamentoDadosPessoais.java`

## Descrição técnica

Cada módulo exporta e exclui os próprios dados.

## Critérios de aceite

- [x] `PrivacidadeIT` verde

## Armadilhas

Funcionalidade nova sem a sua implementação de dados pessoais.
