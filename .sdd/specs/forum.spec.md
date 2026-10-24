---
feature: forum
versao: 1.1
status: aprovada
autor: pm-spec-architect
---

# Fórum de dúvidas

## 1. Resumo Executivo

O aluno matriculado pergunta no fórum da turma ou de uma aula, outros alunos respondem e o instrutor
responde pela moderação. O texto é escrito em Markdown e chega ao navegador já filtrado, sem nenhum
HTML perigoso. Mensagens podem ter imagens.

Entra: tópicos, respostas, imagens, moderação e limite de mensagens. Na versão 1.1, as imagens também
podem ser anexadas e vistas nas telas do aluno e da moderação.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Aluno com matrícula vigente | Listar, perguntar, ver e responder tópicos visíveis da turma |
| Administrador (instrutor) | Ver tudo (ocultos inclusive), ocultar, mostrar, fixar e responder como instrutor |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE manter tópicos da turma ou de uma aula da turma; tópico de aula excluída
  passa a ser da turma.
- **RF-02** — O sistema DEVE listar ao aluno os tópicos visíveis, fixados primeiro, com filtro por aula
  e contagem de respostas.
- **RF-03** — O sistema DEVE permitir perguntar e responder só com matrícula vigente na turma.
- **RF-04** — O sistema DEVE converter o Markdown em HTML filtrado por lista fechada de elementos, sem
  eventos, estilos, imagens embutidas ou quadros, com links marcados como externos não seguidos.
- **RF-05** — O sistema DEVE aceitar até 3 imagens por mensagem, enviadas direto ao armazenamento,
  conferindo que a chave é do próprio usuário e regravando-as sem metadados em até 1600 × 1600.
- **RF-06** — O sistema DEVE permitir à moderação ocultar e mostrar tópicos, fixar tópicos, ocultar
  respostas e responder como instrutor, marcando o tópico como respondido.
- **RF-07** — O sistema DEVE tratar tópico ou resposta oculta como inexistente para o aluno.
- **RF-08** — O sistema DEVE limitar cada aluno a 20 perguntas e respostas por hora, respondendo "muitas
  requisições" até a mais antiga sair da janela.
- **RF-09** — O sistema DEVE permitir ao aluno anexar imagens na pergunta e na resposta escolhendo,
  arrastando ou colando um print, com prévia, remoção e progresso do envio.
- **RF-10** — O sistema DEVE conferir tipo (JPEG ou PNG), tamanho (até 5 MB) e quantidade (até 3) em
  todos os caminhos de escolha, antes de enviar.
- **RF-11** — O sistema DEVE enviar as imagens ao armazenamento só ao publicar a mensagem; uma nova
  tentativa envia de novo.
- **RF-12** — O sistema DEVE mostrar as imagens publicadas na pergunta e nas respostas, para o aluno e
  na moderação, com visualização em tela cheia.

## 4. Modelo de Domínio

- **Tópico:** turma, aula (opcional), autor, título, texto (Markdown e HTML filtrado), fixado, oculto,
  respondido.
- **Resposta:** tópico, autor, texto, de instrutor, oculta.
- **Imagem:** tópico **ou** resposta, chave.

## 5. Estados e Fluxos

- Tópico: `aberto` → `respondido`; `visível` ⇄ `oculto`; `fixado` independente.
- **Fluxo:** aluno pergunta → instrutor responde → aluno vê a resposta.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Listar tópicos | turma, aula opcional | tópicos visíveis |
| Perguntar | `titulo`, `texto`, `aulaId`, `anexos` | tópico |
| Responder | `texto`, `anexos` | resposta |
| Pedir envio de imagem | `contentType`, `tamanho` | endereço de envio e chave |
| Moderar | ocultar, mostrar, fixar, responder como instrutor | item atualizado |

As imagens publicadas saem por endereços temporários de `media.celke.uno`.

## 7. Casos de Borda

- Texto com `<script>` ou `onerror`: guardado como texto, nunca executado.
- Chave de imagem de outro usuário: recusada.
- Aluno sem matrícula ou com matrícula vencida: 403.
- 21ª mensagem na hora: 429 com o tempo de espera.
- Imagem arrastada ou colada fora do tipo ou acima do tamanho: recusada na tela, sem envio.
- Publicação que falha depois do envio: a imagem é enviada de novo na próxima tentativa.

## 8. Premissas Validadas

- O fórum é por turma; não há fórum geral da plataforma.

## 9. Riscos

- **XSS guardado:** duas camadas de filtro e só o HTML filtrado sai na resposta.
- **Spam:** limite por aluno e moderação.
