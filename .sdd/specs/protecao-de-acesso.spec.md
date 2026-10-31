---
feature: protecao-de-acesso
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Proteção de acesso

## 1. Resumo Executivo

Toda rota da API nasce fechada e cada papel só alcança o que lhe pertence. As rotas de sessão ficam
protegidas contra força bruta, contra excesso de requisições e contra requisições forjadas por outras
aplicações do mesmo domínio (`*.celke.uno` conta como o mesmo site para o navegador). Toda tentativa
de login fica registrada.

Entra: autorização por papel, limite por IP, bloqueio temporário por falhas, conferência da origem nas
rotas de sessão, auditoria de login e respostas de erro padronizadas. Fica de fora: segundo fator
(spec `seguranca-de-contas`).

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Visitante | Apenas as rotas públicas declaradas |
| Aluno | Rotas do aluno e da própria sessão |
| Administrador | Rotas de administração, conferidas duas vezes (na entrada e na operação) |
| Atacante | Nada além do visitante; é limitado e registrado |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE negar por padrão toda rota não declarada como pública, com o método
  declarado junto da rota.
- **RF-02** — O sistema DEVE limitar por IP as requisições às rotas de sessão e de conta, respondendo
  "muitas requisições" com o tempo de espera.
- **RF-03** — O sistema DEVE bloquear o login por 15 minutos após 5 falhas seguidas do mesmo e-mail no
  mesmo IP ou 20 falhas do mesmo e-mail em qualquer IP, verificando o bloqueio antes de conferir a
  senha.
- **RF-04** — O sistema DEVE zerar os contadores de falha com um login correto ou após 1 hora sem
  falhas.
- **RF-05** — O sistema DEVE recusar renovação e saída vindas de origem fora da lista permitida
  (`https://aluno.celke.uno`, `https://adm.celke.uno` e as equivalentes da homologação) ou marcadas pelo
  navegador como de outro site.
- **RF-06** — O sistema DEVE registrar toda tentativa de login com resultado, IP, navegador e data,
  mesmo quando a operação principal falha.
- **RF-07** — O sistema DEVE aceitar o IP do cliente informado pelo proxy apenas quando vier do proxy
  interno.
- **RF-08** — O sistema DEVE responder todo erro (inclusive 401, 403 e 429) num formato único com tipo,
  título, detalhe, caminho e identificador da requisição; erros de validação trazem a lista de campos
  com a mensagem em português.

## 4. Modelo de Domínio

- **Tentativa de login:** e-mail informado, usuário (se existir), resultado (`SUCESSO`,
  `EMAIL_DESCONHECIDO`, `SENHA_INVALIDA`, `USUARIO_INATIVO`, `BLOQUEADO`…), IP, navegador, data.
- **Contador de falhas:** por e-mail + IP e por e-mail, com a última falha.

## 5. Estados e Fluxos

- Conta: `liberada` → `bloqueada temporariamente` (limite atingido) → `liberada` (15 min depois).
- **Requisição:** limite por IP → autenticação → papel → operação → resposta (ou erro padronizado).

## 6. Contratos de Interface

| Situação | Resposta |
|---|---|
| Sem credencial em rota privada | 401 no formato único |
| Papel insuficiente | 403 no formato único |
| Limite por IP | 429 com `Retry-After` |
| Bloqueio por falhas | mesma resposta de credencial inválida, sem revelar o bloqueio a quem não sabe a senha |
| Origem não permitida | 403 |

## 7. Casos de Borda

- Cabeçalho de IP forjado pelo cliente: ignorado.
- Origem com maiúsculas, barra final ou porta diferente: normalizada antes de comparar; porta ou
  esquema diferentes são recusados.
- Outra aplicação de `celke.uno` tentando renovar a sessão: recusada (RF-05).
- Falha ao gravar a auditoria não muda a resposta do login.

## 8. Premissas Validadas

- A API fica sempre atrás de um proxy próprio, que substitui o IP informado pelo cliente.
- O domínio `celke.uno` é compartilhado com outras aplicações.

## 9. Riscos

- **Bloqueio usado para negar acesso a um aluno:** limitado a 15 minutos e só após muitas falhas.
- **Rota nova esquecida como pública:** mitigado pela negação por padrão (RF-01).
