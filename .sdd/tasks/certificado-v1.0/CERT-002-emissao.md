# CERT-002 — Situação, emissão e PDF

- **Spec:** .sdd/specs/certificado.spec.md (v1.0) · atende RF-02, RF-04, RF-05, RF-07
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** CERT-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/certificado/api/CertificadoController.java`
- `backend/src/main/java/br/com/celke/cursos/certificado/api/CertificadoResponses.java`
- `backend/src/main/java/br/com/celke/cursos/certificado/application/CertificadoService.java`
- `backend/src/main/java/br/com/celke/cursos/certificado/infra/GeradorPdf.java`
- `backend/src/main/resources/templates/certificado/certificado.html`

## Descrição técnica

PDF gerado uma vez em `certificados/<turma>/<codigo>.pdf`; emitir de novo devolve o mesmo; validação pública sem e-mail nem ids.

## Restrições

Download só do próprio aluno.

## Critérios de aceite

- [x] `CertificadoServiceTest` verde
- [x] `CertificadoIT` e `CertificadoE2E` verdes

## Armadilhas

Gerar o PDF a cada download muda o documento.
