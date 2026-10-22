---
paths:
  - "backend/src/test/**"
  - "backend/src/e2e/**"
  - "web/**/*.test.ts"
  - "web/**/*.test.tsx"
  - "web/e2e/**"
description: Proteção dos testes que já existem — nunca enfraquecer um teste para ele passar. Carrega ao tocar testes.
---

# Testes existentes

Pode, sem pedir:

- criar arquivos de teste novos;
- acrescentar casos em arquivos de teste que já existem;
- ajustar um teste quando a **regra mudou de propósito** na mesma tarefa (ex.: a política de senha
  ficou mais rígida e o dado de teste antigo deixou de ser válido), dizendo no relatório qual teste
  mudou e por quê.

Não pode:

- apagar um teste, trocar a asserção por outra mais fraca, aumentar tolerância, colocar `@Disabled`,
  `skip`, `only` ou espera por tempo fixo **para fazer um teste vermelho passar**;
- mudar o teste quando quem está errado é o código.

Teste falhou e você não sabe se o errado é o código ou o teste? Pare e explique ao usuário o que o
teste espera, o que o código faz e qual dos dois você acha que está certo.
