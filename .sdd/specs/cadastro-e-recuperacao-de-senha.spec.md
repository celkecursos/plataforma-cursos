---
feature: cadastro-e-recuperacao-de-senha
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Cadastro e recuperação de senha

## 1. Resumo Executivo

O visitante cria a própria conta de aluno e a ativa pelo link enviado ao e-mail. Quem esquece a senha
recebe um link para definir outra. As respostas nunca revelam se um e-mail já tem conta, e os e-mails
saem pela fila, a partir do remetente `nao-responda@celke.uno`, com links para `https://aluno.celke.uno`.

Entra: cadastro, confirmação de e-mail, reenvio da confirmação, recuperação e redefinição de senha.
Fica de fora: troca de senha logado (spec `perfil-e-termos`).

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Visitante | Cadastrar-se, confirmar o e-mail, pedir reenvio, pedir e concluir a recuperação de senha |
| Aluno | O mesmo; não precisa estar logado |
| Worker | Montar e enviar os e-mails da fila |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE criar a conta de aluno pendente de confirmação e enviar um link de
  confirmação válido por 48 horas.
- **RF-02** — O sistema DEVE responder igual ao cadastro de e-mail novo e de e-mail já cadastrado:
  conta ativa recebe um aviso (no máximo um por hora); conta pendente recebe o link de novo.
- **RF-03** — O sistema DEVE ativar a conta ao abrir um link de confirmação válido.
- **RF-04** — O sistema DEVE recusar o login de conta pendente com resposta própria somente quando a
  senha estiver certa.
- **RF-05** — O sistema DEVE enviar um link de redefinição válido por 1 hora, com a mesma resposta
  exista ou não a conta.
- **RF-06** — O sistema DEVE, ao redefinir a senha, trocar a senha, confirmar o e-mail e encerrar todas
  as sessões.
- **RF-07** — O sistema DEVE tornar cada link de uso único: um link novo invalida os anteriores do
  mesmo tipo.
- **RF-08** — O sistema DEVE limitar a 3 e-mails por hora por conta e por tipo.
- **RF-09** — O sistema DEVE exigir senha de aluno com 10 a 128 caracteres.
- **RF-10** — O sistema DEVE enviar os e-mails de forma assíncrona, sem atrasar a resposta.

## 4. Modelo de Domínio

- **Usuário:** e-mail confirmado em (vazio = pendente).
- **Link de uso único:** usuário, tipo (`CONFIRMACAO_EMAIL`, `RECUPERACAO_SENHA`), hash do código,
  validade, usado em.
- **E-mail:** destinatário, modelo (`confirmacao-email`, `recuperacao-senha`, `cadastro-existente`),
  variáveis.

## 5. Estados e Fluxos

- Conta: `pendente` → `confirmada`.
- Link: `válido` → `usado` | `vencido` | `substituído`.
- **Cadastro:** dados → conta pendente → e-mail na fila → aluno abre o link → conta confirmada → login.
- **Recuperação:** e-mail → link na fila → nova senha → sessões encerradas → login.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Cadastrar | `nome`, `email`, `senha` | mensagem genérica |
| Confirmar e-mail | `token` | confirmado ou link inválido |
| Reenviar confirmação | `email` | mensagem genérica |
| Recuperar senha | `email` | mensagem genérica |
| Redefinir senha | `token`, `senha` | senha trocada ou link inválido |

Links dos e-mails: `https://aluno.celke.uno/...` (homologação: `https://homolog-aluno.celke.uno/...`).

## 7. Casos de Borda

- Link aberto duas vezes: a segunda é recusada.
- Pedidos repetidos de recuperação: só o último link vale; acima de 3 por hora, nenhum e-mail sai.
- Contas criadas pelo terminal ou pelos dados de teste nascem confirmadas.
- Nome com HTML: aparece escapado no e-mail.

## 8. Premissas Validadas

- Os e-mails de produção saem pela Iagente, com SPF e DKIM configurados em `celke.uno`.
- No ambiente local e nos testes, os e-mails são capturados sem envio real.

## 9. Riscos

- **E-mail no spam:** mitigado pelo remetente do próprio domínio com SPF e DKIM.
- **Envio usado para incomodar terceiros:** limitado a 3 por hora por conta e por tipo, além do limite
  por IP.
