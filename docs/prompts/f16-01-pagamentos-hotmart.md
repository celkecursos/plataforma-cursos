# Pagamentos: estrutura multi-gateway e integração com a Hotmart

**Fase:** 16
**Branch:** `feature/pagamentos`

> A especificação abaixo explica as decisões; o código exato está na seção **Arquivos**. Antes de
> gravar, confira a seção **0. Confirmar a documentação da Hotmart**.

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

## Arquivos

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### Arquivos alterados (substitua o conteúdo inteiro)

`backend/src/main/java/br/com/celke/cursos/auth/application/AuthService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login (com bloqueio por falhas seguidas e auditoria), renovação e saída. */
@Service
public class AuthService {

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final TokenAcessoService tokenAcessoService;
    private final RefreshTokenService refreshTokenService;
    private final BloqueioLoginService bloqueioLoginService;
    private final AuditoriaLoginService auditoriaLoginService;

    /**
     * Hash de uma senha aleatória: conferido quando o e-mail não existe, para o login levar o mesmo
     * tempo nos dois casos e não revelar quais e-mails estão cadastrados.
     */
    private final String hashFicticio;

    public AuthService(
            UsuarioService usuarioService,
            PasswordEncoder passwordEncoder,
            TokenAcessoService tokenAcessoService,
            RefreshTokenService refreshTokenService,
            BloqueioLoginService bloqueioLoginService,
            AuditoriaLoginService auditoriaLoginService) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.tokenAcessoService = tokenAcessoService;
        this.refreshTokenService = refreshTokenService;
        this.bloqueioLoginService = bloqueioLoginService;
        this.auditoriaLoginService = auditoriaLoginService;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Confere o bloqueio antes de tudo (sem calcular hash), depois a senha. Falha e sucesso
     * atualizam os contadores de bloqueio e ficam na auditoria. Toda falha recebe a mesma mensagem;
     * só quem acertou a senha fica sabendo que falta confirmar o e-mail.
     */
    @Transactional
    public Sessao entrar(String email, String senha, OrigemRequisicao origem) {
        try {
            bloqueioLoginService.verificar(email, origem.ip());
        } catch (MuitasTentativasException e) {
            auditoriaLoginService.registrar(email, null, ResultadoLogin.BLOQUEADO, origem);
            throw e;
        }
        Optional<Usuario> encontrado = usuarioService.buscarPorEmail(email);
        // Conta sem senha (criada por uma compra) confere o hash fictício e falha como senha errada
        boolean possuiSenha = encontrado.filter(Usuario::possuiSenha).isPresent();
        String hash = possuiSenha ? encontrado.get().getSenhaHash() : hashFicticio;
        boolean senhaConfere = passwordEncoder.matches(senha, hash) && possuiSenha;
        ResultadoLogin resultado = resultado(encontrado, senhaConfere);
        auditoriaLoginService.registrar(email, encontrado.orElse(null), resultado, origem);
        if (resultado == ResultadoLogin.EMAIL_NAO_CONFIRMADO) {
            // Quem chegou aqui sabe a senha: pode saber que falta confirmar o e-mail
            bloqueioLoginService.registrarSucesso(email, origem.ip());
            throw new EmailNaoConfirmadoException();
        }
        if (resultado != ResultadoLogin.SUCESSO) {
            bloqueioLoginService.registrarFalha(email, origem.ip());
            throw new CredenciaisInvalidasException();
        }
        bloqueioLoginService.registrarSucesso(email, origem.ip());
        Usuario usuario = encontrado.get();
        usuarioService.atualizarHashSeNecessario(usuario, senha);
        return abrirSessao(usuario, refreshTokenService.emitir(usuario.getId()));
    }

    private static ResultadoLogin resultado(Optional<Usuario> encontrado, boolean senhaConfere) {
        if (encontrado.isEmpty()) {
            return ResultadoLogin.EMAIL_DESCONHECIDO;
        }
        if (!senhaConfere) {
            return ResultadoLogin.SENHA_INVALIDA;
        }
        if (!encontrado.get().isAtivo()) {
            return ResultadoLogin.USUARIO_INATIVO;
        }
        return encontrado.get().isEmailConfirmado() ? ResultadoLogin.SUCESSO : ResultadoLogin.EMAIL_NAO_CONFIRMADO;
    }

    /**
     * Troca o refresh token por um novo e emite outro access token. Sem transação própria: a
     * revogação de uma família por reutilização precisa ficar gravada mesmo com a sessão recusada.
     */
    public Sessao renovar(String refreshToken) {
        RefreshEmitido novo = refreshTokenService.rotacionar(refreshToken).orElseThrow(SessaoInvalidaException::new);
        Usuario usuario = usuarioService
                .buscarPorId(novo.usuarioId())
                .filter(Usuario::isAtivo)
                .orElseThrow(SessaoInvalidaException::new);
        return abrirSessao(usuario, novo);
    }

    public void sair(String refreshToken) {
        refreshTokenService.revogarFamiliaDoToken(refreshToken);
    }

    public void sairDeTodos(Long usuarioId) {
        refreshTokenService.revogarTodas(usuarioId);
    }

    private Sessao abrirSessao(Usuario usuario, RefreshEmitido refresh) {
        return new Sessao(usuario, tokenAcessoService.emitir(usuario), refresh);
    }

    public record Sessao(Usuario usuario, TokenAcesso acesso, RefreshEmitido refresh) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/RecuperacaoSenhaService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperação de senha por link enviado ao e-mail. A resposta ao cliente é a mesma exista ou não a
 * conta. Ao redefinir, todas as sessões abertas são encerradas.
 */
@Service
public class RecuperacaoSenhaService {

    private final UsuarioService usuarioService;
    private final TokenUsoUnicoService tokens;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final ContaProperties props;

    public RecuperacaoSenhaService(
            UsuarioService usuarioService,
            TokenUsoUnicoService tokens,
            EmailService emailService,
            RefreshTokenService refreshTokenService,
            ContaProperties props) {
        this.usuarioService = usuarioService;
        this.tokens = tokens;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
        this.props = props;
    }

    @Transactional
    public void solicitar(String email) {
        usuarioService.buscarPorEmail(email).filter(Usuario::isAtivo).ifPresent(this::enviarLink);
    }

    /**
     * Troca a senha pelo link. Também confirma o e-mail (o link chegou à caixa do dono) e encerra
     * todas as sessões, inclusive a de quem tiver descoberto a senha antiga.
     */
    @Transactional
    public void redefinir(String token, String novaSenha) {
        Long usuarioId = tokens.consumir(token, TipoToken.RECUPERACAO_SENHA).orElseThrow(LinkInvalidoException::new);
        Usuario usuario =
                usuarioService.buscarPorId(usuarioId).filter(Usuario::isAtivo).orElseThrow(LinkInvalidoException::new);
        usuarioService.trocarSenha(usuario, novaSenha);
        usuarioService.confirmarEmail(usuario);
        refreshTokenService.revogarTodas(usuario.getId());
    }

    /**
     * Link de "criar senha" para a conta criada por uma compra (o mesmo token da recuperação, com a
     * mesma validade). Vazio se a conta já recebeu o máximo de links na última hora.
     */
    @Transactional
    public Optional<LinkSenha> linkCriarSenha(Long usuarioId) {
        return tokens.emitir(usuarioId, TipoToken.RECUPERACAO_SENHA)
                .map(token -> new LinkSenha(
                        props.urlAluno() + "/redefinir-senha?token=" + token + "&origem=pagamento",
                        Validade.texto(tokens.validade(TipoToken.RECUPERACAO_SENHA))));
    }

    /** Link de criar senha e a validade escrita para o e-mail ("1 hora"). */
    public record LinkSenha(String url, String validade) {}

    private void enviarLink(Usuario usuario) {
        tokens.emitir(usuario.getId(), TipoToken.RECUPERACAO_SENHA)
                .ifPresent(token -> emailService.enviar(
                        usuario.getEmail(),
                        "Crie uma nova senha",
                        "recuperacao-senha",
                        Map.of(
                                "nome", usuario.getNome(),
                                "link", props.urlAluno() + "/redefinir-senha?token=" + token,
                                "validade", Validade.texto(tokens.validade(TipoToken.RECUPERACAO_SENHA)))));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/api/CursoResponses.java`:

`````java
package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.application.CapaCursoService.EnvioCapa;
import br.com.celke.cursos.curso.application.CatalogoService.CursoDetalhe;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.application.OfertasCatalogo.OfertaCatalogo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Respostas das rotas de cursos: gestão ({@code /api/adm}) e catálogo público ({@code /api/cursos}). */
public final class CursoResponses {

    private CursoResponses() {}

    /** Curso na gestão; {@code capaUrl} é temporária (vale uma hora). */
    public record CursoAdm(
            Long id,
            String titulo,
            String slug,
            String resumo,
            String descricao,
            StatusCurso status,
            String capaUrl,
            Instant criadoEm,
            Instant atualizadoEm) {

        public static CursoAdm de(br.com.celke.cursos.curso.domain.Curso curso, Optional<URL> capa) {
            return new CursoAdm(
                    curso.getId(),
                    curso.getTitulo(),
                    curso.getSlug(),
                    curso.getResumo(),
                    curso.getDescricao(),
                    curso.getStatus(),
                    capa.map(URL::toString).orElse(null),
                    curso.getCriadoEm(),
                    curso.getAtualizadoEm());
        }
    }

    public record Turma(
            Long id,
            Long cursoId,
            String nome,
            StatusTurma status,
            int percentualConclusao,
            Integer cargaHorariaHoras) {

        public static Turma de(br.com.celke.cursos.curso.domain.Turma turma) {
            return new Turma(
                    turma.getId(),
                    turma.getCurso().getId(),
                    turma.getNome(),
                    turma.getStatus(),
                    turma.getPercentualConclusao(),
                    turma.getCargaHorariaHoras());
        }
    }

    public record Modulo(Long id, Long turmaId, String titulo, int ordem, StatusConteudo status) {

        public static Modulo de(br.com.celke.cursos.curso.domain.Modulo modulo) {
            return new Modulo(
                    modulo.getId(),
                    modulo.getTurma().getId(),
                    modulo.getTitulo(),
                    modulo.getOrdem(),
                    modulo.getStatus());
        }
    }

    public record Aula(
            Long id,
            Long moduloId,
            String titulo,
            String descricao,
            int ordem,
            boolean gratuita,
            StatusConteudo status) {

        public static Aula de(br.com.celke.cursos.curso.domain.Aula aula) {
            return new Aula(
                    aula.getId(),
                    aula.getModulo().getId(),
                    aula.getTitulo(),
                    aula.getDescricao(),
                    aula.getOrdem(),
                    aula.isGratuita(),
                    aula.getStatus());
        }
    }

    /** Onde e como enviar a imagem: PUT na {@code url} com o cabeçalho {@code Content-Type} informado. */
    public record EnvioCapaResponse(String chave, String url, String contentType, Instant expiraEm) {

        public static EnvioCapaResponse de(EnvioCapa envio) {
            return new EnvioCapaResponse(envio.chave(), envio.url().toString(), envio.contentType(), envio.expiraEm());
        }
    }

    /**
     * Curso no catálogo. {@code capaUrl} é um endereço estável da API (relativo), que redireciona para
     * a imagem: pode ir para HTML em cache ou pré-renderizado sem vencer.
     */
    public record CursoCatalogo(String slug, String titulo, String resumo, String capaUrl) {

        public static CursoCatalogo de(br.com.celke.cursos.curso.domain.Curso curso) {
            return new CursoCatalogo(curso.getSlug(), curso.getTitulo(), curso.getResumo(), enderecoCapa(curso));
        }
    }

    /** Detalhe do curso; {@code ofertas} são as opções à venda (vazia: nada à venda agora). */
    public record CursoCatalogoDetalhe(
            String slug,
            String titulo,
            String resumo,
            String descricao,
            String capaUrl,
            List<ModuloCatalogo> modulos,
            List<OfertaCatalogo> ofertas) {

        public static CursoCatalogoDetalhe de(CursoDetalhe detalhe) {
            var curso = detalhe.curso();
            return new CursoCatalogoDetalhe(
                    curso.getSlug(),
                    curso.getTitulo(),
                    curso.getResumo(),
                    curso.getDescricao(),
                    enderecoCapa(curso),
                    detalhe.modulos().stream().map(ModuloCatalogo::de).toList(),
                    detalhe.ofertas());
        }
    }

    public record ModuloCatalogo(String titulo, List<AulaCatalogo> aulas) {

        static ModuloCatalogo de(ModuloConteudo modulo) {
            return new ModuloCatalogo(
                    modulo.titulo(),
                    modulo.aulas().stream().map(AulaCatalogo::de).toList());
        }
    }

    public record AulaCatalogo(Long id, String titulo, boolean gratuita) {

        static AulaCatalogo de(AulaConteudo aula) {
            return new AulaCatalogo(aula.id(), aula.titulo(), aula.gratuita());
        }
    }

    private static String enderecoCapa(br.com.celke.cursos.curso.domain.Curso curso) {
        return curso.getCapaChave() == null ? null : "/api/cursos/%s/capa".formatted(curso.getSlug());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/application/CatalogoService.java`:

`````java
package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.application.OfertasCatalogo.OfertaCatalogo;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo público: só cursos publicados e, no detalhe, só o conteúdo ativo da turma ativa mais
 * recente. Curso em rascunho ou arquivado responde 404, como se não existisse.
 */
@Service
public class CatalogoService {

    private final CursoRepository cursos;
    private final TurmaRepository turmas;
    private final ConteudoTurmaService conteudoTurmaService;
    private final OfertasCatalogo ofertasCatalogo;

    public CatalogoService(
            CursoRepository cursos,
            TurmaRepository turmas,
            ConteudoTurmaService conteudoTurmaService,
            OfertasCatalogo ofertasCatalogo) {
        this.cursos = cursos;
        this.turmas = turmas;
        this.conteudoTurmaService = conteudoTurmaService;
        this.ofertasCatalogo = ofertasCatalogo;
    }

    @Transactional(readOnly = true)
    public Page<Curso> listar(Pageable pagina) {
        Pageable ordenada = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by("titulo", "id"));
        return cursos.findByStatus(StatusCurso.PUBLICADO, ordenada);
    }

    @Transactional(readOnly = true)
    public Curso buscar(String slug) {
        return cursos.findBySlugAndStatus(slug, StatusCurso.PUBLICADO)
                .orElseThrow(() -> new NaoEncontradoException("Curso não encontrado."));
    }

    @Transactional(readOnly = true)
    public CursoDetalhe detalhar(String slug) {
        Curso curso = buscar(slug);
        return turmas.findFirstByCursoIdAndStatusOrderByIdDesc(curso.getId(), StatusTurma.ATIVA)
                .map(turma -> new CursoDetalhe(
                        curso,
                        conteudoTurmaService.conteudoAtivo(turma.getId()),
                        ofertasCatalogo.ativas(turma.getId())))
                .orElse(new CursoDetalhe(curso, List.of(), List.of()));
    }

    /** Curso com o conteúdo e as ofertas à venda da turma ativa mais recente. */
    public record CursoDetalhe(Curso curso, List<ModuloConteudo> modulos, List<OfertaCatalogo> ofertas) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/domain/FormaPagamento.java`:

`````java
package br.com.celke.cursos.matricula.domain;

/**
 * Como a matrícula foi paga. {@code MANUAL} e {@code CORTESIA} são lançadas pelo ADM; {@code OUTRO}
 * cobre os meios sem valor próprio (PayPal, Google Pay...).
 */
public enum FormaPagamento {
    MANUAL,
    CORTESIA,
    PIX,
    CARTAO,
    BOLETO,
    OUTRO
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/domain/Matricula.java`:

`````java
package br.com.celke.cursos.matricula.domain;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * Matrícula de um usuário numa turma. Guarda só os ids do usuário e da turma: os dados deles vêm dos
 * serviços dos módulos {@code usuario} e {@code curso}.
 */
@Entity
@Table(name = "matriculas")
public class Matricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "turma_id", nullable = false)
    private Long turmaId;

    @Column(nullable = false)
    private Instant inicio;

    /** Nulo: acesso sem prazo. */
    private Instant fim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusMatricula status;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 20)
    private FormaPagamento formaPagamento;

    @Column(length = 100)
    private String transacao;

    /** Pedido que gerou a matrícula; nulo nas lançadas pelo ADM. */
    @Column(name = "pedido_id")
    private Long pedidoId;

    @Column(name = "arquivada_em")
    private Instant arquivadaEm;

    @Column(name = "cancelada_em")
    private Instant canceladaEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Matricula() {}

    public Matricula(
            Long usuarioId,
            Long turmaId,
            Instant inicio,
            Instant fim,
            FormaPagamento formaPagamento,
            String transacao,
            Instant agora) {
        validarPeriodo(inicio, fim);
        this.usuarioId = usuarioId;
        this.turmaId = turmaId;
        this.inicio = inicio;
        this.fim = fim;
        this.status = StatusMatricula.ATIVA;
        this.formaPagamento = formaPagamento;
        this.transacao = transacao == null || transacao.isBlank() ? null : transacao.strip();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Matrícula gerada por um pagamento: guarda o pedido que a criou. */
    public static Matricula dePagamento(
            Long usuarioId,
            Long turmaId,
            Instant inicio,
            Instant fim,
            FormaPagamento formaPagamento,
            String transacao,
            Long pedidoId,
            Instant agora) {
        Matricula matricula = new Matricula(usuarioId, turmaId, inicio, fim, formaPagamento, transacao, agora);
        matricula.pedidoId = pedidoId;
        return matricula;
    }

    /** Situação no instante informado: o fim é inclusivo (vale até o último segundo). */
    public SituacaoMatricula situacao(Instant agora) {
        if (status == StatusMatricula.CANCELADA) {
            return SituacaoMatricula.CANCELADA;
        }
        if (agora.isBefore(inicio)) {
            return SituacaoMatricula.AGUARDANDO_INICIO;
        }
        if (fim != null && agora.isAfter(fim)) {
            return SituacaoMatricula.VENCIDA;
        }
        return SituacaoMatricula.VIGENTE;
    }

    public boolean isVigente(Instant agora) {
        return situacao(agora) == SituacaoMatricula.VIGENTE;
    }

    public void alterarPeriodo(Instant inicio, Instant fim, Instant agora) {
        exigirAtiva();
        validarPeriodo(inicio, fim);
        this.inicio = inicio;
        this.fim = fim;
        this.atualizadoEm = agora;
    }

    public void cancelar(Instant agora) {
        exigirAtiva();
        this.status = StatusMatricula.CANCELADA;
        this.canceladaEm = agora;
        this.atualizadoEm = agora;
    }

    /**
     * Devolve o acesso de uma matrícula cancelada por um pagamento (ex.: disputa resolvida a favor):
     * volta a valer com o mesmo período.
     */
    public void reativar(Instant agora) {
        if (status == StatusMatricula.ATIVA) {
            return;
        }
        this.status = StatusMatricula.ATIVA;
        this.canceladaEm = null;
        this.atualizadoEm = agora;
    }

    /**
     * Estende o acesso por mais {@code dias} a partir do fim atual (ou de {@code aPartirDe}, se já
     * passou). Sem prazo ({@code dias} nulo) ou matrícula que já não vence: fica sem prazo.
     */
    public void estender(Integer dias, Instant aPartirDe, Instant agora) {
        exigirAtiva();
        if (dias == null || fim == null) {
            this.fim = null;
        } else {
            Instant base = fim.isAfter(aPartirDe) ? fim : aPartirDe;
            this.fim = base.plus(Duration.ofDays(dias));
        }
        this.atualizadoEm = agora;
    }

    /** O aluno tira o curso da lista principal; o acesso continua o mesmo. */
    public void arquivar(Instant agora) {
        if (arquivadaEm == null) {
            this.arquivadaEm = agora;
            this.atualizadoEm = agora;
        }
    }

    public void desarquivar(Instant agora) {
        if (arquivadaEm != null) {
            this.arquivadaEm = null;
            this.atualizadoEm = agora;
        }
    }

    public boolean isArquivada() {
        return arquivadaEm != null;
    }

    private void exigirAtiva() {
        if (status == StatusMatricula.CANCELADA) {
            throw new ConflitoException("A matrícula está cancelada.");
        }
    }

    private static void validarPeriodo(Instant inicio, Instant fim) {
        if (fim != null && !fim.isAfter(inicio)) {
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST, "O fim da matrícula deve ser depois do início.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getTurmaId() {
        return turmaId;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFim() {
        return fim;
    }

    public StatusMatricula getStatus() {
        return status;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public String getTransacao() {
        return transacao;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public Instant getArquivadaEm() {
        return arquivadaEm;
    }

    public Instant getCanceladaEm() {
        return canceladaEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`:

`````java
package br.com.celke.cursos.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Segurança da API: tudo exige autenticação, exceto as rotas públicas declaradas aqui com o
 * método HTTP. A API é stateless: o access token (JWT) vem no cabeçalho {@code Authorization}, sem
 * sessão, formulário ou HTTP Basic. Só existe na aplicação web: o worker não atende HTTP.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfig {

    /** Claim do JWT com os papéis do usuário (ALUNO, ADMIN), convertidos em ROLE_ALUNO, ROLE_ADMIN. */
    public static final String CLAIM_PAPEIS = "papeis";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
        RespostasSeguranca respostas = new RespostasSeguranca(jsonMapper);
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 401 e 403 no formato ProblemDetail
                .exceptionHandling(
                        erros -> erros.authenticationEntryPoint(respostas).accessDeniedHandler(respostas))
                .oauth2ResourceServer(recurso -> recurso.jwt(jwt -> jwt.jwtAuthenticationConverter(conversorPapeis()))
                        .authenticationEntryPoint(respostas)
                        .accessDeniedHandler(respostas))
                .authorizeHttpRequests(rotas -> rotas
                        // Health check e probes (Docker e Caddy)
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // Documentação: só responde quando DOCS_ENABLED=true
                        .requestMatchers(
                                HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // Autenticação: login e renovação/saída pelo cookie de refresh
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                        .permitAll()
                        // Conta: cadastro, confirmação de e-mail e recuperação de senha
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/cadastro",
                                "/api/auth/confirmar-email",
                                "/api/auth/reenviar-confirmacao",
                                "/api/auth/recuperar-senha",
                                "/api/auth/redefinir-senha")
                        .permitAll()
                        // Catálogo: cursos publicados, detalhe e capa
                        .requestMatchers(HttpMethod.GET, "/api/cursos", "/api/cursos/*", "/api/cursos/*/capa")
                        .permitAll()
                        // Termos de uso e política de privacidade vigentes (páginas do site)
                        .requestMatchers(HttpMethod.GET, "/api/termos/vigentes")
                        .permitAll()
                        // Validação pública do certificado pelo código (página "validar certificado" do site)
                        .requestMatchers(HttpMethod.GET, "/api/certificados/*")
                        .permitAll()
                        // Notificações dos meios de pagamento: autenticadas pelo próprio meio (hottok...)
                        .requestMatchers(HttpMethod.POST, "/api/pagamentos/webhooks/*")
                        .permitAll()
                        // Situação do pedido na página de retorno (sem login, só o status)
                        .requestMatchers(HttpMethod.GET, "/api/pagamentos/pedidos/*")
                        .permitAll()
                        // Área do ADM: o papel é exigido aqui e repetido com @PreAuthorize nos controllers
                        .requestMatchers("/api/adm/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated());
        return http.build();
    }

    private static JwtAuthenticationConverter conversorPapeis() {
        JwtGrantedAuthoritiesConverter papeis = new JwtGrantedAuthoritiesConverter();
        papeis.setAuthoritiesClaimName(CLAIM_PAPEIS);
        papeis.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(papeis);
        return conversor;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/application/UsuarioService.java`:

`````java
package br.com.celke.cursos.usuario.application;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import br.com.celke.cursos.usuario.infra.UsuarioRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operações de usuário usadas pelos outros módulos (eles nunca acessam o repositório direto). */
@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Cria um usuário com o e-mail já confirmado: contas criadas por quem administra a plataforma
     * (comando {@code criar-admin}, seeds). A senha já deve ter passado pela {@code PoliticaSenha}.
     */
    @Transactional
    public Usuario criarConfirmado(String nome, String email, String senha, Set<Papel> papeis) {
        return criar(nome, email, senha, papeis, true);
    }

    /** Cadastro feito pelo próprio aluno: o e-mail fica pendente até ele confirmar. */
    @Transactional
    public Usuario cadastrarAluno(String nome, String email, String senha) {
        return criar(nome, email, senha, Set.of(Papel.ALUNO), false);
    }

    /**
     * Conta de aluno criada por uma compra: sem senha e com o e-mail confirmado (o pagamento chegou a
     * ele). O dono cria a senha pelo link enviado no e-mail da matrícula.
     */
    @Transactional
    public Usuario criarAlunoSemSenha(String nome, String email) {
        return criar(nome, email, null, Set.of(Papel.ALUNO), true);
    }

    private Usuario criar(String nome, String email, String senha, Set<Papel> papeis, boolean confirmado) {
        String emailNormalizado = Usuario.normalizarEmail(email);
        if (repository.existsByEmail(emailNormalizado)) {
            throw new ConflitoException("Já existe um usuário com este e-mail.");
        }
        String hash = senha == null ? null : passwordEncoder.encode(senha);
        return repository.save(new Usuario(nome, emailNormalizado, hash, papeis, confirmado, clock.instant()));
    }

    @Transactional
    public void confirmarEmail(Usuario usuario) {
        usuario.confirmarEmail(clock.instant());
        repository.save(usuario);
    }

    /** Troca a senha. A nova senha já deve ter passado pela {@code PoliticaSenha}. */
    @Transactional
    public void trocarSenha(Usuario usuario, String novaSenha) {
        usuario.trocarHashSenha(passwordEncoder.encode(novaSenha), clock.instant());
        repository.save(usuario);
    }

    /** A senha informada é a atual do usuário (troca de senha no perfil). */
    public boolean senhaConfere(Usuario usuario, String senha) {
        return senha != null && usuario.possuiSenha() && passwordEncoder.matches(senha, usuario.getSenhaHash());
    }

    @Transactional
    public Usuario renomear(Long usuarioId, String nome) {
        Usuario usuario = repository.findById(usuarioId).orElseThrow();
        usuario.renomear(nome, clock.instant());
        return repository.save(usuario);
    }

    /** Troca a foto de perfil (nula remove); devolve a chave da anterior. */
    @Transactional
    public Optional<String> trocarFoto(Long usuarioId, String chave) {
        Usuario usuario = repository.findById(usuarioId).orElseThrow();
        String anterior = usuario.trocarFoto(chave, clock.instant());
        repository.save(usuario);
        return Optional.ofNullable(anterior);
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorEmail(String email) {
        return repository.findByEmail(Usuario.normalizarEmail(email));
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorId(Long id) {
        return repository.findById(id);
    }

    /** Usuários indexados pelo id, numa consulta só; ids inexistentes ficam fora do mapa. */
    @Transactional(readOnly = true)
    public Map<Long, Usuario> buscarPorIds(Collection<Long> ids) {
        return repository.findAllById(ids).stream().collect(Collectors.toMap(Usuario::getId, Function.identity()));
    }

    /** Regrava o hash quando ele usa um algoritmo antigo (ex.: BCrypt vindo do sistema legado). */
    @Transactional
    public void atualizarHashSeNecessario(Usuario usuario, String senha) {
        if (passwordEncoder.upgradeEncoding(usuario.getSenhaHash())) {
            usuario.trocarHashSenha(passwordEncoder.encode(senha), clock.instant());
            repository.save(usuario);
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/domain/Usuario.java`:

`````java
package br.com.celke.cursos.usuario.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 254)
    private String email;

    /** Nulo na conta criada por uma compra, até o dono criar a senha pelo link do e-mail. */
    @Column(name = "senha_hash")
    private String senhaHash;

    @Column(nullable = false)
    private boolean ativo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_papeis", joinColumns = @JoinColumn(name = "usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "papel", length = 20)
    private Set<Papel> papeis = EnumSet.noneOf(Papel.class);

    /** Nulo enquanto o dono não confirmar o e-mail. */
    @Column(name = "email_confirmado_em")
    private Instant emailConfirmadoEm;

    /** Foto de perfil no bucket ({@code fotos/<usuario>/<uuid>.jpg}); nula sem foto. */
    @Column(name = "foto_chave", length = 200)
    private String fotoChave;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Usuario() {}

    public Usuario(
            String nome, String email, String senhaHash, Set<Papel> papeis, boolean emailConfirmado, Instant agora) {
        this.nome = nome.strip();
        this.email = normalizarEmail(email);
        this.senhaHash = senhaHash;
        this.ativo = true;
        this.papeis = EnumSet.copyOf(papeis);
        this.emailConfirmadoEm = emailConfirmado ? agora : null;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** E-mail sempre em minúsculas e sem espaços nas pontas (o banco também exige minúsculas). */
    public static String normalizarEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** Confirma o e-mail; confirmar de novo não muda a data da primeira confirmação. */
    public void confirmarEmail(Instant agora) {
        if (emailConfirmadoEm == null) {
            this.emailConfirmadoEm = agora;
            this.atualizadoEm = agora;
        }
    }

    public boolean possuiSenha() {
        return senhaHash != null;
    }

    public boolean isEmailConfirmado() {
        return emailConfirmadoEm != null;
    }

    public void trocarHashSenha(String novoHash, Instant agora) {
        this.senhaHash = novoHash;
        this.atualizadoEm = agora;
    }

    public void renomear(String nome, Instant agora) {
        this.nome = nome.strip();
        this.atualizadoEm = agora;
    }

    /** Troca a foto (nula remove) e devolve a chave da anterior, a apagar do bucket. */
    public String trocarFoto(String chave, Instant agora) {
        String anterior = this.fotoChave;
        this.fotoChave = chave;
        this.atualizadoEm = agora;
        return anterior;
    }

    public String getFotoChave() {
        return fotoChave;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Set<Papel> getPapeis() {
        return Set.copyOf(papeis);
    }
}
`````

`backend/src/main/resources/application.yaml`:

`````yaml
spring:
  application:
    name: plataforma-cursos
  threads:
    virtual:
      enabled: true
  # Sem usuário embutido: a autenticação é feita pelos usuários do banco
  autoconfigure:
    exclude: org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
  # Sem valor padrão: sem as credenciais a aplicação não sobe (o compose exige as variáveis)
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
  data:
    web:
      pageable:
        default-page-size: 20
        max-page-size: 100
  web:
    # Mensagens de validação em português
    locale: pt_BR
    locale-resolver: fixed
  mail:
    host: ${SMTP_HOST:}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail.smtp.auth: ${SMTP_AUTH:true}
      mail.smtp.starttls.enable: ${SMTP_STARTTLS:true}
      mail.smtp.starttls.required: ${SMTP_STARTTLS:true}
      mail.smtp.connectiontimeout: 10000
      mail.smtp.timeout: 10000
      mail.smtp.writetimeout: 10000

logging:
  # Horário de Brasília nos logs: o @timestamp do JSON (ECS) e o texto do console do desenvolvimento
  structured:
    json:
      customizer: br.com.celke.cursos.shared.config.FusoHorarioLogs
  pattern:
    dateformat: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX, America/Sao_Paulo"

server:
  shutdown: graceful
  # A API roda atrás do Caddy. Com "native", o Tomcat só aceita X-Forwarded-* vindo de proxy interno
  # (rede privada) e lê o X-Forwarded-For da direita para a esquerda: IP forjado pelo cliente é ignorado
  forward-headers-strategy: native
  error:
    include-stacktrace: never
    include-message: never

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
  health:
    # O SMTP fora do ar não deve tirar a API do ar: o envio é pela fila, com novas tentativas
    mail:
      enabled: false

# Swagger: ligado só no desenvolvimento e na homologação (DOCS_ENABLED=true)
springdoc:
  api-docs:
    enabled: ${DOCS_ENABLED:false}
  swagger-ui:
    enabled: ${DOCS_ENABLED:false}

app:
  auth:
    # Segredo do JWT em Base64, com pelo menos 32 bytes (openssl rand -base64 48)
    jwt-segredo: ${JWT_SEGREDO:}
    emissor: plataforma-cursos
    validade-acesso: PT10M
    validade-refresh: P14D
    cookie-seguro: true
    # Origens do front que podem renovar e encerrar a sessão (separadas por vírgula)
    origens-permitidas: ${AUTH_ORIGENS:}
  conta:
    # Área do aluno: base dos links enviados por e-mail
    url-aluno: ${FRONT_ALUNO_URL:}
    validade-confirmacao: PT48H
    validade-recuperacao: PT1H
    emails-por-hora: 3
  limites:
    # Rotas de autenticação e conta: requisições por IP a cada minuto
    requisicoes-por-ip-por-minuto: 30
    # Falhas de login seguidas que bloqueiam o par e-mail + IP, e o e-mail vindo de qualquer IP
    falhas-por-email-e-ip: 5
    falhas-por-email: 20
    # Contadores de falha zeram depois de uma hora sem novas falhas
    janela-falhas: PT1H
    bloqueio: PT15M
  armazenamento:
    # Endereço usado pela aplicação (rede interna)
    endpoint: ${S3_ENDPOINT:}
    # Endereço que vai nas URLs pré-assinadas (o que o navegador acessa)
    endpoint-publico: ${S3_ENDPOINT_PUBLICO:}
    regiao: ${S3_REGIAO:garage}
    bucket: ${S3_BUCKET:}
    access-key: ${S3_ACCESS_KEY:}
    secret-key: ${S3_SECRET_KEY:}
    cors-origens: ${S3_CORS_ORIGENS:}
    dias-uploads-abandonados: 2
  email:
    remetente: ${EMAIL_REMETENTE:}
    nome-remetente: Plataforma de Cursos
  fila:
    max-tentativas: 5
    lease: PT10M
    backoff-base: PT30S
    backoff-maximo: PT1H
    intervalo: PT2S
    consumidor-ativo: false
  video:
    tamanho-maximo: 5GB
    # Validade das URLs de envio das partes (um vídeo grande numa conexão lenta)
    validade-envio: PT6H
    ffmpeg: ffmpeg
    ffprobe: ffprobe
    # veryfast: na VPS (1,5 vCPU para o worker) o ganho de compressão dos presets lentos não compensa o tempo
    preset: veryfast
    crf: 24
    diretorio-trabalho: ${java.io.tmpdir}/plataforma-cursos-videos
    # Validade das URLs dos segmentos = duração do vídeo + margem (pausas e idas e voltas no player)
    margem-playlist: PT2H
    # Log de acesso aos vídeos: 6 meses (prazo do Marco Civil para registros de acesso a aplicações)
    retencao-log-acesso: P180D
  forum:
    # Perguntas e respostas por aluno dentro da janela (o instrutor não tem limite)
    mensagens-por-janela: 20
    janela: PT1H
    anexos-por-mensagem: 3
  pagamentos:
    # Chave AES-256 dos segredos dos meios de pagamento: 32 bytes em Base64 (openssl rand -base64 32).
    # Em branco, a API sobe, mas o painel não grava credenciais
    chave: ${PAGAMENTOS_CHAVE:}
    webhook-tamanho-maximo: 256KB
    webhook-requisicoes-por-ip-por-minuto: 600
    # Corpo dos eventos processados ou ignorados é apagado depois disso (o resto fica para auditoria)
    retencao-payload: P180D
    # Disputa aberta mantém o acesso; true tira o acesso até a disputa terminar
    disputa-suspende: false
    # Cobrança atrasada mantém o acesso; com app.pagamentos.atraso-suspende-apos (ex.: P7D), o acesso
    # é tirado depois desse prazo
    reconciliacao:
      # Consulta diária na API do meio de pagamento (precisa das credenciais da API)
      ativa: false
      pendente-apos: PT24H
      sem-evento-apos: P3D
      janela: P60D
    hotmart:
      url-token: https://api-sec-vlc.hotmart.com/security/oauth/token
      url-api: https://developers.hotmart.com/payments/api/v1
      url-api-sandbox: https://sandbox.hotmart.com/payments/api/v1
      timeout: PT10S
  alertas:
    # Destinatário dos alertas do worker (jobs com erro, disco); em branco, desligados
    email: ${ALERTA_EMAIL:}
    disco-minimo-percentual: 15
    diretorio-disco: /
    repeticao-disco: PT6H
`````

`backend/src/test/java/br/com/celke/cursos/curso/CatalogoIT.java`:

`````java
package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Cobre: catálogo público — só cursos publicados na lista e no detalhe; o detalhe mostra só os
 * módulos e as aulas ativos da turma ativa mais recente, na ordem, sem dados internos.
 * Não cobre: capa (CapaCursoIT), matriz de papéis (CursoAcessoIT).
 * Dado criado: cursos it-<stamp>.
 */
class CatalogoIT extends IntegrationTest {

    private CursoITHelper adm;

    @BeforeEach
    void entrarComoAdmin() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
    }

    /** Slugs de todas as páginas do catálogo, sem login. */
    private List<String> slugsNoCatalogo() {
        List<String> slugs = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            var resposta = mvc.get().uri("/api/cursos?size=100&page=" + pagina).exchange();
            assertThat(resposta).hasStatusOk();
            String corpo = CursoITHelper.corpo(resposta);
            slugs.addAll(JsonPath.read(corpo, "$.itens[*].slug"));
            if (pagina + 1 >= (int) JsonPath.read(corpo, "$.totalPaginas")) {
                return slugs;
            }
        }
    }

    @Test
    @DisplayName("só cursos publicados aparecem: rascunho e arquivado ficam fora e respondem 404")
    void soPublicados() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        assertThat(slugsNoCatalogo()).doesNotContain(slug);
        assertThat(mvc.get().uri("/api/cursos/" + slug))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Curso não encontrado.");

        adm.publicar(curso);
        assertThat(slugsNoCatalogo()).contains(slug);
        assertThat(mvc.get().uri("/api/cursos?size=100"))
                .bodyJson()
                .isLenientlyEqualTo("{\"pagina\":0,\"tamanho\":100}");

        adm.status("/api/adm/cursos/" + curso + "/status", "ARQUIVADO");
        assertThat(slugsNoCatalogo()).doesNotContain(slug);
        assertThat(mvc.get().uri("/api/cursos/" + slug)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("detalhe mostra o conteúdo ativo da turma ativa mais recente, na ordem")
    void conteudoDaTurmaAtiva() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        adm.publicar(curso);

        long antiga = adm.criarTurma(curso, "Antiga");
        adm.ativarTurma(antiga);
        adm.criarModulo(antiga, "Da turma antiga");

        long atual = adm.criarTurma(curso, "Atual");
        adm.ativarTurma(atual);
        long introducao = adm.criarModulo(atual, "Introdução");
        long inativo = adm.criarModulo(atual, "Inativo");
        long semAulas = adm.criarModulo(atual, "Sem aulas");
        long gratuita = adm.criarAula(introducao, "Gratuita", true);
        long escondida = adm.criarAula(introducao, "Escondida", false);
        long paga = adm.criarAula(introducao, "Paga", false);
        adm.criarAula(inativo, "Aula de módulo inativo", false);
        adm.status("/api/adm/modulos/" + inativo + "/status", "INATIVO");
        adm.status("/api/adm/aulas/" + escondida + "/status", "INATIVO");
        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/turmas/" + atual + "/modulos/ordem",
                        "{\"ids\":[%d,%d,%d]}".formatted(semAulas, introducao, inativo)))
                .hasStatusOk();

        long preparacao = adm.criarTurma(curso, "Próxima");
        adm.criarModulo(preparacao, "Da turma em preparação");

        assertThat(mvc.get().uri("/api/cursos/" + slug))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"slug":"%s","titulo":"IT Curso %s","resumo":"Resumo de IT Curso %s","descricao":null,
                         "capaUrl":null,
                         "modulos":[
                           {"titulo":"Sem aulas","aulas":[]},
                           {"titulo":"Introdução","aulas":[
                             {"id":%d,"titulo":"Gratuita","gratuita":true},
                             {"id":%d,"titulo":"Paga","gratuita":false}]}],
                         "ofertas":[]}
                        """.formatted(slug, slug, slug, gratuita, paga));
    }

    @Test
    @DisplayName("curso publicado sem turma ativa aparece sem conteúdo")
    void semTurmaAtiva() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        adm.criarModulo(adm.criarTurma(curso, "Em preparação"), "Módulo");
        adm.publicar(curso);

        assertThat(mvc.get().uri("/api/cursos/" + slug))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.modulos")
                .asArray()
                .isEmpty();
    }

    @Test
    @DisplayName("a listagem não expõe dados internos do curso")
    void semDadosInternos() {
        String slug = stamp();
        adm.publicar(adm.criarCurso("IT Curso " + slug, slug));

        var resposta = mvc.get().uri("/api/cursos/" + slug).exchange();
        assertThat(resposta).hasStatusOk();
        assertThat(CursoITHelper.corpo(resposta))
                .doesNotContain("\"id\"", "status", "capaChave", "criadoEm", "atualizadoEm");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/application/CatalogoServiceTest.java`:

`````java
package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.publicado;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock
    private CursoRepository cursos;

    @Mock
    private TurmaRepository turmas;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private OfertasCatalogo ofertasCatalogo;

    private CatalogoService service;
    private final Curso curso = publicado(curso(1, "java"));

    @BeforeEach
    void setUp() {
        service = new CatalogoService(cursos, turmas, conteudoTurmaService, ofertasCatalogo);
    }

    @Test
    @DisplayName("lista só publicados, em ordem alfabética, ignorando a ordenação do cliente")
    void listar() {
        when(cursos.findByStatus(StatusCurso.PUBLICADO, PageRequest.of(1, 5, Sort.by("titulo", "id"))))
                .thenReturn(Page.empty());

        service.listar(PageRequest.of(1, 5, Sort.by("capaChave")));

        verify(cursos).findByStatus(StatusCurso.PUBLICADO, PageRequest.of(1, 5, Sort.by("titulo", "id")));
    }

    @Test
    @DisplayName("curso não publicado responde não encontrado")
    void naoPublicado() {
        when(cursos.findBySlugAndStatus("rascunho", StatusCurso.PUBLICADO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detalhar("rascunho"))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Curso não encontrado.");
    }

    @Test
    @DisplayName("curso sem turma ativa aparece sem conteúdo")
    void semTurmaAtiva() {
        when(cursos.findBySlugAndStatus("java", StatusCurso.PUBLICADO)).thenReturn(Optional.of(curso));
        when(turmas.findFirstByCursoIdAndStatusOrderByIdDesc(1L, StatusTurma.ATIVA))
                .thenReturn(Optional.empty());

        var detalhe = service.detalhar("java");

        assertThat(detalhe.modulos()).isEmpty();
        assertThat(detalhe.ofertas()).isEmpty();
    }

    @Test
    @DisplayName("mostra o conteúdo ativo e as ofertas da turma ativa mais recente")
    void conteudoDaTurmaAtiva() {
        List<ModuloConteudo> conteudo = List.of(new ModuloConteudo(10L, "Introdução", List.of()));
        List<OfertasCatalogo.OfertaCatalogo> ofertas =
                List.of(new OfertasCatalogo.OfertaCatalogo(3L, "À vista", 19_700, "BRL"));
        when(ofertasCatalogo.ativas(5L)).thenReturn(ofertas);
        when(cursos.findBySlugAndStatus("java", StatusCurso.PUBLICADO)).thenReturn(Optional.of(curso));
        when(turmas.findFirstByCursoIdAndStatusOrderByIdDesc(1L, StatusTurma.ATIVA))
                .thenReturn(Optional.of(turma(5, curso)));
        when(conteudoTurmaService.conteudoAtivo(5L)).thenReturn(conteudo);

        var detalhe = service.detalhar("java");

        assertThat(detalhe.curso()).isSameAs(curso);
        assertThat(detalhe.modulos()).isEqualTo(conteudo);
        assertThat(detalhe.ofertas()).isEqualTo(ofertas);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/matricula/domain/MatriculaTest.java`:

`````java
package br.com.celke.cursos.matricula.domain;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.cancelada;
import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class MatriculaTest {

    private static final Instant INICIO = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant FIM = Instant.parse("2026-06-01T10:00:00Z");

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "um segundo antes do início,   2026-03-01T09:59:59Z, AGUARDANDO_INICIO",
        "exatamente no início,         2026-03-01T10:00:00Z, VIGENTE",
        "no meio do período,           2026-04-15T00:00:00Z, VIGENTE",
        "exatamente no fim,            2026-06-01T10:00:00Z, VIGENTE",
        "um segundo depois do fim,     2026-06-01T10:00:01Z, VENCIDA",
    })
    void situacaoPeloPeriodo(String caso, Instant agora, SituacaoMatricula esperada) {
        assertThat(matricula(1, 2, 3, INICIO, FIM).situacao(agora)).isEqualTo(esperada);
    }

    @Test
    @DisplayName("sem fim, não vence")
    void semFim() {
        assertThat(matricula(1, 2, 3, INICIO, null).situacao(Instant.parse("2099-01-01T00:00:00Z")))
                .isEqualTo(SituacaoMatricula.VIGENTE);
    }

    @Test
    @DisplayName("cancelada é cancelada em qualquer instante, mesmo dentro do período")
    void canceladaEmQualquerInstante() {
        Matricula matricula = cancelada(matricula(1, 2, 3, INICIO, FIM));
        assertThat(matricula.situacao(Instant.parse("2026-04-15T00:00:00Z"))).isEqualTo(SituacaoMatricula.CANCELADA);
        assertThat(matricula.getStatus()).isEqualTo(StatusMatricula.CANCELADA);
        assertThat(matricula.getCanceladaEm()).isNotNull();
    }

    @ParameterizedTest(name = "fim {0}")
    @CsvSource({"2026-03-01T10:00:00Z", "2026-02-28T10:00:00Z"})
    void fimPrecisaSerDepoisDoInicio(Instant fim) {
        assertThatThrownBy(() -> matricula(1, 2, 3, INICIO, fim))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O fim da matrícula deve ser depois do início.");
    }

    @Test
    @DisplayName("um segundo depois do início já é um fim válido")
    void fimUmSegundoDepois() {
        assertThat(matricula(1, 2, 3, INICIO, INICIO.plusSeconds(1)).getFim()).isEqualTo(INICIO.plusSeconds(1));
    }

    @Test
    @DisplayName("transação em branco vira nula; preenchida é aparada")
    void transacao() {
        Instant agora = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(new Matricula(1L, 2L, INICIO, null, FormaPagamento.PIX, "  ", agora).getTransacao())
                .isNull();
        assertThat(new Matricula(1L, 2L, INICIO, null, FormaPagamento.PIX, " tx-1 ", agora).getTransacao())
                .isEqualTo("tx-1");
    }

    @Nested
    class AlterarPeriodo {

        @Test
        @DisplayName("altera o início e o fim")
        void altera() {
            Matricula matricula = matricula(1, 2, 3, INICIO, FIM);
            Instant novoFim = FIM.plusSeconds(86_400);
            matricula.alterarPeriodo(INICIO, novoFim, INICIO);
            assertThat(matricula.getFim()).isEqualTo(novoFim);
        }

        @Test
        @DisplayName("recusa período invertido sem mudar nada")
        void recusaInvertido() {
            Matricula matricula = matricula(1, 2, 3, INICIO, FIM);
            assertThatThrownBy(() -> matricula.alterarPeriodo(FIM, INICIO, INICIO))
                    .isInstanceOf(RegraNegocioException.class);
            assertThat(matricula.getInicio()).isEqualTo(INICIO);
        }

        @Test
        @DisplayName("matrícula cancelada não muda de período nem é cancelada de novo")
        void naoAlteraCancelada() {
            Matricula matricula = cancelada(matricula(1, 2, 3, INICIO, FIM));
            assertThatThrownBy(() -> matricula.alterarPeriodo(INICIO, null, INICIO))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("A matrícula está cancelada.");
            assertThatThrownBy(() -> matricula.cancelar(INICIO)).isInstanceOf(ConflitoException.class);
        }
    }

    @Test
    @DisplayName("arquivar e desarquivar não mudam a situação e são idempotentes")
    void arquivar() {
        Matricula matricula = matricula(1, 2, 3, INICIO, FIM);
        Instant agora = Instant.parse("2026-04-15T00:00:00Z");
        matricula.arquivar(agora);
        matricula.arquivar(agora.plusSeconds(60));
        assertThat(matricula.getArquivadaEm()).isEqualTo(agora);
        assertThat(matricula.isVigente(agora)).isTrue();
        matricula.desarquivar(agora);
        matricula.desarquivar(agora);
        assertThat(matricula.isArquivada()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(FiltroMeusCursos.class)
    void canceladaNaoApareceEmNenhumaAba(FiltroMeusCursos filtro) {
        assertThat(filtro.aceita(cancelada(matricula(1, 2, 3, INICIO, FIM)), Instant.parse("2026-04-15T00:00:00Z")))
                .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "vigente,                       2026-04-15T00:00:00Z, false, ATIVOS",
        "aguardando início,             2026-02-01T00:00:00Z, false, ATIVOS",
        "vigente arquivada,             2026-04-15T00:00:00Z, true,  ARQUIVADOS",
        "aguardando início arquivada,   2026-02-01T00:00:00Z, true,  ARQUIVADOS",
        "vencida,                       2026-07-01T00:00:00Z, false, EXPIRADOS",
        "vencida arquivada,             2026-07-01T00:00:00Z, true,  EXPIRADOS",
    })
    void abaDeMeusCursos(String caso, Instant agora, boolean arquivada, FiltroMeusCursos esperado) {
        Matricula matricula = matricula(1, 2, 3, INICIO, FIM);
        if (arquivada) {
            matricula.arquivar(INICIO);
        }
        for (FiltroMeusCursos filtro : FiltroMeusCursos.values()) {
            assertThat(filtro.aceita(matricula, agora)).as(filtro.name()).isEqualTo(filtro == esperado);
        }
    }

    @Nested
    class PeloPagamento {

        private final Instant agora = Instant.parse("2026-04-01T00:00:00Z");

        @Test
        @DisplayName("matrícula de pagamento guarda o pedido")
        void guardaPedido() {
            Matricula matricula = Matricula.dePagamento(2L, 3L, INICIO, FIM, FormaPagamento.PIX, " HP1 ", 9L, INICIO);

            assertThat(matricula.getPedidoId()).isEqualTo(9L);
            assertThat(matricula.getTransacao()).isEqualTo("HP1");
            assertThat(matricula.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                value = {
                    "fim no futuro soma a partir do fim,     2026-06-01T10:00:00Z, 30,   2026-07-01T10:00:00Z",
                    "fim já passou soma a partir de agora,   2026-03-15T10:00:00Z, 30,   2026-05-01T00:00:00Z",
                    "oferta sem prazo deixa sem prazo,       2026-06-01T10:00:00Z, nulo, nulo",
                    "matrícula sem prazo continua sem prazo, nulo,                 30,   nulo",
                },
                nullValues = "nulo")
        void estender(String caso, Instant fimAtual, Integer dias, Instant novoFim) {
            Matricula matricula = matricula(1, 2, 3, INICIO, fimAtual);

            matricula.estender(dias, agora, agora);

            assertThat(matricula.getFim()).isEqualTo(novoFim);
        }

        @Test
        @DisplayName("matrícula cancelada não é estendida")
        void naoEstendeCancelada() {
            assertThatThrownBy(() -> cancelada(matricula(1, 2, 3, INICIO, FIM)).estender(30, agora, agora))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("A matrícula está cancelada.");
        }

        @Test
        @DisplayName("reativar devolve o acesso com o mesmo período; ativa não muda")
        void reativar() {
            Matricula matricula = cancelada(matricula(1, 2, 3, INICIO, FIM));

            matricula.reativar(agora);

            assertThat(matricula.getStatus()).isEqualTo(StatusMatricula.ATIVA);
            assertThat(matricula.getCanceladaEm()).isNull();
            assertThat(matricula.situacao(agora)).isEqualTo(SituacaoMatricula.VIGENTE);
            matricula.reativar(agora);
            assertThat(matricula.getFim()).isEqualTo(FIM);
        }
    }
}
`````

`backend/src/test/resources/application-test.yaml`:

`````yaml
# Profile dos testes de integração. Endereços dos containers vêm de support/Containers.
spring:
  mail:
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false
      mail.smtp.starttls.required: false

app:
  auth:
    # Chave só dos testes (48 bytes em Base64)
    jwt-segredo: dGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUt
    origens-permitidas: http://localhost:5173
  conta:
    url-aluno: http://localhost:5173
  limites:
    # Todas as requisições da suíte saem do mesmo IP: o limite por IP fica fora do caminho
    requisicoes-por-ip-por-minuto: 100000
  armazenamento:
    cors-origens: http://localhost:5173
  email:
    remetente: nao-responda@teste.celke.uno
  pagamentos:
    # Chave só dos testes (32 bytes em Base64)
    chave: dGVzdGUtZGEtY2hhdmUtZGUtcGFnYW1lbnRvcy0zMmI=
    hotmart:
      # Sem saída para a internet: o teste de credenciais falha na conexão
      url-token: http://localhost:1/security/oauth/token
      timeout: PT2S
  forum:
    # Limite baixo para o teste do 429 (cada teste usa um aluno próprio)
    mensagens-por-janela: 5
`````

`docs/operacao.md`:

`````markdown
# Operação

Rotina da plataforma depois que a VPS está no ar ([roteiro da VPS](vps.md)): backup e restauração,
monitoramento e alertas, logs, espaço em disco, saída para um S3 externo e a revisão de segurança.

## 1. Backup diário

`infra/scripts/backup.sh <ambiente>` guarda, com o [restic](https://restic.net), num repositório S3
**fora da VPS** (Cloudflare R2 ou Backblaze B2):

- o dump do PostgreSQL (`pg_dump --format=custom`), por stdin, sem arquivo temporário no disco;
- os arquivos do Garage, com o snapshot consistente dos metadados (`garage meta snapshot`); o SQLite em
  uso fica de fora.

Retenção: 7 diários, 4 semanais e 6 mensais por ambiente. O restic cifra tudo com a `RESTIC_PASSWORD`.

### Configuração (uma vez, na VPS)

1. Crie um bucket privado no R2 (ou B2) só para os backups e uma chave com acesso apenas a ele.
2. Na VPS, como `deploy`:

   ```bash
   sudo install -o root -g root -m 755 /tmp/backup.sh /usr/local/bin/plataforma-cursos-backup
   install -m 600 /dev/null /opt/plataforma-cursos/backup.env
   nano /opt/plataforma-cursos/backup.env
   ```

   ```bash
   RESTIC_REPOSITORY=s3:https://<conta>.r2.cloudflarestorage.com/plataforma-cursos-backup
   RESTIC_PASSWORD=          # openssl rand -base64 48 — guarde também no gerenciador de senhas
   AWS_ACCESS_KEY_ID=
   AWS_SECRET_ACCESS_KEY=
   ```

3. Agendamento (`crontab -e` do `deploy`), fora do horário de aula:

   ```cron
   30 3 * * * /usr/local/bin/plataforma-cursos-backup producao >> /opt/plataforma-cursos/backup.log 2>&1
   45 3 * * * /usr/local/bin/plataforma-cursos-backup homolog  >> /opt/plataforma-cursos/backup.log 2>&1
   ```

> Sem a `RESTIC_PASSWORD` o backup não abre. Ela fica em dois lugares: no `backup.env` da VPS e no
> gerenciador de senhas do responsável.

## 2. Restauração

`infra/scripts/restaurar.sh <ambiente> [snapshot-postgres] [snapshot-garage]` para a API e o worker,
recria o banco a partir do dump, troca os arquivos do Garage pelos do backup (com os metadados do
snapshot) e sobe tudo de novo. Pede o nome do ambiente para confirmar (ou `CONFIRMAR=sim`).

```bash
docker run --rm --env-file /opt/plataforma-cursos/backup.env restic/restic:0.19.1 snapshots   # escolher
restaurar.sh homolog                                                                         # o mais recente
```

Se algo falhar no meio, a API e o worker **continuam parados de propósito** (subir com o banco pela
metade é pior): corrija e rode de novo.

### Teste de restauração (feito e repetido a cada trimestre)

Testado na stack de e2e, com um repositório restic local:

```bash
export MSYS_NO_PATHCONV=1 ENV_BACKUP=<backup.env com RESTIC_REPOSITORY=/repo> \
       PROJETO=plataforma-cursos-e2e RESTIC_EXTRA="-v restic-teste:/repo"
infra/scripts/backup.sh e2e
# estrago: UPDATE cursos SET titulo = 'ESTRAGADO'; e apagar /garage/data do volume
CONFIRMAR=sim infra/scripts/restaurar.sh e2e
```

Resultado: os títulos dos 7 cursos voltaram, os 11 objetos do bucket baixaram pela API S3 e a API e o
worker subiram saudáveis. O procedimento trimestral repete o teste na **homologação** com o backup da
produção mais recente.

## 3. Monitoramento e alertas

| O quê | Como |
|---|---|
| Site fora do ar | Monitor externo (UptimeRobot ou Better Stack, plano gratuito) a cada 5 minutos em `https://aluno.celke.uno/actuator/health` e `https://cursos.celke.uno`, com aviso por e-mail e celular |
| Job que desistiu (conversão de vídeo, por exemplo) | O worker verifica a cada 15 minutos os jobs que foram para `ERRO` e envia um e-mail para `ALERTA_EMAIL` com o resumo |
| Pagamento contestado ou notificação com erro | Chargeback, disputa aberta e notificação de pagamento que foi para `ERRO` geram um e-mail para `ALERTA_EMAIL` na hora (veja `docs/pagamentos.md`) |
| Disco quase cheio | O mesmo verificador do worker mede o disco da VPS (o sistema de arquivos do container é o do host) e alerta abaixo de 15% livre, repetindo a cada 6 horas enquanto não melhorar |
| Container parado | `restart: unless-stopped` e o healthcheck de cada serviço; o deploy só conclui com tudo saudável |

`ALERTA_EMAIL` fica no `.env` de cada ambiente; em branco, os alertas do worker ficam desligados.
Falha no envio de e-mail não gera alerta por e-mail (iria pela mesma fila): aparece no log do worker.

## 4. Logs e limpeza

- **Logs do Docker** com rotação em todos os serviços (`json-file`, 5 arquivos de 10 MB por container):
  no máximo ~250 MB por ambiente. Os logs da API e do worker são JSON (ECS) com o id de correlação e o `@timestamp` no horário de Brasília (`-03:00`); o gerador do site e o `deploy.log` usam o mesmo fuso.
- **Uploads abandonados** (`uploads/` do bucket): a regra de expiração do bucket apaga em 2 dias, e os
  uploads em partes não concluídos em 1 dia. Os envios aceitos saem de `uploads/` na confirmação.
- **Log de acesso aos vídeos**: o worker apaga todo dia o que passou de 180 dias.
- **Dados da homologação**: quando acumular lixo de testes,
  `docker compose -p plataforma-cursos-homolog -f compose.prod.yml -f compose.homolog.yml down -v`
  e o próximo deploy da `homolog` recria tudo vazio (crie o admin de novo com `criar-admin`).

## 5. Quanto vídeo cabe no disco

VPS de 100 GB NVMe. Reservas: sistema e imagens Docker (~15 GB), PostgreSQL e logs (~5 GB), uploads em
andamento (~10 GB) e os 15% livres do alerta (15 GB). Sobram **~55 GB para o HLS** (produção e
homologação juntas).

Cada hora de aula guarda as duas variantes (720p e 1080p) mais o áudio de cada uma:

| Conteúdo | Taxa média (720p + 1080p + áudio) | GB por hora | Horas em 55 GB |
|---|---|---|---|
| Tela de código, slides (o típico do curso) | ~1,1 Mbps | ~0,5 | ~110 h |
| Câmera e tela misturadas | ~3,9 Mbps | ~1,8 | ~30 h |
| Pior caso (sempre no teto: 2,8 + 5 Mbps) | ~8,1 Mbps | ~3,6 | ~15 h |

A conta do capítulo de limites da VPS usa o `segundosProcessamentoPorMinuto` e o `tamanhoFinal` que o
ADM mostra para cada vídeo: medir com os vídeos reais e atualizar esta tabela.

## 6. Saída para um S3 externo

Quando o HLS passar de ~40 GB, ou se a banda da VPS virar gargalo, os vídeos vão para o Cloudflare R2
(sem taxa de saída) ou o Backblaze B2. Como a aplicação só fala S3 pelo AWS SDK:

1. Criar o bucket no R2, a chave da aplicação e o CORS para `aluno.celke.uno` e `adm.celke.uno`.
2. Copiar o conteúdo: `rclone sync garage:midia r2:midia` (com as duas origens no `rclone.conf`).
3. No `.env` da produção, trocar `S3_ENDPOINT` e `S3_ENDPOINT_PUBLICO` (endpoint do R2 ou domínio
   próprio `media.celke.uno` apontado para o bucket), `S3_BUCKET`, `S3_ACCESS_KEY` e `S3_SECRET_KEY`.
4. Rodar o `rclone sync` de novo (o que entrou durante a cópia) e reiniciar a API e o worker.
5. Depois de uma semana sem problema, tirar o Garage da produção e o host de mídia do Caddy.

## 7. Segurança

### OWASP ASVS nível 1 — revisão da API

| Capítulo | Situação |
|---|---|
| V2 Autenticação | Senha mínima de 10 (aluno) / 12 (admin), máximo 128, Argon2; bloqueio após falhas por e-mail e por e-mail + IP; limite por IP nas rotas de conta; recuperação com token de uso único e resposta igual para e-mail existente ou não |
| V3 Sessão | Access token de 10 minutos; refresh opaco em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, sem `Domain`; rotação com detecção de reuso (revoga a família); logout e "sair de todos"; troca de senha encerra todas as sessões |
| V4 Controle de acesso | Tudo negado por padrão; rotas públicas declaradas com método; papel no filtro e no `@PreAuthorize`; acesso ao conteúdo só pela `AcessoAulaPolicy`; recursos de outro usuário respondem 404; chaves de upload com o id do dono (IDOR) |
| V5 Entrada e saída | Bean Validation em todo corpo; Markdown com HTML escapado e sanitizado (lista fechada); conteúdo dos arquivos conferido pela assinatura real; imagens regravadas sem metadados; ffmpeg por lista de argumentos |
| V7 Erros e logs | ProblemDetail sem stack trace nem causa; id de correlação; auditoria de login; log de acesso aos vídeos com retenção |
| V8 Dados | Bucket privado, só URLs pré-assinadas curtas; certificado validado sem e-mail nem ids; segredos só nos `.env` e nos secrets |
| V9 Comunicação | HTTPS automático pelo Caddy, HSTS; CORS do bucket com as origens exatas |
| V10 Código malicioso / V14 Configuração | Trivy no pipeline (críticos com correção quebram o build); versões fixas de imagens e dependências; usuário não-root nos containers; Swagger e Actuator fora da produção |

### Varreduras

- **Trivy** (a cada PR, imagens da API e do worker): a primeira varredura apontou o Tomcat 11.0.24
  (3 CVEs críticos) e o Jackson 3.1.5 / 2.21.5; o `pom.xml` fixa as versões corrigidas até o Spring Boot
  trazê-las.
- **OWASP ZAP baseline** na stack local de e2e (rodar também na homologação a cada versão):
  0 falhas, 65 regras sem achado; o aviso de `Cross-Origin-Resource-Policy` foi resolvido no Caddy
  (`same-site`). "Non-Storable Content" é esperado: a API responde sem cache.

  ```bash
  docker run --rm ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t https://homolog-aluno.celke.uno/api/cursos -I
  ```
`````

`docs/vps.md`:

`````markdown
# Roteiro da VPS

Preparação da VPS da Hostinger (Ubuntu 24.04 ou mais novo; testado também no 26.04) para a produção e
a homologação. Feito uma vez; as
versões novas são implantadas pelo pipeline (merge na `homolog` → homologação; merge na `main` →
produção), pelo script `/usr/local/bin/plataforma-cursos-deploy`.

## Como fica a VPS

```
/opt/plataforma-cursos/            (dono: deploy, permissão 750)
├── caddy/                         Caddy único nas portas 80/443 (projeto plataforma-cursos-caddy)
│   ├── compose.yml
│   ├── Caddyfile
│   ├── sites/producao.caddy
│   ├── sites/homolog.caddy
│   └── .env                       hosts, e-mail do Let's Encrypt, IPs liberados
├── producao/                      projeto plataforma-cursos-producao, rede plataforma-cursos-producao
│   ├── compose.prod.yml
│   ├── garage/garage.toml
│   ├── .env                       segredos da produção (TAG = versão no ar)
│   ├── versoes/<sha>/             arquivos de compose das últimas versões (rollback)
│   ├── backups/                   dump do banco antes de cada deploy
│   └── deploy.log                 histórico dos deploys
└── homolog/                       projeto plataforma-cursos-homolog, rede plataforma-cursos-homolog
    ├── compose.prod.yml
    ├── compose.homolog.yml
    ├── garage/garage.toml
    └── .env                       segredos da homologação (diferentes dos da produção)
```

Só o Caddy publica portas. Ele chega à API, aos fronts e ao Garage de cada ambiente pela rede do
ambiente, pelos apelidos `producao-api`, `producao-web`, `producao-garage`, `homolog-api`, `homolog-web`
e `homolog-garage`. Em `aluno` e `adm`, `/api/*` e o health check vão para a API; o resto vai para o
container `web` (área do aluno na porta 8081, ADM na 8082). Em `cursos`, só as leituras públicas da
API (catálogo, termos vigentes, validação de certificado) vão para ela; o resto é o site público, que o
container `site` gera a partir da API ao subir e a cada 5 minutos (`SITE_INTERVALO`), servido pelo
`web` na porta 8083.

| Host | Ambiente | Acesso |
|---|---|---|
| `aluno.celke.uno` | produção | público |
| `adm.celke.uno` | produção | só `ADM_IPS` |
| `cursos.celke.uno` | produção | público (site gerado a partir da API) |
| `media.celke.uno` | produção | público (URLs assinadas) |
| `homolog-aluno.celke.uno`, `homolog-adm.celke.uno`, `homolog-media.celke.uno` | homologação | só `HOMOLOG_IPS`, `noindex`, com Swagger |

## 1. Contratar a VPS na Hostinger

1. Contrate a VPS pelo link https://celke.com.br/page/hostinger e use o cupom **CELKE** no pagamento.
   Recomendado: plano **KVM 4** (4 vCPU, 16 GB de RAM, 200 GB de disco NVMe), com folga para a
   produção e a homologação na mesma VPS e para a conversão dos vídeos. Sistema **Ubuntu 24.04**, sem
   painel de controle; defina uma senha forte para o root (ela só vale até o passo 4).
2. No painel da Hostinger, anote o IP da VPS.
3. Firewall da VPS: libere só 22, 80 e 443 (TCP) e 443 (UDP). O `ufw` faz o mesmo dentro da VPS; o
   painel é a segunda camada.
4. Ative o backup/snapshot semanal do painel (não substitui o backup fora da VPS da fase de operação).

## 2. DNS de celke.uno

Crie registros **A** apontando para o IP da VPS (só subdomínios; o domínio principal continua com as
outras aplicações):

```
cursos          A   <ip-da-vps>
aluno           A   <ip-da-vps>
adm             A   <ip-da-vps>
media           A   <ip-da-vps>
homolog-cursos  A   <ip-da-vps>
homolog-aluno   A   <ip-da-vps>
homolog-adm     A   <ip-da-vps>
homolog-media   A   <ip-da-vps>
```

Confira antes de seguir: `dig +short aluno.celke.uno` responde o IP da VPS.

## 3. Chaves SSH

São **duas chaves diferentes**, as duas geradas no **seu computador** (nunca na VPS: a chave privada do
deploy ficaria guardada no próprio servidor que ela abre). De cada uma, só a parte pública (`.pub`) vai
para a VPS.

| Chave | Quem usa | Pública (`.pub`) vai para | Privada fica |
|---|---|---|---|
| administrador | você, para entrar na VPS | `ADMIN_CHAVE` | no seu computador |
| deploy | o GitHub Actions, para publicar | `DEPLOY_CHAVE` | no secret `VPS_SSH_KEY` (passo 8) |

Não use a mesma chave nas duas: a do deploy só executa o script de deploy (sem terminal), e a do
administrador dá acesso completo com sudo.

No Linux, macOS ou Git Bash:

```bash
# Sua chave (administrador). Use a que já tem, se houver (id_ed25519.pub ou id_rsa.pub):
cat ~/.ssh/id_ed25519.pub

# Chave só do deploy automático
ssh-keygen -t ed25519 -f ~/.ssh/plataforma-cursos-deploy -C "github-deploy" -N ""
cat ~/.ssh/plataforma-cursos-deploy.pub
```

No PowerShell do Windows:

```powershell
Get-Content $HOME\.ssh\id_ed25519.pub     # ou id_rsa.pub; sem nenhuma: ssh-keygen -t ed25519
ssh-keygen -t ed25519 -f $HOME\.ssh\plataforma-cursos-deploy -C "github-deploy" -N '""'
Get-Content $HOME\.ssh\plataforma-cursos-deploy.pub
```

Cada `.pub` é **uma linha inteira**: `ssh-ed25519 AAAAC3Nza...` (cerca de 80 caracteres) ou
`ssh-rsa AAAAB3Nza...` (centenas de caracteres). Copie a linha toda; uma chave cortada deixa você fora
da VPS no fim do passo 4.

## 4. Preparar a VPS

O script recebe três valores:

- `ADMIN_USUARIO`: o nome do **seu** usuário Linux na VPS (ex.: `cesar`). Ele ainda não existe: o script
  cria, com sudo e acesso ao Docker. Não é o usuário do painel da Hostinger nem o do GitHub. Depois do
  script, é com ele que você entra (`ssh cesar@<ip-da-vps>`), porque o root deixa de entrar por SSH.
- `ADMIN_CHAVE`: a linha do `.pub` da sua chave de administrador.
- `DEPLOY_CHAVE`: a linha do `.pub` da chave do deploy. O usuário `deploy` tem nome fixo.

Do seu computador, na raiz do repositório:

```bash
scp infra/scripts/preparar-vps.sh root@<ip-da-vps>:/root/
ssh root@<ip-da-vps>
```

Na VPS, como root. Primeiro garanta o fim de linha LF (um checkout do Windows feito antes do
`.gitattributes` pode ter trazido CRLF, e o bash falha com `set: pipefail: invalid option name`):

```bash
cd /root
sed -i 's/\r$//' preparar-vps.sh
grep -c $'\r' preparar-vps.sh          # 0
```

Depois rode o script, **numa linha só** e com as chaves entre aspas:

```bash
ADMIN_USUARIO=cesar ADMIN_CHAVE="ssh-ed25519 AAAA... sua chave" DEPLOY_CHAVE="ssh-ed25519 AAAA... github-deploy" bash preparar-vps.sh
```

Para quebrar em várias linhas, termine cada uma com `\` (sem espaço depois dela) e não esqueça a última,
`bash preparar-vps.sh`: sem ela as variáveis são definidas e nada é executado. As linhas com `#` no
começo do script são só o exemplo de uso; não precisa editar o arquivo.

O script pede a senha do sudo do administrador (o login continua só por chave) e termina com
**"Pronto."**. A chave do deploy fica presa ao script de deploy (`command=` no `authorized_keys`): ela
não abre shell, terminal nem túnel, mesmo se vazar.

**Não feche a sessão de root** até confirmar, em outro terminal:

```bash
ssh cesar@<ip-da-vps>      # entra sem senha
sudo -v                    # aceita a senha definida pelo script
sudo ufw status            # ativo, com OpenSSH, 80/tcp, 443/tcp e 443/udp
swapon --show              # /swapfile de 2G
```

Depois disso, `ssh root@<ip-da-vps>` e login por senha passam a ser recusados.

### Se algo der errado no passo 4

- **O script parou no meio**: ele pode ser rodado de novo, com os mesmos valores; o que já existe é
  mantido e só a senha do sudo é pedida outra vez. Se ele parou depois de `==> SSH só por chave e sem
  root`, o root pode já não entrar por SSH (no Ubuntu 24.04+ o SSH sobe por socket e cada conexão nova
  já lê a configuração). Entre como administrador, copie o script para a pasta dele
  (`scp infra/scripts/preparar-vps.sh cesar@<ip-da-vps>:~/`) e rode como root com `sudo -i`.
- **`sudo -i` e os comandos seguintes colados juntos**: as linhas coladas se perdem enquanto o shell do
  root abre. Rode o `sudo -i` sozinho e os comandos depois, um de cada vez.
- **`ssh cesar@...` recusado** (`Permission denied (publickey)`), quase sempre por chave incompleta no
  `ADMIN_CHAVE`: entre pelo **terminal do navegador** no painel da Hostinger (VPS → Terminal), que não
  passa pelo SSH, desfaça o bloqueio com `rm /etc/ssh/sshd_config.d/01-plataforma.conf` e rode o script
  de novo com a chave completa.
- **`scp` roda no seu computador**, não dentro da VPS: na VPS ele tenta conectar nela mesma e é recusado.

## 5. Copiar os arquivos da infraestrutura

Do seu computador, na raiz do repositório:

```bash
VPS=cesar@<ip-da-vps>
ssh $VPS 'mkdir -p /tmp/plataforma && cd /tmp/plataforma && mkdir -p caddy/sites producao/garage homolog/garage'
scp infra/caddy/compose.yml infra/caddy/Caddyfile infra/caddy/.env.example $VPS:/tmp/plataforma/caddy/
scp infra/caddy/sites/producao.caddy infra/caddy/sites/homolog.caddy $VPS:/tmp/plataforma/caddy/sites/
scp infra/compose.prod.yml $VPS:/tmp/plataforma/producao/
scp infra/compose.prod.yml infra/compose.homolog.yml $VPS:/tmp/plataforma/homolog/
scp infra/garage/garage.toml $VPS:/tmp/plataforma/producao/garage/
scp infra/garage/garage.toml $VPS:/tmp/plataforma/homolog/garage/
scp infra/scripts/gerar-env-servidor.sh infra/scripts/deploy.sh $VPS:/tmp/plataforma/
ssh $VPS 'sudo install -o root -g root -m 755 /tmp/plataforma/deploy.sh /usr/local/bin/plataforma-cursos-deploy \
  && rm /tmp/plataforma/deploy.sh \
  && sudo cp -r /tmp/plataforma/. /opt/plataforma-cursos/ && sudo chown -R deploy:deploy /opt/plataforma-cursos \
  && rm -rf /tmp/plataforma'
```

## 6. Configuração de cada ambiente

Na VPS, como `deploy` (`sudo -iu deploy`):

```bash
cd /opt/plataforma-cursos
bash gerar-env-servidor.sh producao producao/.env
bash gerar-env-servidor.sh homolog homolog/.env
cp caddy/.env.example caddy/.env && chmod 600 caddy/.env
```

Preencha:

- `producao/.env` e `homolog/.env`: `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD` (painel da Iagente).
  Na homologação, use um remetente identificado ou restrinja os envios a e-mails de teste.
- `caddy/.env`: `ACME_EMAIL`, `ADM_IPS` e `HOMOLOG_IPS` (seus IPs, separados por espaço).

Os segredos de cada `.env` são gerados aleatoriamente e são diferentes entre os ambientes: um token de
homologação nunca vale na produção.

## 7. Acesso às imagens (GHCR)

Crie no GitHub um token clássico só com `read:packages` e, na VPS, como `deploy`:

```bash
echo "<token>" | docker login ghcr.io -u <usuario-github> --password-stdin
```

## 8. Deploy automático no GitHub

Feito com a conta dona do repositório (colaboradores não veem as configurações). Em **Settings →
Secrets and variables → Actions**, crie os secrets do repositório (servem aos dois ambientes: a VPS e a
chave são as mesmas):

| Secret | Valor |
|---|---|
| `VPS_HOST` | IP da VPS |
| `VPS_SSH_KEY` | conteúdo de `~/.ssh/plataforma-cursos-deploy` (a chave **privada** do deploy) |
| `VPS_KNOWN_HOSTS` | saída de `ssh-keyscan -t ed25519 <ip-da-vps>` (conferida com o painel da Hostinger) |

Na aba **Variables** da mesma página, crie a variável `DEPLOY_ATIVO` com o valor `true`. Sem ela, o
pipeline pula os deploys (testes, e2e e publicação das imagens continuam rodando).

Os jobs de deploy usam os environments `homolog` e `production`, criados automaticamente na primeira
execução. Se o plano da conta permitir environments em repositório privado, ative em **Settings →
Environments → production** a opção **Required reviewers**: o deploy da produção passa a esperar a sua
aprovação.

## 9. Primeira subida

1. Abra um PR de `feature/base-projeto` para `homolog`, espere o pipeline ficar verde e faça o merge.
   O pipeline publica as imagens no GHCR e implanta a homologação (é a primeira subida dela: sem backup
   e sem versão anterior).
2. Suba o Caddy na VPS, como `deploy`. Ele se liga às redes dos dois ambientes, e a da produção só nasce
   no deploy dela: na primeira vez, crie essa rede com os rótulos do Docker Compose (o deploy da produção
   a adota depois). Até lá, os endereços da produção respondem 502.

   ```bash
   cd /opt/plataforma-cursos
   docker network inspect plataforma-cursos-producao > /dev/null 2>&1 || docker network create \
     --label com.docker.compose.network=default --label com.docker.compose.project=plataforma-cursos-producao \
     plataforma-cursos-producao
   docker compose -f caddy/compose.yml --env-file caddy/.env --project-directory caddy up -d
   ```

3. Valide a homologação (passo 10, nos endereços `homolog-*`) e abra o PR de `homolog` para `main`:
   o pipeline implanta a produção.

4. Crie o administrador de cada ambiente (a senha é pedida no terminal):

   ```bash
   docker compose -p plataforma-cursos-producao -f producao/compose.prod.yml --env-file producao/.env \
     --project-directory producao run --rm api criar-admin admin@celke.uno "Administrador"
   docker compose -p plataforma-cursos-homolog -f homolog/compose.prod.yml -f homolog/compose.homolog.yml \
     --env-file homolog/.env --project-directory homolog run --rm api criar-admin admin@celke.uno "Administrador"
   ```

## 10. Verificação

Do seu computador:

```bash
curl -s https://aluno.celke.uno/actuator/health            # {"groups":[...],"status":"UP"}
curl -sI https://aluno.celke.uno/actuator/health | grep -i strict-transport
curl -s https://aluno.celke.uno/ | grep -o '<title>.*</title>'   # a área do aluno
curl -s https://aluno.celke.uno/v3/api-docs | head -c 15        # <!doctype html> (o front, não a API)
curl -s -o /dev/null -w "%{http_code}\n" https://adm.celke.uno/actuator/health # 403 fora de ADM_IPS
nc -zv -w 3 <ip-da-vps> 5432 ; nc -zv -w 3 <ip-da-vps> 3900 ; nc -zv -w 3 <ip-da-vps> 8080   # recusadas
ssh root@<ip-da-vps>                                        # recusado
```

Na VPS: `docker stats --no-stream` mostra cada container dentro do limite de memória.

## 11. Operação

**Histórico dos deploys**: `tail -n 50 /opt/plataforma-cursos/<ambiente>/deploy.log`. Um deploy que não
fica saudável volta sozinho para a versão anterior e o job do pipeline falha.

**Voltar para uma versão antiga** (uma das guardadas em `versoes/`), como `deploy`:

```bash
tar -C /opt/plataforma-cursos/producao/versoes/<sha> -cz compose.prod.yml garage/garage.toml \
  | plataforma-cursos-deploy producao <sha>
```

O rollback não desfaz migrations: por isso toda migration precisa ser compatível com a versão anterior.

**Atualizar o Caddy** (é compartilhado pelos dois ambientes, então não entra no deploy automático).
Do seu computador, na raiz do repositório, com a `main` atualizada (`git switch main && git pull`): os
arquivos têm que ser os da versão no ar, não os de uma branch de trabalho antiga. Como na etapa 5, o
`scp` roda no seu computador; o `deploy` não tem terminal e `/opt/plataforma-cursos` só ele lê, por isso
a cópia passa pelo `/tmp` do administrador e o `docker compose` roda com `sudo -u deploy`:

```bash
VPS=cesar@<ip-da-vps>
ssh $VPS 'rm -rf /tmp/caddy && mkdir -p /tmp/caddy/sites'
scp infra/caddy/compose.yml infra/caddy/Caddyfile $VPS:/tmp/caddy/
scp infra/caddy/sites/producao.caddy infra/caddy/sites/homolog.caddy $VPS:/tmp/caddy/sites/
ssh -t $VPS 'C=/opt/plataforma-cursos/caddy \
  && sudo cp -p $C/Caddyfile $C/Caddyfile.bak-$(date +%F-%H%M) \
  && sudo cp -r /tmp/caddy/. $C/ && sudo chown -R deploy:deploy $C && rm -rf /tmp/caddy \
  && sudo -u deploy docker compose -f $C/compose.yml --env-file $C/.env --project-directory $C up -d --force-recreate'
curl -s https://aluno.celke.uno/actuator/health   # {"groups":[...],"status":"UP"}
```

O `Caddyfile` anterior fica em `caddy/Caddyfile.bak-<data>`; para voltar, copie-o por cima do
`Caddyfile` e rode de novo o `docker compose ... up -d --force-recreate`. Sem SSH do seu computador (só
o terminal do navegador da Hostinger, como root), o repositório privado não pode ser baixado na VPS:
edite `/opt/plataforma-cursos/caddy/Caddyfile` lá mesmo, igual ao `infra/caddy/Caddyfile` da `main`, e
rode o mesmo `docker compose` com `sudo -u deploy`.

**Site público (Fase 14):** além do DNS de `cursos` e `homolog-cursos` (etapa 2), o `caddy/.env`
precisa de `PRODUCAO_HOST_SITE` e `HOMOLOG_HOST_SITE` (veja `infra/caddy/.env.example`). O endereço
público do site vem de `SITE_URL` no `.env` do ambiente; em branco, é derivado de `FRONT_ALUNO_URL`
(`aluno.` → `cursos.`). A homologação sai com `noindex` e `robots.txt` bloqueando tudo.

Quando a mudança do Caddy depende de um container novo (como na Fase 11, que passou a mandar as
páginas para o `web`), atualize o Caddy **depois** do deploy dos dois ambientes com esse container: antes
disso o Caddy novo não teria para onde mandar as páginas (502), enquanto o antigo continua respondendo
404 fora da API.

**Pagamentos (Fase 16):** o `gerar-env-servidor.sh` já cria a `PAGAMENTOS_CHAVE` nos ambientes novos.
Nos `.env` que já existem, acrescente uma chave por ambiente (diferentes entre si), antes do deploy:

```bash
echo "PAGAMENTOS_CHAVE=$(openssl rand -base64 32)" >> /opt/plataforma-cursos/producao/.env
echo "PAGAMENTOS_CHAVE=$(openssl rand -base64 32)" >> /opt/plataforma-cursos/homolog/.env
```

Trocar a chave depois exige informar de novo os segredos do meio de pagamento no painel. A
reconciliação fica desligada até `PAGAMENTOS_RECONCILIACAO=true`. Atualize também o Caddy (acima): a
homologação continua restrita por IP, mas deixa passar `/api/pagamentos/webhooks/*`, para a Hotmart de
teste notificar `https://homolog-aluno.celke.uno/api/pagamentos/webhooks/<token>` (o endereço completo
aparece no painel). Configuração da Hotmart: `docs/pagamentos.md`.

**Atualizar o script de deploy**: copie o `infra/scripts/deploy.sh` novo para a VPS e, como
administrador, `sudo install -o root -g root -m 755 deploy.sh /usr/local/bin/plataforma-cursos-deploy`.
`````

`infra/.env.example`:

`````bash
# Copie para infra/.env (ou rode ./infra/scripts/gerar-env.sh, que gera os segredos).
# Nunca faça commit do infra/.env.

# PostgreSQL
POSTGRES_DB=plataforma_cursos
POSTGRES_USER=plataforma_cursos
POSTGRES_PASSWORD=                 # openssl rand -hex 24

# Autenticação: chave do JWT em Base64, com pelo menos 32 bytes
JWT_SEGREDO=                       # openssl rand -base64 48
# Origens aceitas no refresh e no logout (padrão: a própria API e http://localhost:5173)
# AUTH_ORIGENS=http://localhost:8080,http://localhost:5173
# Área do aluno, base dos links enviados por e-mail (padrão: http://localhost:5173)
# FRONT_ALUNO_URL=http://localhost:5173

# Pagamentos: chave AES-256 dos segredos dos meios de pagamento (hottok, credenciais da API).
# Sem ela a API sobe, mas o painel não grava as credenciais. Trocar a chave exige informar os segredos de novo
PAGAMENTOS_CHAVE=                  # openssl rand -base64 32

# Garage (S3)
GARAGE_RPC_SECRET=                 # openssl rand -hex 32
GARAGE_ADMIN_TOKEN=                # openssl rand -hex 32
S3_BUCKET=midia
S3_ACCESS_KEY=                     # GK + openssl rand -hex 12
S3_SECRET_KEY=                     # openssl rand -hex 32
# Origens do front que podem enviar e baixar direto do bucket (separadas por vírgula)
S3_CORS_ORIGENS=http://localhost:5173

# E-mail
EMAIL_REMETENTE=nao-responda@celke.uno

# Portas no host (opcional; só mude se a porta padrão já estiver em uso)
# API_PORTA=8080
# POSTGRES_PORTA=5432
# S3_PORTA=3900
# MAILPIT_PORTA=8025
`````

`infra/caddy/Caddyfile`:

`````caddyfile
# Porta de entrada da plataforma. Um Caddy só atende os dois ambientes da VPS: cada site está em
# sites/<ambiente>.caddy e chega à API, aos fronts e ao Garage do seu ambiente pelos apelidos
# <ambiente>-api, <ambiente>-web e <ambiente>-garage. Com "https://..." o Caddy emite o certificado
# sozinho; com "http://..." atende sem TLS (e2e).
{
	admin off
	email {$ACME_EMAIL:nao-configurado@celke.uno}
	# Sem isso, o handle dos sites roda antes do respond e a restrição por IP (somente_ips_adm,
	# homolog) só valeria na mídia
	order respond before handle
}

# Headers de segurança em todas as respostas
(seguranca) {
	header {
		X-Content-Type-Options nosniff
		Referrer-Policy strict-origin-when-cross-origin
		# Respostas só podem ser carregadas por páginas do mesmo site (aluno, adm e mídia são subdomínios dele)
		Cross-Origin-Resource-Policy same-site
		Strict-Transport-Security "max-age=31536000"
		-Server
	}
}

# Um front (aluno ou ADM) com a sua API. Só a API e o health check chegam à API; o resto (Swagger,
# outros endpoints do Actuator) nunca: vai para o container web, que serve o app React.
# O X-Forwarded-For que o cliente mandar é substituído pelo IP real da conexão.
# Argumentos: o ambiente (producao, homolog, e2e) e a porta do app no container web (8081 aluno, 8082 ADM).
(app) {
	@api path /api/* /actuator/health /actuator/health/*
	handle @api {
		reverse_proxy {args[0]}-api:8080 {
			# Durante o deploy a API reinicia: segura a requisição em vez de responder 502
			lb_try_duration 30s
		}
	}
	handle {
		reverse_proxy {args[0]}-web:{args[1]} {
			lb_try_duration 30s
		}
	}
}

# Como (app), mas também com a documentação (Swagger): só na homologação
(app_com_docs) {
	@api path /api/* /actuator/health /actuator/health/* /v3/api-docs /v3/api-docs/* /swagger-ui.html /swagger-ui/*
	handle @api {
		reverse_proxy {args[0]}-api:8080 {
			lb_try_duration 30s
		}
	}
	handle {
		reverse_proxy {args[0]}-web:{args[1]} {
			lb_try_duration 30s
		}
	}
}

# Site público (cursos.*): só as leituras públicas da API (catálogo, capas, termos vigentes e
# validação de certificado) chegam a ela; o resto é o site gerado, servido pelo container web.
# Argumento: o ambiente.
(site) {
	@api {
		method GET HEAD
		path /api/cursos /api/cursos/* /api/termos/vigentes /api/certificados/*
	}
	handle @api {
		reverse_proxy {args[0]}-api:8080 {
			lb_try_duration 30s
		}
	}
	handle {
		reverse_proxy {args[0]}-web:8083 {
			lb_try_duration 30s
		}
	}
}

# Arquivos do bucket por URL pré-assinada (o host faz parte da assinatura e é repassado como veio)
(midia) {
	reverse_proxy {args[0]}-garage:3900
}

# Painel administrativo: só os IPs de ADM_IPS (separados por espaço)
(somente_ips_adm) {
	@fora not remote_ip {$ADM_IPS}
	respond @fora "Acesso restrito." 403
}

# Homologação: só os IPs de HOMOLOG_IPS e fora dos buscadores. As notificações dos meios de pagamento
# (Hotmart de teste) passam: vêm dos servidores deles e são autenticadas pela API (hottok)
(homolog) {
	header X-Robots-Tag "noindex, nofollow"
	@fora {
		not remote_ip {$HOMOLOG_IPS}
		not path /api/pagamentos/webhooks/*
	}
	respond @fora "Acesso restrito." 403
}

import sites/*.caddy
`````

`infra/compose.e2e.yml`:

`````yaml
# e2e: a stack de um ambiente dos servidores (compose.prod.yml, com os fronts) e o Caddy dos
# servidores, HTTP em *.localhost (sites de caddy/e2e.caddy), Mailpit no lugar do SMTP, imagens
# construídas localmente e o seed do profile e2e. Projeto separado do ambiente local.
#
#   docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
#   docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
name: plataforma-cursos-e2e

services:
  caddy:
    image: caddy:2.11.7-alpine
    ports:
      - "127.0.0.1:${E2E_PORTA:?defina E2E_PORTA}:80"
    volumes:
      - ./caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - ./caddy/e2e.caddy:/etc/caddy/sites/e2e.caddy:ro
    depends_on:
      api:
        condition: service_healthy
      web:
        condition: service_healthy
      site:
        condition: service_healthy

  web:
    image: plataforma-cursos-web:e2e
    build:
      context: ../web
      target: web

  site:
    image: plataforma-cursos-site:e2e
    build:
      context: ../web
      target: site
    environment:
      SITE_URL: http://cursos.localhost:8088
      # Curso publicado pelo teste aparece em segundos
      SITE_INTERVALO: "3"

  api:
    image: plataforma-cursos-api:e2e
    build:
      context: ../backend
      target: api
    environment:
      SPRING_PROFILES_ACTIVE: api,e2e
      SEED_E2E_SENHA: ${SEED_E2E_SENHA:?defina SEED_E2E_SENHA}
      # Todas as jornadas saem do mesmo IP
      APP_LIMITES_REQUISICOES_POR_IP_POR_MINUTO: "300"
      # URLs dos segmentos vencem em segundos: a jornada de entrega confere a expiração
      APP_VIDEO_MARGEM_PLAYLIST: PT5S
      # Sem chamar a Hotmart de verdade: o "Testar credenciais" recebe uma recusa
      APP_PAGAMENTOS_HOTMART_URL_TOKEN: http://mailpit:8025/hotmart-indisponivel

  worker:
    image: plataforma-cursos-worker:e2e
    build:
      context: ../backend
      target: worker
    depends_on:
      mailpit:
        condition: service_healthy

  mailpit:
    image: axllent/mailpit:v1.31.6
    ports:
      - "127.0.0.1:${E2E_MAILPIT_PORTA:?defina E2E_MAILPIT_PORTA}:8025"
    healthcheck:
      test: ["CMD", "/mailpit", "readyz"]
      interval: 5s
      timeout: 3s
      retries: 20
`````

`infra/compose.prod.yml`:

`````yaml
# Stack de um ambiente dos servidores (produção ou homologação): API, worker, fronts (web), gerador
# do site público (site), PostgreSQL e Garage.
# Imagens publicadas no GHCR com a tag do commit (TAG); nenhuma porta publicada. O Caddy (projeto
# próprio, em caddy/) chega à API, aos fronts e ao Garage pela rede plataforma-cursos-<AMBIENTE>,
# pelos apelidos <AMBIENTE>-api, <AMBIENTE>-web e <AMBIENTE>-garage. Todas as variáveis vêm do .env do ambiente.
#
#   Produção:    docker compose -p plataforma-cursos-producao -f compose.prod.yml up -d --wait
#   Homologação: docker compose -p plataforma-cursos-homolog -f compose.prod.yml -f compose.homolog.yml up -d --wait

# Configuração compartilhada pela API e pelo worker
x-app: &app
  DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:?defina POSTGRES_DB}
  DB_USERNAME: ${POSTGRES_USER:?defina POSTGRES_USER}
  DB_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD}
  JWT_SEGREDO: ${JWT_SEGREDO:?defina JWT_SEGREDO}
  AUTH_ORIGENS: ${AUTH_ORIGENS:?defina AUTH_ORIGENS}
  FRONT_ALUNO_URL: ${FRONT_ALUNO_URL:?defina FRONT_ALUNO_URL}
  S3_ENDPOINT: http://garage:3900
  S3_ENDPOINT_PUBLICO: ${S3_ENDPOINT_PUBLICO:?defina S3_ENDPOINT_PUBLICO}
  S3_BUCKET: ${S3_BUCKET:?defina S3_BUCKET}
  S3_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY}
  S3_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY}
  S3_CORS_ORIGENS: ${S3_CORS_ORIGENS:?defina S3_CORS_ORIGENS}
  SMTP_HOST: ${SMTP_HOST:?defina SMTP_HOST}
  SMTP_PORT: ${SMTP_PORT:-587}
  SMTP_USERNAME: ${SMTP_USERNAME:-}
  SMTP_PASSWORD: ${SMTP_PASSWORD:-}
  SMTP_AUTH: ${SMTP_AUTH:-true}
  SMTP_STARTTLS: ${SMTP_STARTTLS:-true}
  EMAIL_REMETENTE: ${EMAIL_REMETENTE:?defina EMAIL_REMETENTE}
  LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs
  # Alertas do worker por e-mail (jobs com erro, disco quase cheio, chargeback); em branco, desligados
  ALERTA_EMAIL: ${ALERTA_EMAIL:-}
  # Chave dos segredos dos meios de pagamento; sem ela, o painel não grava credenciais
  PAGAMENTOS_CHAVE: ${PAGAMENTOS_CHAVE:-}
  APP_PAGAMENTOS_RECONCILIACAO_ATIVA: ${PAGAMENTOS_RECONCILIACAO:-false}

# Logs do Docker com rotação: no máximo 5 arquivos de 10 MB por container
x-logging: &logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "5"

services:
  api:
    logging: *logging
    image: ${IMAGEM_API:-ghcr.io/celkecursos/plataforma-cursos-api}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      <<: *app
      JAVA_TOOL_OPTIONS: -Xmx512m
    depends_on:
      postgres:
        condition: service_healthy
      garage:
        condition: service_healthy
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-api
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 5s
      timeout: 3s
      start_period: 30s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 1G

  worker:
    logging: *logging
    image: ${IMAGEM_WORKER:-ghcr.io/celkecursos/plataforma-cursos-worker}:${TAG:?defina TAG}
    restart: unless-stopped
    # No deploy, o job em andamento tem 30 s para terminar; uma conversão interrompida volta para a fila
    # quando o lease vence (o worker o renova a cada minuto enquanto converte)
    stop_grace_period: 30s
    environment:
      <<: *app
      JAVA_TOOL_OPTIONS: -Xmx768m
    depends_on:
      # As migrations são aplicadas pela API; o worker sobe depois dela
      api:
        condition: service_healthy
    healthcheck:
      # O worker regrava este arquivo a cada 10 s (não tem servidor HTTP para health check). A carência
      # cobre a partida do Spring com pouca CPU (meia CPU na homologação passa de um minuto)
      test: ["CMD-SHELL", "find /tmp/worker-batimento -mmin -1 | grep -q ."]
      interval: 10s
      timeout: 3s
      start_period: 120s
      retries: 3
    deploy:
      resources:
        limits:
          # A transcodificação (ffmpeg) não pode tomar a CPU toda da VPS
          cpus: "1.5"
          memory: 2G

  # Área do aluno (porta 8081) e ADM (8082), estáticos; a API é chamada no mesmo domínio (/api)
  web:
    logging: *logging
    image: ${IMAGEM_WEB:-ghcr.io/celkecursos/plataforma-cursos-web}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      # Origem dos vídeos e arquivos na Content-Security-Policy dos fronts
      MIDIA_ORIGEM: ${S3_ENDPOINT_PUBLICO:?defina S3_ENDPOINT_PUBLICO}
    volumes:
      - site:/srv/site:ro
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-web
    deploy:
      resources:
        limits:
          memory: 64M

  # Gera o site público (cursos.*) a partir da API ao subir e a cada SITE_INTERVALO segundos
  site:
    logging: *logging
    image: ${IMAGEM_SITE:-ghcr.io/celkecursos/plataforma-cursos-site}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      API_URL: http://api:8080
      FRONT_ALUNO_URL: ${FRONT_ALUNO_URL:?defina FRONT_ALUNO_URL}
      # Em branco, deriva do FRONT_ALUNO_URL (aluno.celke.uno → cursos.celke.uno)
      SITE_URL: ${SITE_URL:-}
      SITE_INDEXAVEL: ${SITE_INDEXAVEL:-true}
      SITE_INTERVALO: ${SITE_INTERVALO:-300}
    volumes:
      - site:/site
    depends_on:
      api:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 128M

  postgres:
    logging: *logging
    image: postgres:18.6-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB:?defina POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER:?defina POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD}
    volumes:
      - postgres:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 1G

  garage:
    logging: *logging
    image: dxflrs/garage:v2.4.1
    restart: unless-stopped
    command: ["/garage", "server", "--single-node", "--default-bucket"]
    environment:
      GARAGE_RPC_SECRET: ${GARAGE_RPC_SECRET:?defina GARAGE_RPC_SECRET}
      GARAGE_ADMIN_TOKEN: ${GARAGE_ADMIN_TOKEN:?defina GARAGE_ADMIN_TOKEN}
      GARAGE_DEFAULT_BUCKET: ${S3_BUCKET:?defina S3_BUCKET}
      GARAGE_DEFAULT_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY}
      GARAGE_DEFAULT_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY}
    volumes:
      - ./garage/garage.toml:/etc/garage.toml:ro
      - garage:/var/lib/garage
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-garage
    healthcheck:
      test: ["CMD", "/garage", "status"]
      interval: 5s
      timeout: 5s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 768M

networks:
  default:
    name: plataforma-cursos-${AMBIENTE:?defina AMBIENTE}

volumes:
  postgres:
  garage:
  site:
`````

`infra/compose.yml`:

`````yaml
# Ambiente local. Uso, na raiz do repositório:
#   ./infra/scripts/gerar-env.sh          (uma vez: cria infra/.env com segredos aleatórios)
#   docker compose -f infra/compose.yml up -d --build --wait
name: plataforma-cursos

# Configuração compartilhada pela API e pelo worker
x-app: &app
  DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:?defina POSTGRES_DB no infra/.env}
  DB_USERNAME: ${POSTGRES_USER:?defina POSTGRES_USER no infra/.env}
  DB_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD no infra/.env}
  JWT_SEGREDO: ${JWT_SEGREDO:?defina JWT_SEGREDO no infra/.env}
  # Origens aceitas no refresh e no logout: o Swagger da própria API e o front de desenvolvimento
  AUTH_ORIGENS: ${AUTH_ORIGENS:-http://localhost:${API_PORTA:-8080},http://localhost:5173}
  # Área do aluno: base dos links de confirmação de e-mail e de recuperação de senha
  FRONT_ALUNO_URL: ${FRONT_ALUNO_URL:-http://localhost:5173}
  S3_ENDPOINT: http://garage:3900
  # Endereço que o navegador usa nas URLs pré-assinadas
  S3_ENDPOINT_PUBLICO: http://localhost:${S3_PORTA:-3900}
  S3_BUCKET: ${S3_BUCKET:?defina S3_BUCKET no infra/.env}
  S3_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY no infra/.env}
  S3_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY no infra/.env}
  S3_CORS_ORIGENS: ${S3_CORS_ORIGENS:-}
  # No desenvolvimento os e-mails ficam no Mailpit (http://localhost:8025)
  SMTP_HOST: mailpit
  SMTP_PORT: "1025"
  SMTP_AUTH: "false"
  SMTP_STARTTLS: "false"
  EMAIL_REMETENTE: ${EMAIL_REMETENTE:?defina EMAIL_REMETENTE no infra/.env}
  # Chave dos segredos dos meios de pagamento; sem ela, o painel não grava credenciais
  PAGAMENTOS_CHAVE: ${PAGAMENTOS_CHAVE:-}
  # Logs em JSON (ECS), com o id de correlação de cada requisição
  LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs

services:
  postgres:
    image: postgres:18.6-alpine
    environment:
      POSTGRES_DB: ${POSTGRES_DB:?defina POSTGRES_DB no infra/.env}
      POSTGRES_USER: ${POSTGRES_USER:?defina POSTGRES_USER no infra/.env}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD no infra/.env}
    volumes:
      # A partir da versão 18 os dados ficam em /var/lib/postgresql/18/docker
      - postgres:/var/lib/postgresql
    ports:
      - "127.0.0.1:${POSTGRES_PORTA:-5432}:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 20

  garage:
    image: dxflrs/garage:v2.4.1
    # Na primeira subida cria o layout de nó único, o bucket e a chave da aplicação
    command: ["/garage", "server", "--single-node", "--default-bucket"]
    environment:
      GARAGE_RPC_SECRET: ${GARAGE_RPC_SECRET:?defina GARAGE_RPC_SECRET no infra/.env}
      GARAGE_ADMIN_TOKEN: ${GARAGE_ADMIN_TOKEN:?defina GARAGE_ADMIN_TOKEN no infra/.env}
      GARAGE_DEFAULT_BUCKET: ${S3_BUCKET:?defina S3_BUCKET no infra/.env}
      GARAGE_DEFAULT_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY no infra/.env}
      GARAGE_DEFAULT_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY no infra/.env}
    volumes:
      - ./garage/garage.toml:/etc/garage.toml:ro
      - garage:/var/lib/garage
    ports:
      - "127.0.0.1:${S3_PORTA:-3900}:3900"
    healthcheck:
      test: ["CMD", "/garage", "status"]
      interval: 5s
      timeout: 5s
      retries: 20

  mailpit:
    image: axllent/mailpit:v1.31.6
    ports:
      - "127.0.0.1:${MAILPIT_PORTA:-8025}:8025"   # painel web
    healthcheck:
      test: ["CMD", "/mailpit", "readyz"]
      interval: 5s
      timeout: 3s
      retries: 20

  api:
    build:
      context: ../backend
      target: api
    environment:
      <<: *app
      DOCS_ENABLED: "true"
    ports:
      - "127.0.0.1:${API_PORTA:-8080}:8080"
    depends_on:
      postgres:
        condition: service_healthy
      garage:
        condition: service_healthy
      mailpit:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 5s
      timeout: 3s
      start_period: 30s
      retries: 20

  worker:
    build:
      context: ../backend
      target: worker
    # No deploy, o job em andamento tem 30 s para terminar; uma conversão interrompida volta para a fila
    # quando o lease vence (o worker o renova a cada minuto enquanto converte)
    stop_grace_period: 30s
    environment:
      <<: *app
    depends_on:
      # As migrations são aplicadas pela API; o worker sobe depois dela
      api:
        condition: service_healthy
    healthcheck:
      # O worker regrava este arquivo a cada 10 s (não tem servidor HTTP para health check). A carência
      # cobre a partida do Spring com pouca CPU (meia CPU na homologação passa de um minuto)
      test: ["CMD-SHELL", "find /tmp/worker-batimento -mmin -1 | grep -q ."]
      interval: 10s
      timeout: 3s
      start_period: 120s
      retries: 3

volumes:
  postgres:
  garage:
`````

`infra/e2e.env`:

`````bash
# Variáveis da stack de e2e. Valores só de teste (nenhum é usado em servidor): podem ficar no git.
AMBIENTE=e2e
TAG=e2e
E2E_PORTA=8088
E2E_MAILPIT_PORTA=8026

POSTGRES_DB=plataforma_cursos
POSTGRES_USER=plataforma_cursos
POSTGRES_PASSWORD=senha-do-banco-e2e

JWT_SEGREDO=l4jEHfbYeLV+W8Nf8kJv9QeHkLLCCPJdCLS7pqIJFk5eTx/DoYnOd476xZTrRgMX

GARAGE_RPC_SECRET=066fb7d1d0a13a90272747cd99d0474d26e520e2d6b214200236a01299618731
GARAGE_ADMIN_TOKEN=token-do-admin-e2e
S3_BUCKET=midia
S3_ACCESS_KEY=GK0a51c4026047d3d88c8c6435
S3_SECRET_KEY=292c9b355552db653bcf18f785fda32855125263d3d912ea8658305d5c00d206

AUTH_ORIGENS=http://aluno.localhost:8088,http://adm.localhost:8088
FRONT_ALUNO_URL=http://aluno.localhost:8088
S3_ENDPOINT_PUBLICO=http://media.localhost:8088
S3_CORS_ORIGENS=http://aluno.localhost:8088,http://adm.localhost:8088

SMTP_HOST=mailpit
SMTP_PORT=1025
SMTP_AUTH=false
SMTP_STARTTLS=false
EMAIL_REMETENTE=nao-responda@teste.celke.uno

SEED_E2E_SENHA='123456A#b123'

PAGAMENTOS_CHAVE=ZTJlLWNoYXZlLWRlLXBhZ2FtZW50b3MtMzItYnl0ZXM=
`````

`infra/scripts/gerar-env-servidor.sh`:

`````bash
#!/usr/bin/env bash
# Gera o .env de um ambiente da VPS com segredos aleatórios. Hosts seguem os subdomínios de
# celke.uno (homolog-* na homologação). SMTP e remetente ficam para preencher com os dados da Iagente.
# Não sobrescreve um arquivo existente.
#
#   gerar-env-servidor.sh producao /opt/plataforma-cursos/producao/.env
#   gerar-env-servidor.sh homolog  /opt/plataforma-cursos/homolog/.env
set -euo pipefail

AMBIENTE=${1:?uso: gerar-env-servidor.sh <producao|homolog> <arquivo>}
DESTINO=${2:?uso: gerar-env-servidor.sh <producao|homolog> <arquivo>}

case "$AMBIENTE" in
  producao) PREFIXO="" ;;
  homolog) PREFIXO="homolog-" ;;
  *) echo "Ambiente inválido: $AMBIENTE (use producao ou homolog)" >&2; exit 1 ;;
esac

if [[ -e "$DESTINO" ]]; then
  echo "$DESTINO já existe; nada foi alterado." >&2
  exit 1
fi

hex() { openssl rand -hex "$1"; }

ALUNO="https://${PREFIXO}aluno.celke.uno"
ADM="https://${PREFIXO}adm.celke.uno"
MEDIA="https://${PREFIXO}media.celke.uno"

umask 077
cat > "$DESTINO" <<ENV
# Ambiente: $AMBIENTE
AMBIENTE=$AMBIENTE
# Tag (SHA do commit) das imagens; o deploy grava a tag de cada versão
TAG=

POSTGRES_DB=plataforma_cursos
POSTGRES_USER=plataforma_cursos
POSTGRES_PASSWORD=$(hex 24)

JWT_SEGREDO=$(openssl rand -base64 48)
AUTH_ORIGENS=$ALUNO,$ADM
FRONT_ALUNO_URL=$ALUNO

# Chave dos segredos dos meios de pagamento (trocar exige informar os segredos de novo no painel)
PAGAMENTOS_CHAVE=$(openssl rand -base64 32)
# Reconciliação diária pela API do meio de pagamento (precisa das credenciais da API no painel)
PAGAMENTOS_RECONCILIACAO=false

GARAGE_RPC_SECRET=$(hex 32)
GARAGE_ADMIN_TOKEN=$(hex 32)
S3_BUCKET=midia
S3_ACCESS_KEY=GK$(hex 12)
S3_SECRET_KEY=$(hex 32)
S3_ENDPOINT_PUBLICO=$MEDIA
S3_CORS_ORIGENS=$ALUNO,$ADM

# SMTP da Iagente (dados do painel)
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_AUTH=true
SMTP_STARTTLS=true
EMAIL_REMETENTE=nao-responda@celke.uno

# Alertas do worker (jobs com erro, disco quase cheio); em branco, desligados
ALERTA_EMAIL=
ENV

chmod 600 "$DESTINO"
echo "$DESTINO criado. Preencha SMTP_HOST, SMTP_USERNAME e SMTP_PASSWORD."
`````

`infra/scripts/gerar-env.sh`:

`````bash
#!/usr/bin/env bash
# Cria infra/.env a partir do infra/.env.example, preenchendo os segredos com valores aleatórios.
# Não sobrescreve um infra/.env existente.
set -euo pipefail

INFRA="$(cd "$(dirname "$0")/.." && pwd)"
DESTINO="$INFRA/.env"

if [[ -e "$DESTINO" ]]; then
  echo "infra/.env já existe; nada foi alterado." >&2
  exit 1
fi

hex() { openssl rand -hex "$1"; }

sed -E \
  -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(hex 24)|" \
  -e "s|^JWT_SEGREDO=.*|JWT_SEGREDO=$(openssl rand -base64 48)|" \
  -e "s|^PAGAMENTOS_CHAVE=.*|PAGAMENTOS_CHAVE=$(openssl rand -base64 32)|" \
  -e "s|^GARAGE_RPC_SECRET=.*|GARAGE_RPC_SECRET=$(hex 32)|" \
  -e "s|^GARAGE_ADMIN_TOKEN=.*|GARAGE_ADMIN_TOKEN=$(hex 32)|" \
  -e "s|^S3_ACCESS_KEY=.*|S3_ACCESS_KEY=GK$(hex 12)|" \
  -e "s|^S3_SECRET_KEY=.*|S3_SECRET_KEY=$(hex 32)|" \
  "$INFRA/.env.example" > "$DESTINO"

chmod 600 "$DESTINO"
echo "infra/.env criado com segredos aleatórios."
`````

`web/adm/src/caminhos.ts`:

`````ts
export const CAMINHOS = {
  entrar: '/entrar',
  inicio: '/',
  cursos: '/cursos',
  termos: '/termos',
  meiosPagamento: '/pagamentos',
  pedidos: '/pagamentos/pedidos',
  eventos: '/pagamentos/eventos',
} as const;

export const caminhoPedido = (pedidoId: number | string) => `/pagamentos/pedidos/${pedidoId}`;

export const caminhoCurso = (cursoId: number | string) => `/cursos/${cursoId}`;

export const caminhoTurma = (cursoId: number | string, turmaId: number | string) =>
  `/cursos/${cursoId}/turmas/${turmaId}`;

export const caminhoAula = (
  cursoId: number | string,
  turmaId: number | string,
  moduloId: number | string,
  aulaId: number | string,
) => `/cursos/${cursoId}/turmas/${turmaId}/modulos/${moduloId}/aulas/${aulaId}`;
`````

`web/adm/src/layout/LayoutAdm.tsx`:

`````tsx
import { Estrutura, type SecaoNav } from '@celke/kole-react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';

export const NAV: SecaoNav[] = [
  { itens: [{ rotulo: 'Painel', icone: 'house', rota: CAMINHOS.inicio }] },
  {
    rotulo: 'Conteúdo',
    itens: [
      { rotulo: 'Cursos', icone: 'graduation-cap', rota: CAMINHOS.cursos },
      { rotulo: 'Termos', icone: 'file-text', rota: CAMINHOS.termos },
    ],
  },
  {
    rotulo: 'Pagamentos',
    itens: [
      { rotulo: 'Meios de pagamento', icone: 'credit-card', rota: CAMINHOS.meiosPagamento },
      { rotulo: 'Pedidos', icone: 'receipt', rota: CAMINHOS.pedidos },
      { rotulo: 'Eventos recebidos', icone: 'inbox', rota: CAMINHOS.eventos },
    ],
  },
];

export function LayoutAdm() {
  const { estado, sair } = useSessao();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const nome = estado.status === 'autenticado' ? estado.usuario.nome : '';

  return (
    <Estrutura
      marca={MARCA}
      nav={NAV}
      rotaAtual={pathname}
      aoNavegar={navigate}
      renderLink={(p) => (
        <Link
          to={p.href}
          className={p.className}
          onClick={p.onClick}
          aria-current={p['aria-current']}
        >
          {p.children}
        </Link>
      )}
      usuario={{ nome, cargo: 'Administrador' }}
      menuUsuario={[{ id: 'sair', rotulo: 'Sair', icone: 'log-out', destrutivo: true }]}
      aoEscolherMenuUsuario={(id) => {
        if (id === 'sair') void sair().then(() => navigate(CAMINHOS.entrar, { replace: true }));
      }}
    >
      <Outlet />
    </Estrutura>
  );
}
`````

`web/adm/src/paginas/turma/TurmaPagina.tsx`:

`````tsx
import { Aba, Abas, Alerta, Carregando, Status } from '@celke/kole-react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { caminhoCurso } from '../../caminhos.ts';
import { STATUS_TURMA, type Curso, type Turma } from '../../tipos.ts';
import { ConfiguracaoTurmaAba } from './ConfiguracaoTurmaAba.tsx';
import { ConteudoAba } from './ConteudoAba.tsx';
import { ForumAba } from './ForumAba.tsx';
import { MatriculasAba } from './MatriculasAba.tsx';
import { VendaAba } from './VendaAba.tsx';

export interface ContextoTurma {
  curso: Curso;
  turma: Turma;
}

/** Uma turma: conteúdo, matrículas, venda, fórum e configurações. A API não tem GET da turma: vem da lista do curso. */
export function TurmaPagina() {
  const { cursoId, turmaId } = useParams();
  const navigate = useNavigate();
  const [parametros, setParametros] = useSearchParams();
  const contexto = useCarregar(async (): Promise<ContextoTurma> => {
    const [curso, turmas] = await Promise.all([
      api.get<Curso>(`/api/adm/cursos/${cursoId}`),
      api.get<Turma[]>(`/api/adm/cursos/${cursoId}/turmas`),
    ]);
    const turma = turmas.find((t) => String(t.id) === turmaId);
    if (!turma) throw new ErroApi(404, { detail: 'Turma não encontrada neste curso.' });
    return { curso, turma };
  }, [cursoId, turmaId]);

  if (contexto.erro && !contexto.dados) {
    return (
      <div className="pc-pagina">
        <Alerta severidade="err" titulo="Não foi possível abrir a turma" mensagem={contexto.erro} />
      </div>
    );
  }
  if (!contexto.dados) return <Carregando mensagem="Carregando a turma..." />;
  const { curso, turma } = contexto.dados;
  const voltar = () => navigate(`${caminhoCurso(curso.id)}?aba=turmas`);

  return (
    <div className="pc-pagina">
      <Abas
        titulo={turma.nome}
        identidade={curso.titulo}
        aoVoltar={voltar}
        acoes={
          <Status
            rotulo={STATUS_TURMA[turma.status].rotulo}
            severidade={STATUS_TURMA[turma.status].severidade}
          />
        }
        ativa={parametros.get('aba') ?? 'conteudo'}
        aoMudar={(aba) => setParametros({ aba }, { replace: true })}
        rotulo="Seções da turma"
      >
        <Aba valor="conteudo" rotulo="Conteúdo" icone="layers">
          <ConteudoAba curso={curso} turma={turma} />
        </Aba>
        <Aba valor="matriculas" rotulo="Matrículas" icone="users">
          <MatriculasAba turma={turma} />
        </Aba>
        <Aba valor="venda" rotulo="Venda" icone="tag">
          <VendaAba turma={turma} />
        </Aba>
        <Aba valor="forum" rotulo="Fórum" icone="message-square">
          <ForumAba turma={turma} />
        </Aba>
        <Aba valor="configuracao" rotulo="Configurações" icone="settings">
          <ConfiguracaoTurmaAba
            turma={turma}
            aoMudar={(nova) => contexto.definir({ curso, turma: nova })}
            aoExcluir={voltar}
          />
        </Aba>
      </Abas>
    </div>
  );
}
`````

`web/adm/src/rotas.tsx`:

`````tsx
import { createBrowserRouter } from 'react-router';
import { RotaPrivada } from '@compartilhado/sessao/RotaPrivada.tsx';
import { CAMINHOS } from './caminhos.ts';
import { LayoutAdm } from './layout/LayoutAdm.tsx';
import { SemPermissao } from './layout/SemPermissao.tsx';
import { AulaPagina } from './paginas/aula/AulaPagina.tsx';
import { CursoPagina } from './paginas/curso/CursoPagina.tsx';
import { CursosPagina } from './paginas/cursos/CursosPagina.tsx';
import { EntrarPagina } from './paginas/EntrarPagina.tsx';
import { PainelPagina } from './paginas/PainelPagina.tsx';
import { EventosPagina } from './paginas/pagamentos/EventosPagina.tsx';
import { MeiosPagamentoPagina } from './paginas/pagamentos/MeiosPagamentoPagina.tsx';
import { PedidoPagina } from './paginas/pagamentos/PedidoPagina.tsx';
import { PedidosPagina } from './paginas/pagamentos/PedidosPagina.tsx';
import { TermosPagina } from './paginas/termos/TermosPagina.tsx';
import { TurmaPagina } from './paginas/turma/TurmaPagina.tsx';

export const roteador = createBrowserRouter([
  { path: CAMINHOS.entrar, element: <EntrarPagina /> },
  {
    element: (
      <RotaPrivada login={CAMINHOS.entrar} papel="ADMIN" semPermissao={<SemPermissao />}>
        <LayoutAdm />
      </RotaPrivada>
    ),
    children: [
      { index: true, element: <PainelPagina /> },
      { path: 'cursos', element: <CursosPagina /> },
      { path: 'cursos/:cursoId', element: <CursoPagina /> },
      { path: 'cursos/:cursoId/turmas/:turmaId', element: <TurmaPagina /> },
      {
        path: 'cursos/:cursoId/turmas/:turmaId/modulos/:moduloId/aulas/:aulaId',
        element: <AulaPagina />,
      },
      { path: 'termos', element: <TermosPagina /> },
      { path: 'pagamentos', element: <MeiosPagamentoPagina /> },
      { path: 'pagamentos/pedidos', element: <PedidosPagina /> },
      { path: 'pagamentos/pedidos/:pedidoId', element: <PedidoPagina /> },
      { path: 'pagamentos/eventos', element: <EventosPagina /> },
    ],
  },
  { path: '*', element: <SemPermissao titulo="Página não encontrada" /> },
]);
`````

`web/adm/src/tipos.ts`:

`````ts
import type { Severidade } from '@celke/kole-react';

/** Respostas da API de gestão (/api/adm), espelhando os records do backend. */

export type StatusCurso = 'RASCUNHO' | 'PUBLICADO' | 'ARQUIVADO';
export type StatusTurma = 'PREPARACAO' | 'ATIVA' | 'ENCERRADA';
export type StatusConteudo = 'ATIVO' | 'INATIVO';
export type StatusVideo = 'AGUARDANDO_ENVIO' | 'PENDENTE' | 'PROCESSANDO' | 'PRONTO' | 'ERRO';
export type SituacaoMatricula = 'AGUARDANDO_INICIO' | 'VIGENTE' | 'VENCIDA' | 'CANCELADA';
export type FormaPagamento = 'MANUAL' | 'CORTESIA' | 'PIX' | 'CARTAO' | 'BOLETO' | 'OUTRO';
export type TipoTermo = 'TERMOS_USO' | 'POLITICA_PRIVACIDADE';

export interface Curso {
  id: number;
  titulo: string;
  slug: string;
  resumo: string;
  descricao: string | null;
  status: StatusCurso;
  capaUrl: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface Turma {
  id: number;
  cursoId: number;
  nome: string;
  status: StatusTurma;
  percentualConclusao: number;
  cargaHorariaHoras: number | null;
}

export interface Modulo {
  id: number;
  turmaId: number;
  titulo: string;
  ordem: number;
  status: StatusConteudo;
}

export interface Aula {
  id: number;
  moduloId: number;
  titulo: string;
  descricao: string | null;
  ordem: number;
  gratuita: boolean;
  status: StatusConteudo;
}

export interface Video {
  aulaId: number;
  status: StatusVideo;
  publicado: boolean;
  nomeOriginal: string | null;
  tamanhoOriginal: number | null;
  tamanhoFinal: number | null;
  reducaoPercentual: number | null;
  duracaoSegundos: number | null;
  largura: number | null;
  altura: number | null;
  variantes: string[];
  segundosProcessamentoPorMinuto: number | null;
  thumbnailUrl: string | null;
  erro: string | null;
  processadoEm: string | null;
  atualizadoEm: string;
}

export interface EnvioVideo {
  tamanhoParte: number;
  partes: { numero: number; url: string }[];
  expiraEm: string;
}

/** Envio de um arquivo só (capa, arquivo de apoio): PUT na url com o Content-Type informado. */
export interface EnvioSimples {
  chave: string;
  url: string;
  contentType: string;
  expiraEm: string;
}

export interface ArquivoApoio {
  id: number;
  aulaId: number | null;
  moduloId: number | null;
  titulo: string;
  nomeArquivo: string;
  contentType: string;
  tamanho: number;
  criadoEm: string;
}

export interface Matricula {
  id: number;
  turmaId: number;
  usuarioId: number;
  nome: string;
  email: string;
  inicio: string;
  fim: string | null;
  status: 'ATIVA' | 'CANCELADA';
  situacao: SituacaoMatricula;
  formaPagamento: FormaPagamento;
  transacao: string | null;
}

export interface TopicoResumo {
  id: number;
  turmaId: number;
  aulaId: number | null;
  titulo: string;
  autorNome: string;
  fixado: boolean;
  respondidoInstrutor: boolean;
  respostas: number;
  oculto: boolean | null;
  criadoEm: string;
}

export interface Anexo {
  id: number;
  url: string;
}

export interface Resposta {
  id: number;
  corpoHtml: string;
  autorNome: string;
  instrutor: boolean;
  oculto: boolean | null;
  criadoEm: string;
  anexos: Anexo[];
}

export interface Topico extends Omit<TopicoResumo, 'respostas'> {
  corpoHtml: string;
  anexos: Anexo[];
  respostas: Resposta[];
}

export interface Termo {
  id: number;
  tipo: TipoTermo;
  versao: number;
  conteudoHtml: string;
  publicadoEm: string;
}

export type StatusPedido =
  | 'CRIADO'
  | 'AGUARDANDO_PAGAMENTO'
  | 'APROVADO'
  | 'CONCLUIDO'
  | 'EM_DISPUTA'
  | 'ATRASADO'
  | 'REEMBOLSADO'
  | 'CHARGEBACK'
  | 'CANCELADO'
  | 'EXPIRADO';
export type StatusEvento = 'RECEBIDO' | 'PROCESSADO' | 'IGNORADO' | 'ERRO';

/** Campo da configuração de um meio de pagamento (o formulário é montado a partir daqui). */
export interface CampoConfiguracao {
  chave: string;
  rotulo: string;
  tipo: 'TEXTO' | 'URL' | 'SELECAO';
  secreto: boolean;
  obrigatorio: boolean;
  dica: string | null;
  padrao: string | null;
  opcoes: string[];
}

export interface TipoGateway {
  tipo: string;
  nome: string;
  campos: CampoConfiguracao[];
  capacidades: string[];
}

export interface TiposGateway {
  cifraDisponivel: boolean;
  tipos: TipoGateway[];
}

export interface SegredoResumo {
  configurado: boolean;
  final4: string | null;
}

export interface Gateway {
  id: number;
  tipo: string;
  nomeTipo: string;
  nome: string;
  ativo: boolean;
  valores: Record<string, string>;
  segredos: Record<string, SegredoResumo>;
  segredosLegiveis: boolean;
  webhookUrl: string;
  criadoEm: string;
  atualizadoEm: string;
}

export interface ResultadoTeste {
  ok: boolean;
  mensagem: string;
}

export interface Oferta {
  id: number;
  turmaId: number;
  gatewayId: number;
  gatewayNome: string | null;
  gatewayAtivo: boolean;
  produtoExterno: string;
  ofertaExterna: string;
  codigoCheckout: string | null;
  titulo: string;
  precoCentavos: number;
  moeda: string;
  diasAcesso: number | null;
  ativa: boolean;
  criadoEm: string;
}

export interface Pedido {
  id: number;
  codigo: string;
  status: StatusPedido;
  origem: 'CHECKOUT' | 'EXTERNO';
  email: string;
  nome: string | null;
  usuarioId: number | null;
  gatewayId: number;
  ofertaId: number;
  ofertaTitulo: string | null;
  turmaId: number | null;
  transacaoExterna: string | null;
  valorCentavos: number | null;
  moeda: string | null;
  formaPagamento: FormaPagamento | null;
  matriculaId: number | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface EventoPagamento {
  id: number;
  gatewayId: number;
  idExterno: string;
  tipoOriginal: string;
  status: StatusEvento;
  recebidoEm: string;
  ocorridoEm: string | null;
  transacao: string | null;
  erro: string | null;
  pedidoId: number | null;
  processadoEm: string | null;
}

export interface HistoricoPedido {
  id: number;
  de: StatusPedido | null;
  para: StatusPedido;
  eventoId: number | null;
  motivo: string | null;
  em: string;
}

export interface PedidoDetalhe {
  pedido: Pedido;
  historico: HistoricoPedido[];
  eventos: EventoPagamento[];
}

export interface EventoDetalhe {
  evento: EventoPagamento;
  payload: string | null;
}

interface Rotulo {
  rotulo: string;
  severidade: Severidade;
}

export const STATUS_CURSO: Record<StatusCurso, Rotulo> = {
  RASCUNHO: { rotulo: 'Rascunho', severidade: 'neutral' },
  PUBLICADO: { rotulo: 'Publicado', severidade: 'ok' },
  ARQUIVADO: { rotulo: 'Arquivado', severidade: 'warn' },
};

export const STATUS_TURMA: Record<StatusTurma, Rotulo> = {
  PREPARACAO: { rotulo: 'Em preparação', severidade: 'neutral' },
  ATIVA: { rotulo: 'Ativa', severidade: 'ok' },
  ENCERRADA: { rotulo: 'Encerrada', severidade: 'warn' },
};

export const STATUS_CONTEUDO: Record<StatusConteudo, Rotulo> = {
  ATIVO: { rotulo: 'Ativo', severidade: 'ok' },
  INATIVO: { rotulo: 'Inativo', severidade: 'neutral' },
};

export const STATUS_VIDEO: Record<StatusVideo, Rotulo> = {
  AGUARDANDO_ENVIO: { rotulo: 'Aguardando envio', severidade: 'neutral' },
  PENDENTE: { rotulo: 'Na fila', severidade: 'info' },
  PROCESSANDO: { rotulo: 'Processando', severidade: 'info' },
  PRONTO: { rotulo: 'Pronto', severidade: 'ok' },
  ERRO: { rotulo: 'Erro', severidade: 'err' },
};

export const SITUACAO_MATRICULA: Record<SituacaoMatricula, Rotulo> = {
  AGUARDANDO_INICIO: { rotulo: 'Aguardando início', severidade: 'info' },
  VIGENTE: { rotulo: 'Vigente', severidade: 'ok' },
  VENCIDA: { rotulo: 'Vencida', severidade: 'warn' },
  CANCELADA: { rotulo: 'Cancelada', severidade: 'err' },
};

export const FORMAS_PAGAMENTO: Record<FormaPagamento, string> = {
  MANUAL: 'Manual',
  CORTESIA: 'Cortesia',
  PIX: 'Pix',
  CARTAO: 'Cartão',
  BOLETO: 'Boleto',
  OUTRO: 'Outro',
};

export const STATUS_PEDIDO: Record<StatusPedido, Rotulo> = {
  CRIADO: { rotulo: 'Criado', severidade: 'neutral' },
  AGUARDANDO_PAGAMENTO: { rotulo: 'Aguardando pagamento', severidade: 'info' },
  APROVADO: { rotulo: 'Aprovado', severidade: 'ok' },
  CONCLUIDO: { rotulo: 'Concluído', severidade: 'ok' },
  EM_DISPUTA: { rotulo: 'Em disputa', severidade: 'warn' },
  ATRASADO: { rotulo: 'Atrasado', severidade: 'warn' },
  REEMBOLSADO: { rotulo: 'Reembolsado', severidade: 'err' },
  CHARGEBACK: { rotulo: 'Chargeback', severidade: 'err' },
  CANCELADO: { rotulo: 'Cancelado', severidade: 'err' },
  EXPIRADO: { rotulo: 'Expirado', severidade: 'neutral' },
};

export const STATUS_EVENTO: Record<StatusEvento, Rotulo> = {
  RECEBIDO: { rotulo: 'Na fila', severidade: 'info' },
  PROCESSADO: { rotulo: 'Processado', severidade: 'ok' },
  IGNORADO: { rotulo: 'Ignorado', severidade: 'warn' },
  ERRO: { rotulo: 'Erro', severidade: 'err' },
};

export const TIPOS_TERMO: Record<TipoTermo, string> = {
  TERMOS_USO: 'Termos de uso',
  POLITICA_PRIVACIDADE: 'Política de privacidade',
};

export function opcoes<V extends string>(mapa: Record<V, Rotulo | string>) {
  return (Object.keys(mapa) as V[]).map((valor) => {
    const item = mapa[valor];
    return { valor, rotulo: typeof item === 'string' ? item : item.rotulo };
  });
}
`````

`web/aluno/src/caminhos.ts`:

`````ts
/** Caminhos da área do aluno; os de conta são os mesmos dos links que a API manda por e-mail. */
export const CAMINHOS = {
  entrar: '/entrar',
  cadastro: '/cadastro',
  confirmarEmail: '/confirmar-email',
  recuperarSenha: '/recuperar-senha',
  redefinirSenha: '/redefinir-senha',
  inicio: '/',
  certificados: '/certificados',
  perfil: '/perfil',
  retornoPagamento: '/pagamento/retorno',
} as const;

/** Destino do botão "Comprar" do site. */
export const caminhoComprar = (ofertaId: number | string) => `/comprar/${ofertaId}`;

export const caminhoTurma = (turmaId: number | string) => `/turmas/${turmaId}`;

export const caminhoAula = (turmaId: number | string, aulaId: number | string) =>
  `/turmas/${turmaId}/aulas/${aulaId}`;

export const caminhoTopico = (turmaId: number | string, topicoId: number | string) =>
  `/turmas/${turmaId}/forum/${topicoId}`;
`````

`web/aluno/src/paginas/CadastroPagina.tsx`:

`````tsx
import { Alerta, Botao, CampoEmail, CampoSenha, CampoTexto } from '@celke/kole-react';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Mensagem } from '@compartilhado/api/tipos.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import {
  erroConfirmacao,
  erroEmail,
  erroObrigatorio,
  erroSenhaNova,
  semErros,
} from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../caminhos.ts';

type Erros = Partial<Record<'nome' | 'email' | 'senha' | 'confirmacao', string>>;

/** Cadastro do aluno: a conta só entra depois de confirmar o e-mail (link enviado pela API). */
export function CadastroPagina() {
  const [parametros] = useSearchParams();
  const voltar = parametros.get('voltar');
  const entrar = voltar
    ? `${CAMINHOS.entrar}?voltar=${encodeURIComponent(voltar)}`
    : CAMINHOS.entrar;
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [erros, setErros] = useState<Erros>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const [mensagem, setMensagem] = useState<string>();

  const enviar = async () => {
    const novos: Erros = {
      nome: erroObrigatorio(nome, 'Informe o seu nome.', 120),
      email: erroEmail(email),
      senha: erroSenhaNova(senha),
      confirmacao: erroConfirmacao(senha, confirmacao),
    };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setEnviando(true);
    try {
      const resposta = await api.post<Mensagem>('/api/auth/cadastro', {
        nome: nome.trim(),
        email: email.trim(),
        senha,
      });
      setMensagem(resposta.mensagem);
    } catch (erro) {
      if (erro instanceof ErroApi && erro.erros.length) {
        setErros({
          nome: erro.erroDoCampo('nome'),
          email: erro.erroDoCampo('email'),
          senha: erro.erroDoCampo('senha'),
        });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setEnviando(false);
    }
  };

  if (mensagem) {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Confira o seu e-mail">
        <Alerta severidade="ok" titulo={mensagem}>
          Enviamos um link para <strong>{email.trim()}</strong>. Abra o e-mail e clique no link para
          ativar a conta.
        </Alerta>
        <Link className="pc-link" to={entrar}>
          Depois de confirmar, entre aqui
        </Link>
      </LayoutConta>
    );
  }

  return (
    <LayoutConta
      marca="Plataforma de Cursos"
      titulo="Criar conta"
      descricao="Cadastre-se para acessar seus cursos."
      aoEnviar={enviar}
      rodape={
        <span>
          Já tem conta? <Link to={entrar}>Entrar</Link>
        </span>
      }
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <CampoTexto
        rotulo="Nome"
        valor={nome}
        aoMudar={setNome}
        erro={erros.nome}
        obrigatorio
        autoComplete="name"
      />
      <CampoEmail
        rotulo="E-mail"
        valor={email}
        aoMudar={setEmail}
        erro={erros.email}
        obrigatorio
        autoComplete="email"
      />
      <CampoSenha
        rotulo="Senha"
        valor={senha}
        aoMudar={setSenha}
        erro={erros.senha}
        obrigatorio
        dica="Pelo menos 10 caracteres."
        autoComplete="new-password"
      />
      <CampoSenha
        rotulo="Repita a senha"
        valor={confirmacao}
        aoMudar={setConfirmacao}
        erro={erros.confirmacao}
        obrigatorio
        autoComplete="new-password"
      />
      <Botao type="submit" rotulo="Criar conta" icone="user-plus" carregando={enviando} bloco />
    </LayoutConta>
  );
}
`````

`web/aluno/src/paginas/EntrarPagina.tsx`:

`````tsx
import { TelaLogin } from '@celke/kole-react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { destinoAposLogin } from '@compartilhado/sessao/RotaPrivada.tsx';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';

export function EntrarPagina() {
  const { estado, entrar } = useSessao();
  const navigate = useNavigate();
  const [parametros] = useSearchParams();
  const destino = destinoAposLogin(parametros.get('voltar'));
  // Quem veio de "Comprar" cria a conta e volta para a compra depois de entrar
  const comVoltar = parametros.get('voltar')
    ? `?voltar=${encodeURIComponent(parametros.get('voltar')!)}`
    : '';

  if (estado.status === 'autenticado') return <Navigate to={destino} replace />;

  return (
    <TelaLogin
      marca={MARCA}
      identificador="email"
      titulo="Entre na sua conta"
      descricao={
        parametros.get('senha') === 'alterada'
          ? 'Senha alterada. Entre com a nova senha.'
          : 'Acesse seus cursos, aulas e certificados.'
      }
      apresentacao={{
        chamada: 'Plataforma de Cursos',
        titulo: 'Aprenda no seu ritmo',
        descricao:
          'Aulas em vídeo, materiais de apoio, fórum com o instrutor e certificado ao concluir.',
        destaques: [
          { icone: 'circle-play', texto: 'Aulas em vídeo de qualquer lugar' },
          { icone: 'message-square', texto: 'Dúvidas respondidas no fórum' },
          { icone: 'award', texto: 'Certificado com código de validação' },
        ],
      }}
      linkSenha={<Link to={CAMINHOS.recuperarSenha}>Esqueci minha senha</Link>}
      acoes={
        <p>
          Ainda não tem conta? <Link to={CAMINHOS.cadastro + comVoltar}>Criar conta</Link>
        </p>
      }
      aoEntrar={async ({ usuario, senha }) => {
        try {
          await entrar(usuario.trim(), senha);
          navigate(destino, { replace: true });
        } catch (erro) {
          return { erro: mensagemDeErro(erro) };
        }
      }}
    />
  );
}
`````

`web/aluno/src/paginas/RedefinirSenhaPagina.tsx`:

`````tsx
import { Alerta, Botao, CampoSenha } from '@celke/kole-react';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { erroConfirmacao, erroSenhaNova, semErros } from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../caminhos.ts';

/**
 * Destino do link de recuperação: cria a nova senha (e encerra as sessões abertas). Vindo do e-mail da
 * matrícula paga (`origem=pagamento`), é a primeira senha da conta criada pela compra.
 */
export function RedefinirSenhaPagina() {
  const [parametros] = useSearchParams();
  const token = parametros.get('token');
  const primeiraSenha = parametros.get('origem') === 'pagamento';
  const [senha, setSenha] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [erros, setErros] = useState<{ senha?: string; confirmacao?: string }>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const [pronto, setPronto] = useState(false);

  if (!token) {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Link inválido">
        <Alerta severidade="err" mensagem="Link inválido ou expirado. Peça um novo." />
        <Link className="pc-link" to={CAMINHOS.recuperarSenha}>
          Pedir um novo link
        </Link>
      </LayoutConta>
    );
  }

  if (pronto) {
    return (
      <LayoutConta
        marca="Plataforma de Cursos"
        titulo={primeiraSenha ? 'Senha criada' : 'Senha alterada'}
      >
        <Alerta
          severidade="ok"
          mensagem={
            primeiraSenha
              ? 'Sua senha foi criada. Entre para acessar o seu curso.'
              : 'Sua nova senha já vale. Por segurança, você saiu de todos os dispositivos.'
          }
        />
        <Link className="pc-link" to={CAMINHOS.entrar}>
          Entrar
        </Link>
      </LayoutConta>
    );
  }

  const enviar = async () => {
    const novos = { senha: erroSenhaNova(senha), confirmacao: erroConfirmacao(senha, confirmacao) };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setEnviando(true);
    try {
      await api.post('/api/auth/redefinir-senha', { token, senha });
      setPronto(true);
    } catch (erro) {
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <LayoutConta
      marca="Plataforma de Cursos"
      titulo={primeiraSenha ? 'Crie sua senha' : 'Criar nova senha'}
      descricao={
        primeiraSenha ? 'Sua matrícula está confirmada. Crie a senha para entrar.' : undefined
      }
      aoEnviar={enviar}
      rodape={<Link to={CAMINHOS.recuperarSenha}>Pedir um novo link</Link>}
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <CampoSenha
        rotulo={primeiraSenha ? 'Senha' : 'Nova senha'}
        valor={senha}
        aoMudar={setSenha}
        erro={erros.senha}
        obrigatorio
        dica="Pelo menos 10 caracteres."
        autoComplete="new-password"
      />
      <CampoSenha
        rotulo={primeiraSenha ? 'Repita a senha' : 'Repita a nova senha'}
        valor={confirmacao}
        aoMudar={setConfirmacao}
        erro={erros.confirmacao}
        obrigatorio
        autoComplete="new-password"
      />
      <Botao
        type="submit"
        rotulo={primeiraSenha ? 'Criar senha' : 'Salvar nova senha'}
        icone="save"
        carregando={enviando}
        bloco
      />
    </LayoutConta>
  );
}
`````

`web/aluno/src/rotas.tsx`:

`````tsx
import { createBrowserRouter } from 'react-router';
import { RotaPrivada } from '@compartilhado/sessao/RotaPrivada.tsx';
import { CAMINHOS } from './caminhos.ts';
import { LayoutAluno } from './layout/LayoutAluno.tsx';
import { CadastroPagina } from './paginas/CadastroPagina.tsx';
import { ConfirmarEmailPagina } from './paginas/ConfirmarEmailPagina.tsx';
import { EntrarPagina } from './paginas/EntrarPagina.tsx';
import { MeusCursosPagina } from './paginas/MeusCursosPagina.tsx';
import { AulaPagina } from './paginas/aula/AulaPagina.tsx';
import { CertificadosPagina } from './paginas/CertificadosPagina.tsx';
import { NaoEncontradaPagina } from './paginas/NaoEncontradaPagina.tsx';
import { PerfilPagina } from './paginas/perfil/PerfilPagina.tsx';
import { TopicoPagina } from './paginas/forum/TopicoPagina.tsx';
import { TurmaPagina } from './paginas/turma/TurmaPagina.tsx';
import { RecuperarSenhaPagina } from './paginas/RecuperarSenhaPagina.tsx';
import { RedefinirSenhaPagina } from './paginas/RedefinirSenhaPagina.tsx';
import { ComprarPagina } from './paginas/pagamento/ComprarPagina.tsx';
import { RetornoPagamentoPagina } from './paginas/pagamento/RetornoPagamentoPagina.tsx';

export const roteador = createBrowserRouter([
  { path: CAMINHOS.entrar, element: <EntrarPagina /> },
  { path: CAMINHOS.cadastro, element: <CadastroPagina /> },
  { path: CAMINHOS.confirmarEmail, element: <ConfirmarEmailPagina /> },
  { path: CAMINHOS.recuperarSenha, element: <RecuperarSenhaPagina /> },
  { path: CAMINHOS.redefinirSenha, element: <RedefinirSenhaPagina /> },
  { path: CAMINHOS.retornoPagamento, element: <RetornoPagamentoPagina /> },
  {
    path: 'comprar/:ofertaId',
    element: (
      <RotaPrivada login={CAMINHOS.entrar}>
        <ComprarPagina />
      </RotaPrivada>
    ),
  },
  {
    element: (
      <RotaPrivada login={CAMINHOS.entrar}>
        <LayoutAluno />
      </RotaPrivada>
    ),
    children: [
      { index: true, element: <MeusCursosPagina /> },
      { path: 'turmas/:turmaId', element: <TurmaPagina /> },
      { path: 'turmas/:turmaId/aulas/:aulaId', element: <AulaPagina /> },
      { path: 'turmas/:turmaId/forum/:topicoId', element: <TopicoPagina /> },
      { path: CAMINHOS.certificados, element: <CertificadosPagina /> },
      { path: CAMINHOS.perfil, element: <PerfilPagina /> },
    ],
  },
  { path: '*', element: <NaoEncontradaPagina /> },
]);
`````

`web/aluno/src/tipos.ts`:

`````ts
/** Respostas da API da área do aluno (/api/aluno, /api/perfil), espelhando os records do backend. */

export type SituacaoMatricula = 'AGUARDANDO_INICIO' | 'VIGENTE' | 'VENCIDA' | 'CANCELADA';
export type FiltroMeusCursos = 'ATIVOS' | 'ARQUIVADOS' | 'EXPIRADOS';

export interface MeuCurso {
  matriculaId: number;
  turmaId: number;
  cursoTitulo: string;
  cursoSlug: string;
  turmaNome: string;
  capaUrl: string | null;
  inicio: string;
  fim: string | null;
  situacao: SituacaoMatricula;
  arquivada: boolean;
}

export interface AulaResumo {
  id: number;
  titulo: string;
  gratuita: boolean;
}

export interface ModuloAluno {
  id: number;
  titulo: string;
  aulas: AulaResumo[];
}

export interface TurmaAluno {
  matriculaId: number;
  turmaId: number;
  turmaNome: string;
  cursoTitulo: string;
  cursoSlug: string;
  inicio: string;
  fim: string | null;
  modulos: ModuloAluno[];
}

export interface AulaAluno {
  id: number;
  turmaId: number;
  titulo: string;
  descricao: string | null;
  gratuita: boolean;
}

export interface Progresso {
  aulaId: number;
  posicaoSegundos: number;
  vista: boolean;
  vistaEm: string | null;
  revisar: boolean;
}

export interface ProgressoTurma {
  turmaId: number;
  totalAulas: number;
  aulasVistas: number;
  percentual: number;
  aulas: Progresso[];
}

export interface ArquivoApoio {
  id: number;
  aulaId: number | null;
  moduloId: number | null;
  titulo: string;
  nomeArquivo: string;
  contentType: string;
  tamanho: number;
  criadoEm: string;
}

export interface Download {
  url: string;
  expiraEm: string;
}

export interface Certificado {
  id: number;
  turmaId: number;
  codigo: string;
  nomeCompleto: string;
  cursoTitulo: string;
  turmaNome: string;
  cargaHorariaHoras: number | null;
  emitidoEm: string;
}

export interface SituacaoCertificado {
  percentualVisto: number;
  percentualExigido: number;
  concluiu: boolean;
  certificado: Certificado | null;
}

export interface TopicoResumo {
  id: number;
  turmaId: number;
  aulaId: number | null;
  titulo: string;
  autorNome: string;
  fixado: boolean;
  respondidoInstrutor: boolean;
  respostas: number;
  criadoEm: string;
}

export interface Anexo {
  id: number;
  url: string;
}

export interface Resposta {
  id: number;
  corpoHtml: string;
  autorNome: string;
  instrutor: boolean;
  criadoEm: string;
  anexos: Anexo[];
}

export interface Topico extends Omit<TopicoResumo, 'respostas'> {
  corpoHtml: string;
  anexos: Anexo[];
  respostas: Resposta[];
}

export interface Perfil {
  id: number;
  nome: string;
  email: string;
  papeis: string[];
  fotoUrl: string | null;
}

export interface EnvioSimples {
  chave: string;
  url: string;
  contentType: string;
  expiraEm: string;
}

/** Aula com o módulo e a posição dela na turma, para anterior/próxima. */
export interface AulaNaTurma {
  aula: AulaResumo;
  modulo: ModuloAluno;
  anterior: AulaResumo | null;
  proxima: AulaResumo | null;
}

export function localizarAula(turma: TurmaAluno, aulaId: number): AulaNaTurma | null {
  const todas = turma.modulos.flatMap((modulo) => modulo.aulas.map((aula) => ({ aula, modulo })));
  const indice = todas.findIndex((item) => item.aula.id === aulaId);
  if (indice < 0) return null;
  const { aula, modulo } = todas[indice]!;
  return {
    aula,
    modulo,
    anterior: todas[indice - 1]?.aula ?? null,
    proxima: todas[indice + 1]?.aula ?? null,
  };
}

export type StatusPedido =
  | 'CRIADO'
  | 'AGUARDANDO_PAGAMENTO'
  | 'APROVADO'
  | 'CONCLUIDO'
  | 'EM_DISPUTA'
  | 'ATRASADO'
  | 'REEMBOLSADO'
  | 'CHARGEBACK'
  | 'CANCELADO'
  | 'EXPIRADO';

/** Resposta do checkout: ir para a página do meio de pagamento ou montar o formulário embutido. */
export interface Checkout {
  pedido: string;
  tipo: 'REDIRECIONAMENTO' | 'EMBUTIDO';
  url: string | null;
  dados: Record<string, unknown> | null;
}

/** Situação do pedido na página de retorno; curso e turma só para o dono logado. */
export interface SituacaoPedido {
  codigo: string;
  status: StatusPedido;
  turmaId: number | null;
  turmaNome: string | null;
  cursoTitulo: string | null;
  cursoSlug: string | null;
}
`````

`web/compartilhado/src/ui/estilos.css`:

`````css
/* Telas fora da área logada (cadastro, confirmação, recuperação de senha): cartão centralizado
   com os tokens da @celke/kole-react, nos dois temas. */
.pc-conta {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px 16px;
  background: var(--bg);
  color: var(--ink);
  font-family: var(--font-ui);
}

.pc-conta-cartao {
  width: 100%;
  max-width: 440px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-card);
  padding: 32px 28px;
  display: grid;
  gap: 18px;
}

.pc-conta-marca {
  font-size: var(--fs-caption);
  color: var(--t2);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin: 0;
}

.pc-conta-titulo {
  font-size: var(--fs-titulo);
  margin: 0;
}

.pc-conta-descricao {
  color: var(--t2);
  margin: 0;
}

.pc-conta-form {
  display: grid;
  gap: 14px;
}

.pc-conta-rodape {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  justify-content: space-between;
  font-size: var(--fs-corpo-2);
}

.pc-conta-rodape a,
.pc-link {
  color: var(--pri);
}

.pc-pagina {
  display: grid;
  gap: 20px;
}

.pc-grade-cards {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
}

/* Telas de gestão (ADM) */
.pc-pilha {
  display: grid;
  gap: 16px;
}

.pc-linha {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.pc-linha-fim {
  justify-content: flex-end;
}

.pc-filtros {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pc-grade-detalhes {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  align-items: start;
}

.pc-dica {
  color: var(--t2);
  font-size: var(--fs-corpo-2);
  margin: 0;
}

.pc-subtitulo {
  font-size: var(--fs-corpo);
  margin: 8px 0 0;
}

.pc-texto-longo {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  margin: 0;
}

.pc-texto-longo :where(p, ul, ol, h1, h2, h3) {
  white-space: normal;
}

.pc-capa {
  max-width: min(360px, 100%);
  border-radius: var(--r-card);
}

.pc-lista-aulas {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 20px;
}

.pc-lista-aulas li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
}

.pc-aula-titulo {
  background: none;
  border: 0;
  padding: 0;
  font: inherit;
  cursor: pointer;
  text-align: left;
}

.pc-arquivos {
  display: grid;
  gap: 10px;
  border-top: 1px solid var(--border);
  padding-top: 12px;
}

.pc-resposta {
  border: 1px solid var(--border);
  border-radius: var(--r-card);
  padding: 12px 14px;
  display: grid;
  gap: 8px;
}

.pc-resposta-oculta {
  opacity: 0.6;
}

/* Área do aluno */
.pc-player video {
  width: 100%;
  max-height: 70vh;
  background: #000;
  border-radius: var(--r-card);
}

.pc-progresso-turma {
  display: grid;
  gap: 4px;
  max-width: 520px;
}

.pc-navegacao-aulas {
  justify-content: space-between;
}

.pc-formulario-estreito {
  max-width: 420px;
}

/* Pagamentos (ADM) */
.pc-codigo {
  font-family: var(--font-mono, monospace);
  font-size: var(--fs-corpo-2);
  overflow-wrap: anywhere;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 4px 8px;
}

.pc-passos {
  margin: 0;
  padding-left: 20px;
  display: grid;
  gap: 6px;
}

.pc-json {
  font-family: var(--font-mono, monospace);
  font-size: var(--fs-corpo-2);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 420px;
  overflow: auto;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 12px;
  margin: 0;
}

.pc-linha-tempo {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 10px;
}

.pc-linha-tempo li {
  display: grid;
  gap: 2px;
  border-left: 3px solid var(--border);
  padding-left: 12px;
}
`````

`web/compartilhado/src/ui/formato.ts`:

`````ts
/** Formatação para exibição, em pt-BR e no fuso de quem está usando. */

const DATA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' });
const DATA_HORA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

export function data(valor: string | null | undefined): string {
  return valor ? DATA.format(new Date(valor)) : '—';
}

export function dataHora(valor: string | null | undefined): string {
  return valor ? DATA_HORA.format(new Date(valor)) : '—';
}

/** Valor em centavos na moeda informada ("R$ 197,00"). */
export function moeda(centavos: number | null | undefined, codigo = 'BRL'): string {
  if (centavos == null) return '—';
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: codigo }).format(
    centavos / 100,
  );
}

export function bytes(valor: number | null | undefined): string {
  if (valor == null) return '—';
  const unidades = ['B', 'KB', 'MB', 'GB'];
  let n = valor;
  let i = 0;
  while (n >= 1024 && i < unidades.length - 1) {
    n /= 1024;
    i += 1;
  }
  return `${n.toLocaleString('pt-BR', { maximumFractionDigits: i === 0 ? 0 : 1 })} ${unidades[i]}`;
}

export function duracao(segundos: number | null | undefined): string {
  if (segundos == null) return '—';
  const m = Math.floor(segundos / 60);
  const s = Math.round(segundos % 60);
  return `${m}:${String(s).padStart(2, '0')}`;
}

/** `AAAA-MM-DD` (campo de data) → instante do início do dia no fuso local. */
export function inicioDoDia(dia: string): string {
  return new Date(`${dia}T00:00:00`).toISOString();
}

/** `AAAA-MM-DD` → instante do fim do dia no fuso local (a matrícula vale o dia inteiro). */
export function fimDoDia(dia: string): string {
  return new Date(`${dia}T23:59:59`).toISOString();
}

/** Instante → `AAAA-MM-DD` no fuso local (para preencher campo de data). */
export function diaLocal(valor: string | null | undefined): string {
  if (!valor) return '';
  const d = new Date(valor);
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mes}-${dia}`;
}
`````

`web/compartilhado/src/ui/icones.ts`:

`````ts
import { registrarIcones } from '@celke/kole-react';
import {
  Award,
  BookOpen,
  Bookmark,
  CirclePlay,
  Copy,
  Power,
  PowerOff,
  Receipt,
  RotateCcw,
  Tag,
  GraduationCap,
  LayoutDashboard,
  Pencil,
  Pin,
  PinOff,
  Upload,
  Users,
} from 'lucide-react';

/** Ícones usados pelo nome nas telas e que a @celke/kole-react não registra por padrão. */
registrarIcones({
  award: Award,
  'book-open': BookOpen,
  bookmark: Bookmark,
  'circle-play': CirclePlay,
  copy: Copy,
  power: Power,
  'power-off': PowerOff,
  receipt: Receipt,
  'rotate-ccw': RotateCcw,
  tag: Tag,
  'graduation-cap': GraduationCap,
  'layout-dashboard': LayoutDashboard,
  pencil: Pencil,
  pin: Pin,
  'pin-off': PinOff,
  upload: Upload,
  users: Users,
});
`````

`web/site/assets/site.css`:

`````css
/* Site público: HTML estático com os tokens da @celke/kole-react (tokens.css), sem JavaScript de
   layout. O tema escuro segue o sistema do visitante. */
*,
*::before,
*::after {
  box-sizing: border-box;
}

html {
  -webkit-text-size-adjust: 100%;
}

body {
  margin: 0;
  background: var(--bg);
  color: var(--ink);
  font-family: var(--font-ui);
  font-size: var(--fs-corpo);
  line-height: 1.6;
}

img {
  max-width: 100%;
  height: auto;
}

a {
  color: var(--pri-ink);
}

h1,
h2,
h3 {
  line-height: 1.25;
  margin: 0 0 0.6em;
}

h1 {
  font-size: clamp(1.75rem, 4vw, 2.5rem);
}

h2 {
  font-size: 1.4rem;
}

h3 {
  font-size: 1.1rem;
}

.s-largura {
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 16px;
}

.s-estreito {
  max-width: 760px;
}

.s-secao {
  padding-top: 40px;
  padding-bottom: 40px;
}

.s-pular {
  position: absolute;
  left: -9999px;
}

.s-pular:focus {
  left: 16px;
  top: 8px;
  z-index: 10;
  background: var(--surface);
  padding: 8px 12px;
}

/* Topo e rodapé na cor da marca */
.s-topo,
.s-rodape {
  background: var(--navy);
  color: #fff;
}

.s-topo-linha,
.s-rodape-linha {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 24px;
  padding-top: 14px;
  padding-bottom: 14px;
}

.s-marca {
  color: #fff;
  font-weight: 700;
  font-size: 1.1rem;
  text-decoration: none;
}

.s-menu {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 20px;
}

.s-topo .s-menu a:not(.s-botao),
.s-rodape a {
  color: #fff;
}

.s-rodape {
  margin-top: 40px;
  font-size: var(--fs-corpo-2);
}

/* Botões */
.s-botao {
  display: inline-block;
  padding: 10px 20px;
  border-radius: var(--r-pill);
  background: var(--pri);
  color: var(--on-pri);
  font-weight: 600;
  text-decoration: none;
  border: 2px solid var(--pri);
  cursor: pointer;
  font: inherit;
  font-weight: 600;
}

.s-botao-contorno {
  background: transparent;
  color: var(--pri-ink);
}

.s-botao-claro {
  background: #fff;
  border-color: #fff;
  color: var(--navy);
  padding: 6px 16px;
}

.s-acoes {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}

/* Abertura */
.s-heroi {
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  padding: 56px 0;
}

.s-heroi-texto {
  font-size: 1.15rem;
  color: var(--t2);
  max-width: 680px;
}

.s-detalhe {
  color: var(--t2);
  font-size: var(--fs-corpo-2);
}

/* Cartões de curso */
.s-grade {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 20px;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
}

.s-cartao {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-card);
  overflow: hidden;
}

.s-cartao-link {
  display: flex;
  flex-direction: column;
  height: 100%;
  color: inherit;
  text-decoration: none;
}

.s-cartao-link:hover .s-cartao-titulo,
.s-cartao-link:focus-visible .s-cartao-titulo {
  text-decoration: underline;
}

.s-cartao-capa {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.s-cartao-sem-capa {
  background: linear-gradient(135deg, var(--navy), var(--pri));
}

.s-cartao-corpo {
  display: grid;
  gap: 6px;
  padding: 16px;
}

.s-cartao-titulo {
  font-weight: 700;
  font-size: 1.05rem;
}

.s-cartao-resumo {
  color: var(--t2);
  font-size: var(--fs-corpo-2);
}

.s-vazio {
  color: var(--t2);
}

.s-passos {
  display: grid;
  gap: 12px;
  padding-left: 20px;
}

/* Página do curso */
.s-trilha {
  font-size: var(--fs-corpo-2);
  color: var(--t2);
  margin-bottom: 16px;
}

.s-curso-topo {
  display: grid;
  gap: 24px;
  align-items: start;
  margin-bottom: 32px;
}

@media (min-width: 860px) {
  .s-curso-topo {
    grid-template-columns: 3fr 2fr;
  }
}

.s-curso-capa {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  border-radius: var(--r-card);
}

.s-modulos {
  display: grid;
  gap: 16px;
  padding: 0;
  list-style: none;
  counter-reset: modulo;
}

.s-modulos > li {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-card);
  padding: 16px 20px;
}

.s-aulas {
  margin: 0;
  padding-left: 20px;
  display: grid;
  gap: 4px;
}

.s-selo {
  display: inline-block;
  margin-left: 6px;
  padding: 0 8px;
  border-radius: var(--r-pill);
  background: var(--ok-bg);
  color: var(--ok-fg);
  font-size: var(--fs-caption);
  font-weight: 600;
}

/* Validar certificado */
.s-form {
  display: grid;
  gap: 8px;
  max-width: 420px;
  margin: 24px 0;
}

.s-form label {
  font-weight: 600;
}

.s-form input {
  font: inherit;
  padding: 10px 12px;
  border: 1px solid var(--border-2);
  border-radius: var(--r-field);
  background: var(--surface);
  color: var(--ink);
}

.s-form .s-botao {
  justify-self: start;
}

.s-resultado {
  padding: 16px 20px;
  border-radius: var(--r-card);
  border: 1px solid var(--border);
}

.s-resultado:empty {
  display: none;
}

.s-resultado-ok {
  background: var(--ok-bg);
  border-color: var(--ok-bd);
  color: var(--ok-fg);
}

.s-resultado-erro {
  background: var(--err-bg);
  border-color: var(--err-bd);
  color: var(--err-fg);
}

.s-resultado dl {
  display: grid;
  gap: 4px;
  margin: 12px 0 0;
}

.s-resultado dl div {
  display: flex;
  gap: 8px;
}

.s-resultado dt {
  font-weight: 600;
}

.s-resultado dd {
  margin: 0;
}

.s-texto {
  overflow-wrap: anywhere;
}

/* Ofertas do curso: preço e botão de compra */
.s-ofertas {
  list-style: none;
  margin: 16px 0 8px;
  padding: 0;
  display: grid;
  gap: 10px;
}

.s-oferta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
  padding: 12px 16px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
}

.s-oferta-titulo {
  flex: 1 1 140px;
}

.s-oferta-preco {
  font-size: 1.25rem;
}
`````

`web/site/gerar.ts`:

`````ts
/**
 * Gerador do site público (cursos.celke.uno): busca o catálogo na API do próprio ambiente e grava
 * uma página HTML por rota. Roda no container `site` ao subir e de novo a cada SITE_INTERVALO
 * segundos; o Caddy do container `web` serve a versão mais recente.
 *
 * Cada geração vai para uma pasta nova em versoes/ e só então o link `atual` passa a apontar para
 * ela (troca atômica): o Caddy nunca serve um site pela metade. Se a API falhar, a versão anterior
 * continua no ar e a geração é tentada de novo em pouco tempo.
 *
 *   node site/gerar.ts            (laço)
 *   node site/gerar.ts --uma-vez  (uma geração e sai)
 */
import { cp, mkdir, readdir, rename, rm, symlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import {
  paginaCatalogo,
  paginaCurso,
  paginaInicial,
  paginaNaoEncontrada,
  paginaTermo,
  paginaValidar,
  robots,
  sitemap,
  type Contexto,
  type CursoCatalogo,
  type CursoDetalhe,
  type Termo,
} from './paginas.ts';

const API_URL = (process.env['API_URL'] ?? 'http://localhost:8080').replace(/\/$/, '');
const ALUNO_URL = (process.env['FRONT_ALUNO_URL'] ?? 'http://localhost:5173').replace(/\/$/, '');
/** Sem SITE_URL, deriva do endereço do aluno (aluno.celke.uno → cursos.celke.uno). */
const SITE_URL = (process.env['SITE_URL'] || ALUNO_URL.replace('aluno.', 'cursos.')).replace(
  /\/$/,
  '',
);
const DESTINO = process.env['SITE_DESTINO'] ?? '/site';
const ASSETS = process.env['SITE_ASSETS'] ?? path.join(import.meta.dirname, 'assets');
const INTERVALO = Number(process.env['SITE_INTERVALO'] ?? 300) * 1000;
const NOVA_TENTATIVA = 30_000;
const VERSOES_GUARDADAS = 3;

async function buscar<T>(caminho: string): Promise<T> {
  const resposta = await fetch(API_URL + caminho, { headers: { Accept: 'application/json' } });
  if (!resposta.ok) throw new Error(`GET ${caminho}: ${resposta.status}`);
  return (await resposta.json()) as T;
}

/** Todos os cursos publicados (o catálogo é paginado, até 100 por página). */
async function todosOsCursos(): Promise<CursoCatalogo[]> {
  const cursos: CursoCatalogo[] = [];
  for (let pagina = 0; ; pagina++) {
    const resposta = await buscar<{ itens: CursoCatalogo[]; totalPaginas: number }>(
      `/api/cursos?page=${pagina}&size=100`,
    );
    cursos.push(...resposta.itens);
    if (pagina + 1 >= resposta.totalPaginas) return cursos;
  }
}

async function gravar(raiz: string, caminho: string, conteudo: string): Promise<void> {
  const arquivo = path.join(raiz, caminho.endsWith('/') ? `${caminho}index.html` : caminho);
  await mkdir(path.dirname(arquivo), { recursive: true });
  await writeFile(arquivo, conteudo);
}

export async function gerar(): Promise<number> {
  const ctx: Contexto = {
    siteUrl: SITE_URL,
    alunoUrl: ALUNO_URL,
    indexavel: process.env['SITE_INDEXAVEL'] !== 'false',
    ano: new Date().getFullYear(),
  };
  const [cursos, termos] = await Promise.all([
    todosOsCursos(),
    buscar<Termo[]>('/api/termos/vigentes'),
  ]);
  const detalhes = await Promise.all(
    cursos.map((c) => buscar<CursoDetalhe>(`/api/cursos/${c.slug}`)),
  );

  const versao = `v${Date.now()}`;
  const raiz = path.join(DESTINO, 'versoes', versao);
  await cp(ASSETS, path.join(raiz, 'assets'), { recursive: true });
  await gravar(raiz, '/', paginaInicial(ctx, cursos));
  await gravar(raiz, '/cursos/', paginaCatalogo(ctx, cursos));
  for (const curso of detalhes)
    await gravar(raiz, `/cursos/${curso.slug}/`, paginaCurso(ctx, curso));
  await gravar(raiz, '/certificados/validar/', paginaValidar(ctx));
  await gravar(
    raiz,
    '/termos/',
    paginaTermo(
      ctx,
      termos.find((t) => t.tipo === 'TERMOS_USO'),
      '/termos/',
    ),
  );
  await gravar(
    raiz,
    '/privacidade/',
    paginaTermo(
      ctx,
      termos.find((t) => t.tipo === 'POLITICA_PRIVACIDADE'),
      '/privacidade/',
    ),
  );
  await gravar(raiz, '/404.html', paginaNaoEncontrada(ctx));
  await gravar(raiz, '/sitemap.xml', sitemap(ctx, cursos));
  await gravar(raiz, '/robots.txt', robots(ctx));

  // Troca atômica: link temporário renomeado por cima do `atual`
  const temporario = path.join(DESTINO, `atual.${versao}`);
  await symlink(path.join('versoes', versao), temporario);
  await rename(temporario, path.join(DESTINO, 'atual'));

  const antigas = (await readdir(path.join(DESTINO, 'versoes')))
    .sort()
    .slice(0, -VERSOES_GUARDADAS);
  await Promise.all(
    antigas.map((v) => rm(path.join(DESTINO, 'versoes', v), { recursive: true, force: true })),
  );
  return cursos.length;
}

/** Instante no horário de Brasília, com o deslocamento (`2026-10-22T10:33:20.123-03:00`), como nos logs da API. */
export function instanteBrasilia(data: Date): string {
  const partes = Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/Sao_Paulo',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      fractionalSecondDigits: 3,
      hourCycle: 'h23',
      timeZoneName: 'longOffset',
    })
      .formatToParts(data)
      .map((p) => [p.type, p.value]),
  );
  const deslocamento =
    partes['timeZoneName'] === 'GMT' ? '+00:00' : partes['timeZoneName']!.slice(3);
  return `${partes['year']}-${partes['month']}-${partes['day']}T${partes['hour']}:${partes['minute']}:${partes['second']}.${partes['fractionalSecond']}${deslocamento}`;
}

function registrar(mensagem: string): void {
  console.log(
    JSON.stringify({ '@timestamp': instanteBrasilia(new Date()), servico: 'site', mensagem }),
  );
}

async function laco(): Promise<void> {
  for (;;) {
    let espera = INTERVALO;
    try {
      const total = await gerar();
      registrar(`site gerado com ${total} cursos`);
    } catch (erro) {
      registrar(`falha ao gerar o site (a versão anterior continua no ar): ${String(erro)}`);
      espera = Math.min(INTERVALO, NOVA_TENTATIVA);
    }
    await new Promise((fim) => setTimeout(fim, espera));
  }
}

if (import.meta.main) {
  if (process.argv.includes('--uma-vez')) {
    registrar(`site gerado com ${await gerar()} cursos`);
  } else {
    process.on('SIGTERM', () => process.exit(0));
    await laco();
  }
}
`````

`web/site/paginas.test.ts`:

`````ts
import { describe, expect, it } from 'vitest';
import { instanteBrasilia } from './gerar.ts';
import { html, jsonEmScript, paragrafos } from './html.ts';
import {
  paginaCurso,
  paginaInicial,
  robots,
  sitemap,
  type Contexto,
  type CursoDetalhe,
} from './paginas.ts';

const ctx: Contexto = {
  siteUrl: 'https://cursos.celke.uno',
  alunoUrl: 'https://aluno.celke.uno',
  indexavel: true,
  ano: 2026,
};

const curso: CursoDetalhe = {
  slug: 'java-do-zero',
  titulo: 'Java <do zero>',
  resumo: 'Aprenda "Java" & Spring',
  descricao: 'Primeiro parágrafo.\n\nSegundo parágrafo.',
  capaUrl: '/api/cursos/java-do-zero/capa',
  modulos: [{ titulo: 'Introdução', aulas: [{ id: 1, titulo: 'Boas-vindas', gratuita: true }] }],
  ofertas: [],
};

describe('html', () => {
  it('escapa o que vem de dados e mantém o que já é HTML', () => {
    expect(html`<p>${'<script>alert(1)</script>'}</p>`.toString()).toBe(
      '<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>',
    );
    const itens = ['a', 'b'].map((x) => html`<li>${x}</li>`);
    expect(
      html`<ul>
        ${itens}
      </ul>`
        .toString()
        .replace(/\s+/g, ''),
    ).toBe('<ul><li>a</li><li>b</li></ul>');
    expect(html`${null}${undefined}${false}`.toString()).toBe('');
  });

  it('parágrafos e JSON seguro dentro de script', () => {
    expect(paragrafos('um\n\ndois').toString()).toBe('<p>um</p><p>dois</p>');
    expect(jsonEmScript({ t: '</script>' }).toString()).toBe('{"t":"\\u003c/script>"}');
  });
});

describe('páginas', () => {
  it('página do curso com SEO: título, descrição, canônico, Open Graph e dados estruturados', () => {
    const pagina = paginaCurso(ctx, curso);
    expect(pagina).toContain('<title>Java &lt;do zero&gt; — Plataforma de Cursos</title>');
    expect(pagina).toContain(
      '<meta name="description" content="Aprenda &quot;Java&quot; &amp; Spring" />',
    );
    expect(pagina).toContain(
      '<link rel="canonical" href="https://cursos.celke.uno/cursos/java-do-zero/" />',
    );
    expect(pagina).toContain('content="https://cursos.celke.uno/api/cursos/java-do-zero/capa"');
    expect(pagina).toContain('"@type":"Course"');
    expect(pagina).toContain('Aula gratuita');
    expect(pagina).toContain('<p>Segundo parágrafo.</p>');
    expect(pagina).not.toContain('noindex');
  });

  it('sem oferta, convida para criar a conta; com oferta, mostra o preço e o Comprar', () => {
    expect(paginaCurso(ctx, curso)).toContain('Quero fazer este curso');
    const aVenda = paginaCurso(ctx, {
      ...curso,
      ofertas: [{ id: 7, titulo: 'À vista', precoCentavos: 19_700, moeda: 'BRL' }],
    });
    expect(aVenda).not.toContain('Quero fazer este curso');
    expect(aVenda).toContain('R$\u00a0197,00');
    expect(aVenda).toContain('href="https://aluno.celke.uno/comprar/7"');
    expect(aVenda).toContain('"@type":"Offer"');
    expect(aVenda).toContain('"price":"197.00"');
  });

  it('homologação fica fora dos buscadores', () => {
    const fora = { ...ctx, indexavel: false };
    expect(paginaInicial(fora, [])).toContain('<meta name="robots" content="noindex, nofollow" />');
    expect(robots(fora)).toBe('User-agent: *\nDisallow: /\n');
    expect(robots(ctx)).toContain('Sitemap: https://cursos.celke.uno/sitemap.xml');
  });

  it('home sem cursos e sitemap com cada curso', () => {
    expect(paginaInicial(ctx, [])).toContain('Novos cursos em breve');
    const mapa = sitemap(ctx, [curso]);
    expect(mapa).toContain('<loc>https://cursos.celke.uno/cursos/java-do-zero/</loc>');
    expect(mapa).toContain('<loc>https://cursos.celke.uno/certificados/validar/</loc>');
  });
});

describe('logs do gerador', () => {
  it('horário de Brasília com o deslocamento', () => {
    expect(instanteBrasilia(new Date('2026-10-22T13:33:20.123Z'))).toBe(
      '2026-10-22T10:33:20.123-03:00',
    );
    expect(instanteBrasilia(new Date('2026-10-22T02:59:59.000Z'))).toBe(
      '2026-10-21T23:59:59.000-03:00',
    );
  });
});
`````

`web/site/paginas.ts`:

`````ts
import { confiavel, html, jsonEmScript, paragrafos, type Html } from './html.ts';

/** Dados do catálogo público da API (/api/cursos), espelhando os records do backend. */
export interface CursoCatalogo {
  slug: string;
  titulo: string;
  resumo: string;
  capaUrl: string | null;
}

/** Opção à venda da turma atual (no meio de pagamento ativo). */
export interface OfertaCatalogo {
  id: number;
  titulo: string;
  precoCentavos: number;
  moeda: string;
}

export interface CursoDetalhe extends CursoCatalogo {
  descricao: string | null;
  modulos: { titulo: string; aulas: { id: number; titulo: string; gratuita: boolean }[] }[];
  /** Vazia: nada à venda agora (o botão leva ao cadastro). */
  ofertas: OfertaCatalogo[];
}

export interface Termo {
  tipo: 'TERMOS_USO' | 'POLITICA_PRIVACIDADE';
  versao: number;
  conteudoHtml: string;
  publicadoEm: string;
}

export interface Contexto {
  /** Endereço público do site, sem barra no fim (ex.: https://cursos.celke.uno). */
  siteUrl: string;
  /** Área do aluno (entrar, criar conta). */
  alunoUrl: string;
  /** Homologação: fora dos buscadores. */
  indexavel: boolean;
  /** Ano do rodapé. */
  ano: number;
}

const NOME = 'Plataforma de Cursos';

interface Pagina {
  caminho: string;
  titulo: string;
  descricao: string;
  corpo: Html;
  imagem?: string | null;
  dadosEstruturados?: unknown;
}

function layout(ctx: Contexto, p: Pagina): string {
  const canonico = ctx.siteUrl + p.caminho;
  const titulo = p.caminho === '/' ? `${NOME} — ${p.titulo}` : `${p.titulo} — ${NOME}`;
  return `<!doctype html>\n${html`<html lang="pt-BR">
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1" />
      <title>${titulo}</title>
      <meta name="description" content="${p.descricao}" />
      ${ctx.indexavel ? '' : html`<meta name="robots" content="noindex, nofollow" />`}
      <link rel="canonical" href="${canonico}" />
      <meta property="og:type" content="website" />
      <meta property="og:site_name" content="${NOME}" />
      <meta property="og:locale" content="pt_BR" />
      <meta property="og:title" content="${p.titulo}" />
      <meta property="og:description" content="${p.descricao}" />
      <meta property="og:url" content="${canonico}" />
      ${p.imagem ? html`<meta property="og:image" content="${ctx.siteUrl + p.imagem}" />` : ''}
      <meta name="twitter:card" content="${p.imagem ? 'summary_large_image' : 'summary'}" />
      <link rel="icon" href="/assets/icone.svg" type="image/svg+xml" />
      <link rel="stylesheet" href="/assets/tokens.css" />
      <link rel="stylesheet" href="/assets/site.css" />
      ${
        p.dadosEstruturados
          ? html`<script type="application/ld+json">
              ${jsonEmScript(p.dadosEstruturados)}
            </script>`
          : ''
      }
    </head>
    <body>
      <a class="s-pular" href="#conteudo">Pular para o conteúdo</a>
      <header class="s-topo">
        <div class="s-largura s-topo-linha">
          <a class="s-marca" href="/">${NOME}</a>
          <nav aria-label="Principal" class="s-menu">
            <a href="/cursos/">Cursos</a>
            <a href="/certificados/validar/">Validar certificado</a>
            <a class="s-botao s-botao-claro" href="${ctx.alunoUrl}/entrar">Entrar</a>
          </nav>
        </div>
      </header>
      <main id="conteudo">${p.corpo}</main>
      <footer class="s-rodape">
        <div class="s-largura s-rodape-linha">
          <span>© ${ctx.ano} ${NOME}</span>
          <nav aria-label="Rodapé" class="s-menu">
            <a href="/termos/">Termos de uso</a>
            <a href="/privacidade/">Privacidade</a>
            <a href="/certificados/validar/">Validar certificado</a>
          </nav>
        </div>
      </footer>
    </body>
  </html>`}\n`;
}

function cartaoCurso(curso: CursoCatalogo): Html {
  return html`<li class="s-cartao">
    <a href="/cursos/${curso.slug}/" class="s-cartao-link">
      ${
        curso.capaUrl
          ? html`<img
              class="s-cartao-capa"
              src="${curso.capaUrl}"
              alt=""
              width="640"
              height="360"
              loading="lazy"
            />`
          : html`<span class="s-cartao-capa s-cartao-sem-capa" aria-hidden="true"></span>`
      }
      <span class="s-cartao-corpo">
        <span class="s-cartao-titulo">${curso.titulo}</span>
        <span class="s-cartao-resumo">${curso.resumo}</span>
      </span>
    </a>
  </li>`;
}

function listaCursos(cursos: CursoCatalogo[]): Html {
  if (cursos.length === 0) {
    return html`<p class="s-vazio">Novos cursos em breve. Volte logo!</p>`;
  }
  return html`<ul class="s-grade" role="list">
    ${cursos.map(cartaoCurso)}
  </ul>`;
}

export function paginaInicial(ctx: Contexto, cursos: CursoCatalogo[]): string {
  return layout(ctx, {
    caminho: '/',
    titulo: 'Aprenda no seu ritmo',
    descricao:
      'Cursos online com aulas em vídeo, materiais de apoio, fórum com o instrutor e certificado.',
    corpo: html`<section class="s-heroi">
        <div class="s-largura">
          <h1>Aprenda no seu ritmo</h1>
          <p class="s-heroi-texto">
            Aulas em vídeo para assistir de qualquer lugar, materiais de apoio, dúvidas respondidas
            pelo instrutor e certificado com código de validação.
          </p>
          <p class="s-acoes">
            <a class="s-botao" href="/cursos/">Ver os cursos</a>
            <a class="s-botao s-botao-contorno" href="${ctx.alunoUrl}/cadastro">Criar conta</a>
          </p>
        </div>
      </section>
      <section class="s-largura s-secao" aria-labelledby="titulo-cursos">
        <h2 id="titulo-cursos">Cursos</h2>
        ${listaCursos(cursos.slice(0, 6))}
        ${cursos.length > 6 ? html`<p><a href="/cursos/">Ver todos os ${cursos.length} cursos</a></p>` : ''}
      </section>
      <section class="s-largura s-secao" aria-labelledby="titulo-como">
        <h2 id="titulo-como">Como funciona</h2>
        <ol class="s-passos">
          <li><strong>Crie a sua conta</strong> e confirme o e-mail.</li>
          <li><strong>Matricule-se</strong> no curso e acesse as aulas pela área do aluno.</li>
          <li>
            <strong>Assista no seu tempo:</strong> o progresso fica salvo e você continua de onde
            parou.
          </li>
          <li>
            <strong>Conclua e emita o certificado</strong>, que qualquer pessoa pode validar aqui no
            site.
          </li>
        </ol>
      </section>`,
  });
}

export function paginaCatalogo(ctx: Contexto, cursos: CursoCatalogo[]): string {
  return layout(ctx, {
    caminho: '/cursos/',
    titulo: 'Cursos',
    descricao: `Conheça os ${cursos.length} cursos disponíveis na ${NOME}.`,
    corpo: html`<div class="s-largura s-secao">
      <h1>Cursos</h1>
      ${listaCursos(cursos)}
    </div>`,
  });
}

export function preco(oferta: OfertaCatalogo): string {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: oferta.moeda }).format(
    oferta.precoCentavos / 100,
  );
}

/** Preço e "Comprar" de cada oferta; sem oferta, o convite para criar a conta. */
function acoesCurso(ctx: Contexto, curso: CursoDetalhe): Html {
  const ofertas = curso.ofertas ?? [];
  if (ofertas.length === 0) {
    return html`<p class="s-acoes">
      <a class="s-botao" href="${ctx.alunoUrl}/cadastro">Quero fazer este curso</a>
      <a class="s-botao s-botao-contorno" href="${ctx.alunoUrl}/entrar">Já sou aluno</a>
    </p>`;
  }
  return html`<ul class="s-ofertas" role="list">
      ${ofertas.map(
        (o) =>
          html`<li class="s-oferta">
            <span class="s-oferta-titulo">${o.titulo}</span>
            <strong class="s-oferta-preco">${preco(o)}</strong>
            <a class="s-botao" href="${ctx.alunoUrl}/comprar/${o.id}">Comprar</a>
          </li>`,
      )}
    </ul>
    <p class="s-detalhe">Já é aluno? <a href="${ctx.alunoUrl}/entrar">Entre na sua conta</a>.</p>`;
}

export function paginaCurso(ctx: Contexto, curso: CursoDetalhe): string {
  const totalAulas = curso.modulos.reduce((total, m) => total + m.aulas.length, 0);
  const caminho = `/cursos/${curso.slug}/`;
  return layout(ctx, {
    caminho,
    titulo: curso.titulo,
    descricao: curso.resumo,
    imagem: curso.capaUrl,
    dadosEstruturados: {
      '@context': 'https://schema.org',
      '@type': 'Course',
      name: curso.titulo,
      description: curso.resumo,
      url: ctx.siteUrl + caminho,
      provider: { '@type': 'Organization', name: NOME, sameAs: ctx.siteUrl },
      ...((curso.ofertas ?? []).length > 0
        ? {
            offers: curso.ofertas.map((o) => ({
              '@type': 'Offer',
              name: o.titulo,
              price: (o.precoCentavos / 100).toFixed(2),
              priceCurrency: o.moeda,
              url: `${ctx.alunoUrl}/comprar/${o.id}`,
            })),
          }
        : {}),
    },
    corpo: html`<article class="s-largura s-secao">
      <nav aria-label="Trilha" class="s-trilha">
        <a href="/cursos/">Cursos</a> › <span>${curso.titulo}</span>
      </nav>
      <div class="s-curso-topo">
        <div>
          <h1>${curso.titulo}</h1>
          <p class="s-heroi-texto">${curso.resumo}</p>
          <p class="s-detalhe">${curso.modulos.length} módulos · ${totalAulas} aulas</p>
          ${acoesCurso(ctx, curso)}
        </div>
        ${curso.capaUrl ? html`<img class="s-curso-capa" src="${curso.capaUrl}" alt="Capa do curso ${curso.titulo}" width="640" height="360" />` : ''}
      </div>
      ${
        curso.descricao
          ? html`<section aria-labelledby="titulo-sobre">
              <h2 id="titulo-sobre">Sobre o curso</h2>
              ${paragrafos(curso.descricao)}
            </section>`
          : ''
      }
      <section aria-labelledby="titulo-conteudo">
        <h2 id="titulo-conteudo">Conteúdo</h2>
        ${
          curso.modulos.length === 0
            ? html`<p class="s-vazio">O conteúdo deste curso está em preparação.</p>`
            : html`<ol class="s-modulos">
                ${curso.modulos.map(
                  (m) =>
                    html`<li>
                      <h3>${m.titulo}</h3>
                      <ol class="s-aulas">
                        ${m.aulas.map((a) => html`<li>${a.titulo}${a.gratuita ? html` <span class="s-selo">Aula gratuita</span>` : ''}</li>`)}
                      </ol>
                    </li>`,
                )}
              </ol>`
        }
      </section>
    </article>`,
  });
}

export function paginaValidar(ctx: Contexto): string {
  return layout(ctx, {
    caminho: '/certificados/validar/',
    titulo: 'Validar certificado',
    descricao:
      'Confira se um certificado foi emitido pela plataforma usando o código impresso nele.',
    corpo: html`<div class="s-largura s-secao s-estreito">
      <h1>Validar certificado</h1>
      <p>Informe o código impresso no certificado para conferir os dados dele.</p>
      <form id="validar" class="s-form" novalidate>
        <label for="codigo">Código do certificado</label>
        <input id="codigo" name="codigo" autocomplete="off" required maxlength="64" />
        <button class="s-botao" type="submit">Validar</button>
      </form>
      <div id="resultado" role="status" aria-live="polite"></div>
      <script src="/assets/validar.js" defer></script>
    </div>`,
  });
}

export function paginaTermo(
  ctx: Contexto,
  termo: Termo | undefined,
  caminho: '/termos/' | '/privacidade/',
): string {
  const titulo = caminho === '/termos/' ? 'Termos de uso' : 'Política de privacidade';
  return layout(ctx, {
    caminho,
    titulo,
    descricao: `${titulo} da ${NOME}.`,
    corpo: html`<article class="s-largura s-secao s-estreito s-texto">
      <h1>${titulo}</h1>
      ${
        termo
          ? html`<p class="s-detalhe">
                Versão ${termo.versao}, publicada em
                ${new Date(termo.publicadoEm).toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' })}.
              </p>
              ${confiavel(termo.conteudoHtml)}`
          : html`<p class="s-vazio">Em breve.</p>`
      }
    </article>`,
  });
}

export function paginaNaoEncontrada(ctx: Contexto): string {
  return layout(ctx, {
    caminho: '/404.html',
    titulo: 'Página não encontrada',
    descricao: 'O endereço não existe ou mudou.',
    corpo: html`<div class="s-largura s-secao">
      <h1>Página não encontrada</h1>
      <p>O endereço não existe ou mudou. <a href="/cursos/">Veja os cursos</a>.</p>
    </div>`,
  });
}

export function sitemap(ctx: Contexto, cursos: CursoCatalogo[]): string {
  const caminhos = [
    '/',
    '/cursos/',
    ...cursos.map((c) => `/cursos/${c.slug}/`),
    '/certificados/validar/',
    '/termos/',
    '/privacidade/',
  ];
  return `<?xml version="1.0" encoding="UTF-8"?>\n${html`<urlset
    xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"
  >
    ${caminhos.map((c) => html` <url><loc>${ctx.siteUrl + c}</loc></url> `)}</urlset
  >`}\n`;
}

export function robots(ctx: Contexto): string {
  return ctx.indexavel
    ? `User-agent: *\nAllow: /\n\nSitemap: ${ctx.siteUrl}/sitemap.xml\n`
    : 'User-agent: *\nDisallow: /\n';
}
`````

### Arquivos novos

`backend/src/e2e/java/br/com/celke/cursos/e2e/pagamento/PagamentoE2E.java`:

`````java
package br.com.celke.cursos.e2e.pagamento;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Hotmart;
import br.com.celke.cursos.e2e.support.Mailpit;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: o ADM configura a Hotmart e a oferta pela API; o aluno faz o checkout e recebe a URL do
 * pay.hotmart.com com o código do pedido; a notificação aprovada chega pelo Caddy, o worker processa,
 * o aluno passa a ver a turma e o e-mail de confirmação chega ao Mailpit; o reembolso tira o acesso.
 */
class PagamentoE2E {

    @Test
    @DisplayName("checkout → PURCHASE_APPROVED pelo Caddy → acesso e e-mail → PURCHASE_REFUNDED tira o acesso")
    void compraPelaHotmart() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Pagamento e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");

        // 1. Hotmart configurada e ativa; oferta da turma
        String hottok = "hottok-" + Dados.stamp();
        var gateway = adm.requisicao()
                .body("{\"tipo\":\"HOTMART\",\"nome\":\"Hotmart e2e\",\"valores\":{\"hottok\":\"%s\"}}"
                        .formatted(hottok))
                .post("/api/adm/pagamentos/gateways")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath();
        long gatewayId = gateway.getLong("id");
        String webhookUrl = gateway.getString("webhookUrl");
        adm.requisicao()
                .post("/api/adm/pagamentos/gateways/" + gatewayId + "/ativacao")
                .then()
                .statusCode(200)
                .body("ativo", equalTo(true));
        String produto = Dados.stamp();
        long oferta = adm.criar(
                "/api/adm/turmas/" + turma + "/ofertas",
                "{\"produtoExterno\":\"%s\",\"ofertaExterna\":\"e2e\",\"titulo\":\"À vista\",\"precoCentavos\":19700,\"moeda\":\"BRL\"}"
                        .formatted(produto));

        // 2. Checkout do aluno: URL da Hotmart com o código do pedido
        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");
        String pedido = aluno(token)
                .body("{\"ofertaId\":%d}".formatted(oferta))
                .post("/api/pagamentos/checkout")
                .then()
                .statusCode(201)
                .body("tipo", equalTo("REDIRECIONAMENTO"))
                .body("url", startsWith("https://pay.hotmart.com/" + produto + "?off=e2e&email="))
                .extract()
                .jsonPath()
                .getString("pedido");
        aluno(token).get("/api/aluno/turmas/" + turma).then().statusCode(403);

        // 3. A Hotmart avisa a aprovação, pelo endereço que o painel mostrou (via Caddy)
        String caminho = webhookUrl.substring(webhookUrl.indexOf("/api/"));
        String transacao = "HP" + Dados.stamp();
        Instant agora = Instant.now();
        notificar(
                caminho,
                hottok,
                Hotmart.notificacao("PURCHASE_APPROVED", pedido, transacao, produto, agora.toEpochMilli()));

        await("matrícula pelo pagamento")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> aluno(token).get("/api/aluno/turmas/" + turma).statusCode() == 200);
        aluno(token)
                .get("/api/pagamentos/pedidos/" + pedido)
                .then()
                .statusCode(200)
                .body("status", equalTo("APROVADO"));
        Mailpit.aguardar(email, "Sua matrícula foi confirmada");

        // 4. Reembolso: o acesso termina
        notificar(
                caminho,
                hottok,
                Hotmart.notificacao(
                        "PURCHASE_REFUNDED",
                        pedido,
                        transacao,
                        produto,
                        agora.plusSeconds(60).toEpochMilli()));
        await("acesso retirado pelo reembolso")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> aluno(token).get("/api/aluno/turmas/" + turma).statusCode() == 403);
        Api.aluno()
                .get("/api/pagamentos/pedidos/" + pedido)
                .then()
                .statusCode(200)
                .body("status", equalTo("REEMBOLSADO"));
    }

    private static void notificar(String caminho, String hottok, String corpo) {
        Api.aluno()
                .header("X-HOTMART-HOTTOK", hottok)
                .body(corpo)
                .post(caminho)
                .then()
                .statusCode(200)
                .body("resultado", equalTo("recebido"));
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/Hotmart.java`:

`````java
package br.com.celke.cursos.e2e.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Notificações da Hotmart montadas a partir das fixtures do webhook 2.0.0
 * ({@code src/test/resources/pagamentos/hotmart}), com id, pedido, transação e produto da jornada.
 */
public final class Hotmart {

    private Hotmart() {}

    public static String notificacao(String evento, String xcod, String transacao, String produto, long ocorridoEm) {
        return fixture(evento)
                .replaceFirst("\"id\": \"[^\"]+\"", "\"id\": \"" + UUID.randomUUID() + "\"")
                .replaceFirst("\"creation_date\": \\d+", "\"creation_date\": " + ocorridoEm)
                .replace("XCOD_DO_PEDIDO", xcod)
                .replace("HP17715690036014", transacao)
                .replace("\"id\": 4774438", "\"id\": \"" + produto + "\"")
                .replace("\"code\": \"k2pasun0\"", "\"code\": \"e2e\"");
    }

    private static String fixture(String evento) {
        try (InputStream entrada = Hotmart.class.getResourceAsStream("/pagamentos/hotmart/" + evento + ".json")) {
            if (entrada == null) {
                throw new IllegalArgumentException("Fixture inexistente: " + evento);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/application/OfertasCatalogo.java`:

`````java
package br.com.celke.cursos.curso.application;

import java.util.List;

/**
 * Ofertas à venda de uma turma, mostradas no detalhe do catálogo. Implementada pelo módulo de
 * pagamento: o catálogo não conhece meios de pagamento.
 */
public interface OfertasCatalogo {

    /** Ofertas ativas da turma no meio de pagamento ativo; vazia se nada está à venda. */
    List<OfertaCatalogo> ativas(Long turmaId);

    record OfertaCatalogo(Long id, String titulo, long precoCentavos, String moeda) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/application/MatriculaPagamentoService.java`:

`````java
package br.com.celke.cursos.matricula.application;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Matrícula pelo pagamento (módulo {@code pagamento}): conceder o acesso quando a compra é aprovada e
 * cancelar quando ela é reembolsada ou contestada. As duas operações podem ser repetidas sem efeito
 * duplicado (a mesma notificação pode ser processada de novo).
 */
@Service
public class MatriculaPagamentoService {

    private final MatriculaRepository matriculas;
    private final Clock clock;

    public MatriculaPagamentoService(MatriculaRepository matriculas, Clock clock) {
        this.matriculas = matriculas;
        this.clock = clock;
    }

    /**
     * Garante o acesso do comprador à turma. Ordem: a matrícula do próprio pedido ainda ativa não muda;
     * se o aluno já tem matrícula ativa na turma, ela é estendida; a do pedido, se foi cancelada, é
     * reativada; senão nasce uma nova a partir de {@code inicio}.
     */
    @Transactional
    public Concessao conceder(NovaConcessao dados) {
        Instant agora = clock.instant();
        Optional<Matricula> doPedido =
                Optional.ofNullable(dados.matriculaAtualId()).flatMap(matriculas::findById);
        if (doPedido.filter(m -> m.getStatus() == StatusMatricula.ATIVA).isPresent()) {
            return new Concessao(doPedido.get().getId(), false);
        }
        Optional<Matricula> ativaNaTurma = matriculas.findByUsuarioIdAndTurmaIdAndStatus(
                dados.usuarioId(), dados.turmaId(), StatusMatricula.ATIVA);
        if (ativaNaTurma.isPresent()) {
            Matricula matricula = ativaNaTurma.get();
            matricula.estender(dados.diasAcesso(), dados.inicio(), agora);
            return new Concessao(matricula.getId(), true);
        }
        if (doPedido.isPresent()) {
            doPedido.get().reativar(agora);
            return new Concessao(doPedido.get().getId(), true);
        }
        String transacao = dados.transacao();
        if (transacao != null && matriculas.existsByFormaPagamentoAndTransacao(dados.formaPagamento(), transacao)) {
            // A mesma transação já foi lançada à mão pelo ADM: a matrícula nova fica sem ela
            transacao = null;
        }
        Instant fim = dados.diasAcesso() == null ? null : dados.inicio().plus(Duration.ofDays(dados.diasAcesso()));
        Matricula nova = matriculas.saveAndFlush(Matricula.dePagamento(
                dados.usuarioId(),
                dados.turmaId(),
                dados.inicio(),
                fim,
                dados.formaPagamento(),
                transacao,
                dados.pedidoId(),
                agora));
        return new Concessao(nova.getId(), true);
    }

    /** Cancela a matrícula (reembolso, chargeback); já cancelada ou inexistente, nada muda. */
    @Transactional
    public void cancelar(Long matriculaId) {
        matriculas
                .findById(matriculaId)
                .filter(m -> m.getStatus() == StatusMatricula.ATIVA)
                .ifPresent(m -> m.cancelar(clock.instant()));
    }

    /** A matrícula existe e está ativa (vigente, futura ou vencida). */
    @Transactional(readOnly = true)
    public boolean ativa(Long matriculaId) {
        return matriculas
                .findById(matriculaId)
                .filter(m -> m.getStatus() == StatusMatricula.ATIVA)
                .isPresent();
    }

    /**
     * Dados da concessão.
     *
     * @param inicio início do acesso (a aprovação do pagamento)
     * @param diasAcesso dias de acesso da oferta; nulo, sem prazo
     * @param matriculaAtualId matrícula já ligada ao pedido, se houver
     */
    public record NovaConcessao(
            Long usuarioId,
            Long turmaId,
            Instant inicio,
            Integer diasAcesso,
            FormaPagamento formaPagamento,
            String transacao,
            Long pedidoId,
            Long matriculaAtualId) {}

    /**
     * @param novoAcesso o acesso foi aberto, reaberto ou estendido agora (vale avisar o aluno)
     */
    public record Concessao(Long matriculaId, boolean novoAcesso) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/api/CheckoutController.java`:

`````java
package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.api.PagamentoResponses.CheckoutResponse;
import br.com.celke.cursos.pagamento.api.PagamentoResponses.PedidoPublico;
import br.com.celke.cursos.pagamento.application.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Compra: o checkout (aluno logado) e a situação do pedido para a página de retorno (pública). */
@RestController
@RequestMapping("/api/pagamentos")
@Tag(name = "Pagamentos — compra")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/checkout")
    @Operation(
            summary = "Comprar uma oferta",
            description = "Abre o pedido com o e-mail da conta e devolve para onde levar o aluno.")
    public ResponseEntity<CheckoutResponse> checkout(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PagamentoRequests.Checkout request) {
        return ResponseEntity.status(201)
                .body(CheckoutResponse.de(checkoutService.iniciar(Long.valueOf(jwt.getSubject()), request.ofertaId())));
    }

    @GetMapping("/pedidos/{codigo}")
    @Operation(
            summary = "Situação do pedido",
            description = "Curso e turma só para o dono logado; para os outros, só o status.")
    public PedidoPublico pedido(@AuthenticationPrincipal Jwt jwt, @PathVariable String codigo) {
        Long usuarioId = jwt == null ? null : Long.valueOf(jwt.getSubject());
        return PedidoPublico.de(checkoutService.situacao(codigo, usuarioId));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/api/GatewayAdmController.java`:

`````java
package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.api.PagamentoResponses.GatewayResponse;
import br.com.celke.cursos.pagamento.application.GatewayAdmService;
import br.com.celke.cursos.pagamento.application.GatewayAdmService.TiposDisponiveis;
import br.com.celke.cursos.pagamento.domain.ResultadoTeste;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm/pagamentos")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Meios de pagamento")
public class GatewayAdmController {

    private final GatewayAdmService gatewayAdmService;

    public GatewayAdmController(GatewayAdmService gatewayAdmService) {
        this.gatewayAdmService = gatewayAdmService;
    }

    @GetMapping("/tipos")
    @Operation(
            summary = "Meios de pagamento disponíveis",
            description = "Com os campos de configuração de cada um; cifraDisponivel falso sem a PAGAMENTOS_CHAVE.")
    public TiposDisponiveis tipos() {
        return gatewayAdmService.tipos();
    }

    @GetMapping("/gateways")
    @Operation(summary = "Meios de pagamento configurados")
    public List<GatewayResponse> listar() {
        return gatewayAdmService.listar().stream().map(GatewayResponse::de).toList();
    }

    @GetMapping("/gateways/{id}")
    @Operation(summary = "Meio de pagamento")
    public GatewayResponse buscar(@PathVariable Long id) {
        return GatewayResponse.de(gatewayAdmService.buscar(id));
    }

    @PostMapping("/gateways")
    @Operation(summary = "Configurar meio de pagamento", description = "Nasce inativo.")
    public ResponseEntity<GatewayResponse> criar(@Valid @RequestBody PagamentoRequests.NovoGateway request) {
        GatewayResponse gateway =
                GatewayResponse.de(gatewayAdmService.criar(request.tipo(), request.nome(), request.valores()));
        return ResponseEntity.created(URI.create("/api/adm/pagamentos/gateways/" + gateway.id()))
                .body(gateway);
    }

    @PutMapping("/gateways/{id}")
    @Operation(summary = "Alterar configuração", description = "Segredo em branco mantém o valor atual.")
    public GatewayResponse atualizar(
            @PathVariable Long id, @Valid @RequestBody PagamentoRequests.AtualizarGateway request) {
        return GatewayResponse.de(gatewayAdmService.atualizar(id, request.nome(), request.valores()));
    }

    @PostMapping("/gateways/{id}/teste")
    @Operation(summary = "Testar as credenciais gravadas com o meio de pagamento")
    public ResultadoTeste testar(@PathVariable Long id) {
        return gatewayAdmService.testar(id);
    }

    @PostMapping("/gateways/{id}/ativacao")
    @Operation(
            summary = "Ativar",
            description = "Passa a ser o meio dos novos checkouts; o anterior continua recebendo notificações.")
    public GatewayResponse ativar(@PathVariable Long id) {
        return GatewayResponse.de(gatewayAdmService.ativar(id));
    }

    @DeleteMapping("/gateways/{id}/ativacao")
    @Operation(summary = "Desativar", description = "Os botões de compra somem; as notificações continuam chegando.")
    public GatewayResponse desativar(@PathVariable Long id) {
        return GatewayResponse.de(gatewayAdmService.desativar(id));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/api/LimiteWebhookFilter.java`:

`````java
package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.application.PagamentosProperties;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.erro.ProblemDetailJson;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limite de notificações por IP no webhook, antes da segurança e do banco. Mais alto que o das rotas
 * de conta: o meio de pagamento manda rajadas legítimas do mesmo IP (e um 429 o faria reenviar).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LimiteWebhookFilter extends OncePerRequestFilter {

    static final String PREFIXO = "/api/pagamentos/webhooks/";
    private static final Logger LOG = LoggerFactory.getLogger(LimiteWebhookFilter.class);

    private final int limitePorMinuto;
    private final JsonMapper jsonMapper;
    private final Cache<String, Bucket> baldes = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public LimiteWebhookFilter(PagamentosProperties props, JsonMapper jsonMapper) {
        this.limitePorMinuto = props.webhookRequisicoesPorIpPorMinuto();
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith(PREFIXO);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ConsumptionProbe consumo =
                baldes.get(request.getRemoteAddr(), ip -> novoBalde()).tryConsumeAndReturnRemaining(1);
        if (consumo.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        MuitasTentativasException limite =
                new MuitasTentativasException(Duration.ofNanos(consumo.getNanosToWaitForRefill()));
        LOG.warn("Limite de notificações por IP atingido no webhook");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(limite.segundosDeEspera()));
        ProblemDetailJson.escrever(jsonMapper, request, response, HttpStatus.TOO_MANY_REQUESTS, limite.getMessage());
    }

    private Bucket novoBalde() {
        return Bucket.builder()
                .addLimit(
                        limite -> limite.capacity(limitePorMinuto).refillGreedy(limitePorMinuto, Duration.ofMinutes(1)))
                .build();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/api/OfertaAdmController.java`:

`````java
package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.api.PagamentoResponses.OfertaResponse;
import br.com.celke.cursos.pagamento.application.OfertaAdmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Ofertas")
public class OfertaAdmController {

    private final OfertaAdmService ofertaAdmService;

    public OfertaAdmController(OfertaAdmService ofertaAdmService) {
        this.ofertaAdmService = ofertaAdmService;
    }

    @GetMapping("/turmas/{turmaId}/ofertas")
    @Operation(summary = "Ofertas da turma", description = "Inclui as inativas.")
    public List<OfertaResponse> listar(@PathVariable Long turmaId) {
        return ofertaAdmService.listar(turmaId).stream().map(OfertaResponse::de).toList();
    }

    @PostMapping("/turmas/{turmaId}/ofertas")
    @Operation(summary = "Criar oferta", description = "Sem gatewayId, no meio de pagamento ativo.")
    public ResponseEntity<OfertaResponse> criar(
            @PathVariable Long turmaId, @Valid @RequestBody PagamentoRequests.Oferta request) {
        OfertaResponse oferta =
                OfertaResponse.de(ofertaAdmService.criar(turmaId, request.gatewayId(), request.dados()));
        return ResponseEntity.created(URI.create("/api/adm/ofertas/" + oferta.id()))
                .body(oferta);
    }

    @PutMapping("/ofertas/{id}")
    @Operation(summary = "Alterar oferta", description = "O meio de pagamento não muda.")
    public OfertaResponse atualizar(@PathVariable Long id, @Valid @RequestBody PagamentoRequests.Oferta request) {
        return OfertaResponse.de(ofertaAdmService.atualizar(id, request.dados()));
    }

    @PatchMapping("/ofertas/{id}/status")
    @Operation(summary = "Ativar ou desativar oferta", description = "Inativa some do site e não abre checkout.")
    public OfertaResponse alterarStatus(
            @PathVariable Long id, @Valid @RequestBody PagamentoRequests.StatusOferta request) {
        return OfertaResponse.de(ofertaAdmService.alterarAtiva(id, request.ativa()));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/api/PagamentoRequests.java`:

`````java
package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.domain.Oferta.DadosOferta;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Corpos das rotas de pagamento: checkout do aluno e painel do ADM. */
public final class PagamentoRequests {

    private PagamentoRequests() {}

    public record Checkout(@NotNull Long ofertaId) {}

    /** Campos em {@code valores}, pela chave de {@code camposConfiguracao}; segredo em branco mantém o atual. */
    public record NovoGateway(
            @NotNull TipoGateway tipo,
            @NotBlank @Size(max = 120) String nome,
            Map<String, String> valores) {}

    public record AtualizarGateway(
            @NotBlank @Size(max = 120) String nome, Map<String, String> valores) {}

    /**
     * Oferta de uma turma.
     *
     * @param gatewayId meio de pagamento; sem ele, o ativo (só na criação)
     * @param codigoCheckout código do produto no link do checkout, se diferente do id do produto
     * @param diasAcesso sem ele, acesso sem prazo
     */
    public record Oferta(
            Long gatewayId,
            @NotBlank @Size(max = 100) String produtoExterno,
            @NotBlank @Size(max = 100) String ofertaExterna,
            @Size(max = 100) String codigoCheckout,
            @NotBlank @Size(max = 120) String titulo,
            @NotNull @Min(0) @Max(1_000_000_000) Long precoCentavos,

            @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "use o código de 3 letras (ex.: BRL)")
            String moeda,

            @Min(1) @Max(36_500) Integer diasAcesso) {

        public DadosOferta dados() {
            return new DadosOferta(
                    produtoExterno, ofertaExterna, codigoCheckout, titulo, precoCentavos, moeda, diasAcesso);
        }
    }

    public record StatusOferta(@NotNull Boolean ativa) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/api/PagamentoResponses.java`:

`````java
package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.pagamento.application.CheckoutService.CheckoutIniciado;
import br.com.celke.cursos.pagamento.application.CheckoutService.SituacaoPedido;
import br.com.celke.cursos.pagamento.application.ConfiguracaoGatewayService.SegredoResumo;
import br.com.celke.cursos.pagamento.application.GatewayAdmService.GatewayDetalhe;
import br.com.celke.cursos.pagamento.application.OfertaAdmService.OfertaComGateway;
import br.com.celke.cursos.pagamento.application.PedidoAdmService.DetalhePedido;
import br.com.celke.cursos.pagamento.application.PedidoAdmService.PedidoComOferta;
import br.com.celke.cursos.pagamento.domain.Checkout;
import br.com.celke.cursos.pagamento.domain.EventoRecebido;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.OrigemPedido;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.PedidoHistorico;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Respostas das rotas de pagamento. Segredos dos meios de pagamento nunca saem daqui. */
public final class PagamentoResponses {

    private PagamentoResponses() {}

    /**
     * @param tipo {@code REDIRECIONAMENTO} (ir para {@code url}) ou {@code EMBUTIDO} (formulário com {@code dados})
     */
    public record CheckoutResponse(String pedido, String tipo, String url, Map<String, Object> dados) {

        public static CheckoutResponse de(CheckoutIniciado iniciado) {
            return switch (iniciado.checkout()) {
                case Checkout.Redirecionamento r ->
                    new CheckoutResponse(iniciado.pedido(), "REDIRECIONAMENTO", r.url(), null);
                case Checkout.Embutido e -> new CheckoutResponse(iniciado.pedido(), "EMBUTIDO", null, e.dados());
            };
        }
    }

    /** Situação para a página de retorno; curso e turma só para o dono do pedido. */
    public record PedidoPublico(
            String codigo, StatusPedido status, Long turmaId, String turmaNome, String cursoTitulo, String cursoSlug) {

        public static PedidoPublico de(SituacaoPedido situacao) {
            TurmaResumo turma = situacao.turma();
            return new PedidoPublico(
                    situacao.codigo(),
                    situacao.status(),
                    turma == null ? null : turma.turmaId(),
                    turma == null ? null : turma.turmaNome(),
                    turma == null ? null : turma.cursoTitulo(),
                    turma == null ? null : turma.cursoSlug());
        }
    }

    public record WebhookResponse(String resultado) {}

    /**
     * Meio de pagamento no painel.
     *
     * @param valores campos não secretos
     * @param segredos situação de cada campo secreto (configurado e os 4 últimos caracteres)
     * @param segredosLegiveis falso se a PAGAMENTOS_CHAVE falta ou mudou: informe os segredos de novo
     */
    public record GatewayResponse(
            Long id,
            TipoGateway tipo,
            String nomeTipo,
            String nome,
            boolean ativo,
            Map<String, String> valores,
            Map<String, SegredoResumo> segredos,
            boolean segredosLegiveis,
            String webhookUrl,
            Instant criadoEm,
            Instant atualizadoEm) {

        public static GatewayResponse de(GatewayDetalhe detalhe) {
            var g = detalhe.gateway();
            return new GatewayResponse(
                    g.getId(),
                    g.getTipo(),
                    detalhe.nomeTipo(),
                    g.getNome(),
                    g.isAtivo(),
                    detalhe.valores(),
                    detalhe.segredos(),
                    detalhe.segredosLegiveis(),
                    detalhe.webhookUrl(),
                    g.getCriadoEm(),
                    g.getAtualizadoEm());
        }
    }

    public record OfertaResponse(
            Long id,
            Long turmaId,
            Long gatewayId,
            String gatewayNome,
            boolean gatewayAtivo,
            String produtoExterno,
            String ofertaExterna,
            String codigoCheckout,
            String titulo,
            long precoCentavos,
            String moeda,
            Integer diasAcesso,
            boolean ativa,
            Instant criadoEm) {

        public static OfertaResponse de(OfertaComGateway item) {
            Oferta o = item.oferta();
            return new OfertaResponse(
                    o.getId(),
                    o.getTurmaId(),
                    o.getGatewayId(),
                    item.gateway() == null ? null : item.gateway().getNome(),
                    item.gateway() != null && item.gateway().isAtivo(),
                    o.getProdutoExterno(),
                    o.getOfertaExterna(),
                    o.getCodigoCheckout(),
                    o.getTitulo(),
                    o.getPrecoCentavos(),
                    o.getMoeda(),
                    o.getDiasAcesso(),
                    o.isAtiva(),
                    o.getCriadoEm());
        }
    }

    public record PedidoAdm(
            Long id,
            String codigo,
            StatusPedido status,
            OrigemPedido origem,
            String email,
            String nome,
            Long usuarioId,
            Long gatewayId,
            Long ofertaId,
            String ofertaTitulo,
            Long turmaId,
            String transacaoExterna,
            Long valorCentavos,
            String moeda,
            FormaPagamento formaPagamento,
            Long matriculaId,
            Instant criadoEm,
            Instant atualizadoEm) {

        public static PedidoAdm de(PedidoComOferta item) {
            Pedido p = item.pedido();
            return new PedidoAdm(
                    p.getId(),
                    p.getCodigo(),
                    p.getStatus(),
                    p.getOrigem(),
                    p.getEmail(),
                    p.getNome(),
                    p.getUsuarioId(),
                    p.getGatewayId(),
                    p.getOfertaId(),
                    item.oferta() == null ? null : item.oferta().getTitulo(),
                    item.oferta() == null ? null : item.oferta().getTurmaId(),
                    p.getTransacaoExterna(),
                    p.getValorCentavos(),
                    p.getMoeda(),
                    p.getFormaPagamento(),
                    p.getMatriculaId(),
                    p.getCriadoEm(),
                    p.getAtualizadoEm());
        }
    }

    public record PedidoDetalhe(PedidoAdm pedido, List<Historico> historico, List<EventoResumo> eventos) {

        public static PedidoDetalhe de(DetalhePedido detalhe) {
            return new PedidoDetalhe(
                    PedidoAdm.de(detalhe.pedido()),
                    detalhe.historico().stream().map(Historico::de).toList(),
                    detalhe.eventos().stream().map(EventoResumo::de).toList());
        }
    }

    public record Historico(Long id, StatusPedido de, StatusPedido para, Long eventoId, String motivo, Instant em) {

        static Historico de(PedidoHistorico h) {
            return new Historico(h.getId(), h.getDe(), h.getPara(), h.getEventoId(), h.getMotivo(), h.getEm());
        }
    }

    public record EventoResumo(
            Long id,
            Long gatewayId,
            String idExterno,
            String tipoOriginal,
            StatusEvento status,
            Instant recebidoEm,
            Instant ocorridoEm,
            String transacao,
            String erro,
            Long pedidoId,
            Instant processadoEm) {

        public static EventoResumo de(EventoRecebido e) {
            return new EventoResumo(
                    e.getId(),
                    e.getGatewayId(),
                    e.getIdExterno(),
                    e.getTipoOriginal(),
                    e.getStatus(),
                    e.getRecebidoEm(),
                    e.getOcorridoEm(),
                    e.getTransacao(),
                    e.getErro(),
                    e.getPedidoId(),
                    e.getProcessadoEm());
        }
    }

    /** @param payload corpo recebido em JSON (mascarado); nulo depois da retenção */
    public record EventoDetalhe(EventoResumo evento, String payload) {

        public static EventoDetalhe de(EventoRecebido e) {
            return new EventoDetalhe(EventoResumo.de(e), e.getPayload());
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/api/PedidoAdmController.java`:

`````java
package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.api.PagamentoResponses.EventoDetalhe;
import br.com.celke.cursos.pagamento.api.PagamentoResponses.EventoResumo;
import br.com.celke.cursos.pagamento.api.PagamentoResponses.PedidoAdm;
import br.com.celke.cursos.pagamento.api.PagamentoResponses.PedidoDetalhe;
import br.com.celke.cursos.pagamento.application.EventoAdmService;
import br.com.celke.cursos.pagamento.application.PedidoAdmService;
import br.com.celke.cursos.pagamento.application.PedidoAdmService.FiltroPedidos;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm/pagamentos")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Pedidos e eventos")
public class PedidoAdmController {

    private final PedidoAdmService pedidoAdmService;
    private final EventoAdmService eventoAdmService;

    public PedidoAdmController(PedidoAdmService pedidoAdmService, EventoAdmService eventoAdmService) {
        this.pedidoAdmService = pedidoAdmService;
        this.eventoAdmService = eventoAdmService;
    }

    @GetMapping("/pedidos")
    @Operation(
            summary = "Pedidos",
            description = "Mais recentes primeiro. Filtros: status, gateway, trecho do e-mail e período de criação"
                    + " (de inclusivo, até exclusivo; ISO-8601).")
    public Pagina<PedidoAdm> pedidos(
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) Long gatewayId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Instant de,
            @RequestParam(required = false) Instant ate,
            @ParameterObject Pageable pagina) {
        return Pagina.de(pedidoAdmService
                .listar(new FiltroPedidos(status, gatewayId, email, de, ate), pagina)
                .map(PedidoAdm::de));
    }

    @GetMapping("/pedidos/{id}")
    @Operation(summary = "Pedido", description = "Com a linha do tempo e os eventos recebidos.")
    public PedidoDetalhe pedido(@PathVariable Long id) {
        return PedidoDetalhe.de(pedidoAdmService.detalhar(id));
    }

    @GetMapping("/eventos")
    @Operation(summary = "Eventos recebidos", description = "Mais recentes primeiro; filtro opcional por status.")
    public Pagina<EventoResumo> eventos(
            @RequestParam(required = false) StatusEvento status, @ParameterObject Pageable pagina) {
        return Pagina.de(eventoAdmService.listar(status, pagina).map(EventoResumo::de));
    }

    @GetMapping("/eventos/{id}")
    @Operation(summary = "Evento", description = "Com o corpo recebido (documento e telefone mascarados).")
    public EventoDetalhe evento(@PathVariable Long id) {
        return EventoDetalhe.de(eventoAdmService.buscar(id));
    }

    @PostMapping("/eventos/{id}/reprocessamento")
    @Operation(summary = "Reprocessar evento", description = "Só eventos em ERRO ou IGNORADO.")
    public EventoResumo reprocessar(@PathVariable Long id) {
        return EventoResumo.de(eventoAdmService.reprocessar(id));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/api/WebhookController.java`:

`````java
package br.com.celke.cursos.pagamento.api;

import br.com.celke.cursos.pagamento.api.PagamentoResponses.WebhookResponse;
import br.com.celke.cursos.pagamento.application.PagamentosProperties;
import br.com.celke.cursos.pagamento.application.WebhookService;
import br.com.celke.cursos.pagamento.domain.NotificacaoRecebida;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notificações dos meios de pagamento (rota pública, declarada no {@code SecurityConfig}). O token da
 * URL identifica o gateway; a autenticação é a do próprio meio de pagamento.
 */
@RestController
@RequestMapping("/api/pagamentos/webhooks")
@Tag(name = "Pagamentos — notificações")
public class WebhookController {

    private final WebhookService webhookService;
    private final PagamentosProperties props;

    public WebhookController(WebhookService webhookService, PagamentosProperties props) {
        this.webhookService = webhookService;
        this.props = props;
    }

    @PostMapping("/{webhookToken}")
    @Operation(
            summary = "Receber notificação",
            description = "200 recebido, duplicado ou erro (gravado para o painel); 401 sem autenticação válida;"
                    + " 404 endereço desconhecido; 413 corpo grande demais.")
    public WebhookResponse receber(@PathVariable String webhookToken, HttpServletRequest request) throws IOException {
        if (webhookToken.length() > 64) {
            throw new NaoEncontradoException("Endereço de notificação desconhecido.");
        }
        NotificacaoRecebida notificacao = new NotificacaoRecebida(cabecalhos(request), corpo(request));
        return new WebhookResponse(
                webhookService.receber(webhookToken, notificacao).name().toLowerCase(Locale.ROOT));
    }

    /** Lê o corpo cru até o limite; passou dele, 413 sem ler o resto. */
    private byte[] corpo(HttpServletRequest request) throws IOException {
        long limite = props.webhookTamanhoMaximo().toBytes();
        if (request.getContentLengthLong() > limite) {
            throw grandeDemais();
        }
        try (InputStream entrada = request.getInputStream()) {
            byte[] corpo = entrada.readNBytes((int) limite + 1);
            if (corpo.length > limite) {
                throw grandeDemais();
            }
            return corpo;
        }
    }

    private static Map<String, String> cabecalhos(HttpServletRequest request) {
        Map<String, String> cabecalhos = new HashMap<>();
        for (String nome : Collections.list(request.getHeaderNames())) {
            cabecalhos.put(nome, request.getHeader(nome));
        }
        return cabecalhos;
    }

    private static RegraNegocioException grandeDemais() {
        return new RegraNegocioException(HttpStatus.CONTENT_TOO_LARGE, "Notificação grande demais.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/AvisosPagamento.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.operacao.AlertasProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Avisos de pagamento para quem administra ({@code ALERTA_EMAIL}, o mesmo dos alertas do worker):
 * chargeback, disputa e notificação com erro. Sem destinatário, ficam só no log.
 */
@Component
@EnableConfigurationProperties(AlertasProperties.class)
public class AvisosPagamento {

    private static final Logger LOG = LoggerFactory.getLogger(AvisosPagamento.class);

    private final EmailService emailService;
    private final AlertasProperties alertas;

    public AvisosPagamento(EmailService emailService, AlertasProperties alertas) {
        this.emailService = emailService;
        this.alertas = alertas;
    }

    public void pedidoContestado(Pedido pedido, StatusPedido status) {
        String titulo = status == StatusPedido.CHARGEBACK ? "Chargeback" : "Disputa aberta";
        avisar(
                "%s no pedido %s".formatted(titulo, pedido.getCodigo()),
                "Pedido %d (%s), transação %s, comprador %s. Status: %s."
                        .formatted(
                                pedido.getId(),
                                pedido.getCodigo(),
                                pedido.getTransacaoExterna(),
                                pedido.getEmail(),
                                status));
    }

    public void eventoComErro(Long eventoId, String tipo, String motivo) {
        avisar(
                "Notificação de pagamento com erro",
                "Evento %d (%s): %s\nVeja em Pagamentos → Eventos recebidos e reprocesse depois de corrigir."
                        .formatted(eventoId, tipo, motivo));
    }

    private void avisar(String titulo, String detalhes) {
        LOG.warn("{}: {}", titulo, detalhes);
        if (alertas.ligados()) {
            emailService.enviar(
                    alertas.email(),
                    "[Plataforma de Cursos] " + titulo,
                    "alerta-operacao",
                    Map.of("titulo", titulo, "detalhes", detalhes));
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/CheckoutService.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.pagamento.domain.Checkout;
import br.com.celke.cursos.pagamento.domain.Comprador;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.PedidoHistorico;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.pagamento.infra.OfertaRepository;
import br.com.celke.cursos.pagamento.infra.PedidoHistoricoRepository;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Botão "Comprar" (abre o pedido e monta o checkout) e a página de retorno (situação do pedido). */
@Service
public class CheckoutService {

    private final OfertaRepository ofertas;
    private final GatewayRepository gateways;
    private final PedidoRepository pedidos;
    private final PedidoHistoricoRepository historico;
    private final GatewaysRegistrados registrados;
    private final ConfiguracaoGatewayService configuracoes;
    private final ConteudoTurmaService conteudoTurmaService;
    private final UsuarioService usuarioService;
    private final Clock clock;

    public CheckoutService(
            OfertaRepository ofertas,
            GatewayRepository gateways,
            PedidoRepository pedidos,
            PedidoHistoricoRepository historico,
            GatewaysRegistrados registrados,
            ConfiguracaoGatewayService configuracoes,
            ConteudoTurmaService conteudoTurmaService,
            UsuarioService usuarioService,
            Clock clock) {
        this.ofertas = ofertas;
        this.gateways = gateways;
        this.pedidos = pedidos;
        this.historico = historico;
        this.registrados = registrados;
        this.configuracoes = configuracoes;
        this.conteudoTurmaService = conteudoTurmaService;
        this.usuarioService = usuarioService;
        this.clock = clock;
    }

    /** Abre o pedido com o e-mail da conta e devolve o checkout do meio de pagamento da oferta. */
    @Transactional
    public CheckoutIniciado iniciar(Long usuarioId, Long ofertaId) {
        Oferta oferta = ofertas.findById(ofertaId)
                .filter(Oferta::isAtiva)
                .orElseThrow(() -> new NaoEncontradoException("Oferta não encontrada."));
        Gateway gateway = gateways.findById(oferta.getGatewayId())
                .filter(Gateway::isAtivo)
                .orElseThrow(() -> new ConflitoException("Esta oferta não está à venda no momento."));
        TurmaResumo turma = conteudoTurmaService
                .turma(oferta.getTurmaId())
                .filter(t -> t.cursoStatus() == StatusCurso.PUBLICADO && t.turmaStatus() == StatusTurma.ATIVA)
                .orElseThrow(() -> new ConflitoException("Este curso não está recebendo matrículas no momento."));
        Usuario usuario = usuarioService.buscarPorId(usuarioId).orElseThrow();
        Instant agora = clock.instant();
        Pedido pedido =
                pedidos.save(Pedido.doCheckout(oferta, usuario.getId(), usuario.getEmail(), usuario.getNome(), agora));
        historico.save(
                new PedidoHistorico(pedido.getId(), null, StatusPedido.CRIADO, null, "Checkout iniciado", agora));
        Checkout checkout = registrados
                .de(gateway.getTipo())
                .iniciarCheckout(
                        pedido,
                        oferta,
                        new Comprador(usuario.getEmail(), usuario.getNome()),
                        configuracoes.ler(gateway));
        return new CheckoutIniciado(pedido.getCodigo(), turma.turmaId(), checkout);
    }

    /**
     * Situação do pedido para a página de retorno. Curso e turma só para o dono logado; para os outros,
     * só o status (o código sozinho não revela nada além disso).
     */
    @Transactional(readOnly = true)
    public SituacaoPedido situacao(String codigo, Long usuarioId) {
        Pedido pedido =
                pedidos.findByCodigo(codigo).orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado."));
        if (usuarioId == null || !pedido.pertenceA(usuarioId)) {
            return new SituacaoPedido(pedido.getCodigo(), pedido.getStatus(), null);
        }
        TurmaResumo turma = ofertas.findById(pedido.getOfertaId())
                .flatMap(o -> conteudoTurmaService.turma(o.getTurmaId()))
                .orElse(null);
        return new SituacaoPedido(pedido.getCodigo(), pedido.getStatus(), turma);
    }

    public record CheckoutIniciado(String pedido, Long turmaId, Checkout checkout) {}

    /** @param turma nula quando quem consulta não é o dono do pedido */
    public record SituacaoPedido(String codigo, StatusPedido status, TurmaResumo turma) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/CifraSegredos.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Segredos dos meios de pagamento (hottok, client secret...) cifrados com AES-256-GCM. Cada gravação
 * usa um IV aleatório de 12 bytes, guardado na frente do texto cifrado. O webhook token do gateway
 * entra como dado associado: o blob de um gateway não decifra copiado para outro.
 */
public class CifraSegredos {

    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final SecretKey chave;

    /** @param chaveBase64 32 bytes em Base64; em branco, a cifra fica indisponível */
    public CifraSegredos(String chaveBase64) {
        this.chave = chaveBase64 == null || chaveBase64.isBlank()
                ? null
                : new SecretKeySpec(Base64.getDecoder().decode(chaveBase64.strip()), "AES");
    }

    public boolean disponivel() {
        return chave != null;
    }

    public byte[] cifrar(String texto, String dadoAssociado) {
        exigirChave();
        byte[] iv = new byte[TAMANHO_IV];
        ALEATORIO.nextBytes(iv);
        try {
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            cifra.updateAAD(dadoAssociado.getBytes(StandardCharsets.UTF_8));
            byte[] cifrado = cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + cifrado.length)
                    .put(iv)
                    .put(cifrado)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível cifrar os segredos.", e);
        }
    }

    /** @throws IllegalStateException com chave errada, dado associado diferente ou blob alterado */
    public String decifrar(byte[] blob, String dadoAssociado) {
        exigirChave();
        if (blob == null || blob.length <= TAMANHO_IV) {
            throw new IllegalStateException("Segredos gravados em formato inválido.");
        }
        try {
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, blob, 0, TAMANHO_IV));
            cifra.updateAAD(dadoAssociado.getBytes(StandardCharsets.UTF_8));
            byte[] texto = cifra.doFinal(blob, TAMANHO_IV, blob.length - TAMANHO_IV);
            return new String(texto, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Não foi possível decifrar os segredos do meio de pagamento. A PAGAMENTOS_CHAVE mudou?", e);
        }
    }

    private void exigirChave() {
        if (chave == null) {
            throw new IllegalStateException("PAGAMENTOS_CHAVE não está definida.");
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/ConfiguracaoGatewayService.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.CampoConfiguracao;
import br.com.celke.cursos.pagamento.domain.ConfiguracaoGateway;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.TipoCampo;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.ErroCampo;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuração de um gateway: valida os campos que o meio de pagamento declara, separa os secretos
 * (cifrados) dos comuns e monta a {@link ConfiguracaoGateway} decifrada para a implementação.
 */
@Service
public class ConfiguracaoGatewayService {

    static final int TAMANHO_MAXIMO_VALOR = 500;
    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() {};

    private final GatewaysRegistrados registrados;
    private final CifraSegredos cifra;
    private final JsonMapper jsonMapper;

    public ConfiguracaoGatewayService(GatewaysRegistrados registrados, CifraSegredos cifra, JsonMapper jsonMapper) {
        this.registrados = registrados;
        this.cifra = cifra;
        this.jsonMapper = jsonMapper;
    }

    /** Campos comuns e secretos decifrados, juntos. */
    public ConfiguracaoGateway ler(Gateway gateway) {
        Map<String, String> valores = new HashMap<>(comuns(gateway));
        valores.putAll(segredos(gateway));
        return new ConfiguracaoGateway(gateway.getId(), gateway.getTipo(), valores);
    }

    public Map<String, String> comuns(Gateway gateway) {
        return jsonMapper.readValue(gateway.getConfiguracao(), MAPA);
    }

    /** @throws IllegalStateException se a chave não está definida ou não decifra (chave trocada) */
    public Map<String, String> segredos(Gateway gateway) {
        byte[] blob = gateway.getSegredos();
        if (blob == null) {
            return Map.of();
        }
        return jsonMapper.readValue(cifra.decifrar(blob, gateway.getWebhookToken()), MAPA);
    }

    /**
     * Valida e grava a configuração. Campo secreto em branco mantém o valor atual (o painel nunca o
     * recebe de volta); campo comum em branco fica com o padrão do campo, se houver.
     */
    public void configurar(Gateway gateway, String nome, Map<String, String> recebidos, Instant agora) {
        List<CampoConfiguracao> campos = registrados.de(gateway.getTipo()).camposConfiguracao();
        Map<String, String> valores = recebidos == null ? Map.of() : recebidos;
        Map<String, String> segredosAtuais = gateway.getSegredos() == null ? Map.of() : segredos(gateway);
        List<ErroCampo> erros = new ArrayList<>();
        Set<String> chaves = campos.stream().map(CampoConfiguracao::chave).collect(Collectors.toSet());
        valores.keySet().stream()
                .filter(chave -> !chaves.contains(chave))
                .sorted()
                .forEach(chave -> erros.add(new ErroCampo("valores." + chave, "Campo desconhecido.")));

        Map<String, String> comuns = new TreeMap<>();
        Map<String, String> secretos = new TreeMap<>();
        for (CampoConfiguracao campo : campos) {
            String valor = limpo(valores.get(campo.chave()));
            if (campo.secreto() && valor == null) {
                valor = segredosAtuais.get(campo.chave());
            }
            if (valor == null) {
                valor = campo.padrao();
            }
            String erro = erro(campo, valor);
            if (erro != null) {
                erros.add(new ErroCampo("valores." + campo.chave(), erro));
            } else if (valor != null) {
                (campo.secreto() ? secretos : comuns).put(campo.chave(), valor);
            }
        }
        if (!erros.isEmpty()) {
            erros.sort(Comparator.comparing(ErroCampo::campo));
            throw new ConfiguracaoInvalidaException(erros);
        }
        byte[] blob = null;
        if (!secretos.isEmpty()) {
            if (!cifra.disponivel()) {
                throw new ConflitoException(
                        "Defina a PAGAMENTOS_CHAVE no .env do ambiente para gravar as credenciais do meio de pagamento.");
            }
            blob = cifra.cifrar(jsonMapper.writeValueAsString(secretos), gateway.getWebhookToken());
        }
        gateway.configurar(nome, jsonMapper.writeValueAsString(comuns), blob, agora);
    }

    /** Situação de cada campo secreto, sem o valor: configurado e os 4 últimos caracteres. */
    public Map<String, SegredoResumo> resumoSegredos(Gateway gateway) {
        Map<String, String> atuais = gateway.getSegredos() == null ? Map.of() : segredos(gateway);
        Map<String, SegredoResumo> resumo = new LinkedHashMap<>();
        for (CampoConfiguracao campo : registrados.de(gateway.getTipo()).camposConfiguracao()) {
            if (campo.secreto()) {
                String valor = atuais.get(campo.chave());
                resumo.put(
                        campo.chave(),
                        valor == null
                                ? new SegredoResumo(false, null)
                                : new SegredoResumo(
                                        true, valor.length() <= 4 ? null : valor.substring(valor.length() - 4)));
            }
        }
        return resumo;
    }

    /** @param final4 os 4 últimos caracteres (nulo se o segredo é curto demais para mostrar algo) */
    public record SegredoResumo(boolean configurado, String final4) {}

    private static String erro(CampoConfiguracao campo, String valor) {
        if (valor == null) {
            return campo.obrigatorio() ? "Informe " + campo.rotulo() + "." : null;
        }
        if (valor.length() > TAMANHO_MAXIMO_VALOR) {
            return "Use no máximo " + TAMANHO_MAXIMO_VALOR + " caracteres.";
        }
        if (campo.tipo() == TipoCampo.SELECAO && !campo.opcoes().contains(valor)) {
            return "Escolha uma das opções: " + String.join(", ", campo.opcoes()) + ".";
        }
        if (campo.tipo() == TipoCampo.URL && !urlValida(valor)) {
            return "Informe um endereço começando com https://.";
        }
        return null;
    }

    private static boolean urlValida(String valor) {
        try {
            URI uri = URI.create(valor);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String limpo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/ConfiguracaoInvalidaException.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.shared.erro.ErroCampo;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Campos da configuração que não passam pelas regras do meio de pagamento: 400 com a lista {@code erros}. */
public class ConfiguracaoInvalidaException extends RegraNegocioException {

    private final List<ErroCampo> erros;

    public ConfiguracaoInvalidaException(List<ErroCampo> erros) {
        super(HttpStatus.BAD_REQUEST, "Dados inválidos.");
        this.erros = List.copyOf(erros);
    }

    public List<ErroCampo> getErros() {
        return erros;
    }

    @Override
    public Map<String, Object> propriedades() {
        return Map.of("erros", erros);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/EventoAdmService.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.EventoRecebido;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import br.com.celke.cursos.pagamento.infra.EventoRecebidoRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Eventos recebidos no painel: lista por status, detalhe com o corpo e o reprocessamento. */
@Service
public class EventoAdmService {

    private final EventoRecebidoRepository eventos;
    private final RegistroEventos registro;
    private final Clock clock;

    public EventoAdmService(EventoRecebidoRepository eventos, RegistroEventos registro, Clock clock) {
        this.eventos = eventos;
        this.registro = registro;
        this.clock = clock;
    }

    /** Mais recentes primeiro; sem status, todos. */
    @Transactional(readOnly = true)
    public Page<EventoRecebido> listar(StatusEvento status, Pageable pagina) {
        PageRequest ordenada =
                PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by(Sort.Direction.DESC, "id"));
        return status == null ? eventos.findAll(ordenada) : eventos.findByStatus(status, ordenada);
    }

    @Transactional(readOnly = true)
    public EventoRecebido buscar(Long id) {
        return eventos.findById(id).orElseThrow(() -> new NaoEncontradoException("Evento não encontrado."));
    }

    /**
     * Coloca de novo na fila um evento em ERRO ou IGNORADO (ex.: a oferta foi cadastrada depois). Sem o
     * evento normalizado (corpo que não foi interpretado, ou apagado pela retenção), não há o que refazer.
     */
    @Transactional
    public EventoRecebido reprocessar(Long id) {
        EventoRecebido evento =
                eventos.travar(id).orElseThrow(() -> new NaoEncontradoException("Evento não encontrado."));
        if (evento.getStatus() != StatusEvento.ERRO && evento.getStatus() != StatusEvento.IGNORADO) {
            throw new ConflitoException("Só eventos com erro ou ignorados podem ser reprocessados.");
        }
        if (evento.getDados() == null) {
            throw new ConflitoException(
                    "Este evento não foi interpretado ou o corpo já foi apagado pela retenção: reprocessar não resolve.");
        }
        evento.reabrir();
        registro.enfileirar(evento.getId(), clock.instant());
        return evento;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/GatewayAdmService.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.auth.application.ContaProperties;
import br.com.celke.cursos.auth.application.TokenSeguro;
import br.com.celke.cursos.pagamento.application.ConfiguracaoGatewayService.SegredoResumo;
import br.com.celke.cursos.pagamento.domain.CampoConfiguracao;
import br.com.celke.cursos.pagamento.domain.Capacidade;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.ResultadoTeste;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Meios de pagamento pelo painel: tipos disponíveis, configuração, teste das credenciais e ativação. */
@Service
public class GatewayAdmService {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayAdmService.class);

    private final GatewayRepository gateways;
    private final GatewaysRegistrados registrados;
    private final ConfiguracaoGatewayService configuracoes;
    private final CifraSegredos cifra;
    private final ContaProperties conta;
    private final Clock clock;

    public GatewayAdmService(
            GatewayRepository gateways,
            GatewaysRegistrados registrados,
            ConfiguracaoGatewayService configuracoes,
            CifraSegredos cifra,
            ContaProperties conta,
            Clock clock) {
        this.gateways = gateways;
        this.registrados = registrados;
        this.configuracoes = configuracoes;
        this.cifra = cifra;
        this.conta = conta;
        this.clock = clock;
    }

    public TiposDisponiveis tipos() {
        return new TiposDisponiveis(
                cifra.disponivel(),
                registrados.todos().stream()
                        .map(g -> new TipoDisponivel(g.tipo(), g.nome(), g.camposConfiguracao(), g.capacidades()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public List<GatewayDetalhe> listar() {
        return gateways.findAllByOrderById().stream().map(this::detalhe).toList();
    }

    @Transactional(readOnly = true)
    public GatewayDetalhe buscar(Long id) {
        return detalhe(gateway(id));
    }

    @Transactional
    public GatewayDetalhe criar(TipoGateway tipo, String nome, Map<String, String> valores) {
        registrados.de(tipo);
        Instant agora = clock.instant();
        Gateway gateway = new Gateway(tipo, nome, TokenSeguro.gerar(), agora);
        configuracoes.configurar(gateway, nome, valores, agora);
        return detalhe(gateways.save(gateway));
    }

    @Transactional
    public GatewayDetalhe atualizar(Long id, String nome, Map<String, String> valores) {
        Gateway gateway = gateway(id);
        configuracoes.configurar(gateway, nome, valores, clock.instant());
        return detalhe(gateway);
    }

    /** Confere as credenciais gravadas com o meio de pagamento. */
    @Transactional(readOnly = true)
    public ResultadoTeste testar(Long id) {
        Gateway gateway = gateway(id);
        try {
            return registrados.de(gateway.getTipo()).testar(configuracoes.ler(gateway));
        } catch (IllegalStateException e) {
            LOG.warn("Teste do gateway {}: {}", id, e.getMessage());
            return ResultadoTeste.falha(e.getMessage());
        }
    }

    /** Passa a ser o meio de pagamento dos novos checkouts; o anterior continua recebendo notificações. */
    @Transactional
    public GatewayDetalhe ativar(Long id) {
        Gateway gateway = gateway(id);
        gateways.desativarOutros(id);
        gateway = gateway(id);
        gateway.ativar(clock.instant());
        gateways.flush();
        return detalhe(gateway);
    }

    @Transactional
    public GatewayDetalhe desativar(Long id) {
        Gateway gateway = gateway(id);
        gateway.desativar(clock.instant());
        return detalhe(gateway);
    }

    private Gateway gateway(Long id) {
        return gateways.findById(id).orElseThrow(() -> new NaoEncontradoException("Meio de pagamento não encontrado."));
    }

    private GatewayDetalhe detalhe(Gateway gateway) {
        GatewayPagamento implementacao = registrados.de(gateway.getTipo());
        Map<String, SegredoResumo> segredos;
        boolean legiveis = true;
        try {
            segredos = configuracoes.resumoSegredos(gateway);
        } catch (IllegalStateException e) {
            // Chave ausente ou trocada: o painel avisa e pede para informar os segredos de novo
            segredos = Map.of();
            legiveis = false;
        }
        return new GatewayDetalhe(
                gateway,
                implementacao.nome(),
                configuracoes.comuns(gateway),
                segredos,
                legiveis,
                conta.urlAluno() + "/api/pagamentos/webhooks/" + gateway.getWebhookToken());
    }

    public record TiposDisponiveis(boolean cifraDisponivel, List<TipoDisponivel> tipos) {}

    public record TipoDisponivel(
            TipoGateway tipo, String nome, List<CampoConfiguracao> campos, Set<Capacidade> capacidades) {}

    /**
     * @param valores campos comuns (os secretos nunca saem; ver {@code segredos})
     * @param segredosLegiveis falso se a chave mudou ou falta: os segredos precisam ser informados de novo
     * @param webhookUrl endereço para cadastrar no meio de pagamento
     */
    public record GatewayDetalhe(
            Gateway gateway,
            String nomeTipo,
            Map<String, String> valores,
            Map<String, SegredoResumo> segredos,
            boolean segredosLegiveis,
            String webhookUrl) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/GatewaysRegistrados.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Os meios de pagamento disponíveis (um bean por tipo). Dois beans do mesmo tipo impedem a subida. */
@Component
public class GatewaysRegistrados {

    private final Map<TipoGateway, GatewayPagamento> porTipo = new EnumMap<>(TipoGateway.class);

    public GatewaysRegistrados(List<GatewayPagamento> gateways) {
        for (GatewayPagamento gateway : gateways) {
            GatewayPagamento anterior = porTipo.put(gateway.tipo(), gateway);
            if (anterior != null) {
                throw new IllegalStateException("Dois meios de pagamento do tipo " + gateway.tipo() + ": "
                        + anterior.getClass().getName() + " e "
                        + gateway.getClass().getName());
            }
        }
    }

    /** @throws IllegalStateException se o tipo gravado no banco não tem implementação nesta versão */
    public GatewayPagamento de(TipoGateway tipo) {
        GatewayPagamento gateway = porTipo.get(tipo);
        if (gateway == null) {
            throw new IllegalStateException("Meio de pagamento sem implementação: " + tipo);
        }
        return gateway;
    }

    public List<GatewayPagamento> todos() {
        return porTipo.values().stream()
                .sorted(Comparator.comparing(GatewayPagamento::tipo))
                .toList();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/MascaraDadosPessoais.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Mascara documento, telefone e IP no corpo de uma notificação antes de gravar: o painel mostra o
 * corpo para conferência, sem guardar dado pessoal que a plataforma não usa. Vale para qualquer meio
 * de pagamento: o campo é reconhecido pelo nome.
 */
public final class MascaraDadosPessoais {

    /** Nomes de campo (sem diferenciar maiúsculas) cujo valor é mascarado. */
    private static final Pattern CAMPO_PESSOAL =
            Pattern.compile(".*(document|doc|cpf|cnpj|phone|telefone|celular|_ip|^ip).*");

    private MascaraDadosPessoais() {}

    /** Mascara o JSON no lugar (objetos e listas, em qualquer profundidade). */
    public static JsonNode mascarar(JsonNode no) {
        if (no instanceof ObjectNode objeto) {
            List<Map.Entry<String, JsonNode>> campos = List.copyOf(objeto.properties());
            for (Map.Entry<String, JsonNode> campo : campos) {
                JsonNode valor = campo.getValue();
                if (valor.isValueNode() && !valor.isNull() && pessoal(campo.getKey())) {
                    objeto.put(campo.getKey(), mascarar(valor.asString()));
                } else {
                    mascarar(valor);
                }
            }
        } else if (no instanceof ArrayNode lista) {
            lista.forEach(MascaraDadosPessoais::mascarar);
        }
        return no;
    }

    /** Só os 2 últimos caracteres ficam visíveis ("***64"). */
    public static String mascarar(String valor) {
        if (valor == null || valor.isBlank()) {
            return valor;
        }
        String limpo = valor.strip();
        return limpo.length() <= 2 ? "***" : "***" + limpo.substring(limpo.length() - 2);
    }

    static boolean pessoal(String campo) {
        return CAMPO_PESSOAL.matcher(campo.toLowerCase(Locale.ROOT)).matches();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/OfertaAdmService.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Oferta.DadosOferta;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.pagamento.infra.OfertaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ofertas de uma turma (aba "Venda" do ADM). */
@Service
public class OfertaAdmService {

    private final OfertaRepository ofertas;
    private final GatewayRepository gateways;
    private final ConteudoTurmaService conteudoTurmaService;
    private final Clock clock;

    public OfertaAdmService(
            OfertaRepository ofertas,
            GatewayRepository gateways,
            ConteudoTurmaService conteudoTurmaService,
            Clock clock) {
        this.ofertas = ofertas;
        this.gateways = gateways;
        this.conteudoTurmaService = conteudoTurmaService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OfertaComGateway> listar(Long turmaId) {
        exigirTurma(turmaId);
        Map<Long, Gateway> porId =
                gateways.findAll().stream().collect(Collectors.toMap(Gateway::getId, Function.identity()));
        return ofertas.findByTurmaIdOrderById(turmaId).stream()
                .map(o -> new OfertaComGateway(o, porId.get(o.getGatewayId())))
                .toList();
    }

    /** Sem gateway informado, a oferta fica no meio de pagamento ativo. */
    @Transactional
    public OfertaComGateway criar(Long turmaId, Long gatewayId, DadosOferta dados) {
        exigirTurma(turmaId);
        Gateway gateway = gatewayId == null
                ? gateways.findByAtivoTrue()
                        .orElseThrow(() ->
                                new ConflitoException("Configure e ative um meio de pagamento antes de criar ofertas."))
                : gateways.findById(gatewayId)
                        .orElseThrow(() -> new NaoEncontradoException("Meio de pagamento não encontrado."));
        if (ofertas.existsByGatewayIdAndProdutoExternoAndOfertaExterna(
                gateway.getId(),
                dados.produtoExterno().strip(),
                dados.ofertaExterna().strip())) {
            throw duplicada();
        }
        return new OfertaComGateway(salvar(new Oferta(turmaId, gateway.getId(), dados, clock.instant())), gateway);
    }

    @Transactional
    public OfertaComGateway atualizar(Long id, DadosOferta dados) {
        Oferta oferta = oferta(id);
        if (ofertas.existsByGatewayIdAndProdutoExternoAndOfertaExternaAndIdNot(
                oferta.getGatewayId(),
                dados.produtoExterno().strip(),
                dados.ofertaExterna().strip(),
                id)) {
            throw duplicada();
        }
        oferta.atualizar(dados, clock.instant());
        return new OfertaComGateway(
                salvar(oferta), gateways.findById(oferta.getGatewayId()).orElse(null));
    }

    @Transactional
    public OfertaComGateway alterarAtiva(Long id, boolean ativa) {
        Oferta oferta = oferta(id);
        oferta.alterarAtiva(ativa, clock.instant());
        return new OfertaComGateway(
                oferta, gateways.findById(oferta.getGatewayId()).orElse(null));
    }

    private Oferta salvar(Oferta oferta) {
        try {
            // O único do banco decide quando duas ofertas iguais chegam ao mesmo tempo
            return ofertas.saveAndFlush(oferta);
        } catch (DataIntegrityViolationException e) {
            throw duplicada();
        }
    }

    private Oferta oferta(Long id) {
        return ofertas.findById(id).orElseThrow(() -> new NaoEncontradoException("Oferta não encontrada."));
    }

    private void exigirTurma(Long turmaId) {
        if (conteudoTurmaService.turma(turmaId).isEmpty()) {
            throw new NaoEncontradoException("Turma não encontrada.");
        }
    }

    private static ConflitoException duplicada() {
        return new ConflitoException("Já existe uma oferta com este produto e esta oferta no meio de pagamento.");
    }

    public record OfertaComGateway(Oferta oferta, Gateway gateway) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/OfertasCatalogoPagamento.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.curso.application.OfertasCatalogo;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.pagamento.infra.OfertaRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Ofertas que o catálogo mostra: as ativas da turma no meio de pagamento ativo, da mais barata à mais cara. */
@Component
public class OfertasCatalogoPagamento implements OfertasCatalogo {

    private final GatewayRepository gateways;
    private final OfertaRepository ofertas;

    public OfertasCatalogoPagamento(GatewayRepository gateways, OfertaRepository ofertas) {
        this.gateways = gateways;
        this.ofertas = ofertas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfertaCatalogo> ativas(Long turmaId) {
        return gateways
                .findByAtivoTrue()
                .map(gateway -> ofertas.findByTurmaIdAndGatewayIdAndAtivaTrueOrderByPrecoCentavosAscIdAsc(
                        turmaId, gateway.getId()))
                .orElse(List.of())
                .stream()
                .map(o -> new OfertaCatalogo(o.getId(), o.getTitulo(), o.getPrecoCentavos(), o.getMoeda()))
                .toList();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/PagamentosConfig.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.TransicoesPedido;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PagamentosProperties.class)
public class PagamentosConfig {

    @Bean
    CifraSegredos cifraSegredos(PagamentosProperties props) {
        return new CifraSegredos(props.chave());
    }

    @Bean
    TransicoesPedido transicoesPedido(PagamentosProperties props) {
        return new TransicoesPedido(props.disputaSuspende());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/PagamentosProperties.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Pagamentos.
 *
 * @param chave chave AES-256 dos segredos dos meios de pagamento, 32 bytes em Base64 (em branco: a API
 *     sobe, mas não grava segredos)
 * @param webhookTamanhoMaximo tamanho máximo do corpo de uma notificação
 * @param webhookRequisicoesPorIpPorMinuto notificações por IP a cada minuto
 * @param retencaoPayload depois desse prazo, o corpo dos eventos processados ou ignorados é apagado
 * @param disputaSuspende pedido em disputa tira o acesso até a disputa terminar
 * @param atrasoSuspendeApos pedido atrasado há mais que isso tira o acesso (nulo: nunca)
 */
@Validated
@ConfigurationProperties("app.pagamentos")
public record PagamentosProperties(
        String chave,
        @NotNull DataSize webhookTamanhoMaximo,
        @Min(1) int webhookRequisicoesPorIpPorMinuto,
        @NotNull Duration retencaoPayload,
        boolean disputaSuspende,
        Duration atrasoSuspendeApos,
        @NotNull Reconciliacao reconciliacao) {

    public PagamentosProperties {
        if (chave != null && !chave.isBlank()) {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(chave.strip());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("PAGAMENTOS_CHAVE precisa estar em Base64.", e);
            }
            if (bytes.length != 32) {
                throw new IllegalArgumentException(
                        "PAGAMENTOS_CHAVE precisa ter 32 bytes (openssl rand -base64 32); tem " + bytes.length + ".");
            }
        }
    }

    /**
     * Reconciliação diária pela API dos meios de pagamento.
     *
     * @param ativa desligada por padrão (depende das credenciais da API)
     * @param pendenteApos pedido criado ou aguardando pagamento há mais que isso é consultado
     * @param semEventoApos pedido pago sem notificação há mais que isso é consultado
     * @param janela pedidos sem mudança há mais que isso deixam de ser consultados
     */
    public record Reconciliacao(
            boolean ativa,
            @NotNull Duration pendenteApos,
            @NotNull Duration semEventoApos,
            @NotNull Duration janela) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/PedidoAdmService.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.EventoRecebido;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.PedidoHistorico;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.infra.EventoRecebidoRepository;
import br.com.celke.cursos.pagamento.infra.OfertaRepository;
import br.com.celke.cursos.pagamento.infra.PedidoHistoricoRepository;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pedidos no painel: lista com filtros e o detalhe com a linha do tempo e os eventos recebidos. */
@Service
public class PedidoAdmService {

    private final PedidoRepository pedidos;
    private final PedidoHistoricoRepository historico;
    private final EventoRecebidoRepository eventos;
    private final OfertaRepository ofertas;

    public PedidoAdmService(
            PedidoRepository pedidos,
            PedidoHistoricoRepository historico,
            EventoRecebidoRepository eventos,
            OfertaRepository ofertas) {
        this.pedidos = pedidos;
        this.historico = historico;
        this.eventos = eventos;
        this.ofertas = ofertas;
    }

    /** Mais recentes primeiro. */
    @Transactional(readOnly = true)
    public Page<PedidoComOferta> listar(FiltroPedidos filtro, Pageable pagina) {
        Page<Pedido> page = pedidos.findAll(
                especificacao(filtro),
                PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, Oferta> porId =
                ofertas
                        .findAllById(page.getContent().stream()
                                .map(Pedido::getOfertaId)
                                .distinct()
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Oferta::getId, Function.identity()));
        return page.map(p -> new PedidoComOferta(p, porId.get(p.getOfertaId())));
    }

    @Transactional(readOnly = true)
    public DetalhePedido detalhar(Long id) {
        Pedido pedido = pedidos.findById(id).orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado."));
        return new DetalhePedido(
                new PedidoComOferta(
                        pedido, ofertas.findById(pedido.getOfertaId()).orElse(null)),
                historico.findByPedidoIdOrderById(id),
                eventos.findByPedidoIdOrderById(id));
    }

    private static Specification<Pedido> especificacao(FiltroPedidos filtro) {
        Specification<Pedido> spec = Specification.unrestricted();
        if (filtro.status() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("status"), filtro.status()));
        }
        if (filtro.gatewayId() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("gatewayId"), filtro.gatewayId()));
        }
        if (filtro.email() != null && !filtro.email().isBlank()) {
            String trecho = "%" + filtro.email().strip().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((raiz, consulta, cb) -> cb.like(raiz.get("email"), trecho));
        }
        if (filtro.de() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("criadoEm"), filtro.de()));
        }
        if (filtro.ate() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.lessThan(raiz.get("criadoEm"), filtro.ate()));
        }
        return spec;
    }

    /** Filtros opcionais da lista; o e-mail aceita um trecho. */
    public record FiltroPedidos(StatusPedido status, Long gatewayId, String email, Instant de, Instant ate) {}

    public record PedidoComOferta(Pedido pedido, Oferta oferta) {}

    public record DetalhePedido(
            PedidoComOferta pedido, List<PedidoHistorico> historico, List<EventoRecebido> eventos) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/ProcessamentoEventoService.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.auth.application.ContaProperties;
import br.com.celke.cursos.auth.application.RecuperacaoSenhaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService.Concessao;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService.NovaConcessao;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.EventoRecebido;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.PedidoHistorico;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.domain.TransicoesPedido;
import br.com.celke.cursos.pagamento.domain.TransicoesPedido.Decisao;
import br.com.celke.cursos.pagamento.domain.TransicoesPedido.EfeitoMatricula;
import br.com.celke.cursos.pagamento.infra.EventoRecebidoRepository;
import br.com.celke.cursos.pagamento.infra.OfertaRepository;
import br.com.celke.cursos.pagamento.infra.PedidoHistoricoRepository;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Aplica um evento gravado ao pedido (roda no worker, uma transação por evento, com o pedido
 * travado): acha ou cria o pedido, decide a mudança pela {@link TransicoesPedido}, grava o histórico,
 * concede ou cancela a matrícula e envia os e-mails. Exceção desfaz tudo e a fila tenta de novo.
 */
@Service
public class ProcessamentoEventoService {

    private final EventoRecebidoRepository eventos;
    private final PedidoRepository pedidos;
    private final PedidoHistoricoRepository historico;
    private final OfertaRepository ofertas;
    private final UsuarioService usuarioService;
    private final MatriculaPagamentoService matriculaPagamentoService;
    private final ConteudoTurmaService conteudoTurmaService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final EmailService emailService;
    private final AvisosPagamento avisos;
    private final TransicoesPedido transicoes;
    private final ContaProperties conta;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ProcessamentoEventoService(
            EventoRecebidoRepository eventos,
            PedidoRepository pedidos,
            PedidoHistoricoRepository historico,
            OfertaRepository ofertas,
            UsuarioService usuarioService,
            MatriculaPagamentoService matriculaPagamentoService,
            ConteudoTurmaService conteudoTurmaService,
            RecuperacaoSenhaService recuperacaoSenhaService,
            EmailService emailService,
            AvisosPagamento avisos,
            TransicoesPedido transicoes,
            ContaProperties conta,
            JsonMapper jsonMapper,
            Clock clock) {
        this.eventos = eventos;
        this.pedidos = pedidos;
        this.historico = historico;
        this.ofertas = ofertas;
        this.usuarioService = usuarioService;
        this.matriculaPagamentoService = matriculaPagamentoService;
        this.conteudoTurmaService = conteudoTurmaService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
        this.emailService = emailService;
        this.avisos = avisos;
        this.transicoes = transicoes;
        this.conta = conta;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public void processar(Long eventoId) {
        Instant agora = clock.instant();
        EventoRecebido evento = eventos.travar(eventoId)
                .orElseThrow(() -> new ErroPermanenteException("Evento de pagamento " + eventoId + " não existe."));
        if (evento.getStatus() != StatusEvento.RECEBIDO) {
            // Já processado (job repetido): nada a fazer
            return;
        }
        if (evento.getDados() == null) {
            evento.falhou("O evento não tem dados para processar.", agora);
            return;
        }
        EventoPagamento dados = jsonMapper.readValue(evento.getDados(), EventoPagamento.class);
        if (dados.statusPedido() == null) {
            evento.ignorado(null, "O evento " + dados.tipoOriginal() + " não muda o pedido.", agora);
            return;
        }
        Optional<Pedido> encontrado = localizar(evento.getGatewayId(), dados);
        Pedido pedido;
        if (encontrado.isPresent()) {
            pedido = encontrado.get();
        } else {
            Optional<Oferta> oferta = ofertas.findByGatewayIdAndProdutoExternoAndOfertaExterna(
                    evento.getGatewayId(), dados.produtoExterno(), dados.ofertaExterna());
            if (oferta.isEmpty()) {
                evento.ignorado(
                        null,
                        "Oferta não cadastrada (produto %s, oferta %s)."
                                .formatted(dados.produtoExterno(), dados.ofertaExterna()),
                        agora);
                return;
            }
            if (dados.comprador() == null || dados.comprador().email() == null) {
                evento.ignorado(null, "A notificação não traz o e-mail do comprador.", agora);
                return;
            }
            pedido = pedidos.saveAndFlush(Pedido.externo(
                    oferta.get(), dados.comprador().email(), dados.comprador().nome(), dados.transacao(), agora));
            historico.save(new PedidoHistorico(
                    pedido.getId(),
                    null,
                    StatusPedido.CRIADO,
                    evento.getId(),
                    "Venda sem checkout da plataforma",
                    agora));
        }
        pedido.vincularTransacao(dados.transacao(), agora);

        StatusPedido anterior = pedido.getStatus();
        Decisao decisao =
                transicoes.decidir(anterior, dados.statusPedido(), pedido.getUltimoEventoEm(), dados.ocorridoEm());
        if (!decisao.aplica()) {
            evento.ignorado(pedido.getId(), decisao.motivo(), agora);
            return;
        }
        pedido.registrarPagamento(dados.valorCentavos(), dados.moeda(), dados.formaPagamento(), agora);
        pedido.mudarStatus(dados.statusPedido(), dados.ocorridoEm(), agora);
        historico.save(new PedidoHistorico(
                pedido.getId(), anterior, dados.statusPedido(), evento.getId(), dados.tipoOriginal(), agora));
        aplicar(decisao.efeito(), pedido, dados, agora);
        if (dados.statusPedido() == StatusPedido.CHARGEBACK || dados.statusPedido() == StatusPedido.EM_DISPUTA) {
            avisos.pedidoContestado(pedido, dados.statusPedido());
        }
        evento.processado(pedido.getId(), agora);
    }

    /** O job desistiu (tentativas esgotadas): o evento fica em ERRO e o admin é avisado. */
    @Transactional
    public void falhar(Long eventoId, String motivo) {
        eventos.findById(eventoId).ifPresent(evento -> {
            evento.falhou(motivo, clock.instant());
            avisos.eventoComErro(evento.getId(), evento.getTipoOriginal(), motivo);
        });
    }

    /**
     * Tira o acesso de um pedido atrasado há mais tempo que o tolerado; o pedido continua
     * {@code ATRASADO} e a aprovação da cobrança devolve o acesso.
     */
    @Transactional
    public void suspenderPorAtraso(Long pedidoId) {
        pedidos.travar(pedidoId)
                .filter(p -> p.getStatus() == StatusPedido.ATRASADO && p.getMatriculaId() != null)
                .filter(p -> matriculaPagamentoService.ativa(p.getMatriculaId()))
                .ifPresent(p -> {
                    matriculaPagamentoService.cancelar(p.getMatriculaId());
                    historico.save(new PedidoHistorico(
                            p.getId(),
                            StatusPedido.ATRASADO,
                            StatusPedido.ATRASADO,
                            null,
                            "Acesso suspenso pelo atraso da cobrança",
                            clock.instant()));
                });
    }

    /**
     * O pedido do evento: pelo nosso código (se a transação bate), senão pela transação já conhecida.
     * Vazio: venda sem pedido na plataforma.
     */
    private Optional<Pedido> localizar(Long gatewayId, EventoPagamento dados) {
        if (dados.codigoPedido() != null) {
            Optional<Pedido> peloCodigo = pedidos.travarPorCodigo(dados.codigoPedido())
                    .filter(p -> p.getGatewayId().equals(gatewayId))
                    .filter(p -> p.getTransacaoExterna() == null
                            || dados.transacao() == null
                            || p.getTransacaoExterna().equals(dados.transacao()));
            if (peloCodigo.isPresent()) {
                return peloCodigo;
            }
        }
        if (dados.transacao() != null) {
            return pedidos.travarPorTransacao(gatewayId, dados.transacao());
        }
        return Optional.empty();
    }

    private void aplicar(EfeitoMatricula efeito, Pedido pedido, EventoPagamento dados, Instant agora) {
        switch (efeito) {
            case CONCEDER -> conceder(pedido, dados, agora);
            case CANCELAR -> {
                if (pedido.getMatriculaId() != null) {
                    matriculaPagamentoService.cancelar(pedido.getMatriculaId());
                }
            }
            case NENHUM -> {
                // o status muda, a matrícula não
            }
        }
    }

    private void conceder(Pedido pedido, EventoPagamento dados, Instant agora) {
        Oferta oferta = ofertas.findById(pedido.getOfertaId()).orElseThrow();
        Usuario usuario = comprador(pedido, agora);
        Instant inicio = dados.ocorridoEm() == null || dados.ocorridoEm().isAfter(agora) ? agora : dados.ocorridoEm();
        Concessao concessao = matriculaPagamentoService.conceder(new NovaConcessao(
                usuario.getId(),
                oferta.getTurmaId(),
                inicio,
                oferta.getDiasAcesso(),
                pedido.getFormaPagamento() == null ? FormaPagamento.OUTRO : pedido.getFormaPagamento(),
                pedido.getTransacaoExterna(),
                pedido.getId(),
                pedido.getMatriculaId()));
        pedido.vincularMatricula(concessao.matriculaId(), agora);
        if (concessao.novoAcesso()) {
            enviarConfirmacao(usuario, oferta.getTurmaId());
        }
    }

    /** Dono do pedido; sem conta com o e-mail, cria uma sem senha (o link de criar senha vai no e-mail). */
    private Usuario comprador(Pedido pedido, Instant agora) {
        if (pedido.getUsuarioId() != null) {
            return usuarioService.buscarPorId(pedido.getUsuarioId()).orElseThrow();
        }
        Usuario usuario = usuarioService
                .buscarPorEmail(pedido.getEmail())
                .orElseGet(() -> usuarioService.criarAlunoSemSenha(nomeDoComprador(pedido), pedido.getEmail()));
        pedido.vincularUsuario(usuario.getId(), agora);
        return usuario;
    }

    private void enviarConfirmacao(Usuario usuario, Long turmaId) {
        String curso = conteudoTurmaService
                .turma(turmaId)
                .map(ConteudoTurmaService.TurmaResumo::cursoTitulo)
                .orElse("seu curso");
        Map<String, String> variaveis = new HashMap<>();
        variaveis.put("nome", usuario.getNome());
        variaveis.put("curso", curso);
        variaveis.put("linkCurso", conta.urlAluno() + "/turmas/" + turmaId);
        if (!usuario.possuiSenha()) {
            recuperacaoSenhaService.linkCriarSenha(usuario.getId()).ifPresent(link -> {
                variaveis.put("linkSenha", link.url());
                variaveis.put("validade", link.validade());
            });
        }
        emailService.enviar(usuario.getEmail(), "Sua matrícula foi confirmada", "matricula-confirmada", variaveis);
    }

    private static String nomeDoComprador(Pedido pedido) {
        if (pedido.getNome() != null) {
            return pedido.getNome();
        }
        String local = pedido.getEmail().substring(0, pedido.getEmail().indexOf('@'));
        return local.isBlank() ? "Aluno" : local;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/ProcessarEventoManipulador.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.application.RegistroEventos.PayloadEvento;
import br.com.celke.cursos.shared.fila.Job;
import br.com.celke.cursos.shared.fila.ManipuladorJob;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Job {@code pagamento.processar-evento}: aplica um evento gravado pelo webhook ou pela reconciliação. */
@Component
public class ProcessarEventoManipulador implements ManipuladorJob {

    private final ProcessamentoEventoService processamento;
    private final JsonMapper jsonMapper;

    public ProcessarEventoManipulador(ProcessamentoEventoService processamento, JsonMapper jsonMapper) {
        this.processamento = processamento;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String tipo() {
        return RegistroEventos.TIPO_JOB;
    }

    @Override
    public void processar(Job job) {
        processamento.processar(payload(job).eventoId());
    }

    @Override
    public void desistir(Job job, String motivo) {
        processamento.falhar(payload(job).eventoId(), motivo);
    }

    private PayloadEvento payload(Job job) {
        return jsonMapper.readValue(job.payload(), PayloadEvento.class);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/ReconciliacaoService.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.Capacidade;
import br.com.celke.cursos.pagamento.domain.ConfiguracaoGateway;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Reconciliação: consulta no meio de pagamento os pedidos que podem ter perdido uma notificação e,
 * se o status mudou, grava um evento sintético que segue o mesmo caminho do webhook. Pedidos ainda
 * pendentes depois de {@code pendenteApos} e pedidos pagos sem notificação há {@code semEventoApos}
 * são consultados, até {@code janela} sem mudança. Pedido sem transação (o aluno não chegou a
 * pagar) não tem o que consultar.
 */
@Service
public class ReconciliacaoService {

    static final int LIMITE_POR_CONSULTA = 200;
    private static final Logger LOG = LoggerFactory.getLogger(ReconciliacaoService.class);

    private final GatewayRepository gateways;
    private final PedidoRepository pedidos;
    private final GatewaysRegistrados registrados;
    private final ConfiguracaoGatewayService configuracoes;
    private final RegistroEventos registro;
    private final PagamentosProperties props;
    private final Clock clock;

    public ReconciliacaoService(
            GatewayRepository gateways,
            PedidoRepository pedidos,
            GatewaysRegistrados registrados,
            ConfiguracaoGatewayService configuracoes,
            RegistroEventos registro,
            PagamentosProperties props,
            Clock clock) {
        this.gateways = gateways;
        this.pedidos = pedidos;
        this.registrados = registrados;
        this.configuracoes = configuracoes;
        this.registro = registro;
        this.props = props;
        this.clock = clock;
    }

    /** Devolve quantos eventos sintéticos foram gravados. */
    public int reconciliar() {
        int gravados = 0;
        for (Gateway gateway : gateways.findAllByOrderById()) {
            GatewayPagamento implementacao = registrados.de(gateway.getTipo());
            if (!implementacao.capacidades().contains(Capacidade.CONSULTA_API)) {
                continue;
            }
            ConfiguracaoGateway configuracao;
            try {
                configuracao = configuracoes.ler(gateway);
            } catch (IllegalStateException e) {
                LOG.warn("Reconciliação do gateway {} ignorada: {}", gateway.getId(), e.getMessage());
                continue;
            }
            for (Pedido pedido : candidatos(gateway.getId())) {
                gravados += reconciliar(gateway, implementacao, configuracao, pedido) ? 1 : 0;
            }
        }
        return gravados;
    }

    private List<Pedido> candidatos(Long gatewayId) {
        Instant agora = clock.instant();
        Instant limiteJanela = agora.minus(props.reconciliacao().janela());
        PageRequest limite = PageRequest.of(0, LIMITE_POR_CONSULTA);
        List<Pedido> candidatos = new ArrayList<>(pedidos.paraReconciliar(
                gatewayId,
                EnumSet.of(StatusPedido.CRIADO, StatusPedido.AGUARDANDO_PAGAMENTO),
                agora.minus(props.reconciliacao().pendenteApos()),
                limiteJanela,
                limite));
        candidatos.addAll(pedidos.paraReconciliar(
                gatewayId,
                EnumSet.of(StatusPedido.APROVADO, StatusPedido.ATRASADO, StatusPedido.EM_DISPUTA),
                agora.minus(props.reconciliacao().semEventoApos()),
                limiteJanela,
                limite));
        return candidatos;
    }

    private boolean reconciliar(
            Gateway gateway, GatewayPagamento implementacao, ConfiguracaoGateway configuracao, Pedido pedido) {
        try {
            Optional<EventoPagamento> situacao = implementacao
                    .consultar(pedido.getTransacaoExterna(), configuracao)
                    .filter(e -> e.statusPedido() != null && e.statusPedido() != pedido.getStatus());
            if (situacao.isEmpty()) {
                return false;
            }
            LOG.info(
                    "Reconciliação: pedido {} está {} no meio de pagamento (aqui {})",
                    pedido.getCodigo(),
                    situacao.get().statusPedido(),
                    pedido.getStatus());
            return registro.registrar(gateway.getId(), situacao.get(), null, clock.instant())
                    .isPresent();
        } catch (RuntimeException e) {
            LOG.warn("Reconciliação do pedido {} falhou: {}", pedido.getCodigo(), e.toString());
            return false;
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/RegistroEventos.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import br.com.celke.cursos.pagamento.infra.EventosPagamentoJdbc;
import br.com.celke.cursos.pagamento.infra.EventosPagamentoJdbc.NovoEvento;
import br.com.celke.cursos.shared.fila.FilaService;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grava um evento normalizado e o coloca na fila, na mesma transação: ou os dois ficam, ou nenhum.
 * Mesmo id externo do mesmo gateway é gravado uma vez só (o reenvio vira "duplicado"). Usado pelo
 * webhook e pela reconciliação.
 */
@Component
public class RegistroEventos {

    public static final String TIPO_JOB = "pagamento.processar-evento";

    private final EventosPagamentoJdbc eventos;
    private final FilaService fila;
    private final JsonMapper jsonMapper;

    public RegistroEventos(EventosPagamentoJdbc eventos, FilaService fila, JsonMapper jsonMapper) {
        this.eventos = eventos;
        this.fila = fila;
        this.jsonMapper = jsonMapper;
    }

    /** Devolve o id do evento gravado; vazio se já existia. */
    @Transactional
    public Optional<Long> registrar(Long gatewayId, EventoPagamento evento, String payload, Instant agora) {
        Optional<Long> id = eventos.inserir(new NovoEvento(
                gatewayId,
                evento.idExterno(),
                evento.tipoOriginal(),
                agora,
                evento.ocorridoEm(),
                evento.transacao(),
                payload,
                jsonMapper.writeValueAsString(evento),
                StatusEvento.RECEBIDO.name(),
                null));
        id.ifPresent(eventoId -> enfileirar(eventoId, agora));
        return id;
    }

    /** Notificação autenticada que não foi interpretada: fica em ERRO, com o corpo cru, para o admin ver. */
    @Transactional
    public Optional<Long> registrarInvalido(
            Long gatewayId, String idExterno, String payload, String erro, Instant agora) {
        return eventos.inserir(new NovoEvento(
                gatewayId, idExterno, "INVALIDO", agora, null, null, payload, null, StatusEvento.ERRO.name(), erro));
    }

    public void enfileirar(Long eventoId, Instant agora) {
        fila.enfileirar(TIPO_JOB, new PayloadEvento(eventoId), agora);
    }

    /** Payload do job que processa um evento. */
    public record PayloadEvento(Long eventoId) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/TarefasPagamento.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.infra.EventosPagamentoJdbc;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarefas agendadas dos pagamentos, só no worker: reconciliação diária (se ligada), suspensão por
 * atraso (se configurada) e retenção semanal do corpo dos eventos.
 */
@Component
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
public class TarefasPagamento {

    private static final Logger LOG = LoggerFactory.getLogger(TarefasPagamento.class);

    private final ReconciliacaoService reconciliacao;
    private final ProcessamentoEventoService processamento;
    private final PedidoRepository pedidos;
    private final EventosPagamentoJdbc eventos;
    private final PagamentosProperties props;
    private final Clock clock;

    public TarefasPagamento(
            ReconciliacaoService reconciliacao,
            ProcessamentoEventoService processamento,
            PedidoRepository pedidos,
            EventosPagamentoJdbc eventos,
            PagamentosProperties props,
            Clock clock) {
        this.reconciliacao = reconciliacao;
        this.processamento = processamento;
        this.pedidos = pedidos;
        this.eventos = eventos;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 5 * * *", zone = "America/Sao_Paulo")
    public void reconciliar() {
        if (props.reconciliacao().ativa()) {
            LOG.info("Reconciliação de pagamentos: {} evento(s) gravado(s)", reconciliacao.reconciliar());
        }
    }

    @Scheduled(cron = "0 15 5 * * *", zone = "America/Sao_Paulo")
    public void suspenderAtrasados() {
        if (props.atrasoSuspendeApos() == null) {
            return;
        }
        for (Pedido pedido : pedidos.findByStatusAndAtualizadoEmBeforeAndMatriculaIdNotNull(
                StatusPedido.ATRASADO, clock.instant().minus(props.atrasoSuspendeApos()))) {
            processamento.suspenderPorAtraso(pedido.getId());
        }
    }

    @Scheduled(cron = "0 45 4 * * SUN", zone = "America/Sao_Paulo")
    public void aplicarRetencao() {
        int apagados = eventos.apagarPayloads(clock.instant().minus(props.retencaoPayload()));
        LOG.info("Retenção dos eventos de pagamento: {} corpo(s) apagado(s)", apagados);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/application/WebhookService.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import br.com.celke.cursos.auth.application.TokenSeguro;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.NotificacaoInvalidaException;
import br.com.celke.cursos.pagamento.domain.NotificacaoRecebida;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recebe a notificação de um meio de pagamento: identifica o gateway pelo token da URL, autentica,
 * interpreta, grava e enfileira, respondendo na hora. O processamento fica com o worker. Só o que não
 * pôde ser gravado vira erro para o meio de pagamento: ele reenvia (e desativa a URL se continuar
 * falhando), então evento válido que não se aplica é gravado e respondido com sucesso.
 */
@Service
public class WebhookService {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookService.class);

    private final GatewayRepository gateways;
    private final GatewaysRegistrados registrados;
    private final ConfiguracaoGatewayService configuracoes;
    private final RegistroEventos registro;
    private final AvisosPagamento avisos;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public WebhookService(
            GatewayRepository gateways,
            GatewaysRegistrados registrados,
            ConfiguracaoGatewayService configuracoes,
            RegistroEventos registro,
            AvisosPagamento avisos,
            JsonMapper jsonMapper,
            Clock clock) {
        this.gateways = gateways;
        this.registrados = registrados;
        this.configuracoes = configuracoes;
        this.registro = registro;
        this.avisos = avisos;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public ResultadoWebhook receber(String webhookToken, NotificacaoRecebida notificacao) {
        Gateway gateway = gateways.findByWebhookToken(webhookToken)
                .orElseThrow(() -> new NaoEncontradoException("Endereço de notificação desconhecido."));
        GatewayPagamento implementacao = registrados.de(gateway.getTipo());
        if (!implementacao.autenticar(notificacao, configuracoes.ler(gateway))) {
            LOG.warn("Notificação recusada para o gateway {}: autenticação inválida", gateway.getId());
            throw new RegraNegocioException(HttpStatus.UNAUTHORIZED, "Notificação não autenticada.");
        }
        Instant agora = clock.instant();
        String payload = payloadMascarado(notificacao.corpoTexto());
        List<EventoPagamento> eventos;
        try {
            eventos = implementacao.interpretar(notificacao);
        } catch (NotificacaoInvalidaException e) {
            String idExterno = "invalido:" + TokenSeguro.hash(notificacao.corpoTexto());
            Optional<Long> id = registro.registrarInvalido(gateway.getId(), idExterno, payload, e.getMessage(), agora);
            id.ifPresent(eventoId -> avisos.eventoComErro(eventoId, "INVALIDO", e.getMessage()));
            return id.isPresent() ? ResultadoWebhook.ERRO : ResultadoWebhook.DUPLICADO;
        }
        boolean algumNovo = false;
        for (EventoPagamento evento : eventos) {
            algumNovo |=
                    registro.registrar(gateway.getId(), evento, payload, agora).isPresent();
        }
        return algumNovo ? ResultadoWebhook.RECEBIDO : ResultadoWebhook.DUPLICADO;
    }

    /** Corpo em JSON com os dados pessoais mascarados; corpo que não é JSON vai como texto (string JSON). */
    String payloadMascarado(String corpo) {
        try {
            JsonNode arvore = jsonMapper.readTree(corpo);
            if (arvore != null && (arvore.isObject() || arvore.isArray())) {
                return jsonMapper.writeValueAsString(MascaraDadosPessoais.mascarar(arvore));
            }
        } catch (JacksonException e) {
            // não é JSON: guarda como texto
        }
        return jsonMapper.writeValueAsString(corpo);
    }

    public enum ResultadoWebhook {
        /** Gravado e na fila. */
        RECEBIDO,
        /** Já tinha chegado (reenvio): nada muda. */
        DUPLICADO,
        /** Gravado como erro (corpo não interpretado): reenviar não resolve. */
        ERRO
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/CampoConfiguracao.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import java.util.List;

/**
 * Campo da configuração de um meio de pagamento. O painel monta o formulário a partir da lista que o
 * gateway declara; o campo secreto é gravado cifrado e nunca volta nas respostas.
 *
 * @param padrao valor usado quando o campo vem em branco (nulo: sem padrão)
 * @param opcoes valores aceitos num campo {@link TipoCampo#SELECAO}; vazia nos outros
 */
public record CampoConfiguracao(
        String chave,
        String rotulo,
        TipoCampo tipo,
        boolean secreto,
        boolean obrigatorio,
        String dica,
        String padrao,
        List<String> opcoes) {

    public CampoConfiguracao {
        opcoes = opcoes == null ? List.of() : List.copyOf(opcoes);
    }

    public static CampoConfiguracao texto(String chave, String rotulo, boolean obrigatorio, String dica) {
        return new CampoConfiguracao(chave, rotulo, TipoCampo.TEXTO, false, obrigatorio, dica, null, List.of());
    }

    public static CampoConfiguracao segredo(String chave, String rotulo, boolean obrigatorio, String dica) {
        return new CampoConfiguracao(chave, rotulo, TipoCampo.TEXTO, true, obrigatorio, dica, null, List.of());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/Capacidade.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

/** O que um meio de pagamento sabe fazer. */
public enum Capacidade {
    /** Checkout numa página do meio de pagamento (o aluno é redirecionado). */
    CHECKOUT_REDIRECIONAMENTO,
    /** Checkout num formulário dentro da plataforma. */
    CHECKOUT_EMBUTIDO,
    /** Consulta da situação de uma transação pela API (reconciliação). */
    CONSULTA_API,
    /** Reembolso pedido pela API. */
    REEMBOLSO_API,
    /** Assinaturas com cobrança recorrente. */
    RECORRENCIA
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/Checkout.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import java.util.Map;

/** Como o aluno paga: indo para a página do meio de pagamento ou num formulário embutido. */
public sealed interface Checkout {

    record Redirecionamento(String url) implements Checkout {}

    /** Dados que o front entrega ao formulário/SDK do meio de pagamento. */
    record Embutido(Map<String, Object> dados) implements Checkout {

        public Embutido {
            dados = Map.copyOf(dados);
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/Comprador.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

/** Quem está comprando, para preencher o checkout. */
public record Comprador(String email, String nome) {}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/ConfiguracaoGateway.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import java.util.Map;

/**
 * Configuração de um gateway já decifrada, entregue à implementação dele: campos comuns e secretos
 * juntos, pela chave do {@link CampoConfiguracao}.
 */
public record ConfiguracaoGateway(Long gatewayId, TipoGateway tipo, Map<String, String> valores) {

    public ConfiguracaoGateway {
        valores = Map.copyOf(valores);
    }

    /** Valor do campo; nulo se não preenchido. */
    public String valor(String chave) {
        String valor = valores.get(chave);
        return valor == null || valor.isBlank() ? null : valor;
    }

    /** Valor do campo ou o padrão informado. */
    public String valor(String chave, String padrao) {
        String valor = valor(chave);
        return valor == null ? padrao : valor;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/EventoPagamento.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import java.time.Instant;

/**
 * Notificação traduzida para o formato da plataforma, independente do meio de pagamento.
 *
 * @param idExterno id único do evento no meio de pagamento (a idempotência)
 * @param tipoOriginal nome do evento no meio de pagamento (ex.: {@code PURCHASE_APPROVED})
 * @param statusPedido status interno correspondente; nulo para evento que não muda o pedido
 * @param codigoPedido código do nosso pedido, quando o checkout o levou (Hotmart: {@code xcod})
 * @param valorCentavos valor pago em centavos; nulo quando o meio não informa
 * @param formaPagamento forma de pagamento; nula quando o meio não informa
 * @param assinatura dados da assinatura, quando a venda é recorrente
 */
public record EventoPagamento(
        String idExterno,
        String tipoOriginal,
        Instant ocorridoEm,
        String transacao,
        StatusPedido statusPedido,
        String codigoPedido,
        String produtoExterno,
        String ofertaExterna,
        CompradorEvento comprador,
        Long valorCentavos,
        String moeda,
        FormaPagamento formaPagamento,
        Assinatura assinatura) {

    /** Comprador como veio na notificação; o documento só mascarado. */
    public record CompradorEvento(String email, String nome, String documentoMascarado) {}

    public record Assinatura(String codigo, String status) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/EventoRecebido.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Notificação gravada (tabela {@code eventos_pagamento}). A inclusão é feita com
 * {@code INSERT ... ON CONFLICT DO NOTHING} pelo repositório (idempotência); o corpo e o evento
 * normalizado só são escritos ali e apagados pela retenção.
 */
@Entity
@Table(name = "eventos_pagamento")
public class EventoRecebido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gateway_id", nullable = false, updatable = false)
    private Long gatewayId;

    @Column(name = "id_externo", nullable = false, length = 150, updatable = false)
    private String idExterno;

    @Column(name = "tipo_original", nullable = false, length = 60, updatable = false)
    private String tipoOriginal;

    @Column(name = "recebido_em", nullable = false, updatable = false)
    private Instant recebidoEm;

    @Column(name = "ocorrido_em", updatable = false)
    private Instant ocorridoEm;

    @Column(length = 100, updatable = false)
    private String transacao;

    /** Corpo recebido (mascarado), em JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(insertable = false, updatable = false)
    private String payload;

    /** {@link EventoPagamento} em JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(insertable = false, updatable = false)
    private String dados;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEvento status;

    private String erro;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @Column(name = "processado_em")
    private Instant processadoEm;

    protected EventoRecebido() {}

    public void processado(Long pedidoId, Instant agora) {
        this.status = StatusEvento.PROCESSADO;
        this.pedidoId = pedidoId;
        this.erro = null;
        this.processadoEm = agora;
    }

    public void ignorado(Long pedidoId, String motivo, Instant agora) {
        this.status = StatusEvento.IGNORADO;
        this.pedidoId = pedidoId;
        this.erro = motivo;
        this.processadoEm = agora;
    }

    public void falhou(String motivo, Instant agora) {
        this.status = StatusEvento.ERRO;
        this.erro = motivo;
        this.processadoEm = agora;
    }

    /** Volta para a fila (reprocessamento pelo painel). */
    public void reabrir() {
        this.status = StatusEvento.RECEBIDO;
        this.erro = null;
        this.processadoEm = null;
    }

    public Long getId() {
        return id;
    }

    public Long getGatewayId() {
        return gatewayId;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public String getTipoOriginal() {
        return tipoOriginal;
    }

    public Instant getRecebidoEm() {
        return recebidoEm;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    public String getTransacao() {
        return transacao;
    }

    public String getPayload() {
        return payload;
    }

    public String getDados() {
        return dados;
    }

    public StatusEvento getStatus() {
        return status;
    }

    public String getErro() {
        return erro;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public Instant getProcessadoEm() {
        return processadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/Gateway.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Meio de pagamento configurado pelo painel. Os campos comuns ficam em {@code configuracao} (JSON);
 * os secretos, cifrados em {@code segredos} (quem cifra e decifra é a aplicação).
 */
@Entity
@Table(name = "gateways_pagamento")
public class Gateway {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TipoGateway tipo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private boolean ativo;

    /** JSON com os campos não secretos. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String configuracao;

    /** IV + texto cifrado (AES-256-GCM) do JSON com os campos secretos; nulo sem segredos. */
    private byte[] segredos;

    @Column(name = "webhook_token", nullable = false, length = 64, updatable = false)
    private String webhookToken;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Gateway() {}

    /** Gateway novo nasce inativo: o admin ativa depois de testar. */
    public Gateway(TipoGateway tipo, String nome, String webhookToken, Instant agora) {
        this.tipo = tipo;
        this.nome = nome.strip();
        this.webhookToken = webhookToken;
        this.configuracao = "{}";
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void configurar(String nome, String configuracao, byte[] segredos, Instant agora) {
        this.nome = nome.strip();
        this.configuracao = configuracao;
        this.segredos = segredos == null ? null : segredos.clone();
        this.atualizadoEm = agora;
    }

    public void ativar(Instant agora) {
        this.ativo = true;
        this.atualizadoEm = agora;
    }

    public void desativar(Instant agora) {
        this.ativo = false;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public TipoGateway getTipo() {
        return tipo;
    }

    public String getNome() {
        return nome;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public String getConfiguracao() {
        return configuracao;
    }

    public byte[] getSegredos() {
        return segredos == null ? null : segredos.clone();
    }

    public String getWebhookToken() {
        return webhookToken;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/GatewayPagamento.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Um meio de pagamento. Cada um é um bean; o núcleo não conhece nenhum pelo nome. */
public interface GatewayPagamento {

    TipoGateway tipo();

    /** Nome mostrado no painel (ex.: "Hotmart"). */
    String nome();

    /** Campos que o painel mostra para configurar (o front monta o formulário a partir daqui). */
    List<CampoConfiguracao> camposConfiguracao();

    /** O que este meio de pagamento sabe fazer. */
    Set<Capacidade> capacidades();

    /** Confere as credenciais com o meio de pagamento (botão "Testar" do painel). */
    ResultadoTeste testar(ConfiguracaoGateway configuracao);

    /** Monta o checkout do pedido: URL para redirecionar ou dados para um formulário embutido. */
    Checkout iniciarCheckout(Pedido pedido, Oferta oferta, Comprador comprador, ConfiguracaoGateway configuracao);

    /** Autentica a notificação recebida (cabeçalhos e corpo crus). */
    boolean autenticar(NotificacaoRecebida notificacao, ConfiguracaoGateway configuracao);

    /**
     * Traduz a notificação para eventos normalizados (uma notificação pode gerar mais de um).
     *
     * @throws NotificacaoInvalidaException se o corpo não é do formato esperado
     */
    List<EventoPagamento> interpretar(NotificacaoRecebida notificacao);

    /** Situação atual da transação no meio de pagamento (reconciliação), se tiver CONSULTA_API. */
    Optional<EventoPagamento> consultar(String transacao, ConfiguracaoGateway configuracao);
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/NotificacaoInvalidaException.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

/** Notificação autenticada que não pôde ser interpretada (JSON inválido, sem id, sem evento). */
public class NotificacaoInvalidaException extends RuntimeException {

    public NotificacaoInvalidaException(String mensagem) {
        super(mensagem);
    }

    public NotificacaoInvalidaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/NotificacaoRecebida.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Requisição crua de uma notificação (webhook): cabeçalhos com o nome em minúsculas e o corpo como
 * chegou. Cada meio de pagamento autentica e interpreta do seu jeito.
 */
public record NotificacaoRecebida(Map<String, String> cabecalhos, byte[] corpo) {

    public NotificacaoRecebida {
        cabecalhos = cabecalhos.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
        corpo = corpo.clone();
    }

    public String cabecalho(String nome) {
        return cabecalhos.get(nome.toLowerCase(Locale.ROOT));
    }

    public String corpoTexto() {
        return new String(corpo, StandardCharsets.UTF_8);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/Oferta.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;

/**
 * O que se vende: acesso a uma turma por um produto/oferta de um meio de pagamento. A turma é só o id
 * (os dados vêm do módulo {@code curso}).
 */
@Entity
@Table(name = "ofertas")
public class Oferta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turma_id", nullable = false, updatable = false)
    private Long turmaId;

    @Column(name = "gateway_id", nullable = false, updatable = false)
    private Long gatewayId;

    @Column(name = "produto_externo", nullable = false, length = 100)
    private String produtoExterno;

    @Column(name = "oferta_externa", nullable = false, length = 100)
    private String ofertaExterna;

    /** Código do produto no link do checkout, quando diferente do id do produto. */
    @Column(name = "codigo_checkout", length = 100)
    private String codigoCheckout;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(name = "preco_centavos", nullable = false)
    private long precoCentavos;

    @Column(nullable = false, length = 3)
    private String moeda;

    /** Nulo: acesso sem prazo. */
    @Column(name = "dias_acesso")
    private Integer diasAcesso;

    @Column(nullable = false)
    private boolean ativa;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Oferta() {}

    public Oferta(Long turmaId, Long gatewayId, DadosOferta dados, Instant agora) {
        this.turmaId = turmaId;
        this.gatewayId = gatewayId;
        this.ativa = true;
        this.criadoEm = agora;
        atualizar(dados, agora);
    }

    public void atualizar(DadosOferta dados, Instant agora) {
        this.produtoExterno = dados.produtoExterno().strip();
        this.ofertaExterna = dados.ofertaExterna().strip();
        this.codigoCheckout =
                dados.codigoCheckout() == null || dados.codigoCheckout().isBlank()
                        ? null
                        : dados.codigoCheckout().strip();
        this.titulo = dados.titulo().strip();
        this.precoCentavos = dados.precoCentavos();
        this.moeda = dados.moeda().strip().toUpperCase(Locale.ROOT);
        this.diasAcesso = dados.diasAcesso();
        this.atualizadoEm = agora;
    }

    public void alterarAtiva(boolean ativa, Instant agora) {
        this.ativa = ativa;
        this.atualizadoEm = agora;
    }

    /** Código do produto para o link do checkout (o próprio id do produto, se não informado). */
    public String codigoParaCheckout() {
        return codigoCheckout == null ? produtoExterno : codigoCheckout;
    }

    public Long getId() {
        return id;
    }

    public Long getTurmaId() {
        return turmaId;
    }

    public Long getGatewayId() {
        return gatewayId;
    }

    public String getProdutoExterno() {
        return produtoExterno;
    }

    public String getOfertaExterna() {
        return ofertaExterna;
    }

    public String getCodigoCheckout() {
        return codigoCheckout;
    }

    public String getTitulo() {
        return titulo;
    }

    public long getPrecoCentavos() {
        return precoCentavos;
    }

    public String getMoeda() {
        return moeda;
    }

    public Integer getDiasAcesso() {
        return diasAcesso;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    /** Campos editáveis da oferta. */
    public record DadosOferta(
            String produtoExterno,
            String ofertaExterna,
            String codigoCheckout,
            String titulo,
            long precoCentavos,
            String moeda,
            Integer diasAcesso) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/OrigemPedido.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

public enum OrigemPedido {
    /** Nasceu no botão "Comprar" da plataforma, com o código do pedido no checkout. */
    CHECKOUT,
    /** Venda que chegou sem o código do pedido (afiliado, link direto do meio de pagamento). */
    EXTERNO
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/Pedido.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

/**
 * Compra de uma oferta. O {@code codigo} é público: vai no checkout (Hotmart: {@code xcod}) e volta
 * na notificação e na página de retorno. Status só muda por {@link #mudarStatus}, depois da decisão
 * da {@link TransicoesPedido}.
 */
@Entity
@Table(name = "pedidos")
public class Pedido {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 22, updatable = false)
    private String codigo;

    @Column(name = "oferta_id", nullable = false, updatable = false)
    private Long ofertaId;

    @Column(name = "gateway_id", nullable = false, updatable = false)
    private Long gatewayId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPedido status;

    @Column(name = "transacao_externa", length = 100)
    private String transacaoExterna;

    @Column(name = "valor_centavos")
    private Long valorCentavos;

    @Column(length = 3)
    private String moeda;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 20)
    private FormaPagamento formaPagamento;

    @Column(name = "matricula_id")
    private Long matriculaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private OrigemPedido origem;

    @Column(name = "ultimo_evento_em")
    private Instant ultimoEventoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Pedido() {}

    private Pedido(Oferta oferta, Long usuarioId, String email, String nome, OrigemPedido origem, Instant agora) {
        this.codigo = novoCodigo();
        this.ofertaId = oferta.getId();
        this.gatewayId = oferta.getGatewayId();
        this.usuarioId = usuarioId;
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.nome = nome == null || nome.isBlank() ? null : truncar(nome.strip(), 120);
        this.status = StatusPedido.CRIADO;
        this.valorCentavos = oferta.getPrecoCentavos();
        this.moeda = oferta.getMoeda();
        this.origem = origem;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Pedido do botão "Comprar", do aluno logado. */
    public static Pedido doCheckout(Oferta oferta, Long usuarioId, String email, String nome, Instant agora) {
        return new Pedido(oferta, usuarioId, email, nome, OrigemPedido.CHECKOUT, agora);
    }

    /** Venda que chegou pelo meio de pagamento sem o nosso código (afiliado, link direto). */
    public static Pedido externo(Oferta oferta, String email, String nome, String transacao, Instant agora) {
        Pedido pedido = new Pedido(oferta, null, email, nome, OrigemPedido.EXTERNO, agora);
        pedido.transacaoExterna = transacao;
        return pedido;
    }

    /** 16 bytes aleatórios em Base64 URL: 22 caracteres, impossível de adivinhar. */
    static String novoCodigo() {
        byte[] bytes = new byte[16];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Liga a transação do meio de pagamento ao pedido (primeira notificação). */
    public void vincularTransacao(String transacao, Instant agora) {
        if (transacaoExterna == null && transacao != null) {
            this.transacaoExterna = transacao;
            this.atualizadoEm = agora;
        }
    }

    /** Valor, moeda e forma como o meio de pagamento informou (o preço da oferta pode ter mudado ou ter cupom). */
    public void registrarPagamento(Long valorCentavos, String moeda, FormaPagamento forma, Instant agora) {
        if (valorCentavos != null) {
            this.valorCentavos = valorCentavos;
            this.moeda = moeda;
        }
        if (forma != null) {
            this.formaPagamento = forma;
        }
        this.atualizadoEm = agora;
    }

    /** Aplica uma mudança já decidida pela {@link TransicoesPedido}. */
    public void mudarStatus(StatusPedido novo, Instant ocorridoEm, Instant agora) {
        this.status = novo;
        if (ocorridoEm != null && (ultimoEventoEm == null || ocorridoEm.isAfter(ultimoEventoEm))) {
            this.ultimoEventoEm = ocorridoEm;
        }
        this.atualizadoEm = agora;
    }

    public void vincularUsuario(Long usuarioId, Instant agora) {
        this.usuarioId = usuarioId;
        this.atualizadoEm = agora;
    }

    public void vincularMatricula(Long matriculaId, Instant agora) {
        this.matriculaId = matriculaId;
        this.atualizadoEm = agora;
    }

    /** O usuário é o dono do pedido (a página de retorno mostra o curso só para ele). */
    public boolean pertenceA(Long usuarioId) {
        return this.usuarioId != null && this.usuarioId.equals(usuarioId);
    }

    private static String truncar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public Long getOfertaId() {
        return ofertaId;
    }

    public Long getGatewayId() {
        return gatewayId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getEmail() {
        return email;
    }

    public String getNome() {
        return nome;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public String getTransacaoExterna() {
        return transacaoExterna;
    }

    public Long getValorCentavos() {
        return valorCentavos;
    }

    public String getMoeda() {
        return moeda;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public Long getMatriculaId() {
        return matriculaId;
    }

    public OrigemPedido getOrigem() {
        return origem;
    }

    public Instant getUltimoEventoEm() {
        return ultimoEventoEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/PedidoHistorico.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Uma linha da linha do tempo do pedido (só inclusão). */
@Entity
@Table(name = "pedido_historico")
public class PedidoHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false, updatable = false)
    private Long pedidoId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, updatable = false)
    private StatusPedido de;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private StatusPedido para;

    @Column(name = "evento_id", updatable = false)
    private Long eventoId;

    @Column(length = 300, updatable = false)
    private String motivo;

    @Column(nullable = false, updatable = false)
    private Instant em;

    protected PedidoHistorico() {}

    public PedidoHistorico(
            Long pedidoId, StatusPedido de, StatusPedido para, Long eventoId, String motivo, Instant em) {
        this.pedidoId = pedidoId;
        this.de = de;
        this.para = para;
        this.eventoId = eventoId;
        this.motivo = motivo == null || motivo.length() <= 300 ? motivo : motivo.substring(0, 300);
        this.em = em;
    }

    public Long getId() {
        return id;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public StatusPedido getDe() {
        return de;
    }

    public StatusPedido getPara() {
        return para;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getEm() {
        return em;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/ResultadoTeste.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

/** Resultado do botão "Testar credenciais" do painel. */
public record ResultadoTeste(boolean ok, String mensagem) {

    public static ResultadoTeste ok(String mensagem) {
        return new ResultadoTeste(true, mensagem);
    }

    public static ResultadoTeste falha(String mensagem) {
        return new ResultadoTeste(false, mensagem);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/StatusEvento.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

/** Situação de uma notificação recebida. */
public enum StatusEvento {
    /** Gravada e na fila para processar. */
    RECEBIDO,
    PROCESSADO,
    /** Válida, mas não se aplica (oferta não cadastrada, evento não tratado, evento antigo). */
    IGNORADO,
    /** Não pôde ser interpretada ou o processamento desistiu. */
    ERRO
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/StatusPedido.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

/** Status do pedido, igual para todos os meios de pagamento (cada gateway traduz os seus). */
public enum StatusPedido {
    /** Pedido aberto; o aluno ainda não pagou. */
    CRIADO,
    /** Boleto ou PIX gerado, pagamento em análise. */
    AGUARDANDO_PAGAMENTO,
    APROVADO,
    /** Fim da garantia: a venda não pode mais ser reembolsada pelo aluno. */
    CONCLUIDO,
    /** Pedido de reembolso ou disputa aberta. */
    EM_DISPUTA,
    /** Cobrança atrasada (em geral, renovação de assinatura). */
    ATRASADO,
    REEMBOLSADO,
    CHARGEBACK,
    CANCELADO,
    /** Boleto ou PIX vencido sem pagamento. */
    EXPIRADO
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/TipoCampo.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

/** Como o painel mostra um campo da configuração. */
public enum TipoCampo {
    TEXTO,
    URL,
    /** Uma das {@code opcoes} do campo. */
    SELECAO
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/TipoGateway.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

/**
 * Meios de pagamento que a plataforma sabe usar. Um meio novo entra com um valor aqui e um bean de
 * {@link GatewayPagamento}; o núcleo não muda.
 */
public enum TipoGateway {
    HOTMART
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/domain/TransicoesPedido.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import static br.com.celke.cursos.pagamento.domain.StatusPedido.AGUARDANDO_PAGAMENTO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.APROVADO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.ATRASADO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.CANCELADO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.CHARGEBACK;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.CONCLUIDO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.CRIADO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.EM_DISPUTA;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.EXPIRADO;
import static br.com.celke.cursos.pagamento.domain.StatusPedido.REEMBOLSADO;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Todas as mudanças de status do pedido e o efeito de cada uma na matrícula. Os meios de pagamento
 * mandam eventos fora de ordem e reenviam antigos, por isso:
 *
 * <ul>
 *   <li>só as transições da tabela {@link #ORIGENS} valem (ex.: {@code APROVADO} depois de
 *       {@code REEMBOLSADO} não reabre o acesso);
 *   <li>um evento mais antigo que o da última mudança é ignorado, exceto reembolso e chargeback, que
 *       sempre valem.
 * </ul>
 */
public final class TransicoesPedido {

    /** Para cada status de destino, de quais status o pedido pode chegar nele. */
    static final Map<StatusPedido, Set<StatusPedido>> ORIGENS = new EnumMap<>(StatusPedido.class);

    /** Status que valem mesmo chegando fora de ordem. */
    static final Set<StatusPedido> SEMPRE_VALEM = EnumSet.of(REEMBOLSADO, CHARGEBACK);

    static {
        ORIGENS.put(CRIADO, EnumSet.noneOf(StatusPedido.class));
        ORIGENS.put(AGUARDANDO_PAGAMENTO, EnumSet.of(CRIADO));
        ORIGENS.put(APROVADO, EnumSet.of(CRIADO, AGUARDANDO_PAGAMENTO, EXPIRADO, ATRASADO, EM_DISPUTA));
        ORIGENS.put(CONCLUIDO, EnumSet.of(CRIADO, AGUARDANDO_PAGAMENTO, APROVADO, ATRASADO, EM_DISPUTA));
        ORIGENS.put(EM_DISPUTA, EnumSet.of(APROVADO, CONCLUIDO, ATRASADO));
        ORIGENS.put(ATRASADO, EnumSet.of(APROVADO, CONCLUIDO));
        ORIGENS.put(
                REEMBOLSADO,
                EnumSet.of(
                        CRIADO, AGUARDANDO_PAGAMENTO, APROVADO, CONCLUIDO, EM_DISPUTA, ATRASADO, CANCELADO, EXPIRADO));
        ORIGENS.put(
                CHARGEBACK,
                EnumSet.of(
                        CRIADO,
                        AGUARDANDO_PAGAMENTO,
                        APROVADO,
                        CONCLUIDO,
                        EM_DISPUTA,
                        ATRASADO,
                        REEMBOLSADO,
                        CANCELADO,
                        EXPIRADO));
        ORIGENS.put(CANCELADO, EnumSet.of(CRIADO, AGUARDANDO_PAGAMENTO, APROVADO, CONCLUIDO, EM_DISPUTA, ATRASADO));
        ORIGENS.put(EXPIRADO, EnumSet.of(CRIADO, AGUARDANDO_PAGAMENTO));
    }

    private final boolean disputaSuspende;

    /**
     * @param disputaSuspende pedido em disputa tira o acesso até a disputa terminar (padrão: mantém)
     */
    public TransicoesPedido(boolean disputaSuspende) {
        this.disputaSuspende = disputaSuspende;
    }

    /**
     * Decide se o evento muda o pedido.
     *
     * @param ultimoEventoEm quando ocorreu o evento da última mudança (nulo se nenhuma)
     */
    public Decisao decidir(StatusPedido atual, StatusPedido novo, Instant ultimoEventoEm, Instant ocorridoEm) {
        if (atual == novo) {
            return Decisao.ignorar("O pedido já está " + atual + ".");
        }
        if (!ORIGENS.get(novo).contains(atual)) {
            return Decisao.ignorar("Mudança de " + atual + " para " + novo + " não é permitida.");
        }
        if (!SEMPRE_VALEM.contains(novo)
                && ultimoEventoEm != null
                && ocorridoEm != null
                && ocorridoEm.isBefore(ultimoEventoEm)) {
            return Decisao.ignorar("Evento anterior à última mudança do pedido.");
        }
        return Decisao.aplicar(efeito(novo));
    }

    /** O que a mudança para o status faz com a matrícula. */
    public EfeitoMatricula efeito(StatusPedido novo) {
        return switch (novo) {
            // CONCLUIDO também concede: a aprovação pode ter se perdido (conceder é idempotente)
            case APROVADO, CONCLUIDO -> EfeitoMatricula.CONCEDER;
            case REEMBOLSADO, CHARGEBACK, CANCELADO -> EfeitoMatricula.CANCELAR;
            case EM_DISPUTA -> disputaSuspende ? EfeitoMatricula.CANCELAR : EfeitoMatricula.NENHUM;
            case CRIADO, AGUARDANDO_PAGAMENTO, ATRASADO, EXPIRADO -> EfeitoMatricula.NENHUM;
        };
    }

    /** A matrícula ligada ao pedido: abrir (ou reabrir/estender), cancelar ou não mexer. */
    public enum EfeitoMatricula {
        CONCEDER,
        CANCELAR,
        NENHUM
    }

    /**
     * @param motivo por que o evento foi ignorado (nulo quando aplica)
     */
    public record Decisao(boolean aplica, EfeitoMatricula efeito, String motivo) {

        static Decisao aplicar(EfeitoMatricula efeito) {
            return new Decisao(true, efeito, null);
        }

        static Decisao ignorar(String motivo) {
            return new Decisao(false, EfeitoMatricula.NENHUM, motivo);
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/infra/EventoRecebidoRepository.java`:

`````java
package br.com.celke.cursos.pagamento.infra;

import br.com.celke.cursos.pagamento.domain.EventoRecebido;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EventoRecebidoRepository extends JpaRepository<EventoRecebido, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventoRecebido e where e.id = :id")
    Optional<EventoRecebido> travar(Long id);

    List<EventoRecebido> findByPedidoIdOrderById(Long pedidoId);

    Page<EventoRecebido> findByStatus(StatusEvento status, Pageable pageable);
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/infra/EventosPagamentoJdbc.java`:

`````java
package br.com.celke.cursos.pagamento.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * O que a tabela {@code eventos_pagamento} faz fora do JPA: a inclusão idempotente
 * ({@code ON CONFLICT DO NOTHING} no único de gateway + id externo) e a retenção do corpo.
 */
@Repository
public class EventosPagamentoJdbc {

    private final JdbcClient jdbc;

    public EventosPagamentoJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Grava o evento; vazio se o mesmo id externo já tinha chegado para o gateway (duplicado). */
    public Optional<Long> inserir(NovoEvento evento) {
        return jdbc.sql("""
                        INSERT INTO eventos_pagamento
                               (gateway_id, id_externo, tipo_original, recebido_em, ocorrido_em, transacao,
                                payload, dados, status, erro)
                        VALUES (:gateway, :idExterno, :tipo, :recebidoEm, :ocorridoEm, :transacao,
                                CAST(:payload AS JSONB), CAST(:dados AS JSONB), :status, :erro)
                        ON CONFLICT (gateway_id, id_externo) DO NOTHING
                        RETURNING id
                        """)
                .param("gateway", evento.gatewayId())
                .param("idExterno", evento.idExterno())
                .param("tipo", evento.tipoOriginal())
                .param("recebidoEm", utc(evento.recebidoEm()))
                .param("ocorridoEm", evento.ocorridoEm() == null ? null : utc(evento.ocorridoEm()))
                .param("transacao", evento.transacao())
                .param("payload", evento.payload())
                .param("dados", evento.dados())
                .param("status", evento.status())
                .param("erro", evento.erro())
                .query(Long.class)
                .optional();
    }

    /** Apaga o corpo e o evento normalizado dos processados ou ignorados recebidos antes do instante. */
    public int apagarPayloads(Instant recebidosAntesDe) {
        return jdbc.sql("""
                        UPDATE eventos_pagamento SET payload = NULL, dados = NULL
                         WHERE status IN ('PROCESSADO', 'IGNORADO') AND recebido_em < :antes
                           AND (payload IS NOT NULL OR dados IS NOT NULL)
                        """).param("antes", utc(recebidosAntesDe)).update();
    }

    /**
     * Evento a gravar.
     *
     * @param payload corpo em JSON (mascarado)
     * @param dados evento normalizado em JSON; nulo quando não interpretado
     */
    public record NovoEvento(
            Long gatewayId,
            String idExterno,
            String tipoOriginal,
            Instant recebidoEm,
            Instant ocorridoEm,
            String transacao,
            String payload,
            String dados,
            String status,
            String erro) {}

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/infra/GatewayRepository.java`:

`````java
package br.com.celke.cursos.pagamento.infra;

import br.com.celke.cursos.pagamento.domain.Gateway;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GatewayRepository extends JpaRepository<Gateway, Long> {

    Optional<Gateway> findByWebhookToken(String webhookToken);

    /** O gateway dos novos checkouts; o banco garante no máximo um. */
    Optional<Gateway> findByAtivoTrue();

    List<Gateway> findAllByOrderById();

    /** Desativa os outros antes de ativar um (o índice único parcial não aceita dois ativos nem por um instante). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Gateway g set g.ativo = false where g.ativo = true and g.id <> :id")
    int desativarOutros(Long id);
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/infra/OfertaRepository.java`:

`````java
package br.com.celke.cursos.pagamento.infra;

import br.com.celke.cursos.pagamento.domain.Oferta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {

    List<Oferta> findByTurmaIdOrderById(Long turmaId);

    List<Oferta> findByTurmaIdAndGatewayIdAndAtivaTrueOrderByPrecoCentavosAscIdAsc(Long turmaId, Long gatewayId);

    /** A oferta de uma notificação (produto + oferta no meio de pagamento). */
    Optional<Oferta> findByGatewayIdAndProdutoExternoAndOfertaExterna(
            Long gatewayId, String produtoExterno, String ofertaExterna);

    boolean existsByGatewayIdAndProdutoExternoAndOfertaExternaAndIdNot(
            Long gatewayId, String produtoExterno, String ofertaExterna, Long id);

    boolean existsByGatewayIdAndProdutoExternoAndOfertaExterna(
            Long gatewayId, String produtoExterno, String ofertaExterna);
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/infra/PedidoHistoricoRepository.java`:

`````java
package br.com.celke.cursos.pagamento.infra;

import br.com.celke.cursos.pagamento.domain.PedidoHistorico;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoHistoricoRepository extends JpaRepository<PedidoHistorico, Long> {

    List<PedidoHistorico> findByPedidoIdOrderById(Long pedidoId);
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/infra/PedidoRepository.java`:

`````java
package br.com.celke.cursos.pagamento.infra;

import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PedidoRepository extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido> {

    Optional<Pedido> findByCodigo(String codigo);

    /** Trava o pedido enquanto um evento é aplicado: dois eventos do mesmo pedido não andam juntos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.codigo = :codigo")
    Optional<Pedido> travarPorCodigo(String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.gatewayId = :gatewayId and p.transacaoExterna = :transacao")
    Optional<Pedido> travarPorTransacao(Long gatewayId, String transacao);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> travar(Long id);

    /** Pedidos de um gateway com transação, num dos status, sem mudança entre os dois instantes. */
    @Query("""
            select p from Pedido p
             where p.gatewayId = :gatewayId and p.transacaoExterna is not null and p.status in :status
               and p.atualizadoEm < :antesDe and p.atualizadoEm >= :depoisDe
             order by p.atualizadoEm
            """)
    List<Pedido> paraReconciliar(
            Long gatewayId, Collection<StatusPedido> status, Instant antesDe, Instant depoisDe, Pageable limite);

    /** Pedidos atrasados há mais tempo que o tolerado, ainda com matrícula. */
    List<Pedido> findByStatusAndAtualizadoEmBeforeAndMatriculaIdNotNull(StatusPedido status, Instant antesDe);
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/infra/hotmart/ClienteApiHotmart.java`:

`````java
package br.com.celke.cursos.pagamento.infra.hotmart;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * API REST da Hotmart: token OAuth por client credentials (guardado até 5 minutos antes de vencer) e
 * histórico de vendas filtrado pela transação (reconciliação).
 */
public class ClienteApiHotmart {

    static final Duration MARGEM_TOKEN = Duration.ofMinutes(5);

    private final RestClient http;
    private final HotmartProperties props;
    private final Clock clock;
    private final Map<String, TokenGuardado> tokens = new ConcurrentHashMap<>();

    public ClienteApiHotmart(RestClient http, HotmartProperties props, Clock clock) {
        this.http = http;
        this.props = props;
        this.clock = clock;
    }

    /** Cliente HTTP com o tempo máximo de conexão e de resposta de {@code props.timeout()}. */
    public static RestClient restClient(RestClient.Builder builder, HotmartProperties props) {
        HttpClient cliente =
                HttpClient.newBuilder().connectTimeout(props.timeout()).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(props.timeout());
        return builder.requestFactory(fabrica).build();
    }

    /** Token de acesso; pede um novo quando o guardado está a menos de 5 minutos de vencer. */
    public String token(Credenciais credenciais) {
        Instant agora = clock.instant();
        TokenGuardado guardado = tokens.get(credenciais.chaveCache());
        if (guardado != null && guardado.expiraEm().minus(MARGEM_TOKEN).isAfter(agora)) {
            return guardado.token();
        }
        JsonNode resposta = chamar(() -> http.post()
                .uri(
                        props.urlToken() + "?grant_type=client_credentials&client_id={id}&client_secret={segredo}",
                        credenciais.clientId(),
                        credenciais.clientSecret())
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credenciais.basic())
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        String token = InterpretadorHotmart.texto(resposta.path("access_token"));
        if (token == null) {
            throw new FalhaApiHotmart("A Hotmart não devolveu o token de acesso.");
        }
        long segundos = resposta.path("expires_in").asLong(3600);
        tokens.put(credenciais.chaveCache(), new TokenGuardado(token, agora.plusSeconds(segundos)));
        return token;
    }

    /**
     * A venda da transação no histórico de vendas. Sem filtro de status a Hotmart só devolve as
     * aprovadas e completas; {@code status} nulo pede essas.
     */
    public Optional<JsonNode> venda(Credenciais credenciais, String transacao, String status) {
        String token = token(credenciais);
        String base = credenciais.sandbox() ? props.urlApiSandbox() : props.urlApi();
        JsonNode resposta = chamar(() -> http.get()
                .uri(
                        base + "/sales/history?transaction={transacao}"
                                + (status == null ? "" : "&transaction_status={status}"),
                        status == null ? new Object[] {transacao} : new Object[] {transacao, status})
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        JsonNode itens = resposta.path("items");
        return itens.isArray() && !itens.isEmpty() ? Optional.of(itens.get(0)) : Optional.empty();
    }

    private static JsonNode chamar(Supplier<JsonNode> chamada) {
        try {
            JsonNode resposta = chamada.get();
            if (resposta == null) {
                throw new FalhaApiHotmart("A Hotmart respondeu sem corpo.");
            }
            return resposta;
        } catch (RestClientResponseException e) {
            throw new FalhaApiHotmart(
                    "A Hotmart recusou a chamada (HTTP %d)."
                            .formatted(e.getStatusCode().value()),
                    e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new FalhaApiHotmart("Não foi possível falar com a Hotmart: " + e.getMessage());
        }
    }

    /** Credenciais da API (Ferramentas → Credenciais na Hotmart). */
    public record Credenciais(String clientId, String clientSecret, String basic, boolean sandbox) {

        public Credenciais {
            if (basic != null && basic.regionMatches(true, 0, "Basic ", 0, 6)) {
                basic = basic.substring(6).strip();
            }
        }

        String chaveCache() {
            return clientId + (sandbox ? "@sandbox" : "@producao");
        }
    }

    private record TokenGuardado(String token, Instant expiraEm) {}

    /** Falha ao chamar a API (credencial recusada, rede, resposta inesperada). */
    public static class FalhaApiHotmart extends RuntimeException {

        private final int status;

        public FalhaApiHotmart(String mensagem) {
            this(mensagem, 0);
        }

        public FalhaApiHotmart(String mensagem, int status) {
            super(mensagem);
            this.status = status;
        }

        /** Status HTTP da recusa; 0 quando não houve resposta. */
        public int getStatus() {
            return status;
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/infra/hotmart/GatewayHotmart.java`:

`````java
package br.com.celke.cursos.pagamento.infra.hotmart;

import br.com.celke.cursos.pagamento.domain.CampoConfiguracao;
import br.com.celke.cursos.pagamento.domain.Capacidade;
import br.com.celke.cursos.pagamento.domain.Checkout;
import br.com.celke.cursos.pagamento.domain.Comprador;
import br.com.celke.cursos.pagamento.domain.ConfiguracaoGateway;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.EventoPagamento.CompradorEvento;
import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.NotificacaoRecebida;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.ResultadoTeste;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.domain.TipoCampo;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.Credenciais;
import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.FalhaApiHotmart;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

/**
 * Hotmart com o webhook 2.0.0: notificação autenticada pelo hottok no cabeçalho
 * {@code X-HOTMART-HOTTOK} (a Hotmart não assina o corpo), checkout por redirecionamento para o
 * {@code pay.hotmart.com} com o código do pedido no {@code xcod}, e consulta das vendas pela API.
 */
public class GatewayHotmart implements GatewayPagamento {

    public static final String CABECALHO_HOTTOK = "X-HOTMART-HOTTOK";
    static final String CHECKOUT_PADRAO = "https://pay.hotmart.com";

    /** Status consultados um a um quando a venda não está entre as aprovadas/completas. */
    static final List<String> STATUS_CONSULTADOS = List.of(
            "REFUNDED",
            "CHARGEBACK",
            "CANCELLED",
            "EXPIRED",
            "DISPUTE",
            "OVERDUE",
            "WAITING_PAYMENT",
            "PRINTED_BILLET");

    private static final Logger LOG = LoggerFactory.getLogger(GatewayHotmart.class);

    private static final List<CampoConfiguracao> CAMPOS = List.of(
            CampoConfiguracao.segredo(
                    "hottok",
                    "Hottok",
                    true,
                    "Ferramentas → Webhook na Hotmart: o token que vai no cabeçalho de cada notificação."),
            new CampoConfiguracao(
                    "ambiente",
                    "Ambiente",
                    TipoCampo.SELECAO,
                    false,
                    true,
                    "Sandbox para testar com a conta de testes da Hotmart.",
                    "PRODUCAO",
                    List.of("PRODUCAO", "SANDBOX")),
            new CampoConfiguracao(
                    "urlCheckoutBase",
                    "Endereço do checkout",
                    TipoCampo.URL,
                    false,
                    true,
                    "Início do link de pagamento das ofertas.",
                    CHECKOUT_PADRAO,
                    List.of()),
            CampoConfiguracao.texto(
                    "clientId", "Client ID", false, "Ferramentas → Credenciais (API). Só para a reconciliação."),
            CampoConfiguracao.segredo("clientSecret", "Client Secret", false, "Ferramentas → Credenciais (API)."),
            CampoConfiguracao.segredo("basic", "Basic", false, "Ferramentas → Credenciais (API): o token Basic."));

    private final InterpretadorHotmart interpretador;
    private final ClienteApiHotmart api;
    private final Clock clock;

    public GatewayHotmart(InterpretadorHotmart interpretador, ClienteApiHotmart api, Clock clock) {
        this.interpretador = interpretador;
        this.api = api;
        this.clock = clock;
    }

    @Override
    public TipoGateway tipo() {
        return TipoGateway.HOTMART;
    }

    @Override
    public String nome() {
        return "Hotmart";
    }

    @Override
    public List<CampoConfiguracao> camposConfiguracao() {
        return CAMPOS;
    }

    @Override
    public Set<Capacidade> capacidades() {
        return EnumSet.of(Capacidade.CHECKOUT_REDIRECIONAMENTO, Capacidade.CONSULTA_API, Capacidade.RECORRENCIA);
    }

    @Override
    public ResultadoTeste testar(ConfiguracaoGateway configuracao) {
        if (configuracao.valor("hottok") == null) {
            return ResultadoTeste.falha("Informe o hottok.");
        }
        Optional<Credenciais> credenciais;
        try {
            credenciais = credenciais(configuracao);
        } catch (IllegalArgumentException e) {
            return ResultadoTeste.falha(e.getMessage());
        }
        if (credenciais.isEmpty()) {
            return ResultadoTeste.ok("Hottok gravado. Sem as credenciais da API, a reconciliação fica desligada"
                    + " para este meio de pagamento.");
        }
        try {
            api.token(credenciais.get());
            return ResultadoTeste.ok("Credenciais da API conferidas com a Hotmart.");
        } catch (FalhaApiHotmart e) {
            return ResultadoTeste.falha(e.getMessage() + " Confira o Client ID, o Client Secret e o Basic.");
        }
    }

    /**
     * {@code <checkout>/<produto>?off=<oferta>&email=&name=&xcod=<pedido>&sck=plataforma}: o e-mail e o
     * nome chegam preenchidos e o {@code xcod} volta na notificação.
     */
    @Override
    public Checkout iniciarCheckout(
            Pedido pedido, Oferta oferta, Comprador comprador, ConfiguracaoGateway configuracao) {
        String base = configuracao.valor("urlCheckoutBase", CHECKOUT_PADRAO).replaceAll("/+$", "");
        StringBuilder url = new StringBuilder(base)
                .append('/')
                .append(UriUtils.encodePathSegment(oferta.codigoParaCheckout(), StandardCharsets.UTF_8))
                .append("?off=")
                .append(codificar(oferta.getOfertaExterna()))
                .append("&email=")
                .append(codificar(comprador.email()));
        if (comprador.nome() != null && !comprador.nome().isBlank()) {
            url.append("&name=").append(codificar(comprador.nome()));
        }
        url.append("&xcod=").append(codificar(pedido.getCodigo())).append("&sck=plataforma");
        return new Checkout.Redirecionamento(url.toString());
    }

    /** Hottok do cabeçalho comparado em tempo constante com o configurado. */
    @Override
    public boolean autenticar(NotificacaoRecebida notificacao, ConfiguracaoGateway configuracao) {
        String esperado = configuracao.valor("hottok");
        String recebido = notificacao.cabecalho(CABECALHO_HOTTOK);
        if (esperado == null || recebido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), recebido.strip().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public List<EventoPagamento> interpretar(NotificacaoRecebida notificacao) {
        return List.of(interpretador.interpretar(notificacao.corpoTexto()));
    }

    /** A venda no histórico de vendas, como evento sintético ({@code reconciliacao:<transação>:<status>}). */
    @Override
    public Optional<EventoPagamento> consultar(String transacao, ConfiguracaoGateway configuracao) {
        Optional<Credenciais> credenciais = credenciais(configuracao);
        if (credenciais.isEmpty()) {
            LOG.debug("Hotmart sem credenciais da API: transação {} não consultada", transacao);
            return Optional.empty();
        }
        Optional<JsonNode> venda = api.venda(credenciais.get(), transacao, null);
        for (int i = 0; venda.isEmpty() && i < STATUS_CONSULTADOS.size(); i++) {
            venda = api.venda(credenciais.get(), transacao, STATUS_CONSULTADOS.get(i));
        }
        return venda.map(item -> evento(transacao, item));
    }

    private EventoPagamento evento(String transacao, JsonNode item) {
        JsonNode compra = item.path("purchase");
        String status = Optional.ofNullable(InterpretadorHotmart.texto(compra.path("status")))
                .map(s -> s.toUpperCase(Locale.ROOT))
                .orElse("DESCONHECIDO");
        StatusPedido statusPedido = InterpretadorHotmart.POR_STATUS.get(status);
        JsonNode comprador = item.path("buyer");
        String email = InterpretadorHotmart.texto(comprador.path("email"));
        return new EventoPagamento(
                "reconciliacao:%s:%s".formatted(transacao, status),
                "RECONCILIACAO_" + status,
                clock.instant(),
                transacao,
                statusPedido,
                InterpretadorHotmart.texto(compra.path("tracking").path("external_code")),
                InterpretadorHotmart.texto(item.path("product").path("id")),
                InterpretadorHotmart.texto(compra.path("offer").path("code")),
                email == null
                        ? null
                        : new CompradorEvento(
                                email.toLowerCase(Locale.ROOT),
                                InterpretadorHotmart.texto(comprador.path("name")),
                                null),
                InterpretadorHotmart.centavos(compra.path("price").path("value")),
                InterpretadorHotmart.texto(compra.path("price").path("currency_code")),
                InterpretadorHotmart.forma(
                        InterpretadorHotmart.texto(compra.path("payment").path("type"))),
                null);
    }

    /** Credenciais da API, se as três foram informadas. */
    private static Optional<Credenciais> credenciais(ConfiguracaoGateway configuracao) {
        String clientId = configuracao.valor("clientId");
        String clientSecret = configuracao.valor("clientSecret");
        String basic = configuracao.valor("basic");
        if (clientId == null && clientSecret == null && basic == null) {
            return Optional.empty();
        }
        if (clientId == null || clientSecret == null || basic == null) {
            throw new IllegalArgumentException("Informe o Client ID, o Client Secret e o Basic juntos.");
        }
        return Optional.of(
                new Credenciais(clientId, clientSecret, basic, "SANDBOX".equals(configuracao.valor("ambiente"))));
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/infra/hotmart/HotmartConfig.java`:

`````java
package br.com.celke.cursos.pagamento.infra.hotmart;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Monta a Hotmart como um {@code GatewayPagamento}. */
@Configuration
@EnableConfigurationProperties(HotmartProperties.class)
public class HotmartConfig {

    @Bean
    GatewayHotmart gatewayHotmart(HotmartProperties props, JsonMapper jsonMapper, Clock clock) {
        ClienteApiHotmart api =
                new ClienteApiHotmart(ClienteApiHotmart.restClient(RestClient.builder(), props), props, clock);
        return new GatewayHotmart(new InterpretadorHotmart(jsonMapper), api, clock);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/infra/hotmart/HotmartProperties.java`:

`````java
package br.com.celke.cursos.pagamento.infra.hotmart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Endereços da API da Hotmart (confira na documentação antes de mudar).
 *
 * @param urlToken token OAuth por client credentials
 * @param urlApi API de pagamentos em produção (histórico de vendas)
 * @param urlApiSandbox API de pagamentos no sandbox
 * @param timeout tempo máximo de cada chamada
 */
@Validated
@ConfigurationProperties("app.pagamentos.hotmart")
public record HotmartProperties(
        @NotBlank String urlToken,
        @NotBlank String urlApi,
        @NotBlank String urlApiSandbox,
        @NotNull Duration timeout) {}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/infra/hotmart/InterpretadorHotmart.java`:

`````java
package br.com.celke.cursos.pagamento.infra.hotmart;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.pagamento.application.MascaraDadosPessoais;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.EventoPagamento.Assinatura;
import br.com.celke.cursos.pagamento.domain.EventoPagamento.CompradorEvento;
import br.com.celke.cursos.pagamento.domain.NotificacaoInvalidaException;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduz o webhook 2.0.0 da Hotmart ({@code {id, creation_date, event, version, data}}) para o
 * {@link EventoPagamento}. Tolerante: status desconhecido não quebra, campos de afiliado e coprodutor
 * (sem {@code buyer} e sem {@code price}) são opcionais e a data vem em milissegundos ou em texto.
 */
public class InterpretadorHotmart {

    /** O que cada evento de compra faz com o pedido. */
    static final Map<String, StatusPedido> POR_EVENTO = Map.of(
            "PURCHASE_APPROVED", StatusPedido.APROVADO,
            "PURCHASE_COMPLETE", StatusPedido.CONCLUIDO,
            "PURCHASE_CANCELED", StatusPedido.CANCELADO,
            "PURCHASE_REFUNDED", StatusPedido.REEMBOLSADO,
            "PURCHASE_CHARGEBACK", StatusPedido.CHARGEBACK,
            "PURCHASE_BILLET_PRINTED", StatusPedido.AGUARDANDO_PAGAMENTO,
            "PURCHASE_PROTEST", StatusPedido.EM_DISPUTA,
            "PURCHASE_DELAYED", StatusPedido.ATRASADO,
            "PURCHASE_EXPIRED", StatusPedido.EXPIRADO);

    /** {@code data.purchase.status}, usado quando o evento de compra não está na tabela acima. */
    static final Map<String, StatusPedido> POR_STATUS = Map.ofEntries(
            Map.entry("APPROVED", StatusPedido.APROVADO),
            Map.entry("COMPLETE", StatusPedido.CONCLUIDO),
            Map.entry("CANCELLED", StatusPedido.CANCELADO),
            Map.entry("BLOCKED", StatusPedido.CANCELADO),
            Map.entry("NO_FUNDS", StatusPedido.CANCELADO),
            Map.entry("CHARGEBACK", StatusPedido.CHARGEBACK),
            Map.entry("REFUNDED", StatusPedido.REEMBOLSADO),
            Map.entry("PARTIALLY_REFUNDED", StatusPedido.REEMBOLSADO),
            Map.entry("DISPUTE", StatusPedido.EM_DISPUTA),
            Map.entry("OVERDUE", StatusPedido.ATRASADO),
            Map.entry("EXPIRED", StatusPedido.EXPIRADO),
            Map.entry("PRINTED_BILLET", StatusPedido.AGUARDANDO_PAGAMENTO),
            Map.entry("WAITING_PAYMENT", StatusPedido.AGUARDANDO_PAGAMENTO),
            Map.entry("STARTED", StatusPedido.AGUARDANDO_PAGAMENTO),
            // Grafia da documentação da Hotmart, e a correta por garantia
            Map.entry("UNDER_ANALISYS", StatusPedido.AGUARDANDO_PAGAMENTO),
            Map.entry("UNDER_ANALYSIS", StatusPedido.AGUARDANDO_PAGAMENTO),
            Map.entry("PROCESSING_TRANSACTION", StatusPedido.AGUARDANDO_PAGAMENTO));

    private final JsonMapper jsonMapper;

    public InterpretadorHotmart(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** @throws NotificacaoInvalidaException corpo que não é JSON, sem id, sem evento ou de outra versão */
    public EventoPagamento interpretar(String corpo) {
        JsonNode raiz;
        try {
            raiz = jsonMapper.readTree(corpo);
        } catch (JacksonException e) {
            throw new NotificacaoInvalidaException("O corpo não é um JSON válido.", e);
        }
        if (raiz == null || !raiz.isObject()) {
            throw new NotificacaoInvalidaException("O corpo não é um objeto JSON.");
        }
        String id = texto(raiz.path("id"));
        String evento = texto(raiz.path("event"));
        String versao = texto(raiz.path("version"));
        if (id == null) {
            throw new NotificacaoInvalidaException("A notificação não tem id.");
        }
        if (evento == null) {
            throw new NotificacaoInvalidaException("A notificação não tem o nome do evento.");
        }
        if (versao != null && !versao.startsWith("2.")) {
            throw new NotificacaoInvalidaException(
                    "Webhook na versão " + versao + ": configure a versão 2.0.0 na Hotmart.");
        }
        JsonNode dados = raiz.path("data");
        JsonNode compra = dados.path("purchase");
        return new EventoPagamento(
                id,
                evento,
                instante(raiz.path("creation_date")),
                texto(compra.path("transaction")),
                status(evento, texto(compra.path("status"))),
                texto(compra.path("origin").path("xcod")),
                texto(dados.path("product").path("id")),
                texto(compra.path("offer").path("code")),
                comprador(dados.path("buyer")),
                centavos(compra.path("price").path("value")),
                maiusculas(texto(compra.path("price").path("currency_value"))),
                forma(texto(compra.path("payment").path("type"))),
                assinatura(dados.path("subscription")));
    }

    /** Status interno do evento; nulo para os que não mudam o pedido (assinatura, área de membros...). */
    static StatusPedido status(String evento, String statusCompra) {
        StatusPedido peloEvento = POR_EVENTO.get(evento);
        if (peloEvento != null) {
            return peloEvento;
        }
        if (evento.startsWith("PURCHASE_") && statusCompra != null) {
            return POR_STATUS.get(statusCompra.toUpperCase(Locale.ROOT));
        }
        return null;
    }

    static FormaPagamento forma(String tipo) {
        if (tipo == null) {
            return null;
        }
        return switch (tipo.toUpperCase(Locale.ROOT)) {
            case "CREDIT_CARD" -> FormaPagamento.CARTAO;
            case "PIX" -> FormaPagamento.PIX;
            case "BILLET" -> FormaPagamento.BOLETO;
            default -> FormaPagamento.OUTRO;
        };
    }

    /** Milissegundos desde 1970 (número ou texto) ou data ISO-8601; nulo se não der para ler. */
    static Instant instante(JsonNode no) {
        String valor = texto(no);
        if (valor == null) {
            return null;
        }
        if (valor.chars().allMatch(Character::isDigit)) {
            long numero = Long.parseLong(valor);
            // Menos de 12 dígitos só pode estar em segundos
            return numero < 100_000_000_000L ? Instant.ofEpochSecond(numero) : Instant.ofEpochMilli(numero);
        }
        try {
            return OffsetDateTime.parse(valor).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(valor);
            } catch (DateTimeParseException naoEhData) {
                return null;
            }
        }
    }

    /** Valor em reais (ou outra moeda) para centavos; nulo se ausente ou ilegível. */
    static Long centavos(JsonNode no) {
        String valor = texto(no);
        if (valor == null) {
            return null;
        }
        try {
            return new BigDecimal(valor)
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private static CompradorEvento comprador(JsonNode comprador) {
        if (!comprador.isObject()) {
            return null;
        }
        String email = texto(comprador.path("email"));
        return new CompradorEvento(
                email == null ? null : email.toLowerCase(Locale.ROOT),
                texto(comprador.path("name")),
                MascaraDadosPessoais.mascarar(texto(comprador.path("document"))));
    }

    private static Assinatura assinatura(JsonNode assinatura) {
        if (!assinatura.isObject()) {
            return null;
        }
        String codigo = texto(assinatura.path("subscriber").path("code"));
        String status = texto(assinatura.path("status"));
        return codigo == null && status == null ? null : new Assinatura(codigo, status);
    }

    private static String maiusculas(String texto) {
        return texto == null ? null : texto.toUpperCase(Locale.ROOT);
    }

    /** Texto do nó (número vira texto); nulo se ausente, nulo, vazio ou não for um valor simples. */
    static String texto(JsonNode no) {
        if (no == null || no.isMissingNode() || no.isNull() || !no.isValueNode()) {
            return null;
        }
        String valor = no.asString();
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
`````

`backend/src/main/resources/db/migration/V15__criar_pagamentos.sql`:

`````sql
-- Conta criada por uma compra nasce sem senha: o dono recebe o link de "criar senha"
ALTER TABLE usuarios ALTER COLUMN senha_hash DROP NOT NULL;

-- Meio de pagamento configurado pelo painel. O tipo não tem CHECK: um meio novo entra só com código.
-- Os segredos ficam cifrados (AES-256-GCM, chave PAGAMENTOS_CHAVE do ambiente, IV por gravação).
CREATE TABLE gateways_pagamento (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo           VARCHAR(30)  NOT NULL,
    nome           VARCHAR(120) NOT NULL,
    ativo          BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Campos não secretos da configuração
    configuracao   JSONB        NOT NULL DEFAULT '{}',
    segredos       BYTEA,
    -- Aleatório, entra na URL do webhook e identifica o gateway (não substitui a autenticação dele)
    webhook_token  VARCHAR(64)  NOT NULL,
    criado_em      TIMESTAMPTZ  NOT NULL,
    atualizado_em  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_gateways_pagamento_webhook_token UNIQUE (webhook_token)
);

-- Um gateway ativo por vez para os novos checkouts; os desativados continuam recebendo notificações
CREATE UNIQUE INDEX uq_gateways_pagamento_ativo ON gateways_pagamento (ativo) WHERE ativo;

-- O que se vende: uma turma por um produto/oferta do meio de pagamento
CREATE TABLE ofertas (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turma_id         BIGINT       NOT NULL REFERENCES turmas (id) ON DELETE RESTRICT,
    gateway_id       BIGINT       NOT NULL REFERENCES gateways_pagamento (id) ON DELETE RESTRICT,
    -- Id do produto e código da oferta no meio de pagamento (como chegam nas notificações)
    produto_externo  VARCHAR(100) NOT NULL,
    oferta_externa   VARCHAR(100) NOT NULL,
    -- Código do produto no link do checkout, quando diferente do id (Hotmart: pay.hotmart.com/<código>)
    codigo_checkout  VARCHAR(100),
    titulo           VARCHAR(120) NOT NULL,
    preco_centavos   BIGINT       NOT NULL,
    moeda            VARCHAR(3)   NOT NULL,
    -- Nulo: acesso sem prazo
    dias_acesso      INT,
    ativa            BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em        TIMESTAMPTZ  NOT NULL,
    atualizado_em    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_ofertas_externa UNIQUE (gateway_id, produto_externo, oferta_externa),
    CONSTRAINT ck_ofertas_preco CHECK (preco_centavos >= 0),
    CONSTRAINT ck_ofertas_dias CHECK (dias_acesso IS NULL OR dias_acesso > 0)
);

CREATE INDEX ix_ofertas_turma ON ofertas (turma_id);

-- Compra de uma oferta. "codigo" é público (vai no checkout e na página de retorno)
CREATE TABLE pedidos (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo             VARCHAR(22)  NOT NULL,
    oferta_id          BIGINT       NOT NULL REFERENCES ofertas (id) ON DELETE RESTRICT,
    gateway_id         BIGINT       NOT NULL REFERENCES gateways_pagamento (id) ON DELETE RESTRICT,
    -- Nulo até a conta do comprador existir
    usuario_id         BIGINT       REFERENCES usuarios (id) ON DELETE RESTRICT,
    email              VARCHAR(254) NOT NULL,
    nome               VARCHAR(120),
    status             VARCHAR(30)  NOT NULL,
    transacao_externa  VARCHAR(100),
    valor_centavos     BIGINT,
    moeda              VARCHAR(3),
    forma_pagamento    VARCHAR(20),
    matricula_id       BIGINT       REFERENCES matriculas (id) ON DELETE RESTRICT,
    -- CHECKOUT: nasceu no botão "Comprar"; EXTERNO: venda que chegou sem o nosso código (afiliado, link direto)
    origem             VARCHAR(20)  NOT NULL,
    -- Quando ocorreu o evento da última mudança de status (eventos fora de ordem não desfazem os mais novos)
    ultimo_evento_em   TIMESTAMPTZ,
    criado_em          TIMESTAMPTZ  NOT NULL,
    atualizado_em      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_pedidos_codigo UNIQUE (codigo),
    CONSTRAINT uq_pedidos_transacao UNIQUE (gateway_id, transacao_externa),
    CONSTRAINT ck_pedidos_status CHECK (status IN ('CRIADO', 'AGUARDANDO_PAGAMENTO', 'APROVADO', 'CONCLUIDO',
        'EM_DISPUTA', 'ATRASADO', 'REEMBOLSADO', 'CHARGEBACK', 'CANCELADO', 'EXPIRADO')),
    CONSTRAINT ck_pedidos_origem CHECK (origem IN ('CHECKOUT', 'EXTERNO'))
);

CREATE INDEX ix_pedidos_status ON pedidos (status, atualizado_em);
CREATE INDEX ix_pedidos_email ON pedidos (email);

-- Notificação recebida de um meio de pagamento. O único em (gateway, id_externo) é a idempotência.
CREATE TABLE eventos_pagamento (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gateway_id     BIGINT       NOT NULL REFERENCES gateways_pagamento (id) ON DELETE RESTRICT,
    id_externo     VARCHAR(150) NOT NULL,
    tipo_original  VARCHAR(60)  NOT NULL,
    recebido_em    TIMESTAMPTZ  NOT NULL,
    ocorrido_em    TIMESTAMPTZ,
    transacao      VARCHAR(100),
    -- Corpo recebido, com documento e telefone mascarados (apagado depois da retenção)
    payload        JSONB,
    -- Evento normalizado, lido pelo worker (apagado junto com o payload)
    dados          JSONB,
    status         VARCHAR(20)  NOT NULL,
    erro           TEXT,
    pedido_id      BIGINT       REFERENCES pedidos (id) ON DELETE RESTRICT,
    processado_em  TIMESTAMPTZ,
    CONSTRAINT uq_eventos_pagamento_externo UNIQUE (gateway_id, id_externo),
    CONSTRAINT ck_eventos_pagamento_status CHECK (status IN ('RECEBIDO', 'PROCESSADO', 'IGNORADO', 'ERRO'))
);

CREATE INDEX ix_eventos_pagamento_status ON eventos_pagamento (status, recebido_em);
CREATE INDEX ix_eventos_pagamento_pedido ON eventos_pagamento (pedido_id);

-- Linha do tempo do pedido
CREATE TABLE pedido_historico (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id  BIGINT       NOT NULL REFERENCES pedidos (id) ON DELETE RESTRICT,
    de         VARCHAR(30),
    para       VARCHAR(30)  NOT NULL,
    evento_id  BIGINT       REFERENCES eventos_pagamento (id) ON DELETE RESTRICT,
    motivo     VARCHAR(300),
    em         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_pedido_historico_pedido ON pedido_historico (pedido_id, id);

-- Matrícula feita por um pagamento aponta para o pedido; a forma "OUTRO" cobre PayPal, Google Pay...
ALTER TABLE matriculas ADD COLUMN pedido_id BIGINT REFERENCES pedidos (id) ON DELETE RESTRICT;
ALTER TABLE matriculas DROP CONSTRAINT ck_matriculas_forma_pagamento;
ALTER TABLE matriculas ADD CONSTRAINT ck_matriculas_forma_pagamento
    CHECK (forma_pagamento IN ('MANUAL', 'CORTESIA', 'PIX', 'CARTAO', 'BOLETO', 'OUTRO'));
`````

`backend/src/main/resources/templates/email/matricula-confirmada.html`:

`````html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <title>Sua matrícula foi confirmada</title>
</head>
<body style="margin:0;padding:24px;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
<div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px;">
    <h1 style="margin:0 0 16px;font-size:20px;">Sua matrícula foi confirmada</h1>
    <p>Olá, <span th:text="${nome}">aluno</span>!</p>
    <p>O pagamento foi aprovado e você já tem acesso ao curso
        <strong th:text="${curso}">Curso</strong>.</p>
    <th:block th:if="${linkSenha != null}">
        <p>Criamos a sua conta na Plataforma de Cursos com este e-mail. Para entrar, crie a sua senha:</p>
        <p style="margin:24px 0;">
            <a th:href="${linkSenha}" href="#"
               style="background:#2563eb;color:#ffffff;padding:12px 20px;border-radius:6px;text-decoration:none;display:inline-block;">
                Criar minha senha
            </a>
        </p>
        <p style="color:#6b7280;font-size:13px;">O link vale por <span th:text="${validade}">1 hora</span> e só
            pode ser usado uma vez. Se ele vencer, use "Esqueci minha senha" na página de entrada.</p>
    </th:block>
    <p th:if="${linkSenha == null}" style="margin:24px 0;">
        <a th:href="${linkCurso}" href="#"
           style="background:#2563eb;color:#ffffff;padding:12px 20px;border-radius:6px;text-decoration:none;display:inline-block;">
            Acessar o curso
        </a>
    </p>
    <p style="color:#6b7280;font-size:13px;">Nunca enviamos senhas por e-mail.</p>
</div>
</body>
</html>
`````

`backend/src/test/java/br/com/celke/cursos/matricula/application/MatriculaPagamentoServiceTest.java`:

`````java
package br.com.celke.cursos.matricula.application;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.cancelada;
import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.matricula.application.MatriculaPagamentoService.Concessao;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService.NovaConcessao;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatriculaPagamentoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant APROVACAO = Instant.parse("2026-03-10T11:00:00Z");

    @Mock
    private MatriculaRepository matriculas;

    private MatriculaPagamentoService service;

    @BeforeEach
    void setUp() {
        service = new MatriculaPagamentoService(matriculas, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static NovaConcessao concessao(Integer dias, Long matriculaAtual) {
        return new NovaConcessao(7L, 3L, APROVACAO, dias, FormaPagamento.CARTAO, "HP1", 9L, matriculaAtual);
    }

    @Nested
    class Conceder {

        @Test
        @DisplayName("sem matrícula: cria a do pagamento, com o fim pelos dias de acesso")
        void cria() {
            when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 3L, StatusMatricula.ATIVA))
                    .thenReturn(Optional.empty());
            when(matriculas.existsByFormaPagamentoAndTransacao(FormaPagamento.CARTAO, "HP1"))
                    .thenReturn(false);
            when(matriculas.saveAndFlush(any())).thenAnswer(chamada -> {
                Matricula m = chamada.getArgument(0);
                ReflectionTestUtils.setField(m, "id", 50L);
                return m;
            });

            Concessao resultado = service.conceder(concessao(365, null));

            assertThat(resultado).isEqualTo(new Concessao(50L, true));
            ArgumentCaptor<Matricula> gravada = ArgumentCaptor.forClass(Matricula.class);
            verify(matriculas).saveAndFlush(gravada.capture());
            assertThat(gravada.getValue().getInicio()).isEqualTo(APROVACAO);
            assertThat(gravada.getValue().getFim()).isEqualTo(APROVACAO.plus(Duration.ofDays(365)));
            assertThat(gravada.getValue().getPedidoId()).isEqualTo(9L);
            assertThat(gravada.getValue().getTransacao()).isEqualTo("HP1");
        }

        @Test
        @DisplayName("sem dias de acesso: sem prazo; transação já lançada à mão fica fora da matrícula nova")
        void semPrazoETransacaoRepetida() {
            when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 3L, StatusMatricula.ATIVA))
                    .thenReturn(Optional.empty());
            when(matriculas.existsByFormaPagamentoAndTransacao(FormaPagamento.CARTAO, "HP1"))
                    .thenReturn(true);
            when(matriculas.saveAndFlush(any())).thenAnswer(chamada -> chamada.getArgument(0));

            service.conceder(concessao(null, null));

            ArgumentCaptor<Matricula> gravada = ArgumentCaptor.forClass(Matricula.class);
            verify(matriculas).saveAndFlush(gravada.capture());
            assertThat(gravada.getValue().getFim()).isNull();
            assertThat(gravada.getValue().getTransacao()).isNull();
        }

        @Test
        @DisplayName("a matrícula do pedido ainda ativa não muda (evento repetido)")
        void jaConcedida() {
            when(matriculas.findById(50L)).thenReturn(Optional.of(matricula(50, 7, 3, APROVACAO, null)));

            assertThat(service.conceder(concessao(30, 50L))).isEqualTo(new Concessao(50L, false));
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("aluno já matriculado na turma: estende o período em vez de duplicar")
        void estende() {
            Matricula atual =
                    matricula(40, 7, 3, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-04-01T00:00:00Z"));
            when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 3L, StatusMatricula.ATIVA))
                    .thenReturn(Optional.of(atual));

            assertThat(service.conceder(concessao(30, null))).isEqualTo(new Concessao(40L, true));
            assertThat(atual.getFim()).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"));
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("matrícula do pedido cancelada (disputa resolvida): reativa")
        void reativa() {
            Matricula doPedido = cancelada(matricula(50, 7, 3, APROVACAO, null));
            when(matriculas.findById(50L)).thenReturn(Optional.of(doPedido));
            when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 3L, StatusMatricula.ATIVA))
                    .thenReturn(Optional.empty());

            assertThat(service.conceder(concessao(30, 50L))).isEqualTo(new Concessao(50L, true));
            assertThat(doPedido.getStatus()).isEqualTo(StatusMatricula.ATIVA);
        }
    }

    @Test
    @DisplayName("cancelar: ativa é cancelada; cancelada ou inexistente não muda nada")
    void cancelar() {
        Matricula ativa = matricula(50, 7, 3, APROVACAO, null);
        when(matriculas.findById(50L)).thenReturn(Optional.of(ativa));
        when(matriculas.findById(51L)).thenReturn(Optional.empty());

        service.cancelar(50L);
        service.cancelar(50L);
        service.cancelar(51L);

        assertThat(ativa.getStatus()).isEqualTo(StatusMatricula.CANCELADA);
        assertThat(ativa.getCanceladaEm()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("ativa: só a matrícula existente e não cancelada")
    void ativa() {
        when(matriculas.findById(50L)).thenReturn(Optional.of(matricula(50, 7, 3, APROVACAO, null)));
        when(matriculas.findById(51L)).thenReturn(Optional.of(cancelada(matricula(51, 7, 3, APROVACAO, null))));
        when(matriculas.findById(52L)).thenReturn(Optional.empty());

        assertThat(service.ativa(50L)).isTrue();
        assertThat(service.ativa(51L)).isFalse();
        assertThat(service.ativa(52L)).isFalse();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/GatewayAdmIT.java`:

`````java
package br.com.celke.cursos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Cobre: configuração dos meios de pagamento pelo painel (tipos, criação, segredos que nunca voltam
 * nem ficam em texto puro no banco, segredo em branco mantido, validação, teste de credenciais e um
 * ativo por vez) e as ofertas da turma (criação, duplicada, alteração, status, catálogo).
 * Não cobre: notificações (WebhookIT) e compra (PagamentoFluxoIT).
 * Dado criado: gateways "Hotmart IT", curso it-<stamp> publicado e ofertas com produto it-<stamp>.
 */
class GatewayAdmIT extends IntegrationTest {

    private CursoITHelper adm;
    private PagamentoITHelper pagamentos;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void login() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        pagamentos = new PagamentoITHelper(mvc, adm);
    }

    @Test
    @DisplayName("tipos disponíveis com os campos de configuração; a cifra está disponível")
    void tipos() {
        assertThat(adm.enviar("GET", "/api/adm/pagamentos/tipos", null))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"cifraDisponivel":true,"tipos":[{"tipo":"HOTMART","nome":"Hotmart",
                         "campos":[{"chave":"hottok","secreto":true,"obrigatorio":true},
                                   {"chave":"ambiente","tipo":"SELECAO","padrao":"PRODUCAO","opcoes":["PRODUCAO","SANDBOX"]},
                                   {"chave":"urlCheckoutBase","tipo":"URL","padrao":"https://pay.hotmart.com"},
                                   {"chave":"clientId","secreto":false},{"chave":"clientSecret","secreto":true},
                                   {"chave":"basic","secreto":true}]}]}
                        """);
    }

    @Test
    @DisplayName("segredos nunca voltam nem ficam em texto puro; em branco no PUT, o atual continua valendo")
    void segredos() {
        String hottok = "hottok-secreto-" + stamp();
        var criado = adm.enviar(
                "POST",
                "/api/adm/pagamentos/gateways",
                "{\"tipo\":\"HOTMART\",\"nome\":\" Hotmart IT \",\"valores\":{\"hottok\":\"%s\",\"clientId\":\"cid\"}}"
                        .formatted(hottok));
        long id = CursoITHelper.idCriado(criado);
        String corpo = CursoITHelper.corpo(criado);
        assertThat(corpo).doesNotContain(hottok);
        assertThat(criado).bodyJson().isLenientlyEqualTo("""
                        {"tipo":"HOTMART","nomeTipo":"Hotmart","nome":"Hotmart IT","ativo":false,
                         "valores":{"ambiente":"PRODUCAO","urlCheckoutBase":"https://pay.hotmart.com","clientId":"cid"},
                         "segredos":{"hottok":{"configurado":true,"final4":"%s"},
                                     "clientSecret":{"configurado":false,"final4":null}},
                         "segredosLegiveis":true}
                        """.formatted(hottok.substring(hottok.length() - 4)));
        String webhookUrl = JsonPath.read(corpo, "$.webhookUrl");
        assertThat(webhookUrl).startsWith("http://localhost:5173/api/pagamentos/webhooks/");
        String token = webhookUrl.substring(webhookUrl.lastIndexOf('/') + 1);

        String noBanco = jdbc.sql(
                        "SELECT configuracao::text || encode(segredos, 'escape') FROM gateways_pagamento WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
        assertThat(noBanco).contains("cid").doesNotContain(hottok);
        assertThat(CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/gateways", null)))
                .contains("\"id\":" + id)
                .doesNotContain(hottok);

        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/pagamentos/gateways/" + id,
                        "{\"nome\":\"Renomeada\",\"valores\":{\"hottok\":\"\",\"ambiente\":\"SANDBOX\"}}"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"nome\":\"Renomeada\",\"valores\":{\"ambiente\":\"SANDBOX\"},"
                        + "\"segredos\":{\"hottok\":{\"configurado\":true}}}");
        var gateway = new PagamentoITHelper.Gateway(id, token, hottok);
        pagamentos.enviar(
                gateway, pagamentos.notificacao("SUBSCRIPTION_CANCELLATION").json(), "recebido");

        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/pagamentos/gateways/" + id,
                        "{\"nome\":\"Renomeada\",\"valores\":{\"hottok\":\"novo-hottok\"}}"))
                .hasStatusOk();
        assertThat(pagamentos.webhook(gateway, "{}")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("campos inválidos voltam na lista de erros; meio inexistente 404")
    void validacao() {
        assertThat(adm.enviar(
                        "POST",
                        "/api/adm/pagamentos/gateways",
                        "{\"tipo\":\"HOTMART\",\"nome\":\"x\",\"valores\":{\"ambiente\":\"OUTRO\"}}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("valores.ambiente", "valores.hottok"));
        assertThat(adm.enviar("POST", "/api/adm/pagamentos/gateways", "{\"nome\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("nome", "tipo"));
        assertThat(adm.enviar("GET", "/api/adm/pagamentos/gateways/999999999", null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Meio de pagamento não encontrado.");
    }

    @Test
    @DisplayName("testar: só o hottok passa; credenciais da API que a Hotmart não confirma falham")
    void testar() {
        var gateway = pagamentos.hotmart("hottok-" + stamp(), false);
        assertThat(adm.enviar("POST", "/api/adm/pagamentos/gateways/" + gateway.id() + "/teste", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.ok")
                .isEqualTo(true);

        adm.enviar(
                "PUT",
                "/api/adm/pagamentos/gateways/" + gateway.id(),
                "{\"nome\":\"Hotmart IT\",\"valores\":{\"clientId\":\"id\",\"clientSecret\":\"s\",\"basic\":\"b\"}}");
        var teste = adm.enviar("POST", "/api/adm/pagamentos/gateways/" + gateway.id() + "/teste", null);
        assertThat(teste).hasStatusOk().bodyJson().extractingPath("$.ok").isEqualTo(false);
        assertThat(CursoITHelper.corpo(teste)).contains("Não foi possível falar com a Hotmart");
    }

    @Test
    @DisplayName("um ativo por vez: ativar outro desativa o anterior; desativar tira o ativo")
    void umAtivo() {
        var primeiro = pagamentos.hotmart("h1-" + stamp(), true);
        var segundo = pagamentos.hotmart("h2-" + stamp(), true);

        assertThat(adm.enviar("GET", "/api/adm/pagamentos/gateways/" + primeiro.id(), null))
                .bodyJson()
                .extractingPath("$.ativo")
                .isEqualTo(false);
        List<Integer> ativos = JsonPath.read(
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/gateways", null)),
                "$[?(@.ativo == true)].id");
        assertThat(ativos).containsExactly((int) segundo.id());

        assertThat(adm.enviar("DELETE", "/api/adm/pagamentos/gateways/" + segundo.id() + "/ativacao", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.ativo")
                .isEqualTo(false);
    }

    @Test
    @DisplayName("ofertas: cria no gateway ativo, recusa duplicada, altera, desativa e o catálogo acompanha")
    void ofertas() {
        String slug = stamp();
        long turma = new MatriculaITHelper(adm).cursoPublicado(slug).turma();
        var gateway = pagamentos.hotmart("h-" + stamp(), true);
        String produto = stamp();
        String corpo =
                "{\"produtoExterno\":\"%s\",\"ofertaExterna\":\"k2\",\"codigoCheckout\":\"X1\",\"titulo\":\"À vista\","
                                .formatted(produto)
                        + "\"precoCentavos\":19700,\"moeda\":\"brl\",\"diasAcesso\":365}";

        var criada = adm.enviar("POST", "/api/adm/turmas/" + turma + "/ofertas", corpo);
        long oferta = CursoITHelper.idCriado(criada);
        assertThat(criada).bodyJson().isLenientlyEqualTo("""
                        {"turmaId":%d,"gatewayId":%d,"gatewayNome":"Hotmart IT","gatewayAtivo":true,
                         "produtoExterno":"%s","ofertaExterna":"k2","codigoCheckout":"X1","moeda":"BRL",
                         "diasAcesso":365,"ativa":true}
                        """.formatted(turma, gateway.id(), produto));
        assertThat(adm.enviar("POST", "/api/adm/turmas/" + turma + "/ofertas", corpo))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Já existe uma oferta com este produto e esta oferta no meio de pagamento.");
        assertThat(adm.enviar("GET", "/api/cursos/" + slug, null))
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"ofertas\":[{\"id\":%d,\"titulo\":\"À vista\",\"precoCentavos\":19700,\"moeda\":\"BRL\"}]}"
                                .formatted(oferta));

        assertThat(adm.enviar("PUT", "/api/adm/ofertas/" + oferta, corpo.replace("À vista", "Parcelado")))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.titulo")
                .isEqualTo("Parcelado");
        assertThat(adm.enviar("PATCH", "/api/adm/ofertas/" + oferta + "/status", "{\"ativa\":false}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.ativa")
                .isEqualTo(false);
        assertThat(adm.enviar("GET", "/api/adm/turmas/" + turma + "/ofertas", null))
                .bodyJson()
                .extractingPath("$[0].titulo")
                .isEqualTo("Parcelado");
        assertThat(adm.enviar("GET", "/api/cursos/" + slug, null))
                .bodyJson()
                .extractingPath("$.ofertas")
                .isEqualTo(List.of());
    }

    @Test
    @DisplayName("oferta em turma inexistente 404; validação 400; oferta inexistente 404")
    void ofertasErros() {
        String corpo =
                "{\"produtoExterno\":\"p\",\"ofertaExterna\":\"o\",\"titulo\":\"t\",\"precoCentavos\":1,\"moeda\":\"BRL\"}";
        assertThat(adm.enviar("POST", "/api/adm/turmas/999999999/ofertas", corpo))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Turma não encontrada.");
        assertThat(adm.enviar("POST", "/api/adm/turmas/1/ofertas", "{\"moeda\":\"R$\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("moeda", "ofertaExterna", "precoCentavos", "produtoExterno", "titulo"));
        assertThat(adm.enviar("PATCH", "/api/adm/ofertas/999999999/status", "{\"ativa\":true}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/PagamentoAcessoIT.java`:

`````java
package br.com.celke.cursos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: matriz de papéis das rotas de pagamento — ADM ({@code /api/adm/pagamentos}, ofertas: 401
 * anônimo, 403 ALUNO, ADMIN passa), checkout (privada) e as públicas (situação do pedido e webhook).
 * Não cobre: regras de cada rota (GatewayAdmIT, WebhookIT, PagamentoFluxoIT).
 * Dado criado: curso it-<stamp> publicado, uma Hotmart ativa, uma oferta e um pedido.
 */
class PagamentoAcessoIT extends IntegrationTest {

    private String tokenAdmin;
    private String tokenAluno;
    private PagamentoITHelper.Gateway gateway;
    private long turma;
    private long oferta;
    private String pedido;

    @BeforeEach
    void criarEstrutura() {
        CursoITHelper adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        PagamentoITHelper pagamentos = new PagamentoITHelper(mvc, adm);
        turma = new MatriculaITHelper(adm).cursoPublicado(stamp()).turma();
        gateway = pagamentos.hotmart("hottok-" + stamp(), true);
        oferta = pagamentos.oferta(turma, stamp(), "oferta", null);
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        UsuarioTeste admin = criarUsuario(Papel.ADMIN);
        tokenAluno = login(aluno.email(), aluno.senha());
        tokenAdmin = login(admin.email(), admin.senha());
        pedido = pagamentos.checkout(bearer(tokenAluno), oferta);
    }

    static Stream<Arguments> rotasAdm() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/adm/pagamentos/tipos", null, HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/adm/pagamentos/gateways", null, HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/adm/pagamentos/gateways/{g}", null, HttpStatus.OK),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/pagamentos/gateways",
                        "{\"tipo\":\"HOTMART\",\"nome\":\"Outra\",\"valores\":{\"hottok\":\"h\"}}",
                        HttpStatus.CREATED),
                Arguments.of(
                        HttpMethod.PUT,
                        "/api/adm/pagamentos/gateways/{g}",
                        "{\"nome\":\"Hotmart IT\",\"valores\":{}}",
                        HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/pagamentos/gateways/{g}/teste", null, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/pagamentos/gateways/{g}/ativacao", null, HttpStatus.OK),
                Arguments.of(HttpMethod.DELETE, "/api/adm/pagamentos/gateways/{g}/ativacao", null, HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/adm/turmas/{t}/ofertas", null, HttpStatus.OK),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/turmas/{t}/ofertas",
                        "{\"gatewayId\":{g},\"produtoExterno\":\"{novo}\",\"ofertaExterna\":\"o\",\"titulo\":\"t\","
                                + "\"precoCentavos\":1,\"moeda\":\"BRL\"}",
                        HttpStatus.CREATED),
                Arguments.of(
                        HttpMethod.PUT,
                        "/api/adm/ofertas/{o}",
                        "{\"produtoExterno\":\"{novo}\",\"ofertaExterna\":\"o\",\"titulo\":\"t\",\"precoCentavos\":1,"
                                + "\"moeda\":\"BRL\"}",
                        HttpStatus.OK),
                Arguments.of(HttpMethod.PATCH, "/api/adm/ofertas/{o}/status", "{\"ativa\":true}", HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/adm/pagamentos/pedidos", null, HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/adm/pagamentos/eventos", null, HttpStatus.OK));
    }

    private MvcTestResult chamar(HttpMethod metodo, String rota, String corpo, String token) {
        String novo = stamp();
        var requisicao = mvc.method(metodo)
                .uri(rota.replace("{g}", String.valueOf(gateway.id()))
                        .replace("{t}", String.valueOf(turma))
                        .replace("{o}", String.valueOf(oferta)));
        if (corpo != null) {
            requisicao = requisicao
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpo.replace("{g}", String.valueOf(gateway.id())).replace("{novo}", novo));
        }
        if (token != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao.exchange();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: anônimo 401, ALUNO 403")
    void admSemPapel(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(metodo, rota, corpo, tokenAluno))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Acesso negado.");
    }

    @ParameterizedTest(name = "{0} {1} → {3}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: ADMIN passa")
    void admAdmin(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, tokenAdmin)).hasStatus(admin);
    }

    @Test
    @DisplayName("checkout: anônimo 401; ALUNO e ADMIN compram")
    void checkout() {
        String corpo = "{\"ofertaId\":%d}".formatted(oferta);
        assertThat(chamar(HttpMethod.POST, "/api/pagamentos/checkout", corpo, null))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Autenticação necessária.");
        assertThat(chamar(HttpMethod.POST, "/api/pagamentos/checkout", corpo, tokenAluno))
                .hasStatus(HttpStatus.CREATED);
        assertThat(chamar(HttpMethod.POST, "/api/pagamentos/checkout", corpo, tokenAdmin))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("situação do pedido é pública (só GET); o webhook é público, mas exige o hottok")
    void publicas() {
        assertThat(chamar(HttpMethod.GET, "/api/pagamentos/pedidos/" + pedido, null, null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CRIADO");
        assertThat(chamar(HttpMethod.POST, "/api/pagamentos/pedidos/" + pedido, null, null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.POST, "/api/pagamentos/webhooks/" + gateway.webhookToken(), "{}", null))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Notificação não autenticada.");
        assertThat(chamar(HttpMethod.GET, "/api/pagamentos/webhooks/" + gateway.webhookToken(), null, null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/PagamentoFluxoIT.java`:

`````java
package br.com.celke.cursos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.pagamento.application.ProcessamentoEventoService;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.Mailpit;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: a compra de ponta a ponta na API — checkout com o xcod, notificação aprovada que matricula e
 * avisa, reembolso e chargeback que tiram o acesso, aprovação depois do reembolso que não reabre,
 * evento antigo que não desfaz o mais novo, boleto aguardando, venda sem xcod que cria a conta sem
 * senha (com o link de criar senha), dois eventos do mesmo pedido ao mesmo tempo e o reprocessamento
 * depois de cadastrar a oferta. A página de retorno vê só o status sem login e o curso com o dono.
 * Não cobre: recebimento e autenticação do webhook (WebhookIT), painel (GatewayAdmIT).
 * Dado criado: curso it-<stamp> publicado, Hotmart ativa da classe, ofertas e contas it-<stamp>.
 */
class PagamentoFluxoIT extends IntegrationTest {

    @Autowired
    private Mailpit mailpit;

    @Autowired
    private ProcessamentoEventoService processamento;

    private CursoITHelper adm;
    private PagamentoITHelper pagamentos;
    private PagamentoITHelper.Gateway gateway;
    private long turma;
    private String produto;
    private long oferta;

    @BeforeEach
    void configurar() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        pagamentos = new PagamentoITHelper(mvc, adm);
        turma = new MatriculaITHelper(adm).cursoPublicado(stamp()).turma();
        gateway = pagamentos.hotmart("hottok-" + stamp(), true);
        produto = stamp();
        oferta = pagamentos.oferta(turma, produto, "k2", 365);
    }

    private PagamentoITHelper.Notificacao evento(String tipo, String xcod, String transacao) {
        return pagamentos.notificacao(tipo).xcod(xcod).transacao(transacao).produto(produto, "k2");
    }

    private void notificar(PagamentoITHelper.Notificacao notificacao) {
        pagamentos.enviar(gateway, notificacao.json(), "recebido");
        processarFila();
    }

    private MvcTestResult turmaDoAluno(String token) {
        return mvc.get()
                .uri("/api/aluno/turmas/" + turma)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }

    private MvcTestResult situacao(String codigo, String token) {
        var requisicao = mvc.get().uri("/api/pagamentos/pedidos/" + codigo);
        if (token != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao.exchange();
    }

    /** Pedido no painel pelo código (lista filtrada pelo e-mail do comprador). */
    private Map<String, Object> pedidoNoPainel(String email) {
        List<Map<String, Object>> itens = JsonPath.read(
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/pedidos?email=" + email, null)), "$.itens");
        assertThat(itens).hasSize(1);
        return itens.getFirst();
    }

    @Test
    @DisplayName("checkout → aprovado matricula e avisa → reembolso tira o acesso → aprovado de novo não reabre")
    void compraEReembolso() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String token = login(aluno.email(), aluno.senha());

        // 1. Checkout: URL da Hotmart com a oferta, o e-mail e o código do pedido
        MvcTestResult checkout = mvc.post()
                .uri("/api/pagamentos/checkout")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ofertaId\":%d}".formatted(oferta))
                .exchange();
        assertThat(checkout).hasStatus(HttpStatus.CREATED);
        String codigo = JsonPath.read(CursoITHelper.corpo(checkout), "$.pedido");
        assertThat(codigo).hasSize(22);
        assertThat(checkout).bodyJson().isLenientlyEqualTo("""
                        {"tipo":"REDIRECIONAMENTO","url":"https://pay.hotmart.com/%s?off=k2&email=%s&name=Usu%%C3%%A1rio+%s&xcod=%s&sck=plataforma"}
                        """.formatted(
                produto, aluno.email().replace("@", "%40"), aluno.email().replace("@", "%40"), codigo));
        assertThat(situacao(codigo, null)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"codigo":"%s","status":"CRIADO","turmaId":null,"cursoTitulo":null}
                """.formatted(codigo));
        assertThat(turmaDoAluno(token)).hasStatus(HttpStatus.FORBIDDEN);

        // 2. Aprovado: matrícula e e-mail de confirmação (conta com senha: sem link de criar senha)
        Instant aprovado = Instant.now();
        notificar(evento("PURCHASE_APPROVED", codigo, "HP-" + codigo).ocorridoEm(aprovado.toEpochMilli()));
        assertThat(turmaDoAluno(token)).hasStatusOk();
        assertThat(situacao(codigo, token)).bodyJson().isLenientlyEqualTo("""
                {"status":"APROVADO","turmaId":%d,"turmaNome":"Turma"}
                """.formatted(turma));
        assertThat(situacao(codigo, null))
                .bodyJson()
                .extractingPath("$.turmaId")
                .isNull();
        Mailpit.EmailRecebido email = mailpit.aguardarEmailPara(aluno.email());
        assertThat(email.Subject()).isEqualTo("Sua matrícula foi confirmada");
        assertThat(email.HTML())
                .contains("http://localhost:5173/turmas/" + turma)
                .doesNotContain("token=");

        // 3. Reembolso: acesso termina
        notificar(evento("PURCHASE_REFUNDED", codigo, "HP-" + codigo)
                .ocorridoEm(aprovado.plusSeconds(60).toEpochMilli()));
        assertThat(turmaDoAluno(token)).hasStatus(HttpStatus.FORBIDDEN);

        // 4. Aprovado de novo (reenvio tardio): não reabre
        notificar(evento("PURCHASE_APPROVED", codigo, "HP-" + codigo)
                .ocorridoEm(aprovado.plusSeconds(120).toEpochMilli()));
        assertThat(turmaDoAluno(token)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(situacao(codigo, token))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("REEMBOLSADO");
        assertThat(mailpit.quantidadePara(aluno.email())).isEqualTo(1);

        // 5. Painel: linha do tempo e eventos
        Map<String, Object> pedido = pedidoNoPainel(aluno.email());
        assertThat(pedido).containsEntry("origem", "CHECKOUT").containsEntry("formaPagamento", "CARTAO");
        String detalhe =
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/pedidos/" + pedido.get("id"), null));
        assertThat(JsonPath.<List<String>>read(detalhe, "$.historico[*].para"))
                .containsExactly("CRIADO", "APROVADO", "REEMBOLSADO");
        assertThat(JsonPath.<List<String>>read(detalhe, "$.eventos[*].status"))
                .containsExactly("PROCESSADO", "PROCESSADO", "IGNORADO");
    }

    @Test
    @DisplayName("venda sem xcod: cria a conta sem senha, matricula e manda o link de criar senha")
    void vendaExterna() {
        String email = stamp() + "@teste.celke.uno";

        notificar(evento("PURCHASE_APPROVED", null, "HP-" + stamp()).comprador(email, "Compradora Externa"));

        Map<String, Object> pedido = pedidoNoPainel(email);
        assertThat(pedido).containsEntry("origem", "EXTERNO").containsEntry("status", "APROVADO");
        assertThat(pedido.get("matriculaId")).isNotNull();
        Mailpit.EmailRecebido recebido = mailpit.aguardarEmailPara(email);
        assertThat(recebido.Subject()).isEqualTo("Sua matrícula foi confirmada");
        assertThat(recebido.HTML())
                .contains("/redefinir-senha?token=")
                .contains("origem=pagamento")
                .contains("Criar minha senha");
        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"qualquer-senha-1\"}".formatted(email)))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(mvc.post()
                        .uri("/api/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"senha\":\"senha-criada-agora\"}"
                                .formatted(Mailpit.tokenDoLink(recebido))))
                .hasStatusOk();
        String token = login(email, "senha-criada-agora");
        assertThat(turmaDoAluno(token)).hasStatusOk();
    }

    @Test
    @DisplayName("chargeback tira o acesso; boleto impresso só aguarda; evento antigo não desfaz o novo")
    void chargebackBoletoForaDeOrdem() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String token = login(aluno.email(), aluno.senha());
        String codigo = pagamentos.checkout(bearer(token), oferta);
        String transacao = "HP-" + codigo;
        Instant t = Instant.now();

        notificar(evento("PURCHASE_BILLET_PRINTED", codigo, transacao).ocorridoEm(t.toEpochMilli()));
        assertThat(situacao(codigo, token))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(turmaDoAluno(token)).hasStatus(HttpStatus.FORBIDDEN);

        notificar(evento("PURCHASE_APPROVED", codigo, transacao)
                .ocorridoEm(t.plusSeconds(60).toEpochMilli()));
        // Cancelamento com data anterior à aprovação chega depois: não desfaz
        notificar(evento("PURCHASE_CANCELED", codigo, transacao)
                .ocorridoEm(t.plusSeconds(30).toEpochMilli()));
        assertThat(turmaDoAluno(token)).hasStatusOk();
        assertThat(situacao(codigo, token))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("APROVADO");

        notificar(evento("PURCHASE_CHARGEBACK", codigo, transacao)
                .ocorridoEm(t.plusSeconds(90).toEpochMilli()));
        assertThat(turmaDoAluno(token)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(situacao(codigo, token))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CHARGEBACK");
    }

    @Test
    @DisplayName("dois eventos do mesmo pedido ao mesmo tempo: a trava aplica um, o outro é ignorado")
    void concorrencia() throws Exception {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String codigo = pagamentos.checkout(bearer(login(aluno.email(), aluno.senha())), oferta);
        String primeiro = "evento-" + stamp();
        String segundo = "evento-" + stamp();
        pagamentos.enviar(
                gateway,
                evento("PURCHASE_APPROVED", codigo, "HP-" + codigo).id(primeiro).json(),
                "recebido");
        pagamentos.enviar(
                gateway,
                evento("PURCHASE_APPROVED", codigo, "HP-" + codigo).id(segundo).json(),
                "recebido");
        String lista =
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/eventos?status=RECEBIDO&size=100", null));
        List<Integer> ids = JsonPath.read(
                lista,
                "$.itens[?(@.idExterno in ['%s','%s'] && @.gatewayId == %d)].id"
                        .formatted(primeiro, segundo, gateway.id()));
        assertThat(ids).hasSize(2);

        CountDownLatch largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = CompletableFuture.runAsync(() -> esperarEProcessar(largada, ids.get(0)), executor);
            var b = CompletableFuture.runAsync(() -> esperarEProcessar(largada, ids.get(1)), executor);
            largada.countDown();
            CompletableFuture.allOf(a, b).join();
        }
        processarFila();

        String detalhe = CursoITHelper.corpo(adm.enviar(
                "GET",
                "/api/adm/pagamentos/pedidos/" + pedidoNoPainel(aluno.email()).get("id"),
                null));
        assertThat(JsonPath.<List<String>>read(detalhe, "$.historico[*].para")).containsExactly("CRIADO", "APROVADO");
        assertThat(JsonPath.<List<String>>read(detalhe, "$.eventos[*].status"))
                .containsExactlyInAnyOrder("PROCESSADO", "IGNORADO");
        assertThat(mailpit.aguardarEmailPara(aluno.email()).Subject()).isEqualTo("Sua matrícula foi confirmada");
        assertThat(mailpit.quantidadePara(aluno.email())).isEqualTo(1);
    }

    private void esperarEProcessar(CountDownLatch largada, int eventoId) {
        try {
            largada.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        processamento.processar((long) eventoId);
    }

    @Test
    @DisplayName("venda de oferta ainda não cadastrada é ignorada; cadastrada a oferta, o reprocessamento matricula")
    void reprocessaDepoisDeCadastrarAOferta() {
        String outroProduto = stamp();
        String email = stamp() + "@teste.celke.uno";
        String id = "evento-" + stamp();
        notificar(pagamentos
                .notificacao("PURCHASE_APPROVED")
                .id(id)
                .xcod(null)
                .transacao("HP-" + stamp())
                .produto(outroProduto, "nova")
                .comprador(email, "Compradora"));
        List<Integer> ids = JsonPath.read(
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/eventos?status=IGNORADO&size=100", null)),
                "$.itens[?(@.idExterno == '%s')].id".formatted(id));
        assertThat(ids).hasSize(1);

        pagamentos.oferta(turma, outroProduto, "nova", null);
        assertThat(adm.enviar("POST", "/api/adm/pagamentos/eventos/" + ids.getFirst() + "/reprocessamento", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("RECEBIDO");
        processarFila();

        assertThat(adm.enviar("GET", "/api/adm/pagamentos/eventos/" + ids.getFirst(), null))
                .bodyJson()
                .extractingPath("$.evento.status")
                .isEqualTo("PROCESSADO");
        assertThat(pedidoNoPainel(email)).containsEntry("status", "APROVADO");
        assertThat(adm.enviar("POST", "/api/adm/pagamentos/eventos/" + ids.getFirst() + "/reprocessamento", null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Só eventos com erro ou ignorados podem ser reprocessados.");
    }

    @Test
    @DisplayName("checkout de oferta inativa 404; de gateway desativado 409; pedido inexistente 404")
    void checkoutRecusado() {
        String token = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);
        adm.enviar("PATCH", "/api/adm/ofertas/" + oferta + "/status", "{\"ativa\":false}");
        assertThat(mvc.post()
                        .uri("/api/pagamentos/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ofertaId\":%d}".formatted(oferta)))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Oferta não encontrada.");

        adm.enviar("PATCH", "/api/adm/ofertas/" + oferta + "/status", "{\"ativa\":true}");
        adm.enviar("DELETE", "/api/adm/pagamentos/gateways/" + gateway.id() + "/ativacao", null);
        assertThat(mvc.post()
                        .uri("/api/pagamentos/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ofertaId\":%d}".formatted(oferta)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Esta oferta não está à venda no momento.");
        assertThat(situacao("nao-existe", null)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/PagamentoITHelper.java`:

`````java
package br.com.celke.cursos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.pagamento.infra.hotmart.FixturesHotmart;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Configura a Hotmart e ofertas pela API do ADM e manda notificações montadas a partir das fixtures. */
public final class PagamentoITHelper {

    private final MockMvcTester mvc;
    private final CursoITHelper adm;
    private final JsonMapper json = JsonMapper.builder().build();

    public PagamentoITHelper(MockMvcTester mvc, CursoITHelper adm) {
        this.mvc = mvc;
        this.adm = adm;
    }

    /** Hotmart configurada só com o hottok; ativa se pedido. */
    public Gateway hotmart(String hottok, boolean ativar) {
        MvcTestResult criado = adm.enviar(
                "POST",
                "/api/adm/pagamentos/gateways",
                "{\"tipo\":\"HOTMART\",\"nome\":\"Hotmart IT\",\"valores\":{\"hottok\":\"%s\"}}".formatted(hottok));
        long id = CursoITHelper.idCriado(criado);
        String webhookUrl = JsonPath.read(CursoITHelper.corpo(criado), "$.webhookUrl");
        if (ativar) {
            assertThat(adm.enviar("POST", "/api/adm/pagamentos/gateways/" + id + "/ativacao", null))
                    .hasStatusOk();
        }
        return new Gateway(id, webhookUrl.substring(webhookUrl.lastIndexOf('/') + 1), hottok);
    }

    /** Oferta na turma, no gateway ativo; devolve o id. */
    public long oferta(long turma, String produto, String oferta, Integer diasAcesso) {
        return CursoITHelper.idCriado(adm.enviar(
                "POST",
                "/api/adm/turmas/" + turma + "/ofertas",
                "{\"produtoExterno\":\"%s\",\"ofertaExterna\":\"%s\",\"titulo\":\"À vista\",\"precoCentavos\":19700,\"moeda\":\"BRL\"%s}"
                        .formatted(produto, oferta, diasAcesso == null ? "" : ",\"diasAcesso\":" + diasAcesso)));
    }

    /** Checkout do aluno; confere o 201 e devolve o código do pedido. */
    public String checkout(String bearer, long oferta) {
        MvcTestResult resposta = mvc.post()
                .uri("/api/pagamentos/checkout")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ofertaId\":%d}".formatted(oferta))
                .exchange();
        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(CursoITHelper.corpo(resposta), "$.pedido");
    }

    public MvcTestResult webhook(Gateway gateway, String corpo) {
        return mvc.post()
                .uri("/api/pagamentos/webhooks/" + gateway.webhookToken())
                .header("X-HOTMART-HOTTOK", gateway.hottok())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
                .exchange();
    }

    /** Envia e confere o 200 com o resultado esperado. */
    public void enviar(Gateway gateway, String corpo, String resultado) {
        assertThat(webhook(gateway, corpo))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.resultado")
                .isEqualTo(resultado);
    }

    /**
     * Notificação a partir da fixture do evento, com id novo e ocorrida agora (a data da fixture
     * envelhece e deixaria vencida a matrícula com prazo).
     */
    public Notificacao notificacao(String evento) {
        return new Notificacao((ObjectNode) json.readTree(FixturesHotmart.corpo(evento)))
                .id(UUID.randomUUID().toString())
                .ocorridoEm(System.currentTimeMillis());
    }

    public record Gateway(long id, String webhookToken, String hottok) {}

    /** Corpo de uma notificação com os campos que os testes trocam. */
    public final class Notificacao {

        private final ObjectNode raiz;

        private Notificacao(ObjectNode raiz) {
            this.raiz = raiz;
        }

        public Notificacao id(String id) {
            raiz.put("id", id);
            return this;
        }

        public Notificacao ocorridoEm(long epochMillis) {
            raiz.put("creation_date", epochMillis);
            return this;
        }

        public Notificacao xcod(String xcod) {
            ObjectNode origem = (ObjectNode) raiz.get("data").get("purchase").get("origin");
            if (xcod == null) {
                origem.remove("xcod");
            } else {
                origem.put("xcod", xcod);
            }
            return this;
        }

        public Notificacao transacao(String transacao) {
            ((ObjectNode) raiz.get("data").get("purchase")).put("transaction", transacao);
            return this;
        }

        public Notificacao produto(String produto, String oferta) {
            ((ObjectNode) raiz.get("data").get("product")).put("id", produto);
            ((ObjectNode) raiz.get("data").get("purchase").get("offer")).put("code", oferta);
            return this;
        }

        public Notificacao comprador(String email, String nome) {
            ObjectNode comprador = (ObjectNode) raiz.get("data").get("buyer");
            comprador.put("email", email);
            comprador.put("name", nome);
            return this;
        }

        public String json() {
            return json.writeValueAsString(raiz);
        }
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/WebhookIT.java`:

`````java
package br.com.celke.cursos.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Cobre: recebimento das notificações — hottok certo grava (corpo mascarado) e enfileira; o mesmo id
 * é gravado uma vez; hottok errado ou ausente 401 sem gravar; endereço desconhecido 404; corpo que não
 * é JSON 200 gravado como erro; corpo grande demais 413; oferta não cadastrada e evento não tratado
 * ficam ignorados com o motivo.
 * Não cobre: efeito no pedido e na matrícula (PagamentoFluxoIT).
 * Dado criado: uma Hotmart inativa por teste e eventos com produto it-<stamp>.
 */
class WebhookIT extends IntegrationTest {

    private CursoITHelper adm;
    private PagamentoITHelper pagamentos;
    private PagamentoITHelper.Gateway gateway;

    @BeforeEach
    void configurar() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        pagamentos = new PagamentoITHelper(mvc, adm);
        gateway = pagamentos.hotmart("hottok-" + stamp(), false);
    }

    /** Eventos do gateway desta classe com o id externo, pelo painel (mais recentes primeiro). */
    private List<Map<String, Object>> eventos(String idExterno) {
        return JsonPath.read(
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/eventos?size=100", null)),
                "$.itens[?(@.idExterno == '%s' && @.gatewayId == %d)]".formatted(idExterno, gateway.id()));
    }

    @Test
    @DisplayName("hottok certo: grava com o corpo mascarado, enfileira e o mesmo id não é gravado de novo")
    void gravaUmaVez() {
        String id = "evento-" + stamp();
        String produto = stamp();
        String corpo = pagamentos
                .notificacao("PURCHASE_APPROVED")
                .id(id)
                .produto(produto, "k2")
                .json();

        pagamentos.enviar(gateway, corpo, "recebido");
        pagamentos.enviar(gateway, corpo, "duplicado");

        List<Map<String, Object>> gravados = eventos(id);
        assertThat(gravados).singleElement().satisfies(evento -> {
            assertThat(evento).containsEntry("status", "RECEBIDO").containsEntry("tipoOriginal", "PURCHASE_APPROVED");
            assertThat(evento.get("transacao")).isEqualTo("HP17715690036014");
        });
        int eventoId = (Integer) gravados.getFirst().get("id");
        String detalhe = CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/eventos/" + eventoId, null));
        assertThat(detalhe).contains("***09").doesNotContain("12345678909", "5511999990000", "200.100.50.25");

        processarFila();

        assertThat(eventos(id))
                .singleElement()
                .satisfies(evento -> assertThat(evento)
                        .containsEntry("status", "IGNORADO")
                        .containsEntry("erro", "Oferta não cadastrada (produto %s, oferta k2).".formatted(produto)));
    }

    @Test
    @DisplayName("evento que não muda o pedido é gravado e ignorado")
    void naoTratado() {
        String id = "evento-" + stamp();
        pagamentos.enviar(
                gateway,
                pagamentos.notificacao("SUBSCRIPTION_CANCELLATION").id(id).json(),
                "recebido");

        processarFila();

        assertThat(eventos(id))
                .singleElement()
                .satisfies(evento -> assertThat(evento)
                        .containsEntry("status", "IGNORADO")
                        .containsEntry("erro", "O evento SUBSCRIPTION_CANCELLATION não muda o pedido."));
    }

    @Test
    @DisplayName("hottok errado ou ausente: 401 sem gravar nada")
    void hottokErrado() {
        String id = "evento-" + stamp();
        String corpo = pagamentos.notificacao("PURCHASE_APPROVED").id(id).json();
        var outroHottok = new PagamentoITHelper.Gateway(gateway.id(), gateway.webhookToken(), "hottok-errado");

        assertThat(pagamentos.webhook(outroHottok, corpo))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Notificação não autenticada.");
        assertThat(mvc.post()
                        .uri("/api/pagamentos/webhooks/" + gateway.webhookToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(eventos(id)).isEmpty();
    }

    @Test
    @DisplayName("endereço desconhecido: 404")
    void enderecoDesconhecido() {
        var desconhecido = new PagamentoITHelper.Gateway(0, "nao-existe-" + stamp(), gateway.hottok());

        assertThat(pagamentos.webhook(desconhecido, "{}"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Endereço de notificação desconhecido.");
        assertThat(pagamentos.webhook(new PagamentoITHelper.Gateway(0, "x".repeat(65), "h"), "{}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("corpo que não é JSON: 200, gravado como erro (reenviar não resolve) e uma vez só")
    void corpoInvalido() {
        String corpo = "não é json " + stamp();

        pagamentos.enviar(gateway, corpo, "erro");
        pagamentos.enviar(gateway, corpo, "duplicado");

        List<Map<String, Object>> invalidos = JsonPath.read(
                CursoITHelper.corpo(adm.enviar("GET", "/api/adm/pagamentos/eventos?status=ERRO&size=100", null)),
                "$.itens[?(@.gatewayId == %d)]".formatted(gateway.id()));
        assertThat(invalidos)
                .singleElement()
                .satisfies(evento -> assertThat(evento)
                        .containsEntry("tipoOriginal", "INVALIDO")
                        .containsEntry("erro", "O corpo não é um JSON válido."));
        int id = (Integer) invalidos.getFirst().get("id");
        assertThat(adm.enviar("POST", "/api/adm/pagamentos/eventos/" + id + "/reprocessamento", null))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("corpo acima de 256 KB: 413")
    void grandeDemais() {
        String corpo = "{\"x\":\"" + "a".repeat(256 * 1024) + "\"}";

        assertThat(pagamentos.webhook(gateway, corpo))
                .hasStatus(HttpStatus.CONTENT_TOO_LARGE)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Notificação grande demais.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/api/PagamentoRequestsTest.java`:

`````java
package br.com.celke.cursos.pagamento.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.pagamento.domain.TipoGateway;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PagamentoRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static PagamentoRequests.Oferta ofertaValida() {
        return new PagamentoRequests.Oferta(null, "4774438", "k2pasun0", null, "À vista", 19_700L, "BRL", 365);
    }

    @Test
    @DisplayName("oferta válida: gateway, código do checkout e dias de acesso são opcionais")
    void ofertaValidaPassa() {
        assertThat(camposInvalidos(ofertaValida())).isEmpty();
        assertThat(camposInvalidos(new PagamentoRequests.Oferta(1L, "p", "o", "c", "t", 0L, "usd", null)))
                .isEmpty();
    }

    @Test
    @DisplayName("oferta: obrigatórios, tamanhos, preço, moeda e dias de acesso")
    void ofertaInvalida() {
        assertThat(camposInvalidos(new PagamentoRequests.Oferta(null, " ", "", "x".repeat(101), null, null, "R$", 0)))
                .containsExactlyInAnyOrder(
                        "produtoExterno",
                        "ofertaExterna",
                        "codigoCheckout",
                        "titulo",
                        "precoCentavos",
                        "moeda",
                        "diasAcesso");
        assertThat(camposInvalidos(new PagamentoRequests.Oferta(
                        null, "x".repeat(101), "x".repeat(101), null, "x".repeat(121), -1L, "BRLL", 36_501)))
                .containsExactlyInAnyOrder(
                        "produtoExterno", "ofertaExterna", "titulo", "precoCentavos", "moeda", "diasAcesso");
        assertThat(camposInvalidos(
                        new PagamentoRequests.Oferta(null, "p", "o", null, "t", 1_000_000_001L, "BRL", null)))
                .containsExactly("precoCentavos");
    }

    @Test
    @DisplayName("os dados da oferta repassam todos os campos")
    void dados() {
        var dados = ofertaValida().dados();

        assertThat(dados.produtoExterno()).isEqualTo("4774438");
        assertThat(dados.ofertaExterna()).isEqualTo("k2pasun0");
        assertThat(dados.titulo()).isEqualTo("À vista");
        assertThat(dados.precoCentavos()).isEqualTo(19_700L);
        assertThat(dados.moeda()).isEqualTo("BRL");
        assertThat(dados.diasAcesso()).isEqualTo(365);
    }

    @Test
    @DisplayName("gateway exige tipo e nome; checkout exige a oferta; status exige o valor")
    void demais() {
        assertThat(camposInvalidos(new PagamentoRequests.NovoGateway(TipoGateway.HOTMART, "Hotmart", Map.of())))
                .isEmpty();
        assertThat(camposInvalidos(new PagamentoRequests.NovoGateway(null, "x".repeat(121), null)))
                .containsExactlyInAnyOrder("tipo", "nome");
        assertThat(camposInvalidos(new PagamentoRequests.AtualizarGateway(" ", null)))
                .containsExactly("nome");
        assertThat(camposInvalidos(new PagamentoRequests.Checkout(null))).containsExactly("ofertaId");
        assertThat(camposInvalidos(new PagamentoRequests.StatusOferta(null))).containsExactly("ativa");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/application/CifraSegredosTest.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CifraSegredosTest {

    private static final String CHAVE = "dGVzdGUtZGEtY2hhdmUtZGUtcGFnYW1lbnRvcy0zMmI=";
    private static final String OUTRA_CHAVE = "b3V0cmEtY2hhdmUtZGUtcGFnYW1lbnRvcy0zMi1ieXQ=";
    private final CifraSegredos cifra = new CifraSegredos(CHAVE);

    @Test
    @DisplayName("ida e volta devolve o texto; o blob não contém o texto")
    void idaEVolta() {
        byte[] blob = cifra.cifrar("{\"hottok\":\"segredo-123\"}", "token-a");

        assertThat(new String(blob, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("segredo-123");
        assertThat(cifra.decifrar(blob, "token-a")).isEqualTo("{\"hottok\":\"segredo-123\"}");
    }

    @Test
    @DisplayName("cada gravação usa um IV diferente")
    void ivDiferente() {
        byte[] primeiro = cifra.cifrar("igual", "token-a");
        byte[] segundo = cifra.cifrar("igual", "token-a");

        assertThat(Arrays.copyOf(primeiro, 12)).isNotEqualTo(Arrays.copyOf(segundo, 12));
        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    @DisplayName("chave errada não decifra")
    void chaveErrada() {
        byte[] blob = cifra.cifrar("segredo", "token-a");

        assertThatThrownBy(() -> new CifraSegredos(OUTRA_CHAVE).decifrar(blob, "token-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Não foi possível decifrar os segredos do meio de pagamento. A PAGAMENTOS_CHAVE mudou?");
    }

    @Test
    @DisplayName("blob de um gateway não decifra em outro (dado associado)")
    void outroGateway() {
        byte[] blob = cifra.cifrar("segredo", "token-a");

        assertThatThrownBy(() -> cifra.decifrar(blob, "token-b")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("blob alterado ou curto demais não decifra")
    void blobInvalido() {
        byte[] blob = cifra.cifrar("segredo", "token-a");
        blob[blob.length - 1] ^= 1;

        assertThatThrownBy(() -> cifra.decifrar(blob, "token-a")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cifra.decifrar(new byte[12], "token-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Segredos gravados em formato inválido.");
        assertThatThrownBy(() -> cifra.decifrar(null, "token-a")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("sem chave a cifra fica indisponível e recusa cifrar e decifrar")
    void semChave() {
        CifraSegredos semChave = new CifraSegredos(" ");

        assertThat(semChave.disponivel()).isFalse();
        assertThat(new CifraSegredos(null).disponivel()).isFalse();
        assertThatThrownBy(() -> semChave.cifrar("a", "b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PAGAMENTOS_CHAVE não está definida.");
        assertThatThrownBy(() -> semChave.decifrar(new byte[20], "b")).isInstanceOf(IllegalStateException.class);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/application/ConfiguracaoGatewayServiceTest.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.pagamento.application.ConfiguracaoGatewayService.SegredoResumo;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import br.com.celke.cursos.pagamento.infra.hotmart.GatewayHotmart;
import br.com.celke.cursos.pagamento.infra.hotmart.InterpretadorHotmart;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.ErroCampo;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ConfiguracaoGatewayServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String CHAVE = "dGVzdGUtZGEtY2hhdmUtZGUtcGFnYW1lbnRvcy0zMmI=";

    private final JsonMapper json = JsonMapper.builder().build();
    private final GatewaysRegistrados registrados = new GatewaysRegistrados(
            List.of(new GatewayHotmart(new InterpretadorHotmart(json), null, Clock.fixed(AGORA, ZoneOffset.UTC))));
    private final ConfiguracaoGatewayService service =
            new ConfiguracaoGatewayService(registrados, new CifraSegredos(CHAVE), json);

    private static Gateway gateway() {
        return new Gateway(TipoGateway.HOTMART, "Hotmart", "token-do-webhook", AGORA);
    }

    private static Map<String, String> valores(String... chavesEValores) {
        Map<String, String> valores = new HashMap<>();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            valores.put(chavesEValores[i], chavesEValores[i + 1]);
        }
        return valores;
    }

    @Test
    @DisplayName("separa os segredos (cifrados) dos campos comuns e aplica os padrões")
    void grava() {
        Gateway gateway = gateway();

        service.configurar(gateway, " Minha Hotmart ", valores("hottok", " segredo-hottok ", "clientId", "abc"), AGORA);

        assertThat(gateway.getNome()).isEqualTo("Minha Hotmart");
        assertThat(gateway.getConfiguracao())
                .isEqualTo(
                        "{\"ambiente\":\"PRODUCAO\",\"clientId\":\"abc\",\"urlCheckoutBase\":\"https://pay.hotmart.com\"}");
        assertThat(gateway.getSegredos()).isNotNull();
        assertThat(service.segredos(gateway)).isEqualTo(Map.of("hottok", "segredo-hottok"));
        assertThat(service.ler(gateway).valores())
                .containsEntry("hottok", "segredo-hottok")
                .containsEntry("clientId", "abc")
                .containsEntry("ambiente", "PRODUCAO");
    }

    @Test
    @DisplayName("segredo em branco mantém o valor atual; informado, troca")
    void segredoEmBrancoMantem() {
        Gateway gateway = gateway();
        service.configurar(gateway, "Hotmart", valores("hottok", "antigo", "basic", "b-antigo"), AGORA);

        service.configurar(gateway, "Hotmart", valores("hottok", "", "basic", "b-novo"), AGORA);

        assertThat(service.segredos(gateway)).isEqualTo(Map.of("hottok", "antigo", "basic", "b-novo"));
        assertThat(service.resumoSegredos(gateway))
                .containsExactly(
                        Map.entry("hottok", new SegredoResumo(true, "tigo")),
                        Map.entry("clientSecret", new SegredoResumo(false, null)),
                        Map.entry("basic", new SegredoResumo(true, "novo")));
    }

    @Test
    @DisplayName("segredo curto demais não mostra os últimos caracteres")
    void segredoCurto() {
        Gateway gateway = gateway();
        service.configurar(gateway, "Hotmart", valores("hottok", "1234"), AGORA);

        assertThat(service.resumoSegredos(gateway).get("hottok")).isEqualTo(new SegredoResumo(true, null));
    }

    @Test
    @DisplayName("campo obrigatório, opção, endereço, tamanho e campo desconhecido viram erros de campo")
    void validacao() {
        Gateway gateway = gateway();

        assertThatThrownBy(() -> service.configurar(
                        gateway,
                        "Hotmart",
                        valores(
                                "ambiente", "TESTE",
                                "urlCheckoutBase", "ftp://pay",
                                "clientId", "x".repeat(501),
                                "inventado", "1"),
                        AGORA))
                .isInstanceOf(ConfiguracaoInvalidaException.class)
                .hasMessage("Dados inválidos.")
                .extracting(e -> ((ConfiguracaoInvalidaException) e).getErros())
                .isEqualTo(List.of(
                        new ErroCampo("valores.ambiente", "Escolha uma das opções: PRODUCAO, SANDBOX."),
                        new ErroCampo("valores.clientId", "Use no máximo 500 caracteres."),
                        new ErroCampo("valores.hottok", "Informe Hottok."),
                        new ErroCampo("valores.inventado", "Campo desconhecido."),
                        new ErroCampo("valores.urlCheckoutBase", "Informe um endereço começando com https://.")));
        assertThat(gateway.getSegredos()).isNull();
        assertThat(gateway.getConfiguracao()).isEqualTo("{}");
    }

    @Test
    @DisplayName("endereço sem host ou malformado é recusado; valores nulos contam como vazios")
    void enderecoInvalido() {
        assertThatThrownBy(() -> service.configurar(
                        gateway(), "Hotmart", valores("hottok", "h", "urlCheckoutBase", "https://"), AGORA))
                .isInstanceOf(ConfiguracaoInvalidaException.class);
        assertThatThrownBy(() -> service.configurar(
                        gateway(), "Hotmart", valores("hottok", "h", "urlCheckoutBase", "https://a b"), AGORA))
                .isInstanceOf(ConfiguracaoInvalidaException.class);
        assertThatThrownBy(() -> service.configurar(gateway(), "Hotmart", null, AGORA))
                .isInstanceOf(ConfiguracaoInvalidaException.class);
    }

    @Test
    @DisplayName("sem a PAGAMENTOS_CHAVE não grava segredos")
    void semChave() {
        var semChave = new ConfiguracaoGatewayService(registrados, new CifraSegredos(""), json);
        Gateway gateway = gateway();

        assertThatThrownBy(() -> semChave.configurar(gateway, "Hotmart", valores("hottok", "h"), AGORA))
                .isInstanceOf(ConflitoException.class)
                .hasMessage(
                        "Defina a PAGAMENTOS_CHAVE no .env do ambiente para gravar as credenciais do meio de pagamento.");
        assertThat(gateway.getSegredos()).isNull();
    }

    @Test
    @DisplayName("sem segredos gravados, a leitura traz só os comuns")
    void semSegredos() {
        assertThat(service.ler(gateway()).valores()).isEmpty();
        assertThat(service.segredos(gateway())).isEmpty();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/application/MascaraDadosPessoaisTest.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class MascaraDadosPessoaisTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("mascara documento, telefone e IP em qualquer profundidade; o resto fica igual")
    void mascaraCorpo() {
        var arvore = json.readTree("""
                {"data":{"buyer":{"email":"a@b.c","document":"12345678909","checkout_phone":"5511999990000"},
                 "purchase":{"buyer_ip":"200.1.2.3","transaction":"HP1"},
                 "lista":[{"doc":"987"}], "producer":{"document":null}}}
                """);

        String mascarado = json.writeValueAsString(MascaraDadosPessoais.mascarar(arvore));

        assertThat(mascarado)
                .contains("\"email\":\"a@b.c\"")
                .contains("\"document\":\"***09\"")
                .contains("\"checkout_phone\":\"***00\"")
                .contains("\"buyer_ip\":\"***.3\"")
                .contains("\"transaction\":\"HP1\"")
                .contains("\"doc\":\"***87\"")
                .contains("\"document\":null")
                .doesNotContain("12345678909", "5511999990000", "200.1.2.3");
    }

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource(
            value = {"12345678909, ***09", "12, ***", "1, ***", "'  ab12 ', ***12"},
            nullValues = "nulo")
    void mascaraValor(String valor, String esperado) {
        assertThat(MascaraDadosPessoais.mascarar(valor)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("vazio e nulo ficam como estão")
    void vazio() {
        assertThat(MascaraDadosPessoais.mascarar((String) null)).isNull();
        assertThat(MascaraDadosPessoais.mascarar(" ")).isEqualTo(" ");
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
        "document, true",
        "document_type, true",
        "CPF, true",
        "checkout_phone, true",
        "buyer_ip, true",
        "ip, true",
        "email, false",
        "zipcode, false",
        "shipping, false",
        "product, false",
    })
    void camposPessoais(String campo, boolean pessoal) {
        assertThat(MascaraDadosPessoais.pessoal(campo)).isEqualTo(pessoal);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/application/PagamentosPropertiesTest.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class PagamentosPropertiesTest {

    private static PagamentosProperties com(String chave) {
        return new PagamentosProperties(
                chave,
                DataSize.ofKilobytes(256),
                600,
                Duration.ofDays(180),
                false,
                null,
                new PagamentosProperties.Reconciliacao(
                        false, Duration.ofHours(24), Duration.ofDays(3), Duration.ofDays(60)));
    }

    @Test
    @DisplayName("chave em branco é aceita (a API sobe sem gravar segredos); 32 bytes em Base64 também")
    void aceitas() {
        assertThat(com(null).chave()).isNull();
        assertThat(com("").chave()).isEmpty();
        assertThat(com("dGVzdGUtZGEtY2hhdmUtZGUtcGFnYW1lbnRvcy0zMmI=").chave()).isNotBlank();
    }

    @Test
    @DisplayName("chave fora de Base64 ou com outro tamanho impede a subida")
    void recusadas() {
        assertThatThrownBy(() -> com("não é base64!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PAGAMENTOS_CHAVE precisa estar em Base64.");
        assertThatThrownBy(() -> com("Y3VydGE="))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PAGAMENTOS_CHAVE precisa ter 32 bytes (openssl rand -base64 32); tem 5.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/application/ProcessamentoEventoServiceTest.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import static br.com.celke.cursos.support.fixtures.UsuarioFixture.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.application.ContaProperties;
import br.com.celke.cursos.auth.application.RecuperacaoSenhaService;
import br.com.celke.cursos.auth.application.RecuperacaoSenhaService.LinkSenha;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService.Concessao;
import br.com.celke.cursos.matricula.application.MatriculaPagamentoService.NovaConcessao;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.EventoPagamento.CompradorEvento;
import br.com.celke.cursos.pagamento.domain.EventoRecebido;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.OrigemPedido;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.PedidoHistorico;
import br.com.celke.cursos.pagamento.domain.StatusEvento;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.domain.TransicoesPedido;
import br.com.celke.cursos.pagamento.infra.EventoRecebidoRepository;
import br.com.celke.cursos.pagamento.infra.OfertaRepository;
import br.com.celke.cursos.pagamento.infra.PedidoHistoricoRepository;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ProcessamentoEventoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant OCORRIDO = AGORA.minus(Duration.ofMinutes(5));
    private static final Long GATEWAY = 1L;

    @Mock
    private EventoRecebidoRepository eventos;

    @Mock
    private PedidoRepository pedidos;

    @Mock
    private PedidoHistoricoRepository historico;

    @Mock
    private OfertaRepository ofertas;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private MatriculaPagamentoService matriculaPagamentoService;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private RecuperacaoSenhaService recuperacaoSenhaService;

    @Mock
    private EmailService emailService;

    @Mock
    private AvisosPagamento avisos;

    private final JsonMapper json = JsonMapper.builder().build();
    private ProcessamentoEventoService service;
    private Oferta oferta;

    @BeforeEach
    void setUp() {
        service = new ProcessamentoEventoService(
                eventos,
                pedidos,
                historico,
                ofertas,
                usuarioService,
                matriculaPagamentoService,
                conteudoTurmaService,
                recuperacaoSenhaService,
                emailService,
                avisos,
                new TransicoesPedido(false),
                new ContaProperties("https://aluno.celke.uno", Duration.ofHours(48), Duration.ofHours(1), 3),
                json,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        oferta = new Oferta(
                3L, GATEWAY, new Oferta.DadosOferta("4774438", "k2", null, "À vista", 19_700, "BRL", 365), AGORA);
        ReflectionTestUtils.setField(oferta, "id", 20L);
    }

    private static EventoPagamento dados(StatusPedido status, String xcod, String email) {
        return new EventoPagamento(
                "ev-1",
                "PURCHASE_X",
                OCORRIDO,
                "HP1",
                status,
                xcod,
                "4774438",
                "k2",
                email == null ? null : new CompradorEvento(email, "Ana", null),
                19_700L,
                "BRL",
                FormaPagamento.PIX,
                null);
    }

    private EventoRecebido evento(EventoPagamento dados, StatusEvento status) {
        EventoRecebido evento = BeanUtils.instantiateClass(EventoRecebido.class);
        ReflectionTestUtils.setField(evento, "id", 100L);
        ReflectionTestUtils.setField(evento, "gatewayId", GATEWAY);
        ReflectionTestUtils.setField(evento, "status", status);
        ReflectionTestUtils.setField(evento, "dados", dados == null ? null : json.writeValueAsString(dados));
        when(eventos.travar(100L)).thenReturn(Optional.of(evento));
        return evento;
    }

    private Pedido pedidoDoCheckout(StatusPedido status) {
        Pedido pedido = Pedido.doCheckout(oferta, 7L, "ana@celke.uno", "Ana", AGORA.minusSeconds(3600));
        ReflectionTestUtils.setField(pedido, "id", 30L);
        ReflectionTestUtils.setField(pedido, "status", status);
        return pedido;
    }

    @Test
    @DisplayName("evento inexistente é erro permanente; já processado não faz nada")
    void inexistenteOuProcessado() {
        when(eventos.travar(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.processar(99L)).isInstanceOf(ErroPermanenteException.class);

        evento(dados(StatusPedido.APROVADO, null, null), StatusEvento.PROCESSADO);
        service.processar(100L);
        verifyNoInteractions(pedidos, matriculaPagamentoService, emailService);
    }

    @Test
    @DisplayName("sem dados, o evento vai para erro; evento que não muda o pedido é ignorado")
    void semDadosOuNaoTratado() {
        EventoRecebido semDados = evento(null, StatusEvento.RECEBIDO);
        service.processar(100L);
        assertThat(semDados.getStatus()).isEqualTo(StatusEvento.ERRO);

        EventoRecebido naoTratado = evento(dados(null, null, null), StatusEvento.RECEBIDO);
        service.processar(100L);
        assertThat(naoTratado.getStatus()).isEqualTo(StatusEvento.IGNORADO);
        assertThat(naoTratado.getErro()).isEqualTo("O evento PURCHASE_X não muda o pedido.");
        verifyNoInteractions(pedidos);
    }

    @Test
    @DisplayName("venda sem pedido e de oferta não cadastrada: ignorada com o motivo")
    void ofertaNaoCadastrada() {
        EventoRecebido evento = evento(dados(StatusPedido.APROVADO, null, "ana@celke.uno"), StatusEvento.RECEBIDO);
        when(pedidos.travarPorTransacao(GATEWAY, "HP1")).thenReturn(Optional.empty());
        when(ofertas.findByGatewayIdAndProdutoExternoAndOfertaExterna(GATEWAY, "4774438", "k2"))
                .thenReturn(Optional.empty());

        service.processar(100L);

        assertThat(evento.getStatus()).isEqualTo(StatusEvento.IGNORADO);
        assertThat(evento.getErro()).isEqualTo("Oferta não cadastrada (produto 4774438, oferta k2).");
        verify(pedidos, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("venda sem pedido e sem e-mail do comprador (afiliado): ignorada")
    void semComprador() {
        EventoRecebido evento = evento(dados(StatusPedido.APROVADO, null, null), StatusEvento.RECEBIDO);
        when(pedidos.travarPorTransacao(GATEWAY, "HP1")).thenReturn(Optional.empty());
        when(ofertas.findByGatewayIdAndProdutoExternoAndOfertaExterna(GATEWAY, "4774438", "k2"))
                .thenReturn(Optional.of(oferta));

        service.processar(100L);

        assertThat(evento.getErro()).isEqualTo("A notificação não traz o e-mail do comprador.");
    }

    @Test
    @DisplayName("venda externa aprovada: cria o pedido, a conta sem senha, a matrícula e manda o link de criar senha")
    void vendaExterna() {
        EventoRecebido evento = evento(dados(StatusPedido.APROVADO, null, "nova@celke.uno"), StatusEvento.RECEBIDO);
        when(pedidos.travarPorTransacao(GATEWAY, "HP1")).thenReturn(Optional.empty());
        when(ofertas.findByGatewayIdAndProdutoExternoAndOfertaExterna(GATEWAY, "4774438", "k2"))
                .thenReturn(Optional.of(oferta));
        when(pedidos.saveAndFlush(any())).thenAnswer(chamada -> {
            Pedido p = chamada.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 31L);
            return p;
        });
        when(ofertas.findById(20L)).thenReturn(Optional.of(oferta));
        when(usuarioService.buscarPorEmail("nova@celke.uno")).thenReturn(Optional.empty());
        Usuario nova = usuario(8, "nova@celke.uno", null, Papel.ALUNO);
        when(usuarioService.criarAlunoSemSenha("Ana", "nova@celke.uno")).thenReturn(nova);
        when(matriculaPagamentoService.conceder(
                        new NovaConcessao(8L, 3L, OCORRIDO, 365, FormaPagamento.PIX, "HP1", 31L, null)))
                .thenReturn(new Concessao(50L, true));
        when(conteudoTurmaService.turma(3L)).thenReturn(Optional.empty());
        when(recuperacaoSenhaService.linkCriarSenha(8L))
                .thenReturn(Optional.of(new LinkSenha("https://aluno.celke.uno/redefinir-senha?token=t", "1 hora")));

        service.processar(100L);

        ArgumentCaptor<Pedido> pedido = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidos).saveAndFlush(pedido.capture());
        assertThat(pedido.getValue().getOrigem()).isEqualTo(OrigemPedido.EXTERNO);
        assertThat(pedido.getValue().getStatus()).isEqualTo(StatusPedido.APROVADO);
        assertThat(pedido.getValue().getUsuarioId()).isEqualTo(8L);
        assertThat(pedido.getValue().getMatriculaId()).isEqualTo(50L);
        assertThat(evento.getStatus()).isEqualTo(StatusEvento.PROCESSADO);
        assertThat(evento.getPedidoId()).isEqualTo(31L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variaveis = ArgumentCaptor.forClass(Map.class);
        verify(emailService)
                .enviar(
                        eq("nova@celke.uno"),
                        eq("Sua matrícula foi confirmada"),
                        eq("matricula-confirmada"),
                        variaveis.capture());
        assertThat(variaveis.getValue())
                .containsEntry("curso", "seu curso")
                .containsEntry("linkCurso", "https://aluno.celke.uno/turmas/3")
                .containsEntry("linkSenha", "https://aluno.celke.uno/redefinir-senha?token=t")
                .containsEntry("validade", "1 hora");
        verify(historico, times(2)).save(any(PedidoHistorico.class));
    }

    @Test
    @DisplayName("pedido do checkout reembolsado: cancela a matrícula, sem e-mail ao aluno")
    void reembolso() {
        evento(dados(StatusPedido.REEMBOLSADO, "codigo", "ana@celke.uno"), StatusEvento.RECEBIDO);
        Pedido pedido = pedidoDoCheckout(StatusPedido.APROVADO);
        pedido.vincularMatricula(50L, AGORA);
        when(pedidos.travarPorCodigo("codigo")).thenReturn(Optional.of(pedido));

        service.processar(100L);

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.REEMBOLSADO);
        assertThat(pedido.getTransacaoExterna()).isEqualTo("HP1");
        verify(matriculaPagamentoService).cancelar(50L);
        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("aprovado depois de reembolsado não reabre: evento ignorado com o motivo")
    void naoReabre() {
        EventoRecebido evento = evento(dados(StatusPedido.APROVADO, "codigo", "ana@celke.uno"), StatusEvento.RECEBIDO);
        when(pedidos.travarPorCodigo("codigo")).thenReturn(Optional.of(pedidoDoCheckout(StatusPedido.REEMBOLSADO)));

        service.processar(100L);

        assertThat(evento.getStatus()).isEqualTo(StatusEvento.IGNORADO);
        assertThat(evento.getErro()).isEqualTo("Mudança de REEMBOLSADO para APROVADO não é permitida.");
        verifyNoInteractions(matriculaPagamentoService, historico);
    }

    @Test
    @DisplayName("xcod de um pedido de outra transação: segue pela transação conhecida")
    void xcodDeOutraTransacao() {
        evento(dados(StatusPedido.CHARGEBACK, "codigo", "ana@celke.uno"), StatusEvento.RECEBIDO);
        Pedido outro = pedidoDoCheckout(StatusPedido.APROVADO);
        outro.vincularTransacao("HP-OUTRA", AGORA);
        Pedido certo = pedidoDoCheckout(StatusPedido.APROVADO);
        when(pedidos.travarPorCodigo("codigo")).thenReturn(Optional.of(outro));
        when(pedidos.travarPorTransacao(GATEWAY, "HP1")).thenReturn(Optional.of(certo));

        service.processar(100L);

        assertThat(certo.getStatus()).isEqualTo(StatusPedido.CHARGEBACK);
        assertThat(outro.getStatus()).isEqualTo(StatusPedido.APROVADO);
        verify(avisos).pedidoContestado(certo, StatusPedido.CHARGEBACK);
    }

    @Test
    @DisplayName("aluno com senha: confirmação sem link; acesso já concedido não manda de novo")
    void alunoComSenha() {
        evento(dados(StatusPedido.APROVADO, "codigo", "ana@celke.uno"), StatusEvento.RECEBIDO);
        Pedido pedido = pedidoDoCheckout(StatusPedido.CRIADO);
        when(pedidos.travarPorCodigo("codigo")).thenReturn(Optional.of(pedido));
        when(ofertas.findById(20L)).thenReturn(Optional.of(oferta));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(usuario(7, "ana@celke.uno", "hash", Papel.ALUNO)));
        when(matriculaPagamentoService.conceder(any())).thenReturn(new Concessao(50L, true));
        when(conteudoTurmaService.turma(3L)).thenReturn(Optional.empty());

        service.processar(100L);

        verify(recuperacaoSenhaService, never()).linkCriarSenha(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variaveis = ArgumentCaptor.forClass(Map.class);
        verify(emailService).enviar(eq("ana@celke.uno"), anyString(), anyString(), variaveis.capture());
        assertThat(variaveis.getValue()).doesNotContainKey("linkSenha");
    }

    @Test
    @DisplayName("desistência: evento em erro e aviso ao admin")
    void falhar() {
        EventoRecebido evento = BeanUtils.instantiateClass(EventoRecebido.class);
        ReflectionTestUtils.setField(evento, "id", 100L);
        ReflectionTestUtils.setField(evento, "tipoOriginal", "PURCHASE_APPROVED");
        when(eventos.findById(100L)).thenReturn(Optional.of(evento));

        service.falhar(100L, "banco fora");

        assertThat(evento.getStatus()).isEqualTo(StatusEvento.ERRO);
        verify(avisos).eventoComErro(100L, "PURCHASE_APPROVED", "banco fora");
    }

    @Test
    @DisplayName("suspensão por atraso cancela a matrícula ativa e registra no histórico; sem matrícula ativa, nada")
    void suspenderPorAtraso() {
        Pedido atrasado = pedidoDoCheckout(StatusPedido.ATRASADO);
        atrasado.vincularMatricula(50L, AGORA);
        when(pedidos.travar(30L)).thenReturn(Optional.of(atrasado));
        when(matriculaPagamentoService.ativa(50L)).thenReturn(true, false);

        service.suspenderPorAtraso(30L);
        service.suspenderPorAtraso(30L);

        verify(matriculaPagamentoService).cancelar(50L);
        verify(historico).save(any(PedidoHistorico.class));
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/application/ReconciliacaoServiceTest.java`:

`````java
package br.com.celke.cursos.pagamento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.pagamento.domain.Capacidade;
import br.com.celke.cursos.pagamento.domain.ConfiguracaoGateway;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.Gateway;
import br.com.celke.cursos.pagamento.domain.GatewayPagamento;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import br.com.celke.cursos.pagamento.infra.GatewayRepository;
import br.com.celke.cursos.pagamento.infra.PedidoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class ReconciliacaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private GatewayRepository gateways;

    @Mock
    private PedidoRepository pedidos;

    @Mock
    private GatewayPagamento implementacao;

    @Mock
    private ConfiguracaoGatewayService configuracoes;

    @Mock
    private RegistroEventos registro;

    private ReconciliacaoService service;
    private Gateway gateway;
    private final ConfiguracaoGateway configuracao = new ConfiguracaoGateway(1L, TipoGateway.HOTMART, Map.of());

    @BeforeEach
    void setUp() {
        when(implementacao.tipo()).thenReturn(TipoGateway.HOTMART);
        var props = new PagamentosProperties(
                null,
                DataSize.ofKilobytes(256),
                600,
                Duration.ofDays(180),
                false,
                null,
                new PagamentosProperties.Reconciliacao(
                        true, Duration.ofHours(24), Duration.ofDays(3), Duration.ofDays(60)));
        service = new ReconciliacaoService(
                gateways,
                pedidos,
                new GatewaysRegistrados(List.of(implementacao)),
                configuracoes,
                registro,
                props,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        gateway = new Gateway(TipoGateway.HOTMART, "Hotmart", "token", AGORA);
        ReflectionTestUtils.setField(gateway, "id", 1L);
        when(gateways.findAllByOrderById()).thenReturn(List.of(gateway));
    }

    private static Pedido pedido(StatusPedido status, String transacao) {
        Oferta oferta = new Oferta(3L, 1L, new Oferta.DadosOferta("p", "o", null, "t", 1, "BRL", null), AGORA);
        ReflectionTestUtils.setField(oferta, "id", 20L);
        Pedido pedido = Pedido.doCheckout(oferta, 7L, "ana@celke.uno", null, AGORA);
        pedido.vincularTransacao(transacao, AGORA);
        ReflectionTestUtils.setField(pedido, "status", status);
        return pedido;
    }

    private static EventoPagamento situacao(StatusPedido status) {
        return new EventoPagamento(
                "reconciliacao:HP1:" + status,
                "RECONCILIACAO",
                AGORA,
                "HP1",
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("consulta pendentes e pagos sem notificação; grava só quando o status mudou")
    void gravaQuandoMudou() {
        when(implementacao.capacidades()).thenReturn(Set.of(Capacidade.CONSULTA_API));
        when(configuracoes.ler(gateway)).thenReturn(configuracao);
        Pedido pendente = pedido(StatusPedido.AGUARDANDO_PAGAMENTO, "HP1");
        Pedido pago = pedido(StatusPedido.APROVADO, "HP2");
        Pedido falha = pedido(StatusPedido.APROVADO, "HP3");
        when(pedidos.paraReconciliar(
                        eq(1L),
                        eq(Set.of(StatusPedido.CRIADO, StatusPedido.AGUARDANDO_PAGAMENTO)),
                        eq(AGORA.minus(Duration.ofHours(24))),
                        eq(AGORA.minus(Duration.ofDays(60))),
                        any()))
                .thenReturn(List.of(pendente));
        when(pedidos.paraReconciliar(
                        eq(1L),
                        eq(Set.of(StatusPedido.APROVADO, StatusPedido.ATRASADO, StatusPedido.EM_DISPUTA)),
                        eq(AGORA.minus(Duration.ofDays(3))),
                        eq(AGORA.minus(Duration.ofDays(60))),
                        any()))
                .thenReturn(List.of(pago, falha));
        EventoPagamento aprovado = situacao(StatusPedido.APROVADO);
        when(implementacao.consultar("HP1", configuracao)).thenReturn(Optional.of(aprovado));
        when(implementacao.consultar("HP2", configuracao)).thenReturn(Optional.of(situacao(StatusPedido.APROVADO)));
        when(implementacao.consultar("HP3", configuracao)).thenThrow(new IllegalStateException("rede"));
        when(registro.registrar(1L, aprovado, null, AGORA)).thenReturn(Optional.of(77L));

        assertThat(service.reconciliar()).isEqualTo(1);
    }

    @Test
    @DisplayName("gateway sem consulta pela API ou com segredos ilegíveis é pulado")
    void pulaGateway() {
        when(implementacao.capacidades()).thenReturn(Set.of(Capacidade.CHECKOUT_REDIRECIONAMENTO));
        assertThat(service.reconciliar()).isZero();

        when(implementacao.capacidades()).thenReturn(Set.of(Capacidade.CONSULTA_API));
        when(configuracoes.ler(gateway)).thenThrow(new IllegalStateException("PAGAMENTOS_CHAVE não está definida."));
        assertThat(service.reconciliar()).isZero();
        verify(pedidos, never()).paraReconciliar(any(), any(), any(), any(), any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/domain/TransicoesPedidoTest.java`:

`````java
package br.com.celke.cursos.pagamento.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.pagamento.domain.TransicoesPedido.Decisao;
import br.com.celke.cursos.pagamento.domain.TransicoesPedido.EfeitoMatricula;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class TransicoesPedidoTest {

    private static final Instant ULTIMO = Instant.parse("2026-03-10T12:00:00Z");
    private static final TransicoesPedido TRANSICOES = new TransicoesPedido(false);

    /** Cada linha da tabela: de → para, permitido ou não. */
    @ParameterizedTest(name = "{0} → {1}: {2}")
    @CsvSource({
        "CRIADO,               AGUARDANDO_PAGAMENTO, true",
        "CRIADO,               APROVADO,             true",
        "CRIADO,               CONCLUIDO,            true",
        "CRIADO,               EM_DISPUTA,           false",
        "CRIADO,               ATRASADO,             false",
        "CRIADO,               REEMBOLSADO,          true",
        "CRIADO,               CHARGEBACK,           true",
        "CRIADO,               CANCELADO,            true",
        "CRIADO,               EXPIRADO,             true",
        "AGUARDANDO_PAGAMENTO, CRIADO,               false",
        "AGUARDANDO_PAGAMENTO, APROVADO,             true",
        "AGUARDANDO_PAGAMENTO, CONCLUIDO,            true",
        "AGUARDANDO_PAGAMENTO, EXPIRADO,             true",
        "AGUARDANDO_PAGAMENTO, CANCELADO,            true",
        "AGUARDANDO_PAGAMENTO, EM_DISPUTA,           false",
        "APROVADO,             AGUARDANDO_PAGAMENTO, false",
        "APROVADO,             CONCLUIDO,            true",
        "APROVADO,             EM_DISPUTA,           true",
        "APROVADO,             ATRASADO,             true",
        "APROVADO,             REEMBOLSADO,          true",
        "APROVADO,             CHARGEBACK,           true",
        "APROVADO,             CANCELADO,            true",
        "APROVADO,             EXPIRADO,             false",
        "CONCLUIDO,            APROVADO,             false",
        "CONCLUIDO,            EM_DISPUTA,           true",
        "CONCLUIDO,            ATRASADO,             true",
        "CONCLUIDO,            REEMBOLSADO,          true",
        "CONCLUIDO,            CHARGEBACK,           true",
        "EM_DISPUTA,           APROVADO,             true",
        "EM_DISPUTA,           CONCLUIDO,            true",
        "EM_DISPUTA,           REEMBOLSADO,          true",
        "EM_DISPUTA,           ATRASADO,             false",
        "ATRASADO,             APROVADO,             true",
        "ATRASADO,             EM_DISPUTA,           true",
        "ATRASADO,             CANCELADO,            true",
        "REEMBOLSADO,          APROVADO,             false",
        "REEMBOLSADO,          CONCLUIDO,            false",
        "REEMBOLSADO,          CANCELADO,            false",
        "REEMBOLSADO,          CHARGEBACK,           true",
        "CHARGEBACK,           APROVADO,             false",
        "CHARGEBACK,           REEMBOLSADO,          false",
        "CANCELADO,            APROVADO,             false",
        "CANCELADO,            REEMBOLSADO,          true",
        "EXPIRADO,             APROVADO,             true",
        "EXPIRADO,             AGUARDANDO_PAGAMENTO, false",
    })
    void tabela(StatusPedido de, StatusPedido para, boolean permitido) {
        Decisao decisao = TRANSICOES.decidir(de, para, null, ULTIMO);

        assertThat(decisao.aplica()).isEqualTo(permitido);
        if (!permitido) {
            assertThat(decisao.motivo()).isEqualTo("Mudança de " + de + " para " + para + " não é permitida.");
            assertThat(decisao.efeito()).isEqualTo(EfeitoMatricula.NENHUM);
        }
    }

    static Stream<Arguments> todosOsPares() {
        return Arrays.stream(StatusPedido.values())
                .flatMap(de -> Arrays.stream(StatusPedido.values()).map(para -> Arguments.of(de, para)));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("todosOsPares")
    @DisplayName("toda combinação segue a tabela de origens; o mesmo status nunca muda nada")
    void todasAsCombinacoes(StatusPedido de, StatusPedido para) {
        Decisao decisao = TRANSICOES.decidir(de, para, null, ULTIMO);

        assertThat(decisao.aplica())
                .isEqualTo(de != para && TransicoesPedido.ORIGENS.get(para).contains(de));
        if (de == para) {
            assertThat(decisao.motivo()).isEqualTo("O pedido já está " + de + ".");
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "um segundo antes da última mudança, 2026-03-10T11:59:59Z, false",
        "no mesmo instante,                  2026-03-10T12:00:00Z, true",
        "um segundo depois,                  2026-03-10T12:00:01Z, true",
    })
    @DisplayName("evento mais antigo que a última mudança não desfaz a mais nova")
    void foraDeOrdem(String caso, Instant ocorridoEm, boolean aplica) {
        Decisao decisao = TRANSICOES.decidir(StatusPedido.APROVADO, StatusPedido.CANCELADO, ULTIMO, ocorridoEm);

        assertThat(decisao.aplica()).isEqualTo(aplica);
        if (!aplica) {
            assertThat(decisao.motivo()).isEqualTo("Evento anterior à última mudança do pedido.");
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"REEMBOLSADO", "CHARGEBACK"})
    @DisplayName("reembolso e chargeback valem mesmo chegando fora de ordem")
    void reembolsoSempreVale(StatusPedido novo) {
        Decisao decisao = TRANSICOES.decidir(StatusPedido.CONCLUIDO, novo, ULTIMO, ULTIMO.minusSeconds(3600));

        assertThat(decisao.aplica()).isTrue();
        assertThat(decisao.efeito()).isEqualTo(EfeitoMatricula.CANCELAR);
    }

    @Test
    @DisplayName("sem a data do evento ou sem mudança anterior, só a tabela decide")
    void semDatas() {
        assertThat(TRANSICOES
                        .decidir(StatusPedido.CRIADO, StatusPedido.APROVADO, ULTIMO, null)
                        .aplica())
                .isTrue();
        assertThat(TRANSICOES
                        .decidir(StatusPedido.CRIADO, StatusPedido.APROVADO, null, null)
                        .aplica())
                .isTrue();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "CRIADO,               NENHUM",
        "AGUARDANDO_PAGAMENTO, NENHUM",
        "APROVADO,             CONCEDER",
        "CONCLUIDO,            CONCEDER",
        "EM_DISPUTA,           NENHUM",
        "ATRASADO,             NENHUM",
        "REEMBOLSADO,          CANCELAR",
        "CHARGEBACK,           CANCELAR",
        "CANCELADO,            CANCELAR",
        "EXPIRADO,             NENHUM",
    })
    @DisplayName("efeito de cada status na matrícula")
    void efeitos(StatusPedido status, EfeitoMatricula efeito) {
        assertThat(TRANSICOES.efeito(status)).isEqualTo(efeito);
    }

    @Test
    @DisplayName("configurado para suspender, disputa tira o acesso e a aprovação devolve")
    void disputaSuspende() {
        TransicoesPedido suspende = new TransicoesPedido(true);

        assertThat(suspende.decidir(StatusPedido.APROVADO, StatusPedido.EM_DISPUTA, null, ULTIMO)
                        .efeito())
                .isEqualTo(EfeitoMatricula.CANCELAR);
        assertThat(suspende.decidir(StatusPedido.EM_DISPUTA, StatusPedido.APROVADO, null, ULTIMO)
                        .efeito())
                .isEqualTo(EfeitoMatricula.CONCEDER);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/infra/hotmart/ClienteApiHotmartTest.java`:

`````java
package br.com.celke.cursos.pagamento.infra.hotmart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.Credenciais;
import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.FalhaApiHotmart;
import br.com.celke.cursos.support.fixtures.RelogioAjustavel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClienteApiHotmartTest {

    private static final String TOKEN_URL = "https://token.hotmart.test/security/oauth/token";
    private static final HotmartProperties PROPS = new HotmartProperties(
            TOKEN_URL, "https://api.hotmart.test/v1", "https://sandbox.hotmart.test/v1", Duration.ofSeconds(5));
    private static final Credenciais CREDENCIAIS = new Credenciais("id", "segredo", "Basic YmFzaWM=", false);

    private final RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-03-10T12:00:00Z"));
    private MockRestServiceServer servidor;
    private ClienteApiHotmart cliente;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new ClienteApiHotmart(builder.build(), PROPS, relogio);
    }

    private void esperarToken(int expiraEm) {
        servidor.expect(
                        once(),
                        requestTo(TOKEN_URL + "?grant_type=client_credentials&client_id=id&client_secret=segredo"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic YmFzaWM="))
                .andRespond(withSuccess(
                        "{\"access_token\":\"tok-%d\",\"expires_in\":%d}".formatted(expiraEm, expiraEm),
                        MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("pede o token com o Basic (sem repetir o prefixo) e guarda até 5 minutos antes de vencer")
    void tokenEmCache() {
        esperarToken(3600);

        assertThat(cliente.token(CREDENCIAIS)).isEqualTo("tok-3600");
        relogio.avancar(Duration.ofMinutes(54).plusSeconds(59));
        assertThat(cliente.token(CREDENCIAIS)).isEqualTo("tok-3600");
        servidor.verify();

        servidor.reset();
        esperarToken(1800);
        relogio.avancar(Duration.ofSeconds(1));
        assertThat(cliente.token(CREDENCIAIS)).isEqualTo("tok-1800");
        servidor.verify();
    }

    @Test
    @DisplayName("credencial recusada vira falha com o status")
    void recusada() {
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith(TOKEN_URL)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> cliente.token(CREDENCIAIS))
                .isInstanceOf(FalhaApiHotmart.class)
                .hasMessage("A Hotmart recusou a chamada (HTTP 401).")
                .extracting(e -> ((FalhaApiHotmart) e).getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("resposta sem token é falha")
    void semToken() {
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith(TOKEN_URL)))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.token(CREDENCIAIS))
                .isInstanceOf(FalhaApiHotmart.class)
                .hasMessage("A Hotmart não devolveu o token de acesso.");
    }

    @Test
    @DisplayName("histórico de vendas pela transação, com e sem filtro de status, e no sandbox")
    void venda() {
        esperarToken(3600);
        servidor.expect(requestTo("https://api.hotmart.test/v1/sales/history?transaction=HP1"))
                .andExpect(header("Authorization", "Bearer tok-3600"))
                .andRespond(withSuccess(
                        "{\"items\":[{\"purchase\":{\"status\":\"APPROVED\"}}]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(
                        "https://api.hotmart.test/v1/sales/history?transaction=HP1&transaction_status=REFUNDED"))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        assertThat(cliente.venda(CREDENCIAIS, "HP1", null))
                .hasValueSatisfying(
                        item -> assertThat(item.path("purchase").path("status").asString())
                                .isEqualTo("APPROVED"));
        assertThat(cliente.venda(CREDENCIAIS, "HP1", "REFUNDED")).isEmpty();
        servidor.verify();
    }

    @Test
    @DisplayName("no sandbox usa o endereço do sandbox")
    void sandbox() {
        Credenciais sandbox = new Credenciais("id", "segredo", "YmFzaWM=", true);
        esperarToken(3600);
        servidor.expect(requestTo("https://sandbox.hotmart.test/v1/sales/history?transaction=HP1"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(cliente.venda(sandbox, "HP1", null)).isEmpty();
        servidor.verify();
    }

    @Test
    @DisplayName("o cliente HTTP de produção usa o tempo máximo configurado")
    void restClient() {
        assertThat(ClienteApiHotmart.restClient(RestClient.builder(), PROPS)).isNotNull();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/infra/hotmart/FixturesHotmart.java`:

`````java
package br.com.celke.cursos.pagamento.infra.hotmart;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Corpos do webhook 2.0.0 em {@code src/test/resources/pagamentos/hotmart} (também usados pelos ITs). */
public final class FixturesHotmart {

    /** Marcador do código do pedido no {@code xcod} das fixtures. */
    public static final String XCOD = "XCOD_DO_PEDIDO";

    private FixturesHotmart() {}

    public static String corpo(String evento) {
        try (InputStream entrada =
                FixturesHotmart.class.getResourceAsStream("/pagamentos/hotmart/" + evento + ".json")) {
            if (entrada == null) {
                throw new IllegalArgumentException("Fixture inexistente: " + evento);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/infra/hotmart/GatewayHotmartTest.java`:

`````java
package br.com.celke.cursos.pagamento.infra.hotmart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.pagamento.domain.Checkout;
import br.com.celke.cursos.pagamento.domain.Comprador;
import br.com.celke.cursos.pagamento.domain.ConfiguracaoGateway;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.NotificacaoRecebida;
import br.com.celke.cursos.pagamento.domain.Oferta;
import br.com.celke.cursos.pagamento.domain.Pedido;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import br.com.celke.cursos.pagamento.domain.TipoGateway;
import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.Credenciais;
import br.com.celke.cursos.pagamento.infra.hotmart.ClienteApiHotmart.FalhaApiHotmart;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GatewayHotmartTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String HOTTOK = "hottok-de-teste-123";

    @Mock
    private ClienteApiHotmart api;

    private final JsonMapper json = JsonMapper.builder().build();
    private GatewayHotmart gateway;

    @BeforeEach
    void setUp() {
        gateway = new GatewayHotmart(new InterpretadorHotmart(json), api, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static ConfiguracaoGateway config(String... chavesEValores) {
        Map<String, String> valores = new HashMap<>();
        valores.put("hottok", HOTTOK);
        for (int i = 0; i < chavesEValores.length; i += 2) {
            valores.put(chavesEValores[i], chavesEValores[i + 1]);
        }
        return new ConfiguracaoGateway(1L, TipoGateway.HOTMART, valores);
    }

    private static NotificacaoRecebida notificacao(Map<String, String> cabecalhos) {
        return new NotificacaoRecebida(cabecalhos, "{}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("declara os campos do painel e o que sabe fazer")
    void descricao() {
        assertThat(gateway.tipo()).isEqualTo(TipoGateway.HOTMART);
        assertThat(gateway.nome()).isEqualTo("Hotmart");
        assertThat(gateway.camposConfiguracao())
                .extracting(c -> c.chave() + (c.secreto() ? "*" : ""))
                .containsExactly("hottok*", "ambiente", "urlCheckoutBase", "clientId", "clientSecret*", "basic*");
        assertThat(gateway.capacidades()).hasSize(3);
    }

    @Nested
    class Autenticar {

        @Test
        @DisplayName("hottok certo no cabeçalho, com qualquer caixa no nome")
        void hottokCerto() {
            assertThat(gateway.autenticar(notificacao(Map.of("x-hotmart-hottok", HOTTOK)), config()))
                    .isTrue();
            assertThat(gateway.autenticar(notificacao(Map.of("X-HOTMART-HOTTOK", " " + HOTTOK + " ")), config()))
                    .isTrue();
        }

        @Test
        @DisplayName("hottok errado, parecido, ausente ou não configurado é recusado")
        void hottokErrado() {
            assertThat(gateway.autenticar(notificacao(Map.of("X-HOTMART-HOTTOK", "outro")), config()))
                    .isFalse();
            assertThat(gateway.autenticar(notificacao(Map.of("X-HOTMART-HOTTOK", HOTTOK + "4")), config()))
                    .isFalse();
            assertThat(gateway.autenticar(notificacao(Map.of()), config())).isFalse();
            assertThat(gateway.autenticar(
                            notificacao(Map.of("X-HOTMART-HOTTOK", HOTTOK)),
                            new ConfiguracaoGateway(1L, TipoGateway.HOTMART, Map.of())))
                    .isFalse();
        }
    }

    @Nested
    class IniciarCheckout {

        private Oferta oferta(String codigoCheckout) {
            Oferta oferta = new Oferta(
                    5L,
                    1L,
                    new Oferta.DadosOferta("4774438", "k2 pasun0", codigoCheckout, "À vista", 19_700, "BRL", null),
                    AGORA);
            ReflectionTestUtils.setField(oferta, "id", 9L);
            return oferta;
        }

        @Test
        @DisplayName("redireciona para o checkout com oferta, e-mail, nome e o código do pedido codificados")
        void url() {
            Oferta oferta = oferta("X12345678Y");
            Pedido pedido = Pedido.doCheckout(oferta, 7L, "ana+teste@celke.uno", "Ana Maria", AGORA);

            Checkout checkout = gateway.iniciarCheckout(
                    pedido, oferta, new Comprador("ana+teste@celke.uno", "Ana & Maria"), config());

            assertThat(checkout)
                    .isEqualTo(new Checkout.Redirecionamento("https://pay.hotmart.com/X12345678Y?off=k2+pasun0"
                            + "&email=ana%2Bteste%40celke.uno&name=Ana+%26+Maria&xcod=" + pedido.getCodigo()
                            + "&sck=plataforma"));
        }

        @Test
        @DisplayName("sem código de checkout usa o id do produto; endereço configurado sem a barra final; sem nome")
        void enderecoConfigurado() {
            Oferta oferta = oferta(null);
            Pedido pedido = Pedido.doCheckout(oferta, 7L, "ana@celke.uno", null, AGORA);

            Checkout checkout = gateway.iniciarCheckout(
                    pedido,
                    oferta,
                    new Comprador("ana@celke.uno", null),
                    config("urlCheckoutBase", "https://sandbox.hotmart.com/"));

            assertThat(((Checkout.Redirecionamento) checkout).url())
                    .startsWith("https://sandbox.hotmart.com/4774438?off=")
                    .doesNotContain("&name=");
        }
    }

    @Nested
    class Testar {

        @Test
        @DisplayName("só o hottok: ok, avisando que a reconciliação fica desligada")
        void soHottok() {
            var resultado = gateway.testar(config());

            assertThat(resultado.ok()).isTrue();
            assertThat(resultado.mensagem()).startsWith("Hottok gravado.");
            verify(api, never()).token(any());
        }

        @Test
        @DisplayName("sem hottok ou com credenciais da API pela metade: falha")
        void incompleto() {
            assertThat(gateway.testar(new ConfiguracaoGateway(1L, TipoGateway.HOTMART, Map.of())))
                    .isEqualTo(new br.com.celke.cursos.pagamento.domain.ResultadoTeste(false, "Informe o hottok."));
            assertThat(gateway.testar(config("clientId", "id")).mensagem())
                    .isEqualTo("Informe o Client ID, o Client Secret e o Basic juntos.");
        }

        @Test
        @DisplayName("credenciais aceitas pela Hotmart")
        void aceitas() {
            when(api.token(new Credenciais("id", "segredo", "YmFzaWM=", true))).thenReturn("token");

            var resultado = gateway.testar(config(
                    "clientId", "id", "clientSecret", "segredo", "basic", "Basic YmFzaWM=", "ambiente", "SANDBOX"));

            assertThat(resultado.ok()).isTrue();
            assertThat(resultado.mensagem()).isEqualTo("Credenciais da API conferidas com a Hotmart.");
        }

        @Test
        @DisplayName("credenciais recusadas: falha com o motivo")
        void recusadas() {
            when(api.token(any())).thenThrow(new FalhaApiHotmart("A Hotmart recusou a chamada (HTTP 401).", 401));

            var resultado = gateway.testar(config("clientId", "id", "clientSecret", "x", "basic", "y"));

            assertThat(resultado.ok()).isFalse();
            assertThat(resultado.mensagem())
                    .isEqualTo(
                            "A Hotmart recusou a chamada (HTTP 401). Confira o Client ID, o Client Secret e o Basic.");
        }
    }

    @Nested
    class Consultar {

        private final ConfiguracaoGateway comApi = config("clientId", "id", "clientSecret", "x", "basic", "y");

        @Test
        @DisplayName("sem credenciais da API não consulta")
        void semCredenciais() {
            assertThat(gateway.consultar("HP1", config())).isEmpty();
            verify(api, never()).venda(any(), any(), any());
        }

        @Test
        @DisplayName("venda aprovada vira evento sintético com o status interno")
        void aprovada() {
            when(api.venda(any(), eq("HP1"), isNull())).thenReturn(Optional.of(json.readTree("""
                    {"product":{"id":4774438},"buyer":{"email":"ANA@celke.uno","name":"Ana"},
                     "purchase":{"transaction":"HP1","status":"APPROVED","offer":{"code":"k2"},
                       "price":{"value":197.0,"currency_code":"BRL"},"payment":{"type":"PIX"},
                       "tracking":{"external_code":"COD"}}}
                    """)));

            EventoPagamento evento = gateway.consultar("HP1", comApi).orElseThrow();

            assertThat(evento)
                    .isEqualTo(new EventoPagamento(
                            "reconciliacao:HP1:APPROVED",
                            "RECONCILIACAO_APPROVED",
                            AGORA,
                            "HP1",
                            StatusPedido.APROVADO,
                            "COD",
                            "4774438",
                            "k2",
                            new EventoPagamento.CompradorEvento("ana@celke.uno", "Ana", null),
                            19_700L,
                            "BRL",
                            FormaPagamento.PIX,
                            null));
        }

        @Test
        @DisplayName("fora das aprovadas, procura status a status até achar")
        void reembolsada() {
            when(api.venda(any(), eq("HP1"), isNull())).thenReturn(Optional.empty());
            when(api.venda(any(), eq("HP1"), eq("REFUNDED"))).thenReturn(Optional.of(json.readTree("""
                    {"purchase":{"status":"REFUNDED"}}
                    """)));

            EventoPagamento evento = gateway.consultar("HP1", comApi).orElseThrow();

            assertThat(evento.statusPedido()).isEqualTo(StatusPedido.REEMBOLSADO);
            assertThat(evento.comprador()).isNull();
        }

        @Test
        @DisplayName("transação que não aparece em nenhum status: vazio")
        void naoEncontrada() {
            when(api.venda(any(), eq("HP1"), any())).thenReturn(Optional.empty());

            assertThat(gateway.consultar("HP1", comApi)).isEmpty();
            verify(api).venda(any(), eq("HP1"), eq("PRINTED_BILLET"));
        }
    }

    @Test
    @DisplayName("interpreta a notificação pelo corpo")
    void interpretar() {
        var eventos = gateway.interpretar(new NotificacaoRecebida(
                Map.of(), FixturesHotmart.corpo("PURCHASE_REFUNDED").getBytes(StandardCharsets.UTF_8)));

        assertThat(eventos)
                .singleElement()
                .extracting(EventoPagamento::statusPedido)
                .isEqualTo(StatusPedido.REEMBOLSADO);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/pagamento/infra/hotmart/InterpretadorHotmartTest.java`:

`````java
package br.com.celke.cursos.pagamento.infra.hotmart;

import static br.com.celke.cursos.pagamento.infra.hotmart.FixturesHotmart.corpo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.pagamento.domain.EventoPagamento;
import br.com.celke.cursos.pagamento.domain.NotificacaoInvalidaException;
import br.com.celke.cursos.pagamento.domain.StatusPedido;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class InterpretadorHotmartTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final InterpretadorHotmart interpretador = new InterpretadorHotmart(json);

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "PURCHASE_APPROVED,       APROVADO,             CARTAO",
        "PURCHASE_COMPLETE,       CONCLUIDO,            CARTAO",
        "PURCHASE_CANCELED,       CANCELADO,            CARTAO",
        "PURCHASE_REFUNDED,       REEMBOLSADO,          CARTAO",
        "PURCHASE_CHARGEBACK,     CHARGEBACK,           CARTAO",
        "PURCHASE_BILLET_PRINTED, AGUARDANDO_PAGAMENTO, BOLETO",
        "PURCHASE_PROTEST,        EM_DISPUTA,           CARTAO",
        "PURCHASE_DELAYED,        ATRASADO,             CARTAO",
        "PURCHASE_EXPIRED,        EXPIRADO,             CARTAO",
    })
    @DisplayName("cada fixture de compra vira o status interno, com transação, xcod, produto, oferta e comprador")
    void fixturesDeCompra(String evento, StatusPedido status, FormaPagamento forma) {
        EventoPagamento lido = interpretador.interpretar(corpo(evento));

        assertThat(lido.tipoOriginal()).isEqualTo(evento);
        assertThat(lido.statusPedido()).isEqualTo(status);
        assertThat(lido.idExterno()).startsWith("6c4e6a5b-");
        assertThat(lido.transacao()).isEqualTo("HP17715690036014");
        assertThat(lido.codigoPedido()).isEqualTo(FixturesHotmart.XCOD);
        assertThat(lido.produtoExterno()).isEqualTo("4774438");
        assertThat(lido.ofertaExterna()).isEqualTo("k2pasun0");
        assertThat(lido.comprador().email()).isEqualTo("comprador.teste@example.com");
        assertThat(lido.comprador().nome()).isEqualTo("Comprador de Teste");
        assertThat(lido.comprador().documentoMascarado()).isEqualTo("***09");
        assertThat(lido.valorCentavos()).isEqualTo(19_700L);
        assertThat(lido.moeda()).isEqualTo("BRL");
        assertThat(lido.formaPagamento()).isEqualTo(forma);
        assertThat(lido.ocorridoEm()).isAfter(Instant.parse("2025-10-22T00:00:00Z"));
    }

    @Test
    @DisplayName("assinatura vem quando a venda é recorrente")
    void assinatura() {
        EventoPagamento lido = interpretador.interpretar(corpo("PURCHASE_DELAYED"));

        assertThat(lido.assinatura()).isEqualTo(new EventoPagamento.Assinatura("I9OT62C3", "DELAYED"));
        assertThat(interpretador.interpretar(corpo("PURCHASE_APPROVED")).assinatura())
                .isNull();
    }

    @Test
    @DisplayName("afiliado e coprodutor: sem comprador e sem preço, data em texto")
    void afiliado() {
        EventoPagamento lido = interpretador.interpretar(corpo("PURCHASE_APPROVED_AFILIADO"));

        assertThat(lido.statusPedido()).isEqualTo(StatusPedido.APROVADO);
        assertThat(lido.comprador()).isNull();
        assertThat(lido.valorCentavos()).isNull();
        assertThat(lido.moeda()).isNull();
        assertThat(lido.formaPagamento()).isEqualTo(FormaPagamento.OUTRO);
        assertThat(lido.ocorridoEm()).isEqualTo(Instant.ofEpochMilli(1_761_140_001_000L));
    }

    @Test
    @DisplayName("evento que não é de compra é lido, mas não muda o pedido")
    void eventoNaoTratado() {
        EventoPagamento lido = interpretador.interpretar(corpo("SUBSCRIPTION_CANCELLATION"));

        assertThat(lido.tipoOriginal()).isEqualTo("SUBSCRIPTION_CANCELLATION");
        assertThat(lido.statusPedido()).isNull();
        assertThat(lido.transacao()).isNull();
        assertThat(lido.comprador()).isNull();
    }

    @ParameterizedTest(name = "{0} / {1} → {2}")
    @CsvSource(
            value = {
                "PURCHASE_NOVO_EVENTO, APPROVED,           APROVADO",
                "PURCHASE_NOVO_EVENTO, under_analisys,     AGUARDANDO_PAGAMENTO",
                "PURCHASE_NOVO_EVENTO, PARTIALLY_REFUNDED, REEMBOLSADO",
                "PURCHASE_NOVO_EVENTO, STATUS_INVENTADO,   nulo",
                "PURCHASE_NOVO_EVENTO, nulo,               nulo",
                "CLUB_FIRST_ACCESS,    APPROVED,           nulo",
                "PURCHASE_APPROVED,    CANCELLED,          APROVADO",
            },
            nullValues = "nulo")
    @DisplayName("evento de compra desconhecido usa o purchase.status; status desconhecido não quebra")
    void statusPorEventoOuCompra(String evento, String statusCompra, StatusPedido esperado) {
        assertThat(InterpretadorHotmart.status(evento, statusCompra)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
            value = {
                "CREDIT_CARD, CARTAO",
                "credit_card, CARTAO",
                "PIX,         PIX",
                "BILLET,      BOLETO",
                "PAYPAL,      OUTRO",
                "GOOGLE_PAY,  OUTRO",
                "nulo,        nulo",
            },
            nullValues = "nulo")
    void forma(String tipo, FormaPagamento esperada) {
        assertThat(InterpretadorHotmart.forma(tipo)).isEqualTo(esperada);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            value = {
                "milissegundos,          1761140000000,             2025-10-22T13:33:20Z",
                "segundos,               1761140000,                2025-10-22T13:33:20Z",
                "ISO com fuso,           2025-10-22T10:33:20-03:00, 2025-10-22T13:33:20Z",
                "ISO em UTC,             2025-10-22T13:33:20Z,      2025-10-22T13:33:20Z",
                "texto qualquer,         ontem,                     nulo",
            },
            nullValues = "nulo")
    @DisplayName("data em número ou em texto")
    void datas(String caso, String valor, Instant esperado) {
        assertThat(InterpretadorHotmart.instante(JsonNodeFactory.instance.stringNode(valor)))
                .isEqualTo(esperado);
    }

    @Test
    @DisplayName("data como número JSON e data ausente")
    void dataNumero() {
        assertThat(InterpretadorHotmart.instante(JsonNodeFactory.instance.numberNode(1_761_140_000_000L)))
                .isEqualTo(Instant.parse("2025-10-22T13:33:20Z"));
        assertThat(InterpretadorHotmart.instante(JsonNodeFactory.instance.missingNode()))
                .isNull();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
            value = {"197, 19700", "197.5, 19750", "0.005, 1", "19.994, 1999", "abc, nulo"},
            nullValues = "nulo")
    @DisplayName("valor para centavos, arredondando a meia unidade para cima")
    void centavos(String valor, Long esperado) {
        assertThat(InterpretadorHotmart.centavos(JsonNodeFactory.instance.stringNode(valor)))
                .isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "não é JSON        | {quebrado              | O corpo não é um JSON válido.",
                "lista             | []                     | O corpo não é um objeto JSON.",
                "sem id            | {\"event\":\"PURCHASE_APPROVED\"} | A notificação não tem id.",
                "sem evento        | {\"id\":\"1\"}         | A notificação não tem o nome do evento.",
                "versão 1          | {\"id\":\"1\",\"event\":\"x\",\"version\":\"1.0.0\"} | Webhook na versão 1.0.0: configure a versão 2.0.0 na Hotmart.",
            })
    @DisplayName("corpo fora do formato 2.0.0 é recusado com o motivo")
    void invalido(String caso, String corpo, String mensagem) {
        assertThatThrownBy(() -> interpretador.interpretar(corpo))
                .isInstanceOf(NotificacaoInvalidaException.class)
                .hasMessage(mensagem);
    }
}
`````

`backend/src/test/resources/pagamentos/hotmart/LEIAME.md`:

`````markdown
# Fixtures do webhook 2.0.0 da Hotmart

Um arquivo por evento (`<EVENTO>.json`), com dados fictícios. Montados a partir do formato da
documentação oficial; troque pelos corpos recebidos no "Enviar teste" da Hotmart (Ferramentas →
Webhook, versão 2.0.0) sempre que a Hotmart mudar algum campo — sem dados pessoais reais.

- `XCOD_DO_PEDIDO` em `data.purchase.origin.xcod` é trocado pelo código do pedido nos testes.
- `PURCHASE_APPROVED_AFILIADO.json`: como chega ao afiliado/coprodutor (sem `buyer`, sem `price`,
  `creation_date` em texto).
`````

`backend/src/test/resources/pagamentos/hotmart/PURCHASE_APPROVED.json`:

`````json
{
  "id": "6c4e6a5b-0000-4000-8000-000000000001",
  "creation_date": 1761140001000,
  "event": "PURCHASE_APPROVED",
  "version": "2.0.0",
  "data": {
    "product": {
      "id": 4774438,
      "ucode": "0d3b5d0a-5f0e-4bd2-9a4b-6d1f0c2b9e11",
      "name": "Curso de Teste",
      "warranty_date": "2026-11-21T00:00:00Z",
      "has_co_production": false,
      "is_physical_product": false
    },
    "affiliates": [
      {
        "affiliate_code": "Q58388177J",
        "name": "Afiliado de Teste"
      }
    ],
    "buyer": {
      "email": "comprador.teste@example.com",
      "name": "Comprador de Teste",
      "first_name": "Comprador",
      "last_name": "de Teste",
      "checkout_phone": "5511999990000",
      "document": "12345678909",
      "document_type": "CPF",
      "address": {
        "country": "Brasil",
        "country_iso": "BR",
        "zipcode": "01001000"
      }
    },
    "producer": {
      "name": "Produtor de Teste",
      "document": "11222333000181",
      "legal_nature": "Pessoa Jurídica"
    },
    "commissions": [
      {
        "value": 14.95,
        "source": "MARKETPLACE",
        "currency_value": "BRL"
      },
      {
        "value": 182.05,
        "source": "PRODUCER",
        "currency_value": "BRL"
      }
    ],
    "purchase": {
      "approved_date": 1761140000000,
      "full_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "checkout_country": {
        "name": "Brasil",
        "iso": "BR"
      },
      "buyer_ip": "200.100.50.25",
      "origin": {
        "xcod": "XCOD_DO_PEDIDO",
        "sck": "plataforma"
      },
      "original_offer_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "order_date": 1761139900000,
      "status": "APPROVED",
      "transaction": "HP17715690036014",
      "payment": {
        "installments_number": 1,
        "type": "CREDIT_CARD"
      },
      "offer": {
        "code": "k2pasun0"
      },
      "invoice_by": "HOTMART",
      "is_funnel": false,
      "business_model": "I"
    }
  }
}
`````

`backend/src/test/resources/pagamentos/hotmart/PURCHASE_APPROVED_AFILIADO.json`:

`````json
{
  "id": "6c4e6a5b-0000-4000-8000-000000000101",
  "creation_date": "1761140001000",
  "event": "PURCHASE_APPROVED",
  "version": "2.0.0",
  "data": {
    "product": {
      "id": 4774438,
      "ucode": "0d3b5d0a-5f0e-4bd2-9a4b-6d1f0c2b9e11",
      "name": "Curso de Teste",
      "warranty_date": "2026-11-21T00:00:00Z",
      "has_co_production": false,
      "is_physical_product": false
    },
    "affiliates": [
      {
        "affiliate_code": "Q58388177J",
        "name": "Afiliado de Teste"
      }
    ],
    "producer": {
      "name": "Produtor de Teste",
      "document": "11222333000181",
      "legal_nature": "Pessoa Jurídica"
    },
    "commissions": [
      {
        "value": 14.95,
        "source": "MARKETPLACE",
        "currency_value": "BRL"
      },
      {
        "value": 182.05,
        "source": "PRODUCER",
        "currency_value": "BRL"
      }
    ],
    "purchase": {
      "approved_date": 1761140000000,
      "full_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "checkout_country": {
        "name": "Brasil",
        "iso": "BR"
      },
      "buyer_ip": "200.100.50.25",
      "origin": {
        "xcod": "XCOD_DO_PEDIDO",
        "sck": "plataforma"
      },
      "original_offer_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "order_date": 1761139900000,
      "status": "APPROVED",
      "transaction": "HP17715690036014",
      "payment": {
        "installments_number": 1,
        "type": "PAYPAL"
      },
      "offer": {
        "code": "k2pasun0"
      },
      "invoice_by": "HOTMART",
      "is_funnel": false,
      "business_model": "I"
    }
  }
}
`````

`backend/src/test/resources/pagamentos/hotmart/PURCHASE_BILLET_PRINTED.json`:

`````json
{
  "id": "6c4e6a5b-0000-4000-8000-000000000006",
  "creation_date": 1761140006000,
  "event": "PURCHASE_BILLET_PRINTED",
  "version": "2.0.0",
  "data": {
    "product": {
      "id": 4774438,
      "ucode": "0d3b5d0a-5f0e-4bd2-9a4b-6d1f0c2b9e11",
      "name": "Curso de Teste",
      "warranty_date": "2026-11-21T00:00:00Z",
      "has_co_production": false,
      "is_physical_product": false
    },
    "affiliates": [
      {
        "affiliate_code": "Q58388177J",
        "name": "Afiliado de Teste"
      }
    ],
    "buyer": {
      "email": "comprador.teste@example.com",
      "name": "Comprador de Teste",
      "first_name": "Comprador",
      "last_name": "de Teste",
      "checkout_phone": "5511999990000",
      "document": "12345678909",
      "document_type": "CPF",
      "address": {
        "country": "Brasil",
        "country_iso": "BR",
        "zipcode": "01001000"
      }
    },
    "producer": {
      "name": "Produtor de Teste",
      "document": "11222333000181",
      "legal_nature": "Pessoa Jurídica"
    },
    "commissions": [
      {
        "value": 14.95,
        "source": "MARKETPLACE",
        "currency_value": "BRL"
      },
      {
        "value": 182.05,
        "source": "PRODUCER",
        "currency_value": "BRL"
      }
    ],
    "purchase": {
      "full_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "checkout_country": {
        "name": "Brasil",
        "iso": "BR"
      },
      "buyer_ip": "200.100.50.25",
      "origin": {
        "xcod": "XCOD_DO_PEDIDO",
        "sck": "plataforma"
      },
      "original_offer_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "order_date": 1761139900000,
      "status": "PRINTED_BILLET",
      "transaction": "HP17715690036014",
      "payment": {
        "installments_number": 1,
        "type": "BILLET"
      },
      "offer": {
        "code": "k2pasun0"
      },
      "invoice_by": "HOTMART",
      "is_funnel": false,
      "business_model": "I"
    }
  }
}
`````

`backend/src/test/resources/pagamentos/hotmart/PURCHASE_CANCELED.json`:

`````json
{
  "id": "6c4e6a5b-0000-4000-8000-000000000003",
  "creation_date": 1761140003000,
  "event": "PURCHASE_CANCELED",
  "version": "2.0.0",
  "data": {
    "product": {
      "id": 4774438,
      "ucode": "0d3b5d0a-5f0e-4bd2-9a4b-6d1f0c2b9e11",
      "name": "Curso de Teste",
      "warranty_date": "2026-11-21T00:00:00Z",
      "has_co_production": false,
      "is_physical_product": false
    },
    "affiliates": [
      {
        "affiliate_code": "Q58388177J",
        "name": "Afiliado de Teste"
      }
    ],
    "buyer": {
      "email": "comprador.teste@example.com",
      "name": "Comprador de Teste",
      "first_name": "Comprador",
      "last_name": "de Teste",
      "checkout_phone": "5511999990000",
      "document": "12345678909",
      "document_type": "CPF",
      "address": {
        "country": "Brasil",
        "country_iso": "BR",
        "zipcode": "01001000"
      }
    },
    "producer": {
      "name": "Produtor de Teste",
      "document": "11222333000181",
      "legal_nature": "Pessoa Jurídica"
    },
    "commissions": [
      {
        "value": 14.95,
        "source": "MARKETPLACE",
        "currency_value": "BRL"
      },
      {
        "value": 182.05,
        "source": "PRODUCER",
        "currency_value": "BRL"
      }
    ],
    "purchase": {
      "approved_date": 1761140000000,
      "full_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "checkout_country": {
        "name": "Brasil",
        "iso": "BR"
      },
      "buyer_ip": "200.100.50.25",
      "origin": {
        "xcod": "XCOD_DO_PEDIDO",
        "sck": "plataforma"
      },
      "original_offer_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "order_date": 1761139900000,
      "status": "CANCELLED",
      "transaction": "HP17715690036014",
      "payment": {
        "installments_number": 1,
        "type": "CREDIT_CARD"
      },
      "offer": {
        "code": "k2pasun0"
      },
      "invoice_by": "HOTMART",
      "is_funnel": false,
      "business_model": "I"
    }
  }
}
`````

`backend/src/test/resources/pagamentos/hotmart/PURCHASE_CHARGEBACK.json`:

`````json
{
  "id": "6c4e6a5b-0000-4000-8000-000000000005",
  "creation_date": 1761140005000,
  "event": "PURCHASE_CHARGEBACK",
  "version": "2.0.0",
  "data": {
    "product": {
      "id": 4774438,
      "ucode": "0d3b5d0a-5f0e-4bd2-9a4b-6d1f0c2b9e11",
      "name": "Curso de Teste",
      "warranty_date": "2026-11-21T00:00:00Z",
      "has_co_production": false,
      "is_physical_product": false
    },
    "affiliates": [
      {
        "affiliate_code": "Q58388177J",
        "name": "Afiliado de Teste"
      }
    ],
    "buyer": {
      "email": "comprador.teste@example.com",
      "name": "Comprador de Teste",
      "first_name": "Comprador",
      "last_name": "de Teste",
      "checkout_phone": "5511999990000",
      "document": "12345678909",
      "document_type": "CPF",
      "address": {
        "country": "Brasil",
        "country_iso": "BR",
        "zipcode": "01001000"
      }
    },
    "producer": {
      "name": "Produtor de Teste",
      "document": "11222333000181",
      "legal_nature": "Pessoa Jurídica"
    },
    "commissions": [
      {
        "value": 14.95,
        "source": "MARKETPLACE",
        "currency_value": "BRL"
      },
      {
        "value": 182.05,
        "source": "PRODUCER",
        "currency_value": "BRL"
      }
    ],
    "purchase": {
      "approved_date": 1761140000000,
      "full_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "checkout_country": {
        "name": "Brasil",
        "iso": "BR"
      },
      "buyer_ip": "200.100.50.25",
      "origin": {
        "xcod": "XCOD_DO_PEDIDO",
        "sck": "plataforma"
      },
      "original_offer_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "order_date": 1761139900000,
      "status": "CHARGEBACK",
      "transaction": "HP17715690036014",
      "payment": {
        "installments_number": 1,
        "type": "CREDIT_CARD"
      },
      "offer": {
        "code": "k2pasun0"
      },
      "invoice_by": "HOTMART",
      "is_funnel": false,
      "business_model": "I"
    }
  }
}
`````

`backend/src/test/resources/pagamentos/hotmart/PURCHASE_COMPLETE.json`:

`````json
{
  "id": "6c4e6a5b-0000-4000-8000-000000000002",
  "creation_date": 1761140002000,
  "event": "PURCHASE_COMPLETE",
  "version": "2.0.0",
  "data": {
    "product": {
      "id": 4774438,
      "ucode": "0d3b5d0a-5f0e-4bd2-9a4b-6d1f0c2b9e11",
      "name": "Curso de Teste",
      "warranty_date": "2026-11-21T00:00:00Z",
      "has_co_production": false,
      "is_physical_product": false
    },
    "affiliates": [
      {
        "affiliate_code": "Q58388177J",
        "name": "Afiliado de Teste"
      }
    ],
    "buyer": {
      "email": "comprador.teste@example.com",
      "name": "Comprador de Teste",
      "first_name": "Comprador",
      "last_name": "de Teste",
      "checkout_phone": "5511999990000",
      "document": "12345678909",
      "document_type": "CPF",
      "address": {
        "country": "Brasil",
        "country_iso": "BR",
        "zipcode": "01001000"
      }
    },
    "producer": {
      "name": "Produtor de Teste",
      "document": "11222333000181",
      "legal_nature": "Pessoa Jurídica"
    },
    "commissions": [
      {
        "value": 14.95,
        "source": "MARKETPLACE",
        "currency_value": "BRL"
      },
      {
        "value": 182.05,
        "source": "PRODUCER",
        "currency_value": "BRL"
      }
    ],
    "purchase": {
      "approved_date": 1761140000000,
      "full_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "checkout_country": {
        "name": "Brasil",
        "iso": "BR"
      },
      "buyer_ip": "200.100.50.25",
      "origin": {
        "xcod": "XCOD_DO_PEDIDO",
        "sck": "plataforma"
      },
      "original_offer_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "order_date": 1761139900000,
      "status": "COMPLETE",
      "transaction": "HP17715690036014",
      "payment": {
        "installments_number": 1,
        "type": "CREDIT_CARD"
      },
      "offer": {
        "code": "k2pasun0"
      },
      "invoice_by": "HOTMART",
      "is_funnel": false,
      "business_model": "I"
    }
  }
}
`````

`backend/src/test/resources/pagamentos/hotmart/PURCHASE_DELAYED.json`:

`````json
{
  "id": "6c4e6a5b-0000-4000-8000-000000000008",
  "creation_date": 1761140008000,
  "event": "PURCHASE_DELAYED",
  "version": "2.0.0",
  "data": {
    "product": {
      "id": 4774438,
      "ucode": "0d3b5d0a-5f0e-4bd2-9a4b-6d1f0c2b9e11",
      "name": "Curso de Teste",
      "warranty_date": "2026-11-21T00:00:00Z",
      "has_co_production": false,
      "is_physical_product": false
    },
    "affiliates": [
      {
        "affiliate_code": "Q58388177J",
        "name": "Afiliado de Teste"
      }
    ],
    "buyer": {
      "email": "comprador.teste@example.com",
      "name": "Comprador de Teste",
      "first_name": "Comprador",
      "last_name": "de Teste",
      "checkout_phone": "5511999990000",
      "document": "12345678909",
      "document_type": "CPF",
      "address": {
        "country": "Brasil",
        "country_iso": "BR",
        "zipcode": "01001000"
      }
    },
    "producer": {
      "name": "Produtor de Teste",
      "document": "11222333000181",
      "legal_nature": "Pessoa Jurídica"
    },
    "commissions": [
      {
        "value": 14.95,
        "source": "MARKETPLACE",
        "currency_value": "BRL"
      },
      {
        "value": 182.05,
        "source": "PRODUCER",
        "currency_value": "BRL"
      }
    ],
    "purchase": {
      "approved_date": 1761140000000,
      "full_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "checkout_country": {
        "name": "Brasil",
        "iso": "BR"
      },
      "buyer_ip": "200.100.50.25",
      "origin": {
        "xcod": "XCOD_DO_PEDIDO",
        "sck": "plataforma"
      },
      "original_offer_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "order_date": 1761139900000,
      "status": "OVERDUE",
      "transaction": "HP17715690036014",
      "payment": {
        "installments_number": 1,
        "type": "CREDIT_CARD"
      },
      "offer": {
        "code": "k2pasun0"
      },
      "invoice_by": "HOTMART",
      "is_funnel": false,
      "business_model": "I"
    },
    "subscription": {
      "status": "DELAYED",
      "plan": {
        "id": 123,
        "name": "Plano mensal"
      },
      "subscriber": {
        "code": "I9OT62C3"
      }
    }
  }
}
`````

`backend/src/test/resources/pagamentos/hotmart/PURCHASE_EXPIRED.json`:

`````json
{
  "id": "6c4e6a5b-0000-4000-8000-000000000009",
  "creation_date": 1761140009000,
  "event": "PURCHASE_EXPIRED",
  "version": "2.0.0",
  "data": {
    "product": {
      "id": 4774438,
      "ucode": "0d3b5d0a-5f0e-4bd2-9a4b-6d1f0c2b9e11",
      "name": "Curso de Teste",
      "warranty_date": "2026-11-21T00:00:00Z",
      "has_co_production": false,
      "is_physical_product": false
    },
    "affiliates": [
      {
        "affiliate_code": "Q58388177J",
        "name": "Afiliado de Teste"
      }
    ],
    "buyer": {
      "email": "comprador.teste@example.com",
      "name": "Comprador de Teste",
      "first_name": "Comprador",
      "last_name": "de Teste",
      "checkout_phone": "5511999990000",
      "document": "12345678909",
      "document_type": "CPF",
      "address": {
        "country": "Brasil",
        "country_iso": "BR",
        "zipcode": "01001000"
      }
    },
    "producer": {
      "name": "Produtor de Teste",
      "document": "11222333000181",
      "legal_nature": "Pessoa Jurídica"
    },
    "commissions": [
      {
        "value": 14.95,
        "source": "MARKETPLACE",
        "currency_value": "BRL"
      },
      {
        "value": 182.05,
        "source": "PRODUCER",
        "currency_value": "BRL"
      }
    ],
    "purchase": {
      "approved_date": 1761140000000,
      "full_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "checkout_country": {
        "name": "Brasil",
        "iso": "BR"
      },
      "buyer_ip": "200.100.50.25",
      "origin": {
        "xcod": "XCOD_DO_PEDIDO",
        "sck": "plataforma"
      },
      "original_offer_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "order_date": 1761139900000,
      "status": "EXPIRED",
      "transaction": "HP17715690036014",
      "payment": {
        "installments_number": 1,
        "type": "CREDIT_CARD"
      },
      "offer": {
        "code": "k2pasun0"
      },
      "invoice_by": "HOTMART",
      "is_funnel": false,
      "business_model": "I"
    }
  }
}
`````

`backend/src/test/resources/pagamentos/hotmart/PURCHASE_PROTEST.json`:

`````json
{
  "id": "6c4e6a5b-0000-4000-8000-000000000007",
  "creation_date": 1761140007000,
  "event": "PURCHASE_PROTEST",
  "version": "2.0.0",
  "data": {
    "product": {
      "id": 4774438,
      "ucode": "0d3b5d0a-5f0e-4bd2-9a4b-6d1f0c2b9e11",
      "name": "Curso de Teste",
      "warranty_date": "2026-11-21T00:00:00Z",
      "has_co_production": false,
      "is_physical_product": false
    },
    "affiliates": [
      {
        "affiliate_code": "Q58388177J",
        "name": "Afiliado de Teste"
      }
    ],
    "buyer": {
      "email": "comprador.teste@example.com",
      "name": "Comprador de Teste",
      "first_name": "Comprador",
      "last_name": "de Teste",
      "checkout_phone": "5511999990000",
      "document": "12345678909",
      "document_type": "CPF",
      "address": {
        "country": "Brasil",
        "country_iso": "BR",
        "zipcode": "01001000"
      }
    },
    "producer": {
      "name": "Produtor de Teste",
      "document": "11222333000181",
      "legal_nature": "Pessoa Jurídica"
    },
    "commissions": [
      {
        "value": 14.95,
        "source": "MARKETPLACE",
        "currency_value": "BRL"
      },
      {
        "value": 182.05,
        "source": "PRODUCER",
        "currency_value": "BRL"
      }
    ],
    "purchase": {
      "approved_date": 1761140000000,
      "full_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "checkout_country": {
        "name": "Brasil",
        "iso": "BR"
      },
      "buyer_ip": "200.100.50.25",
      "origin": {
        "xcod": "XCOD_DO_PEDIDO",
        "sck": "plataforma"
      },
      "original_offer_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "order_date": 1761139900000,
      "status": "DISPUTE",
      "transaction": "HP17715690036014",
      "payment": {
        "installments_number": 1,
        "type": "CREDIT_CARD"
      },
      "offer": {
        "code": "k2pasun0"
      },
      "invoice_by": "HOTMART",
      "is_funnel": false,
      "business_model": "I"
    }
  }
}
`````

`backend/src/test/resources/pagamentos/hotmart/PURCHASE_REFUNDED.json`:

`````json
{
  "id": "6c4e6a5b-0000-4000-8000-000000000004",
  "creation_date": 1761140004000,
  "event": "PURCHASE_REFUNDED",
  "version": "2.0.0",
  "data": {
    "product": {
      "id": 4774438,
      "ucode": "0d3b5d0a-5f0e-4bd2-9a4b-6d1f0c2b9e11",
      "name": "Curso de Teste",
      "warranty_date": "2026-11-21T00:00:00Z",
      "has_co_production": false,
      "is_physical_product": false
    },
    "affiliates": [
      {
        "affiliate_code": "Q58388177J",
        "name": "Afiliado de Teste"
      }
    ],
    "buyer": {
      "email": "comprador.teste@example.com",
      "name": "Comprador de Teste",
      "first_name": "Comprador",
      "last_name": "de Teste",
      "checkout_phone": "5511999990000",
      "document": "12345678909",
      "document_type": "CPF",
      "address": {
        "country": "Brasil",
        "country_iso": "BR",
        "zipcode": "01001000"
      }
    },
    "producer": {
      "name": "Produtor de Teste",
      "document": "11222333000181",
      "legal_nature": "Pessoa Jurídica"
    },
    "commissions": [
      {
        "value": 14.95,
        "source": "MARKETPLACE",
        "currency_value": "BRL"
      },
      {
        "value": 182.05,
        "source": "PRODUCER",
        "currency_value": "BRL"
      }
    ],
    "purchase": {
      "approved_date": 1761140000000,
      "full_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "checkout_country": {
        "name": "Brasil",
        "iso": "BR"
      },
      "buyer_ip": "200.100.50.25",
      "origin": {
        "xcod": "XCOD_DO_PEDIDO",
        "sck": "plataforma"
      },
      "original_offer_price": {
        "value": 197.0,
        "currency_value": "BRL"
      },
      "order_date": 1761139900000,
      "status": "REFUNDED",
      "transaction": "HP17715690036014",
      "payment": {
        "installments_number": 1,
        "type": "CREDIT_CARD"
      },
      "offer": {
        "code": "k2pasun0"
      },
      "invoice_by": "HOTMART",
      "is_funnel": false,
      "business_model": "I"
    }
  }
}
`````

`backend/src/test/resources/pagamentos/hotmart/SUBSCRIPTION_CANCELLATION.json`:

`````json
{
  "id": "6c4e6a5b-0000-4000-8000-000000000201",
  "creation_date": 1761140002000,
  "event": "SUBSCRIPTION_CANCELLATION",
  "version": "2.0.0",
  "data": {
    "date_next_charge": 1763818400000,
    "product": {
      "id": 4774438,
      "name": "Curso de Teste"
    },
    "actual_recurrence_value": 97.0,
    "subscriber": {
      "code": "I9OT62C3",
      "name": "Comprador de Teste",
      "email": "comprador.teste@example.com",
      "phone": {
        "dddPhone": "11",
        "phone": "999990000"
      }
    },
    "subscription": {
      "id": 4148584,
      "plan": {
        "id": 123,
        "name": "Plano mensal"
      }
    },
    "cancellation_date": 1761140002000
  }
}
`````

`docs/pagamentos.md`:

`````markdown
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
`````

`web/adm/src/pagamentos.test.ts`:

`````ts
import { describe, expect, it } from 'vitest';
import { moeda } from '@compartilhado/ui/formato.ts';
import { formatarJson } from './paginas/pagamentos/DialogoEvento.tsx';
import { erroCampo } from './paginas/pagamentos/FormGateway.tsx';
import type { CampoConfiguracao, Gateway } from './tipos.ts';

const campo = (parcial: Partial<CampoConfiguracao>): CampoConfiguracao => ({
  chave: 'hottok',
  rotulo: 'Hottok',
  tipo: 'TEXTO',
  secreto: true,
  obrigatorio: true,
  dica: null,
  padrao: null,
  opcoes: [],
  ...parcial,
});

const gatewayCom = (configurado: boolean) =>
  ({
    segredos: { hottok: { configurado, final4: configurado ? '1234' : null } },
  }) as unknown as Gateway;

describe('formulário do meio de pagamento', () => {
  it('obrigatório em branco: erro, exceto segredo já configurado ou campo com padrão', () => {
    expect(erroCampo(campo({}), '  ')).toBe('Informe Hottok.');
    expect(erroCampo(campo({}), '', gatewayCom(true))).toBeUndefined();
    expect(erroCampo(campo({}), '', gatewayCom(false))).toBe('Informe Hottok.');
    expect(erroCampo(campo({ secreto: false, padrao: 'x' }), '')).toBeUndefined();
    expect(erroCampo(campo({ obrigatorio: false }), undefined)).toBeUndefined();
  });

  it('endereço precisa começar com http(s) e o valor tem no máximo 500 caracteres', () => {
    const url = campo({ tipo: 'URL', secreto: false });
    expect(erroCampo(url, 'pay.hotmart.com')).toBe('Informe um endereço começando com https://.');
    expect(erroCampo(url, 'https://pay.hotmart.com')).toBeUndefined();
    expect(erroCampo(campo({}), 'a'.repeat(501))).toBe('Use no máximo 500 caracteres.');
  });
});

describe('corpo do evento', () => {
  it('JSON indentado; texto (corpo que não era JSON) fica como veio', () => {
    expect(formatarJson('{"a":{"b":1}}')).toBe('{\n  "a": {\n    "b": 1\n  }\n}');
    expect(formatarJson('"não é json"')).toBe('não é json');
    expect(formatarJson('quebrado {')).toBe('quebrado {');
  });
});

describe('moeda', () => {
  it('centavos na moeda informada; vazio vira travessão', () => {
    expect(moeda(19_700)).toBe('R$ 197,00');
    expect(moeda(5, 'USD')).toBe('US$ 0,05');
    expect(moeda(null)).toBe('—');
  });
});
`````

`web/adm/src/paginas/pagamentos/DialogoEvento.tsx`:

`````tsx
import { Alerta, Botao, Carregando, Detalhes, Dialogo, Status } from '@celke/kole-react';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { STATUS_EVENTO, type EventoDetalhe } from '../../tipos.ts';

interface Props {
  eventoId: number;
  aoFechar: () => void;
  aoReprocessar: () => void;
}

/** Uma notificação recebida: dados, motivo, corpo (formatado) e o reprocessamento. */
export function DialogoEvento({ eventoId, aoFechar, aoReprocessar }: Props) {
  const { executar, executando } = useAcao();
  const detalhe = useCarregar(
    () => api.get<EventoDetalhe>(`/api/adm/pagamentos/eventos/${eventoId}`),
    [eventoId],
  );
  const evento = detalhe.dados?.evento;
  const podeReprocessar = evento && (evento.status === 'ERRO' || evento.status === 'IGNORADO');

  return (
    <Dialogo
      aberto
      titulo={evento ? evento.tipoOriginal : 'Notificação'}
      subtitulo={evento?.idExterno}
      tamanho="grande"
      aoFechar={aoFechar}
      rodape={
        podeReprocessar && (
          <Botao
            rotulo="Reprocessar"
            icone="rotate-ccw"
            carregando={executando}
            onClick={async () => {
              const ok = await executar(
                () =>
                  api
                    .post(`/api/adm/pagamentos/eventos/${eventoId}/reprocessamento`)
                    .then(() => true),
                'Evento na fila para reprocessar',
              );
              if (ok) aoReprocessar();
            }}
          />
        )
      }
    >
      {detalhe.erro && <Alerta severidade="err" mensagem={detalhe.erro} />}
      {!detalhe.dados && !detalhe.erro && <Carregando mensagem="Carregando a notificação..." />}
      {evento && (
        <div className="pc-pilha">
          <div className="pc-grade-detalhes">
            <Detalhes rotulo="Situação">
              <Status
                rotulo={STATUS_EVENTO[evento.status].rotulo}
                severidade={STATUS_EVENTO[evento.status].severidade}
              />
            </Detalhes>
            <Detalhes rotulo="Transação" valor={evento.transacao} />
            <Detalhes rotulo="Ocorrido em" valor={dataHora(evento.ocorridoEm)} />
            <Detalhes rotulo="Recebido em" valor={dataHora(evento.recebidoEm)} />
          </div>
          {evento.erro && (
            <Alerta severidade={evento.status === 'ERRO' ? 'err' : 'warn'} mensagem={evento.erro} />
          )}
          <Detalhes rotulo="Corpo recebido (documento e telefone mascarados)">
            {detalhe.dados?.payload ? (
              <pre className="pc-json">{formatarJson(detalhe.dados.payload)}</pre>
            ) : (
              <p className="pc-dica">O corpo já foi apagado pela retenção.</p>
            )}
          </Detalhes>
        </div>
      )}
    </Dialogo>
  );
}

/** JSON indentado; texto que não é JSON fica como veio. */
export function formatarJson(texto: string): string {
  try {
    const valor: unknown = JSON.parse(texto);
    return typeof valor === 'string' ? valor : JSON.stringify(valor, null, 2);
  } catch {
    return texto;
  }
}
`````

`web/adm/src/paginas/pagamentos/EventosPagina.tsx`:

`````tsx
import { CabecalhoPagina, CampoSelect, Status, Tabela } from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { opcoes, STATUS_EVENTO, type EventoPagamento, type StatusEvento } from '../../tipos.ts';
import { DialogoEvento } from './DialogoEvento.tsx';

const TAMANHO = 20;

/** Notificações recebidas dos meios de pagamento; as com erro ou ignoradas podem ser reprocessadas. */
export function EventosPagina() {
  const [pagina, setPagina] = useState(1);
  const [status, setStatus] = useState<StatusEvento | null>(null);
  const [aberto, setAberto] = useState<number | null>(null);

  const eventos = useCarregar(() => {
    const filtro = new URLSearchParams({ page: String(pagina - 1), size: String(TAMANHO) });
    if (status) filtro.set('status', status);
    return api.get<Pagina<EventoPagamento>>(`/api/adm/pagamentos/eventos?${filtro}`);
  }, [pagina, status]);

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Eventos recebidos" icone="inbox">
        Cada notificação do meio de pagamento fica registrada. Evento ignorado (oferta ainda não
        cadastrada, por exemplo) pode ser reprocessado depois de corrigir.
      </CabecalhoPagina>
      <div className="pc-filtros">
        <CampoSelect
          rotulo="Situação"
          variante="inline"
          placeholder="Todas"
          botaoLimpar
          opcoes={opcoes(STATUS_EVENTO)}
          valor={status}
          aoMudar={(valor) => {
            setStatus(valor);
            setPagina(1);
          }}
        />
      </div>
      <Tabela<EventoPagamento>
        rotulo="Eventos recebidos"
        colunas={[
          { campo: 'tipoOriginal', rotulo: 'Evento', destaque: true },
          { campo: 'transacao', rotulo: 'Transação' },
          {
            campo: 'status',
            rotulo: 'Situação',
            render: (e) => (
              <Status
                rotulo={STATUS_EVENTO[e.status].rotulo}
                severidade={STATUS_EVENTO[e.status].severidade}
              />
            ),
          },
          { campo: 'erro', rotulo: 'Motivo' },
          { campo: 'recebidoEm', rotulo: 'Recebido em', formato: (v) => dataHora(v as string) },
        ]}
        registros={eventos.dados?.itens ?? []}
        carregando={eventos.carregando && !eventos.dados}
        atualizando={eventos.carregando && !!eventos.dados}
        erro={eventos.erro}
        aoRecarregar={eventos.recarregar}
        vazioTitulo="Nenhum evento"
        vazioDescricao="Use “Enviar teste” na Hotmart para conferir a configuração."
        vazioIcone="inbox"
        aoClicarLinha={(e) => setAberto(e.id)}
        paginacao={{
          total: eventos.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
      />
      {aberto !== null && (
        <DialogoEvento
          eventoId={aberto}
          aoFechar={() => setAberto(null)}
          aoReprocessar={() => {
            setAberto(null);
            eventos.recarregar();
          }}
        />
      )}
    </div>
  );
}
`````

`web/adm/src/paginas/pagamentos/FormGateway.tsx`:

`````tsx
import {
  Alerta,
  CampoSelect,
  CampoSenha,
  CampoTexto,
  Form,
  FormSecao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { erroObrigatorio } from '@compartilhado/ui/validacao.ts';
import type { CampoConfiguracao, Gateway, TiposGateway } from '../../tipos.ts';

interface Props {
  tipos: TiposGateway;
  /** Sem gateway, cria um novo. */
  gateway?: Gateway;
  aoFechar: () => void;
  aoSalvar: () => void;
}

/**
 * Formulário montado a partir dos campos que o meio de pagamento declara. Campo secreto vem em
 * branco (a API nunca o devolve): em branco, o valor atual continua valendo.
 */
export function FormGateway({ tipos, gateway, aoFechar, aoSalvar }: Props) {
  const toast = useToast();
  const [tipo, setTipo] = useState<string | null>(gateway?.tipo ?? tipos.tipos[0]?.tipo ?? null);
  const [nome, setNome] = useState(gateway?.nome ?? tipos.tipos[0]?.nome ?? '');
  const [valores, setValores] = useState<Record<string, string>>(gateway?.valores ?? {});
  const [erros, setErros] = useState<Record<string, string | undefined>>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);
  const campos = tipos.tipos.find((t) => t.tipo === tipo)?.campos ?? [];

  const mudar = (chave: string, valor: string) =>
    setValores((atuais) => ({ ...atuais, [chave]: valor }));

  const salvar = async () => {
    const novos: Record<string, string | undefined> = {
      nome: erroObrigatorio(nome, 'Informe o nome.', 120),
      tipo: tipo ? undefined : 'Escolha o meio de pagamento.',
    };
    for (const campo of campos)
      novos[campo.chave] = erroCampo(campo, valores[campo.chave], gateway);
    setErros(novos);
    setErroGeral(undefined);
    if (Object.values(novos).some(Boolean)) return;
    setSalvando(true);
    try {
      const corpo = { nome: nome.trim(), valores: limpos(valores) };
      if (gateway) {
        await api.put(`/api/adm/pagamentos/gateways/${gateway.id}`, corpo);
        toast.ok('Meio de pagamento alterado', nome.trim());
      } else {
        await api.post('/api/adm/pagamentos/gateways', { ...corpo, tipo });
        toast.ok('Meio de pagamento cadastrado', 'Teste as credenciais e ative.');
      }
      aoSalvar();
    } catch (erro) {
      if (erro instanceof ErroApi) {
        const daApi: Record<string, string | undefined> = { nome: erro.erroDoCampo('nome') };
        for (const campo of campos) daApi[campo.chave] = erro.erroDoCampo(`valores.${campo.chave}`);
        setErros(daApi);
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Form
      arranjo="modal"
      aberto
      titulo={gateway ? `Editar ${gateway.nome}` : 'Novo meio de pagamento'}
      rotuloSalvar="Salvar"
      aoSalvar={() => void salvar()}
      aoCancelar={aoFechar}
      salvando={salvando}
      tamanho="medio"
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <FormSecao legenda="Identificação" colunas={2}>
        <CampoSelect<string>
          rotulo="Meio de pagamento"
          opcoes={tipos.tipos.map((t) => ({ valor: t.tipo, rotulo: t.nome }))}
          valor={tipo}
          aoMudar={setTipo}
          erro={erros['tipo']}
          disabled={!!gateway}
          obrigatorio
        />
        <CampoTexto
          rotulo="Nome"
          valor={nome}
          aoMudar={setNome}
          erro={erros['nome']}
          maxLength={120}
          obrigatorio
        />
      </FormSecao>
      <FormSecao legenda="Configuração" colunas={1}>
        {campos.map((campo) =>
          campo.tipo === 'SELECAO' ? (
            <CampoSelect<string>
              key={campo.chave}
              rotulo={campo.rotulo}
              opcoes={campo.opcoes.map((o) => ({ valor: o, rotulo: o }))}
              valor={valores[campo.chave] ?? campo.padrao}
              aoMudar={(v) => mudar(campo.chave, v ?? '')}
              erro={erros[campo.chave]}
              dica={campo.dica ?? undefined}
              obrigatorio={campo.obrigatorio}
            />
          ) : campo.secreto ? (
            <CampoSenha
              key={campo.chave}
              rotulo={campo.rotulo}
              valor={valores[campo.chave] ?? ''}
              aoMudar={(v) => mudar(campo.chave, v)}
              erro={erros[campo.chave]}
              dica={dicaSegredo(campo, gateway)}
              obrigatorio={campo.obrigatorio && !gateway?.segredos[campo.chave]?.configurado}
              autoComplete="off"
            />
          ) : (
            <CampoTexto
              key={campo.chave}
              rotulo={campo.rotulo}
              valor={valores[campo.chave] ?? ''}
              aoMudar={(v) => mudar(campo.chave, v)}
              erro={erros[campo.chave]}
              dica={campo.dica ?? (campo.padrao ? `Em branco: ${campo.padrao}` : undefined)}
              placeholder={campo.padrao ?? undefined}
              maxLength={500}
              obrigatorio={campo.obrigatorio && !campo.padrao}
            />
          ),
        )}
      </FormSecao>
    </Form>
  );
}

function dicaSegredo(campo: CampoConfiguracao, gateway?: Gateway): string | undefined {
  const atual = gateway?.segredos[campo.chave];
  if (atual?.configurado) {
    return `Configurado ${atual.final4 ? `••••${atual.final4}` : ''}. Em branco, continua o atual.`;
  }
  return campo.dica ?? undefined;
}

/** Erro do campo antes de ir à API (a API confere de novo). */
export function erroCampo(
  campo: CampoConfiguracao,
  valor: string | undefined,
  gateway?: Gateway,
): string | undefined {
  const texto = valor?.trim() ?? '';
  if (!texto) {
    const jaTem = campo.secreto && gateway?.segredos[campo.chave]?.configurado;
    return campo.obrigatorio && !campo.padrao && !jaTem ? `Informe ${campo.rotulo}.` : undefined;
  }
  if (texto.length > 500) return 'Use no máximo 500 caracteres.';
  if (campo.tipo === 'URL' && !/^https?:\/\/[^\s/]+/.test(texto)) {
    return 'Informe um endereço começando com https://.';
  }
  return undefined;
}

function limpos(valores: Record<string, string>): Record<string, string> {
  return Object.fromEntries(Object.entries(valores).map(([chave, valor]) => [chave, valor.trim()]));
}
`````

`web/adm/src/paginas/pagamentos/MeiosPagamentoPagina.tsx`:

`````tsx
import {
  Alerta,
  Botao,
  CabecalhoPagina,
  Card,
  Carregando,
  Detalhes,
  Status,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import type { Gateway, ResultadoTeste, TiposGateway } from '../../tipos.ts';
import { FormGateway } from './FormGateway.tsx';

/** Meios de pagamento: configuração, teste das credenciais, ativação e a URL do webhook. */
export function MeiosPagamentoPagina() {
  const toast = useToast();
  const { executar, executando } = useAcao();
  const [dialogo, setDialogo] = useState<{ gateway?: Gateway } | null>(null);
  const [testes, setTestes] = useState<Record<number, ResultadoTeste>>({});
  const tipos = useCarregar(() => api.get<TiposGateway>('/api/adm/pagamentos/tipos'), []);
  const gateways = useCarregar(() => api.get<Gateway[]>('/api/adm/pagamentos/gateways'), []);

  const testar = async (gateway: Gateway) => {
    const resultado = await executar(() =>
      api.post<ResultadoTeste>(`/api/adm/pagamentos/gateways/${gateway.id}/teste`),
    );
    if (resultado) setTestes((atuais) => ({ ...atuais, [gateway.id]: resultado }));
  };

  const alternar = async (gateway: Gateway) => {
    const rota = `/api/adm/pagamentos/gateways/${gateway.id}/ativacao`;
    const ok = await executar(
      () => (gateway.ativo ? api.delete(rota) : api.post(rota)).then(() => true),
      gateway.ativo ? 'Meio de pagamento desativado' : 'Meio de pagamento ativado',
    );
    if (ok) gateways.recarregar();
  };

  const copiar = async (url: string) => {
    try {
      await navigator.clipboard.writeText(url);
      toast.ok('Endereço copiado');
    } catch {
      toast.err('Não foi possível copiar', 'Selecione o endereço e copie à mão.');
    }
  };

  if ((tipos.erro && !tipos.dados) || (gateways.erro && !gateways.dados)) {
    return (
      <div className="pc-pagina">
        <Alerta
          severidade="err"
          titulo="Não foi possível carregar"
          mensagem={tipos.erro ?? gateways.erro}
        />
      </div>
    );
  }
  if (!tipos.dados || !gateways.dados) {
    return <Carregando mensagem="Carregando os meios de pagamento..." />;
  }

  return (
    <div className="pc-pagina">
      <CabecalhoPagina
        titulo="Meios de pagamento"
        icone="credit-card"
        acoes={
          <Botao rotulo="Novo meio de pagamento" icone="plus" onClick={() => setDialogo({})} />
        }
      >
        O meio ativo recebe os novos checkouts. Um meio desativado continua recebendo as
        notificações das compras antigas.
      </CabecalhoPagina>
      {!tipos.dados.cifraDisponivel && (
        <Alerta
          severidade="warn"
          titulo="Credenciais não podem ser gravadas"
          mensagem="Defina a PAGAMENTOS_CHAVE no .env do ambiente (openssl rand -base64 32) e reinicie a API."
        />
      )}
      {gateways.dados.length === 0 && (
        <Alerta
          severidade="info"
          mensagem="Nenhum meio de pagamento configurado. Cadastre a Hotmart para vender os cursos."
        />
      )}
      {gateways.dados.map((gateway) => {
        const teste = testes[gateway.id];
        return (
          <Card
            key={gateway.id}
            titulo={gateway.nome}
            subtitulo={`${gateway.nomeTipo} · alterado em ${dataHora(gateway.atualizadoEm)}`}
            borda={gateway.ativo ? 'ok' : undefined}
            ferramentas={
              <Status
                rotulo={gateway.ativo ? 'Ativo' : 'Inativo'}
                severidade={gateway.ativo ? 'ok' : 'neutral'}
              />
            }
          >
            <div className="pc-pilha">
              {!gateway.segredosLegiveis && (
                <Alerta
                  severidade="err"
                  mensagem="As credenciais gravadas não puderam ser lidas (a PAGAMENTOS_CHAVE mudou ou falta). Informe-as de novo."
                />
              )}
              <div className="pc-grade-detalhes">
                {Object.entries(gateway.valores).map(([chave, valor]) => (
                  <Detalhes
                    key={chave}
                    rotulo={rotuloCampo(tipos.dados!, gateway, chave)}
                    valor={valor}
                  />
                ))}
                {Object.entries(gateway.segredos).map(([chave, segredo]) => (
                  <Detalhes
                    key={chave}
                    rotulo={rotuloCampo(tipos.dados!, gateway, chave)}
                    valor={
                      segredo.configurado
                        ? `configurado ${segredo.final4 ? `••••${segredo.final4}` : ''}`
                        : 'não configurado'
                    }
                  />
                ))}
              </div>
              <Detalhes rotulo="Endereço do webhook">
                <div className="pc-linha">
                  <code className="pc-codigo">{gateway.webhookUrl}</code>
                  <Botao
                    rotulo="Copiar"
                    icone="copy"
                    aparencia="contorno"
                    tamanho="pequeno"
                    onClick={() => void copiar(gateway.webhookUrl)}
                  />
                </div>
              </Detalhes>
              {gateway.tipo === 'HOTMART' && <PassoAPassoHotmart />}
              {teste && (
                <Alerta
                  severidade={teste.ok ? 'ok' : 'err'}
                  titulo={teste.ok ? 'Credenciais conferidas' : 'Credenciais recusadas'}
                  mensagem={teste.mensagem}
                />
              )}
              <div className="pc-linha">
                <Botao
                  rotulo="Editar"
                  icone="pencil"
                  aparencia="contorno"
                  onClick={() => setDialogo({ gateway })}
                />
                <Botao
                  rotulo="Testar credenciais"
                  icone="shield-check"
                  aparencia="contorno"
                  carregando={executando}
                  onClick={() => void testar(gateway)}
                />
                <Botao
                  rotulo={gateway.ativo ? 'Desativar' : 'Ativar'}
                  icone={gateway.ativo ? 'power-off' : 'power'}
                  severidade={gateway.ativo ? 'err' : undefined}
                  aparencia={gateway.ativo ? 'contorno' : undefined}
                  onClick={() => void alternar(gateway)}
                />
              </div>
            </div>
          </Card>
        );
      })}
      {dialogo && (
        <FormGateway
          tipos={tipos.dados}
          gateway={dialogo.gateway}
          aoFechar={() => setDialogo(null)}
          aoSalvar={() => {
            setDialogo(null);
            gateways.recarregar();
          }}
        />
      )}
    </div>
  );
}

function rotuloCampo(tipos: TiposGateway, gateway: Gateway, chave: string): string {
  const tipo = tipos.tipos.find((t) => t.tipo === gateway.tipo);
  return tipo?.campos.find((c) => c.chave === chave)?.rotulo ?? chave;
}

/** Onde cada coisa fica na Hotmart (webhook 2.0.0). */
function PassoAPassoHotmart() {
  return (
    <Card titulo="Como configurar na Hotmart" recolhivel recolhidoInicial>
      <ol className="pc-passos">
        <li>
          Em <strong>Ferramentas → Webhook</strong>, crie uma configuração na{' '}
          <strong>versão 2.0.0</strong> com o endereço acima.
        </li>
        <li>
          Marque os eventos de compra: aprovada, completa, cancelada, reembolsada, chargeback,
          boleto impresso, pedido de reembolso (protesto), atrasada e expirada.
        </li>
        <li>Copie o hottok da configuração para o campo Hottok daqui.</li>
        <li>
          Em cada produto, aponte a página de obrigado para{' '}
          <code>{'<área do aluno>'}/pagamento/retorno</code>.
        </li>
        <li>
          Para a reconciliação, crie as credenciais em <strong>Ferramentas → Credenciais</strong> e
          informe o Client ID, o Client Secret e o Basic.
        </li>
        <li>Use “Enviar teste” na Hotmart e confira em Pagamentos → Eventos recebidos.</li>
      </ol>
    </Card>
  );
}
`````

`web/adm/src/paginas/pagamentos/PedidoPagina.tsx`:

`````tsx
import { Alerta, Card, Carregando, Detalhes, Status, Tabela } from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora, moeda } from '@compartilhado/ui/formato.ts';
import { CAMINHOS } from '../../caminhos.ts';
import {
  FORMAS_PAGAMENTO,
  STATUS_EVENTO,
  STATUS_PEDIDO,
  type EventoPagamento,
  type PedidoDetalhe,
} from '../../tipos.ts';
import { DialogoEvento } from './DialogoEvento.tsx';

/** Um pedido: dados, linha do tempo e as notificações recebidas. */
export function PedidoPagina() {
  const { pedidoId } = useParams();
  const navigate = useNavigate();
  const [evento, setEvento] = useState<number | null>(null);
  const detalhe = useCarregar(
    () => api.get<PedidoDetalhe>(`/api/adm/pagamentos/pedidos/${pedidoId}`),
    [pedidoId],
  );

  if (detalhe.erro && !detalhe.dados) {
    return (
      <div className="pc-pagina">
        <Alerta severidade="err" titulo="Não foi possível abrir o pedido" mensagem={detalhe.erro} />
      </div>
    );
  }
  if (!detalhe.dados) return <Carregando mensagem="Carregando o pedido..." />;
  const { pedido, historico, eventos } = detalhe.dados;

  return (
    <div className="pc-pagina">
      <Card
        titulo={`Pedido ${pedido.codigo}`}
        subtitulo={pedido.email}
        ferramentas={
          <Status
            rotulo={STATUS_PEDIDO[pedido.status].rotulo}
            severidade={STATUS_PEDIDO[pedido.status].severidade}
          />
        }
        rodape={
          <button type="button" className="pc-link" onClick={() => navigate(CAMINHOS.pedidos)}>
            Voltar para os pedidos
          </button>
        }
      >
        <div className="pc-grade-detalhes">
          <Detalhes rotulo="Comprador" valor={pedido.nome ?? pedido.email} />
          <Detalhes rotulo="Oferta" valor={pedido.ofertaTitulo} />
          <Detalhes rotulo="Valor" valor={moeda(pedido.valorCentavos, pedido.moeda ?? 'BRL')} />
          <Detalhes
            rotulo="Forma"
            valor={pedido.formaPagamento ? FORMAS_PAGAMENTO[pedido.formaPagamento] : null}
          />
          <Detalhes rotulo="Transação" valor={pedido.transacaoExterna} />
          <Detalhes
            rotulo="Origem"
            valor={pedido.origem === 'EXTERNO' ? 'Direto no meio de pagamento' : 'Plataforma'}
          />
          <Detalhes
            rotulo="Matrícula"
            valor={pedido.matriculaId ? `#${pedido.matriculaId}` : null}
          />
          <Detalhes rotulo="Criado em" valor={dataHora(pedido.criadoEm)} />
        </div>
      </Card>

      <Card titulo="Linha do tempo">
        <ol className="pc-linha-tempo">
          {historico.map((h) => (
            <li key={h.id}>
              <strong>
                {h.de ? `${STATUS_PEDIDO[h.de].rotulo} → ` : ''}
                {STATUS_PEDIDO[h.para].rotulo}
              </strong>
              <span className="pc-dica">
                {dataHora(h.em)}
                {h.motivo ? ` · ${h.motivo}` : ''}
              </span>
            </li>
          ))}
        </ol>
      </Card>

      <Card titulo="Notificações recebidas" corpoSemEspaco>
        <Tabela<EventoPagamento>
          rotulo="Notificações do pedido"
          colunas={[
            { campo: 'tipoOriginal', rotulo: 'Evento', destaque: true },
            {
              campo: 'status',
              rotulo: 'Situação',
              render: (e) => (
                <Status
                  rotulo={STATUS_EVENTO[e.status].rotulo}
                  severidade={STATUS_EVENTO[e.status].severidade}
                />
              ),
            },
            { campo: 'erro', rotulo: 'Motivo' },
            { campo: 'recebidoEm', rotulo: 'Recebido em', formato: (v) => dataHora(v as string) },
          ]}
          registros={eventos}
          vazioTitulo="Nenhuma notificação"
          vazioDescricao="O meio de pagamento ainda não avisou nada sobre este pedido."
          vazioIcone="inbox"
          aoClicarLinha={(e) => setEvento(e.id)}
        />
      </Card>
      {evento !== null && (
        <DialogoEvento
          eventoId={evento}
          aoFechar={() => setEvento(null)}
          aoReprocessar={() => {
            setEvento(null);
            detalhe.recarregar();
          }}
        />
      )}
    </div>
  );
}
`````

`web/adm/src/paginas/pagamentos/PedidosPagina.tsx`:

`````tsx
import { CabecalhoPagina, CampoSelect, CampoTexto, Status, Tabela } from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora, moeda } from '@compartilhado/ui/formato.ts';
import { caminhoPedido } from '../../caminhos.ts';
import { opcoes, STATUS_PEDIDO, type Pedido, type StatusPedido } from '../../tipos.ts';

const TAMANHO = 20;

/** Pedidos de todos os meios de pagamento, com filtro por status e por e-mail. */
export function PedidosPagina() {
  const navigate = useNavigate();
  const [pagina, setPagina] = useState(1);
  const [status, setStatus] = useState<StatusPedido | null>(null);
  const [email, setEmail] = useState('');
  const filtroEmail = email.trim();

  const pedidos = useCarregar(() => {
    const filtro = new URLSearchParams({ page: String(pagina - 1), size: String(TAMANHO) });
    if (status) filtro.set('status', status);
    if (filtroEmail) filtro.set('email', filtroEmail);
    return api.get<Pagina<Pedido>>(`/api/adm/pagamentos/pedidos?${filtro}`);
  }, [pagina, status, filtroEmail]);

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Pedidos" icone="receipt">
        Compras feitas pela plataforma e vendas que chegaram direto do meio de pagamento.
      </CabecalhoPagina>
      <div className="pc-filtros">
        <CampoSelect
          rotulo="Status"
          variante="inline"
          placeholder="Todos"
          botaoLimpar
          opcoes={opcoes(STATUS_PEDIDO)}
          valor={status}
          aoMudar={(valor) => {
            setStatus(valor);
            setPagina(1);
          }}
        />
        <CampoTexto
          rotulo="E-mail"
          valor={email}
          aoMudar={(valor) => {
            setEmail(valor);
            setPagina(1);
          }}
          placeholder="Parte do e-mail"
        />
      </div>
      <Tabela<Pedido>
        rotulo="Pedidos"
        colunas={[
          { campo: 'email', rotulo: 'Comprador', destaque: true },
          { campo: 'ofertaTitulo', rotulo: 'Oferta' },
          {
            campo: 'valorCentavos',
            rotulo: 'Valor',
            formato: (v, p) => moeda(v as number | null, p.moeda ?? 'BRL'),
          },
          {
            campo: 'status',
            rotulo: 'Status',
            render: (p) => (
              <Status
                rotulo={STATUS_PEDIDO[p.status].rotulo}
                severidade={STATUS_PEDIDO[p.status].severidade}
              />
            ),
          },
          {
            campo: 'origem',
            rotulo: 'Origem',
            formato: (v) => (v === 'EXTERNO' ? 'Direto no meio de pagamento' : 'Plataforma'),
          },
          { campo: 'criadoEm', rotulo: 'Criado em', formato: (v) => dataHora(v as string) },
        ]}
        registros={pedidos.dados?.itens ?? []}
        carregando={pedidos.carregando && !pedidos.dados}
        atualizando={pedidos.carregando && !!pedidos.dados}
        erro={pedidos.erro}
        aoRecarregar={pedidos.recarregar}
        vazioTitulo="Nenhum pedido"
        vazioDescricao="Os pedidos aparecem aqui assim que alguém clica em Comprar."
        vazioIcone="receipt"
        aoClicarLinha={(p) => navigate(caminhoPedido(p.id))}
        paginacao={{
          total: pedidos.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
      />
    </div>
  );
}
`````

`web/adm/src/paginas/turma/VendaAba.tsx`:

`````tsx
import {
  AcaoLinha,
  Alerta,
  Botao,
  CampoInteiro,
  CampoMoeda,
  CampoTexto,
  Form,
  FormSecao,
  Status,
  Tabela,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { moeda } from '@compartilhado/ui/formato.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { erroObrigatorio } from '@compartilhado/ui/validacao.ts';
import type { Oferta, Turma } from '../../tipos.ts';

/** Ofertas da turma: o que se vende no meio de pagamento (produto e oferta de lá) e o acesso que dá. */
export function VendaAba({ turma }: { turma: Turma }) {
  const { executar } = useAcao();
  const [dialogo, setDialogo] = useState<{ oferta?: Oferta } | null>(null);
  const ofertas = useCarregar(
    () => api.get<Oferta[]>(`/api/adm/turmas/${turma.id}/ofertas`),
    [turma.id],
  );

  return (
    <div className="pc-pilha">
      {turma.status !== 'ATIVA' && (
        <Alerta
          severidade="info"
          mensagem="O botão Comprar só aparece no site para a turma ativa de um curso publicado."
        />
      )}
      <div className="pc-linha pc-linha-fim">
        <Botao rotulo="Nova oferta" icone="plus" onClick={() => setDialogo({})} />
      </div>
      <Tabela<Oferta>
        rotulo="Ofertas"
        colunas={[
          { campo: 'titulo', rotulo: 'Oferta', destaque: true },
          {
            campo: 'precoCentavos',
            rotulo: 'Preço',
            formato: (v, o) => moeda(v as number, o.moeda),
          },
          {
            campo: 'diasAcesso',
            rotulo: 'Acesso',
            formato: (v) => (v ? `${v as number} dias` : 'Sem prazo'),
          },
          {
            campo: 'produtoExterno',
            rotulo: 'Produto / oferta',
            formato: (v, o) => `${v as string} / ${o.ofertaExterna}`,
          },
          { campo: 'gatewayNome', rotulo: 'Meio de pagamento' },
          {
            campo: 'ativa',
            rotulo: 'Situação',
            render: (o) => (
              <Status
                rotulo={o.ativa ? (o.gatewayAtivo ? 'À venda' : 'Meio inativo') : 'Inativa'}
                severidade={o.ativa && o.gatewayAtivo ? 'ok' : 'neutral'}
              />
            ),
          },
        ]}
        registros={ofertas.dados ?? []}
        carregando={ofertas.carregando && !ofertas.dados}
        erro={ofertas.erro}
        aoRecarregar={ofertas.recarregar}
        vazioTitulo="Nenhuma oferta"
        vazioDescricao="Cadastre o produto e a oferta do meio de pagamento para vender esta turma."
        vazioIcone="tag"
        acoes={(o) => (
          <>
            <AcaoLinha
              icone="pencil"
              rotulo={`Editar ${o.titulo}`}
              onClick={() => setDialogo({ oferta: o })}
            />
            <AcaoLinha
              icone={o.ativa ? 'power-off' : 'power'}
              rotulo={o.ativa ? `Desativar ${o.titulo}` : `Ativar ${o.titulo}`}
              onClick={async () => {
                const ok = await executar(
                  () =>
                    api
                      .patch(`/api/adm/ofertas/${o.id}/status`, { ativa: !o.ativa })
                      .then(() => true),
                  o.ativa ? 'Oferta desativada' : 'Oferta ativada',
                );
                if (ok) ofertas.recarregar();
              }}
            />
          </>
        )}
      />
      {dialogo && (
        <FormOferta
          turma={turma}
          oferta={dialogo.oferta}
          aoFechar={() => setDialogo(null)}
          aoSalvar={() => {
            setDialogo(null);
            ofertas.recarregar();
          }}
        />
      )}
    </div>
  );
}

type Campos =
  'produtoExterno' | 'ofertaExterna' | 'codigoCheckout' | 'titulo' | 'precoCentavos' | 'diasAcesso';

interface PropsForm {
  turma: Turma;
  oferta?: Oferta;
  aoFechar: () => void;
  aoSalvar: () => void;
}

function FormOferta({ turma, oferta, aoFechar, aoSalvar }: PropsForm) {
  const toast = useToast();
  const [titulo, setTitulo] = useState(oferta?.titulo ?? '');
  const [produto, setProduto] = useState(oferta?.produtoExterno ?? '');
  const [codigoOferta, setCodigoOferta] = useState(oferta?.ofertaExterna ?? '');
  const [codigoCheckout, setCodigoCheckout] = useState(oferta?.codigoCheckout ?? '');
  const [preco, setPreco] = useState<number | null>(oferta ? oferta.precoCentavos / 100 : null);
  const [dias, setDias] = useState<number | null>(oferta?.diasAcesso ?? null);
  const [erros, setErros] = useState<Partial<Record<Campos, string>>>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const novos: Partial<Record<Campos, string>> = {
      titulo: erroObrigatorio(titulo, 'Informe o título.', 120),
      produtoExterno: erroObrigatorio(produto, 'Informe o id do produto.', 100),
      ofertaExterna: erroObrigatorio(codigoOferta, 'Informe o código da oferta.', 100),
      precoCentavos: preco == null ? 'Informe o preço.' : undefined,
      diasAcesso: dias != null && (dias < 1 || dias > 36_500) ? 'De 1 a 36.500 dias.' : undefined,
    };
    setErros(novos);
    setErroGeral(undefined);
    if (Object.values(novos).some(Boolean)) return;
    setSalvando(true);
    try {
      const corpo = {
        titulo: titulo.trim(),
        produtoExterno: produto.trim(),
        ofertaExterna: codigoOferta.trim(),
        codigoCheckout: codigoCheckout.trim() || null,
        precoCentavos: Math.round((preco ?? 0) * 100),
        moeda: oferta?.moeda ?? 'BRL',
        diasAcesso: dias,
      };
      if (oferta) {
        await api.put(`/api/adm/ofertas/${oferta.id}`, corpo);
        toast.ok('Oferta alterada', corpo.titulo);
      } else {
        await api.post(`/api/adm/turmas/${turma.id}/ofertas`, corpo);
        toast.ok('Oferta cadastrada', corpo.titulo);
      }
      aoSalvar();
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({
          titulo: erro.erroDoCampo('titulo'),
          produtoExterno: erro.erroDoCampo('produtoExterno'),
          ofertaExterna: erro.erroDoCampo('ofertaExterna'),
          codigoCheckout: erro.erroDoCampo('codigoCheckout'),
          precoCentavos: erro.erroDoCampo('precoCentavos'),
          diasAcesso: erro.erroDoCampo('diasAcesso'),
        });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Form
      arranjo="modal"
      aberto
      titulo={oferta ? `Editar ${oferta.titulo}` : 'Nova oferta'}
      rotuloSalvar="Salvar oferta"
      aoSalvar={() => void salvar()}
      aoCancelar={aoFechar}
      salvando={salvando}
      tamanho="medio"
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <FormSecao legenda="Oferta" colunas={2}>
        <CampoTexto
          rotulo="Título"
          valor={titulo}
          aoMudar={setTitulo}
          erro={erros.titulo}
          maxLength={120}
          obrigatorio
          dica="Aparece no site, no botão de compra."
          className="lp-col-full"
        />
        <CampoMoeda
          rotulo="Preço"
          valor={preco}
          aoMudar={setPreco}
          erro={erros.precoCentavos}
          obrigatorio
        />
        <CampoInteiro
          rotulo="Dias de acesso"
          valor={dias}
          aoMudar={setDias}
          erro={erros.diasAcesso}
          dica="Em branco, sem prazo."
        />
      </FormSecao>
      <FormSecao legenda="No meio de pagamento" colunas={2}>
        <CampoTexto
          rotulo="Id do produto"
          valor={produto}
          aoMudar={setProduto}
          erro={erros.produtoExterno}
          maxLength={100}
          obrigatorio
          dica="Hotmart: o id numérico do produto (vem nas notificações)."
        />
        <CampoTexto
          rotulo="Código da oferta"
          valor={codigoOferta}
          aoMudar={setCodigoOferta}
          erro={erros.ofertaExterna}
          maxLength={100}
          obrigatorio
          dica="Hotmart: o valor de off= no link da oferta."
        />
        <CampoTexto
          rotulo="Código do checkout"
          valor={codigoCheckout}
          aoMudar={setCodigoCheckout}
          erro={erros.codigoCheckout}
          maxLength={100}
          dica="Hotmart: o trecho depois de pay.hotmart.com/ no link. Em branco, usa o id do produto."
          className="lp-col-full"
        />
      </FormSecao>
    </Form>
  );
}
`````

`web/aluno/src/pagamento.test.tsx`:

`````tsx
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  INTERVALO_MS,
  LIMITE_MS,
  resultado,
  RetornoPagamentoPagina,
} from './paginas/pagamento/RetornoPagamentoPagina.tsx';
import type { StatusPedido } from './tipos.ts';

// Sessão da página: retomada (aluno logado) por padrão; um teste simula a retomada ainda em andamento
type EstadoSessao = { status: 'carregando' | 'anonimo' | 'autenticado' };
const sessao = vi.hoisted(() => {
  let estado: EstadoSessao = { status: 'autenticado' };
  const ouvintes = new Set<() => void>();
  return {
    ler: () => estado,
    definir: (novo: EstadoSessao) => {
      estado = novo;
      ouvintes.forEach((ouvinte) => ouvinte());
    },
    assinar: (ouvinte: () => void) => {
      ouvintes.add(ouvinte);
      return () => ouvintes.delete(ouvinte);
    },
  };
});
vi.mock('@compartilhado/sessao/ProvedorSessao.tsx', async () => {
  const { useSyncExternalStore } = await import('react');
  return { useSessao: () => ({ estado: useSyncExternalStore(sessao.assinar, sessao.ler) }) };
});

describe('resultado do pedido na página de retorno', () => {
  it.each<[StatusPedido, string]>([
    ['APROVADO', 'confirmado'],
    ['CONCLUIDO', 'confirmado'],
    ['CRIADO', 'aguardando'],
    ['AGUARDANDO_PAGAMENTO', 'aguardando'],
    ['CANCELADO', 'recusado'],
    ['EXPIRADO', 'recusado'],
    ['REEMBOLSADO', 'recusado'],
    ['CHARGEBACK', 'recusado'],
  ])('%s → %s', (status, esperado) => {
    expect(resultado(status)).toBe(esperado);
  });
});

function json(corpo: unknown) {
  return new Response(JSON.stringify(corpo), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function pedido(status: StatusPedido) {
  return {
    codigo: 'abc',
    status,
    turmaId: null,
    turmaNome: null,
    cursoTitulo: null,
    cursoSlug: null,
  };
}

function abrir(caminho: string) {
  const roteador = createMemoryRouter(
    [{ path: '/pagamento/retorno', element: <RetornoPagamentoPagina /> }],
    { initialEntries: [caminho] },
  );
  render(<RouterProvider router={roteador} />);
}

describe('página de retorno', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('consulta a cada 3 s até o pagamento ser confirmado', async () => {
    fetchMock
      .mockResolvedValueOnce(json(pedido('CRIADO')))
      .mockResolvedValueOnce(json(pedido('APROVADO')));

    abrir('/pagamento/retorno?pedido=abc');

    expect(await screen.findByText('Aguardando pagamento')).toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(INTERVALO_MS));
    expect(await screen.findByText('Pagamento confirmado')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0]![0]).toBe('/api/pagamentos/pedidos/abc');
  });

  it('para de consultar depois de 2 minutos', async () => {
    fetchMock.mockImplementation(async () => json(pedido('AGUARDANDO_PAGAMENTO')));

    abrir('/pagamento/retorno?pedido=abc');
    await screen.findByText('Aguardando pagamento');
    await act(() => vi.advanceTimersByTimeAsync(LIMITE_MS + INTERVALO_MS * 2));
    const chamadas = fetchMock.mock.calls.length;
    await act(() => vi.advanceTimersByTimeAsync(INTERVALO_MS * 5));

    expect(fetchMock.mock.calls.length).toBe(chamadas);
    expect(chamadas).toBeLessThanOrEqual(LIMITE_MS / INTERVALO_MS + 2);
  });

  it('pagamento recusado e link sem pedido', async () => {
    fetchMock.mockResolvedValueOnce(json(pedido('CANCELADO')));
    abrir('/pagamento/retorno?pedido=abc');
    expect(await screen.findByText('Pagamento não aprovado')).toBeInTheDocument();
  });

  it('link sem o código do pedido', () => {
    abrir('/pagamento/retorno');
    expect(screen.getByText('O link não tem o código do pedido.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('sessão ainda sendo retomada', () => {
  const fetchMock = vi.fn<typeof fetch>();

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    sessao.definir({ status: 'autenticado' });
  });

  it('só consulta o pedido depois que a sessão volta (senão a API não devolve a turma)', async () => {
    cleanup(); // páginas dos testes anteriores também ouviriam a sessão
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockResolvedValueOnce(json(pedido('APROVADO')));
    sessao.definir({ status: 'carregando' });
    abrir('/pagamento/retorno?pedido=abc');

    expect(screen.getByText('Consultando o seu pedido...')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    act(() => sessao.definir({ status: 'autenticado' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(fetchMock.mock.calls[0]![0]).toBe('/api/pagamentos/pedidos/abc');
    expect(await screen.findByText('Pagamento confirmado')).toBeInTheDocument();
  });
});

describe('código do pedido na página de retorno', () => {
  const fetchMock = vi.fn<typeof fetch>();

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it('aceita o xcod como a Hotmart repassa', async () => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockResolvedValueOnce(json(pedido('APROVADO')));
    abrir('/pagamento/retorno?xcod=abc');
    expect(await screen.findByText('Pagamento confirmado')).toBeInTheDocument();
    expect(fetchMock.mock.calls[0]![0]).toBe('/api/pagamentos/pedidos/abc');
  });
});
`````

`web/aluno/src/paginas/pagamento/ComprarPagina.tsx`:

`````tsx
import { Alerta, Carregando } from '@celke/kole-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { CAMINHOS } from '../../caminhos.ts';
import type { Checkout } from '../../tipos.ts';

/** Para onde levar o aluno no checkout (redirecionamento separado para os testes). */
export const navegador = {
  ir: (url: string) => window.location.assign(url),
};

/**
 * Destino do botão "Comprar" do site (exige login: sem sessão, volta para cá depois de entrar ou
 * criar a conta). Abre o pedido e leva ao checkout do meio de pagamento.
 */
export function ComprarPagina() {
  const { ofertaId } = useParams();
  const [erro, setErro] = useState<string>();
  const [embutido, setEmbutido] = useState<Checkout | null>(null);
  // Um pedido por visita, mesmo com o StrictMode montando duas vezes
  const pedido = useRef(false);

  useEffect(() => {
    if (pedido.current) return;
    pedido.current = true;
    api
      .post<Checkout>('/api/pagamentos/checkout', { ofertaId: Number(ofertaId) })
      .then((checkout) => {
        if (checkout.tipo === 'REDIRECIONAMENTO' && checkout.url) navegador.ir(checkout.url);
        else setEmbutido(checkout);
      })
      .catch((falha: unknown) => setErro(mensagemDeErro(falha)));
  }, [ofertaId]);

  if (erro) {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Não foi possível iniciar a compra">
        <Alerta severidade="err" mensagem={erro} />
        <Link className="pc-link" to={CAMINHOS.inicio}>
          Ir para Meus cursos
        </Link>
      </LayoutConta>
    );
  }
  if (embutido) {
    // Meio de pagamento com formulário próprio: os dados vão para o componente dele
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Pagamento">
        <Alerta
          severidade="info"
          mensagem="Este meio de pagamento usa um formulário próprio, ainda não disponível nesta versão."
        />
      </LayoutConta>
    );
  }
  return (
    <LayoutConta marca="Plataforma de Cursos" titulo="Indo para o pagamento">
      <Carregando mensagem="Preparando o seu pedido..." />
    </LayoutConta>
  );
}
`````

`web/aluno/src/paginas/pagamento/RetornoPagamentoPagina.tsx`:

`````tsx
import { Alerta, Botao, Carregando } from '@celke/kole-react';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { CAMINHOS, caminhoTurma } from '../../caminhos.ts';
import type { SituacaoPedido, StatusPedido } from '../../tipos.ts';

export const INTERVALO_MS = 3_000;
export const LIMITE_MS = 120_000;

export type Resultado = 'confirmado' | 'aguardando' | 'recusado';

/** O que a página mostra para cada status do pedido. */
export function resultado(status: StatusPedido): Resultado {
  switch (status) {
    case 'APROVADO':
    case 'CONCLUIDO':
      return 'confirmado';
    case 'CRIADO':
    case 'AGUARDANDO_PAGAMENTO':
      return 'aguardando';
    default:
      return 'recusado';
  }
}

/**
 * Página de obrigado do meio de pagamento (`?pedido=<código>` ou `?xcod=<código>`): consulta o pedido a cada 3 segundos
 * por até 2 minutos, até a notificação chegar. Quem volta do meio de pagamento abre a página do zero: a
 * consulta espera a sessão ser retomada pelo cookie, senão sai sem login e a API não devolve a turma.
 */
export function RetornoPagamentoPagina() {
  const [parametros] = useSearchParams();
  // `xcod`: o código do pedido como a Hotmart o repassa à página de obrigado
  const codigo = parametros.get('pedido') ?? parametros.get('xcod');
  const navigate = useNavigate();
  const { estado: sessao } = useSessao();
  const sessaoPronta = sessao.status !== 'carregando';
  const [situacao, setSituacao] = useState<SituacaoPedido>();
  const [erro, setErro] = useState<string>();
  const [esgotou, setEsgotou] = useState(false);

  useEffect(() => {
    if (!codigo || !sessaoPronta) return;
    let ativo = true;
    const inicio = Date.now();
    let espera: ReturnType<typeof setTimeout>;
    const consultar = async () => {
      try {
        const atual = await api.get<SituacaoPedido>(
          `/api/pagamentos/pedidos/${encodeURIComponent(codigo)}`,
        );
        if (!ativo) return;
        setSituacao(atual);
        if (resultado(atual.status) !== 'aguardando') return;
      } catch (falha) {
        if (ativo) setErro(mensagemDeErro(falha));
        return;
      }
      if (Date.now() - inicio >= LIMITE_MS) {
        setEsgotou(true);
        return;
      }
      espera = setTimeout(() => void consultar(), INTERVALO_MS);
    };
    void consultar();
    return () => {
      ativo = false;
      clearTimeout(espera);
    };
  }, [codigo, sessaoPronta]);

  if (!codigo || erro) {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Pedido não encontrado">
        <Alerta severidade="err" mensagem={erro ?? 'O link não tem o código do pedido.'} />
        <Link className="pc-link" to={CAMINHOS.inicio}>
          Ir para Meus cursos
        </Link>
      </LayoutConta>
    );
  }
  if (!situacao) {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Conferindo o pagamento">
        <Carregando mensagem="Consultando o seu pedido..." />
      </LayoutConta>
    );
  }

  const estado = resultado(situacao.status);
  if (estado === 'confirmado') {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Pagamento confirmado">
        <Alerta
          severidade="ok"
          mensagem={
            situacao.cursoTitulo
              ? `Sua matrícula em ${situacao.cursoTitulo} está ativa.`
              : 'Sua matrícula está ativa. Confira o e-mail de confirmação.'
          }
        />
        <Botao
          rotulo={situacao.turmaId ? 'Ir para o curso' : 'Ir para Meus cursos'}
          icone="graduation-cap"
          bloco
          onClick={() =>
            navigate(situacao.turmaId ? caminhoTurma(situacao.turmaId) : CAMINHOS.inicio)
          }
        />
      </LayoutConta>
    );
  }
  if (estado === 'aguardando') {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Aguardando pagamento">
        <Alerta
          severidade="info"
          mensagem="Assim que o pagamento for confirmado (boleto ou PIX podem levar um tempo), você recebe um e-mail e o curso aparece em Meus cursos."
        />
        {!esgotou && <Carregando mensagem="Conferindo de novo em instantes..." />}
        <Link className="pc-link" to={CAMINHOS.inicio}>
          Ir para Meus cursos
        </Link>
      </LayoutConta>
    );
  }
  return (
    <LayoutConta marca="Plataforma de Cursos" titulo="Pagamento não aprovado">
      <Alerta
        severidade="err"
        mensagem="O pagamento não foi aprovado ou foi cancelado. Tente de novo pelo site do curso."
      />
      <Link className="pc-link" to={CAMINHOS.inicio}>
        Ir para Meus cursos
      </Link>
    </LayoutConta>
  );
}
`````

`web/e2e/adm/pagamentos.spec.ts`:

`````ts
import { request as criarRequest, type APIRequestContext } from '@playwright/test';
import { clienteApi, montarCurso, type ClienteApi } from '../support/api.ts';
import { emailNovo, stamp } from '../support/dados.ts';
import { criarOferta, hotmartAtiva, notificar } from '../support/pagamentos.ts';
import { expect, test } from '../support/sessoes.ts';
import { escolher } from '../support/telas.ts';

let contexto: APIRequestContext;
let adm: ClienteApi;

test.beforeAll(async () => {
  contexto = await criarRequest.newContext();
  adm = await clienteApi(contexto);
});

test.afterAll(async () => {
  await contexto.dispose();
});

test.describe('Meios de pagamento', () => {
  test('cadastra a Hotmart com validação, testa credencial errada, ativa e vende uma turma', async ({
    comoAdmin: page,
  }) => {
    const nome = `Hotmart ${stamp()}`;
    await page.goto('/pagamentos');
    await page.getByRole('button', { name: 'Novo meio de pagamento' }).click();
    await page.getByRole('textbox', { name: 'Nome' }).fill(nome);
    await page.getByRole('button', { name: 'Salvar', exact: true }).click();
    await expect(page.getByText('Informe Hottok.')).toBeVisible();

    await page.getByLabel('Hottok').fill('hottok-da-tela-123');
    await page.getByRole('textbox', { name: 'Client ID' }).fill('id-errado');
    await page.getByLabel('Client Secret').fill('segredo-errado');
    await page.getByLabel('Basic').fill('basic-errado');
    await page.getByRole('button', { name: 'Salvar', exact: true }).click();
    const cartao = page.locator('.lp-card').filter({ hasText: nome });
    await expect(cartao.getByText('configurado ••••-123')).toBeVisible();
    await expect(page.getByText('hottok-da-tela-123')).toHaveCount(0);
    await expect(cartao.getByText(/\/api\/pagamentos\/webhooks\//)).toBeVisible();

    await cartao.getByRole('button', { name: 'Testar credenciais' }).click();
    await expect(cartao.getByText('Credenciais recusadas')).toBeVisible();

    await cartao.getByRole('button', { name: 'Ativar' }).click();
    await expect(cartao.getByText('Ativo', { exact: true })).toBeVisible();

    // Aba "Venda" da turma
    const curso = await montarCurso(adm);
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=venda`);
    await page.getByRole('button', { name: 'Nova oferta' }).click();
    await page.getByRole('button', { name: 'Salvar oferta' }).click();
    await expect(page.getByText('Informe o id do produto.')).toBeVisible();
    await page.getByRole('textbox', { name: 'Título' }).fill('À vista');
    await page.getByRole('textbox', { name: 'Preço' }).fill('19700');
    await page.getByRole('textbox', { name: 'Id do produto' }).fill(stamp());
    await page.getByRole('textbox', { name: 'Código da oferta' }).fill('e2e');
    await page.getByRole('button', { name: 'Salvar oferta' }).click();
    await expect(page.getByRole('cell', { name: 'R$ 197,00' })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'À venda' })).toBeVisible();
  });

  test('evento de oferta não cadastrada é ignorado e reprocessado depois; o pedido aparece no painel', async ({
    comoAdmin: page,
    request,
  }) => {
    const hotmart = await hotmartAtiva(adm);
    const curso = await montarCurso(adm);
    const produto = stamp();
    const email = emailNovo();
    await notificar(request, hotmart, {
      evento: 'PURCHASE_APPROVED',
      produto,
      transacao: `HP${stamp()}`,
      email,
    });

    await page.goto('/pagamentos/eventos');
    await escolher(page, 'Situação', 'Ignorado');
    const linha = page.getByRole('row').filter({ hasText: `produto ${produto}` });
    await expect(async () => {
      await page.reload();
      await escolher(page, 'Situação', 'Ignorado');
      await expect(linha).toBeVisible({ timeout: 2_000 });
    }).toPass({ timeout: 30_000 });

    await criarOferta(adm, curso.turmaId, produto);
    await linha.click();
    const dialogo = page.getByRole('dialog', { name: 'PURCHASE_APPROVED' });
    await expect(dialogo.getByText('***09')).toBeVisible();
    await dialogo.getByRole('button', { name: 'Reprocessar' }).click();
    await expect(page.getByText('Evento na fila para reprocessar', { exact: true })).toBeVisible();

    await page.goto('/pagamentos/pedidos');
    await page.getByRole('textbox', { name: 'E-mail' }).fill(email);
    const pedido = page.getByRole('row').filter({ hasText: email });
    await expect(async () => {
      await page.reload();
      await page.getByRole('textbox', { name: 'E-mail' }).fill(email);
      await expect(pedido.getByText('Aprovado')).toBeVisible({ timeout: 2_000 });
    }).toPass({ timeout: 30_000 });
    await pedido.click();
    await expect(page.getByText('Venda sem checkout da plataforma')).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Processado' })).toBeVisible();
  });
});
`````

`web/e2e/aluno/compra.spec.ts`:

`````ts
import { request as criarRequest, type APIRequestContext, type Page } from '@playwright/test';
import { clienteApi, montarCurso, type ClienteApi, type CursoMontado } from '../support/api.ts';
import { SITE_URL } from '../support/config.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA, stamp } from '../support/dados.ts';
import {
  criarOferta,
  hotmartAtiva,
  notificar,
  type HotmartConfigurada,
} from '../support/pagamentos.ts';
import { entrarPelaApi, expect, test } from '../support/sessoes.ts';
import { aceitarTermosSePedir } from '../support/telas.ts';

/**
 * Compra pela Hotmart: o botão "Comprar" do site leva ao checkout (a ida ao pay.hotmart.com é
 * interceptada), a notificação é mandada pelo teste como a Hotmart faria e a página de retorno
 * acompanha o pedido. O curso e a oferta são montados uma vez; cada teste usa um aluno novo.
 */
let contexto: APIRequestContext;
let adm: ClienteApi;
let curso: CursoMontado;
let slug: string;
let hotmart: HotmartConfigurada;
const produto = stamp();

test.beforeAll(async () => {
  contexto = await criarRequest.newContext();
  adm = await clienteApi(contexto);
  curso = await montarCurso(adm);
  slug = (await adm.get<{ slug: string }>(`/api/adm/cursos/${curso.cursoId}`)).slug;
  await adm.patch(`/api/adm/cursos/${curso.cursoId}/status`, { status: 'PUBLICADO' });
  hotmart = await hotmartAtiva(adm);
  await criarOferta(adm, curso.turmaId, produto);
});

test.afterAll(async () => {
  await contexto.dispose();
});

/** Clica em "Comprar" no site e devolve o checkout que iria para a Hotmart. */
async function comprarPeloSite(page: Page, email: string): Promise<URL> {
  let checkout: URL | undefined;
  await page.route('https://pay.hotmart.com/**', async (rota) => {
    checkout = new URL(rota.request().url());
    await rota.fulfill({ contentType: 'text/html', body: '<h1>Checkout da Hotmart</h1>' });
  });
  await expect(async () => {
    await page.goto(`${SITE_URL}/cursos/${slug}/?v=${Date.now()}`);
    await expect(page.getByText('R$ 197,00')).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 30_000 });
  await page.getByRole('link', { name: 'Comprar' }).click();
  await expect(page.getByRole('heading', { name: 'Checkout da Hotmart' })).toBeVisible();
  expect(checkout?.pathname).toBe(`/${produto}`);
  expect(checkout?.searchParams.get('off')).toBe('e2e');
  expect(checkout?.searchParams.get('email')).toBe(email);
  expect(checkout?.searchParams.get('xcod')).toHaveLength(22);
  return checkout!;
}

async function alunoLogado(page: Page, request: APIRequestContext): Promise<string> {
  const email = await alunoConfirmado(request);
  await entrarPelaApi(page, email, SENHA_NOVA);
  return email;
}

test('compra aprovada: retorno confirma, o curso aparece; reembolso tira o curso', async ({
  page,
  request,
}) => {
  const email = await alunoLogado(page, request);
  const checkout = await comprarPeloSite(page, email);
  const pedido = checkout.searchParams.get('xcod')!;
  const transacao = `HP${stamp()}`;

  await notificar(request, hotmart, {
    evento: 'PURCHASE_APPROVED',
    produto,
    transacao,
    xcod: pedido,
  });
  await page.goto(`/pagamento/retorno?pedido=${pedido}`);
  await expect(page.getByRole('heading', { name: 'Pagamento confirmado' })).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole('button', { name: 'Ir para o curso' }).click();
  await aceitarTermosSePedir(page, 'turma');
  await expect(page.getByText('Aula 1')).toBeVisible();

  await page.goto('/');
  await aceitarTermosSePedir(page);
  await expect(page.getByText(curso.titulo)).toBeVisible();

  await notificar(request, hotmart, {
    evento: 'PURCHASE_REFUNDED',
    produto,
    transacao,
    xcod: pedido,
    ocorridoEm: Date.now() + 1_000,
  });
  await expect(async () => {
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Meus cursos' })).toBeVisible();
    await expect(page.getByText(curso.titulo)).toHaveCount(0, { timeout: 1_000 });
  }).toPass({ timeout: 30_000 });
});

test('boleto impresso: a página de retorno mostra que aguarda o pagamento', async ({
  page,
  request,
}) => {
  const email = await alunoLogado(page, request);
  const pedido = (await comprarPeloSite(page, email)).searchParams.get('xcod')!;

  await notificar(request, hotmart, {
    evento: 'PURCHASE_BILLET_PRINTED',
    produto,
    transacao: `HP${stamp()}`,
    xcod: pedido,
  });
  await page.goto(`/pagamento/retorno?pedido=${pedido}`);
  await expect(page.getByRole('heading', { name: 'Aguardando pagamento' })).toBeVisible();
  await expect(page.getByText(/você recebe um e-mail/)).toBeVisible();
});

test('sem login, Comprar leva à entrada e volta para a compra depois de entrar', async ({
  page,
  request,
}) => {
  const email = await alunoConfirmado(request);
  const oferta = (await contexto.get(`${SITE_URL}/api/cursos/${slug}`).then((r) => r.json())) as {
    ofertas: { id: number }[];
  };
  await page.goto(`/comprar/${oferta.ofertas[0]!.id}`);
  await expect(page).toHaveURL(/\/entrar\?voltar=%2Fcomprar%2F/);
  await page.route('https://pay.hotmart.com/**', (rota) =>
    rota.fulfill({ contentType: 'text/html', body: '<h1>Checkout da Hotmart</h1>' }),
  );
  await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(email);
  await page.getByRole('textbox', { name: 'Senha', exact: true }).fill(SENHA_NOVA);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Checkout da Hotmart' })).toBeVisible();
});
`````

`web/e2e/support/pagamentos.ts`:

`````ts
import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { expect, type APIRequestContext } from '@playwright/test';
import type { ClienteApi } from './api.ts';
import { ALUNO_URL } from './config.ts';
import { stamp } from './dados.ts';

/** Fixtures do webhook 2.0.0 da Hotmart, as mesmas dos testes do backend. */
const FIXTURES = new URL(
  '../../../backend/src/test/resources/pagamentos/hotmart/',
  import.meta.url,
);

export interface HotmartConfigurada {
  id: number;
  hottok: string;
  /** Caminho do webhook (`/api/pagamentos/webhooks/<token>`), igual nos hosts do aluno e do ADM. */
  webhook: string;
}

/** Hotmart configurada só com o hottok e ativa (a ativa recebe os novos checkouts). */
export async function hotmartAtiva(adm: ClienteApi): Promise<HotmartConfigurada> {
  const hottok = `hottok-${stamp()}`;
  const gateway = await adm.post<{ id: number; webhookUrl: string }>(
    '/api/adm/pagamentos/gateways',
    {
      tipo: 'HOTMART',
      nome: `Hotmart ${stamp()}`,
      valores: { hottok },
    },
  );
  await adm.post(`/api/adm/pagamentos/gateways/${gateway.id}/ativacao`);
  const webhook = new URL(gateway.webhookUrl).pathname;
  return { id: gateway.id, hottok, webhook };
}

export async function criarOferta(adm: ClienteApi, turmaId: number, produto: string) {
  return adm.post<{ id: number }>(`/api/adm/turmas/${turmaId}/ofertas`, {
    produtoExterno: produto,
    ofertaExterna: 'e2e',
    titulo: 'À vista',
    precoCentavos: 19_700,
    moeda: 'BRL',
  });
}

interface Notificacao {
  evento: string;
  produto: string;
  transacao: string;
  xcod?: string;
  email?: string;
  ocorridoEm?: number;
}

/** Manda à API, pelo Caddy, a notificação da fixture com os dados do teste (como a Hotmart faria). */
export async function notificar(
  request: APIRequestContext,
  hotmart: HotmartConfigurada,
  { evento, produto, transacao, xcod, email, ocorridoEm }: Notificacao,
): Promise<void> {
  const corpo = JSON.parse(readFileSync(new URL(`${evento}.json`, FIXTURES), 'utf8')) as {
    id: string;
    creation_date: number;
    data: {
      product: { id: string | number };
      buyer: { email: string };
      purchase: { transaction: string; offer: { code: string }; origin: { xcod?: string } };
    };
  };
  corpo.id = randomUUID();
  corpo.creation_date = ocorridoEm ?? Date.now();
  corpo.data.product.id = produto;
  corpo.data.purchase.offer.code = 'e2e';
  corpo.data.purchase.transaction = transacao;
  if (xcod) corpo.data.purchase.origin.xcod = xcod;
  else delete corpo.data.purchase.origin.xcod;
  if (email) corpo.data.buyer.email = email;
  const resposta = await request.post(`${ALUNO_URL}${hotmart.webhook}`, {
    data: corpo,
    headers: { 'X-HOTMART-HOTTOK': hotmart.hottok },
  });
  expect(resposta.status(), await resposta.text()).toBe(200);
}
`````

## Antes de executar

Fase 14 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/pagamentos
```

## Orientações para o usuário

Esta etapa tem passos que só o usuário faz, fora do código (no navegador, no painel da Hostinger, no DNS, no GitHub ou na VPS). Depois de gravar os arquivos e passar na **Verificação**, **mostre no terminal**, para o usuário, cada bloco abaixo **exatamente como está**, um de cada vez, na ordem. Depois de cada bloco, pare e espere o usuário confirmar que concluiu antes de mostrar o próximo. Quando ele informar um valor (o IP da VPS, por exemplo), use-o nos blocos seguintes no lugar de `<ip-da-vps>`.

### 1. Chave dos pagamentos e Caddy

```text
==> PAGAMENTOS — ETAPA 1 DE 2: NA VPS (docs/vps.md, seção 11)

1. ANTES do deploy desta etapa, na VPS, como deploy (uma chave diferente por ambiente):

     echo "PAGAMENTOS_CHAVE=$(openssl rand -base64 32)" >> /opt/plataforma-cursos/producao/.env
     echo "PAGAMENTOS_CHAVE=$(openssl rand -base64 32)" >> /opt/plataforma-cursos/homolog/.env

2. Depois do deploy, atualize o Caddy (a homologação passa a aceitar as notificações da Hotmart):
   no SEU COMPUTADOR (não na VPS), na raiz do repositório, com a main atualizada
   (git switch main && git pull):

     VPS=cesar@<ip-da-vps>
     ssh $VPS 'rm -rf /tmp/caddy && mkdir -p /tmp/caddy/sites'
     scp infra/caddy/compose.yml infra/caddy/Caddyfile $VPS:/tmp/caddy/
     scp infra/caddy/sites/producao.caddy infra/caddy/sites/homolog.caddy $VPS:/tmp/caddy/sites/
     ssh -t $VPS 'C=/opt/plataforma-cursos/caddy \
       && sudo cp -p $C/Caddyfile $C/Caddyfile.bak-$(date +%F-%H%M) \
       && sudo cp -r /tmp/caddy/. $C/ && sudo chown -R deploy:deploy $C && rm -rf /tmp/caddy \
       && sudo -u deploy docker compose -f $C/compose.yml --env-file $C/.env --project-directory $C up -d --force-recreate'
     curl -s https://aluno.celke.uno/actuator/health   # {"groups":[...],"status":"UP"}
```

### 2. Hotmart

```text
==> PAGAMENTOS — ETAPA 2 DE 2: HOTMART (docs/pagamentos.md)

1. No ADM → Pagamentos → Meios de pagamento, cadastre a Hotmart (Sandbox na homologação) e copie a
   URL do webhook que aparece pronta.
2. Na Hotmart, em Ferramentas → Webhook, cadastre essa URL (versão 2.0.0) e copie o Hottok para o
   cadastro do ADM; para a reconciliação, informe também as credenciais da API (Ferramentas →
   Credenciais).
3. Em cada turma à venda, crie a oferta (aba Venda) com o produto e a oferta da Hotmart e faça uma
   compra de teste no sandbox.
```

## Verificação

- `./mvnw verify` (unitários, integração, cobertura, Spotless) e as jornadas `-Pe2e` passando.
- `npm run format:check`, `lint`, `typecheck`, `test`, `build` e `npm run e2e` no `web/`.
- Na homologação, com a Hotmart de teste: "Enviar teste" de `PURCHASE_APPROVED`, `PURCHASE_REFUNDED`,
  `PURCHASE_CHARGEBACK` e `PURCHASE_BILLET_PRINTED` aparece em "Eventos recebidos" e muda o pedido como na
  tabela; o histórico de entregas na Hotmart mostra 200 em todos.
- O mesmo evento reenviado pela Hotmart (botão de reenvio) não gera matrícula nem e-mail duplicados.
- Nenhum segredo (hottok, client secret, basic) aparece em resposta da API, log ou no banco em texto puro.

## Depois de executar

```bash
git add backend/ web/ infra/ docs/ .github/ README.md
git commit -m "feat(pagamento): adicionar pagamentos multi-gateway e a Hotmart"
git push -u origin feature/pagamentos
```

Abra o Pull Request de `feature/pagamentos` para `homolog`.
