---
name: write-specs
description: Transforma uma ideia crua de funcionalidade numa especificação rigorosa e agnóstica de tecnologia — descoberta com perguntas, validação de premissas, modelo de nove seções em .sdd/specs/ e parada obrigatória para aprovação humana antes de virar tasks ou código. É a Fase 1 do SDD (comando /sdd).
---

# Escrever especificações (Fase 1 do SDD)

Transforme a ideia do usuário numa **especificação rigorosa e agnóstica de tecnologia** e **pare para
aprovação**. A spec é o contrato das fases seguintes: decisão errada custa muito menos aqui do que no
código.

## Regras

- **Agnóstica:** descreve o QUÊ e o PORQUÊ (atores, regras, estados, contratos, casos de borda), nunca
  o COMO. Sem linguagem, framework, biblioteca, nome de arquivo, código, pseudocódigo ou SQL.
- **Onde salvar:** `.sdd/specs/<slug>.spec.md` (kebab-case, ex.: `cupom-de-desconto.spec.md`).
- **Contexto do produto:** leia `README.md` e `docs/plano-implementacao.md` para usar os mesmos termos
  do domínio (curso, turma, matrícula, aula, oferta, pedido…) e não especificar o que já existe.
- **Portão:** pergunte explicitamente se o usuário aprova antes de qualquer avanço.
- **Valores do projeto:** use os de `docs/configuracao.md` (domínio, subdomínios, e-mails, dono do
  repositório) nos contratos e nas premissas. Senha, token, chave e IP nunca entram na spec.

## Passos

1. **Analise** a ideia.
2. **Descoberta:** até **5 perguntas por rodada** — o porquê de negócio, o "e se" dos casos de borda,
   conflitos com regras que já existem (acesso ao conteúdo, pagamentos, LGPD, auditoria), volume.
   Desafie a complexidade: se parece grande demais, proponha a versão menor que resolve.
3. **Premissas e riscos:** liste e espere a confirmação **antes** de redigir.
4. **Redija** no modelo abaixo, salve e acrescente a spec ao índice `.sdd/README.md`.
5. **Pare** e pergunte: _"Esta é a especificação baseada na nossa conversa. Revise cada seção (comente
   ou peça alterações). Você aprova?"_ Só depois de um "sim" explícito informe:
   `/gerar-tasks .sdd/specs/<slug>.spec.md`.

## Modelo (nove seções)

```markdown
---
feature: <slug>
versao: 1.0
status: rascunho
autor: pm-spec-architect
---

# <Nome da funcionalidade>

## 1. Resumo Executivo
O problema, o valor e o escopo (o que entra e o que fica de fora).

## 2. Atores e Papéis
Quem usa ou é afetado (visitante, aluno, administrador, meio de pagamento, worker) e o que cada um pode.

## 3. Requisitos Funcionais
`RF-01`, `RF-02`… no formato "O sistema DEVE…", verificáveis e sem ambiguidade.

## 4. Modelo de Domínio
Entidades, atributos e relações em linguagem de negócio.

## 5. Estados e Fluxos
Estados de cada entidade, transições e o passo a passo dos fluxos principais.

## 6. Contratos de Interface
Entradas e saídas de cada operação, em termos de domínio (nome do campo e significado).

## 7. Casos de Borda
Erros, concorrência, vazios, limites, permissões, dados pessoais.

## 8. Premissas Validadas
O que foi assumido e confirmado pelo usuário.

## 9. Riscos
O que pode dar errado e a mitigação.
```

Ao revisar uma spec aprovada: suba a `versao`, volte o `status` para `rascunho` e peça aprovação de
novo.
