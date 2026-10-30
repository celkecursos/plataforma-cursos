# PAG-003 — Notificações e processamento

- **Spec:** .sdd/specs/pagamentos.spec.md (v1.0) · atende RF-05, RF-06, RF-07, RF-08, RF-09
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** PAG-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/pagamento/api/WebhookController.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/api/LimiteWebhookFilter.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/application/WebhookService.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/application/ProcessarEventoManipulador.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/application/ProcessamentoEventoService.java`
- `backend/src/main/java/br/com/celke/cursos/matricula/application/MatriculaPagamentoService.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/infra/hotmart/GatewayHotmart.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/infra/hotmart/InterpretadorHotmart.java`

## Descrição técnica

Webhook 2.0.0 com `X-HOTMART-HOTTOK` comparado em tempo constante; grava o corpo e processa pela fila; conta do comprador sem senha com link de criar senha.

## Restrições

Só responde erro quando não gravou.

## Critérios de aceite

- [x] `InterpretadorHotmartTest`, `ProcessamentoEventoServiceTest` verdes
- [x] `WebhookIT` e `PagamentoFluxoIT` verdes

## Armadilhas

Responder 4xx para curso não encontrado desativa o webhook.
