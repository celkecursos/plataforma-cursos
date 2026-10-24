# CERT-001 — Regra de conclusão e código

- **Spec:** .sdd/specs/certificado.spec.md (v1.0) · atende RF-01, RF-03, RF-06
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/certificado/domain/RegraConclusao.java`
- `backend/src/main/java/br/com/celke/cursos/certificado/domain/CodigoValidacao.java`
- `backend/src/main/java/br/com/celke/cursos/certificado/domain/NomeCertificado.java`
- `backend/src/main/java/br/com/celke/cursos/certificado/domain/Certificado.java`
- `backend/src/main/java/br/com/celke/cursos/certificado/infra/CertificadoRepository.java`

## Descrição técnica

Código de 12 caracteres sem letras ambíguas (60 bits); turma sem aulas nunca é concluída.

## Critérios de aceite

- [x] `CertificadoDominioTest` verde

## Armadilhas

Gerador não criptográfico para o código.
