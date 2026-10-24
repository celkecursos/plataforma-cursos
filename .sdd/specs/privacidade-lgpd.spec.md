---
feature: privacidade-lgpd
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Privacidade (LGPD)

## 1. Resumo Executivo

O aluno exerce os direitos da LGPD pelo próprio perfil: baixa uma cópia dos seus dados e exclui a
conta. O que a lei manda guardar continua guardado pelo prazo legal; o resto é apagado ou anonimizado.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Aluno | Baixar os próprios dados e excluir a própria conta |
| Sistema | Aplicar os prazos de retenção |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE entregar ao aluno uma cópia dos dados em formato estruturado, com a conta e
  uma seção por funcionalidade.
- **RF-02** — O sistema DEVE excluir a conta com a senha e uma confirmação explícita, avisando por
  e-mail.
- **RF-03** — O sistema DEVE, na exclusão, anonimizar a conta e apagar progresso, certificados, foto,
  sessões e segundo fator; pedidos ficam sem e-mail e nome.
- **RF-04** — O sistema DEVE manter pelo prazo legal o que tem obrigação de guarda (registros de acesso,
  auditoria, dados fiscais).
- **RF-05** — O sistema DEVE apagar os registros de tentativa de login com mais de 180 dias.
- **RF-06** — O sistema DEVE oferecer um modelo de política de privacidade com dados, finalidades, bases
  legais e os prazos que o sistema aplica.

## 4. Modelo de Domínio

- **Cópia dos dados:** conta e seções (matrículas, progresso, fórum, certificados, termos, pedidos…).
- **Conta anonimizada:** identificador mantido, dados pessoais removidos.

## 5. Estados e Fluxos

- Conta: `ativa` → `excluída (anonimizada)`.
- **Exclusão:** perfil → privacidade → senha + confirmação → e-mail de aviso → anonimização → sessão
  encerrada.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Baixar meus dados | — | arquivo com os dados |
| Excluir conta | `senha`, `confirmacao` | conta excluída |

O aviso sai de `nao-responda@celke.uno`.

## 7. Casos de Borda

- Senha errada na exclusão: recusada, nada apagado.
- Administrador: não exclui a própria conta por esta tela.
- E-mail reutilizado depois da exclusão: pode criar uma conta nova.

## 8. Premissas Validadas

- Prazos: registros de acesso 180 dias (Marco Civil), auditoria 5 anos, tentativas de login 180 dias.

## 9. Riscos

- **Exclusão por terceiro com sessão aberta:** exige a senha.
- **Dado pessoal esquecido numa funcionalidade nova:** cada funcionalidade declara os próprios dados
  pessoais para a cópia e a exclusão.
