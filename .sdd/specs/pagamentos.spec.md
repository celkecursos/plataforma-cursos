---
feature: pagamentos
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Pagamentos

## 1. Resumo Executivo

O aluno compra um curso pela plataforma: clica em "Comprar", paga no checkout do meio de pagamento e a
plataforma recebe a confirmação e cada mudança de status da compra, criando, mantendo ou cancelando a
matrícula. O primeiro meio de pagamento é a Hotmart, mas o núcleo aceita outros sem mudança: o
administrador escolhe e configura o meio de pagamento pelo painel.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Aluno | Comprar uma oferta, acompanhar o pedido na página de retorno |
| Comprador sem conta | Comprar; recebe a conta criada e um link para criar a senha |
| Meio de pagamento | Notificar eventos da compra |
| Administrador | Configurar meios de pagamento, ofertas por turma, ver pedidos e eventos, reprocessar |
| Worker | Processar as notificações da fila e reconciliar com a API do meio de pagamento |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE suportar vários meios de pagamento por uma porta comum, com um status de
  pedido único para todos, e um meio ativo por vez.
- **RF-02** — O sistema DEVE configurar o meio de pagamento pelo painel, a partir dos campos que ele
  declara, guardando os segredos cifrados e nunca os devolvendo em texto.
- **RF-03** — O sistema DEVE manter ofertas por turma, ligadas ao produto e à oferta do meio de
  pagamento.
- **RF-04** — O sistema DEVE abrir o pedido só com a confirmação do aluno e redirecioná-lo ao checkout
  com o código do pedido; a página de retorno acompanha o status.
- **RF-05** — O sistema DEVE receber as notificações autenticadas pelo segredo do meio de pagamento,
  comparadas em tempo constante, gravar o corpo original e responder logo, processando depois pela
  fila.
- **RF-06** — O sistema DEVE processar cada evento uma vez só (idempotência pelo identificador do
  evento) e tolerar eventos fora de ordem pelas transições permitidas.
- **RF-07** — O sistema DEVE criar a matrícula em compra aprovada, mantê-la nas confirmações e cancelá-la
  em reembolso, chargeback ou cancelamento.
- **RF-08** — O sistema DEVE criar a conta do comprador sem senha e enviar o link de criar senha, nunca a
  senha por e-mail.
- **RF-09** — O sistema DEVE gravar como ignorado e responder com sucesso o evento válido que não se
  aplica (curso não encontrado, evento não tratado), respondendo erro só quando não conseguiu gravar.
- **RF-10** — O sistema DEVE reconciliar os pedidos pela API de vendas do meio de pagamento, ligado por
  configuração.
- **RF-11** — O sistema DEVE mostrar no painel os pedidos com histórico e os eventos recebidos, com a
  opção de reprocessar.

## 4. Modelo de Domínio

- **Meio de pagamento:** tipo, campos de configuração, segredos cifrados, ativo, capacidades
  (`CHECKOUT_REDIRECIONAMENTO`, `CHECKOUT_EMBUTIDO`, `CONSULTA_API`, `REEMBOLSO_API`, `RECORRENCIA`).
- **Oferta:** turma, meio de pagamento, produto e oferta externos, ativa.
- **Pedido:** código, aluno ou comprador, oferta, origem (`CHECKOUT`, `EXTERNO`), status, transação,
  valor, forma de pagamento, histórico.
- **Evento recebido:** identificador externo, tipo, corpo original, status (`RECEBIDO`, `PROCESSADO`,
  `IGNORADO`, `ERRO`), motivo.

## 5. Estados e Fluxos

- Pedido: `CRIADO` → `AGUARDANDO_PAGAMENTO` → `APROVADO` → `CONCLUIDO`; desvios `EM_DISPUTA`,
  `ATRASADO`, `CANCELADO`, `EXPIRADO`; `REEMBOLSADO` e `CHARGEBACK` valem a partir de quase todos.
  Transição não permitida é ignorada e registrada.
- **Compra:** comprar → confirmar → pedido `CRIADO` → checkout → notificação → fila → status →
  matrícula.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Comprar | oferta, confirmação | endereço do checkout |
| Retorno | código do pedido | status |
| Notificação | corpo do meio de pagamento + segredo no cabeçalho | 200 quando gravado |
| Configurar meio | campos declarados | meio configurado (segredos mascarados) |
| Ofertas, pedidos, eventos | filtros | listas paginadas; reprocessar evento |

Na homologação (`homolog-aluno.celke.uno`, restrita por IP), só o caminho da notificação aceita
requisições de fora. A chave que cifra os segredos fica em `PAGAMENTOS_CHAVE`, só no `.env`.

## 7. Casos de Borda

- Mesmo evento entregue duas vezes: processado uma vez.
- Reembolso chegando antes da aprovação: aplicado pelas transições permitidas.
- Evento de afiliado sem comprador nem preço: aceito.
- Status desconhecido do meio de pagamento: registrado sem quebrar o processamento.
- Segredo errado: 401, sem gravar.

## 8. Premissas Validadas

- O meio de pagamento não assina o corpo: o segredo no cabeçalho é a única autenticação.
- Falhas de entrega são reenviadas pelo meio de pagamento, que desativa a notificação se continuar
  falhando.

## 9. Riscos

- **Notificação desativada pelo meio de pagamento:** só responder erro quando não gravou (RF-09).
- **Vazamento das credenciais:** segredos cifrados e mascarados no painel.
