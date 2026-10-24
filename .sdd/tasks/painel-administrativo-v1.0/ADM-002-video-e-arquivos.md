# ADM-002 — Vídeo, capa e arquivos

- **Spec:** .sdd/specs/painel-administrativo.spec.md (v1.0) · atende RF-03, RF-04
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `web/adm/src/paginas/aula/AulaPagina.tsx`
- `web/adm/src/paginas/aula/VideoAula.tsx`
- `web/compartilhado/src/api/envio.ts` — envio multipart com progresso e cancelamento
- `web/compartilhado/src/ui/EscolherArquivo.tsx`

## Descrição técnica

Partes direto ao Garage com progresso; consulta do status a cada 3 s.

## Critérios de aceite

- [x] `web/e2e/adm/curso-completo.spec.ts` (vídeo curto até pronto) verde

## Armadilhas

Não cancelar o envio ao sair da página.
