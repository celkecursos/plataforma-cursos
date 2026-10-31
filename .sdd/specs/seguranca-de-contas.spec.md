---
feature: seguranca-de-contas
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Segurança das contas

## 1. Resumo Executivo

Os administradores entram com um segundo fator, as senhas fracas ou vazadas são recusadas, o banco é
usado pela aplicação com o mínimo de privilégio e o administrador consegue bloquear um usuário e
encerrar as sessões dele na hora.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Administrador | Cadastrar e usar o segundo fator; buscar, bloquear, desbloquear e encerrar sessões de usuários |
| Aluno | Ver o medidor de força da senha |
| Operador do servidor | Redefinir o segundo fator de um administrador pelo terminal |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE exigir dos administradores um segundo fator por código temporário
  (aplicativo autenticador), pedido depois da senha, com cadastro por QR code.
- **RF-02** — O sistema DEVE fornecer códigos de recuperação de uso único e recusar a reutilização de um
  código temporário.
- **RF-03** — O sistema DEVE guardar o segredo do segundo fator cifrado (`TOTP_CHAVE`, só no `.env`).
- **RF-04** — O sistema DEVE permitir redefinir o segundo fator de um administrador pelo terminal do
  servidor.
- **RF-05** — O sistema DEVE recusar senhas comuns ou vazadas, que contenham o nome ou o e-mail, ou
  feitas de sequências e repetições, e mostrar um medidor de força nas telas.
- **RF-06** — O sistema DEVE usar no dia a dia um usuário do banco que só lê e grava dados, deixando as
  mudanças de estrutura para um serviço próprio de migração.
- **RF-07** — O sistema DEVE limitar por usuário as gravações e o checkout.
- **RF-08** — O sistema DEVE permitir ao administrador buscar, bloquear e desbloquear usuários e encerrar
  as sessões deles, invalidando na hora a credencial de acesso já emitida, com registro na auditoria.
- **RF-09** — O sistema DEVE abrir o checkout só por HTTPS nos domínios do meio de pagamento.
- **RF-10** — O sistema DEVE retirar da barra de endereço o código dos links de e-mail depois de lido.
- **RF-11** — O sistema DEVE verificar automaticamente que só as rotas públicas declaradas respondem sem
  login.
- **RF-12** — O sistema DEVE manter as dependências monitoradas e auditar as do front no pipeline.

## 4. Modelo de Domínio

- **Segundo fator:** usuário, segredo cifrado, último código usado, ativo.
- **Código de recuperação:** usuário, hash, usado em.
- **Usuário:** bloqueado, sessões invalidadas em.

## 5. Estados e Fluxos

- Login do administrador: senha → desafio → código ou recuperação → sessão.
- Usuário: `ativo` ⇄ `bloqueado`.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Responder desafio | desafio, `codigo` | sessão |
| Cadastrar segundo fator | desafio | QR code; depois `codigo` → códigos de recuperação |
| Bloquear / desbloquear | usuário | usuário |
| Encerrar sessões | usuário | sessões encerradas |

## 7. Casos de Borda

- Mesmo código usado duas vezes na janela: recusado.
- Código de recuperação usado: não vale de novo.
- Administrador que perdeu o aplicativo: redefinição pelo terminal (RF-04).

## 8. Premissas Validadas

- O segundo fator é obrigatório só para administradores.

## 9. Riscos

- **Senha de administrador vazada:** segundo fator obrigatório.
- **Injeção que altere a estrutura do banco:** a aplicação não tem esse privilégio.
