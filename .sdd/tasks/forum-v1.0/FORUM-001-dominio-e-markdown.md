# FORUM-001 — Tópicos, respostas e Markdown seguro

- **Spec:** .sdd/specs/forum.spec.md (v1.0) · atende RF-01, RF-04, RF-07
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/forum/domain/Topico.java`
- `backend/src/main/java/br/com/celke/cursos/forum/domain/Resposta.java`
- `backend/src/main/java/br/com/celke/cursos/forum/domain/Anexo.java`
- `backend/src/main/java/br/com/celke/cursos/shared/texto/MarkdownSeguro.java` — commonmark + sanitizador OWASP
- `backend/src/main/java/br/com/celke/cursos/forum/infra/TopicoRepository.java`
- `backend/src/main/java/br/com/celke/cursos/forum/infra/RespostaRepository.java`
- `backend/src/main/java/br/com/celke/cursos/forum/infra/AnexoRepository.java`

## Descrição técnica

Markdown guardado junto com o HTML filtrado; links com `rel="nofollow noopener noreferrer"`.

## Restrições

Só o HTML filtrado sai na resposta.

## Critérios de aceite

- [x] `MarkdownSeguroTest` (XSS em tabela) e `TopicoTest` verdes

## Armadilhas

Filtrar só na saída e guardar HTML cru.
