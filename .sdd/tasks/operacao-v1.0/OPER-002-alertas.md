# OPER-002 — Alertas por e-mail

- **Spec:** .sdd/specs/operacao.spec.md (v1.0) · atende RF-03
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/shared/operacao/AlertasOperacao.java`
- `backend/src/main/java/br/com/celke/cursos/shared/operacao/AlertasProperties.java`
- `backend/src/main/java/br/com/celke/cursos/shared/operacao/MedidorDisco.java`
- `backend/src/main/resources/templates/email/alerta-operacao.html`

## Descrição técnica

A cada 15 minutos: jobs em erro e disco abaixo de 15% livre, para `ALERTA_EMAIL`.

## Critérios de aceite

- [x] `AlertasOperacaoTest` verde

## Armadilhas

Repetir o mesmo alerta a cada verificação.
