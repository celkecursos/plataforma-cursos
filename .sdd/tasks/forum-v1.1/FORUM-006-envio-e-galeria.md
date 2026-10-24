# FORUM-006 — Envio ao publicar e galeria

- **Spec:** .sdd/specs/forum.spec.md (v1.1) · atende RF-11, RF-12
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** FORUM-005

## Arquivos a criar/alterar

- `web/compartilhado/src/ui/GaleriaImagens.tsx`
- `web/aluno/src/paginas/forum/TopicoPagina.tsx`
- `web/aluno/src/paginas/forum/ForumLista.tsx`
- `web/adm/src/paginas/turma/ForumAba.tsx`

## Descrição técnica

Envia as imagens só ao publicar; mostra as publicadas com visualização em tela cheia.

## Critérios de aceite

- [x] `web/e2e/aluno/forum.spec.ts` (caminho feliz e infeliz) verde

## Armadilhas

Reaproveitar a chave de uma tentativa que falhou: a API já apagou o envio.
