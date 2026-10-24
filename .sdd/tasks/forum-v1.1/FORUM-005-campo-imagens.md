# FORUM-005 — Campo de imagens com escolher, arrastar e colar

- **Spec:** .sdd/specs/forum.spec.md (v1.1) · atende RF-09, RF-10
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `web/compartilhado/src/ui/CampoImagens.tsx`
- `web/compartilhado/src/ui/imagens.ts`
- `web/aluno/src/paginas/forum/useImagensMensagem.ts`

## Descrição técnica

Prévia, remoção e progresso; tipo, tamanho e quantidade conferidos em todos os caminhos.

## Restrições

O `accept` do seletor não filtra arrastar e colar.

## Critérios de aceite

- [x] Vitest do campo verde

## Armadilhas

Conferir só no seletor de arquivo.
