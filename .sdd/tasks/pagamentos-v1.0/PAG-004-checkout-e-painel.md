# PAG-004 — Checkout, reconciliação e painel

- **Spec:** .sdd/specs/pagamentos.spec.md (v1.0) · atende RF-04, RF-10, RF-11
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** PAG-002, PAG-003

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/pagamento/api/CheckoutController.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/application/CheckoutService.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/application/ReconciliacaoService.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/infra/hotmart/ClienteApiHotmart.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/api/PedidoAdmController.java`
- `web/aluno/src/paginas/pagamento/ComprarPagina.tsx`
- `web/aluno/src/paginas/pagamento/RetornoPagamentoPagina.tsx`
- `web/adm/src/paginas/pagamentos/`

## Descrição técnica

Checkout por redirecionamento com `xcod`; reconciliação ligada por configuração.

## Critérios de aceite

- [x] `ReconciliacaoServiceTest` verde
- [x] `PagamentoE2E`, `web/e2e/aluno/compra.spec.ts` e `adm/pagamentos.spec.ts` verdes
