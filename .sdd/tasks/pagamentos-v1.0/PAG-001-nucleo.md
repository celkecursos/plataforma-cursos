# PAG-001 — Porta de meio de pagamento e pedido

- **Spec:** .sdd/specs/pagamentos.spec.md (v1.0) · atende RF-01, RF-06
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/pagamento/domain/GatewayPagamento.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/domain/Pedido.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/domain/StatusPedido.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/domain/TransicoesPedido.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/domain/EventoPagamento.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/domain/Capacidade.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/application/GatewaysRegistrados.java`

## Descrição técnica

Um bean por meio de pagamento; transições permitidas toleram fora de ordem.

## Critérios de aceite

- [x] `TransicoesPedidoTest` verde

## Armadilhas

Status específico do meio vazando para o núcleo.
