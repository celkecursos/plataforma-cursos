---
feature: catalogo-de-cursos
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Catálogo de cursos

## 1. Resumo Executivo

O administrador monta a estrutura de cada curso — curso, turmas, módulos e aulas — e decide o que é
publicado. O visitante vê no catálogo público apenas os cursos publicados, com o conteúdo da turma
ativa mais recente e a capa.

Entra: gestão de cursos, turmas, módulos e aulas, ordem do conteúdo, capa e catálogo público. Fica de
fora: matrícula (spec `matricula-e-acesso`) e vídeo (spec `envio-de-video`).

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Administrador | Criar, alterar, reordenar, mudar o status e excluir itens vazios |
| Visitante | Ver o catálogo e a página dos cursos publicados |
| Aluno | O mesmo que o visitante; recebe 403 na gestão |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE manter cursos com título, endereço amigável único (gerado do título quando
  não informado) e status `RASCUNHO`, `PUBLICADO` ou `ARQUIVADO`.
- **RF-02** — O sistema DEVE manter turmas de um curso com status `PREPARACAO`, `ATIVA` ou `ENCERRADA`.
- **RF-03** — O sistema DEVE manter módulos de uma turma e aulas de um módulo, com status `ATIVO` ou
  `INATIVO` e a marcação de aula gratuita.
- **RF-04** — O sistema DEVE manter a ordem de módulos e aulas sem lacunas nem repetições: item novo
  entra no fim, exclusão refaz a sequência e a reordenação recebe a lista completa.
- **RF-05** — O sistema DEVE excluir apenas itens vazios (curso sem turmas, turma sem módulos, módulo sem
  aulas); os demais são arquivados, encerrados ou inativados.
- **RF-06** — O sistema DEVE receber a capa do curso direto no armazenamento (JPEG ou PNG até 5 MB) e
  guardá-la reduzida para até 1280 × 720, sem metadados, apagando o original e a capa anterior.
- **RF-07** — O sistema DEVE listar no catálogo público só os cursos publicados, em ordem alfabética,
  paginados.
- **RF-08** — O sistema DEVE mostrar na página do curso os módulos e as aulas ativos da turma ativa mais
  recente, sem dados internos; rascunho e arquivado respondem "não encontrado".
- **RF-09** — O sistema DEVE oferecer um endereço estável para a capa que leva a uma cópia de acesso
  temporário.

## 4. Modelo de Domínio

- **Curso:** título, endereço amigável, descrição, status, capa.
- **Turma:** curso, nome, status.
- **Módulo:** turma, título, posição, status.
- **Aula:** módulo, título, descrição, posição, status, gratuita.

## 5. Estados e Fluxos

- Curso: `RASCUNHO` ⇄ `PUBLICADO` → `ARQUIVADO`.
- Turma: `PREPARACAO` → `ATIVA` → `ENCERRADA`.
- **Montagem:** curso → turma → módulos → aulas → capa → publicar.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Criar curso | `titulo`, `slug` opcional, `descricao` | curso |
| Mudar status | `status` | item atualizado |
| Reordenar | lista completa de ids | ordem nova |
| Pedir envio da capa | `contentType`, `tamanho` | endereço temporário de envio e chave |
| Confirmar capa | `chave` | curso com capa |
| Catálogo | página | cursos publicados |
| Página do curso | endereço amigável | curso, módulos e aulas ativos |

A capa e os arquivos ficam em `media.celke.uno`.

## 7. Casos de Borda

- Endereço amigável repetido: 409.
- Reordenação com id faltando, sobrando ou repetido: 400.
- Duas criações simultâneas no mesmo módulo: posições distintas, sem conflito.
- Capa com chave de outro curso, tamanho diferente do informado ou lado acima de 8000 px: recusada.
- Imagem com orientação nos metadados ou transparência: regravada corretamente.

## 8. Premissas Validadas

- Um curso pode ter várias turmas; o catálogo mostra a ativa mais recente.
- Nada é apagado se tiver histórico: arquivar e encerrar preservam matrículas.

## 9. Riscos

- **Imagem maliciosa ou gigante:** limites de tamanho e de lado antes de decodificar.
- **Aluno alcançar a gestão:** papel conferido na entrada e em cada operação.
