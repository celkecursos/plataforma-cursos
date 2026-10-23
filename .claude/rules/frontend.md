---
paths:
  - "web/**"
description: Padrões dos fronts (área do aluno e ADM em React) e do site público (React com renderização no servidor). Carrega ao tocar web/.
---

# Fronts e site (`web/`)

## Stack

React 19 + TypeScript + Vite, componentes da biblioteca `@celke/kole-react`, Vitest + Testing Library
nos testes de unidade e Playwright nos e2e. Comandos dentro de `web/`:

```bash
npm run format:check && npm run lint && npm run typecheck && npm test && npm run build
```

## Estrutura

```
web/
  compartilhado/src/   cliente da API, sessão, componentes e utilitários usados pelos dois fronts
  aluno/src/           área do aluno: rotas, layout, páginas
  adm/src/             ADM: rotas, layout, páginas
  site/                site público: React Router em modo framework, renderizado no servidor (SEO)
  scripts/             scripts de apoio rodados no Node (ex.: cursos de exemplo pela API do ADM)
  e2e/                 Playwright: specs por projeto (aluno, adm, site) e apoio em support/
```

## Regras

- **Use o componente da biblioteca** (`@celke/kole-react`) antes de criar um; não reestilize por dentro
  as classes internas dela. Faltou algo? componha por fora ou pergunte.
- **Toda chamada à API passa pelo cliente de `compartilhado/src/api`**: ele põe o token, renova a
  sessão pelo cookie e transforma o `ProblemDetail` em erro tipado. Nada de `fetch` solto. Exceção: o
  site (no servidor, em `site/app/api.server.ts`) e os `scripts/`, que rodam no Node e buscam a API direto.
- **Access token só em memória**; o refresh fica no cookie `HttpOnly`. Nunca `localStorage` para token.
- Erro de validação do backend (`erros[]` com `campo`) aparece no campo certo do formulário.
- Toda tela tem os estados carregando, vazio e erro.
- Texto da tela em português; rótulo de campo e nome de botão são o que o e2e usa como seletor —
  mudou por UX, conserte o teste, não volte o rótulo.
- Token ou dado sensível que chega pela URL (link de e-mail) sai da barra de endereço depois de lido.
- Redirecionamento externo só para destino conhecido; parâmetro `?volta=` só aceita caminho interno.
- Lógica pura (formatação, regra de exibição, montagem de caminhos) fica em função testável com teste
  `*.test.ts` ao lado.

## e2e

Guia completo em `web/e2e/README.md`. Seletores pelo que o usuário vê (`getByRole`, `getByLabel`),
espera por condição (nunca por tempo fixo), dado de teste com carimbo único, contas do seed só leitura.
