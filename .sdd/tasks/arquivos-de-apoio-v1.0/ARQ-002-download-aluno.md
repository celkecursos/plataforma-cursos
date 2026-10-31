# ARQ-002 — Lista e download do aluno

- **Spec:** .sdd/specs/arquivos-de-apoio.spec.md (v1.0) · atende RF-04, RF-05
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** ARQ-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/arquivo/api/ArquivoAlunoController.java`
- `backend/src/main/java/br/com/celke/cursos/arquivo/application/ArquivoAlunoService.java`

## Descrição técnica

Lista da aula (acesso da aula) e do módulo (matrícula vigente); download por URL de 5 minutos como anexo.

## Restrições

A chave do bucket nunca sai na resposta.

## Critérios de aceite

- [x] `ArquivoAlunoServiceTest` verde
- [x] `ArquivoAcessoIT` verde
- [x] `ArquivoE2E` verde

## Armadilhas

Esquecer o `Content-Disposition` abre o arquivo no navegador.
