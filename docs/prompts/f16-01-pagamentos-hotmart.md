# Pagamentos: estrutura multi-gateway e integração com a Hotmart

**Fase:** 16
**Branch:** `feature/pagamentos`

> Diferente dos prompts das fases 0 a 10, este não traz o código pronto: ele especifica o que
> construir. Antes de implementar, siga a seção **0. Confirmar a documentação da Hotmart**.

## Prompt

Vamos vender os cursos pela plataforma. O aluno clica em "Comprar", paga no checkout do meio de
pagamento e a plataforma recebe do meio de pagamento a confirmação e cada mudança de status da compra
(aprovada, reembolsada, chargeback, etc.), criando, mantendo ou cancelando a matrícula. O primeiro meio
de pagamento é a **Hotmart**, mas a estrutura tem que aceitar outros (PagSeguro, Mercado Pago, Stripe...)
sem mexer no núcleo: o administrador escolhe e configura o meio de pagamento **pelo painel**.

Siga o `backend/CLAUDE.md` e os padrões do `web/` (fases 11 a 14). Não faça commit.

### 0. Confirmar a documentação da Hotmart (antes de escrever código)

A Hotmart tem duas versões de webhook: a **1.0.0**, que não é mais a recomendada, e a **2.0.0**. Use a
**2.0.0**. As diferenças que importam:

| | Webhook 1.0.0 (não usar) | Webhook 2.0.0 (usar) |
|---|---|---|
| Corpo | campos soltos (`prod`, `status`, `email`, `transaction`, `xcod`...) | JSON `{ id, creation_date, event, version, data }` |
| Autenticação | `hottok` no corpo | cabeçalho `X-HOTMART-HOTTOK` |
| O que aconteceu | `status` (minúsculo) + `callback_type` | `event` (`PURCHASE_APPROVED`, `PURCHASE_REFUNDED`...) + `data.purchase.status` |
| Idempotência | não há | `id` único por evento |
| Transação | `transaction` | `data.purchase.transaction` (ex.: `HP17715690036014`) |
| Produto e oferta | `prod`, `off` | `data.product.id`, `data.purchase.offer.code` |
| Comprador | `email`, `name`, `doc` | `data.buyer.email`, `data.buyer.name`, `data.buyer.document` |
| Código nosso no checkout | `xcod` | `data.purchase.origin.xcod` (também `origin.sck` e `origin.src`) |

O portal de desenvolvedores é renderizado por JavaScript (o `curl` traz a página vazia). Abra no
navegador e confira, porque a Hotmart muda os campos sem aviso:

- Eventos de compra: https://developers.hotmart.com/docs/pt-BR/2.0.0/webhook/purchase-webhook/
- Cancelamento de assinatura: https://developers.hotmart.com/docs/pt-BR/2.0.0/webhook/cancel-subscription-webhook/
- Troca de plano e de data de cobrança: `.../2.0.0/webhook/switch-plan-webhook/` e `.../2.0.0/webhook/update-subscription-charge-date/`
- Códigos de resposta HTTP e reenvio: https://developers.hotmart.com/docs/pt-BR/1.0.0/webhook/http-response-codes-webhook/
- Histórico de vendas (API REST): https://developers.hotmart.com/docs/pt-BR/v1/sales/sales-history/
- Changelog: https://developers.hotmart.com/docs/pt-BR/changelog
- Configurar o webhook (reenvio e desativação): https://help.hotmart.com/pt-br/article/360001491352

Depois, na Hotmart (Ferramentas → Webhook), crie uma configuração **versão 2.0.0** apontando para a
homologação e use **"Enviar teste"** de cada evento. Guarde os corpos recebidos (sem dados pessoais reais)
em `backend/src/test/resources/pagamentos/hotmart/<EVENTO>.json`: eles são as fixtures dos testes. Se
algum campo diferir deste prompt, vale o corpo real; ajuste o prompt e registre a diferença no
`docs/pagamentos.md`. Enquanto não houver corpos reais, monte as fixtures a partir do exemplo da
documentação, com dados fictícios (comprador `comprador.teste@example.com`, CPF `12345678909`,
transação `HP17715690036014`, produto `4774438`, oferta `k2pasun0`, `xcod` = `XCOD_DO_PEDIDO`, que os
testes trocam pelo código do pedido), uma por evento de compra, mais `SUBSCRIPTION_CANCELLATION` e
`PURCHASE_APPROVED_AFILIADO` (sem `buyer`, sem `price`, `creation_date` em texto), e um `LEIAME.md` na
pasta explicando que devem ser trocadas pelos corpos reais.

O que já se sabe da 2.0.0 (confira de novo antes de implementar):

- **Eventos de compra** (mesmo formato de corpo): `PURCHASE_APPROVED`, `PURCHASE_COMPLETE` (fim da
  garantia), `PURCHASE_CANCELED`, `PURCHASE_REFUNDED`, `PURCHASE_CHARGEBACK`, `PURCHASE_BILLET_PRINTED`,
  `PURCHASE_PROTEST` (pedido de reembolso/disputa), `PURCHASE_DELAYED` (atraso, em geral renovação),
  `PURCHASE_EXPIRED`.
- **Outros**: `PURCHASE_OUT_OF_SHOPPING_CART` (abandono), `SUBSCRIPTION_CANCELLATION`, `SWITCH_PLAN`,
  `UPDATE_SUBSCRIPTION_CHARGE_DATE`, `CLUB_FIRST_ACCESS`, `CLUB_MODULE_COMPLETED`, `ORDER_FULFILLMENT`
  (produto físico). Os não tratados são registrados e respondidos com 200.
- **`data.purchase.status`**: `APPROVED`, `BLOCKED`, `CANCELLED`, `CHARGEBACK`, `COMPLETE`, `EXPIRED`,
  `NO_FUNDS`, `OVERDUE`, `PARTIALLY_REFUNDED`, `PRE_ORDER`, `PRINTED_BILLET`, `PROCESSING_TRANSACTION`,
  `DISPUTE`, `REFUNDED`, `STARTED`, `UNDER_ANALISYS` (assim mesmo, na documentação), `WAITING_PAYMENT`.
  Trate como texto livre: valor desconhecido não pode quebrar o processamento.
- **`data.purchase.payment.type`**: `CREDIT_CARD`, `PIX`, `BILLET`, `PAYPAL`, `GOOGLE_PAY`, entre outros.
- **Datas** em milissegundos desde 1970 (alguns exemplos oficiais mandam como texto): aceite número e texto.
- **Afiliado e coprodutor** recebem o corpo sem `buyer` e sem `price`: esses campos são opcionais.
- A Hotmart **não assina o corpo**: o hottok no cabeçalho é a única autenticação. Compare em tempo
  constante (`MessageDigest.isEqual`).
- **Reenvio**: falhas são reenviadas automaticamente (até 5 vezes) e a configuração é **desativada** se a
  URL continuar falhando. Por isso, só responda erro quando o evento não pôde ser gravado.
- **API REST** (reconciliação): token OAuth por *client credentials* em
  `https://api-sec-vlc.hotmart.com/security/oauth/token` (cabeçalho `Authorization: Basic <basic>`, criado
  em Ferramentas → Credenciais junto com `client_id` e `client_secret`), e histórico de vendas em
  `GET https://developers.hotmart.com/payments/api/v1/sales/history` (filtros `transaction` e
  `transaction_status`; sem filtro de status, só vêm `APPROVED` e `COMPLETE`; paginação por
  `page_token`). Confirme host e parâmetros na documentação antes de usar.

Armadilhas comuns nessa integração, que **não** devem aparecer aqui:

- Gerar senha aleatória e mandar por e-mail. Aqui a conta nasce sem senha e o aluno recebe o link de
  "criar senha" (o mesmo token da recuperação de senha).
- Responder 401/500 para curso não encontrado: isso faz a Hotmart reenviar e desativar o webhook.
  Evento válido que não se aplica é **gravado como ignorado e respondido com 200**.
- Processar no próprio request, sem idempotência e sem guardar o corpo original.
- Deixar o hottok e as credenciais em texto puro no banco.

### 1. Módulo `pagamento` (backend)

Novo módulo `br.com.celke.cursos.pagamento` com `api/ application/ domain/ infra/`, como os outros. A
matrícula continua sendo do módulo `matricula`: o `pagamento` só a chama pelo serviço de aplicação.

#### 1.1 A porta de cada meio de pagamento

```java
/** Um meio de pagamento. Cada um é um bean; o núcleo não conhece nenhum pelo nome. */
public interface GatewayPagamento {
    TipoGateway tipo();

    /** Nome mostrado no painel (ex.: "Hotmart"). */
    String nome();

    /** Campos que o painel mostra para configurar (o front monta o formulário a partir daqui). */
    List<CampoConfiguracao> camposConfiguracao();

    /** O que este meio de pagamento sabe fazer. */
    Set<Capacidade> capacidades(); // CHECKOUT_REDIRECIONAMENTO, CHECKOUT_EMBUTIDO, CONSULTA_API, REEMBOLSO_API, RECORRENCIA

    /** Confere as credenciais com o meio de pagamento (botão "Testar" do painel). */
    ResultadoTeste testar(ConfiguracaoGateway configuracao);

    /** Monta o checkout do pedido: URL para redirecionar ou dados para um formulário embutido. */
    Checkout iniciarCheckout(Pedido pedido, Oferta oferta, Comprador comprador, ConfiguracaoGateway configuracao);

    /** Autentica a notificação recebida (cabeçalhos e corpo crus). */
    boolean autenticar(NotificacaoRecebida notificacao, ConfiguracaoGateway configuracao);

    /** Traduz a notificação para eventos normalizados (uma notificação pode gerar mais de um). */
    List<EventoPagamento> interpretar(NotificacaoRecebida notificacao);

    /** Situação atual da transação no meio de pagamento (reconciliação), se tiver CONSULTA_API. */
    Optional<EventoPagamento> consultar(String transacao, ConfiguracaoGateway configuracao);
}
```

- `TipoGateway`: `HOTMART` agora; `PAGSEGURO`, `MERCADO_PAGO` etc. entram depois só com um valor novo e
  um bean novo.
- `CampoConfiguracao(chave, rotulo, tipo, secreto, obrigatorio, dica, padrao, opcoes)`, com `tipo`
  `TEXTO`, `URL` ou `SELECAO` (valores em `opcoes`); campo em branco fica com o `padrao`. Para a Hotmart,
  nesta ordem: `hottok` (secreto, obrigatório), `ambiente` (`PRODUCAO`/`SANDBOX`, padrão `PRODUCAO`),
  `urlCheckoutBase` (URL, padrão `https://pay.hotmart.com`), `clientId`, `clientSecret` (secreto) e
  `basic` (secreto) — os três últimos opcionais, mas informados juntos.
- `Checkout`: `Redirecionamento(url)` ou `Embutido(Map<String, Object> dados)`; o front trata os dois.
- `EventoPagamento` (normalizado, independente do meio): `idExterno`, `tipoOriginal`, `ocorridoEm`,
  `transacao`, `statusPedido` (abaixo), `codigoPedido` (do `xcod`), `produtoExterno`, `ofertaExterna`,
  `comprador` (e-mail, nome; documento só mascarado), `valor` e `moeda`, `formaPagamento`, `assinatura`
  (código do assinante e status, quando houver).
- `GatewaysRegistrados`: junta os beans por `TipoGateway` (falha ao subir se houver dois do mesmo tipo).
- `ConfiguracaoGatewayService`: valida os valores contra `camposConfiguracao` (obrigatório, opção,
  endereço `http(s)://` com host, até 500 caracteres, chave desconhecida), devolvendo 400 com a lista
  `erros` em `valores.<chave>`; separa os comuns (JSON) dos secretos (JSON cifrado) e monta a
  `ConfiguracaoGateway` decifrada. Sem `PAGAMENTOS_CHAVE`, gravar segredo responde 409.
- Hotmart em `pagamento/infra/hotmart`: `InterpretadorHotmart` (o corpo 2.0.0 para `EventoPagamento`;
  valor em reais para centavos com arredondamento meio para cima; corpo que não é JSON, sem `id`, sem
  `event` ou de versão diferente de `2.x` lança `NotificacaoInvalidaException`), `ClienteApiHotmart`
  (token e histórico de vendas, com `RestClient` e tempo máximo configurável) e `GatewayHotmart`, montados
  num `@Configuration` com as propriedades `app.pagamentos.hotmart` (`url-token`, `url-api`,
  `url-api-sandbox`, `timeout`).

#### 1.2 Banco (`V15__criar_pagamentos.sql`)

- `usuarios.senha_hash` passa a aceitar nulo (conta criada pela compra). O login de conta sem senha
  confere o hash fictício e falha como senha errada; a troca de senha no perfil não aceita "senha atual".
- **`gateways_pagamento`**: `id`, `tipo` (sem CHECK: meio novo entra só com código), `nome`, `ativo`,
  `configuracao` (jsonb, campos não secretos), `segredos` (bytea, **AES-256-GCM** com a chave
  `PAGAMENTOS_CHAVE` do ambiente, IV aleatório de 12 bytes na frente, o `webhook_token` como dado
  associado), `webhook_token` (aleatório, entra na URL do webhook, único), `criado_em`, `atualizado_em`.
  **Índice único parcial**: só um gateway `ativo` para novos checkouts. Gateway desativado continua
  recebendo notificações (compras antigas ainda mudam de status).
- **`ofertas`**: `id`, `turma_id`, `gateway_id`, `produto_externo` (id do produto na Hotmart, como vem em
  `data.product.id`), `oferta_externa` (código da oferta), `codigo_checkout` (o código do produto no
  link `pay.hotmart.com/<código>`, quando diferente do id; nulo usa o id), `titulo`, `preco_centavos`,
  `moeda`, `dias_acesso` (nulo = sem prazo), `ativa`, `criado_em`, `atualizado_em`. Único em
  (`gateway_id`, `produto_externo`, `oferta_externa`).
- **`pedidos`**: `id`, `codigo` (público, aleatório, 22 caracteres URL-safe), `oferta_id`, `gateway_id`,
  `usuario_id` (nulo até a conta existir), `email`, `nome` (do comprador, para criar a conta),
  `status`, `transacao_externa`, `valor_centavos`, `moeda`, `forma_pagamento`, `matricula_id`, `origem`
  (`CHECKOUT` ou `EXTERNO`, venda que chegou sem `xcod`), `ultimo_evento_em` (quando ocorreu o evento da
  última mudança), `criado_em`, `atualizado_em`. Único em (`gateway_id`, `transacao_externa`).
- **`pedido_historico`**: `pedido_id`, `de`, `para`, `evento_id`, `motivo`, `em`.
- **`eventos_pagamento`**: `id`, `gateway_id`, `id_externo`, `tipo_original`, `recebido_em`,
  `ocorrido_em`, `transacao`, `payload` (jsonb, o corpo com documento, telefone e IP mascarados — campo
  reconhecido pelo nome, para qualquer meio; corpo que não é JSON vai como string JSON), `dados` (jsonb,
  o `EventoPagamento` normalizado que o worker lê), `status` (`RECEBIDO`, `PROCESSADO`, `IGNORADO`,
  `ERRO`), `erro` (também o motivo do ignorado), `pedido_id`, `processado_em`. **Único em
  (`gateway_id`, `id_externo`)**: é a idempotência. O JPA só lê `payload` e `dados`; a inclusão e a
  retenção são SQL (`JdbcClient`).
- **`pedido_historico`** fica depois de `eventos_pagamento` (aponta para o evento).
- `FormaPagamento` da matrícula ganha `OUTRO`, e `Matricula` ganha `pedido_id` (nulo nas manuais). A
  matrícula feita pelo pagamento guarda `transacao` = transação externa, como já faz a manual.

Chave nova no `.env` dos ambientes: `PAGAMENTOS_CHAVE` (32 bytes em base64; `gerar-env.sh` e
`gerar-env-servidor.sh` geram, `e2e.env` traz uma fixa, `.env.example` documenta). Sem a chave, a API
sobe, mas a tela de gateways avisa e não salva segredos; chave fora de Base64 ou sem 32 bytes impede a
subida. `PAGAMENTOS_RECONCILIACAO` (padrão `false`) liga a reconciliação nos servidores. Propriedades
em `app.pagamentos` (`PagamentosProperties`): `chave`, `webhook-tamanho-maximo` (256KB),
`webhook-requisicoes-por-ip-por-minuto` (600), `retencao-payload` (P180D), `disputa-suspende` (false),
`atraso-suspende-apos` (sem valor: nunca) e `reconciliacao` (`ativa`, `pendente-apos` PT24H,
`sem-evento-apos` P3D, `janela` P60D).

#### 1.3 Status do pedido e o que acontece com a matrícula

Status interno, igual para todos os meios de pagamento:

| Status do pedido | Hotmart (evento / `purchase.status`) | Matrícula |
|---|---|---|
| `CRIADO` | pedido aberto, aluno ainda não pagou | nada |
| `AGUARDANDO_PAGAMENTO` | `PURCHASE_BILLET_PRINTED`, `WAITING_PAYMENT`, `PRINTED_BILLET`, `STARTED`, `UNDER_ANALISYS`, `PROCESSING_TRANSACTION` | nada |
| `APROVADO` | `PURCHASE_APPROVED` / `APPROVED` | cria (ou reativa) a matrícula na turma da oferta |
| `CONCLUIDO` | `PURCHASE_COMPLETE` / `COMPLETE` | mantém |
| `EM_DISPUTA` | `PURCHASE_PROTEST` / `DISPUTE` | mantém e avisa o admin (configurável: suspender) |
| `ATRASADO` | `PURCHASE_DELAYED` / `OVERDUE` | mantém (configurável: suspender após N dias) |
| `REEMBOLSADO` | `PURCHASE_REFUNDED` / `REFUNDED`, `PARTIALLY_REFUNDED` | **cancela** a matrícula |
| `CHARGEBACK` | `PURCHASE_CHARGEBACK` / `CHARGEBACK` | **cancela** a matrícula e avisa o admin |
| `CANCELADO` | `PURCHASE_CANCELED` / `CANCELLED`, `BLOCKED`, `NO_FUNDS` | cancela, se existia |
| `EXPIRADO` | `PURCHASE_EXPIRED` / `EXPIRED` | nada (nunca foi pago) |

Regras:

- **Transições permitidas** (para ← de): `AGUARDANDO_PAGAMENTO` ← `CRIADO`; `APROVADO` ← `CRIADO`,
  `AGUARDANDO_PAGAMENTO`, `EXPIRADO`, `ATRASADO`, `EM_DISPUTA`; `CONCLUIDO` ← `CRIADO`,
  `AGUARDANDO_PAGAMENTO`, `APROVADO`, `ATRASADO`, `EM_DISPUTA`; `EM_DISPUTA` ← `APROVADO`, `CONCLUIDO`,
  `ATRASADO`; `ATRASADO` ← `APROVADO`, `CONCLUIDO`; `REEMBOLSADO` ← todos menos `CHARGEBACK`;
  `CHARGEBACK` ← todos; `CANCELADO` ← `CRIADO`, `AGUARDANDO_PAGAMENTO`, `APROVADO`, `CONCLUIDO`,
  `EM_DISPUTA`, `ATRASADO`; `EXPIRADO` ← `CRIADO`, `AGUARDANDO_PAGAMENTO`. O mesmo status não muda nada.
  `CONCLUIDO` também concede (a aprovação pode ter se perdido; conceder é idempotente).
- **Fora de ordem**: a Hotmart pode mandar eventos fora de ordem e reenviar antigos. Não deixe um evento
  mais antigo (`ocorrido_em` menor que o da última transição) desfazer um mais novo, e use precedência
  (ex.: `APROVADO` depois de `REEMBOLSADO` não reabre o acesso; um `REEMBOLSADO` sempre vale). A tabela de
  transições permitidas fica num lugar só (`TransicoesPedido`), com teste para cada linha.
- **Matrícula** (`MatriculaPagamentoService` no módulo `matricula`, idempotente): período `inicio` =
  aprovação (o `ocorrido_em`, nunca no futuro); `fim` = `inicio + dias_acesso` (ou sem prazo).
  `formaPagamento` vem de `payment.type` (`CREDIT_CARD` → `CARTAO`, `PIX` → `PIX`, `BILLET` → `BOLETO`,
  outros → `OUTRO`). A matrícula do pedido ainda ativa não muda; se o aluno já tem matrícula ativa na
  turma, estende o período (a partir do fim atual, ou da aprovação se já venceu) em vez de duplicar; a
  matrícula do pedido cancelada (disputa resolvida) é reativada com o mesmo período. Transação já
  lançada à mão pelo ADM fica fora da matrícula nova (o índice único não deixa repetir).
- **Suspensões configuráveis**: com `disputa-suspende`, `EM_DISPUTA` cancela a matrícula e a aprovação
  depois a reativa; com `atraso-suspende-apos`, um job diário cancela a matrícula dos pedidos `ATRASADO`
  há mais que o prazo (registra no histórico; o pedido continua `ATRASADO`).
- **Conta do comprador**: se o e-mail não tem conta, cria um usuário `ALUNO` **sem senha** e com o
  e-mail confirmado (o pagamento confirmou o e-mail), e envia "Sua matrícula foi confirmada" com o link
  de criar senha (token de `RECUPERACAO_SENHA`, mesma validade, link
  `{FRONT_ALUNO_URL}/redefinir-senha?token=...&origem=pagamento`, emitido pelo `RecuperacaoSenhaService`).
  Se a conta já tem senha, envia só a confirmação (com o link do curso). O e-mail sai quando o acesso foi
  aberto, reaberto ou estendido agora (nunca duas vezes para o mesmo evento). Nunca senha por e-mail.
- **Avisos** (e-mail para `ALERTA_EMAIL`, como o worker da fase 10): chargeback, disputa e evento em `ERRO`.

#### 1.4 Receber a notificação: `POST /api/pagamentos/webhooks/{webhookToken}`

O `webhookToken` identifica o gateway (a URL que o admin cola na Hotmart aparece pronta no painel) e não
substitui a autenticação do meio de pagamento.

1. Limite de tamanho (256 KB: `Content-Length` e leitura do corpo cru, 413) e de requisições por IP
   (filtro próprio antes da segurança, 600 por minuto: o meio de pagamento manda rajadas do mesmo IP e
   um 429 o faria reenviar).
2. Acha o gateway pelo token (não achou: 404). Chama `autenticar` (Hotmart: `X-HOTMART-HOTTOK` contra o
   hottok salvo, em tempo constante). Falhou: **401** e log de aviso, sem gravar nada.
3. `interpretar` e grava cada evento em `eventos_pagamento` com `INSERT ... ON CONFLICT DO NOTHING`. Já
   existia: responde **200** (`duplicado`). Corpo que não interpreta (JSON inválido, sem `id`): grava como
   `ERRO`, tipo `INVALIDO`, id externo `invalido:<sha256 do corpo>`, com o corpo cru, avisa o admin e
   responde **200** (reenviar não resolve; o admin vê no painel). A resposta é
   `{"resultado": "recebido" | "duplicado" | "erro"}`.
4. Enfileira o job `pagamento.processar-evento` (`FilaService`, na mesma transação da gravação) e
   responde **200** na hora. Só responde
   **500** se não conseguiu gravar (banco fora): aí o reenvio da Hotmart ajuda.

O processamento (worker), numa transação por evento e com trava no pedido (`SELECT ... FOR UPDATE`):

1. Acha o pedido: pelo `codigoPedido` (`xcod`), se a transação dele for a mesma (ou ainda não houver);
   senão pela transação já conhecida; senão cria um pedido
   `EXTERNO` a partir da oferta (`produto_externo` + `oferta_externa`) e do e-mail do comprador (venda por
   afiliado ou por link direto da Hotmart). Oferta não cadastrada: evento `IGNORADO`, motivo
   "Oferta não cadastrada (produto X, oferta Y)."; sem o e-mail do comprador, `IGNORADO`. Evento que não
   muda o pedido (status interno nulo): `IGNORADO`; transição não permitida ou evento antigo:
   `IGNORADO` com o motivo, ligado ao pedido.
2. Aplica a transição (1.3), grava o histórico, executa o efeito na matrícula e nos e-mails, marca o
   evento `PROCESSADO`. Exceção: o job falha e a fila tenta de novo com backoff; esgotou, evento `ERRO` e
   aviso ao admin.

#### 1.5 Comprar: checkout

- `GET /api/cursos/{slug}` (catálogo público) passa a trazer `ofertas`: as ativas da turma atual no
  gateway ativo (id, título, `precoCentavos`, moeda), da mais barata à mais cara. O módulo `curso` não
  conhece pagamento: declara a porta `OfertasCatalogo` em `curso/application`, implementada pelo módulo
  `pagamento`. O site gera o botão "Comprar" a partir disso.
- `POST /api/pagamentos/checkout` `{ ofertaId }` (aluno logado; sem login, o front manda para entrar ou
  criar conta e volta). Oferta inativa ou inexistente 404; gateway da oferta inativo 409 "Esta oferta não
  está à venda no momento."; turma que não está ativa ou curso não publicado 409. Cria o pedido `CRIADO`
  com o e-mail e o nome da conta (histórico "Checkout iniciado") e chama `iniciarCheckout`. Resposta
  201 `{ pedido, tipo: "REDIRECIONAMENTO" | "EMBUTIDO", url, dados }`.
  - **Hotmart**: `Redirecionamento` para
    `{urlCheckoutBase}/{codigo_checkout ou produto}?off={oferta}&email={email}&name={nome}&xcod={pedido.codigo}&sck=plataforma`
    (caminho com `UriUtils.encodePathSegment`, parâmetros com `URLEncoder`; sem nome, sem `name`;
    confira na documentação de parâmetros do checkout os nomes aceitos para preencher e-mail e nome).
  - Na Hotmart, a "página de obrigado" do produto aponta para
    `{FRONT_ALUNO_URL}/pagamento/retorno?pedido={codigo}` (documente no `docs/pagamentos.md`).
- `GET /api/pagamentos/pedidos/{codigo}`: situação do pedido para a página de retorno (status, curso,
  turma; nada de dados pessoais). Só o dono do pedido logado vê; sem login, só o status.

#### 1.6 Reconciliação (deixar pronta, ligada por configuração)

Job diário `pagamento.reconciliar` para gateways com `CONSULTA_API`: pedidos `CRIADO` ou
`AGUARDANDO_PAGAMENTO` há mais de 24 h e transações sem evento há mais de 3 dias são consultados
(`consultar`). Se o status mudou, gera um `EventoPagamento` sintético (`idExterno` = `reconciliacao:...`)
pelo mesmo caminho do webhook. Hotmart: OAuth *client credentials* com o token em cache até 5 minutos
antes de vencer (cabeçalho `Basic` aceito com ou sem o prefixo), e histórico de vendas filtrado por
`transaction` — primeiro sem status, depois status a status (`REFUNDED`, `CHARGEBACK`, `CANCELLED`,
`EXPIRED`, `DISPUTE`, `OVERDUE`, `WAITING_PAYMENT`, `PRINTED_BILLET`) até achar; `tracking.external_code`
é o `xcod`. Só pedidos com transação, até 200 por consulta, sem mudança há menos de 60 dias. Desligado
por padrão (`app.pagamentos.reconciliacao.ativa`), para não depender das credenciais da API no primeiro
momento. Os agendamentos (reconciliação 5h, suspensão por atraso 5h15, retenção domingo 4h45, fuso de
São Paulo) rodam só no worker.

#### 1.7 Painel (API do ADM, `ADMIN`)

- `GET/POST /api/adm/pagamentos/gateways`, `GET/PUT .../{id}`, `POST .../{id}/teste`,
  `POST .../{id}/ativacao` (desativa os outros antes, numa atualização em lote, por causa do índice
  único) e `DELETE .../{id}/ativacao`. `GET /api/adm/pagamentos/tipos` devolve
  `{ cifraDisponivel, tipos: [{ tipo, nome, campos, capacidades }] }`. A resposta do gateway traz
  `valores` (comuns), `segredos` (por chave: `configurado` e `final4`), `segredosLegiveis` (falso se a
  chave falta ou mudou) e `webhookUrl` (`{FRONT_ALUNO_URL}/api/pagamentos/webhooks/{token}`). **Segredos
  nunca voltam**; campo secreto vazio no PUT mantém o valor atual.
- `GET/POST /api/adm/turmas/{id}/ofertas` (sem `gatewayId`, no gateway ativo; sem nenhum ativo, 409),
  `PUT /api/adm/ofertas/{id}` (o gateway não muda), `PATCH /api/adm/ofertas/{id}/status` `{ ativa }`.
  Produto + oferta repetidos no mesmo gateway: 409.
- `GET /api/adm/pagamentos/pedidos` (filtros: status, gateway, e-mail, período), `GET .../{id}` com
  histórico e eventos.
- `GET /api/adm/pagamentos/eventos` (filtro por status), `GET .../{id}` (com o `payload`) e
  `POST .../{id}/reprocessamento` para eventos em `ERRO` ou `IGNORADO` (ex.: a oferta foi cadastrada
  depois); outros status 409; sem `dados` (não interpretado ou apagado pela retenção) 409.
- Avisos (`AvisosPagamento`, mesmo `ALERTA_EMAIL` e template `alerta-operacao` dos alertas do worker):
  chargeback, disputa e evento em `ERRO` (corpo inválido ou desistência do job).
- Retenção: eventos `PROCESSADO`/`IGNORADO` com mais de 180 dias têm o `payload` e os `dados` apagados
  por um job semanal (o resto da linha fica para auditoria).
- Rotas públicas no `SecurityConfig`: `POST /api/pagamentos/webhooks/*` e `GET /api/pagamentos/pedidos/*`.

#### 1.8 Infra

- Na homologação (restrita por IP no Caddy), o snippet `(homolog)` deixa passar só o caminho
  `/api/pagamentos/webhooks/*`, para a Hotmart de teste notificar.
- `compose.prod.yml` e `compose.yml` repassam `PAGAMENTOS_CHAVE` (e o prod, `PAGAMENTOS_RECONCILIACAO`
  como `APP_PAGAMENTOS_RECONCILIACAO_ATIVA`); no `compose.e2e.yml`, a API usa
  `APP_PAGAMENTOS_HOTMART_URL_TOKEN=http://mailpit:8025/hotmart-indisponivel` (o "Testar" recebe uma
  recusa sem sair para a internet). No `application-test.yaml`, chave de teste e `url-token` em
  `http://localhost:1/...`.

### 2. Frontend

**ADM** (menu "Pagamentos"):

- **Meios de pagamento**: lista com o ativo destacado; formulário gerado a partir de `camposConfiguracao`
  (campos secretos como senha, com "configurado ••••1234"); botão "Testar credenciais"; "Ativar"; a URL do
  webhook pronta para copiar, com o passo a passo da Hotmart (versão 2.0.0, eventos a marcar).
- **Turma → aba "Venda"**: ofertas da turma (produto e oferta da Hotmart, título, preço, dias de acesso,
  ativa).
- **Pedidos**: tabela paginada com filtros; detalhe com a linha do tempo (histórico) e os eventos recebidos.
- **Eventos recebidos**: filtro por status, corpo do evento (formatado) e "Reprocessar".

**Aluno**:

- `/comprar/:ofertaId`: exige login (a entrada repassa o `voltar` para o cadastro e o cadastro de volta
  para a entrada), chama o checkout uma vez por visita e redireciona (ou mostra o aviso do formulário
  embutido, para um futuro meio de pagamento que use).
- `/pagamento/retorno?pedido=` (ou `?xcod=`, como a Hotmart repassa): consulta o pedido a cada 3 s por
  até 2 minutos: "Pagamento confirmado" (botão para o curso), "Aguardando pagamento" (boleto/PIX: "você
  recebe um e-mail quando confirmar") ou "Pagamento não aprovado".
- A página de definir senha reaproveita a de redefinir (texto "Crie sua senha" quando vier do pagamento).

**Site**: na página do curso, o preço e o botão "Comprar" de cada oferta ativa (link para
`{aluno}/comprar/{ofertaId}`) e as ofertas nos dados estruturados (`Offer`); sem oferta, continua
"Quero fazer este curso".

### 3. Testes

- **Unitários**: `TransicoesPedido` (todas as linhas da tabela e todas as combinações, fora de ordem,
  precedência do reembolso, efeitos, disputa que suspende); interpretação de cada fixture da Hotmart (campos opcionais ausentes, status desconhecido,
  data em número e em texto); hottok em tempo constante; montagem da URL do checkout (codificação);
  cifra dos segredos (ida e volta, IV diferente a cada gravação, chave errada falha); validação da
  configuração; cliente da API da Hotmart com `MockRestServiceServer` (token em cache, recusa, histórico);
  processamento do evento e reconciliação com dependências simuladas; matrícula pelo pagamento
  (criar, estender, reativar, cancelar); DTOs. No front (Vitest): validação do formulário do gateway,
  formatação do corpo e da moeda, e a página de retorno (consulta a cada 3 s, para em 2 minutos, `xcod`).
- **Integração** (Testcontainers): webhook com hottok certo grava e enfileira; mesmo `id` duas vezes
  processa uma vez; hottok errado 401 sem gravar; token de URL inexistente 404; oferta desconhecida 200 e
  `IGNORADO`; aprovado cria conta sem senha, matrícula e e-mail com link de criar senha; reembolso e
  chargeback cancelam a matrícula; aprovado depois de reembolsado não reabre; venda sem `xcod` cria pedido
  `EXTERNO`; dois eventos do mesmo pedido ao mesmo tempo (trava); segredos nunca aparecem nas respostas
  do painel; matriz de papéis das rotas novas.
- **e2e (Java, stack completa)**: o admin configura o gateway e a oferta pela API; o teste manda o
  `PURCHASE_APPROVED` da fixture (com o `xcod` do pedido criado no checkout) para o webhook pelo Caddy;
  o aluno passa a ver o curso; o `PURCHASE_REFUNDED` tira o acesso; o e-mail de confirmação chega no
  Mailpit.
- **Playwright** (caminho feliz e infeliz): admin cadastra a Hotmart (validações, "Testar" com credencial
  errada), ativa e cria a oferta; no site aparece o preço; aluno clica em "Comprar", o teste intercepta a
  ida a `pay.hotmart.com` (`page.route`) e confere `xcod`, `off` e e-mail na URL; o teste dispara o
  webhook; a página de retorno mostra "Pagamento confirmado" e o curso aparece em Meus cursos; boleto
  (`PURCHASE_BILLET_PRINTED`) mostra "Aguardando pagamento"; reembolso tira o curso; pedido e eventos
  aparecem no painel e um evento `IGNORADO` é reprocessado depois de cadastrar a oferta.

### 4. Documentação

- `docs/pagamentos.md`: como configurar a Hotmart (webhook 2.0.0 com a URL do painel, eventos a marcar,
  página de obrigado, credenciais da API), a tabela de status, o que fazer com evento em `ERRO`, e
  **como adicionar um meio de pagamento novo**:
  1. valor novo em `TipoGateway`;
  2. bean que implementa `GatewayPagamento` (campos de configuração, checkout, autenticação,
     interpretação e mapeamento de status para o status interno);
  3. fixtures das notificações reais e testes de interpretação;
  4. nada no núcleo, no painel ou no front muda (o formulário vem de `camposConfiguracao`; o checkout
     embutido já é tratado).
- `docs/plano-implementacao.md`: Fase 16 marcada; `docs/vps.md`: `PAGAMENTOS_CHAVE` no `.env` dos
  ambientes (inclusive nos que já existem), a atualização do Caddy e a URL do webhook da homologação na
  Hotmart de teste; `docs/operacao.md`: os avisos de pagamento em `ALERTA_EMAIL`.

## Verificação

- `./mvnw verify` (unitários, integração, cobertura, Spotless) e as jornadas `-Pe2e` passando.
- `npm run format:check`, `lint`, `typecheck`, `test`, `build` e `npm run e2e` no `web/`.
- Na homologação, com a Hotmart de teste: "Enviar teste" de `PURCHASE_APPROVED`, `PURCHASE_REFUNDED`,
  `PURCHASE_CHARGEBACK` e `PURCHASE_BILLET_PRINTED` aparece em "Eventos recebidos" e muda o pedido como na
  tabela; o histórico de entregas na Hotmart mostra 200 em todos.
- O mesmo evento reenviado pela Hotmart (botão de reenvio) não gera matrícula nem e-mail duplicados.
- Nenhum segredo (hottok, client secret, basic) aparece em resposta da API, log ou no banco em texto puro.
