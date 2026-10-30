# ALUNO-002 — Aula e player

- **Spec:** .sdd/specs/area-do-aluno.spec.md (v1.0) · atende RF-03, RF-04, RF-05
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `web/aluno/src/paginas/aula/AulaPagina.tsx`
- `web/aluno/src/paginas/aula/PlayerVideo.tsx` — hls.js
- `web/aluno/src/paginas/aula/ListaArquivos.tsx`
- `web/aluno/src/paginas/forum/ForumLista.tsx`
- `web/aluno/src/paginas/forum/TopicoPagina.tsx`
- `web/aluno/src/paginas/turma/ForumTurma.tsx`

## Descrição técnica

Token só nas playlists da API; retomada e envio periódico da posição.

## Restrições

Nunca enviar o token ao armazenamento.

## Critérios de aceite

- [x] `web/e2e/aluno/estudo.spec.ts` (vídeo até o fim) verde

## Armadilhas

Enviar o `Authorization` aos trechos assinados quebra a assinatura.
