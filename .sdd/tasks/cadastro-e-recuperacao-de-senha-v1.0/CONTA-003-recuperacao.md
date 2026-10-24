# CONTA-003 — Recuperação e redefinição de senha

- **Spec:** .sdd/specs/cadastro-e-recuperacao-de-senha.spec.md (v1.0) · atende RF-05, RF-06
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
- **Depende de:** CONTA-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/auth/application/RecuperacaoSenhaService.java`
- `backend/src/main/resources/templates/email/recuperacao-senha.html`

## Descrição técnica

Link de 1 h; redefinir troca a senha, confirma o e-mail e encerra todas as sessões.

## Restrições

Mesma resposta exista ou não a conta.

## Critérios de aceite

- [x] `RecuperacaoSenhaServiceTest` verde
- [x] `RecuperacaoSenhaIT` verde

## Armadilhas

Esquecer de encerrar as sessões deixa o invasor logado.
