# PAG-002 — Configuração pelo painel e ofertas

- **Spec:** .sdd/specs/pagamentos.spec.md (v1.0) · atende RF-02, RF-03
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Concluída
- **Depende de:** PAG-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/pagamento/application/ConfiguracaoGatewayService.java`
- `backend/src/main/java/br/com/celke/cursos/shared/cripto/CifraSegredos.java` — AES-GCM
- `backend/src/main/java/br/com/celke/cursos/pagamento/api/GatewayAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/api/OfertaAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/pagamento/application/OfertaAdmService.java`

## Descrição técnica

Formulário montado dos campos declarados; segredos cifrados com `PAGAMENTOS_CHAVE` e mascarados.

## Critérios de aceite

- [x] `ConfiguracaoGatewayServiceTest`, `CifraSegredosTest` verdes
- [x] `GatewayAdmIT` verde

## Armadilhas

Devolver o segredo no GET de edição.
