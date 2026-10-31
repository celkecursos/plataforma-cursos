# Pagamentos

A plataforma vende as turmas por um meio de pagamento externo. O aluno clica em **Comprar** no site,
paga no checkout do meio de pagamento e a plataforma recebe as notificações (webhook) de cada mudança da
compra, criando, mantendo ou cancelando a matrícula. O primeiro meio de pagamento é a **Hotmart**
(webhook **2.0.0**); a estrutura aceita outros sem mexer no núcleo.

## Como funciona

```
Site (Comprar) → /comprar/:oferta (aluno logado) → POST /api/pagamentos/checkout
   → pedido CRIADO (código público de 22 caracteres) → redireciona ao checkout com o código no xcod
Meio de pagamento → POST /api/pagamentos/webhooks/<token> (autenticado pelo hottok)
   → evento gravado (idempotente pelo id do evento) → fila → worker aplica ao pedido e à matrícula
Página de obrigado → /pagamento/retorno?pedido=<código> → consulta o pedido a cada 3 s (até 2 min)
```

- Um meio de pagamento **ativo** por vez recebe os novos checkouts. Um meio desativado continua
  recebendo as notificações (compras antigas ainda mudam de status).
- Os segredos (hottok, client secret, basic) ficam cifrados com AES-256-GCM pela chave
  `PAGAMENTOS_CHAVE` do `.env` do ambiente e **nunca** voltam nas respostas da API: o painel mostra só
  "configurado ••••1234". Campo secreto em branco ao editar mantém o valor atual.
- Venda que chega **sem o código do pedido** (afiliado, link direto da Hotmart) vira um pedido
  `EXTERNO`, achado pela oferta (produto + oferta) e pelo e-mail do comprador.
- O comprador sem conta ganha uma conta de aluno **sem senha**, com o e-mail confirmado, e recebe
  "Sua matrícula foi confirmada" com o link de **criar a senha** (o mesmo token da recuperação de
  senha, com a mesma validade). Nunca há senha por e-mail.

## Configurar a Hotmart

1. No ADM, **Pagamentos → Meios de pagamento → Novo meio de pagamento**: escolha Hotmart, dê um nome
   e informe o **hottok** (Hotmart → Ferramentas → Webhook). Salve, use **Testar credenciais** e
   **Ativar**.
2. Copie o **endereço do webhook** mostrado no painel (`https://aluno.celke.uno/api/pagamentos/webhooks/<token>`).
3. Na Hotmart, **Ferramentas → Webhook**: crie uma configuração na **versão 2.0.0** com esse endereço e
   marque os eventos de compra:
   `PURCHASE_APPROVED`, `PURCHASE_COMPLETE`, `PURCHASE_CANCELED`, `PURCHASE_REFUNDED`,
   `PURCHASE_CHARGEBACK`, `PURCHASE_BILLET_PRINTED`, `PURCHASE_PROTEST`, `PURCHASE_DELAYED`,
   `PURCHASE_EXPIRED`. Os outros (assinatura, área de membros) podem ficar marcados: são gravados e
   ignorados.
4. Em cada produto, aponte a **página de obrigado** para `https://aluno.celke.uno/pagamento/retorno`.
   A página lê o código do pedido em `?pedido=` ou em `?xcod=`; confira no "Enviar teste" se a Hotmart
   repassa o `xcod` na URL e, se não repassar, inclua-o pelas variáveis da página de obrigado do produto.
5. Na turma, aba **Venda**, cadastre a oferta: **Id do produto** (o número que vem nas notificações em
   `data.product.id`), **Código da oferta** (o `off=` do link), **Código do checkout** (o trecho depois
   de `pay.hotmart.com/` no link, quando for diferente do id), título, preço e dias de acesso (em
   branco, sem prazo).
6. Use **Enviar teste** de cada evento na Hotmart e confira em **Pagamentos → Eventos recebidos**. O
   histórico de entregas da Hotmart deve mostrar 200 em todos.

Credenciais da API (opcional, só para a reconciliação): **Ferramentas → Credenciais** → Client ID,
Client Secret e Basic. Com elas, ligue `PAGAMENTOS_RECONCILIACAO=true` no `.env` do ambiente.

Na **homologação** (restrita por IP), o Caddy deixa passar só `/api/pagamentos/webhooks/*`, para a
Hotmart de teste conseguir notificar. Use uma conta/produto de teste da Hotmart apontando para
`https://homolog-aluno.celke.uno/api/pagamentos/webhooks/<token>`.

## Status do pedido

| Status | Hotmart (evento / `purchase.status`) | Matrícula |
|---|---|---|
| `CRIADO` | pedido aberto, o aluno ainda não pagou | nada |
| `AGUARDANDO_PAGAMENTO` | `PURCHASE_BILLET_PRINTED`; `WAITING_PAYMENT`, `PRINTED_BILLET`, `STARTED`, `UNDER_ANALISYS`, `PROCESSING_TRANSACTION` | nada |
| `APROVADO` | `PURCHASE_APPROVED` / `APPROVED` | cria (ou reativa, ou estende) a matrícula |
| `CONCLUIDO` | `PURCHASE_COMPLETE` / `COMPLETE` | mantém (cria se a aprovação se perdeu) |
| `EM_DISPUTA` | `PURCHASE_PROTEST` / `DISPUTE` | mantém e avisa o admin (`app.pagamentos.disputa-suspende=true`: tira o acesso) |
| `ATRASADO` | `PURCHASE_DELAYED` / `OVERDUE` | mantém (`app.pagamentos.atraso-suspende-apos=P7D`: tira o acesso depois do prazo) |
| `REEMBOLSADO` | `PURCHASE_REFUNDED` / `REFUNDED`, `PARTIALLY_REFUNDED` | **cancela** |
| `CHARGEBACK` | `PURCHASE_CHARGEBACK` / `CHARGEBACK` | **cancela** e avisa o admin |
| `CANCELADO` | `PURCHASE_CANCELED` / `CANCELLED`, `BLOCKED`, `NO_FUNDS` | cancela, se existia |
| `EXPIRADO` | `PURCHASE_EXPIRED` / `EXPIRED` | nada |

As transições permitidas ficam em `TransicoesPedido` (um lugar só). Evento mais antigo que a última
mudança é ignorado (a Hotmart manda fora de ordem e reenvia), exceto reembolso e chargeback, que sempre
valem; `APROVADO` depois de `REEMBOLSADO` não reabre o acesso.

Matrícula: início na aprovação e fim pelos dias de acesso da oferta; se o aluno já tem matrícula ativa
na turma, o período é estendido. A forma vem de `payment.type` (`CREDIT_CARD` → cartão, `PIX`,
`BILLET` → boleto, outros → `OUTRO`).

## O que a API responde à Hotmart

| Situação | Resposta |
|---|---|
| Gravado e na fila | 200 `recebido` |
| Mesmo `id` de novo (reenvio) | 200 `duplicado` (nada muda) |
| Corpo que não é o JSON 2.0.0 | 200 `erro` (gravado em `ERRO` com o corpo; reenviar não resolve) |
| Oferta não cadastrada, evento não tratado | 200 `recebido` e o evento fica `IGNORADO` |
| Hottok errado ou ausente | 401 (nada é gravado) |
| Endereço desconhecido | 404 |
| Corpo acima de 256 KB | 413 |
| Banco fora do ar | 500 (a Hotmart reenvia) |

A Hotmart reenvia as falhas e **desativa a configuração** se a URL continuar falhando: por isso só o
que não pôde ser gravado vira erro.

## Evento em erro ou ignorado

- **Pagamentos → Eventos recebidos**, filtro por situação. O detalhe mostra o motivo e o corpo (com
  documento, telefone e IP mascarados).
- **Ignorado por oferta não cadastrada**: cadastre a oferta na turma e use **Reprocessar**.
- **Erro** (o worker desistiu depois das tentativas): o admin recebe um e-mail em `ALERTA_EMAIL`.
  Corrija a causa (veja o log do worker) e use **Reprocessar**.
- Corpo que não foi interpretado não é reprocessável: confira a versão do webhook (2.0.0) na Hotmart.
- Retenção: o corpo de eventos processados ou ignorados com mais de 180 dias é apagado toda semana
  (o resto da linha fica para auditoria).

## Reconciliação

Desligada por padrão (`PAGAMENTOS_RECONCILIACAO=false`). Ligada, o worker consulta todo dia às 5h, pela
API de histórico de vendas, os pedidos com transação que estão pendentes há mais de 24 h ou pagos sem
notificação há mais de 3 dias (até 60 dias sem mudança). Se o status mudou, grava um evento sintético
(`reconciliacao:<transação>:<status>`) que segue o mesmo caminho do webhook. Confira host e parâmetros
na documentação da Hotmart antes de ligar.

## Diferenças em relação ao webhook 1.0.0

| | 1.0.0 (não usar) | 2.0.0 (usado aqui) |
|---|---|---|
| Corpo | campos soltos (`prod`, `status`, `email`, `transaction`, `xcod`) | JSON `{ id, creation_date, event, version, data }` |
| Autenticação | `hottok` no corpo | cabeçalho `X-HOTMART-HOTTOK` |
| Idempotência | não há | `id` único por evento |
| Código nosso | `xcod` | `data.purchase.origin.xcod` |

As fixtures dos testes ficam em `backend/src/test/resources/pagamentos/hotmart/`. Quando a Hotmart
mudar um campo, troque a fixture pelo corpo real do "Enviar teste" (sem dados pessoais) e ajuste o
`InterpretadorHotmart`.

## Como adicionar um meio de pagamento

1. Valor novo em `TipoGateway` (ex.: `MERCADO_PAGO`).
2. Um bean que implementa `GatewayPagamento`: campos de configuração (o painel monta o formulário a
   partir deles; `secreto` vai cifrado), capacidades, teste das credenciais, checkout
   (`Redirecionamento` ou `Embutido`), autenticação da notificação, interpretação para
   `EventoPagamento` (com o status interno) e, se tiver API, a consulta da transação.
3. Fixtures das notificações reais e testes de interpretação e de autenticação.
4. Nada no núcleo, no painel ou no front muda: o formulário vem de `camposConfiguracao` e o checkout
   embutido já chega ao front (`/comprar/:oferta`) com os `dados` para o formulário do meio.
