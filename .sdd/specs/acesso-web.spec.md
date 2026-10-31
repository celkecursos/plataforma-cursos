---
feature: acesso-web
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Acesso pelas telas

## 1. Resumo Executivo

O aluno, em `aluno.celke.uno`, e o administrador, em `adm.celke.uno`, entram pelo navegador e mantêm a
sessão sem perceber as renovações. As telas de entrada, cadastro, confirmação e recuperação de senha
usam o layout da biblioteca de componentes da Celke.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Visitante | Entrar, cadastrar-se, confirmar o e-mail e recuperar a senha na área do aluno |
| Aluno | Usar a área do aluno; é barrado no painel |
| Administrador | Entrar no painel |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE oferecer na área do aluno as telas de entrar, cadastrar-se, confirmar o
  e-mail e recuperar e redefinir a senha, com validação na tela e mensagens em português.
- **RF-02** — O sistema DEVE oferecer no painel a tela de entrar e a moldura de navegação.
- **RF-03** — O sistema DEVE manter a credencial de acesso só em memória e renová-la em silêncio,
  segurando as requisições feitas durante a renovação.
- **RF-04** — O sistema DEVE proteger as rotas por papel, levando o usuário sem sessão para a entrada e
  o aluno para fora do painel.
- **RF-05** — O sistema DEVE levar o aluno com termos pendentes para a tela de aceite.
- **RF-06** — O sistema DEVE servir cada tela pela mesma origem da API, com política de conteúdo que só
  libera a mídia de `media.celke.uno`.
- **RF-07** — O sistema DEVE guardar os arquivos estáticos com cache longo e a página inicial sem cache.

## 4. Modelo de Domínio

- **Sessão na tela:** usuário, papéis, credencial em memória, renovação em andamento.

## 5. Estados e Fluxos

- Sessão: `anônima` → `autenticada` → `renovando` → `autenticada` | `encerrada`.
- **Recarregar a página:** sem credencial em memória → renovação pelo cookie → autenticada ou entrada.

## 6. Contratos de Interface

| Tela | Rota | Usa |
|---|---|---|
| Entrar (aluno) | `/entrar` | sessão (spec `autenticacao`) |
| Cadastro | `/cadastro` | spec `cadastro-e-recuperacao-de-senha` |
| Confirmar e-mail | `/confirmar-email` | link do e-mail |
| Recuperar / redefinir | `/recuperar-senha`, `/redefinir-senha` | link do e-mail |
| Entrar (painel) | `/entrar` em `adm.celke.uno` | sessão |

## 7. Casos de Borda

- Renovação reutilizada (duas abas): a sessão termina e a tela volta à entrada.
- Link de e-mail inválido ou vencido: mensagem clara e caminho para pedir outro.
- Aluno no painel: recusado com a mensagem de acesso negado.

## 8. Premissas Validadas

- O layout vem da biblioteca `@celke/kole-react`, a mesma nos três fronts.

## 9. Riscos

- **Credencial roubada por script:** fica só em memória; a renovação é cookie inacessível a scripts.
