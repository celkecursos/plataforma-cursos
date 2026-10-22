---
description: Fase 2 do SDD — decompõe uma spec APROVADA em tasks de implementação (delega ao dev-lead).
argument-hint: <caminho da spec .sdd/specs/…spec.md>
---

Spec a decompor: **$1**

Confira que a spec está **aprovada** (`status: aprovada` ou o "sim" do usuário nesta conversa). Se
não estiver, pare e volte para o `/sdd`.

## Faça

Delegue ao subagente **`dev-lead`** (ferramenta Agent, `subagent_type: "dev-lead"`) passando:

- o conteúdo integral da spec;
- a instrução de gerar as tasks em `.sdd/tasks/<slug>-v<versão>/` com o `README.md` (ordem, grafo,
  níveis) e uma task por arquivo, sem código, cada uma com `Nível:` (`Pleno` ou `Sênior`) e
  `Depende de:`, e a task de fiação por último.

## Ao voltar

Confira a regra de paralelismo (tasks sem dependência mútua não repetem arquivo; arquivos de fiação só
na task de fiação). Apresente o `README.md` das tasks e **pare no Portão 2**. Aprovado:
`/implementar-tasks .sdd/tasks/<slug>-v<versão>/README.md $1`.
