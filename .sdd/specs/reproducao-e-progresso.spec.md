---
feature: reproducao-e-progresso
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Reprodução de vídeo e progresso

## 1. Resumo Executivo

O aluno com acesso assiste ao vídeo por uma lista de reprodução gerada a cada pedido, com endereços
temporários para cada trecho: ninguém acessa o armazenamento direto. O sistema guarda onde o aluno
parou e quais aulas viu, e mostra o percentual da turma.

Entra: lista de reprodução protegida, progresso por aula e por turma, registro de acesso ao vídeo. Fica
de fora: criptografia do vídeo.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Aluno com acesso | Assistir, salvar posição, marcar vista e "revisar" |
| Visitante | Assistir só aulas gratuitas abertas |
| Worker | Apagar os registros de acesso vencidos |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE entregar a lista de reprodução só a quem tem acesso à aula, pela regra
  única da spec `matricula-e-acesso`.
- **RF-02** — O sistema DEVE gerar endereços temporários dos trechos válidos pela duração do vídeo mais
  uma margem de 2 horas, sem guardar a lista em cache.
- **RF-03** — O sistema DEVE aceitar na lista apenas nomes de trechos no formato produzido pela
  conversão.
- **RF-04** — O sistema DEVE guardar, por aluno e aula, a posição, se foi vista e se está marcada para
  revisar, alterando só os campos enviados.
- **RF-05** — O sistema DEVE calcular o percentual da turma pelas aulas ativas vistas, arredondado para
  baixo (turma sem aulas = 0%), exigindo matrícula vigente.
- **RF-06** — O sistema DEVE registrar cada abertura do vídeo com usuário, aula, IP e navegador, e apagar
  os registros após 180 dias.

## 4. Modelo de Domínio

- **Progresso da aula:** aluno, aula, posição em segundos, vista, revisar, atualizado em.
- **Acesso ao vídeo:** usuário, aula, IP, navegador, data.

## 5. Estados e Fluxos

- Aula para o aluno: `não iniciada` → `em andamento` → `vista`; `revisar` é marcação independente.
- **Reprodução:** abrir aula → lista principal → lista da variante → trechos temporários → posição
  enviada periodicamente.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Lista principal | aula | variantes |
| Lista da variante | aula, variante | trechos com endereço temporário |
| Progresso da aula | `posicaoSegundos`, `vista`, `revisar` | progresso |
| Progresso da turma | turma | percentual e aulas vistas |

## 7. Casos de Borda

- Trecho pedido depois do prazo: recusado pelo armazenamento.
- Lista adulterada (nome fora do padrão): recusada.
- Progresso de outro aluno: nunca visível.

## 8. Premissas Validadas

- Os 180 dias do registro de acesso seguem o Marco Civil da Internet.

## 9. Riscos

- **Compartilhamento do endereço do trecho:** validade limitada à duração do vídeo + margem.
