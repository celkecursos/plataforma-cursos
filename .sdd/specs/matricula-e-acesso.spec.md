---
feature: matricula-e-acesso
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Matrícula e acesso ao conteúdo

## 1. Resumo Executivo

O administrador matricula alunos em turmas com um período, e uma única regra decide se o aluno vê cada
aula. Vídeo, arquivos, fórum, progresso e certificado consultam essa mesma regra, então o acesso nunca
diverge entre as partes do sistema.

Entra: matrícula pelo administrador, período, cancelamento, regra de acesso e "meus cursos" do aluno.
Fica de fora: matrícula por compra (spec `pagamentos`).

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Administrador | Listar, matricular por e-mail, alterar o período e cancelar matrículas |
| Aluno | Ver os próprios cursos por aba, a turma e as aulas liberadas, arquivar e desarquivar |
| Visitante | Ver só as aulas gratuitas abertas |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE matricular um usuário existente numa turma por e-mail, com início (padrão:
  agora) e fim opcional (sem prazo).
- **RF-02** — O sistema DEVE recusar matrícula em turma encerrada, para usuário inexistente, repetida
  enquanto houver uma ativa e com transação de pagamento já usada.
- **RF-03** — O sistema DEVE calcular a situação da matrícula pelo relógio — aguardando início,
  vigente, vencida, cancelada — sem gravar "vencida", com o fim inclusivo.
- **RF-04** — O sistema DEVE liberar uma aula quando houver matrícula vigente na turma ou quando a aula
  for gratuita, a turma estiver ativa e o curso publicado; a turma inteira só com matrícula vigente.
- **RF-05** — O sistema DEVE oferecer um único ponto de decisão de acesso, usado por todas as
  funcionalidades de conteúdo.
- **RF-06** — O sistema DEVE mostrar ao aluno os cursos em abas: ativos (inclusive os que ainda vão
  começar), arquivados e expirados; cancelados não aparecem.
- **RF-07** — O sistema DEVE permitir ao aluno arquivar e desarquivar as próprias matrículas.
- **RF-08** — O sistema DEVE tratar matrícula de outro aluno como inexistente.
- **RF-09** — O sistema DEVE impedir a exclusão de turma com matrículas.

## 4. Modelo de Domínio

- **Matrícula:** usuário, turma, início, fim (opcional), status (`ATIVA`, `CANCELADA`), forma de
  pagamento (`MANUAL`, `CORTESIA`, `PIX`, `CARTAO`, `BOLETO`), transação, arquivada.

## 5. Estados e Fluxos

- Status gravado: `ATIVA` → `CANCELADA`.
- Situação calculada: `aguardando início` → `vigente` → `vencida`.
- **Acesso:** pedido do aluno → regra única → liberado ou 403.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Matricular | `email`, `inicio`, `fim`, `formaPagamento` | matrícula |
| Alterar período | `inicio`, `fim` | matrícula |
| Cancelar | — | matrícula cancelada |
| Meus cursos | aba (`ATIVOS`, `ARQUIVADOS`, `EXPIRADOS`) | cursos com situação |
| Turma do aluno | turma | módulos e aulas ativos |

## 7. Casos de Borda

- Instante exatamente no início ou no fim: tratado como vigente.
- Aula inativa, curso em rascunho ou outra turma: sem acesso, mesmo com matrícula.
- Duas matrículas simultâneas do mesmo aluno na mesma turma: só uma ativa.

## 8. Premissas Validadas

- Não existe tarefa agendada para vencer matrículas: a situação é sempre calculada.
- O identificador da matrícula de outro aluno responde como não encontrado, sem revelar que existe.

## 9. Riscos

- **Regra de acesso duplicada e divergente:** mitigada pelo ponto único (RF-05).
- **Acesso por identificador alheio:** coberto pelo RF-08 e por testes com outro aluno.
