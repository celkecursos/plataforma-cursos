# Fixtures do webhook 2.0.0 da Hotmart

Um arquivo por evento (`<EVENTO>.json`), com dados fictícios. Montados a partir do formato da
documentação oficial; troque pelos corpos recebidos no "Enviar teste" da Hotmart (Ferramentas →
Webhook, versão 2.0.0) sempre que a Hotmart mudar algum campo — sem dados pessoais reais.

- `XCOD_DO_PEDIDO` em `data.purchase.origin.xcod` é trocado pelo código do pedido nos testes.
- `PURCHASE_APPROVED_AFILIADO.json`: como chega ao afiliado/coprodutor (sem `buyer`, sem `price`,
  `creation_date` em texto).
