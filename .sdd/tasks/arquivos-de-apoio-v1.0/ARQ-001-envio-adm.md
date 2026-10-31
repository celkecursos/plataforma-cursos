# ARQ-001 — Envio pelo administrador

- **Spec:** .sdd/specs/arquivos-de-apoio.spec.md (v1.0) · atende RF-01, RF-02, RF-03, RF-06
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/arquivo/api/ArquivoAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/arquivo/application/ArquivoAdmService.java`
- `backend/src/main/java/br/com/celke/cursos/arquivo/domain/ArquivoApoio.java`
- `backend/src/main/java/br/com/celke/cursos/arquivo/domain/FormatoArquivo.java`
- `backend/src/main/java/br/com/celke/cursos/arquivo/domain/NomeArquivo.java`
- `backend/src/main/java/br/com/celke/cursos/arquivo/application/DonoArquivo.java`
- `backend/src/main/java/br/com/celke/cursos/arquivo/infra/ArquivoApoioRepository.java`

## Descrição técnica

Duas etapas como a capa; confere chave do mesmo dono, tamanho e assinatura e move de `uploads/` para `arquivos/`.

## Restrições

Espelhar `CapaCursoService`.

## Critérios de aceite

- [x] `FormatoArquivoTest`, `NomeArquivoTest`, `ArquivoAdmServiceTest` verdes
- [x] `ArquivoIT` verde

## Armadilhas

Nome de download com caminho ou aspas quebra o cabeçalho.
