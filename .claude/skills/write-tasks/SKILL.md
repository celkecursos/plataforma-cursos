---
name: write-tasks
description: Decompõe uma spec aprovada em tasks de implementação em .sdd/tasks/<slug>-v<versao>/ — README com ordem e grafo, uma task por arquivo, sem código, com nível, dependências, arquivos exatos e critérios de aceite. É a Fase 2 do SDD (comando /gerar-tasks, executado pelo dev-lead).
---

# Escrever tasks (Fase 2 do SDD)

Transforme uma spec **aprovada** em tasks que um agente executa sem adivinhar nada. A spec diz o QUÊ;
a task diz ONDE e COMO no código deste repositório, em prosa.

## Antes de escrever

1. Confira o `status: aprovada` da spec (ou o "sim" do usuário). Sem isso, pare.
2. Leia `CLAUDE.md`, `backend/CLAUDE.md`, `.claude/rules/` e `docs/plano-implementacao.md`.
3. Escolha o módulo existente mais parecido com o caso: é o espelho que as tasks vão citar.
4. Leia `docs/configuracao.md`: hosts, e-mails e endereços citados nas tasks usam esses valores.

## Regras

- **Rastreio:** toda task atende a um ou mais `RF-NN` da spec, e todo `RF-NN` é atendido por alguma
  task. Escreva no README a tabela RF → tasks.
- **Sem código:** contratos em prosa e o arquivo exato a espelhar. Exceção: `record`, `enum` ou `type`
  de domínio que fixa o contrato.
- **Explícito:** nomeie arquivo, classe, rota (método + caminho), tabela e migration. Proibido "algo
  como".
- **Nível:** `Pleno` segue padrão existente; `Sênior` define padrão (módulo novo, segurança, acesso ao
  conteúdo, integração externa, fila, migration com dado existente, decisão que cruza módulos).
- **Paralelismo:** tasks sem dependência entre si rodam juntas e por isso **não listam o mesmo
  arquivo**.
- **Fiação por último:** `SecurityConfig`, `application.yaml`, `infra/.env.example`, os compose, a
  migration da funcionalidade e o `rotas.tsx` de cada front ficam numa única task de fiação, que depende
  de todas as outras.
- **Teste faz parte da task:** cada task com regra lista o teste (unitário, integração com a matriz de
  papéis, e2e quando atravessa containers, Vitest ou Playwright no front).
- **Sem segredos:** cite o nome da variável (`SMTP_PASSWORD`, `JWT_SEGREDO`), nunca o valor. Senha,
  token, chave e IP não entram em task.

## Onde salvar

`.sdd/tasks/<slug>-v<versao>/`, com a versão da spec (`autenticacao-v1.0/`). Spec revisada ganha uma
pasta nova; as tasks já implementadas não são reescritas.

- `README.md` — tabela (Ordem · ID · Título · Nível · Depende de), grafo em texto, tabela RF → tasks e
  resumo por nível.
- Uma task por arquivo: `<FEAT>-NNN-<titulo>.md` (`FEAT` em maiúsculas, curto: `AUTH-001-...`).

## Modelo da task

```markdown
# <FEAT>-NNN — <Título>

- **Spec:** .sdd/specs/<slug>.spec.md (v<versao>) · atende RF-NN
- **Nível:** Pleno | Sênior
- **Agente:** dev-pleno | dev-senior
- **Status:** Pronta | Concluída
- **Depende de:** <IDs ou —>

## Arquivos a criar/alterar
- caminho/exato — o que passa a fazer

## Descrição técnica
Contrato, padrão a espelhar (arquivo exato), integração esperada.

## Restrições
Invariantes que valem aqui.

## Critérios de aceite
- [ ] verificável por máquina ou inspeção
- [ ] teste de <regra> em <arquivo de teste>

## Armadilhas
O que costuma dar errado aqui.
```

## Depois

Atualize o índice `.sdd/README.md` com a pasta nova, apresente o `README.md` das tasks e **pare para a
revisão humana**. Aprovado: `/implementar-tasks .sdd/tasks/<slug>-v<versao>/README.md
.sdd/specs/<slug>.spec.md`.
