---
feature: auditoria
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Auditoria

## 1. Resumo Executivo

Toda alteração de dados fica registrada — quem mudou, o quê, antes e depois, de onde e quando — numa
trilha que não pode ser editada nem apagada, e o administrador a consulta pelo painel. Os registros
técnicos da API e do worker também ficam guardados em arquivo.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Administrador | Consultar a trilha com filtros e ver o antes e depois lado a lado |
| Sistema | Registrar cada alteração e apagar o que passou do prazo |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE registrar, na mesma transação da mudança, o usuário, a operação, a entidade
  e o identificador, o antes e o depois (só o que mudou), a origem (rota ou tarefa), o IP, o navegador,
  o identificador da requisição e a data.
- **RF-02** — O sistema DEVE mascarar os dados sensíveis e deixar fora da trilha as entidades de registro
  e de sessão.
- **RF-03** — O sistema DEVE impedir no banco a alteração e a exclusão dos registros, só permitindo
  inclusão.
- **RF-04** — O sistema DEVE apagar os registros com mais de 5 anos.
- **RF-05** — O sistema DEVE oferecer a consulta com filtros (usuário, entidade, operação, período) e o
  antes e depois lado a lado.
- **RF-06** — O sistema DEVE guardar os registros técnicos da API e do worker em arquivo, um por dia, por
  180 dias ou 2 GB por serviço.

## 4. Modelo de Domínio

- **Registro de auditoria:** usuário, operação, entidade, identificador, antes, depois, origem, IP,
  navegador, requisição, data.

## 5. Estados e Fluxos

- **Mudança:** operação → registro na mesma transação → consulta no painel.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Consultar | filtros, página | registros |
| Detalhe | registro | antes e depois |

## 7. Casos de Borda

- Mudança desfeita (transação revertida): nenhum registro.
- Senha e segredos: aparecem como `***`.
- Tentativa de apagar registro direto no banco: recusada.

## 8. Premissas Validadas

- O prazo de 5 anos atende às obrigações de guarda dos registros financeiros e de matrícula.

## 9. Riscos

- **Crescimento da trilha:** só o que mudou e retenção de 5 anos.
- **Dado pessoal na trilha:** mascaramento dos campos sensíveis.
