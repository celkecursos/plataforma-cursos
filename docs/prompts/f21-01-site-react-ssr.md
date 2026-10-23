# Site em React com renderização no servidor

**Fase:** 21
**Branch:** `feature/site-layout`

## Prompt

Vamos trocar o site público gerado de tempos em tempos por um site em React renderizado no servidor a
cada requisição: o conteúdo aparece assim que é publicado no ADM e os buscadores continuam recebendo o
HTML completo. Junto, o layout fica moderno. Siga as rules de `.claude/rules/` e os padrões do `web/`.
Não faça commit.

Entregas:

1. **Site em React Router 8 (modo framework)** com renderização no servidor, em `web/site/app/`.
2. **Container `site`** como servidor Node na porta 3000; o `web` deixa de servir o site (8083).
3. **Layout moderno** com os tokens da `@celke/kole-react` e tema escuro.
4. **Favicon** da `@celke/kole-react` nos três fronts.
5. **Cursos de exemplo** criados pela API do ADM (`npm run cursos-exemplo`).

### 1. Site (`web/site/`)

- `react-router.config.ts` (`appDirectory: 'app'`, `buildDirectory: '../dist/site'`, `ssr: true`) e
  `vite.config.ts` (porta 5175, proxy de `/api`). Scripts `dev:site`, `start:site` e o build do site no
  fim do `build`. Dependências: `@react-router/node`, `@react-router/serve`, `isbot` e, de
  desenvolvimento, `@react-router/dev`.
- `seo.ts` (funções puras, com teste): tipos da API, `metaTags` (título, descrição, canônico, Open
  Graph, `noindex` fora da produção), `dadosCurso` (schema.org `Course` com as ofertas), `jsonEmScript`,
  `filtrarCursos` (sem acento e sem diferenciar maiúsculas, todas as palavras), `paragrafos`, `sitemap`
  e `robots`.
- `config.server.ts`: `API_URL`, `SITE_URL` (em branco: derivado de `FRONT_ALUNO_URL`, `aluno.` →
  `cursos.`), `SITE_INDEXAVEL` e `SITE_CACHE_SEGUNDOS` (30 s; 1 s no e2e).
- `api.server.ts`: leituras públicas da API com cache curto em memória e tempo limite de 10 s; 404 da
  API vira 404 da página; erro não fica no cache.
- `entry.server.tsx`: streaming para o navegador e a página inteira para os robôs (`isbot`); CSP com
  nonce por requisição (`script-src 'self' 'nonce-…'`, imagens de `MIDIA_ORIGEM`) e
  `X-Frame-Options: DENY`.
- `root.tsx`: layout (topo fixo com "Cursos", "Validar certificado" e "Entrar"; rodapé em três colunas),
  `loader` com o contexto do ambiente e `ErrorBoundary` (404 e 500 com `noindex`).
- Rotas: home, `/cursos/` (com a busca), `/cursos/:slug/`, `/certificados/validar/` (formulário GET,
  funciona sem JavaScript), `/termos/`, `/privacidade/`, `sitemap.xml`, `robots.txt`, `/saude` e o
  coringa que responde 404. Cache público de 60 s (`stale-while-revalidate`) nas páginas.
- Os textos que o Playwright usa continuam os mesmos ("Criar conta", "Quero fazer este curso", "Já sou
  aluno", "Certificado válido." etc.).

### 2. Infraestrutura

- `web/Dockerfile`: o estágio `site` vira `node:24-alpine` com `node_modules` de produção e
  `dist/site`, porta 3000, `HEALTHCHECK` em `/saude`. O `web` expõe só 8081 e 8082; o bloco `:8083`
  sai do `web/Caddyfile`.
- `infra/compose.prod.yml`: o serviço `site` recebe `API_URL`, `FRONT_ALUNO_URL`, `SITE_URL`,
  `SITE_INDEXAVEL` e `MIDIA_ORIGEM`, apelido `<ambiente>-site` e 256 MB; somem `SITE_INTERVALO` e o
  volume `site`. `infra/compose.e2e.yml`: `SITE_CACHE_SEGUNDOS: "1"`.
- `infra/caddy/Caddyfile`: o snippet `(site)` manda as páginas para `<ambiente>-site:3000`.
- `.gitignore`: `.react-router/`.

### 3. Layout

Topo translúcido, abertura com degradê e uma ilustração de player em CSS, vantagens em cartões, cursos
em destaque, "Como funciona" em passos, chamada final, página do curso com coluna de compra fixa (capa,
ofertas ou "Quero fazer este curso") e conteúdo por módulo com o selo "Aula gratuita", tema escuro por
`prefers-color-scheme` e `prefers-reduced-motion`. Ícones em SVG próprio (`componentes/Icone.tsx`).

### 4. Favicon

O mesmo `favicon.ico` da `@celke/kole-react` em `web/aluno/public/`, `web/adm/public/` e
`web/site/public/`, com `<link rel="icon" href="/favicon.ico" sizes="32x32" />` nos dois `index.html` e
no `root.tsx` do site. O arquivo é binário: grave-o a partir do base64 abaixo, dentro de `web/`:

```bash
mkdir -p aluno/public adm/public site/public
base64 -d > site/public/favicon.ico <<'FIM'
AAABAAMAEBAAAAEAIABoBAAANgAAACAgAAABACAAqBAAAJ4EAAAwMAAAAQAgAKglAABGFQAAKAAAABAAAAAgAAAAAQAgAAAAAAAA
BAAAEwsAABMLAAAAAAAAAAAAAAAAAADQm3MFzYdFQ82DO2PNhEBRzo5TIebOtgEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAADHmm8Fyn83ucp/Nf/Lfzb/y382/8t+NfzKgDjMzoE6Z9CWYwsAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAx4VD
Nst/NP/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/KfzbhyYI+W9/AowIAAAAAAAAAAAAAAAAAAAAAAAAAAM2IRDLKfzX/zH82/8qA
Nf/KfzX/y382/8t/Nv/Lfzb/y382/8t/Nv7MfziyzIpPFgAAAAAAAAAAAAAAAAAAAADVpG8Hyn8248qBON3NiERczoxKTs6GQGTM
gju3y381+Mt/Nv/Lfzb/yn82/8p/Nt7JiEgtAAAAAAAAAAAAAAAAAAAAAMqDOHDLhD9mAAAAAAAAAAAAAAAAAAAAANCQUiPLgz2q
zIA1/st/Nv/LfzX/zH826saERDEAAAAAAAAAAAAAAADZm2YHzIhKVfjs1wAAAAAAasH1BSGj/IUbof2yKqT5Qc6IRl7LgDfuy382
/8t/Nv/KfzblyoVIHwAAAAAAAAAAAAAAANSlfw/mw6kBAAAAACKl+UkTn/z/FZ///xmg/NXV8foA0YtOI8uAOc/Lfzb/yn82/8uA
OMLUnWoEAAAAAAAAAAAAAAAAAAAAAAAAAAA7rvoYGqD81Baf//gfovl9AAAAAAAAAADUl2AQzII5wMt/Nv/Kfzb+zII+YgAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAGfD9gQ+r/sN0/D/AAAAAAAAAAAAAAAAAM+YYA7LgDnOy382/8yCO9cAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAy4lHQ8x/Nf7KfTP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAANe4kgTLgTflyn84+gAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAADbom0Oy4A23cmCPl8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAyZprDNyueQoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACB/wAAAH8A
AAAfAAAADwAAAAcAAJ4DAACYAQAAyEAAAPhgAAD88AAA//gAAP/4AAD/+AAA//kAAP//AAD//wAAKAAAACAAAABAAAAAAQAgAAAA
AAAAEAAAEwsAABMLAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAA0JtzFc6IR2TMhkSmzoM8xM2COsbMhUK5z4Q+jc2LTV3RlGEl5s62BAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAM6OTjzOfDTUyoAz/ct/
Nv/Lfzb/y382/8t/Nv/LfzX/yn41/8x+M/HGfjrDxodDb9KUYB7x4dUBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADHmm8Vx34018l+N//Jfjb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/
yn82/8uBNv/MfjT+z3816cuBO5bPlWAp27yeAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAMqKTVrMfzL9y381/8x/Nf/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y4A2/8t/Nf/Kfzb/y381/8t8
NPLGhUCSzqBrHOjMsAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAxYE8fcp+Nf/L
fzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/yYEz/8t+NP/IfDTmyYhHa9/AowYAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADOhj52yX81/8t/Nv/Lfzb/zIA1/8uANf/KgDX/yYA1
/8qANv/LgDX/zH81/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y381/8uANv/Kfzb8y342us6WYSj26tsAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAMuLTFHKfjT+y382/8t/Nv/MfzX/yoA1/8t/Nf/LfzX/zH80/8x/N//Lfjj/y382/8t/Nv/L
fzb/y382/8t/Nv/Lfzb/y382/8t/Nv/KfzX/zn025syJTlbbrnwDAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
1KJsHMp9M+vLfzf/zH81/8p+NvzMhD3HzItIoc+MSZvMi0ubz4pGrMt/N9bLfjT7zH80/8t/Nv/MfzX/y382/8t/Nf/Lfzb/y382
/8h/Nv/KgDb/yX4y+MyFQYTgrn8JAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD36dEByIM8oct/M//LfjP9x41OfOm+
kgkAAAAAAAAAAAAAAAAAAAAA47WAD8qQVErQiEWWzII448l9Mv7LgDf/zIA0/8yANf/Lfzb/y382/8t/Nv/Kfzb/yX0z/caDQJ7a
pXMOAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADJk1szy4Av8siDPtH21roFAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAADZs4gUzolJd8mAOezKfzb/y4A2/8t/Nv/Lfzb/y382/8t/Nv/MgTX/zn8y/suAO6zaooYMAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAO3StwLIgTmazYQ8wwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADm28EA3qp1HMqG
Q6POgTP7yn82/8p/Nv/Lfzb/y382/8t/Nv/Kfzb/zH41/sOAO67br4YLAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAANmbZhvJ
gD657tOyCQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAHzE+ws/r/pPJ6f3dz2u/FRpxPwQ47CIBdSPVl3KfznozH81/8t/Nv/Lfzb/
y382/8t/Nv/KgTX/zX8z/ciCP5zWrH8HAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAM2HTUjPklhL+OzXAAAAAAAAAAAAAAAA
AAAAAABqwfUVHaP7wBaf/fwSnv7/Ep7//hyf/NFUt/ce7dq9AdWhZjHMhT+8yn40/Mx/Nv/Lfzb/y382/8t/Nv/Jfjf/y34y/MmC
RHPcupMCAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAzJ52AdSlfzrmw6kCAAAAAAAAAAAAAAAAAAAAACml+n0Snf3+FaD9/xWf//8V
n///E578/yOm+Zfc8fkAAAAAANmyhQzQh0mByX80+Mt/Nv/Lfzb/y382/8t/Nv/JfzX/zH0z8cmLT0MAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAHaT5phOg+v8ToP3/FZ///xWf//8Tnv7/IKH7wNXx+gEAAAAAAAAA
AP/v3QDPkFZXyn427st/N//Lfzb/y382/8qANv/Kfzj/zIA209SdahEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAA6rvtcE5v8/Bef/v8Vn///FZ///xKd/f8uqvNsAAAAAAAAAAAAAAAAAAAAAAAAAADUl19Czn805cp/Nf/L
fzb/y382/8t/Nv/KfzX9yoVFgQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGzG8QMqqfVy
HKH84Raf/vsaoP/nKKT4gm2+/AYAAAAAAAAAAAAAAAAAAAAAAAAAAOrXwgDQl188zII64st/Nv/Lfzb/y382/8qANv/LfDTq1qZu
HgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMzw/wFjwfYRKqn6IWC7/RPT8P8BAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAO7d0wDPl184y38158t+N//Lfzb/y381/85/NP/Gh0RzAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAOvQugDMkFZWy380+8t/Nv/KfzX/y382/82HRewAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAPTXuwTIhTy7yn82/8t/
Nf/Lfzb/y30z/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAM6PWUvQfzP6yYA1/8t/Nv/Iei//AAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAA17iSD8uBM93KgTP/yX43/8t8M/8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAzYU/ucp9Nv/Kfzb/zIU/7AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAPHWwwLOhDvGzH81/8p+Ne/JjExLAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD56tsA2qBpNcl9MOvIgDnCxolRQgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAPnq2wHJmWkx3Kx1JOPOvAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/////4Af/
/8AA//+AAD//gAAf/4AAB/+AAAP/gAAA/4AAAH+B4AA/w/wAH8f/AA/j8AAH8+AAA/HgMAP/4BwB/+A+Af/gPwD/8H+A////wP//
/8D////g////4P////D////g////4f///8P//////////////////////////ygAAAAwAAAAYAAAAAEAIAAAAAAAACQAABMLAAAT
CwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAA+tvACd64lSTRoXQ1x5dmPMmcbjjXrYIu7safHungzgQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA7dK+A86YbivPjE2FyoI9vcl9N+nNfTHvz3wt8c58LvDJfDPsy385
482AN73PjEuZzYpQYtCecirmzrYKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADd
qG4Kx4lRZ896MtzKfzD7yX82/8t/Nf/Lfzb/y382/8x/Nv/Lfzb/y342/8x/NP/KfjT/yn00+s58MOXJfju1wolScc6ZYi3lwKQH
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAOzYtwPMi0tvy3wt8NB+Mv/LgDX/yn41/8t/Nv/Lfzb/y382/8t/
Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8qAN//IfzX/xX0v+8qAMeHQgj6e0JBXRdmwjA8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAMWVaSzFfTDdyX84/8d/Of/HfTb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/MgDf/zH83
/8x/Of/NfjT/0X4v88h+ObjQlF5RzqF4D+3ZxgEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMmLT3jLfi77zH81/8t/NP/MfzT/y382/8t/Nv/L
fzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8qANf/KgDX/y4A2/8t/Nf/KgDb/yn83/8x+NP/IdzH3x4A+t8ybZEfYtYkJ
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAMmHRazLfzP/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/
Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/MgDT/0IEu/8h9L/XGgz+i0qR6KQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMWAOr/JfjX/y382/8t/Nv/Lfzb/
y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/JgTP/xoA4
/8l+OP/KfDP/yHwz5ciLTnvfwKMOAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAMyEO7jIfjX/y382/8t/Nv/Lfzb/zH82/8t/Nf/KfzX/yn82/8p/Nv/Kfzb/yoA2/8uANv/L
gDX/zIA1/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfjf/zH81/8p/NP/LgTb/y4E1/8h9NvnLfznB05hiOQAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAANOMSJvJfzL/yn82
/8t/Nv/Lfzb/zX80/82ANP/MgTX/yoE0/8qBNP/KgTT/yoA0/8eAN//JgDj/yX83/8p/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/
Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/LfjT+yXsw5s2JUWzlwZwLAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMaJTG3KfjL8y382/8t/Nv/Lfzb/zH83/8t/Nv/JgDb/yX81/8t+Nf/KfTX/
y341/89/Mv/OfzX/zX44/8t/N//Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/yoA2
/9F7MvfJgUSc1qFpIfnx3gAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAANCb
YjTLfTHqzH82/8t/Nv/Lfzb/y342/8p+Nv/IfjTxy4M74cmEP9nMhkHXy4ZB18iFQ9fLhT/dzX816cp8MvjLfjT/y382/8t/Nv/L
fzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8t/Nv/Kfzb/xH82/8x/N//IfTD9y4A4xdGYYjny1r4CAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAOjKoQ3HgTi6yH4z/8uAN//NfzX/z4Ev/8SBP9LYmV9b5LGE
I+jKqxbx0rMS8tO0EvTUtRLswpkc0pxpMNKSUnzHgjvHy38y7tCAMPzMfzb/zX8z/8t/NP/Lfzb/zYE2/81/M//MgDX/y382/8t/
Nv/Lfzb/yn83/8qANf/Kfzb/yn4z/8p9NdfRll9N4cCdBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAADJj1JhzH4u98l/NP/KfjX/x4U+1NOwiCT34cMBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADjyrEN
1qF2NdCQWH3MhkDDyX4w+cd8M//Kfzj/y382/8yANP/NgDX/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/xn44/8t+Mv/EfTbhy5JX
Yt6qkAMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADVqXwYw4E6ws6BMP/EfDT5255oVgAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADkyqgP0JpkWM2DPsnGfDX/yX83/8p+Nf/M
gDb/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/yn84/8yBM//QgS//znsy6s2NWmUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAEBAQA0JNUW8h8KPnFezH14LyQLgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAACgkHAObbwQvfnVtoyII83sp+NP/MgTX/zIA3/8t/OP/Ofzb/y382/8t/Nv/Lfzb/zX80/8qA
NP/JgDj/zH41/8x7MO7NjVdtAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA2ap2EcmAOrXI
eSr157OIMgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD3z7IC
26x6I8uIS5bQgTL3zIE1/8d/Nf/HgDX/y382/8t/Nv/Lfzb/y382/8t/Nv/Lfzb/yn43/8x/NP/Eey7ww5NlXOK3jwMAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA+/HeANmaZDnJeDPaz55oTgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAst/+BW7E/Sk/tPVCWLPyNqDW/BgAAAAAAAAAAOOwiArZlV1gyn4+1s19NP/NgDX/y382/8t/Nv/L
fzb/y382/8t/Nv/Lfzb/yX82/8qCNP/QgTH/ynsz6MeUY1DpzasDAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAN2ofgPMiEdtzIdKmvvy3gAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABfvP0yMaf7nRmh+uIToPv0EZz8
7Ryj/9c9rft9esj+FQAAAAD2yKYE1KJyO8qIRb3JfTX3y381/8p/Nv/Mfzb/y382/8t/Nv/Lfzb/y381/8p/N//Hfjj/zIAz/8d9
MdzJj1k2AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADfsIsNzIZJltGogCz47NcAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAGrB9S8ZofnWEZ7+/hmf/v8Vn/7/FJ///xSf//8Rm/38KaX5pHTE9QwAAAAA//TWAeC3hRrQklV+
yoE168x+Nf/Nfjb/yn82/8t/Nv/Lfzb/y382/8t/Nv/Kfzb/y382/8t/M//Kfjy8y5lqIQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAzJ52F9GfeF/hwKQEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADGp+akKnP3+FqH9/xKg
/v8Vn///FZ///xWf//8Xn/z/DJz5+Uu3+FEAAAAAAAAAAAAAAADiy6cF1Z1uRsx+OtHHfzH+y4A3/8t/Nv/Lfzb/y382/8t/Nv/L
fzb/y382/8h/NP/LfDL7yYVDmtawiw0AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQEBAPTWvw7zy7cB
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAyun7Bxaf+ucZnf7/F5/8/xSg//8Vn///FZ///xWf//8XoP3/D53+/ySn+Jva8PkBAAAA
AAAAAAAAAAAABQQEAOGsfCLNhESzyn00+Mt/Nv/Lfzb/y382/8t/Nv/Lfzb/y382/8mANf/OgDX/ynsz88qMTGXiz7kBAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAxen6EBOh+OcU
ofj/CqD9/xah/P8Vn///FZ///xag//8OnP//GZ3+/y2m+57U8foDAAAAAAAAAAAAAAAAAAAAAAAAAADmuJISzItLl8t8NfnNfzf/
yn43/8yAN//Lfzb/y382/8qANf/Kfzf/yn03/8yANNjTm2gmAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAQFADOq/KwYm/3/F6D9/xid//8Vn///FZ///xWf//8cnv3/DJ74/FC4
80kBAQEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA786uC9GPVH/RfjH1yoA1/8p+Nf/Lfzb/y382/8t/Nv/Lfzb/y382/8l9M/rG
hUqV++bQBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAGDB8ykXofXVEZz8/xei/f8Vn///FZ///xaf//8Knfv/LqT6mXzG9QoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AOLAnQrMjlZzy38w88yAN//Lfzb/y382/8t/Nv/Lfzb/y382/8mANv/Mey/u0pxpQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABcvvMlKaf6nh2i/N4WoP36GaD+8R2h/9I5
qvdvb778DQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADjvpkLz5BXbcyBOPLMfzT/zH83/8t/Nv/Lfzb/
y382/8qBNv/OfTT+yYRDqvbrzwMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAntv6CFi89R8mqfoyOKn6LXvI/xjT8P8CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAA3riTCM2SV2/IfTHzzn43/8t/Nv/Lfzb/y382/8t/Nf/Mfzf/y3ws7tKuhSoAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAOC/ognKjlKCzH40
+cp+Nv/Lfzb/y382/8p/Nf/MgTf/zX4z+8aRXIIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADmwJwPyIQ/w8x/NP7Lfzb/y382/8mANf/Ifzb/zn41/86LSf8AAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAKCAcA1J5pTcR/L/XJfzX/yn44/8x/Nf/Kfzb/zH81/8x+M/8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA9tvDCMyFRbrRfjT/yX43/8qANP/Lfzb/y382
/8l5Lv8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAM2VZVTQfzL0y381/8uBNf/Lfzb/y382/8d3K/8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAANa3jyDJgTPay4Ax/8yC
NP/Kfzb/yn84/8l4Lv8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAOzVxQLMiEWmy38x/8l/M//HfTb/zH41/81/OP8AAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADQ
j06Ty3w0/8t9N//Jfzn/z4Aw/smNUtQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAPzp2AHPi0mezH4y/81+Nf/Lfjf+yH4x38ifcTAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAObBqBPNhD3Tx38x/8x+MfvHfDbUzJJaTwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA0bKbA9mcYWfKeyzsw3ow28uIR5LMnHEnAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAABAQEA1LmkGcaRWlfbp2tC3sOlFQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQEBAAEBAQAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAD///////8AAP4B/////wAA8AAf////AADgAAP///8AAMAAAP///wAAwAAAH///AADAAAAP//8AAMAAAAf//wAAwAAAAf//AADA
AAAA//8AAMAAAAA//wAAwAAAAB//AADAAAAAB/8AAMAAAAAD/wAA4D/AAAH/AADg//gAAf8AAPD//gAA/wAA8P//AAA/AAD4/+DA
AB8AAPj/wCAAHwAA/H+AEAAPAAD+P4AcAAcAAP8/AA8AAwAA//8AD4ADAAD//4AfwAEAAP//gB/gAQAA///AP/AAAAD//+B/+AAA
AP/////8AAAA//////4AAAD//////wAAAP//////AAAA//////+AAAD//////4AAAP//////gAAA///////AAAD//////4AAAP//
////gQAA//////8DAAD//////w8AAP///////wAA////////AAD///////8AAP///////wAA////////AAD///////8AAP//////
/wAA////////AAA=
FIM
cp site/public/favicon.ico aluno/public/favicon.ico
cp site/public/favicon.ico adm/public/favicon.ico
```

### 5. Cursos de exemplo

`web/scripts/cursos-exemplo.ts` (`npm run cursos-exemplo -- <endereço do ADM>`): pede e-mail, senha
(sem eco) e o código do aplicativo autenticador e cria seis cursos publicados, cada um com turma ativa,
regra do certificado, módulos, aulas (a primeira gratuita) e capa PNG em degradê gerada no próprio
script (sem dependências). Curso que já existe (slug repetido, 409) é pulado.

### 6. Documentação

`README.md`, `docs/vps.md` (o `site` na 3000 e a atualização do Caddy depois do deploy),
`web/e2e/README.md`, `.claude/rules/frontend.md`, plano (Fase 21) e capítulos (Cap. 45).

## Arquivos

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### Arquivos removidos (ou movidos para outro caminho)

- `web/site/assets/site.css`
- `web/site/assets/validar.js`
- `web/site/gerar.ts`
- `web/site/html.ts`
- `web/site/paginas.test.ts`
- `web/site/paginas.ts`
- `web/site/assets/icone.svg`

### Arquivos alterados (substitua o conteúdo inteiro)

`.claude/rules/frontend.md`:

`````markdown
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
`````

`.github/workflows/pipeline.yml`:

`````yaml
# Testes, build das imagens e e2e em todo PR para homolog e main. Depois do merge (push), refaz tudo
# com o código exato do commit, publica no GHCR as imagens que passaram no e2e (tag = SHA) e implanta:
# push na homolog → homologação; push na main → produção. Os deploys só rodam com a variável do
# repositório DEPLOY_ATIVO=true (criada quando a VPS estiver pronta); sem ela, são pulados.
name: Pipeline

on:
  pull_request:
    branches: [homolog, main]
  push:
    branches: [homolog, main]
  workflow_dispatch:

concurrency:
  group: pipeline-${{ github.ref }}
  # Só PRs cancelam execuções antigas: um deploy nunca é interrompido no meio
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

permissions:
  contents: read

env:
  COMPOSE_E2E: docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env

jobs:
  testes:
    name: Testes (unitários e integração)
    runs-on: ubuntu-latest
    timeout-minutes: 20
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "25"
          cache: maven

      # A transcodificação de vídeo é testada com o ffmpeg de verdade. Build estático (sem o apt, cujo
      # espelho trava no meio dos downloads), guardado em cache entre as execuções
      - name: Cache do ffmpeg
        uses: actions/cache@v6
        with:
          path: ~/ffmpeg
          key: ffmpeg-estatico-7-${{ runner.os }}

      - name: Instalar ffmpeg
        timeout-minutes: 10
        run: |
          if [ ! -x ~/ffmpeg/ffmpeg ]; then
            mkdir -p ~/ffmpeg
            curl -fsSL --retry 3 --retry-delay 5 --max-time 300 \
              https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz \
              | tar -xJ -C ~/ffmpeg --strip-components=1 --wildcards '*/ffmpeg' '*/ffprobe'
          fi
          echo "$HOME/ffmpeg" >> "$GITHUB_PATH"
          ~/ffmpeg/ffmpeg -version | head -1

      # Unitários, integração (Testcontainers), cobertura mínima e formatação
      - name: Verificar
        run: ./mvnw -B verify

      - name: Relatórios dos testes
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: relatorios-testes
          path: |
            backend/target/surefire-reports
            backend/target/failsafe-reports

      - name: Relatório de cobertura
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: cobertura
          path: backend/target/site/jacoco
          if-no-files-found: ignore

  frontend:
    name: Frontend (lint, tipos, unitários e build)
    runs-on: ubuntu-latest
    timeout-minutes: 15
    defaults:
      run:
        working-directory: web
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-node@v6
        with:
          node-version: "24"
          cache: npm
          cache-dependency-path: web/package-lock.json

      # O lock guarda o kole-react como git+ssh; o repositório é público e vem por HTTPS
      - name: Dependências
        run: |
          git config --global url."https://github.com/".insteadOf "ssh://git@github.com/"
          npm ci --no-audit --no-fund

      # Falha com vulnerabilidade alta ou crítica nas dependências que vão para o navegador
      - name: Auditoria das dependências
        run: npm audit --omit=dev --audit-level=high
      - run: npm run format:check
      - run: npm run lint
      - run: npm run typecheck
      - run: npm test
      - run: npm run build

  e2e:
    name: e2e (stack completa)
    needs: [testes, frontend]
    runs-on: ubuntu-latest
    timeout-minutes: 30
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "25"
          cache: maven

      - uses: docker/setup-buildx-action@v4

      # As mesmas imagens que vão para os servidores, com cache de camadas entre execuções
      - name: Imagem da API
        uses: docker/build-push-action@v7
        with:
          context: backend
          target: api
          tags: plataforma-cursos-api:e2e
          load: true
          # Liga o pacote do GHCR ao repositório (mesmas permissões)
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=api
          cache-to: type=gha,mode=max,scope=api

      - name: Imagem do worker
        uses: docker/build-push-action@v7
        with:
          context: backend
          target: worker
          tags: plataforma-cursos-worker:e2e
          load: true
          # Liga o pacote do GHCR ao repositório (mesmas permissões)
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=worker
          cache-to: type=gha,mode=max,scope=worker

      - name: Imagem dos fronts
        uses: docker/build-push-action@v7
        with:
          context: web
          target: web
          tags: plataforma-cursos-web:e2e
          load: true
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=web
          cache-to: type=gha,mode=max,scope=web

      - name: Imagem do site
        uses: docker/build-push-action@v7
        with:
          context: web
          target: site
          tags: plataforma-cursos-site:e2e
          load: true
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=web

      # Vulnerabilidades críticas com correção disponível nas imagens (sistema e dependências Java)
      - name: Varredura da imagem da API (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-api:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Varredura da imagem do worker (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-worker:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Varredura da imagem dos fronts (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-web:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Varredura da imagem do site (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-site:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Subir a stack de e2e
        run: $COMPOSE_E2E up -d --no-build --wait

      - name: Jornadas e2e
        working-directory: backend
        run: ./mvnw -B verify -Pe2e

      - uses: actions/setup-node@v6
        with:
          node-version: "24"
          cache: npm
          cache-dependency-path: web/package-lock.json

      # Navegador real contra os fronts servidos pelo container web, atrás do Caddy
      - name: Jornadas e2e dos fronts (Playwright)
        working-directory: web
        run: |
          git config --global url."https://github.com/".insteadOf "ssh://git@github.com/"
          npm ci --no-audit --no-fund
          npx playwright install --with-deps chromium
          npm run e2e

      # Meta de Lighthouse do site público (desempenho, acessibilidade, boas práticas e SEO)
      - name: Lighthouse do site
        working-directory: web
        run: npm run lighthouse

      # Só depois do merge, e só se o e2e passou: as imagens publicadas são exatamente as testadas
      - name: Publicar imagens no GHCR
        if: github.event_name == 'push'
        env:
          REGISTRO: ghcr.io/${{ github.repository_owner }}
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u "${{ github.actor }}" --password-stdin
          for imagem in api worker web site; do
            docker tag "plataforma-cursos-$imagem:e2e" "$REGISTRO/plataforma-cursos-$imagem:$GITHUB_SHA"
            docker push "$REGISTRO/plataforma-cursos-$imagem:$GITHUB_SHA"
          done

      - name: Logs da stack
        if: failure()
        run: $COMPOSE_E2E logs --no-color > stack-e2e.log

      - name: Relatórios do e2e
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: e2e-falha
          path: |
            stack-e2e.log
            backend/target/failsafe-reports
            web/playwright-report
            web/test-results

      - name: Derrubar a stack de e2e
        if: always()
        run: $COMPOSE_E2E down -v

  deploy-homolog:
    name: Homologação
    needs: e2e
    if: github.event_name == 'push' && github.ref == 'refs/heads/homolog' && vars.DEPLOY_ATIVO == 'true'
    uses: ./.github/workflows/deploy.yml
    with:
      ambiente: homolog
    secrets: inherit

  deploy-producao:
    name: Produção
    needs: e2e
    if: github.event_name == 'push' && github.ref == 'refs/heads/main' && vars.DEPLOY_ATIVO == 'true'
    uses: ./.github/workflows/deploy.yml
    with:
      ambiente: producao
    secrets: inherit
`````

`README.md`:

`````markdown
# Plataforma de Cursos

Plataforma de cursos online com área do aluno, painel administrativo (ADM) e site público, desenvolvida
passo a passo como tutorial.

## Índice

- [Funcionalidades](#funcionalidades)
- [Tecnologias](#tecnologias)
- [Requisitos](#requisitos)
- [Executar com Docker (recomendado)](#executar-com-docker-recomendado)
- [Executar para desenvolvimento](#executar-para-desenvolvimento)
- [Testes](#testes)
- [Comandos úteis](#comandos-úteis)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Branches](#branches)
- [Documentação](#documentação)

## Funcionalidades

- **Área do aluno:** meus cursos (ativos, arquivados e expirados), turma com módulos e progresso, aula
  com vídeo HLS (retoma de onde parou), arquivos de apoio, fórum por aula e por turma, certificado em PDF
  com código de validação, perfil (nome, foto e senha), compra de cursos.
- **ADM:** cursos, turmas, módulos e aulas; envio de vídeo em partes com conversão para HLS; capa,
  arquivos de apoio, matrículas, moderação do fórum, regras do certificado e termos; meios de pagamento,
  ofertas, pedidos e notificações recebidas; usuários (bloquear e encerrar sessões) e trilha de
  auditoria.
- **Site público:** home, catálogo, página do curso com preço e botão de compra, validação de
  certificado, termos e privacidade, com SEO (Open Graph, dados estruturados, sitemap).
- **Conta e segurança:** cadastro com confirmação de e-mail, login com JWT e refresh em cookie
  `HttpOnly`, verificação em duas etapas (aplicativo autenticador) obrigatória para administradores,
  senhas comuns e vazadas recusadas, recuperação de senha, bloqueio por tentativas, limites por IP e por
  usuário, termos versionados com aceite, usuário do banco da aplicação sem permissão de mudar tabelas.
- **Privacidade (LGPD):** o aluno baixa uma cópia dos dados e exclui a conta pelo perfil; retenção dos
  registros com prazo legal e um modelo de política de privacidade com os prazos que o sistema aplica.
- **Pagamentos:** Hotmart (webhook 2.0.0) numa estrutura que aceita outros meios de pagamento.
- **Operação:** fila de jobs no PostgreSQL (worker), alertas por e-mail, backup e restauração, logs em
  JSON no horário de Brasília guardados em arquivo, auditoria de todas as alterações nos dados.

## Tecnologias

| Parte | Tecnologias |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA, Flyway, PostgreSQL 18 |
| Vídeos e arquivos | Garage (S3) com URLs pré-assinadas, ffmpeg (HLS) |
| Frontend | React 19, TypeScript, Vite, `@celke/kole-react`, hls.js |
| Site | Páginas HTML geradas a partir da API (Node) |
| Testes | JUnit 5, Mockito, Testcontainers, REST Assured, Vitest, Testing Library, Playwright |
| Infraestrutura | Docker Compose, Caddy, GitHub Actions, deploy numa VPS |

## Requisitos

**Para executar com Docker:** Docker com o Docker Compose e Git.

**Para desenvolver:** além do Docker, JDK 25 e ffmpeg (para os testes do backend) e Node.js 24 (para os
fronts).

Portas usadas:

| Porta | Serviço |
|---|---|
| 8088 | Stack completa (Caddy): aluno, ADM, site e mídia |
| 8026 | Mailpit da stack completa (e-mails enviados) |
| 8080 | API (desenvolvimento) |
| 5173 e 5174 | Área do aluno e ADM (desenvolvimento, Vite) |
| 5432, 3900 e 8025 | PostgreSQL, Garage e Mailpit (desenvolvimento) |

## Executar com Docker (recomendado)

Sobe tudo como em produção — Caddy, API, worker, fronts, site, PostgreSQL, Garage e Mailpit — com contas
de exemplo já criadas. Os valores de `infra/e2e.env` são só para uso local.

**1. Clonar o repositório**

```bash
git clone https://github.com/celkecursos/plataforma-cursos.git
cd plataforma-cursos
```

**2. Subir os serviços** (a primeira vez leva alguns minutos para construir as imagens)

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
```

Este comando:

- cria o banco e aplica as migrations;
- sobe a API, o worker (fila, e-mails e conversão de vídeo), os fronts e o site público (React com renderização no servidor);
- cria o bucket no Garage e as contas de exemplo;
- deixa tudo atrás do Caddy na porta 8088.

**3. Acessar**

| Endereço | O quê |
|---|---|
| http://aluno.localhost:8088 | Área do aluno |
| http://adm.localhost:8088 | ADM |
| http://cursos.localhost:8088 | Site público (atualizado a cada 3 segundos a partir da API) |
| http://localhost:8026 | Mailpit: e-mails de confirmação, recuperação de senha e matrícula |

Os navegadores atuais resolvem `*.localhost` sozinhos; não é preciso mexer no `hosts`.

**4. Contas de exemplo**

| Conta | E-mail | Senha |
|---|---|---|
| Administrador | `admin@celke.com.br` | `123456A#b123` |
| Aluno | `aluno@celke.com.br` | `123456A#b123` |

O administrador entra em duas etapas: depois da senha, informe o código de 6 dígitos de um aplicativo
autenticador (Google Authenticator, Microsoft Authenticator, 1Password...). No aplicativo, adicione uma
conta pela chave `JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP` (tipo "baseado em tempo"): é o segredo do admin de
exemplo, definido em `SEED_E2E_TOTP` no `infra/e2e.env`.

Para ver um curso no site e na área do aluno: entre no ADM, crie o curso, uma turma (ative-a), um módulo
e uma aula, publique o curso e matricule o aluno na aba **Matrículas** da turma. Para ter vários cursos
de exemplo publicados de uma vez (com capa, módulos e aulas), dentro de `web/`:

```bash
npm run cursos-exemplo -- http://adm.localhost:8088
```

O script pede o e-mail, a senha e o código do aplicativo autenticador do administrador e pode rodar
de novo (o curso que já existe é pulado). Serve também para a homologação, com o endereço do ADM dela.

**5. Parar**

```bash
# Para os serviços e mantém os dados
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env stop
# Para e apaga tudo (banco, vídeos, logs)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

## Executar para desenvolvimento

API e worker no Docker (com Swagger e Mailpit) e os fronts no Vite, com recarga ao salvar.

**1. Gerar o `infra/.env`** com segredos aleatórios (uma vez)

```bash
./infra/scripts/gerar-env.sh
```

Para usar o ADM no Vite, acrescente no `infra/.env` a porta dele nas origens:

```bash
AUTH_ORIGENS=http://localhost:8080,http://localhost:5173,http://localhost:5174
S3_CORS_ORIGENS=http://localhost:5173,http://localhost:5174
```

**2. Subir o backend**

```bash
docker compose -f infra/compose.yml up -d --build --wait
```

| Endereço | O quê |
|---|---|
| http://localhost:8080/swagger-ui.html | Documentação da API (Swagger) |
| http://localhost:8025 | Mailpit |
| http://localhost:8080/actuator/health | Saúde da API |

**3. Criar o administrador** (pede a senha no terminal; mínimo de 12 caracteres, sem o nome ou o
e-mail e fora das senhas mais usadas)

```bash
docker compose -f infra/compose.yml run --rm api criar-admin admin@exemplo.com "Administrador"
```

No primeiro login do ADM, leia o QR code com um aplicativo autenticador e guarde os códigos de
recuperação que aparecem em seguida.

Alunos criam a conta pela tela de cadastro; o link de confirmação chega no Mailpit.

**4. Subir os fronts** (cada um num terminal)

```bash
cd web
npm ci
npm run dev:aluno   # http://localhost:5173
npm run dev:adm     # http://localhost:5174
```

O Vite manda `/api` para a API em `http://localhost:8080` (outra API: `VITE_API_PROXY`).

## Testes

**Backend** (dentro de `backend/`, com o Docker rodando e o ffmpeg no PATH):

```bash
./mvnw spotless:apply        # formata
./mvnw verify                # unitários, integração (Testcontainers), cobertura mínima e formatação
```

**Jornadas e2e** (com a stack completa no ar, como em "Executar com Docker"):

```bash
(cd backend && ./mvnw verify -Pe2e)    # API pelo Caddy, worker, Garage e e-mail
(cd web && npm run e2e)                # navegador real (Playwright): aluno, ADM e site
```

Como escolher o que rodar, ver os testes na tela (janela aberta, câmera lenta, modo UI), contas, dados de
teste e o que cada spec cobre: [`web/e2e/README.md`](web/e2e/README.md).

**Fronts** (dentro de `web/`):

```bash
npm run format:check && npm run lint && npm run typecheck && npm test && npm run build
```

## Comandos úteis

```bash
# Situação dos containers (desenvolvimento)
docker compose -f infra/compose.yml ps

# Logs da API e do worker
docker compose -f infra/compose.yml logs -f api worker

# Reconstruir depois de mudar o backend
docker compose -f infra/compose.yml up -d --build --wait

# Parar o ambiente de desenvolvimento (com -v, apaga os dados)
docker compose -f infra/compose.yml down

# Abrir o banco de desenvolvimento
docker compose -f infra/compose.yml exec postgres psql -U plataforma_cursos plataforma_cursos

# Administrador perdeu o celular e os códigos de recuperação: o próximo login pede o QR code de novo
docker compose -f infra/compose.yml run --rm api redefinir-2fa admin@exemplo.com
```

## Estrutura do projeto

```
backend/   API e worker (Java 25, Spring Boot 4.1): módulos por funcionalidade, testes e e2e
web/       Área do aluno e ADM (React + Vite), site público em React com renderização no servidor, testes Playwright
infra/     Docker Compose (local, servidores e e2e), Caddy, scripts de VPS, deploy e backup
docs/      Plano, roteiro dos capítulos, VPS, operação, pagamentos e prompts
```

## Branches

| Branch | Uso |
|---|---|
| `main` | Produção |
| `homolog` | Homologação |
| `feature/nome` | Nova funcionalidade (PR para `homolog`) |
| `fix/nome` | Correção (PR para `homolog`) |
| `hotfix/nome` | Correção urgente a partir da `main` |

## Documentação

- [Plano de implementação](docs/plano-implementacao.md)
- [Roteiro da VPS](docs/vps.md)
- [Operação: backup, alertas, logs, auditoria e segurança](docs/operacao.md)
- [Pagamentos (Hotmart)](docs/pagamentos.md)
- [Modelo da política de privacidade](docs/politica-privacidade-modelo.md)
- [Roteiro do tutorial (capítulos)](docs/capitulos.md)
- [Prompts para recriar o projeto do zero](docs/prompts-iniciais/README.md)
- [Prompts por fase](docs/prompts/README.md)
`````

`docs/capitulos.md`:

`````markdown
# Roteiro do tutorial — capítulos

Roteiro de gravação da Plataforma de Cursos. Os capítulos seguem a ordem das fases do
[plano de implementação](plano-implementacao.md): primeiro todo o backend, com infraestrutura,
testes, pipeline e deploy; depois o frontend. Cada capítulo é gravado executando os
[prompts](prompts/README.md) da etapa correspondente.

## Formato de cada capítulo

Cada capítulo traz: **Objetivo**, **Conteúdo**, **Segurança** (quando houver), **Testes** e
**Entrega**. A entrega é o que quem acompanha o tutorial precisa ter funcionando no fim do
capítulo.

| Parte | Capítulos | Fases do plano |
|---|---|---|
| 1. Fundação | 1–5 | 0, 1.1–1.3 |
| 2. Autenticação e segurança | 6–10 | 1.4–1.6 |
| 3. Qualidade e entrega contínua | 11–14 | 1.7–1.9 |
| 4. Domínio de cursos | 15–18 | 2, 3 |
| 5. Vídeo | 19–24 | 4, 5 |
| 6. Restante do domínio | 25–28 | 6–9 |
| 7. Operação | 29–31 | 10 |
| 8. Frontend com `@celke/kole-react` | 32–36 | 11–14 |
| 9. Migração do legado (opcional) | 37 | 15 |
| 10. Vendas e pagamentos | 38–40 | 16 |
| 11. Auditoria e logs | 41 | 17 |

---

## Parte 1 — Fundação

### Cap. 1 — Visão geral e arquitetura

**Fase:** 0

- **Objetivo:** entender o que será construído, o que fica de fora e como as peças se ligam.
- **Conteúdo:**
  - Funcionalidades que vêm do CLMS legado: cursos, turmas, módulos, aulas em vídeo, matrícula, progresso, arquivos de apoio, fórum, certificado, perfil e termos.
  - O que fica de fora: LeadLovers, automação de e-mail, clube de desafios. A venda pela Hotmart entra no fim, na Parte 10, numa estrutura que aceita outros meios de pagamento.
  - Arquitetura: uma VPS da Hostinger (2 vCPU, 8 GB, 100 GB NVMe) com tudo em Docker — Caddy (HTTPS e roteamento), API e worker Spring Boot, PostgreSQL 18 e Garage (S3) — servindo produção e uma homologação leve.
  - Domínios, todos subdomínios de `celke.uno` (o domínio principal pertence a outras aplicações): `cursos.` (site), `aluno.`, `adm.` e `media.`, e os equivalentes `homolog-*`.
  - Fluxo do vídeo: upload direto ao Garage → worker com ffmpeg → HLS → playlist assinada pela API.
  - Modelo de dados enxuto: usuário, papel, curso, turma, módulo, aula, matrícula, vídeo, progresso, fórum, certificado, termo.
- **Entrega:** diagrama da arquitetura e lista de funcionalidades.

### Cap. 2 — Repositório, branches e fluxo de trabalho

**Fase:** 0 · **Prompts:** [f00-01](prompts/f00-01-estrutura-inicial.md), [f00-02](prompts/f00-02-orientacoes-para-ia.md)

- **Objetivo:** deixar o repositório pronto para o fluxo de PRs com testes e deploy.
- **Conteúdo:**
  - Repositório `celkecursos/plataforma-cursos` com README e `.gitignore` (segredos, `target/`, `node_modules/`).
  - Branches: `main` (produção), `homolog` (homologação), `feature/nome` e `fix/nome` temporárias, `hotfix/nome` a partir da `main`.
  - A `main` recebe código só da `homolog`; alteração reprovada na homologação é desfeita com `git revert`; depois de um hotfix, merge da `main` de volta na `homolog`.
  - Exclusão automática da branch após o merge; environments `homolog` e `production` no GitHub.
  - Como os prompts recriam o projeto passo a passo.
  - Orientações para agentes de IA versionadas em `.claude/`: rules que carregam pelo caminho tocado,
    mensagens de commit em Conventional Commits sem coautoria de IA (`/gc` e `/pc`), `/nova-branch`,
    `/abrir-pr`, `/revisar-dod` e o pipeline SDD (spec → tasks → código) com três portões humanos.
- **Entrega:** `main` e `homolog` no GitHub e a branch `feature/base-projeto` criada.

### Cap. 3 — Esqueleto do backend e padrão de testes

**Fase:** 1.1 · **Prompt:** [f01-01](prompts/f01-01-esqueleto-backend-e-testes.md)

- **Objetivo:** criar o projeto Java e as regras que todas as próximas etapas seguem.
- **Conteúdo:**
  - Spring Boot 4.1 com Java 25, gerado no Spring Initializr, organizado por funcionalidade (`shared/`, `auth/`, `curso/`...; cada módulo com `api/`, `application/`, `domain/`, `infra/`).
  - Dependências mínimas: webmvc, security, data-jpa, validation, flyway, actuator, postgresql, springdoc. As demais entram na etapa que as usa.
  - `application.yaml` sem valores padrão para segredos; perfil `worker` sem servidor HTTP.
  - `CLAUDE.md` da raiz e do backend; skills `criar-teste-unitario`, `criar-teste-integracao` e `criar-teste-e2e`, cada uma com o padrão do seu nível.
  - Build: Surefire (`*Test`), Failsafe (`*IT`), JaCoCo com 80% de linhas somando os dois, Spotless com palantir-java-format.
  - Dockerfile multi-stage com targets `api` e `worker` (com ffmpeg), jar em camadas.
- **Segurança:**
  - Tudo exige autenticação; públicos só `GET` de health/probes e da documentação.
  - Sem sessão, CSRF, formulário, HTTP Basic nem usuário embutido do Spring Security.
  - Actuator expõe só `health`, sem detalhes; Swagger só com `DOCS_ENABLED=true`.
  - Imagens rodam com usuário sem root.
- **Testes:** base `support/Containers` (PostgreSQL 18 no Testcontainers) e `support/IntegrationTest` (`MockMvcTester`); `SegurancaPadraoIT` como referência.
- **Entrega:** `./mvnw verify` verde e as imagens `api` e `worker` construídas.

### Cap. 4 — Ambiente local com Docker

**Fase:** 1.2 · **Prompt:** [f01-02](prompts/f01-02-ambiente-local.md)

- **Objetivo:** subir toda a stack local com um comando.
- **Conteúdo:**
  - `infra/compose.yml` com PostgreSQL 18, Garage, API, worker e Mailpit, todos com versão fixa.
  - Garage em modo `--single-node --default-bucket`: na primeira subida ele cria o bucket privado `midia` (prefixos `uploads/` e `videos/`) e a chave da aplicação; `garage.toml` com `replication_factor = 1` e `db_engine = "sqlite"`, sem segredos.
  - `.env` + `.env.example`; `gerar-env.sh` cria o `.env` com segredos aleatórios; variáveis obrigatórias com `${VARIAVEL:?mensagem}`.
  - Healthchecks e `depends_on: condition: service_healthy`; o worker sobe depois da API, que aplica as migrations.
  - Portas do host configuráveis no `.env`, para quando a porta padrão já estiver em uso.
- **Segurança:** portas publicadas só em `127.0.0.1`; nenhum segredo no yaml nem no `garage.toml`; `.env` com permissão 600.
- **Entrega:** `docker compose up --wait` sobe tudo saudável, a API responde em `/actuator/health`, o Swagger abre e o painel do Mailpit fica acessível.

### Cap. 5 — Padrões compartilhados

**Fase:** 1.3 · **Prompt:** [f01-03](prompts/f01-03-padroes-compartilhados.md)

- **Objetivo:** criar a base que todos os módulos usam.
- **Conteúdo:**
  - Erros no formato `ProblemDetail` (RFC 9457) com handler global: validação com a lista `erros` (`campo`, `mensagem`) em português, exceções de negócio, erros do Spring MVC traduzidos, erro inesperado só com o id da requisição; 401/403 da segurança no mesmo formato.
  - Id de correlação (`X-Request-Id`) no log e na resposta; logs em JSON (ECS).
  - DTOs como `record`, paginação padrão (`Pagina<T>`), `Clock` único.
  - `StorageService` com AWS SDK v2 (path-style): URLs pré-assinadas de GET/PUT com o endereço público, upload em partes, consulta, exclusão.
  - Configuração do bucket aplicada pela API ao subir: CORS para os hosts do front (expondo o `ETag`) e expiração de `uploads/` abandonados.
  - Fila no PostgreSQL: tabela `jobs`, `SELECT ... FOR UPDATE SKIP LOCKED`, lease, backoff exponencial, erro permanente, processador por tipo e consumo só no worker.
  - `EmailService` enfileira; o worker monta o HTML com Thymeleaf e envia pelo SMTP (Mailpit no desenvolvimento, Iagente nos servidores).
  - Configuração obrigatória em `@ConfigurationProperties` validadas.
- **Segurança:** nenhuma resposta com stack trace ou mensagem interna; id de correlação recebido só é aceito se for seguro para o log; nome de template restrito a `[a-z0-9-]`; variáveis escapadas no HTML do e-mail; preflight do CORS recusado para origens fora da lista.
- **Testes:** handler de erros e 401/403; `StorageService` contra o Garage real; dois consumidores da fila nunca pegam o mesmo job; backoff e lease vencido; e-mail conferido no Mailpit.
- **Entrega:** `./mvnw verify` verde com Garage e Mailpit no Testcontainers, e a stack local aplicando o CORS no bucket.

---

## Parte 2 — Autenticação e segurança

### Cap. 6 — Usuários e senhas

**Fase:** 1.4 · **Prompt:** [f01-04](prompts/f01-04-usuarios-e-login.md)

- **Objetivo:** guardar usuários e senhas com segurança.
- **Conteúdo:**
  - Migrations de `usuarios` e `papeis` (ALUNO, ADMIN).
  - Senhas com Argon2; hash `$2y$` do Laravel aceito e regravado em Argon2 no login.
  - Política de senha por tamanho (mínimo 12 para admin, máximo 128).
  - O primeiro admin é criado por comando (`docker compose run --rm api criar-admin <email>`), com a senha digitada na hora — nunca por migration.
  - Seed do profile `test` e `support/UsuariosSeed`.
- **Testes:** hash da senha e criação do admin.
- **Entrega:** admin criado pelo comando e gravado com a senha em hash.

### Cap. 7 — Login com JWT e refresh em cookie

**Fase:** 1.4 · **Prompt:** [f01-04](prompts/f01-04-usuarios-e-login.md)

- **Objetivo:** sessão segura sem guardar token em `localStorage`.
- **Conteúdo:**
  - Access token de 10 minutos no corpo da resposta, guardado pelo frontend só em memória.
  - Refresh token opaco, com o hash salvo no banco e rotação a cada uso. Se um refresh já usado reaparecer, toda a família de tokens é revogada.
  - Cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, **sem `Domain`**, para não ser enviado às outras aplicações de `celke.uno`.
  - JWT assinado e validado pelo Resource Server do Spring Security: só HS256, emissor e validade conferidos.
  - Login com a mesma resposta e o mesmo tempo para e-mail inexistente e senha errada.
  - Logout e "sair de todos os dispositivos"; `login()`, `bearer()` e `criarUsuario()` no `IntegrationTest`.
- **Testes:** token adulterado, expirado, de outra chave, com outro algoritmo, `alg: none` ou de outro emissor é recusado; refresh reutilizado revoga a família; atributos do cookie conferidos.
- **Entrega:** login, refresh e logout funcionando pelo Swagger.

### Cap. 8 — Autorização

**Fase:** 1.5 · **Prompt:** [f01-05](prompts/f01-05-protecoes.md)

- **Objetivo:** cada rota acessível só por quem deve.
- **Conteúdo:**
  - Papéis vindos do token, com `@PreAuthorize`.
  - `SecurityFilterChain` em modo "nega tudo por padrão", com cada rota pública declarada junto do método HTTP — nenhuma rota fica liberada para qualquer método por engano (como um `/api/usuarios` aberto).
- **Testes:** matriz parametrizada rota × papel (anônimo / ALUNO / ADMIN) com o status esperado; método não declarado numa rota pública exige token.
- **Entrega:** aluno recebe 403 em rota de admin.

### Cap. 9 — Proteções extras

**Fase:** 1.5 · **Prompt:** [f01-05](prompts/f01-05-protecoes.md)

- **Objetivo:** dificultar força bruta, abuso e CSRF.
- **Conteúdo:**
  - Limite por IP com Bucket4j em login e refresh, num filtro que roda antes da segurança e conta até as requisições inválidas; 429 com `Retry-After`.
  - Bloqueio temporário após falhas seguidas: 5 por e-mail + IP (o dono ainda entra de outro lugar) e 20 por e-mail vindas de qualquer IP; verificado antes de calcular o hash.
  - Proteção CSRF no refresh e no logout: `Origin` e `Sec-Fetch-Site` conferidos, porque outras aplicações em `*.celke.uno` são o mesmo site para o navegador e o `SameSite=Strict` não as barra.
  - Auditoria de login: resultado, IP e user-agent de toda tentativa, gravada mesmo quando o login falha.
  - IP real do cliente atrás do proxy: `X-Forwarded-For` aceito só de proxy interno (`forward-headers-strategy: native`).
  - Sem CORS na API: o Caddy serve front e API na mesma origem.
- **Testes:** bloqueio com relógio ajustável (bordas de 15 min e da janela de 1 h); filtro de limite por IP direto, com limite pequeno; 429 e auditoria pela API; refresh e logout de outra origem recusados sem tocar na sessão.
- **Entrega:** tentativas seguidas de login bloqueadas e registradas na auditoria.

### Cap. 10 — Cadastro, confirmação de e-mail e recuperação de senha

**Fase:** 1.6 · **Prompt:** [f01-06](prompts/f01-06-cadastro-e-recuperacao.md)

- **Objetivo:** o aluno cria a conta e recupera a senha sozinho.
- **Conteúdo:**
  - Cadastro com conta pendente e link de confirmação válido por 48 horas; login só depois de confirmar.
  - Recuperação de senha com link de 1 hora; a nova senha encerra todas as sessões e também confirma o e-mail.
  - Tokens de uso único com só o hash no banco; um link novo invalida o anterior.
  - E-mails pela fila, montados com Thymeleaf e enviados pelo worker: Mailpit no desenvolvimento, Iagente nos servidores (remetente de `celke.uno` com SPF e DKIM).
  - Healthcheck do worker por arquivo de batimento, para o compose e o deploy saberem que ele subiu.
- **Segurança:**
  - Cadastro, reenvio e recuperação respondem sempre o mesmo, exista ou não a conta; quem descobre o que aconteceu é o dono da caixa, pelo e-mail que recebe.
  - No máximo 3 e-mails por hora por conta e tipo, contra uso da plataforma para lotar a caixa de alguém.
  - Senha de aluno de 10 a 128 caracteres; limite por IP também nas rotas da conta.
- **Testes:** jornadas completas lendo o link no Mailpit; link de uso único; link antigo invalidado pelo reenvio; e-mail inexistente sem envio; limite de e-mails por hora.
- **Entrega:** cadastro → e-mail → confirmação → login, e recuperação de senha completa, com os e-mails enviados pelo worker.

---

## Parte 3 — Qualidade e entrega contínua

### Cap. 11 — Testes e2e e pipeline no GitHub Actions

**Fase:** 1.7 · **Prompt:** [f01-07](prompts/f01-07-e2e-e-pipeline.md)

- **Objetivo:** cada PR testado com a stack inteira, usando as imagens que vão para produção.
- **Conteúdo:**
  - `infra/compose.prod.yml`: a stack dos servidores, com Caddy na frente e imagens do GHCR pela tag do commit.
  - `infra/caddy/Caddyfile`: hosts pelo ambiente, só `/api` e o health check chegam à API, headers de segurança.
  - `infra/compose.e2e.yml` + `infra/e2e.env`: a mesma stack com HTTP em `*.localhost`, Mailpit e o seed do profile `e2e`, num projeto Compose separado do ambiente local.
  - Perfil Maven `e2e` no backend (`src/e2e/java`, REST Assured, Awaitility); os sites são escolhidos pelo cabeçalho `Host`, sem depender da resolução de `*.localhost`.
  - Jornadas `CadastroE2E` (e-mail entregue pelo worker de verdade), `SessaoE2E` (login → refresh → logout pelo Caddy) e `ProxyE2E` (headers, rotas bloqueadas, `X-Forwarded-For` forjado sem efeito).
  - `pipeline.yml`: testes → build das imagens com cache → e2e → relatórios e `docker compose logs` anexados quando falha; PR e push para `homolog` e `main`.
  - Regras de proteção das branches com os checks obrigatórios.
- **Segurança:** o e2e prova pelo próprio limite por IP que o cliente não consegue forjar o IP atrás do Caddy; Swagger e endpoints internos do Actuator não saem do proxy.
- **Testes:** as próprias jornadas e2e.
- **Entrega:** PR com o pipeline verde e as regras de proteção ativas.

### Cap. 12 — Preparar a VPS

**Fase:** 1.8 · **Prompt:** [f01-08](prompts/f01-08-vps.md) · **Roteiro:** [vps.md](vps.md)

- **Objetivo:** servidor seguro antes de receber a aplicação.
- **Conteúdo:**
  - `preparar-vps.sh` numa VPS Ubuntu 24.04 nova: Docker oficial com rotação de logs, administrador com sudo e usuário `deploy` sem sudo, SSH só por chave e sem root, fail2ban, atualizações automáticas, swap de 2 GB.
  - Firewall do painel da Hostinger + `ufw`: só 22, 80 e 443.
  - Cuidado: o Docker contorna o `ufw` nas portas que publica. Por isso só o Caddy publica portas; PostgreSQL, Garage e API ficam só na rede interna.
  - A configuração do SSH vale antes da que vem na imagem (`50-cloud-init.conf`), e o script só termina depois de você confirmar o login do administrador num segundo terminal.
  - DNS dos seis subdomínios (produção e homologação) apontando para a VPS.
- **Entrega:** acesso SSH só por chave e portas fechadas conferidas de fora.

### Cap. 13 — Produção e homologação com Docker na VPS

**Fase:** 1.8 · **Prompt:** [f01-08](prompts/f01-08-vps.md) · **Roteiro:** [vps.md](vps.md)

- **Objetivo:** os dois ambientes rodando na mesma VPS sem um derrubar o outro.
- **Conteúdo:**
  - `compose.prod.yml` por ambiente (`AMBIENTE`), com rede e apelidos próprios (`producao-api`, `homolog-api`); `compose.homolog.yml` por cima, com limites menores e o Swagger.
  - `/opt/plataforma-cursos/{caddy,producao,homolog}`, cada ambiente com `.env` próprio (permissão 600) gerado com segredos aleatórios e diferentes.
  - Um Caddy só nas portas 80/443, ligado às redes dos dois ambientes; `Caddyfile` com trechos comuns e um arquivo de sites por ambiente — o e2e usa o mesmo `Caddyfile`.
  - Homologação restrita por IP e com `noindex` (senha do Caddy não serve: ocuparia o cabeçalho `Authorization` do JWT); `adm.` de produção restrito por IP.
  - Headers no Caddy: HSTS sem `includeSubDomains`, `X-Content-Type-Options`, `Referrer-Policy`, sem `Server`; `lb_try_duration` para a API reiniciar sem erro 502.
  - Limites de memória e CPU por container (produção: API 1 GB, worker 2 GB e 1,5 CPU, PostgreSQL 1 GB, Garage 768 MB; homologação menor, com o worker em meia CPU) e `-Xmx` de acordo.
- **Entrega:** `homolog-aluno.celke.uno/api` e `aluno.celke.uno/api` respondendo com HTTPS.

### Cap. 14 — Deploy automático com rollback

**Fase:** 1.9 · **Prompt:** [f01-09](prompts/f01-09-deploy-automatico.md) · **Roteiro:** [vps.md](vps.md)

- **Objetivo:** merge na branch = versão no ar, com volta automática se falhar.
- **Conteúdo:**
  - Imagens com tag = SHA do commit, nunca `latest`, publicadas no GHCR pelo próprio job de e2e: vai para o servidor exatamente a imagem que passou nas jornadas.
  - `deploy.sh` na VPS: valida os argumentos, recebe só os arquivos de compose esperados, faz backup do banco, troca a tag, espera todos os containers ficarem saudáveis e, se algum não ficar, volta para a versão anterior (arquivos e tag).
  - Um deploy por vez (`flock`), deploy que continua mesmo se a conexão SSH cair, últimas versões e backups guardados.
  - Chave SSH presa ao script no `authorized_keys` (`command=...,no-pty,no-port-forwarding`) e impressão digital da VPS fixada no Actions.
  - Push na `homolog` → homologação; push na `main` → produção, com aprovação opcional; só PRs cancelam execuções antigas do pipeline.
  - Migrations compatíveis com a versão anterior, porque o rollback não desfaz migration.
- **Segurança:** a chave do deploy não abre shell, terminal nem túnel; o script recusa ambiente ou SHA fora do formato (inclusive tentativa de injetar comandos pelo SSH) e só extrai os arquivos esperados do pacote.
- **Testes:** simulação local de uma VPS: versão boa no ar, versão quebrada com rollback automático, backup restaurável, argumentos inválidos e deploy simultâneo recusados; chave presa ao script conferida com um `sshd` real.
- **Entrega:** deploy nos dois ambientes pelo pipeline e um rollback testado de propósito.

---

## Parte 4 — Domínio de cursos

### Cap. 15 — Curso, turma, módulo e aula

**Fase:** 2 · **Prompt:** [f02-01](prompts/f02-01-cursos-e-catalogo.md)

- **Objetivo:** modelar o conteúdo dos cursos.
- **Conteúdo:**
  - Curso → turmas → módulos → aulas. A turma é a oferta do curso: tem os próprios módulos e aulas e, no próximo capítulo, as matrículas.
  - Status como enum e `CHECK` no banco (nada de `[3,4,13,14]`): curso em rascunho, publicado ou arquivado; turma em preparação, ativa ou encerrada; módulo e aula ativos ou inativos.
  - Slug para o site, gerado do título quando não informado (sem acento, minúsculas e hífens) e único no banco.
  - Ordem de módulos e aulas sempre 1, 2, 3...: item novo entra no fim, a exclusão refaz a sequência e a reordenação recebe a lista completa.
  - Restrição única adiável (`DEFERRABLE INITIALLY DEFERRED`): o PostgreSQL só confere a ordem no commit, e a troca de posições acontece numa transação.
  - Trava do pai (`SELECT ... FOR UPDATE`) para duas criações simultâneas não pegarem a mesma posição.
  - Exclusão só de item vazio (`ON DELETE RESTRICT`); o que tem conteúdo é arquivado pelo status.
- **Testes:** slug e ordenação em tabela (lista incompleta, repetida, de outro pai, vazia); reordenação real no PostgreSQL; oito módulos criados ao mesmo tempo recebem 1 a 8.
- **Entrega:** tabelas criadas pelo Flyway e regras de ordem garantidas pelo banco.

### Cap. 16 — CRUD administrativo e catálogo público

**Fase:** 2 · **Prompt:** [f02-01](prompts/f02-01-cursos-e-catalogo.md)

- **Objetivo:** o admin gerencia os cursos e o site lista os publicados.
- **Conteúdo:**
  - Endpoints `/api/adm/**` com validação, `Location` na criação e `PATCH .../status`; reordenação de módulos e aulas.
  - Capa do curso em duas etapas: o navegador envia a imagem direto ao Garage por URL pré-assinada e a API confirma, reduz para até 1280 × 720 e regrava em JPEG.
  - Catálogo público: cursos publicados e curso por slug, com o conteúdo ativo da turma ativa mais recente.
  - Capa no catálogo por um endereço estável da API, que redireciona para uma URL assinada nova: a URL assinada vence em uma hora e quebraria o HTML pré-renderizado do site.
- **Segurança:**
  - `/api/adm/**` exige ADMIN no `SecurityConfig` e de novo com `@PreAuthorize`: o aluno recebe 403 antes de o corpo ser lido, e um controller novo sem a anotação continua protegido.
  - A imagem é conferida pelo conteúdo, não pela extensão nem pelo `Content-Type`: HTML enviado como `image/png` é recusado.
  - A re-codificação descarta o EXIF (GPS e dados da câmera) e qualquer conteúdo escondido no arquivo.
  - Dimensões lidas do cabeçalho antes de decodificar (proteção contra "bomba de descompressão"); 5 MB conferidos de novo no arquivo enviado, porque a URL pré-assinada não limita o tamanho.
  - A confirmação só aceita a chave gerada para o mesmo curso; o arquivo enviado é apagado em qualquer resultado.
  - Catálogo sem dados internos (ids de curso, status, chaves do bucket); rascunho e arquivado respondem 404.
- **Testes:** matriz de papéis de todas as rotas com ids reais; CRUD com `ProblemDetail`; capa com o Garage real (redução, EXIF removido, troca, remoção, envios recusados); catálogo com turmas, módulos e aulas inativos; e2e do admin criando o curso com capa e o visitante vendo no catálogo.
- **Entrega:** curso completo cadastrado pelo Swagger, com capa, visível no catálogo.

### Cap. 17 — Matrícula

**Fase:** 3 · **Prompt:** [f03-01](prompts/f03-01-matricula-e-acesso.md)

- **Objetivo:** ligar alunos às turmas.
- **Conteúdo:**
  - `matriculas` com início, fim opcional (acesso sem prazo), status (enum), forma de pagamento e transação.
  - Status gravado só `ATIVA` ou `CANCELADA`; vigente, vencida ou aguardando início são calculados com o `Clock`, sem job que marque matrículas.
  - Índices únicos parciais: uma matrícula ativa por aluno e turma, e cada transação gera uma matrícula só (base para o webhook de pagamento).
  - Matrícula manual pelo admin: matricular por e-mail, alterar período, cancelar (o histórico fica).
  - Endpoints do aluno: meus cursos (ativos, arquivados, expirados), arquivar e desarquivar.
  - Fronteira entre módulos: a matrícula guarda ids e consulta o conteúdo por um serviço exportado pelo módulo de cursos.
- **Segurança:** tudo da área do aluno usa o id do token, nunca um id enviado pelo cliente; matrícula de outro aluno responde 404.
- **Testes:** situação da matrícula em tabela; matrícula repetida, turma encerrada, transação repetida; abas de meus cursos; turma com matrículas não pode ser excluída.
- **Entrega:** aluno matriculado vê seus cursos.

### Cap. 18 — Política única de acesso

**Fase:** 3 · **Prompt:** [f03-01](prompts/f03-01-matricula-e-acesso.md)

- **Objetivo:** um ponto só decide o que o aluno pode ver.
- **Conteúdo:**
  - `AcessoAulaPolicy`: decide o acesso a aula, vídeo, arquivo, fórum, progresso e certificado; `AcessoAulaService.exigirAcesso` é a porta de entrada dos outros módulos.
  - Regras: matrícula ativa e dentro do período (fim inclusivo), ou aula gratuita de turma ativa e curso publicado.
  - A turma inteira (lista de módulos e aulas) só com matrícula vigente: a aula gratuita não abre a turma.
  - Data e hora por `Clock` injetado.
- **Segurança:**
  - Recurso de outro aluno responde 404, para não revelar que existe.
  - Aula gratuita de curso que voltou para rascunho não vaza; aula ou módulo inativo responde 404.
- **Testes:** unitários com todas as combinações de status × datas (instante antes, exato e depois de cada limite); matriz de acesso ao conteúdo; IDOR (aluno A tentando ver dados do aluno B); e2e com a matrícula vencendo.
- **Entrega:** aluno sem matrícula ou com matrícula vencida recebe 403.

---

## Parte 5 — Vídeo

### Cap. 19 — Upload direto para o Garage

**Fase:** 4

- **Objetivo:** enviar vídeos grandes sem passar pela API.
- **Conteúdo:**
  - O ADM pede URLs pré-assinadas de upload multipart, e o navegador envia direto para `media.celke.uno` (prefixo `uploads/` do bucket `midia`).
  - A API valida tipo e tamanho antes de assinar.
- **Segurança:** depois do upload, a API confere o tamanho e o tipo real do arquivo antes de enfileirar.
- **Testes:** upload multipart contra o Garage do Testcontainers; arquivo com extensão falsa rejeitado.
- **Entrega:** vídeo em `uploads/` e job criado na fila.

### Cap. 20 — Transcodificação com ffmpeg

**Fase:** 4

- **Objetivo:** reduzir o tamanho do vídeo e prepará-lo para streaming.
- **Conteúdo:**
  - Worker consome a fila: `ffprobe` para analisar, ffmpeg para gerar HLS em 720p + 1080p (sem aumentar a resolução de origem), H.264 CRF 23–26, `maxrate`, AAC 128k, segmentos de 6 s.
  - Thumbnail e duração; resultado em `videos/`; o original é apagado depois que a transcodificação dá certo.
  - Status `pendente → processando → pronto/erro`, reprocessar pelo ADM.
  - Job volta para a fila se o worker parar no meio (`stop_grace_period` + lease com expiração).
  - Comparativo de tamanho antes e depois.
- **Segurança:** ffmpeg chamado via `ProcessBuilder` com lista de argumentos, nunca concatenando uma string de shell.
- **Testes:** transcodificar um vídeo de 3 s; e2e de upload até o status "pronto".
- **Entrega:** vídeo convertido em HLS com o tamanho reduzido registrado.

### Cap. 21 — Limites da VPS

**Fase:** 4

- **Objetivo:** a transcodificação não pode derrubar a API.
- **Conteúdo:**
  - `cpus` e limite de memória no container do worker; um job por vez; prioridade baixa no processo.
  - Medição do tempo de processamento por minuto de vídeo na VPS.
  - Conta de quantas horas de vídeo cabem no disco.
- **Entrega:** tabela com o tempo medido e a API respondendo normalmente durante a transcodificação.

### Cap. 22 — Playlist assinada pela API

**Fase:** 5

- **Objetivo:** só quem tem acesso assiste, e o bucket continua privado.
- **Conteúdo:**
  - `GET /api/aulas/{id}/video.m3u8` verifica a `AcessoAulaPolicy` e reescreve a playlist com URLs pré-assinadas de cada segmento, válidas pela duração do vídeo + margem.
  - Substitui os cookies assinados de CDN, que não existem nesta hospedagem.
- **Testes:** sem matrícula, 403; com matrícula, segmentos assinados que baixam e depois expiram.
- **Entrega:** vídeo tocando por uma playlist assinada.

### Cap. 23 — Criptografia AES-128 do HLS (nível extra)

**Fase:** 5 (opcional)

- **Objetivo:** um segmento copiado não serve para nada sem a chave.
- **Conteúdo:** segmentos cifrados; a chave só é entregue por um endpoint autenticado que passa pela `AcessoAulaPolicy`.
- **Testes:** a chave sem token ou sem matrícula é recusada.
- **Entrega:** segmento baixado direto do Garage não toca sem a chave.

### Cap. 24 — Progresso e log de acesso

**Fase:** 5

- **Objetivo:** o aluno retoma de onde parou e acompanha o avanço.
- **Conteúdo:**
  - Posição do vídeo, aula vista / revisar, percentual da turma.
  - Log de acesso com retenção definida (LGPD).
- **Testes:** progresso de outro aluno responde 404; percentual calculado com as bordas (nenhuma aula, todas as aulas).
- **Entrega:** progresso salvo e refletido na turma.

---

## Parte 6 — Restante do domínio

### Cap. 25 — Arquivos de apoio

**Fase:** 6

- **Objetivo:** entregar código-fonte e materiais com o mesmo controle do vídeo.
- **Conteúdo:** arquivos por aula e por módulo, enviados pelo ADM e baixados por URL pré-assinada curta, sempre pela `AcessoAulaPolicy`.
- **Testes:** download permitido e bloqueado.
- **Entrega:** aluno matriculado baixa o arquivo; sem matrícula, 403.

### Cap. 26 — Fórum por aula e por turma

**Fase:** 7

- **Objetivo:** dúvidas respondidas dentro da plataforma.
- **Conteúdo:** perguntas e respostas com anexo validado; moderação pelo admin (ocultar, fixar, responder como instrutor); rate limit na criação.
- **Segurança:** conteúdo em Markdown convertido para HTML filtrado, contra XSS.
- **Testes:** XSS, IDOR em anexo, aluno sem matrícula; e2e de pergunta e resposta.
- **Entrega:** aluno pergunta, admin responde, aluno vê a resposta.

### Cap. 27 — Certificado em PDF

**Fase:** 8

- **Objetivo:** certificado gerado e verificável.
- **Conteúdo:**
  - Regra de conclusão (percentual de aulas vistas) configurável por turma; nome confirmado pelo aluno.
  - PDF com openhtmltopdf e código de validação público, usado na página "validar certificado" do site.
- **Testes:** regra de conclusão com as bordas; e2e de conclusão → certificado → validação.
- **Entrega:** certificado baixado e validado pelo código.

### Cap. 28 — Perfil e termos

**Fase:** 9

- **Objetivo:** o aluno cuida dos próprios dados e aceita os termos vigentes.
- **Conteúdo:**
  - Dados, troca de senha (exige a senha atual) e foto de perfil redimensionada, sem metadados EXIF.
  - Termos versionados: aceite obrigatório no primeiro acesso e a cada nova versão.
- **Testes:** troca de senha com usuário próprio do teste; e2e de novo termo bloqueando as rotas até o aceite.
- **Entrega:** perfil editável e aceite de termos funcionando.

---

## Parte 7 — Operação

Fica antes do frontend porque precisa estar pronta antes de entrarem dados reais em produção.

### Cap. 29 — Backup e restauração

**Fase:** 10

- **Objetivo:** recuperar tudo se a VPS for perdida.
- **Conteúdo:**
  - `pg_dump` diário.
  - Backup do Garage com restic **para fora da VPS**: o snapshot da Hostinger sozinho não basta, porque o vídeo e o banco estão no mesmo disco.
  - Teste de restauração documentado.
- **Entrega:** restauração completa executada a partir do backup.

### Cap. 30 — Monitoramento e disco

**Fase:** 10

- **Objetivo:** saber do problema antes do aluno.
- **Conteúdo:**
  - Uptime externo e alertas; alerta de disco e de falhas no worker.
  - Rotação de logs do Docker; limpeza de uploads órfãos e dos dados da homologação.
  - Plano de saída dos vídeos para um S3 externo (R2/B2) quando o disco apertar: só muda o endpoint no `.env`.
- **Entrega:** alertas chegando num teste provocado.

### Cap. 31 — Checklist final de segurança

**Fase:** 10

- **Objetivo:** revisar a API inteira antes de abrir para alunos.
- **Conteúdo:**
  - Revisão das categorias do OWASP ASVS nível 1.
  - Varredura com OWASP ZAP contra a homologação.
  - Trivy no pipeline e atualização de dependências.
- **Entrega:** relatório sem achados de severidade alta.

---

## Parte 8 — Frontend com `@celke/kole-react`

### Cap. 32 — A biblioteca `@celke/kole-react`

**Pré-requisito do frontend** (repositório da biblioteca)

- **Objetivo:** conhecer a biblioteca de layout usada pelos fronts e como instalá-la.
- **Conteúdo:** moldura (`Estrutura`, `TelaLogin`), formulários, tabelas, diálogos, avisos e o visualizador de arquivos; instalação do GitHub (`github:celkecursos/kole-react#<commit>`). Os componentes próprios da área do aluno (player de vídeo, lista de aulas, fórum, campo de imagens) ficam no app.
- **Testes:** componentes testados com Vitest no repositório da biblioteca.
- **Entrega:** commit da biblioteca fixado no `web/package.json`.

### Cap. 33 — Workspace web e a biblioteca

**Fase:** 11

- **Objetivo:** base dos três frontends servida pelo Caddy.
- **Conteúdo:**
  - Pacote único em `web/` com `aluno` e `adm` (Vite + React + TypeScript); o `site` entra na Fase 14.
  - `@celke/kole-react` instalada do GitHub com commit fixo.
  - Client HTTP com token em memória, refresh silencioso e fila de requisições durante o refresh; proteção de rotas por papel; tratamento de termo pendente.
  - Telas de login, cadastro, confirmação de e-mail e recuperação de senha.
  - Imagem `web`: build dos fronts + Caddy servindo `/srv/site`, `/srv/aluno` e `/srv/adm`; proxy do Vite `/api` no desenvolvimento.
- **Segurança:** CSP (`connect-src 'self' https://media.celke.uno`), `X-Frame-Options`, cache longo nos assets com hash e sem cache no `index.html`.
- **Testes:** Vitest + Testing Library; Playwright no pipeline (login do aluno e do admin, aluno barrado no ADM, logout).
- **Entrega:** login funcionando em `aluno.celke.uno` e `adm.celke.uno`.

### Cap. 34 — ADM

**Fase:** 12

- **Objetivo:** o admin opera a plataforma pelo navegador.
- **Conteúdo:** cursos, turmas, módulos e aulas (reordenação pelos botões subir e descer); upload de vídeo multipart com progresso e status de processamento; arquivos de apoio, matrículas, moderação do fórum e termos.
- **Testes:** Playwright do admin criando um curso completo e enviando um vídeo curto até "pronto".
- **Entrega:** curso publicado inteiramente pelo ADM.

### Cap. 35 — Área do aluno

**Fase:** 13

- **Objetivo:** a experiência completa do aluno.
- **Conteúdo:** dashboard de cursos; página da turma com progresso; aula com player, lista lateral, anterior/próxima, vista/revisar, arquivos e fórum (com imagens nas perguntas e respostas, Fase 18); certificados e perfil.
- **Testes:** Testing Library e Playwright (login → turma → aula → vídeo toca → marca como vista → progresso atualizado).
- **Entrega:** aluno assiste a uma aula do início ao fim.

### Cap. 36 — Site moderno

**Fase:** 14

- **Objetivo:** vitrine pública rápida e bem indexada.
- **Conteúdo:**
  - Pré-renderização estática (cada página vira um HTML) em `cursos.celke.uno`, gerada pelo container `site` a partir da API do próprio ambiente ao subir e a cada 5 minutos.
  - Home, catálogo, página do curso, validar certificado e páginas legais.
  - Layout novo com os mesmos tokens visuais da `@celke/kole-react`.
  - SEO: meta tags, Open Graph, sitemap, `robots.txt`.
- **Testes:** meta de Lighthouse verificada no pipeline.
- **Entrega:** site publicado com a nota de Lighthouse atingida.

---

## Parte 9 — Migração do legado (opcional)

### Cap. 37 — Importar dados do MySQL do CLMS

**Fase:** 15

- **Objetivo:** trazer os alunos e cursos atuais para a nova plataforma.
- **Conteúdo:**
  - ETL de usuários, cursos, turmas, módulos, aulas, matrículas e progresso.
  - Status numéricos do legado (`3, 4, 13, 14, 15`...) mapeados para os enums novos.
  - Senhas bcrypt do Laravel aproveitadas: o hash `$2y$` é aceito no primeiro login e regravado em Argon2.
  - Reprocessamento dos vídeos já existentes no S3 para HLS.
- **Entrega:** alunos do legado entram com a senha atual e encontram seus cursos.

---

## Parte 10 — Vendas e pagamentos

### Cap. 38 — Meios de pagamento e o webhook da Hotmart

**Fase:** 16

- **Objetivo:** receber as notificações da Hotmart com segurança, numa estrutura que aceita outros meios de pagamento sem mexer no núcleo.
- **Conteúdo:**
  - Webhook 1.0.0 (formato antigo, não recomendado) × 2.0.0: corpo JSON com `id` por evento, `X-HOTMART-HOTTOK` no cabeçalho, `xcod` em `data.purchase.origin`.
  - A porta `GatewayPagamento` (um bean por meio): campos de configuração, capacidades, teste das credenciais, checkout, autenticação, interpretação e consulta; `GatewaysRegistrados` por tipo.
  - Meio de pagamento configurado pelo painel: campos comuns em JSON, segredos cifrados com AES-256-GCM (`PAGAMENTOS_CHAVE`, IV por gravação, webhook token como dado associado), um ativo por vez com índice único parcial.
  - Migration `V15`: meios de pagamento, ofertas, pedidos, histórico e eventos recebidos; usuário sem senha; matrícula ligada ao pedido.
  - `POST /api/pagamentos/webhooks/{token}`: limite de tamanho e por IP, hottok comparado em tempo constante, `INSERT ... ON CONFLICT DO NOTHING` como idempotência, corpo mascarado (documento, telefone, IP), job na fila e 200 na hora.
  - Por que só responder erro quando nada foi gravado: a Hotmart reenvia e desativa a URL que continua falhando.
  - Homologação restrita por IP com exceção só para o caminho do webhook.
- **Segurança:** segredos nunca voltam na API nem ficam em texto puro no banco; 401 sem gravar nada; 413 para corpo grande.
- **Testes:** interpretação de cada fixture (campos opcionais do afiliado, status desconhecido, data em número e em texto), hottok, cifra (ida e volta, IV diferente, chave errada), integração do webhook (duplicado, 401, 404, corpo inválido, 413) e matriz de papéis.
- **Entrega:** "Enviar teste" da Hotmart aparece em "Eventos recebidos" na homologação.

### Cap. 39 — Pedido, status e matrícula

**Fase:** 16

- **Objetivo:** transformar cada notificação na matrícula certa, mesmo com eventos repetidos e fora de ordem.
- **Conteúdo:**
  - Status interno do pedido, igual para todos os meios, e a tabela de transições num lugar só (`TransicoesPedido`): reembolso e chargeback sempre valem; aprovação depois do reembolso não reabre.
  - Worker com uma transação por evento e trava no pedido (`SELECT ... FOR UPDATE`); pedido achado pelo `xcod`, pela transação ou criado como `EXTERNO` (afiliado, link direto).
  - Matrícula pelo pagamento: criar, estender quem já está matriculado, reativar depois de disputa, cancelar no reembolso.
  - Conta do comprador sem senha e o link de criar senha no e-mail da matrícula (nunca senha por e-mail).
  - Checkout por redirecionamento com o código do pedido no `xcod`; página de retorno que consulta o pedido.
  - Avisos ao admin (chargeback, disputa, evento com erro), reprocessamento pelo painel, retenção do corpo dos eventos e a reconciliação pela API de vendas (ligada por configuração).
- **Testes:** todas as transições; integração do fluxo completo (aprovado → acesso e e-mail → reembolso tira o acesso), venda sem `xcod` criando a conta, boleto aguardando, evento antigo ignorado, dois eventos do mesmo pedido ao mesmo tempo; jornada e2e pelo Caddy até o e-mail no Mailpit.
- **Entrega:** compra aprovada libera o curso e o reembolso tira, sem matrícula nem e-mail duplicado.

### Cap. 40 — Comprar pelo site, painel de vendas e testes de ponta a ponta

**Fase:** 16

- **Objetivo:** o aluno compra pelo site e o admin acompanha as vendas pelo ADM.
- **Conteúdo:**
  - ADM: meios de pagamento com o formulário montado a partir dos campos do gateway, "Testar credenciais", ativação e a URL do webhook pronta para copiar; aba "Venda" da turma com as ofertas; pedidos com a linha do tempo; eventos recebidos com o corpo e "Reprocessar".
  - Aluno: `/comprar/:oferta` (volta para a compra depois de entrar ou criar a conta) e `/pagamento/retorno`, que acompanha o pedido a cada 3 s; "Crie sua senha" para quem veio da compra.
  - Site: preço e botão "Comprar" de cada oferta na página do curso, com as ofertas nos dados estruturados.
  - Como adicionar outro meio de pagamento (Mercado Pago, Stripe...) só com um valor no enum e um bean novo.
- **Testes:** Vitest da página de retorno e do formulário; Playwright do admin cadastrando a Hotmart (validação e credencial errada), da compra interceptando o `pay.hotmart.com`, do boleto, do reembolso e do evento ignorado reprocessado.
- **Entrega:** compra de ponta a ponta na homologação com a Hotmart de teste.

---

## Parte 11 — Auditoria e logs

### Cap. 41 — Trilha de auditoria e logs persistentes

**Fase:** 17

- **Objetivo:** saber quem fez o quê nos dados, de onde e quando, e guardar o log técnico por meses.
- **Conteúdo:**
  - Por que não Hibernate Envers, JaVers ou Spring Data Auditing: uma tabela por entidade, outra camada de modelo ou só "quem alterou por último"; aqui, uma tabela única com ação, origem e IP.
  - Ouvinte dos eventos do Hibernate (`PostInsert`, `PostUpdate`, `PostDelete`) que grava na mesma transação, com só os campos que mudaram na alteração.
  - Contexto da ação: usuário do token, rota com as variáveis, IP, navegador, id da requisição; job da fila pelo MDC; "sistema" nos agendamentos.
  - `@DadoSensivel` (hash de senha, segredos, tokens como `***`) e `@SemAuditoria` (progresso, logs, tokens de sessão).
  - Só inclusão garantida pelo banco (gatilho que recusa `UPDATE` e `DELETE`) e a retenção como única exceção.
  - Tela de consulta no ADM com filtros e o antes/depois lado a lado.
  - Logs da API e do worker em arquivo num volume, com rotação diária no horário de Brasília, compressão e limite por idade e por tamanho; como ler na VPS.
- **Segurança:** a trilha não guarda segredos; o ADM só lê; nem a própria aplicação consegue alterar ou apagar fora da retenção.
- **Testes:** unitários da conversão de valores e do contexto; integração com ADM, aluno, worker, segredo oculto, operação recusada sem rastro, gatilho e retenção; Playwright da tela.
- **Entrega:** a alteração de um curso aparece na auditoria com o antes e o depois; os logs ficam no volume depois de um deploy.

---

## Parte 12 — Segurança reforçada

### Cap. 42 — Segundo fator dos administradores e senhas fortes

**Fase:** 19 · **Prompt:** [f19-01](prompts/f19-01-seguranca.md)

- **Objetivo:** uma senha vazada não basta para entrar no ADM, e as senhas fáceis de adivinhar ficam de fora.
- **Conteúdo:**
  - TOTP (RFC 6238) sem biblioteca: HMAC-SHA1, passos de 30 s, 6 dígitos, tolerância de um passo, Base32 e o endereço `otpauth://` do QR code; testes com os vetores da RFC.
  - Login em duas etapas: a senha certa devolve um desafio de 5 minutos (sem sessão); o código abre a sessão; o código errado conta no bloqueio; o mesmo código não vale duas vezes.
  - Primeiro acesso com o QR code e os 10 códigos de recuperação de uso único (só o hash no banco); segredo cifrado com AES-256-GCM e dado associado (`TOTP_CHAVE`); comando `redefinir-2fa`.
  - Política de senha: a lista de senhas comuns e vazadas (gerada por comando a partir de uma lista pública), nome e e-mail dentro da senha, sequências de teclado e de alfabeto, repetições; a mesma regra no cadastro, na redefinição, no perfil e no `criar-admin`; o medidor de força da biblioteca nas telas.
  - Front: a tela da segunda etapa compartilhada entre o ADM e a área do aluno, com o QR code gerado no navegador.
- **Segurança:** a sessão do administrador nunca abre só com a senha; o segredo do aplicativo não sai do banco em texto.
- **Testes:** unitários do TOTP (RFC), da política e do login em duas etapas; integração do cadastro, do código errado, do código de recuperação de uso único e do segredo cifrado; Playwright do admin entrando com o código.
- **Entrega:** o admin cadastra o aplicativo no primeiro acesso e passa a entrar com o código.

### Cap. 43 — Menos privilégio: banco, limites, contas e dependências

**Fase:** 19 · **Prompt:** [f19-01](prompts/f19-01-seguranca.md)

- **Objetivo:** uma falha na aplicação causa o menor estrago possível.
- **Conteúdo:**
  - Usuário do banco só de dados para a API e o worker; o serviço `migracao` com o dono do schema, o `afterMigrate.sql` que cria e libera o usuário da aplicação, e o deploy que completa o `.env` dos servidores.
  - Limite por usuário nas gravações e no checkout (Bucket4j, depois da autenticação).
  - ADM → Usuários: bloquear, desbloquear e encerrar as sessões, com o access token já emitido recusado na hora (validador do JWT com a situação da conta em memória por 30 s).
  - Compra com confirmação (nenhum pedido ao abrir a página) e checkout só em HTTPS nos domínios do meio de pagamento.
  - Token dos links de e-mail fora da barra de endereço.
  - Dependabot e `npm audit` no pipeline.
  - O teste que varre todas as rotas da API e falha se uma rota nova nascer aberta.
- **Testes:** integração do usuário do banco (dados sim, estrutura não), da tela de usuários e da varredura das rotas; unitário do limite por usuário; Playwright do bloqueio e da compra confirmada.
- **Entrega:** a aplicação não consegue apagar tabelas; um aluno bloqueado sai na hora.

---

## Parte 13 — Privacidade

### Cap. 44 — LGPD: cópia dos dados, exclusão da conta e política de privacidade

**Fase:** 20 · **Prompt:** [f20-01](prompts/f20-01-lgpd.md)

- **Objetivo:** o aluno exerce os direitos do titular sozinho, pelo perfil, e a política de privacidade diz exatamente o que o sistema faz.
- **Conteúdo:**
  - O que é dado pessoal na plataforma, por módulo, e por que cada módulo responde pelas próprias tabelas (`DadosPessoais`).
  - Baixar meus dados: um JSON com a conta e uma seção por módulo, datas em ISO-8601.
  - Excluir a conta: o que é apagado (progresso, certificados, foto, sessões, segundo fator), o que é anonimizado (conta, pedidos) e o que fica pelo prazo legal (matrículas e pedidos, aceites, registros de acesso, auditoria, backups); a anonimização em vez de apagar a linha do usuário.
  - Retenção: a auditoria de login passa a ser limpa depois de 180 dias, como o log de acesso aos vídeos.
  - O modelo da política de privacidade: controlador e encarregado, dados e finalidades, bases legais, compartilhamento, prazos, direitos, segurança, cookies.
- **Segurança:** a exclusão exige a senha e a palavra de confirmação; o access token cai na hora; administrador não se exclui por aqui.
- **Testes:** integração da exportação e da exclusão (dados anonimizados, sessões encerradas, auditoria mantida, e-mail enviado) e da retenção; Playwright de baixar os dados e excluir a conta.
- **Entrega:** um aluno baixa os dados e exclui a conta pelo perfil; a política publicada bate com o sistema.

---

## Parte 14 — Site dinâmico

### Cap. 45 — Site em React com renderização no servidor

**Fase:** 21 · **Prompt:** [f21-01](prompts/f21-01-site-react-ssr.md)

- **Objetivo:** o site público mostra sempre o que está na API, com o mesmo HTML completo para os buscadores e uma interface moderna.
- **Conteúdo:**
  - Estático gerado de tempos em tempos × renderização no servidor a cada requisição: o que muda para o SEO (nada, o HTML chega pronto) e para o conteúdo (na hora, sem esperar a próxima geração).
  - React Router em modo framework: `loader` no servidor, `meta` com canônico, Open Graph e `noindex` na homologação, `headers` de cache, `ErrorBoundary` com 404 de verdade, rotas de recurso para `sitemap.xml` e `robots.txt`.
  - Streaming para o navegador e a página inteira de uma vez para os robôs (`isbot`); CSP com nonce por requisição.
  - Cache curto das leituras da API no servidor e o que muda no Caddy (o `site` atende na porta 3000).
  - Layout moderno com os tokens da `@celke/kole-react`, tema escuro e movimento reduzido; busca do catálogo no navegador depois da hidratação.
  - Cursos de exemplo pela API do ADM, com capa gerada no próprio script.
- **Testes:** funções de SEO (unidade), Playwright do site (meta tags, 404, busca, certificado) e Lighthouse no pipeline.
- **Entrega:** o site publica um curso assim que ele é publicado no ADM, com Lighthouse 100 nas quatro categorias.
`````

`docs/operacao.md`:

`````markdown
# Operação

Rotina da plataforma depois que a VPS está no ar ([roteiro da VPS](vps.md)): backup e restauração,
monitoramento e alertas, logs, espaço em disco, saída para um S3 externo e a revisão de segurança.

## 1. Backup diário

`infra/scripts/backup.sh <ambiente>` guarda, com o [restic](https://restic.net), num repositório S3
**fora da VPS** (Cloudflare R2 ou Backblaze B2):

- o dump do PostgreSQL (`pg_dump --format=custom`), por stdin, sem arquivo temporário no disco;
- os arquivos do Garage, com o snapshot consistente dos metadados (`garage meta snapshot`); o SQLite em
  uso fica de fora.

Retenção: 7 diários, 4 semanais e 6 mensais por ambiente. O restic cifra tudo com a `RESTIC_PASSWORD`.

### Configuração (uma vez, na VPS)

1. Crie um bucket privado no R2 (ou B2) só para os backups e uma chave com acesso apenas a ele.
2. Na VPS, como `deploy`:

   ```bash
   sudo install -o root -g root -m 755 /tmp/backup.sh /usr/local/bin/plataforma-cursos-backup
   install -m 600 /dev/null /opt/plataforma-cursos/backup.env
   nano /opt/plataforma-cursos/backup.env
   ```

   ```bash
   RESTIC_REPOSITORY=s3:https://<conta>.r2.cloudflarestorage.com/plataforma-cursos-backup
   RESTIC_PASSWORD=          # openssl rand -base64 48 — guarde também no gerenciador de senhas
   AWS_ACCESS_KEY_ID=
   AWS_SECRET_ACCESS_KEY=
   ```

3. Agendamento (`crontab -e` do `deploy`), fora do horário de aula:

   ```cron
   30 3 * * * /usr/local/bin/plataforma-cursos-backup producao >> /opt/plataforma-cursos/backup.log 2>&1
   45 3 * * * /usr/local/bin/plataforma-cursos-backup homolog  >> /opt/plataforma-cursos/backup.log 2>&1
   ```

> Sem a `RESTIC_PASSWORD` o backup não abre. Ela fica em dois lugares: no `backup.env` da VPS e no
> gerenciador de senhas do responsável.

## 2. Restauração

`infra/scripts/restaurar.sh <ambiente> [snapshot-postgres] [snapshot-garage]` para a API e o worker,
recria o banco a partir do dump, troca os arquivos do Garage pelos do backup (com os metadados do
snapshot) e sobe tudo de novo. Pede o nome do ambiente para confirmar (ou `CONFIRMAR=sim`).

```bash
docker run --rm --env-file /opt/plataforma-cursos/backup.env restic/restic:0.19.1 snapshots   # escolher
restaurar.sh homolog                                                                         # o mais recente
```

Se algo falhar no meio, a API e o worker **continuam parados de propósito** (subir com o banco pela
metade é pior): corrija e rode de novo.

O dump traz as tabelas, não os papéis do PostgreSQL: a subida depois da restauração passa pelo serviço
`migracao`, que recria o usuário da aplicação e as permissões dele.

### Teste de restauração (feito e repetido a cada trimestre)

Testado na stack de e2e, com um repositório restic local:

```bash
export MSYS_NO_PATHCONV=1 ENV_BACKUP=<backup.env com RESTIC_REPOSITORY=/repo> \
       PROJETO=plataforma-cursos-e2e RESTIC_EXTRA="-v restic-teste:/repo"
infra/scripts/backup.sh e2e
# estrago: UPDATE cursos SET titulo = 'ESTRAGADO'; e apagar /garage/data do volume
CONFIRMAR=sim infra/scripts/restaurar.sh e2e
```

Resultado: os títulos dos 7 cursos voltaram, os 11 objetos do bucket baixaram pela API S3 e a API e o
worker subiram saudáveis. O procedimento trimestral repete o teste na **homologação** com o backup da
produção mais recente.

## 3. Monitoramento e alertas

| O quê | Como |
|---|---|
| Site fora do ar | Monitor externo (UptimeRobot ou Better Stack, plano gratuito) a cada 5 minutos em `https://aluno.celke.uno/actuator/health` e `https://cursos.celke.uno`, com aviso por e-mail e celular |
| Job que desistiu (conversão de vídeo, por exemplo) | O worker verifica a cada 15 minutos os jobs que foram para `ERRO` e envia um e-mail para `ALERTA_EMAIL` com o resumo |
| Pagamento contestado ou notificação com erro | Chargeback, disputa aberta e notificação de pagamento que foi para `ERRO` geram um e-mail para `ALERTA_EMAIL` na hora (veja `docs/pagamentos.md`) |
| Disco quase cheio | O mesmo verificador do worker mede o disco da VPS (o sistema de arquivos do container é o do host) e alerta abaixo de 15% livre, repetindo a cada 6 horas enquanto não melhorar |
| Container parado | `restart: unless-stopped` e o healthcheck de cada serviço; o deploy só conclui com tudo saudável |

`ALERTA_EMAIL` fica no `.env` de cada ambiente; em branco, os alertas do worker ficam desligados.
Falha no envio de e-mail não gera alerta por e-mail (iria pela mesma fila): aparece no log do worker.

## 4. Logs e limpeza

- **Logs do Docker** com rotação em todos os serviços (`json-file`, 5 arquivos de 10 MB por container):
  no máximo ~250 MB por ambiente, poucos dias de histórico. Os logs da API e do worker são JSON (ECS) com o id de correlação e o `@timestamp` no horário de Brasília (`-03:00`); o site e o `deploy.log` usam o mesmo fuso.
- **Logs persistentes da API e do worker**: além do console, cada um grava o mesmo JSON em
  `/var/log/plataforma-cursos/api.log` e `worker.log`, no volume `logs` do ambiente (sobrevive a deploy e
  recriação do container). Um arquivo por dia de Brasília, comprimido (`api.log.2026-10-22.0.gz`), novo
  arquivo a cada 50 MB, guardado por **180 dias** ou até **2 GB por serviço**, o que vier primeiro. Para
  ler, como `deploy`:

  ```bash
  cd /opt/plataforma-cursos/producao
  docker compose -p plataforma-cursos-producao -f compose.prod.yml exec api ls -lh /var/log/plataforma-cursos
  docker compose -p plataforma-cursos-producao -f compose.prod.yml exec api tail -n 100 /var/log/plataforma-cursos/api.log
  # Um dia antigo, procurando um id de requisição (funciona com o container parado)
  docker run --rm -v plataforma-cursos-producao_logs:/logs:ro alpine \
    sh -c 'zcat /logs/api.log.2026-10-22.*.gz | grep 7f1c2a90'
  ```

  O volume não entra no backup diário (o que importa guardar por anos está no banco: a auditoria).
- **Uploads abandonados** (`uploads/` do bucket): a regra de expiração do bucket apaga em 2 dias, e os
  uploads em partes não concluídos em 1 dia. Os envios aceitos saem de `uploads/` na confirmação.
- **Log de acesso aos vídeos**: o worker apaga todo dia o que passou de 180 dias.
- **Auditoria de login** (e-mail, IP, navegador de cada tentativa): o worker apaga todo dia o que passou
  de 180 dias (`app.auth.auditoria-login.retencao`).
- **Dados da homologação**: quando acumular lixo de testes,
  `docker compose -p plataforma-cursos-homolog -f compose.prod.yml -f compose.homolog.yml down -v`
  e o próximo deploy da `homolog` recria tudo vazio (crie o admin de novo com `criar-admin`).

## 4.1 Auditoria

A tabela `auditoria` guarda cada inclusão, alteração e exclusão feita pela aplicação nos dados (ADM, área
do aluno, cadastro e worker): **quem** (usuário do token; vazio para visitante e sistema), **o quê**
(entidade e id), a **operação**, o **antes e o depois** dos campos (na alteração, só os que mudaram),
**de onde** (rota da requisição, como `PUT /api/adm/cursos/{id}`, ou o job da fila, como
`job pagamento.processar-evento`), **IP**, navegador, id da requisição e a data.

- Consulta no ADM: **Segurança → Auditoria**, com filtro por entidade, id, operação e e-mail do usuário.
- Dados sensíveis (hash de senha, segredos do meio de pagamento, token do webhook) aparecem como `***`:
  a trilha mostra que mudaram, nunca o valor.
- Ficam fora: progresso das aulas (muda a cada poucos segundos), log de acesso a vídeo, auditoria de
  login, notificações de pagamento (já são registros próprios) e tokens de sessão e de e-mail. Mudanças
  feitas direto em SQL (fila, atualizações em lote) também não passam pela trilha.
- O registro entra na mesma transação da mudança: o que foi desfeito não aparece.
- **Só inclusão**: o banco recusa `UPDATE` e `DELETE` na tabela (gatilho). A única exclusão é a retenção,
  feita pelo worker aos domingos, que apaga o que passou de **5 anos** (`app.auditoria.retencao`).
- Login e tentativas de login continuam na `auditoria_login`; acesso a vídeo na `acessos_video`.

## 4.2 LGPD: direitos do titular e retenção

- **Baixar meus dados** (`GET /api/perfil/dados`, perfil do aluno → Privacidade): JSON com a conta e uma
  seção por módulo (`matriculas`, `progresso`, `forum`, `certificados`, `pedidos`, `aceitesTermos`,
  `entradas`, `acessosVideo`). Cada módulo responde pelas próprias tabelas (`shared/lgpd/DadosPessoais`):
  módulo novo com dado pessoal implementa a interface.
- **Excluir minha conta** (`POST /api/perfil/exclusao`, senha + "EXCLUIR"; administradores não se
  excluem por aqui): aviso por e-mail para o endereço da conta; apagados progresso, certificados (com o
  PDF), foto, sessões, links de e-mail e segundo fator; conta anonimizada (`Conta excluída`,
  `excluida-<id>@conta-excluida.invalid`, sem senha, inativa); pedidos sem e-mail e nome. Ficam, pelo
  prazo legal: matrículas e pedidos (5 anos), aceites dos termos, entradas e acessos aos vídeos (180
  dias), a trilha de auditoria (5 anos, com o antes da anonimização) e os backups (até 6 meses).
- **Política de privacidade**: o modelo em [`politica-privacidade-modelo.md`](politica-privacidade-modelo.md)
  traz os dados, as finalidades, as bases legais e os prazos acima; publique pelo ADM → Termos depois da
  revisão jurídica. Mudou um prazo na configuração? Mude o texto publicado.

| Dado | Retenção | Onde |
|---|---|---|
| Trilha de auditoria | 5 anos | `app.auditoria.retencao` (worker, semanal) |
| Auditoria de login | 180 dias | `app.auth.auditoria-login.retencao` (worker, diária) |
| Acessos aos vídeos | 180 dias | `app.video.retencao-log-acesso` (worker, diária) |
| Corpo das notificações de pagamento | 180 dias | `app.pagamentos.retencao-payload` (worker) |
| Logs da API e do worker | 180 dias ou 2 GB | volume `logs` |
| Backups | 7 diários, 4 semanais, 6 mensais | `backup.sh` (restic) |

## 5. Quanto vídeo cabe no disco

VPS de 100 GB NVMe. Reservas: sistema e imagens Docker (~15 GB), PostgreSQL e logs (~5 GB), uploads em
andamento (~10 GB) e os 15% livres do alerta (15 GB). Sobram **~55 GB para o HLS** (produção e
homologação juntas).

Cada hora de aula guarda as duas variantes (720p e 1080p) mais o áudio de cada uma:

| Conteúdo | Taxa média (720p + 1080p + áudio) | GB por hora | Horas em 55 GB |
|---|---|---|---|
| Tela de código, slides (o típico do curso) | ~1,1 Mbps | ~0,5 | ~110 h |
| Câmera e tela misturadas | ~3,9 Mbps | ~1,8 | ~30 h |
| Pior caso (sempre no teto: 2,8 + 5 Mbps) | ~8,1 Mbps | ~3,6 | ~15 h |

A conta do capítulo de limites da VPS usa o `segundosProcessamentoPorMinuto` e o `tamanhoFinal` que o
ADM mostra para cada vídeo: medir com os vídeos reais e atualizar esta tabela.

## 6. Saída para um S3 externo

Quando o HLS passar de ~40 GB, ou se a banda da VPS virar gargalo, os vídeos vão para o Cloudflare R2
(sem taxa de saída) ou o Backblaze B2. Como a aplicação só fala S3 pelo AWS SDK:

1. Criar o bucket no R2, a chave da aplicação e o CORS para `aluno.celke.uno` e `adm.celke.uno`.
2. Copiar o conteúdo: `rclone sync garage:midia r2:midia` (com as duas origens no `rclone.conf`).
3. No `.env` da produção, trocar `S3_ENDPOINT` e `S3_ENDPOINT_PUBLICO` (endpoint do R2 ou domínio
   próprio `media.celke.uno` apontado para o bucket), `S3_BUCKET`, `S3_ACCESS_KEY` e `S3_SECRET_KEY`.
4. Rodar o `rclone sync` de novo (o que entrou durante a cópia) e reiniciar a API e o worker.
5. Depois de uma semana sem problema, tirar o Garage da produção e o host de mídia do Caddy.

## 7. Segurança

### OWASP ASVS nível 1 — revisão da API

| Capítulo | Situação |
|---|---|
| V2 Autenticação | Senha mínima de 10 (aluno) / 12 (admin), máximo 128, Argon2; recusa as senhas mais comuns e vazadas (lista de ~117 mil), as que contêm o nome ou o e-mail e as sequências e repetições; medidor de força nas telas; **segundo fator (TOTP) obrigatório para administradores**, com códigos de recuperação de uso único; bloqueio após falhas por e-mail e por e-mail + IP (o código errado do segundo fator conta como falha); limite por IP nas rotas de conta; recuperação com token de uso único e resposta igual para e-mail existente ou não |
| V3 Sessão | Access token de 10 minutos; refresh opaco em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, sem `Domain`; rotação com detecção de reuso (revoga a família); logout e "sair de todos"; troca de senha encerra todas as sessões; o ADM bloqueia a conta ou encerra as sessões e o access token já emitido deixa de valer na hora (conferido a cada requisição, 30 s em memória); token de link de e-mail sai da barra de endereço depois de lido |
| V4 Controle de acesso | Tudo negado por padrão; rotas públicas declaradas com método e conferidas por um teste que varre todas as rotas; papel no filtro e no `@PreAuthorize`; acesso ao conteúdo só pela `AcessoAulaPolicy`; recursos de outro usuário respondem 404; chaves de upload com o id do dono (IDOR); limite por usuário nas gravações (120 por minuto) e no checkout (10 por hora); a compra só abre o pedido com a confirmação do aluno; checkout só em HTTPS nos domínios da Hotmart |
| V5 Entrada e saída | Bean Validation em todo corpo; Markdown com HTML escapado e sanitizado (lista fechada); conteúdo dos arquivos conferido pela assinatura real; imagens regravadas sem metadados; ffmpeg por lista de argumentos |
| V7 Erros e logs | ProblemDetail sem stack trace nem causa; id de correlação; auditoria de login; log de acesso aos vídeos com retenção |
| V8 Dados | Bucket privado, só URLs pré-assinadas curtas; certificado validado sem e-mail nem ids; segredos só nos `.env` e nos secrets |
| V9 Comunicação | HTTPS automático pelo Caddy, HSTS; CORS do bucket com as origens exatas |
| V10 Código malicioso / V14 Configuração | Trivy no pipeline (críticos com correção quebram o build); `npm audit` das dependências do navegador (alta ou crítica quebra o build); Dependabot semanal para Maven, npm, GitHub Actions e imagens Docker; versões fixas de imagens e dependências; usuário não-root nos containers; Swagger e Actuator fora da produção; a API e o worker entram no banco com um usuário que só lê e grava dados (o dono do schema fica para as migrations) |

### Segundo fator dos administradores

- Todo usuário com o papel `ADMIN` entra em duas etapas: a senha certa devolve um desafio (válido por
  5 minutos, sem sessão nem cookie) e a sessão só abre com o código de 6 dígitos do aplicativo
  autenticador (Google Authenticator, Microsoft Authenticator, 1Password...).
- No primeiro acesso, a tela mostra o QR code; o primeiro código certo ativa o segundo fator e mostra
  **uma vez** os 10 códigos de recuperação (cada um vale uma entrada sem o celular).
- O segredo fica cifrado no banco (AES-256-GCM, chave `TOTP_CHAVE` do `.env`); o mesmo código não vale
  duas vezes; cada etapa fica na auditoria de login (`SEGUNDO_FATOR_PENDENTE`, `SEGUNDO_FATOR_INVALIDO`,
  `SEGUNDO_FATOR_CADASTRADO`, `CODIGO_RECUPERACAO`).
- Perdeu o celular e os códigos? Na VPS, como `deploy`:

  ```bash
  cd /opt/plataforma-cursos/producao
  docker compose -p plataforma-cursos-producao -f compose.prod.yml run --rm api redefinir-2fa admin@celke.com.br
  ```

  O comando apaga o segundo fator e encerra as sessões; o próximo login pede o cadastro de novo.
- Trocar a `TOTP_CHAVE` invalida todos os segredos: rode o `redefinir-2fa` para cada administrador.

### Usuário do banco da aplicação

- O serviço `migracao` (mesma imagem da API, comando `migrar`) roda a cada deploy com o dono do schema
  (`POSTGRES_USER`), aplica as migrations e, no `afterMigrate.sql`, cria (na primeira vez) e libera o
  usuário da aplicação (`POSTGRES_APP_USER`): `SELECT`, `INSERT`, `UPDATE` e `DELETE` nas tabelas, uso
  das sequências, sem criar, alterar ou apagar tabelas e sem acesso ao histórico do Flyway.
- A API e o worker sobem só depois dele, com `POSTGRES_APP_USER`/`POSTGRES_APP_PASSWORD`. Uma injeção
  de SQL ou uma conta roubada da aplicação não derruba tabelas nem apaga a trilha de auditoria (o
  gatilho continua barrando `UPDATE` e `DELETE` nela).
- O `deploy.sh` acrescenta ao `.env` as variáveis novas (`POSTGRES_APP_USER`, `POSTGRES_APP_PASSWORD`,
  `TOTP_CHAVE`) que ainda não existirem, com valores aleatórios.

### Varreduras

- **Trivy** (a cada PR, imagens da API e do worker): a primeira varredura apontou o Tomcat 11.0.24
  (3 CVEs críticos) e o Jackson 3.1.5 / 2.21.5; o `pom.xml` fixa as versões corrigidas até o Spring Boot
  trazê-las.
- **OWASP ZAP baseline** na stack local de e2e (rodar também na homologação a cada versão):
  0 falhas, 65 regras sem achado; o aviso de `Cross-Origin-Resource-Policy` foi resolvido no Caddy
  (`same-site`). "Non-Storable Content" é esperado: a API responde sem cache.

  ```bash
  docker run --rm ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t https://homolog-aluno.celke.uno/api/cursos -I
  ```
`````

`docs/plano-implementacao.md`:

`````markdown
# Plano de implementação — Plataforma de Cursos

Sistema do aluno (cursos, aulas em vídeo, fórum, certificado) com backend Java, frontend React
usando `@celke/kole-react`, tudo em Docker numa VPS da Hostinger. Este plano também é o roteiro do tutorial:
cada fase vira uma branch e um ou mais capítulos.

**Prompts:** cada etapa implementada tem um prompt em [`docs/prompts/`](prompts/README.md). Executados
na ordem do índice, eles recriam o projeto do zero.

**Ordem de trabalho:** primeiro todo o backend (com infraestrutura, CI e deploy), depois o frontend,
com a `@celke/kole-react` instalada do GitHub (commit fixo no `package.json`).

---

## 1. Decisões

| Tema | Decisão |
|---|---|
| Java | **Java 25 LTS** |
| Backend | **Spring Boot 4.1.x** (última correção disponível ao criar o projeto); organizado por funcionalidade; profiles `api` e `worker` no mesmo código. Atualizar para 4.2 numa branch `fix/` quando sair |
| Banco | **PostgreSQL 18** + Flyway |
| Fila | Tabela no PostgreSQL com `SELECT ... FOR UPDATE SKIP LOCKED` (sem Redis/RabbitMQ) |
| S3 na VPS | **Garage** (`dxflrs/garage:v2.4.1`), modo `--single-node`. Escolhido por ser leve, ter configuração curta e suportar tudo que o projeto usa (veja abaixo). O MinIO comunitário parou de publicar imagens Docker e entrou em modo de manutenção |
| Acesso ao S3 | Somente AWS SDK v2 com path-style; trocar de servidor = trocar endpoint no `.env` |
| Vídeo | Upload direto por URL pré-assinada → ffmpeg → HLS 720p + 1080p → playlist reescrita pela API com segmentos assinados |
| E-mail | **SMTP da Iagente** |
| Frontend | Pacote único em `web/` com `aluno`, `adm` e o gerador do `site`; `@celke/kole-react` instalada **do GitHub** com commit fixo |
| Hospedagem | Tudo em Docker na VPS (2 vCPU, 8 GB, 100 GB NVMe); Caddy com HTTPS automático serve os fronts e faz proxy de `/api` |
| Ambientes | Produção + homologação leve na mesma VPS |
| Branches | `main` (produção), `homolog` (homologação), `feature/nome` e `fix/nome` temporárias, `hotfix/nome` a partir da `main` |
| CI | GitHub Actions: testes + e2e em todo PR; build e e2e refeitos na `main` |
| Deploy | SSH com chave presa a `deploy.sh`, imagem com tag = SHA, health check e rollback |

### Garage: o que o projeto usa e o que ele não tem

- ✅ URLs pré-assinadas, upload multipart, CORS por bucket, lifecycle com `Expiration` e `AbortIncompleteMultipartUpload` (limpa uploads abandonados)
- ❌ Versionamento, ACLs e bucket policies. Não fazem falta: o bucket é privado e o acesso é por chave com permissão por bucket.
- Um bucket privado `midia`, com os prefixos `uploads/` (originais enviados) e `videos/` (HLS e thumbnails). O próprio Garage cria o bucket e a chave da aplicação na primeira subida (`--default-bucket`); a expiração de uploads abandonados é feita por prefixo.

### Domínios (somente subdomínios de `celke.uno`)

O domínio principal `celke.uno` já é usado por outras aplicações e não será tocado.

| Produção | Homologação | Serve |
|---|---|---|
| `cursos.celke.uno` | `homolog-cursos.celke.uno` | site |
| `aluno.celke.uno` | `homolog-aluno.celke.uno` | SPA do aluno + `/api` |
| `adm.celke.uno` | `homolog-adm.celke.uno` | SPA do ADM + `/api` (restrito por IP) |
| `media.celke.uno` | `homolog-media.celke.uno` | Garage (subdomínio próprio: a assinatura depende do host) |

> Nomes propostos — confirmar que nenhum desses subdomínios já está em uso antes da etapa 1.8 (VPS).

Cuidados por dividir o domínio com outras aplicações:

- Cookies **sem** atributo `Domain` (cookie só do host). Com `Domain=celke.uno`, todas as outras aplicações dos subdomínios receberiam o refresh token.
- A CSP e o CORS do Garage listam os subdomínios exatos, nunca `*.celke.uno`.
- HSTS sem `includeSubDomains`, a não ser que todas as aplicações de `celke.uno` já usem HTTPS.

### SMTP Iagente

- Host, porta e credenciais vêm do painel da Iagente (porta 587 com STARTTLS é o padrão esperado; confirmar no painel).
- Remetente: endereço de `celke.uno` com SPF e DKIM configurados na Iagente.
- Credenciais só no `.env` de cada ambiente. Homologação com remetente identificado ("[HOMOLOG]") ou envio restrito a e-mails de teste.
- No dev e no e2e: Mailpit no compose (captura os e-mails sem enviar).

---

## 2. Fluxo de branches e pipeline

```
feature/x ─┐
fix/y ─────┼─PR─▶ homolog ──PR──▶ main
           │      (deploy          (build refeito,
           │       homologação)     deploy produção)
hotfix/z ──────────────────────PR─▶ main ──merge──▶ homolog
```

| Evento | Testes | e2e | Deploy |
|---|---|---|---|
| PR → `homolog` ou → `main` | ✅ | ✅ | — |
| Push em `homolog` | ✅ | ✅ | homologação |
| Push em `main` | ✅ | ✅ | produção (aprovação opcional no environment `production`) |

Regras:

- `main` recebe código **somente** da `homolog` (ou de `hotfix/*`). Alteração reprovada na homologação é desfeita com `git revert` na `homolog` antes de promover.
- Depois de um hotfix, merge da `main` de volta na `homolog`.
- Branch de trabalho apagada automaticamente após o merge (Settings → *Automatically delete head branches*).
- Branches curtas: merge em poucos dias. A `feature/base-projeto` é a única exceção.

**e2e por etapa:**

- **Fases de backend:** e2e de API — o compose completo sobe no Actions e os testes chamam a API por HTTP, passando pelo Caddy (REST Assured num módulo separado, ou Playwright em modo API).
- **Fases de frontend:** acrescenta Playwright no navegador.

---

## 3. Estrutura do repositório

```
plataforma-cursos/
├── CLAUDE.md                    regras gerais do repositório
├── .claude/                     orientações para agentes de IA: rules, skills (commits, especificação),
│                                agentes e comandos do pipeline SDD (/sdd → /gerar-tasks → /implementar-tasks)
├── .sdd/                        specs e tasks geradas pelo pipeline SDD (quando usado)
├── backend/
│   ├── CLAUDE.md                regras do projeto Java (código e testes)
│   ├── .claude/skills/          criar-teste-unitario, criar-teste-integracao, criar-teste-e2e
│   │                            (cada uma com SKILL.md = processo e padrao.md = padrão)
│   ├── Dockerfile               targets: api | worker (com ffmpeg)
│   ├── pom.xml                  Surefire (*Test), Failsafe (*IT), JaCoCo, Spotless, perfil e2e
│   └── src/
│       ├── main/java/br/com/celke/cursos/
│       │   ├── shared/          config, security, erros, storage S3, fila, e-mail
│       │   ├── auth/
│       │   ├── usuario/
│       │   ├── curso/           curso, turma, módulo, aula
│       │   ├── matricula/       matrícula + AcessoAulaPolicy
│       │   ├── video/           upload, transcodificação, playlist
│       │   ├── progresso/
│       │   ├── arquivo/
│       │   ├── forum/
│       │   ├── certificado/
│       │   └── perfil/
│       │   (cada módulo: api/ · application/ · domain/ · infra/)
│       ├── test/java/           unitários (*Test) e integração (*IT) + support/
│       └── e2e/java/            e2e caixa-preta (*E2E: REST Assured + Awaitility)
├── web/                         (a partir da Fase 11) um pacote só, um build por app
│   ├── package.json
│   ├── compartilhado/           cliente HTTP, sessão, rotas protegidas, validação
│   ├── aluno/
│   ├── adm/
│   ├── site/                    site público (React Router com renderização no servidor)
│   ├── e2e/                     Playwright contra a stack de e2e
│   ├── Dockerfile               build dos fronts + Caddy interno (aluno :8081, ADM :8082)
│   └── Caddyfile
├── infra/
│   ├── compose.yml              ambiente local (inclui Mailpit)
│   ├── compose.prod.yml         um ambiente dos servidores: API, worker, web, PostgreSQL, Garage
│   ├── compose.homolog.yml      sobreposição: limites menores e Swagger
│   ├── compose.e2e.yml          sobreposição: Caddy com *.localhost, Mailpit, seed e2e
│   ├── e2e.env                  variáveis da stack de e2e (só valores de teste)
│   ├── garage/garage.toml
│   ├── caddy/
│   │   ├── compose.yml          Caddy único da VPS, ligado às redes dos dois ambientes
│   │   ├── Caddyfile            trechos comuns (headers, api, mídia, restrição por IP)
│   │   ├── sites/               producao.caddy, homolog.caddy
│   │   ├── e2e.caddy            sites da stack de e2e
│   │   └── .env.example
│   └── scripts/                 gerar-env.sh, gerar-env-servidor.sh, preparar-vps.sh, deploy.sh, backup...
├── docs/
│   ├── plano-implementacao.md
│   ├── prompts/                 um prompt por fase (especificação e verificação)
│   └── prompts-iniciais/        sequência completa para criar o projeto do zero
└── .github/workflows/
    └── pipeline.yml
```

---

## 4. Fases

Cada fase = uma branch = um PR para `homolog`. As regras gerais ficam no `CLAUDE.md` da raiz; as do
backend, no `backend/CLAUDE.md`, com o padrão de cada nível de teste nas skills
`/criar-teste-unitario`, `/criar-teste-integracao` e `/criar-teste-e2e` (`backend/.claude/skills/`).
Definição de pronto de **todo** PR:

- [ ] `./mvnw verify` do backend passando (unitários + integração + cobertura + Spotless); `npm test` a partir da Fase 11
- [ ] e2e passando no Actions
- [ ] Rotas novas com matriz de papéis (anônimo / ALUNO / ADMIN) e, se usam a `AcessoAulaPolicy`, matriz de acesso ao conteúdo
- [ ] Endpoints novos documentados no OpenAPI
- [ ] Migrations compatíveis com a versão anterior (expandir → depois contrair)
- [ ] Nenhum segredo no código ou no compose
- [ ] Funcionando na homologação

---

### Fase 0 — Preparação (sem branch)

- [x] Criar repositório `celkecursos/plataforma-cursos` no GitHub
- [x] Primeiro commit na `main` (README, `.gitignore`, `docs/`)
- [x] Criar `homolog` a partir da `main`
- [x] Orientações para agentes de IA em `.claude/`: rules por caminho (segurança, fronts, testes, prompts),
  commits em Conventional Commits sem coautoria de IA (`/gc`, `/pc`), `/nova-branch`, `/abrir-pr`,
  `/revisar-dod` e o pipeline SDD com portões humanos
- [ ] Regras de proteção em `main` e `homolog`: só PR, checks obrigatórios (ativar quando o pipeline existir)
- [ ] Environments `homolog` e `production` com seus secrets
- [ ] Confirmar os subdomínios livres em `celke.uno`
- [ ] Obter host, porta e credenciais SMTP no painel da Iagente; configurar SPF/DKIM

---

## Bloco A — Backend

### Fase 1 — `feature/base-projeto`

Objetivo: API rodando local e na VPS (homologação e produção), com auth completa, CI, e2e de API e
deploy automático. Depois desta fase, toda funcionalidade nova segue o fluxo normal.

**1.1 Esqueleto do backend e padrão de testes** — capítulo 3

- [x] `CLAUDE.md` da raiz (regras gerais) e `backend/CLAUDE.md` (código e testes do projeto Java)
- [x] Skills `criar-teste-unitario`, `criar-teste-integracao` e `criar-teste-e2e` em `backend/.claude/skills/`, cada uma com o padrão do nível (`padrao.md`)
- [x] Projeto gerado no Spring Initializr: Spring Boot 4.1.1, Java 25, `br.com.celke.cursos`; dependências: webmvc, security, data-jpa, validation, flyway, actuator, postgresql, springdoc, testcontainers. As demais (AWS SDK, mail, Thymeleaf) entram na etapa que as usa
- [x] `application.yaml` com credenciais do banco sem valor padrão, `open-in-view` desligado, `ddl-auto: validate`, threads virtuais, shutdown gracioso, cabeçalhos do proxy, sem stack trace nas respostas; `application-worker.yaml` sem servidor HTTP, sem Flyway (as migrations são da API) e com `spring.main.keep-alive`
- [x] Sem usuário embutido do Spring Security (`UserDetailsServiceAutoConfiguration` excluída)
- [x] `SecurityConfig` (só na aplicação web, com `@ConditionalOnWebApplication`): stateless, sem CSRF/sessão/formulário/HTTP Basic, 401 para não autenticado; públicos só `GET` de health/probes e da documentação
- [x] Actuator: só `health`, com probes de liveness/readiness e sem detalhes
- [x] Swagger ligado por `DOCS_ENABLED=true` (dev e homologação), desligado por padrão
- [x] Surefire para `*Test` (com `-Dskip.unit=true` para pular), Failsafe para `*IT`, JaCoCo somando os dois com mínimo de 80% de linhas, Spotless (palantir-java-format) verificado no `verify`
- [x] `support/Containers` (PostgreSQL 18 como bean com `@ServiceConnection`) e `support/IntegrationTest` (`MockMvcTester`, `stamp()`)
- [x] `SegurancaPadraoIT`: health e probes públicos com `UP` e sem detalhes; demais rotas e métodos exigem autenticação; documentação desligada
- [x] Dockerfile multi-stage: build com JDK 25, jar em camadas, execução com JRE 25, `curl` para healthcheck e usuário sem root; targets `api` e `worker` (com ffmpeg)

**1.2 Ambiente local** — capítulo 4

- [x] `infra/compose.yml` (projeto `plataforma-cursos`): PostgreSQL 18.6, Garage v2.4.1, Mailpit v1.31.6, API e worker, com versões fixas
- [x] Variáveis obrigatórias com `${VARIAVEL:?mensagem}`; conexão do banco num bloco compartilhado (`x-banco`) pela API e pelo worker
- [x] `infra/.env.example` e `infra/scripts/gerar-env.sh`, que cria o `infra/.env` com segredos aleatórios (permissão 600) e não sobrescreve um existente
- [x] Garage em `--single-node --default-bucket`: `garage.toml` sem segredos (vêm de `GARAGE_RPC_SECRET` e `GARAGE_ADMIN_TOKEN`), volume de metadados e dados; bucket `midia` e chave da aplicação criados na primeira subida
- [x] PostgreSQL com o volume em `/var/lib/postgresql` (layout da versão 18)
- [x] Healthchecks (`pg_isready`, `garage status`, `mailpit readyz`, readiness da API com `curl`) e `depends_on: condition: service_healthy`
- [x] Worker sobe depois da API, que aplica as migrations
- [x] Portas ligadas apenas em `127.0.0.1` e configuráveis no `.env` (`API_PORTA`, `POSTGRES_PORTA`, `S3_PORTA`, `MAILPIT_PORTA`)
- [x] Swagger ligado no dev (`DOCS_ENABLED=true`)

**1.3 Padrões compartilhados** — capítulo 5

- [x] `ProblemDetail` (RFC 9457) com handler global: validação com a lista `erros` (`campo`, `mensagem`) em português, regra de negócio (`RegraNegocioException`, `NaoEncontradoException`, `ConflitoException`), erros do Spring MVC traduzidos em `messages.properties`, erro inesperado só com o id da requisição; `instance` e `requestId` em todo erro
- [x] 401/403 da segurança também em `ProblemDetail` (`RespostasSeguranca`)
- [x] Id de correlação `X-Request-Id` (aceito só se seguro, senão gerado) no MDC e na resposta; logs em JSON (ECS) no compose
- [x] `Pagina<T>` e paginação padrão (20, máximo 100); `Clock` único em UTC
- [x] `StorageService` (AWS SDK v2, path-style): presign GET/PUT, multipart, consulta, exclusão; presigner com o endereço público
- [x] Configuração do bucket aplicada pela API ao subir, de forma idempotente: CORS para os hosts do front (expondo `ETag`) e lifecycle que expira `uploads/` e uploads em partes incompletos
- [x] Fila: tabela `jobs` + `FilaService` (enfileirar, reservar com `SKIP LOCKED`, lease, concluir, falhar com backoff, falha definitiva), `ProcessadorFila` por tipo e consumo agendado só no worker
- [x] `EmailService` (valida destinatário e template) e envio no worker com Thymeleaf e SMTP; health check do e-mail desligado
- [x] `@ConfigurationProperties` validadas (`app.armazenamento`, `app.email`, `app.fila`)
- [x] `support/Containers` com Garage e Mailpit; `support/Mailpit`; controller de erros só de teste
- [x] Testes: handler de erros, id de correlação, `StorageService` contra o Garage real (inclusive preflight do CORS), fila (backoff, tentativas esgotadas, lease vencido, concorrência sem duplicidade), e-mail entregue no Mailpit com HTML escapado

**1.4 Usuários e autenticação** — capítulos 6 e 7

- [x] Migrations `usuarios` (e-mail único e em minúsculas), `usuario_papeis` e `refresh_tokens`
- [x] Hash de senha Argon2; BCrypt `$2y$` do Laravel aceito e regravado em Argon2 no login; política de senha por tamanho (admin: 12 a 128)
- [x] Comando `docker compose run --rm api criar-admin <email> "<nome>"`: sem servidor HTTP, senha digitada duas vezes no terminal
- [x] Access token JWT (Nimbus, HS256, 10 min, `sub` e `papeis`) com o Resource Server do Spring Security: só HS256, emissor e validade conferidos, papéis em `ROLE_*`; `JWT_SEGREDO` validado ao subir
- [x] Refresh opaco com hash SHA-256, rotação, família revogada na reutilização (gravada mesmo com a sessão recusada) e `SELECT ... FOR UPDATE`
- [x] Cookie `refresh_token`: `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, 14 dias, **sem `Domain`**
- [x] Rotas `login`, `refresh`, `logout` (públicas), `sair-de-todos` e `me` (privadas); mesma resposta e mesmo tempo para e-mail inexistente e senha errada
- [x] Seed do profile `test` (admin, aluno); `support/UsuariosSeed`; `login()`, `bearer()` e `criarUsuario()` no `IntegrationTest`
- [x] Testes: token adulterado, expirado, de outra chave, com outro algoritmo, `alg: none` e de outro emissor recusados; rotação, reutilização, logout e sair de todos; matriz de papéis das rotas de autenticação

**1.5 Autorização e proteções** — capítulos 8 e 9

- [x] `SecurityFilterChain` nega tudo por padrão; rotas públicas declaradas com método HTTP; papéis do token + `@PreAuthorize` (matriz de papéis testada)
- [x] Limite por IP com Bucket4j nas rotas de autenticação (`LimitePorIpFilter`): conta toda requisição, roda antes da segurança, 429 em ProblemDetail com `Retry-After`
- [x] Bloqueio temporário por falhas seguidas: 5 por e-mail + IP e 20 por e-mail (qualquer IP), 15 min, contadores zerados por login correto ou após 1 h sem falhas; verificado antes de calcular o hash
- [x] Refresh e logout: verificação de `Origin` e `Sec-Fetch-Site` (CSRF), porque outras aplicações em `*.celke.uno` contam como o mesmo site; origens em `AUTH_ORIGENS`
- [x] Auditoria de login (`auditoria_login`): resultado, IP e user-agent de toda tentativa, em transação própria
- [x] `server.forward-headers-strategy: native`: `X-Forwarded-For` aceito só de proxy interno e lido da direita para a esquerda
- [x] Erros que nascem em filtros escritos por `ProblemDetailJson` (mesmo formato do handler global)

**1.6 Cadastro e recuperação de senha** — capítulo 10

- [x] Migration: `usuarios.email_confirmado_em` (contas existentes confirmadas), `tokens_uso_unico` e novo resultado `EMAIL_NAO_CONFIRMADO` na auditoria
- [x] Cadastro de aluno (`POST /api/auth/cadastro`) com conta pendente e link de confirmação (48 h); resposta igual para e-mail novo e já cadastrado — conta ativa recebe um aviso (no máximo um por hora), cadastro pendente recebe o link de novo
- [x] Confirmação (`confirmar-email`) e reenvio (`reenviar-confirmacao`); login com e-mail pendente recebe 403 próprio só quando a senha está certa
- [x] Recuperação (`recuperar-senha`, link de 1 h, resposta igual exista ou não a conta) e redefinição (`redefinir-senha`): troca a senha, confirma o e-mail e encerra todas as sessões
- [x] Tokens de uso único com hash SHA-256 (`TokenSeguro`, compartilhado com o refresh): um novo invalida os anteriores do mesmo tipo; no máximo 3 e-mails por hora por conta e tipo
- [x] Senha de aluno com 10 a 128 caracteres; contas do comando e dos seeds nascem confirmadas
- [x] Templates de e-mail (`confirmacao-email`, `recuperacao-senha`, `cadastro-existente`) com links para `FRONT_ALUNO_URL`; envio pelo worker (Mailpit no ambiente local)
- [x] Rotas da conta públicas (só POST) e no `LimitePorIpFilter`
- [x] Healthcheck do worker por arquivo de batimento (`BatimentoWorker`), separado do consumo da fila: o `up --wait` e o deploy passam a esperar o worker
- [x] Testes: jornadas de cadastro e recuperação com os e-mails lidos no Mailpit, link de uso único, limite de e-mails, mesma resposta para e-mail inexistente

**1.7 e2e de API e pipeline** — capítulo 11

- [x] `infra/compose.prod.yml`: stack dos servidores com Caddy, imagens do GHCR pela `TAG`, `restart: unless-stopped`, healthchecks e nenhuma porta publicada além das do Caddy
- [x] `infra/caddy/Caddyfile`: hosts pelo ambiente (`HOST_ALUNO`, `HOST_ADM`, `HOST_MEDIA`), só `/api/*` e `/actuator/health` chegam à API, 404 para o resto, headers de segurança, `-Server`, `lb_try_duration`, `X-Forwarded-For` substituído
- [x] `infra/compose.e2e.yml` + `infra/e2e.env` (valores só de teste, no git): HTTP em `*.localhost` na porta 8088, Mailpit, imagens locais, profile `e2e`, projeto Compose separado
- [x] Seed do profile `e2e` (`SeedE2e`: admin e aluno, senha de `SEED_E2E_SENHA`)
- [x] Perfil Maven `e2e` (`src/e2e/java`, REST Assured, Awaitility) com `support/` conforme o padrão da skill `criar-teste-e2e`; Spotless também nas fontes e2e
- [x] Jornadas `CadastroE2E` (cadastro → e-mail entregue pelo worker → confirmação → login), `SessaoE2E` (login → refresh → logout pelo Caddy, cookie sem `Domain`, origem recusada) e `ProxyE2E` (headers, rotas fora da API bloqueadas, `X-Forwarded-For` forjado sem efeito)
- [x] `.github/workflows/pipeline.yml`: testes → imagens (cache `type=gha`) → e2e → relatórios e `docker compose logs` em caso de falha; roda em PR e push para `homolog` e `main`
- [ ] Regras de proteção de `homolog` e `main` com os checks `Testes (unitários e integração)` e `e2e (stack completa)` obrigatórios (configuração no GitHub)

**1.8 VPS** — capítulos 12 e 13 · roteiro em [vps.md](vps.md)

- [x] `infra/scripts/preparar-vps.sh` (Ubuntu 24.04): Docker oficial com rotação de logs, administrador com sudo, `deploy` sem sudo no grupo docker, SSH só por chave e sem root (`01-plataforma.conf`, antes do `50-cloud-init.conf`), `ufw` (22/80/443), fail2ban, atualizações automáticas, swap de 2 GB, `/opt/plataforma-cursos/{caddy,producao,homolog}`
- [x] `compose.prod.yml` por ambiente (`AMBIENTE`): rede `plataforma-cursos-<ambiente>`, apelidos `<ambiente>-api` e `<ambiente>-garage`, nenhuma porta publicada, limites de recursos e `-Xmx` ajustado
- [x] `compose.homolog.yml`: limites menores, worker ligado com meia CPU (envia os e-mails da homologação), Swagger ligado
- [x] Caddy único (`infra/caddy/compose.yml`) nas portas 80/443, ligado às duas redes; `Caddyfile` com trechos comuns e `sites/producao.caddy` e `sites/homolog.caddy`
  - homologação restrita por IP (`HOMOLOG_IPS`) e com `X-Robots-Tag: noindex` — senha do Caddy não serve, porque ocuparia o cabeçalho `Authorization` do JWT
  - `adm.` de produção restrito por IP (`ADM_IPS`)
  - HSTS sem `includeSubDomains`, `X-Content-Type-Options`, `Referrer-Policy`, sem `Server`, `lb_try_duration`; `X-Forwarded-For` substituído pelo IP real
- [x] e2e usando o mesmo `Caddyfile` com `caddy/e2e.caddy`
- [x] `infra/scripts/gerar-env-servidor.sh`: `.env` de cada ambiente com segredos aleatórios e diferentes, hosts de `celke.uno`, SMTP para preencher
- [x] Roteiro `docs/vps.md`: painel, DNS, chaves, preparação, cópia dos arquivos, `.env`, GHCR, primeira subida, verificação
- [ ] Executar o roteiro na VPS (painel, DNS dos seis subdomínios, preparação, `.env`, login no GHCR)
- [ ] Primeira subida dos dois ambientes e do Caddy, com as imagens publicadas pelo deploy automático (1.9)

Limites de recursos:

| Serviço | Produção | Homologação |
|---|---|---|
| API | 1 GB, `-Xmx512m` | 768 MB, `-Xmx384m`, `cpus: 0.5` |
| Worker + ffmpeg | 2 GB, `-Xmx768m`, `cpus: 1.5` | 768 MB, `-Xmx256m`, `cpus: 0.5` (sempre ligado: envia os e-mails) |
| PostgreSQL | 1 GB | 512 MB |
| Garage | 768 MB | 512 MB |
| Caddy (compartilhado) | 128 MB | — |

**1.9 Deploy automático** — capítulo 14

- [x] Publicação no GHCR pelo job de e2e, depois das jornadas e só em push: as imagens testadas recebem a tag do SHA (sem rebuild); label `org.opencontainers.image.source` liga o pacote ao repositório
- [x] `infra/scripts/deploy.sh` (na VPS em `/usr/local/bin/plataforma-cursos-deploy`, dono root): valida `<producao|homolog> <sha>` (também de `SSH_ORIGINAL_COMMAND`), extrai só os arquivos de compose esperados, backup do banco, troca a tag, `up --pull missing --wait`, rollback automático para a versão anterior (arquivos e tag) se algum container não ficar saudável; `flock`, `trap '' HUP`, guarda 5 versões e 10 backups, `deploy.log`
- [x] Chave do deploy presa ao script no `authorized_keys` (`command=...,no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty`), instalada pelo `preparar-vps.sh`
- [x] Workflow reutilizável `deploy.yml` (environment `homolog` ou `production`, um deploy por ambiente, nunca cancelado); no pipeline: push em `homolog` → homologação; push em `main` → produção, só com a variável `DEPLOY_ATIVO=true` (sem ela, os deploys são pulados); só PRs cancelam execuções antigas
- [x] Testado localmente: deploy de uma versão boa, deploy de uma versão quebrada com rollback automático, backup restaurável, validação de argumentos (inclusive injeção pelo SSH) e deploy simultâneo barrado; chave presa ao script testada com `sshd` real (sem shell, terminal ou túnel)
- [x] Roteiro `docs/vps.md`: secrets do repositório, primeira subida pelo pipeline, Caddy e administradores, operação (histórico, voltar versão, atualizar Caddy e script)
- [ ] Secrets `VPS_HOST`, `VPS_SSH_KEY` e `VPS_KNOWN_HOSTS` e variável `DEPLOY_ATIVO=true` no repositório (conta dona) e, se o plano permitir, aprovação obrigatória no environment `production`
- [ ] Primeiro deploy nos dois ambientes pelo pipeline; Caddy no ar; administrador criado em cada um

**Pronto quando:** cadastro, login e logout funcionando pela API em `homolog-aluno.celke.uno/api` e
`aluno.celke.uno/api`, e-mail chegando pela Iagente, pipeline completo verde e um rollback testado de propósito.

---

### Fase 2 — `feature/cursos`

Objetivo: API de gestão de cursos, turmas, módulos e aulas. Capítulos 15 e 16.

- [x] Migration `V6`: `cursos` (slug único com `CHECK` do formato), `turmas`, `modulos` e `aulas`; status como texto com `CHECK` (curso `RASCUNHO`/`PUBLICADO`/`ARQUIVADO`, turma `PREPARACAO`/`ATIVA`/`ENCERRADA`, módulo e aula `ATIVO`/`INATIVO`); aula gratuita; `ON DELETE RESTRICT` em toda a hierarquia
- [x] Ordem de módulos e aulas única por pai com restrição `DEFERRABLE INITIALLY DEFERRED` (a reordenação troca todas as posições numa transação); item novo entra no fim; exclusão refaz a sequência; o pai fica travado (`PESSIMISTIC_WRITE`) enquanto a ordem muda
- [x] CRUD em `/api/adm/**` com Bean Validation: cursos (lista paginada com filtro por status, slug gerado do título quando não informado, 409 para slug repetido), turmas, módulos e aulas, e `PATCH .../status`
- [x] Exclusão só de item vazio (curso sem turmas, turma sem módulos, módulo sem aulas); o resto é arquivado, encerrado ou inativado pelo status, para não perder matrículas e histórico
- [x] Reordenação `PUT /api/adm/turmas/{id}/modulos/ordem` e `PUT /api/adm/modulos/{id}/aulas/ordem` com a lista completa de ids (400 se faltar, sobrar ou repetir)
- [x] `/api/adm/**` exige ADMIN no `SecurityConfig` (antes de ler o corpo) e de novo com `@PreAuthorize` nos controllers
- [x] Capa do curso: URL pré-assinada de PUT em `uploads/capas/<curso>/<uuid>` (JPEG ou PNG, até 5 MB); a confirmação aceita só a chave gerada para o mesmo curso, confere o tamanho real, recusa lado acima de 8000 px antes de decodificar, reduz para até 1280 × 720 e regrava em JPEG sem metadados em `capas/<curso>/<uuid>.jpg`; o original e a capa anterior são apagados. `ProcessadorImagem` em `shared/imagem` (reutilizado pela foto de perfil)
- [x] Catálogo público (`GET`, declarado no `SecurityConfig`): `/api/cursos` (publicados, paginado, ordem alfabética), `/api/cursos/{slug}` (módulos e aulas ativos da turma ativa mais recente, sem dados internos) e `/api/cursos/{slug}/capa` (endereço estável que redireciona para uma URL assinada nova, com cache de 5 minutos); rascunho e arquivado respondem 404
- [x] Testes: unitários (slug, ordenação, DTOs, serviços, capa, processamento de imagem com EXIF e transparência), integração (matriz de papéis de todas as rotas, CRUD, ordem com restrição adiável e criação simultânea, catálogo, capa com o Garage real)
- [x] e2e `CatalogoE2E`: o ADM cria curso → turma → módulo → aulas, envia a capa pelo Caddy direto ao Garage e publica; o visitante sem login vê o curso, o conteúdo e a capa; o aluno recebe 403 na gestão

**Pronto quando:** um curso completo, com capa, é cadastrado pela API do ADM e aparece no catálogo
público com o conteúdo da turma ativa.

---

### Fase 3 — `feature/matricula-acesso`

Objetivo: matrícula e uma política única de acesso. Capítulos 17 e 18.

- [x] Migration `V7` `matriculas`: usuário e turma (FK `RESTRICT`), início, fim opcional (sem prazo), status `ATIVA`/`CANCELADA`, forma de pagamento (`MANUAL`, `CORTESIA`, `PIX`, `CARTAO`, `BOLETO`), transação, arquivamento; índice único parcial de uma matrícula ativa por aluno e turma e de transação por forma de pagamento (pronto para o webhook)
- [x] "Vencida" e "aguardando início" não são status gravados: `Matricula.situacao(agora)` calcula pelo `Clock` (fim inclusivo), sem job para marcar matrículas
- [x] Fronteira entre módulos: a matrícula guarda só os ids; o conteúdo vem do `ConteudoTurmaService` (exportado pelo módulo `curso`, também usado pelo catálogo) e os usuários do `UsuarioService`
- [x] Endpoints do ADM: listar as matrículas da turma, matricular por e-mail (início padrão agora; turma encerrada, usuário inexistente, matrícula ativa repetida e transação repetida recusados), alterar período, cancelar; turma com matrículas não pode ser excluída (409 pela FK)
- [x] `AcessoAulaPolicy` (classe pura com `Clock`): aula liberada com matrícula vigente na turma ou aula aberta (gratuita, turma ativa, curso publicado); turma inteira só com matrícula vigente. `AcessoAulaService.exigirAcesso` é a porta de entrada para vídeo, arquivo, fórum, progresso e certificado
- [x] Endpoints do aluno (`/api/aluno`, escopo do token): meus cursos por aba (`ATIVOS` com as futuras, `ARQUIVADOS`, `EXPIRADOS`; cancelada em nenhuma), turma com módulos e aulas ativos, aula, arquivar/desarquivar (matrícula de outro aluno responde 404)
- [x] Testes: situação e política em tabela (instante antes, exato e depois de cada limite), matriz de papéis, matriz de acesso ao conteúdo (vigente, sem, vencida, cancelada, futura, gratuita, curso em rascunho, conteúdo inativo, outra turma), IDOR
- [x] e2e `MatriculaE2E`: conta nova vê só a aula gratuita; matriculada vê a turma e a aula paga; com o período vencido, 403 e o curso nos expirados

**Pronto quando:** o ADM matricula um aluno e o acesso dele às aulas segue o período da matrícula,
decidido num ponto só.

---

### Fase 4 — `feature/upload-video`

Objetivo: o vídeo enviado vira HLS comprimido. Capítulos 19 a 21.

- [x] Migration `V8` `videos`: um vídeo por aula (FK `RESTRICT`: aula com vídeo responde 409 na exclusão), status `AGUARDANDO_ENVIO`/`PENDENTE`/`PROCESSANDO`/`PRONTO`/`ERRO`, chaves do original e do HLS, tamanho original e final, duração, resolução, variantes, tempo de processamento e erro. O HLS publicado só muda quando a conversão do envio novo termina
- [x] Envio em partes (`POST /api/adm/aulas/{id}/video/envio`): MP4, MOV, MKV ou WebM até 5 GB, validados antes de assinar; partes de 16 MiB (maiores acima de 10.000 partes), uma URL pré-assinada por parte em `uploads/videos/<aula>/<uuid>`; envio anterior ainda aberto é abortado
- [x] Conclusão (`.../envio/conclusao` com os ETags): junta as partes, confere o tamanho real igual ao informado e a assinatura do início do arquivo (`ftyp` ou EBML); recusado é apagado e o vídeo fica com erro; aceito vai para a fila na mesma transação
- [x] Worker (`TranscodificacaoManipulador`): `ffprobe` → ffmpeg via `ProcessBuilder` (lista de argumentos, `nice -n 10`), numa passada só
  - H.264 CRF 24 (`veryfast`), `maxrate` 2800k/5000k, AAC 128k, segmentos de 6 s, 720p + 1080p sem aumentar a resolução de origem (abaixo de 720p, uma variante na altura original)
  - thumbnail; envio para `videos/<aula>/<job>/`; apaga o original e o HLS anterior só depois de publicar
- [x] Lease renovado a cada minuto durante a conversão; worker que cai deixa o lease vencer e o job volta para a fila (refaz o prefixo do job do zero); `stop_grace_period: 30s`. Arquivo inválido é erro permanente; desistência da fila (`ManipuladorJob.desistir`) deixa o vídeo com erro
- [x] Endpoints do ADM: status com tamanho antes/depois, redução percentual, variantes, segundos de processamento por minuto de vídeo e thumbnail; reprocessar (só com erro e com o original ainda no bucket); remover o vídeo e os arquivos (fora do processamento)
- [ ] Medir tempo de processamento por minuto de vídeo na VPS e registrar no tutorial (o ADM já mostra a medida: `segundosProcessamentoPorMinuto`)
- [x] Testes: unitários (formatos e assinaturas, divisão em partes, variantes, argumentos do ffmpeg, saída do ffprobe, entidade, serviços, fila), integração com o Garage e o ffmpeg reais (transcodificação do vídeo de 3 s, troca, extensão falsa, vídeo corrompido e reprocessamento, matriz de papéis); o pipeline instala o ffmpeg
- [x] e2e `VideoE2E`: upload em partes pelo Caddy direto ao Garage → worker → status "pronto" com a thumbnail no host de mídia

**Pronto quando:** o ADM envia o vídeo de uma aula direto ao armazenamento e, sem passar o arquivo pela
API, ele vira HLS em 720p/1080p com o tamanho antes e depois registrado.

---

### Fase 5 — `feature/entrega-video`

Objetivo: vídeo protegido e progresso salvo. Capítulos 22 a 24.

- [x] `GET /api/aulas/{id}/video.m3u8` (master) e `GET /api/aulas/{id}/video/{variante}.m3u8`: passam pela `AcessoAulaService` (matrícula vigente ou aula gratuita); a master aponta para as variantes servidas pela API e cada variante traz os segmentos por URLs pré-assinadas válidas pela duração do vídeo + `app.video.margem-playlist` (2 h). Só nomes no formato gerado pelo worker são aceitos na reescrita; resposta com `Cache-Control: no-store`
- [ ] Opcional (cap. 23): HLS com AES-128 e chave por endpoint autenticado — não feito nesta fase
- [x] Progresso (migration `V9` `progresso_aulas`, um registro por aluno e aula): `GET` e `PATCH /api/aluno/aulas/{id}/progresso` (posição, vista, revisar; só os campos enviados mudam) e `GET /api/aluno/turmas/{id}/progresso` (percentual das aulas ativas vistas, arredondado para baixo; turma sem aulas = 0%). Sempre no escopo do token; a turma exige matrícula vigente (`AcessoAulaService.exigirAcessoTurma`)
- [x] Log de acesso (migration `V10` `acessos_video`): cada abertura da master registra usuário, aula, IP e navegador; o worker apaga todo dia o que passa de `app.video.retencao-log-acesso` (180 dias, o prazo do Marco Civil para registros de acesso a aplicações)
- [x] Testes: unitários (reescrita das playlists e playlists adulteradas, validade, percentual com as bordas, entidade e serviço do progresso, retenção do log), integração (matriz de acesso ao conteúdo na playlist e no progresso, segmento assinado baixando do Garage com `X-Amz-Expires` = duração + margem, isolamento entre alunos, retenção do log)
- [x] e2e `EntregaVideoE2E` (margem de 5 s na stack de e2e): playlist assinada → segmento baixa com 200 → após expirar, recusado (o Garage responde 400 "Date is too old"; a AWS, 403); progresso refletido na turma

**Pronto quando:** o aluno matriculado assiste ao vídeo por uma playlist assinada, sem acesso direto ao
bucket, e retoma de onde parou com o progresso refletido na turma.

---

### Fase 6 — `feature/arquivos-apoio`

Capítulo 25.

- [x] Migration `V11` `arquivos_apoio`: de uma aula **ou** de um módulo (`CHECK` com `num_nonnulls`), título, nome de download, chave, tipo e tamanho; aula ou módulo com arquivos responde 409 na exclusão
- [x] Envio pelo ADM em duas etapas, como a capa: `POST /api/adm/{aulas|modulos}/{id}/arquivos/envio` (ZIP, PDF ou TXT até 200 MB) → PUT direto no Garage → `POST /api/adm/{aulas|modulos}/{id}/arquivos` confere a chave gerada para o mesmo dono, o tamanho real e o conteúdo (assinatura do ZIP e do PDF; texto sem byte nulo) e move de `uploads/` (que expira) para `arquivos/`; recusado é apagado. Nome de download limpo (sem caminho, acento ou aspas) e com a extensão do formato real. `DELETE /api/adm/arquivos/{id}` remove do bucket
- [x] Aluno (`/api/aluno`): lista da aula (mesmo acesso da aula, gratuita inclusive) e do módulo (matrícula vigente na turma; módulo inativo responde 404) e `GET /arquivos/{id}/download` com URL pré-assinada de 5 minutos que baixa como anexo (`Content-Disposition` com o nome do arquivo); a chave do bucket nunca sai na resposta
- [x] Testes: unitários (formatos, nomes, serviços), integração (matriz de papéis do ADM, matriz de acesso ao conteúdo na aula e no módulo, conteúdo falso, chave de outra aula, exclusões bloqueadas) e e2e `ArquivoE2E` (envio pelo Caddy direto ao Garage; download bloqueado sem matrícula e liberado com matrícula)

**Pronto quando:** o aluno matriculado baixa o arquivo da aula ou do módulo por uma URL curta, e sem
matrícula recebe 403.

---

### Fase 7 — `feature/forum`

Capítulo 26.

- [x] Migration `V12`: `forum_topicos` (da turma ou de uma aula da turma; aula excluída vira tópico da turma), `forum_respostas` (marca de instrutor) e `forum_anexos` (de um tópico **ou** de uma resposta); Markdown guardado junto com o HTML já filtrado
- [x] Aluno (`/api/aluno`, só com matrícula vigente na turma): listar tópicos visíveis (fixados primeiro, filtro por aula, contagem de respostas), perguntar, ver o tópico com as respostas e responder; tópico ou resposta oculta não aparece (404)
- [x] Markdown → HTML em duas camadas: commonmark com `escapeHtml` e `sanitizeUrls` e sanitizador OWASP com lista fechada de elementos (sem eventos, estilo, imagens ou iframes); links com `rel="nofollow noopener noreferrer"`. Só o HTML filtrado sai na resposta
- [x] Anexos de imagem: URL pré-assinada em `uploads/forum/<usuario>/<uuid>`; ao publicar, a API confere que a chave é do próprio usuário (contra IDOR), regrava em JPEG sem metadados (até 1600 × 1600) em `forum/anexos/` e devolve URLs temporárias; até 3 por mensagem
- [x] Moderação no ADM (`/api/adm`): todos os tópicos e respostas (ocultos inclusive), ocultar/mostrar e fixar tópico, ocultar resposta, responder como instrutor (o tópico fica marcado como respondido)
- [x] Limite de mensagens por aluno contado no banco (`app.forum`: 20 perguntas e respostas por hora): 429 com `Retry-After` até a mais antiga sair da janela
- [x] Testes: unitários (XSS em tabela, entidade, limite, anexos e IDOR, serviços, DTOs), integração (pergunta e resposta do instrutor, XSS guardado, anexo e IDOR, sem matrícula, moderação, 429, matriz de papéis da moderação)
- [x] e2e `ForumE2E`: aluno pergunta (com HTML malicioso filtrado), admin responde como instrutor, aluno vê a resposta

**Pronto quando:** o aluno matriculado pergunta no fórum da aula, o instrutor responde pela moderação e
o aluno vê a resposta, sem nenhum HTML perigoso chegando ao navegador.

---

### Fase 8 — `feature/certificado`

Capítulo 27.

- [x] Regra de conclusão por turma (migration `V13`: `percentual_conclusao` de 1 a 100, padrão 100, e `carga_horaria_horas` opcional), definida em `PUT /api/adm/turmas/{id}/certificado`; turma sem aulas nunca é concluída
- [x] Situação (`GET /api/aluno/turmas/{id}/certificado`: percentual visto, exigido, concluiu, certificado se já emitido) e emissão (`POST`, com o nome completo confirmado pelo aluno), ambas com matrícula vigente; emitir de novo devolve o mesmo certificado (dados impressos congelados na emissão: nome, curso, turma, carga horária)
- [x] PDF com openhtmltopdf a partir de um template Thymeleaf, gerado uma vez e guardado em `certificados/<turma>/<codigo>.pdf`; download do próprio aluno por URL de 5 minutos (de outro aluno, 404); lista em `GET /api/aluno/certificados`
- [x] Código de validação de 12 caracteres sem letras ambíguas (60 bits aleatórios) e `GET /api/certificados/{codigo}` público, sem e-mail nem ids internos
- [x] Testes: unitários (regra com as bordas, código, nome, serviço, turma), integração (ciclo completo com PDF no Garage, regra do ADM, sem matrícula, download de outro aluno, validações) e e2e `CertificadoE2E` (aluno conclui → gera certificado → baixa o PDF → código valida sem login)

**Pronto quando:** o aluno que concluiu a turma baixa o certificado em PDF e qualquer pessoa confere o
código impresso nele.

---

### Fase 9 — `feature/perfil-termos`

Capítulo 28.

- [x] Perfil (`/api/perfil`, para aluno e admin, sempre pelo token): dados sem senha nem hash, troca de nome, troca de senha (exige a atual, política do papel — aluno 10, admin 12 —, diferente da atual; encerra todas as sessões e limpa o cookie de refresh) e foto (migration `V14`: `foto_chave`; envio direto ao bucket, chave do próprio usuário contra IDOR, regravada em JPEG sem metadados até 400 × 400, anterior apagada)
- [x] Termos versionados (migration `V14`: `termos` com tipo `TERMOS_USO`/`POLITICA_PRIVACIDADE` e versão crescente; `aceites_termos` com IP e navegador): a vigente de cada tipo é a maior versão; `GET /api/termos/vigentes` público, `GET /api/termos/pendentes` e `POST /api/termos/{id}/aceite` (só a vigente; antiga 409)
- [x] Bloqueio: `TermoPendenteInterceptor` em `/api/aluno/**` e `/api/aulas/**` responde 403 com `termosPendentes` enquanto houver vigente sem aceite (primeiro acesso e cada versão nova); perfil, sessão e termos continuam liberados. `RegraNegocioException.propriedades()` leva campos extras ao ProblemDetail
- [x] ADM: `POST /api/adm/termos` publica a próxima versão (Markdown → HTML filtrado pelo `MarkdownSeguro`, agora em `shared/texto`) e `GET /api/adm/termos?tipo=` lista as versões
- [x] Testes: unitários (termos, interceptor, perfil), integração (perfil com usuário próprio, troca de senha encerrando a sessão, foto e IDOR, bloqueio e aceite — apagando os termos ao fim, porque o banco é compartilhado entre as classes) e e2e `PerfilTermosE2E`. No e2e, `Api.login` aceita os termos pendentes como o front fará no primeiro acesso; `Api.loginSemAceite` serve à jornada do bloqueio
- [x] e2e de API: novo termo publicado → rotas do aluno bloqueadas até o aceite; perfil atualizado e troca de senha invalidando o refresh antigo

**Pronto quando:** o aluno cuida do próprio perfil e só usa a área do aluno depois de aceitar os termos
vigentes, a cada versão nova.

---

### Fase 10 — `feature/operacao`

Capítulos 29 a 31. Fica antes do front porque precisa estar pronta antes de entrarem dados reais em produção.

- [x] Backup com restic para fora da VPS (`infra/scripts/backup.sh`): dump do PostgreSQL por stdin e arquivos do Garage com o snapshot consistente dos metadados; retenção 7/4/6. Restauração (`restaurar.sh`) testada na stack de e2e — banco e os 11 objetos do bucket de volta — e documentada em [operação](operacao.md)
- [ ] Agendar o backup no cron da VPS e repetir o teste de restauração na homologação (depende da VPS no ar)
- [x] Alertas do worker por e-mail (`ALERTA_EMAIL`): jobs que foram para ERRO e disco abaixo de 15% livre, a cada 15 minutos
- [ ] Monitor de uptime externo (UptimeRobot ou Better Stack) no health check e no site (depende da VPS no ar)
- [x] Rotação de logs do Docker (`json-file`, 5 × 10 MB) em todos os serviços; uploads órfãos já expiram pelo bucket (2 dias); roteiro para limpar a homologação
- [x] Conta das horas de vídeo que cabem no disco e plano de saída para o R2/B2 (troca de endpoint + `rclone sync`) em [operação](operacao.md)
- [x] Revisão OWASP ASVS nível 1 documentada; Trivy no pipeline (críticos com correção quebram o build — já corrigiu Tomcat e Jackson); OWASP ZAP baseline na stack local (0 falhas; `Cross-Origin-Resource-Policy` acrescentado ao Caddy)
- [ ] OWASP ZAP baseline na homologação a cada versão (depende da VPS no ar)

**Pronto quando:** existe backup diário fora da VPS com a restauração testada, os alertas chegam por
e-mail e o pipeline barra imagens com vulnerabilidade crítica conhecida.

---

## Bloco B — Frontend

**Biblioteca de layout:** `@celke/kole-react`, instalada do GitHub (`github:celkecursos/kole-react`)
com o commit fixo no `package.json`. Ela traz a moldura (`Estrutura`, `TelaLogin`), formulários,
tabelas e feedback. Os componentes próprios da área do aluno (`PlayerVideo` com hls.js, `CardCurso`,
`ListaAulas`, fórum) ficam no app enquanto não existirem na biblioteca.

### Fase 11 — `feature/web-base`

Capítulo 33.

- [x] `web/` com `aluno` e `adm` (Vite + React + TypeScript, Node 24); um pacote só, sem workspaces; `site` entra na Fase 14
- [x] `@celke/kole-react` do GitHub com commit fixo
- [x] Cliente HTTP: token em memória, refresh silencioso, fila de requisições durante o refresh
- [x] Rotas protegidas por papel; tratamento de termo pendente
- [x] Aluno: login, cadastro, confirmação de e-mail, recuperar senha
- [x] ADM: login, layout `Estrutura`
- [x] Proxy do Vite `/api → localhost:8080` (mesma origem também no dev)
- [x] Imagem `web`: build dos fronts + Caddy servindo `/srv/aluno` (:8081) e `/srv/adm` (:8082); o Caddy da VPS manda `/api` para a API e o resto para ela
- [x] Caddy: CSP (mídia em `MIDIA_ORIGEM`), `X-Frame-Options`, cache longo nos assets com hash e sem cache no `index.html` (o Swagger da homologação continua: a API ainda é testada por ele)
- [x] Vitest + Testing Library; Playwright no navegador entra no `pipeline.yml`
- [x] e2e (Playwright, caminho feliz e infeliz): login aluno e admin, senha errada, cadastro com validação e confirmação pelo Mailpit, recuperação de senha, links inválidos, aluno barrado no ADM, sessão por refresh, refresh reutilizado, logout

### Fase 12 — `feature/web-adm`

Capítulo 34.

- [x] Cursos, turmas, módulos e aulas (`Tabela`, `Form`, `Paginacao`, `Abas`); reordenar com os botões subir/descer (acessível por teclado, uma troca por clique)
- [x] Upload de vídeo multipart direto ao Garage com progresso e cancelamento, status do processamento (consulta a cada 3 s), reprocessar, remover
- [x] Capa do curso, arquivos de apoio (aula e módulo), matrículas (período, cancelamento), moderação do fórum (responder, fixar, ocultar), certificado da turma, termos
- [x] e2e (Playwright, caminho feliz e infeliz): admin cria curso completo e envia vídeo curto até "pronto"; validações na tela, conflitos da API (slug repetido, exclusão com filhos), formatos recusados, matrícula sem conta e em turma encerrada, moderação do fórum

### Fase 13 — `feature/web-aluno`

Capítulo 35.

- [x] Meus cursos em abas (ativos, arquivados, expirados), arquivar/desarquivar
- [x] Turma com módulos, aulas, arquivos do módulo e progresso (vistas e revisar)
- [x] Aula: player HLS (hls.js; token só nas playlists da API) com retomada e envio da posição, anterior/próxima, vista/revisar, arquivos, fórum da aula (pergunta e resposta; as imagens nas mensagens entram na Fase 18)
- [x] Certificado (situação, emissão com o nome completo, PDF), lista de certificados e perfil (nome, foto, senha)
- [x] e2e (Playwright, caminho feliz e infeliz): login → turma → aula → vídeo toca até o fim → vista → progresso da turma; retomada, revisar, navegação, download do arquivo, fórum, certificado (bloqueado → emitido → validado), perfil; sem matrícula, matrícula futura, arquivar

### Fase 14 — `feature/site`

Capítulo 36.

- [x] Layout novo com os tokens da `@celke/kole-react` (HTML estático, sem JavaScript de layout)
- [x] Pré-renderização estática (uma página HTML por rota) em `cursos.celke.uno`: o container `site` gera ao subir e a cada `SITE_INTERVALO` (padrão 5 min) a partir da API do próprio ambiente, com troca atômica da versão; a mesma imagem serve homologação e produção
- [x] Home, catálogo, página do curso, validar certificado, termos, privacidade, 404
- [x] SEO: meta tags, canônico, Open Graph, dados estruturados (Course), sitemap, `robots.txt` (homologação fora dos buscadores)
- [x] Meta de Lighthouse no CI (desktop): desempenho ≥ 90, acessibilidade ≥ 95, boas práticas ≥ 95, SEO ≥ 90 em todas as páginas

---

### Fase 15 (opcional) — `feature/migracao-legado`

Capítulo 37.

- [ ] ETL do MySQL do CLMS: usuários, cursos, turmas, módulos, aulas, matrículas, progresso
- [ ] Mapear status numéricos do legado (`3, 4, 13, 14, 15`...) para os enums novos
- [ ] Senhas `$2y$` do Laravel aceitas no primeiro login e re-hash
- [ ] Reprocessar vídeos existentes no S3 para HLS


### Fase 16 — `feature/pagamentos`

Prompt: [`docs/prompts/f16-01-pagamentos-hotmart.md`](prompts/f16-01-pagamentos-hotmart.md). Operação e
configuração da Hotmart: [`docs/pagamentos.md`](pagamentos.md).

- [x] Módulo `pagamento` com a porta `GatewayPagamento` (um bean por meio de pagamento) e o status de pedido único para todos
- [x] Meio de pagamento configurado pelo painel (formulário a partir dos campos do gateway, segredos com AES-GCM, um ativo por vez)
- [x] Hotmart com o **webhook 2.0.0** (`X-HOTMART-HOTTOK`, JSON com `id` por evento); a versão 1.0.0 não é mais recomendada
- [x] Checkout por redirecionamento com o código do pedido no `xcod`; página de retorno que acompanha o status
- [x] Notificações idempotentes, processadas pela fila, tolerantes a fora de ordem; matrícula criada, mantida ou cancelada conforme o status
- [x] Conta do comprador sem senha, com link de criar senha (nunca senha por e-mail)
- [x] Reconciliação pela API de vendas (preparada, ligada por configuração)
- [x] Painel: meios de pagamento, ofertas por turma, pedidos e eventos recebidos (reprocessar)
- [x] Testes unitários, de integração, e2e e Playwright com caminho feliz e infeliz
- [x] `PAGAMENTOS_CHAVE` nos `.env` dos ambientes; homologação (restrita por IP) aceita só o caminho do webhook de fora
- [ ] Na homologação: "Enviar teste" da Hotmart de cada evento, fixtures trocadas pelos corpos reais


### Fase 17 — `feature/auditoria`

Prompt: [`docs/prompts/f17-01-auditoria-e-logs.md`](prompts/f17-01-auditoria-e-logs.md). Operação:
[`docs/operacao.md`](operacao.md), seções 4 e 4.1.

- [x] Trilha de auditoria (`auditoria`) gravada por um ouvinte dos eventos do Hibernate, na transação da mudança: usuário, operação, entidade e id, antes e depois (só o que mudou), origem (rota ou job), IP, navegador, id da requisição e data
- [x] Dados sensíveis (`@DadoSensivel`) como `***`; entidades de log e de sessão fora da trilha (`@SemAuditoria`)
- [x] Só inclusão garantida pelo banco (gatilho); retenção de 5 anos pelo worker
- [x] Tela de consulta no ADM (Segurança → Auditoria) com filtros e o antes/depois lado a lado
- [x] Logs da API e do worker também em arquivo, no volume `logs`: um por dia, 180 dias ou 2 GB por serviço
- [x] Testes unitários, de integração e Playwright

### Fase 18 — `feature/forum-anexos`

Prompt: [`docs/prompts/f18-01-anexos-no-forum.md`](prompts/f18-01-anexos-no-forum.md).

- [x] Imagens na pergunta e na resposta do aluno: escolher, arrastar ou colar um print (no texto também), com prévia, remover e progresso do envio
- [x] Tipo, tamanho e quantidade conferidos em todos os caminhos (o `accept` do seletor não filtra arrastar e colar): JPEG ou PNG, até 5 MB, até 3 por mensagem
- [x] Envio direto ao armazenamento só ao publicar (a API apaga o envio ao processá-lo; uma nova tentativa envia de novo)
- [x] Imagens publicadas na pergunta e nas respostas, para o aluno e na moderação do ADM, com visualizador em tela cheia
- [x] Testes unitários e Playwright (caminho feliz e infeliz)

### Fase 19 — `feature/seguranca`

Prompt: [`docs/prompts/f19-01-seguranca.md`](prompts/f19-01-seguranca.md). Operação:
[`docs/operacao.md`](operacao.md), seção 7.

- [x] Segundo fator (TOTP, RFC 6238) obrigatório para administradores: desafio depois da senha, cadastro por QR code, códigos de recuperação de uso único, segredo cifrado (`TOTP_CHAVE`), código sem reuso, comando `redefinir-2fa`
- [x] Usuário do banco só de dados para a API e o worker; migrations com o dono do schema no serviço `migracao` (comando `migrar`), que cria e libera o usuário da aplicação
- [x] Política de senha: lista de senhas comuns e vazadas, nome e e-mail, sequências e repetições; medidor de força nas telas
- [x] Limite por usuário nas gravações e no checkout
- [x] Dependabot (Maven, npm, Actions, Docker) e `npm audit` no pipeline
- [x] ADM → Segurança → Usuários: busca, bloquear e desbloquear, encerrar as sessões (o access token já emitido deixa de valer na hora), tudo na auditoria
- [x] Checkout só em HTTPS nos domínios da Hotmart; a compra só abre o pedido com a confirmação do aluno
- [x] Token dos links de e-mail sai da barra de endereço depois de lido
- [x] Teste que varre todas as rotas da API: só as públicas declaradas respondem sem login
- [x] Testes unitários, de integração e Playwright

### Fase 20 — `feature/lgpd`

Prompt: [`docs/prompts/f20-01-lgpd.md`](prompts/f20-01-lgpd.md). Operação:
[`docs/operacao.md`](operacao.md), seção 4.2. Modelo da política:
[`docs/politica-privacidade-modelo.md`](politica-privacidade-modelo.md).

- [x] Baixar meus dados: JSON com a conta e uma seção por módulo (`DadosPessoais` em cada módulo)
- [x] Excluir minha conta: senha e confirmação, aviso por e-mail, conta anonimizada, progresso, certificados, foto, sessões e segundo fator apagados, pedidos sem e-mail e nome; o que tem prazo legal fica
- [x] Retenção da auditoria de login (180 dias)
- [x] Modelo da política de privacidade com dados, finalidades, bases legais e os prazos aplicados pelo sistema
- [x] Perfil do aluno → Privacidade; testes de integração e Playwright

### Fase 21 — `feature/site-layout`

Prompt: [`docs/prompts/f21-01-site-react-ssr.md`](prompts/f21-01-site-react-ssr.md).

- [x] Site em React Router (modo framework) com renderização no servidor a cada requisição: HTML completo para os buscadores, hidratado no navegador; o container `site` vira um servidor Node na porta 3000 (o `web` deixa de servir a 8083)
- [x] Leituras da API com cache curto em memória (`SITE_CACHE_SEGUNDOS`, 30 s), 404 de verdade para curso inexistente, CSP com nonce por requisição
- [x] Layout moderno: topo fixo, abertura com ilustração, vantagens, cursos em destaque, como funciona, página do curso com coluna de compra fixa e conteúdo por módulo, rodapé, tema escuro
- [x] Busca no catálogo direto no navegador (sem acento e sem diferenciar maiúsculas)
- [x] Favicon da `@celke/kole-react` nos três fronts
- [x] Script `npm run cursos-exemplo` que cria cursos publicados de exemplo pela API do ADM
- [x] Testes unitários do SEO, Playwright do site e Lighthouse (metas da Fase 14)

---

## 5. Mapa fase → capítulos do tutorial

O conteúdo de cada capítulo está em [capitulos.md](capitulos.md).

| Fase | Branch | Capítulos |
|---|---|---|
| 0 | — | 1, 2 |
| 1 | `feature/base-projeto` | 3–14 |
| 2 | `feature/cursos` | 15, 16 |
| 3 | `feature/matricula-acesso` | 17, 18 |
| 4 | `feature/upload-video` | 19–21 |
| 5 | `feature/entrega-video` | 22–24 |
| 6 | `feature/arquivos-apoio` | 25 |
| 7 | `feature/forum` | 26 |
| 8 | `feature/certificado` | 27 |
| 9 | `feature/perfil-termos` | 28 |
| 10 | `feature/operacao` | 29–31 |
| — | repositório da `@celke/kole-react` | 32 |
| 11 | `feature/web-base` | 33 |
| 12 | `feature/web-adm` | 34 |
| 13 | `feature/web-aluno` | 35 |
| 14 | `feature/site` | 36 |
| 15 | `feature/migracao-legado` | 37 |
| 16 | `feature/pagamentos` | 38–40 |
| 17 | `feature/auditoria` | 41 |
| 18 | `feature/forum-anexos` | 26 |
| 19 | `feature/seguranca` | 42, 43 |
| 20 | `feature/lgpd` | 44 |
| 21 | `feature/site-layout` | 45 |
`````

`docs/vps.md`:

`````markdown
# Roteiro da VPS

Preparação da VPS da Hostinger (Ubuntu 24.04 ou mais novo; testado também no 26.04) para a produção e
a homologação. Feito uma vez; as
versões novas são implantadas pelo pipeline (merge na `homolog` → homologação; merge na `main` →
produção), pelo script `/usr/local/bin/plataforma-cursos-deploy`.

## Como fica a VPS

```
/opt/plataforma-cursos/            (dono: deploy, permissão 750)
├── caddy/                         Caddy único nas portas 80/443 (projeto plataforma-cursos-caddy)
│   ├── compose.yml
│   ├── Caddyfile
│   ├── sites/producao.caddy
│   ├── sites/homolog.caddy
│   └── .env                       hosts, e-mail do Let's Encrypt, IPs liberados
├── producao/                      projeto plataforma-cursos-producao, rede plataforma-cursos-producao
│   ├── compose.prod.yml
│   ├── garage/garage.toml
│   ├── .env                       segredos da produção (TAG = versão no ar)
│   ├── versoes/<sha>/             arquivos de compose das últimas versões (rollback)
│   ├── backups/                   dump do banco antes de cada deploy
│   └── deploy.log                 histórico dos deploys
└── homolog/                       projeto plataforma-cursos-homolog, rede plataforma-cursos-homolog
    ├── compose.prod.yml
    ├── compose.homolog.yml
    ├── garage/garage.toml
    └── .env                       segredos da homologação (diferentes dos da produção)
```

Só o Caddy publica portas. Ele chega à API, aos fronts e ao Garage de cada ambiente pela rede do
ambiente, pelos apelidos `producao-api`, `producao-web`, `producao-site`, `producao-garage`,
`homolog-api`, `homolog-web`, `homolog-site` e `homolog-garage`. Em `aluno` e `adm`, `/api/*` e o health check vão para a API; o resto vai para o
container `web` (área do aluno na porta 8081, ADM na 8082). Em `cursos`, só as leituras públicas da
API (catálogo, termos vigentes, validação de certificado) vão para ela; o resto é o site público, que o
container `site` (React com renderização no servidor) monta a cada requisição a partir da API, na
porta 3000.

| Host | Ambiente | Acesso |
|---|---|---|
| `aluno.celke.uno` | produção | público |
| `adm.celke.uno` | produção | só `ADM_IPS` |
| `cursos.celke.uno` | produção | público (site renderizado a partir da API) |
| `media.celke.uno` | produção | público (URLs assinadas) |
| `homolog-aluno.celke.uno`, `homolog-adm.celke.uno`, `homolog-media.celke.uno` | homologação | só `HOMOLOG_IPS`, `noindex`, com Swagger |

## 1. Contratar a VPS na Hostinger

1. Contrate a VPS pelo link https://celke.com.br/page/hostinger e use o cupom **CELKE** no pagamento.
   Recomendado: plano **KVM 4** (4 vCPU, 16 GB de RAM, 200 GB de disco NVMe), com folga para a
   produção e a homologação na mesma VPS e para a conversão dos vídeos. Sistema **Ubuntu 24.04**, sem
   painel de controle; defina uma senha forte para o root (ela só vale até o passo 4).
2. No painel da Hostinger, anote o IP da VPS.
3. Firewall da VPS: libere só 22, 80 e 443 (TCP) e 443 (UDP). O `ufw` faz o mesmo dentro da VPS; o
   painel é a segunda camada.
4. Ative o backup/snapshot semanal do painel (não substitui o backup fora da VPS da fase de operação).

## 2. DNS de celke.uno

Crie registros **A** apontando para o IP da VPS (só subdomínios; o domínio principal continua com as
outras aplicações):

```
cursos          A   <ip-da-vps>
aluno           A   <ip-da-vps>
adm             A   <ip-da-vps>
media           A   <ip-da-vps>
homolog-cursos  A   <ip-da-vps>
homolog-aluno   A   <ip-da-vps>
homolog-adm     A   <ip-da-vps>
homolog-media   A   <ip-da-vps>
```

Confira antes de seguir: `dig +short aluno.celke.uno` responde o IP da VPS.

## 3. Chaves SSH

São **duas chaves diferentes**, as duas geradas no **seu computador** (nunca na VPS: a chave privada do
deploy ficaria guardada no próprio servidor que ela abre). De cada uma, só a parte pública (`.pub`) vai
para a VPS.

| Chave | Quem usa | Pública (`.pub`) vai para | Privada fica |
|---|---|---|---|
| administrador | você, para entrar na VPS | `ADMIN_CHAVE` | no seu computador |
| deploy | o GitHub Actions, para publicar | `DEPLOY_CHAVE` | no secret `VPS_SSH_KEY` (passo 8) |

Não use a mesma chave nas duas: a do deploy só executa o script de deploy (sem terminal), e a do
administrador dá acesso completo com sudo.

No Linux, macOS ou Git Bash:

```bash
# Sua chave (administrador). Use a que já tem, se houver (id_ed25519.pub ou id_rsa.pub):
cat ~/.ssh/id_ed25519.pub

# Chave só do deploy automático
ssh-keygen -t ed25519 -f ~/.ssh/plataforma-cursos-deploy -C "github-deploy" -N ""
cat ~/.ssh/plataforma-cursos-deploy.pub
```

No PowerShell do Windows:

```powershell
Get-Content $HOME\.ssh\id_ed25519.pub     # ou id_rsa.pub; sem nenhuma: ssh-keygen -t ed25519
ssh-keygen -t ed25519 -f $HOME\.ssh\plataforma-cursos-deploy -C "github-deploy" -N '""'
Get-Content $HOME\.ssh\plataforma-cursos-deploy.pub
```

Cada `.pub` é **uma linha inteira**: `ssh-ed25519 AAAAC3Nza...` (cerca de 80 caracteres) ou
`ssh-rsa AAAAB3Nza...` (centenas de caracteres). Copie a linha toda; uma chave cortada deixa você fora
da VPS no fim do passo 4.

## 4. Preparar a VPS

O script recebe três valores:

- `ADMIN_USUARIO`: o nome do **seu** usuário Linux na VPS (ex.: `cesar`). Ele ainda não existe: o script
  cria, com sudo e acesso ao Docker. Não é o usuário do painel da Hostinger nem o do GitHub. Depois do
  script, é com ele que você entra (`ssh cesar@<ip-da-vps>`), porque o root deixa de entrar por SSH.
- `ADMIN_CHAVE`: a linha do `.pub` da sua chave de administrador.
- `DEPLOY_CHAVE`: a linha do `.pub` da chave do deploy. O usuário `deploy` tem nome fixo.

Do seu computador, na raiz do repositório:

```bash
scp infra/scripts/preparar-vps.sh root@<ip-da-vps>:/root/
ssh root@<ip-da-vps>
```

Na VPS, como root. Primeiro garanta o fim de linha LF (um checkout do Windows feito antes do
`.gitattributes` pode ter trazido CRLF, e o bash falha com `set: pipefail: invalid option name`):

```bash
cd /root
sed -i 's/\r$//' preparar-vps.sh
grep -c $'\r' preparar-vps.sh          # 0
```

Depois rode o script, **numa linha só** e com as chaves entre aspas:

```bash
ADMIN_USUARIO=cesar ADMIN_CHAVE="ssh-ed25519 AAAA... sua chave" DEPLOY_CHAVE="ssh-ed25519 AAAA... github-deploy" bash preparar-vps.sh
```

Para quebrar em várias linhas, termine cada uma com `\` (sem espaço depois dela) e não esqueça a última,
`bash preparar-vps.sh`: sem ela as variáveis são definidas e nada é executado. As linhas com `#` no
começo do script são só o exemplo de uso; não precisa editar o arquivo.

O script pede a senha do sudo do administrador (o login continua só por chave) e termina com
**"Pronto."**. A chave do deploy fica presa ao script de deploy (`command=` no `authorized_keys`): ela
não abre shell, terminal nem túnel, mesmo se vazar.

**Não feche a sessão de root** até confirmar, em outro terminal:

```bash
ssh cesar@<ip-da-vps>      # entra sem senha
sudo -v                    # aceita a senha definida pelo script
sudo ufw status            # ativo, com OpenSSH, 80/tcp, 443/tcp e 443/udp
swapon --show              # /swapfile de 2G
```

Depois disso, `ssh root@<ip-da-vps>` e login por senha passam a ser recusados.

### Se algo der errado no passo 4

- **O script parou no meio**: ele pode ser rodado de novo, com os mesmos valores; o que já existe é
  mantido e só a senha do sudo é pedida outra vez. Se ele parou depois de `==> SSH só por chave e sem
  root`, o root pode já não entrar por SSH (no Ubuntu 24.04+ o SSH sobe por socket e cada conexão nova
  já lê a configuração). Entre como administrador, copie o script para a pasta dele
  (`scp infra/scripts/preparar-vps.sh cesar@<ip-da-vps>:~/`) e rode como root com `sudo -i`.
- **`sudo -i` e os comandos seguintes colados juntos**: as linhas coladas se perdem enquanto o shell do
  root abre. Rode o `sudo -i` sozinho e os comandos depois, um de cada vez.
- **`ssh cesar@...` recusado** (`Permission denied (publickey)`), quase sempre por chave incompleta no
  `ADMIN_CHAVE`: entre pelo **terminal do navegador** no painel da Hostinger (VPS → Terminal), que não
  passa pelo SSH, desfaça o bloqueio com `rm /etc/ssh/sshd_config.d/01-plataforma.conf` e rode o script
  de novo com a chave completa.
- **`scp` roda no seu computador**, não dentro da VPS: na VPS ele tenta conectar nela mesma e é recusado.

## 5. Copiar os arquivos da infraestrutura

Do seu computador, na raiz do repositório:

```bash
VPS=cesar@<ip-da-vps>
ssh $VPS 'mkdir -p /tmp/plataforma && cd /tmp/plataforma && mkdir -p caddy/sites producao/garage homolog/garage'
scp infra/caddy/compose.yml infra/caddy/Caddyfile infra/caddy/.env.example $VPS:/tmp/plataforma/caddy/
scp infra/caddy/sites/producao.caddy infra/caddy/sites/homolog.caddy $VPS:/tmp/plataforma/caddy/sites/
scp infra/compose.prod.yml $VPS:/tmp/plataforma/producao/
scp infra/compose.prod.yml infra/compose.homolog.yml $VPS:/tmp/plataforma/homolog/
scp infra/garage/garage.toml $VPS:/tmp/plataforma/producao/garage/
scp infra/garage/garage.toml $VPS:/tmp/plataforma/homolog/garage/
scp infra/scripts/gerar-env-servidor.sh infra/scripts/deploy.sh $VPS:/tmp/plataforma/
ssh $VPS 'sudo install -o root -g root -m 755 /tmp/plataforma/deploy.sh /usr/local/bin/plataforma-cursos-deploy \
  && rm /tmp/plataforma/deploy.sh \
  && sudo cp -r /tmp/plataforma/. /opt/plataforma-cursos/ && sudo chown -R deploy:deploy /opt/plataforma-cursos \
  && rm -rf /tmp/plataforma'
```

## 6. Configuração de cada ambiente

Na VPS, como `deploy` (`sudo -iu deploy`):

```bash
cd /opt/plataforma-cursos
bash gerar-env-servidor.sh producao producao/.env
bash gerar-env-servidor.sh homolog homolog/.env
cp caddy/.env.example caddy/.env && chmod 600 caddy/.env
```

Preencha:

- `producao/.env` e `homolog/.env`: `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD` (painel da Iagente).
  Na homologação, use um remetente identificado ou restrinja os envios a e-mails de teste.
- `caddy/.env`: `ACME_EMAIL`, `ADM_IPS` e `HOMOLOG_IPS` (seus IPs, separados por espaço).

Os segredos de cada `.env` são gerados aleatoriamente e são diferentes entre os ambientes: um token de
homologação nunca vale na produção. Entre eles estão a senha do usuário da aplicação no banco
(`POSTGRES_APP_USER`/`POSTGRES_APP_PASSWORD`, só lê e grava dados) e a `TOTP_CHAVE`, que cifra o segundo
fator dos administradores. Num ambiente criado antes dessas variáveis, o próprio deploy as acrescenta
ao `.env`.

Depois da primeira subida, crie o administrador (`criar-admin`) e entre no ADM: o primeiro login pede o
cadastro do aplicativo autenticador (QR code) e mostra os códigos de recuperação, que devem ser
guardados fora da VPS.

## 7. Acesso às imagens (GHCR)

Crie no GitHub um token clássico só com `read:packages` e, na VPS, como `deploy`:

```bash
echo "<token>" | docker login ghcr.io -u <usuario-github> --password-stdin
```

## 8. Deploy automático no GitHub

Feito com a conta dona do repositório (colaboradores não veem as configurações). Em **Settings →
Secrets and variables → Actions**, crie os secrets do repositório (servem aos dois ambientes: a VPS e a
chave são as mesmas):

| Secret | Valor |
|---|---|
| `VPS_HOST` | IP da VPS |
| `VPS_SSH_KEY` | conteúdo de `~/.ssh/plataforma-cursos-deploy` (a chave **privada** do deploy) |
| `VPS_KNOWN_HOSTS` | saída de `ssh-keyscan -t ed25519 <ip-da-vps>` (conferida com o painel da Hostinger) |

Na aba **Variables** da mesma página, crie a variável `DEPLOY_ATIVO` com o valor `true`. Sem ela, o
pipeline pula os deploys (testes, e2e e publicação das imagens continuam rodando).

Os jobs de deploy usam os environments `homolog` e `production`, criados automaticamente na primeira
execução. Se o plano da conta permitir environments em repositório privado, ative em **Settings →
Environments → production** a opção **Required reviewers**: o deploy da produção passa a esperar a sua
aprovação.

## 9. Primeira subida

1. Abra um PR de `feature/base-projeto` para `homolog`, espere o pipeline ficar verde e faça o merge.
   O pipeline publica as imagens no GHCR e implanta a homologação (é a primeira subida dela: sem backup
   e sem versão anterior).
2. Suba o Caddy na VPS, como `deploy`. Ele se liga às redes dos dois ambientes, e a da produção só nasce
   no deploy dela: na primeira vez, crie essa rede com os rótulos do Docker Compose (o deploy da produção
   a adota depois). Até lá, os endereços da produção respondem 502.

   ```bash
   cd /opt/plataforma-cursos
   docker network inspect plataforma-cursos-producao > /dev/null 2>&1 || docker network create \
     --label com.docker.compose.network=default --label com.docker.compose.project=plataforma-cursos-producao \
     plataforma-cursos-producao
   docker compose -f caddy/compose.yml --env-file caddy/.env --project-directory caddy up -d
   ```

3. Valide a homologação (passo 10, nos endereços `homolog-*`) e abra o PR de `homolog` para `main`:
   o pipeline implanta a produção.

4. Crie o administrador de cada ambiente (a senha é pedida no terminal):

   ```bash
   docker compose -p plataforma-cursos-producao -f producao/compose.prod.yml --env-file producao/.env \
     --project-directory producao run --rm api criar-admin admin@celke.uno "Administrador"
   docker compose -p plataforma-cursos-homolog -f homolog/compose.prod.yml -f homolog/compose.homolog.yml \
     --env-file homolog/.env --project-directory homolog run --rm api criar-admin admin@celke.uno "Administrador"
   ```

## 10. Verificação

Do seu computador:

```bash
curl -s https://aluno.celke.uno/actuator/health            # {"groups":[...],"status":"UP"}
curl -sI https://aluno.celke.uno/actuator/health | grep -i strict-transport
curl -s https://aluno.celke.uno/ | grep -o '<title>.*</title>'   # a área do aluno
curl -s https://aluno.celke.uno/v3/api-docs | head -c 15        # <!doctype html> (o front, não a API)
curl -s -o /dev/null -w "%{http_code}\n" https://adm.celke.uno/actuator/health # 403 fora de ADM_IPS
nc -zv -w 3 <ip-da-vps> 5432 ; nc -zv -w 3 <ip-da-vps> 3900 ; nc -zv -w 3 <ip-da-vps> 8080   # recusadas
ssh root@<ip-da-vps>                                        # recusado
```

Na VPS: `docker stats --no-stream` mostra cada container dentro do limite de memória.

## 11. Operação

**Histórico dos deploys**: `tail -n 50 /opt/plataforma-cursos/<ambiente>/deploy.log`. Um deploy que não
fica saudável volta sozinho para a versão anterior e o job do pipeline falha.

**Voltar para uma versão antiga** (uma das guardadas em `versoes/`), como `deploy`:

```bash
tar -C /opt/plataforma-cursos/producao/versoes/<sha> -cz compose.prod.yml garage/garage.toml \
  | plataforma-cursos-deploy producao <sha>
```

O rollback não desfaz migrations: por isso toda migration precisa ser compatível com a versão anterior.

**Atualizar o Caddy** (é compartilhado pelos dois ambientes, então não entra no deploy automático):
copie os arquivos novos de `infra/caddy/` (`Caddyfile` e `sites/`) para `/opt/plataforma-cursos/caddy/`
e, como `deploy`:

```bash
cd /opt/plataforma-cursos
docker compose -f caddy/compose.yml --env-file caddy/.env --project-directory caddy up -d --force-recreate
```

**Site público (Fase 14):** além do DNS de `cursos` e `homolog-cursos` (etapa 2), o `caddy/.env`
precisa de `PRODUCAO_HOST_SITE` e `HOMOLOG_HOST_SITE` (veja `infra/caddy/.env.example`). O endereço
público do site vem de `SITE_URL` no `.env` do ambiente; em branco, é derivado de `FRONT_ALUNO_URL`
(`aluno.` → `cursos.`). A homologação sai com `noindex` e `robots.txt` bloqueando tudo.

**Site em React com renderização no servidor (Fase 21):** o site passa a ser servido pelo container
`site` (porta 3000) e não mais pelo `web` (8083, que deixa de existir). Depois do deploy dos dois
ambientes com esta versão, atualize o Caddy como acima: até lá, o Caddy antigo continua mandando
`cursos.*` para o `web`, que já não responde nessa porta.

Quando a mudança do Caddy depende de um container novo (como na Fase 11, que passou a mandar as
páginas para o `web`), atualize o Caddy **depois** do deploy dos dois ambientes com esse container: antes
disso o Caddy novo não teria para onde mandar as páginas (502), enquanto o antigo continua respondendo
404 fora da API.

**Pagamentos (Fase 16):** o `gerar-env-servidor.sh` já cria a `PAGAMENTOS_CHAVE` nos ambientes novos.
Nos `.env` que já existem, acrescente uma chave por ambiente (diferentes entre si), antes do deploy:

```bash
echo "PAGAMENTOS_CHAVE=$(openssl rand -base64 32)" >> /opt/plataforma-cursos/producao/.env
echo "PAGAMENTOS_CHAVE=$(openssl rand -base64 32)" >> /opt/plataforma-cursos/homolog/.env
```

Trocar a chave depois exige informar de novo os segredos do meio de pagamento no painel. A
reconciliação fica desligada até `PAGAMENTOS_RECONCILIACAO=true`. Atualize também o Caddy (acima): a
homologação continua restrita por IP, mas deixa passar `/api/pagamentos/webhooks/*`, para a Hotmart de
teste notificar `https://homolog-aluno.celke.uno/api/pagamentos/webhooks/<token>` (o endereço completo
aparece no painel). Configuração da Hotmart: `docs/pagamentos.md`.

**Logs persistentes e auditoria (Fase 17):** nada a configurar. O deploy cria o volume `logs` de cada
ambiente; a API e o worker gravam nele com retenção (180 dias ou 2 GB por serviço). Como ler:
`docs/operacao.md`, seção 4.

**Atualizar o script de deploy**: copie o `infra/scripts/deploy.sh` novo para a VPS e, como
administrador, `sudo install -o root -g root -m 755 deploy.sh /usr/local/bin/plataforma-cursos-deploy`.
`````

`infra/caddy/Caddyfile`:

`````caddyfile
# Porta de entrada da plataforma. Um Caddy só atende os dois ambientes da VPS: cada site está em
# sites/<ambiente>.caddy e chega à API, aos fronts e ao Garage do seu ambiente pelos apelidos
# <ambiente>-api, <ambiente>-web e <ambiente>-garage. Com "https://..." o Caddy emite o certificado
# sozinho; com "http://..." atende sem TLS (e2e).
{
	admin off
	email {$ACME_EMAIL:nao-configurado@celke.uno}
}

# Headers de segurança em todas as respostas
(seguranca) {
	header {
		X-Content-Type-Options nosniff
		Referrer-Policy strict-origin-when-cross-origin
		# Respostas só podem ser carregadas por páginas do mesmo site (aluno, adm e mídia são subdomínios dele)
		Cross-Origin-Resource-Policy same-site
		Strict-Transport-Security "max-age=31536000"
		-Server
	}
}

# Um front (aluno ou ADM) com a sua API. Só a API e o health check chegam à API; o resto (Swagger,
# outros endpoints do Actuator) nunca: vai para o container web, que serve o app React.
# O X-Forwarded-For que o cliente mandar é substituído pelo IP real da conexão.
# Argumentos: o ambiente (producao, homolog, e2e) e a porta do app no container web (8081 aluno, 8082 ADM).
(app) {
	@api path /api/* /actuator/health /actuator/health/*
	handle @api {
		reverse_proxy {args[0]}-api:8080 {
			# Durante o deploy a API reinicia: segura a requisição em vez de responder 502
			lb_try_duration 30s
		}
	}
	handle {
		reverse_proxy {args[0]}-web:{args[1]} {
			lb_try_duration 30s
		}
	}
}

# Como (app), mas também com a documentação (Swagger): só na homologação
(app_com_docs) {
	@api path /api/* /actuator/health /actuator/health/* /v3/api-docs /v3/api-docs/* /swagger-ui.html /swagger-ui/*
	handle @api {
		reverse_proxy {args[0]}-api:8080 {
			lb_try_duration 30s
		}
	}
	handle {
		reverse_proxy {args[0]}-web:{args[1]} {
			lb_try_duration 30s
		}
	}
}

# Site público (cursos.*): só as leituras públicas da API (catálogo, capas, termos vigentes e
# validação de certificado) chegam a ela; o resto é o site em React renderizado no servidor.
# Argumento: o ambiente.
(site) {
	@api {
		method GET HEAD
		path /api/cursos /api/cursos/* /api/termos/vigentes /api/certificados/*
	}
	handle @api {
		reverse_proxy {args[0]}-api:8080 {
			lb_try_duration 30s
		}
	}
	handle {
		reverse_proxy {args[0]}-site:3000 {
			lb_try_duration 30s
		}
	}
}

# Arquivos do bucket por URL pré-assinada (o host faz parte da assinatura e é repassado como veio)
(midia) {
	reverse_proxy {args[0]}-garage:3900
}

# Painel administrativo: só os IPs de ADM_IPS (separados por espaço)
(somente_ips_adm) {
	@fora not remote_ip {$ADM_IPS}
	respond @fora "Acesso restrito." 403
}

# Homologação: só os IPs de HOMOLOG_IPS e fora dos buscadores. As notificações dos meios de pagamento
# (Hotmart de teste) passam: vêm dos servidores deles e são autenticadas pela API (hottok)
(homolog) {
	header X-Robots-Tag "noindex, nofollow"
	@fora {
		not remote_ip {$HOMOLOG_IPS}
		not path /api/pagamentos/webhooks/*
	}
	respond @fora "Acesso restrito." 403
}

import sites/*.caddy
`````

`infra/compose.e2e.yml`:

`````yaml
# e2e: a stack de um ambiente dos servidores (compose.prod.yml, com os fronts) e o Caddy dos
# servidores, HTTP em *.localhost (sites de caddy/e2e.caddy), Mailpit no lugar do SMTP, imagens
# construídas localmente e o seed do profile e2e. Projeto separado do ambiente local.
#
#   docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
#   docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
name: plataforma-cursos-e2e

services:
  caddy:
    image: caddy:2.11.7-alpine
    ports:
      - "127.0.0.1:${E2E_PORTA:?defina E2E_PORTA}:80"
    volumes:
      - ./caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - ./caddy/e2e.caddy:/etc/caddy/sites/e2e.caddy:ro
    depends_on:
      api:
        condition: service_healthy
      web:
        condition: service_healthy
      site:
        condition: service_healthy

  web:
    image: plataforma-cursos-web:e2e
    build:
      context: ../web
      target: web

  site:
    image: plataforma-cursos-site:e2e
    build:
      context: ../web
      target: site
    environment:
      SITE_URL: http://cursos.localhost:8088
      # Curso publicado pelo teste aparece em até um segundo
      SITE_CACHE_SEGUNDOS: "1"

  migracao:
    image: plataforma-cursos-api:e2e

  api:
    image: plataforma-cursos-api:e2e
    build:
      context: ../backend
      target: api
    environment:
      SPRING_PROFILES_ACTIVE: api,e2e
      SEED_E2E_SENHA: ${SEED_E2E_SENHA:?defina SEED_E2E_SENHA}
      # Admin do seed com o segundo fator ativo; os testes calculam o código com este segredo
      SEED_E2E_TOTP: ${SEED_E2E_TOTP:?defina SEED_E2E_TOTP}
      # Os testes entram várias vezes no mesmo intervalo de 30 s
      APP_AUTH_SEGUNDO_FATOR_REJEITAR_REUSO: "false"
      # Todas as jornadas saem do mesmo IP
      APP_LIMITES_REQUISICOES_POR_IP_POR_MINUTO: "300"
      # O admin do seed grava em quase todas as jornadas
      APP_LIMITES_USUARIO_ESCRITAS_POR_MINUTO: "3000"
      APP_LIMITES_USUARIO_CHECKOUTS_POR_HORA: "300"
      # URLs dos segmentos vencem em segundos: a jornada de entrega confere a expiração
      APP_VIDEO_MARGEM_PLAYLIST: PT5S
      # Sem chamar a Hotmart de verdade: o "Testar credenciais" recebe uma recusa
      APP_PAGAMENTOS_HOTMART_URL_TOKEN: http://mailpit:8025/hotmart-indisponivel

  worker:
    image: plataforma-cursos-worker:e2e
    build:
      context: ../backend
      target: worker
    depends_on:
      mailpit:
        condition: service_healthy

  mailpit:
    image: axllent/mailpit:v1.31.6
    ports:
      - "127.0.0.1:${E2E_MAILPIT_PORTA:?defina E2E_MAILPIT_PORTA}:8025"
    healthcheck:
      test: ["CMD", "/mailpit", "readyz"]
      interval: 5s
      timeout: 3s
      retries: 20
`````

`infra/compose.prod.yml`:

`````yaml
# Stack de um ambiente dos servidores (produção ou homologação): API, worker, fronts (web), gerador
# do site público (site, React renderizado no servidor), PostgreSQL e Garage.
# Imagens publicadas no GHCR com a tag do commit (TAG); nenhuma porta publicada. O Caddy (projeto
# próprio, em caddy/) chega à API, aos fronts, ao site e ao Garage pela rede plataforma-cursos-<AMBIENTE>,
# pelos apelidos <AMBIENTE>-api, <AMBIENTE>-web, <AMBIENTE>-site e <AMBIENTE>-garage. Todas as variáveis vêm do .env do ambiente.
#
#   Produção:    docker compose -p plataforma-cursos-producao -f compose.prod.yml up -d --wait
#   Homologação: docker compose -p plataforma-cursos-homolog -f compose.prod.yml -f compose.homolog.yml up -d --wait

# Configuração compartilhada pela API e pelo worker
x-app: &app
  DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:?defina POSTGRES_DB}
  # API e worker entram com o usuário da aplicação: só lê e grava dados (sem criar, alterar ou apagar
  # tabelas). O dono do schema (POSTGRES_USER) só é usado pelas migrations, no serviço migracao
  DB_USERNAME: ${POSTGRES_APP_USER:?defina POSTGRES_APP_USER}
  DB_PASSWORD: ${POSTGRES_APP_PASSWORD:?defina POSTGRES_APP_PASSWORD}
  SPRING_FLYWAY_ENABLED: "false"
  JWT_SEGREDO: ${JWT_SEGREDO:?defina JWT_SEGREDO}
  # Chave dos segredos do aplicativo autenticador (segundo fator dos administradores)
  TOTP_CHAVE: ${TOTP_CHAVE:?defina TOTP_CHAVE}
  AUTH_ORIGENS: ${AUTH_ORIGENS:?defina AUTH_ORIGENS}
  FRONT_ALUNO_URL: ${FRONT_ALUNO_URL:?defina FRONT_ALUNO_URL}
  S3_ENDPOINT: http://garage:3900
  S3_ENDPOINT_PUBLICO: ${S3_ENDPOINT_PUBLICO:?defina S3_ENDPOINT_PUBLICO}
  S3_BUCKET: ${S3_BUCKET:?defina S3_BUCKET}
  S3_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY}
  S3_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY}
  S3_CORS_ORIGENS: ${S3_CORS_ORIGENS:?defina S3_CORS_ORIGENS}
  SMTP_HOST: ${SMTP_HOST:?defina SMTP_HOST}
  SMTP_PORT: ${SMTP_PORT:-587}
  SMTP_USERNAME: ${SMTP_USERNAME:-}
  SMTP_PASSWORD: ${SMTP_PASSWORD:-}
  SMTP_AUTH: ${SMTP_AUTH:-true}
  SMTP_STARTTLS: ${SMTP_STARTTLS:-true}
  EMAIL_REMETENTE: ${EMAIL_REMETENTE:?defina EMAIL_REMETENTE}
  LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs
  # Log também em arquivo, no volume logs: um por dia, guardado por 180 dias ou 2 GB por serviço
  LOGGING_STRUCTURED_FORMAT_FILE: ecs
  # Alertas do worker por e-mail (jobs com erro, disco quase cheio, chargeback); em branco, desligados
  ALERTA_EMAIL: ${ALERTA_EMAIL:-}
  # Chave dos segredos dos meios de pagamento; sem ela, o painel não grava credenciais
  PAGAMENTOS_CHAVE: ${PAGAMENTOS_CHAVE:-}
  APP_PAGAMENTOS_RECONCILIACAO_ATIVA: ${PAGAMENTOS_RECONCILIACAO:-false}

# Logs do Docker com rotação: no máximo 5 arquivos de 10 MB por container
x-logging: &logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "5"

services:
  # Aplica as migrations com o dono do schema e dá ao usuário da aplicação só o acesso aos dados
  # (criado na primeira vez). Roda a cada deploy e termina; a API só sobe depois dele
  migracao:
    logging: *logging
    image: ${IMAGEM_API:-ghcr.io/celkecursos/plataforma-cursos-api}:${TAG:?defina TAG}
    command: ["migrar"]
    restart: "no"
    environment:
      <<: *app
      DB_USERNAME: ${POSTGRES_USER:?defina POSTGRES_USER}
      DB_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD}
      DB_APP_USERNAME: ${POSTGRES_APP_USER:?defina POSTGRES_APP_USER}
      DB_APP_PASSWORD: ${POSTGRES_APP_PASSWORD:?defina POSTGRES_APP_PASSWORD}
      SPRING_FLYWAY_ENABLED: "true"
      JAVA_TOOL_OPTIONS: -Xmx256m
    depends_on:
      postgres:
        condition: service_healthy

  api:
    logging: *logging
    image: ${IMAGEM_API:-ghcr.io/celkecursos/plataforma-cursos-api}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      <<: *app
      JAVA_TOOL_OPTIONS: -Xmx512m
      LOGGING_FILE_NAME: /var/log/plataforma-cursos/api.log
    volumes:
      - logs:/var/log/plataforma-cursos
    depends_on:
      migracao:
        condition: service_completed_successfully
      postgres:
        condition: service_healthy
      garage:
        condition: service_healthy
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-api
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 5s
      timeout: 3s
      start_period: 30s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 1G

  worker:
    logging: *logging
    image: ${IMAGEM_WORKER:-ghcr.io/celkecursos/plataforma-cursos-worker}:${TAG:?defina TAG}
    restart: unless-stopped
    # No deploy, o job em andamento tem 30 s para terminar; uma conversão interrompida volta para a fila
    # quando o lease vence (o worker o renova a cada minuto enquanto converte)
    stop_grace_period: 30s
    environment:
      <<: *app
      JAVA_TOOL_OPTIONS: -Xmx768m
      LOGGING_FILE_NAME: /var/log/plataforma-cursos/worker.log
    volumes:
      - logs:/var/log/plataforma-cursos
    depends_on:
      # O worker sobe depois da API (que sobe depois das migrations)
      api:
        condition: service_healthy
    healthcheck:
      # O worker regrava este arquivo a cada 10 s (não tem servidor HTTP para health check). A carência
      # cobre a partida do Spring com pouca CPU (meia CPU na homologação passa de um minuto)
      test: ["CMD-SHELL", "find /tmp/worker-batimento -mmin -1 | grep -q ."]
      interval: 10s
      timeout: 3s
      start_period: 120s
      retries: 3
    deploy:
      resources:
        limits:
          # A transcodificação (ffmpeg) não pode tomar a CPU toda da VPS
          cpus: "1.5"
          memory: 2G

  # Área do aluno (porta 8081) e ADM (8082), estáticos; a API é chamada no mesmo domínio (/api)
  web:
    logging: *logging
    image: ${IMAGEM_WEB:-ghcr.io/celkecursos/plataforma-cursos-web}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      # Origem dos vídeos e arquivos na Content-Security-Policy dos fronts
      MIDIA_ORIGEM: ${S3_ENDPOINT_PUBLICO:?defina S3_ENDPOINT_PUBLICO}
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-web
    deploy:
      resources:
        limits:
          memory: 64M

  # Site público (cursos.*): React renderizado no servidor a cada requisição, lendo a API do ambiente
  # pela rede interna (com cache curto em memória)
  site:
    logging: *logging
    image: ${IMAGEM_SITE:-ghcr.io/celkecursos/plataforma-cursos-site}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      API_URL: http://api:8080
      FRONT_ALUNO_URL: ${FRONT_ALUNO_URL:?defina FRONT_ALUNO_URL}
      # Em branco, deriva do FRONT_ALUNO_URL (aluno.celke.uno → cursos.celke.uno)
      SITE_URL: ${SITE_URL:-}
      SITE_INDEXAVEL: ${SITE_INDEXAVEL:-true}
      # Origem das capas (URLs pré-assinadas do Garage), liberada na Content-Security-Policy
      MIDIA_ORIGEM: ${S3_ENDPOINT_PUBLICO:?defina S3_ENDPOINT_PUBLICO}
    depends_on:
      api:
        condition: service_healthy
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-site
    deploy:
      resources:
        limits:
          memory: 256M

  postgres:
    logging: *logging
    image: postgres:18.6-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB:?defina POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER:?defina POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD}
    volumes:
      - postgres:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 1G

  garage:
    logging: *logging
    image: dxflrs/garage:v2.4.1
    restart: unless-stopped
    command: ["/garage", "server", "--single-node", "--default-bucket"]
    environment:
      GARAGE_RPC_SECRET: ${GARAGE_RPC_SECRET:?defina GARAGE_RPC_SECRET}
      GARAGE_ADMIN_TOKEN: ${GARAGE_ADMIN_TOKEN:?defina GARAGE_ADMIN_TOKEN}
      GARAGE_DEFAULT_BUCKET: ${S3_BUCKET:?defina S3_BUCKET}
      GARAGE_DEFAULT_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY}
      GARAGE_DEFAULT_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY}
    volumes:
      - ./garage/garage.toml:/etc/garage.toml:ro
      - garage:/var/lib/garage
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-garage
    healthcheck:
      test: ["CMD", "/garage", "status"]
      interval: 5s
      timeout: 5s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 768M

networks:
  default:
    name: plataforma-cursos-${AMBIENTE:?defina AMBIENTE}

volumes:
  postgres:
  garage:
  # Logs da API e do worker em arquivo (o log do Docker roda em poucos dias)
  logs:
`````

`web/Caddyfile`:

`````caddyfile
# Caddy interno do container web: só arquivos estáticos (aluno e ADM), sem TLS (o TLS e os headers comuns ficam no
# Caddy da VPS). MIDIA_ORIGEM é a origem pública do Garage (vídeos, capas, arquivos de apoio).
{
	admin off
	auto_https off
}

# Argumento: a pasta do app (aluno ou adm)
(spa) {
	root * /srv/{args[0]}
	encode zstd gzip
	header {
		-Server
		Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: {$MIDIA_ORIGEM}; media-src 'self' blob: {$MIDIA_ORIGEM}; connect-src 'self' {$MIDIA_ORIGEM}; font-src 'self' data:; worker-src 'self' blob:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
		X-Frame-Options DENY
	}

	# Arquivos com hash no nome: nunca mudam
	handle /assets/* {
		header Cache-Control "public, max-age=31536000, immutable"
		file_server
	}

	# Qualquer outra rota é do React Router: devolve o index.html, sempre revalidado
	handle {
		header Cache-Control "no-cache"
		try_files {path} /index.html
		file_server
	}
}

:8081 {
	import spa aluno
}

:8082 {
	import spa adm
}
`````

`web/Dockerfile`:

`````dockerfile
# Duas imagens a partir do mesmo build:
#   site (alvo `site`): site público em React renderizado no servidor (Node, porta 3000), que lê a
#                       API do próprio ambiente pela rede interna
#   web  (padrão):      Caddy interno com a área do aluno (8081) e o ADM (8082)
# O Caddy da VPS manda /api para a API, cursos.* para o site e o resto para o container web.

FROM node:24.11-bookworm-slim AS build
RUN apt-get update -q && apt-get install -y -q --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
# O lock guarda o kole-react como git+ssh; o repositório é público e é baixado por HTTPS
RUN git config --global url."https://github.com/".insteadOf "ssh://git@github.com/"
WORKDIR /web
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build
# O servidor do site só precisa das dependências de produção
RUN npm prune --omit=dev --no-audit --no-fund

# Site público: o servidor do React Router com o build do site
FROM node:24.19-alpine AS site
# Correções do sistema; npm e yarn saem da imagem (o servidor não instala nada)
RUN apk upgrade --no-cache \
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx \
        /opt/yarn-* /usr/local/bin/yarn /usr/local/bin/yarnpkg
WORKDIR /app
COPY --from=build /web/package.json ./
COPY --from=build /web/node_modules ./node_modules
COPY --from=build /web/dist/site ./dist/site
# O build grava a pasta dos arquivos do navegador relativa ao app (../dist/site/client): o servidor
# roda de /app/site
WORKDIR /app/site
USER node
ENV NODE_ENV=production PORT=3000
EXPOSE 3000
HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=20 \
    CMD wget -q -O /dev/null http://127.0.0.1:3000/saude || exit 1
CMD ["node", "../node_modules/@react-router/serve/bin.cjs", "../dist/site/server/index.js"]

FROM caddy:2.11.7-alpine AS web
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /web/dist/aluno /srv/aluno
COPY --from=build /web/dist/adm /srv/adm
EXPOSE 8081 8082
HEALTHCHECK --interval=5s --timeout=3s --retries=20 \
    CMD wget -q --spider http://127.0.0.1:8081/ && wget -q --spider http://127.0.0.1:8082/ || exit 1
`````

`web/adm/index.html`:

`````html
<!doctype html>
<html lang="pt-BR">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <link rel="icon" href="/favicon.ico" sizes="32x32" />
    <meta name="robots" content="noindex" />
    <title>Administração — Plataforma de Cursos</title>
  </head>
  <body>
    <div id="raiz"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
`````

`web/aluno/index.html`:

`````html
<!doctype html>
<html lang="pt-BR">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <link rel="icon" href="/favicon.ico" sizes="32x32" />
    <meta name="robots" content="noindex" />
    <title>Área do aluno — Plataforma de Cursos</title>
  </head>
  <body>
    <div id="raiz"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
`````

`web/e2e/README.md`:

`````markdown
# Testes E2E dos fronts (Playwright)

Testes ponta a ponta: dirigem um navegador real contra a **stack de e2e rodando de verdade** — Caddy,
API, worker (fila, e-mail e conversão de vídeo), fronts, site público, PostgreSQL, Garage e Mailpit,
com as mesmas imagens que vão para produção. Não há mock de API: dependem dos containers no ar.

**Nunca contra um servidor.** A stack de e2e é descartável (`down -v` apaga tudo) e as contas do seed só
existem nela; `support/config.ts` recusa qualquer endereço que não seja `*.localhost`.

**Neste documento:** [Começo rápido](#começo-rápido) · [O que é testado aqui](#o-que-é-e-o-que-não-é-testado-aqui) ·
[Pré-requisitos](#pré-requisitos-uma-vez-por-máquina) · [Como rodar](#como-rodar) ·
[Contas e sessão](#contas-e-sessão) · [Dado de teste e limpeza](#dado-de-teste-e-limpeza) ·
[Os specs](#os-specs) · [Lighthouse do site](#lighthouse-do-site) · [Manutenção](#manutenção)

## Começo rápido

Depois dos pré-requisitos (uma vez por máquina, abaixo), na **raiz do repositório**:

**1. Subir a stack de e2e** (a primeira vez constrói as imagens e leva alguns minutos):

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
```

**2. Rodar os testes**, dentro de `web/`:

```bash
cd web
npm run e2e                                  # todos (aluno, ADM e site)
E2E_SLOWMO=500 npm run e2e:headed -- e2e/adm/ # só o ADM, com a janela aberta e em câmera lenta
```

**3. Derrubar a stack** quando terminar (apaga banco, vídeos e e-mails):

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- Troque `e2e/adm/` por `e2e/aluno/`, `e2e/site/` ou um arquivo (`e2e/aluno/compra.spec.ts`): apontar
  para uma pasta roda todos os specs dela.
- `:headed` e `E2E_SLOWMO` servem só para **acompanhar na tela**; para rodar rápido, sem janela,
  use `npm run e2e`.
- Mudou o backend ou os fronts? Suba a stack de novo com o `--build` para os testes verem o código novo.

O resto deste documento é referência: não é preciso ler tudo para rodar o primeiro teste.

## O que é e o que não é testado aqui

| Camada             | Cobre                                                                                                       | Onde                                                                                                           |
| :----------------- | :---------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------- |
| Unidade (front)    | funções puras, componentes, cliente HTTP                                                                    | `{aluno,adm,compartilhado}/src/**/*.test.ts(x)`, `site/app/**/*.test.ts` e `scripts/**/*.test.ts` — `npm test` |
| Backend            | regra, rotas, segurança, banco, fila                                                                        | `backend/src/test` — `./mvnw verify`                                                                           |
| Jornadas da API    | API pelo Caddy, worker, Garage e e-mail, sem navegador                                                      | `backend/src/e2e` — `./mvnw verify -Pe2e`                                                                      |
| **E2E dos fronts** | **telas de verdade**: navegação, formulários, mensagens, sessão por cookie, vídeo tocando, site renderizado | **aqui**                                                                                                       |

**Regra de negócio não sobe para cá.** Se o caso é de cálculo, validação ou permissão, ele tem teste no
backend ou de unidade; aqui fica o que só o navegador prova (a tela mostra o erro certo, o vídeo toca, o
cookie mantém a sessão, o link do e-mail funciona).

## Pré-requisitos (uma vez por máquina)

1. Docker com o Docker Compose (a stack sobe com ele).
2. Node.js 24 e as dependências: `cd web && npm ci`
3. O navegador do Playwright: `npx playwright install chromium`
4. As portas **8088** (Caddy) e **8026** (Mailpit) livres.

Os navegadores atuais resolvem `*.localhost` sozinhos: não é preciso mexer no `hosts`.

## Como rodar

Todos os comandos dentro de `web/`, com a stack de e2e no ar.

**Escolher o que rodar** — uma pasta, um arquivo, um teste pelo nome ou um projeto:

```bash
npm run e2e                                    # tudo
npm run e2e -- e2e/aluno/                      # só a área do aluno
npm run e2e -- e2e/aluno/compra.spec.ts        # só um spec
npm run e2e -- -g "reembolso"                  # só os testes cujo nome tem o texto
npm run e2e -- --project=site                  # projeto: aluno, adm ou site
```

**Ver o teste rodando** — combine com qualquer seleção acima:

```bash
npm run e2e:ui                                 # melhor opção: linha do tempo, volta no tempo, rede
npm run e2e:headed                             # janela maximizada
E2E_SLOWMO=500 npm run e2e:headed              # câmera lenta (ms entre ações): dá para ler a tela
npm run e2e:debug -- e2e/adm/turma.spec.ts     # passo a passo, com o Inspector
```

**Depois de rodar:**

```bash
npm run e2e:report                             # abre o último relatório HTML
npm run e2e:trace test-results/<pasta>/trace.zip   # revê uma falha gravada (o relatório mostra o caminho)
```

Quando um teste falha, ficam em `test-results/` o trace, o vídeo e a captura da tela. A janela só
maximiza com `--headed`; na execução normal o viewport é fixo (Desktop Chrome), igual em qualquer
máquina.

Os testes rodam **um de cada vez** (`workers: 1`): escrevem no mesmo banco e gastam o mesmo limite de
requisições por IP.

**Variáveis** (padrões de `infra/e2e.env`; só mude se subir a stack com outras portas):

| Variável          | Padrão                             | Uso                                                 |
| :---------------- | :--------------------------------- | :-------------------------------------------------- |
| `E2E_ALUNO_URL`   | `http://aluno.localhost:8088`      | área do aluno                                       |
| `E2E_ADM_URL`     | `http://adm.localhost:8088`        | ADM                                                 |
| `E2E_SITE_URL`    | `http://cursos.localhost:8088`     | site público                                        |
| `E2E_MAILPIT_URL` | `http://localhost:8026`            | e-mails (links de confirmação e de senha)           |
| `E2E_SENHA_SEED`  | `123456A#b123`                     | senha das contas do seed                            |
| `E2E_TOTP_SEED`   | `JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP` | segredo do aplicativo autenticador do admin do seed |
| `E2E_SLOWMO`      | `0`                                | câmera lenta, em ms entre ações                     |

## Contas e sessão

- **Seed da stack** (criado pela API no perfil `e2e`): `admin@celke.com.br` e
  `aluno@celke.com.br`, senha `123456A#b123`. São **só leitura**: nenhum teste troca a senha,
  sai ou mexe no perfil delas.
- **Segundo fator do admin**: o admin do seed já tem o aplicativo autenticador cadastrado com o segredo
  de `SEED_E2E_TOTP`; `support/totp.ts` calcula o código como o celular, e as fixtures e o
  `confirmarSegundoFator` (`support/telas.ts`) passam pela segunda etapa. Na stack de e2e o mesmo código
  vale mais de uma vez (os testes entram várias vezes no mesmo intervalo de 30 s).
- **Sessão nova por teste**: as fixtures `comoAdmin` e `comoAluno` (`support/sessoes.ts`) entram pela API
  no contexto do navegador; ao abrir a página, o app renova a sessão pelo cookie `HttpOnly`. Não há
  sessão gravada: o refresh token é rotativo e um token reutilizado derruba a sessão inteira.
- **Conta própria**: teste que troca senha, sai, compra ou mexe no perfil cria um aluno novo
  (`support/contas.ts`), confirmado pelo link que chega no Mailpit.
- **Termos**: se outra jornada publicou termos, `aceitarTermosSePedir` (`support/telas.ts`) aceita antes
  de seguir.

## Dado de teste e limpeza

- Cursos, contas e ofertas levam um carimbo único (`pw<tempo><sequência>`, e-mails
  `pw...@teste.celke.uno`): execuções seguidas não colidem, mesmo sem limpar a stack.
- Os testes **não apagam** o que criam; o lixo some com o `down -v` da stack. Para começar do zero:
  `down -v` e `up` de novo.
- O pagamento não sai para a internet: a ida ao `pay.hotmart.com` é interceptada (`page.route`) e a
  notificação da Hotmart é enviada pelo próprio teste (`support/pagamentos.ts`, a partir das fixtures do
  backend).

## Os specs

**ADM** (`e2e/adm/`)

- **`acesso.spec.ts`** — entrada do admin com o código do aplicativo, volta para onde ia, código errado,
  senha errada; aluno barrado na tela e na API do ADM.
- **`usuarios.spec.ts`** — bloqueio de um aluno (não entra mais) e desbloqueio; o admin sem as ações na
  própria linha.
- **`conteudo.spec.ts`** — cursos (validação, slug repetido, capa em formato errado, exclusão com
  turmas, filtro), conteúdo da turma (reordenar, inativar, exclusão com aulas) e aula (vídeo recusado na
  tela e pela API, edição, arquivo de apoio recusado).
- **`curso-completo.spec.ts`** — cria um curso completo e envia um vídeo curto até ficar "pronto"
  (o worker converte de verdade).
- **`turma.spec.ts`** — matrículas (matricular, período, cancelar, e-mail sem conta, turma encerrada),
  moderação do fórum e configurações da turma (certificado, exclusão).
- **`termos.spec.ts`** — texto vazio barrado; publicação de uma versão nova.
- **`pagamentos.spec.ts`** — cadastra a Hotmart (validação, credencial errada recusada), ativa e vende
  uma turma; evento de oferta não cadastrada é ignorado e reprocessado depois; o pedido aparece no
  painel.
- **`auditoria.spec.ts`** — a trilha mostra quem criou o curso, de onde e os campos gravados.

**Aluno** (`e2e/aluno/`)

- **`entrar.spec.ts`** — login, senha errada, e-mail inexistente com a mesma resposta, volta para a
  página privada, redirecionamento externo ignorado, 404.
- **`cadastro.spec.ts`** — cadastro confirmado pelo link do e-mail; validações; e-mail já cadastrado não
  é revelado; link de confirmação inválido ou sem token.
- **`senha.spec.ts`** — recuperação de senha pelo e-mail e os casos de link inválido.
- **`sessao.spec.ts`** — a sessão volta pelo cookie ao abrir e recarregar; sair encerra; cookie de
  refresh reutilizado derruba a família.
- **`estudo.spec.ts`** — do curso à aula: o vídeo toca até o fim, o progresso sobe, retomada, vista e
  revisar, anterior e próxima, arquivo de apoio, fórum da aula, certificado (bloqueado → emitido →
  validado); sem matrícula, matrícula futura, arquivar.
- **`perfil.spec.ts`** — troca de nome, foto (formato errado recusado) e senha.
- **`privacidade.spec.ts`** — baixar a cópia dos dados (JSON com a conta) e excluir a conta (validação,
  e-mail de confirmação, a conta não entra mais).
- **`compra.spec.ts`** — compra pelo botão do site com a Hotmart interceptada: a página mostra curso e
  valor e só abre o pedido no "Ir para o pagamento"; aprovada (o curso aparece), reembolso (o curso
  some), boleto aguardando, e o "Comprar" sem login que volta para a compra depois de entrar.

**Site** (`e2e/site/`)

- **`site.spec.ts`** — home e catálogo com o curso publicado (rascunho não aparece), página do curso com
  meta tags, capa, 404, rotas da API fora das públicas barradas, sitemap e robots, validação de
  certificado e termos publicados, busca do catálogo no navegador. O site guarda as leituras da API por
  1 s na stack de e2e (`SITE_CACHE_SEGUNDOS`); os testes recarregam até a mudança aparecer.

**Apoio** (`e2e/support/`): `config.ts` (endereços e trava), `api.ts` (montar dados pela API),
`sessoes.ts` (fixtures de sessão), `contas.ts` e `mailpit.ts` (contas confirmadas pelo e-mail),
`telas.ts` (login pela tela, segundo fator, termos, escolher opção do `CampoSelect`), `totp.ts` (código
do aplicativo autenticador), `amostras.ts` (arquivos de amostra), `pagamentos.ts` (Hotmart e
notificações).

## Lighthouse do site

Com a stack no ar, mede desempenho, acessibilidade, boas práticas e SEO das páginas do site (desktop) e
falha abaixo de 90, 95, 95 e 90:

```bash
npm run lighthouse
```

Precisa de pelo menos um curso publicado (rode antes o `npm run e2e -- e2e/site/`).

## Manutenção

- Seletores pelo que o usuário vê: campo pelo rótulo (`getByLabel`, `getByRole('textbox', { name })`),
  botão e link pelo nome acessível (`getByRole`), linha da tabela pelo dado que o próprio teste criou.
  **Nunca** por posição nem por classe interna da `@celke/kole-react` (`.lp-*`): ela muda numa
  atualização da biblioteca.
- O `CampoSelect` da biblioteca não dá nome acessível ao combobox: use `escolher(page, rótulo, opção)`.
- Se a tela mudar um rótulo por decisão de UX, **conserte o teste** — não volte o rótulo para acomodar a
  automação.
- Espera sempre por condição (`expect(...).toBeVisible()`, `expect.poll`, `toPass`), nunca por tempo
  fixo.
`````

`web/e2e/site/site.spec.ts`:

`````ts
import { expect, request as criarRequest, test, type Page } from '@playwright/test';
import { PNG } from '../support/amostras.ts';
import {
  clienteAluno,
  clienteApi,
  montarCurso,
  type ClienteApi,
  type CursoMontado,
} from '../support/api.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA, stamp } from '../support/dados.ts';

/**
 * Site público em React renderizado no servidor a partir da API (na stack de e2e, com cache de 1 s).
 * Um curso publicado pelo ADM aparece sozinho; rascunho nunca aparece.
 */
let adm: ClienteApi;
let publicado: CursoMontado;
let slug: string;
let rascunho: string;

test.beforeAll(async () => {
  const contexto = await criarRequest.newContext();
  adm = await clienteApi(contexto);
  publicado = await montarCurso(adm);
  const curso = await adm.get<{ slug: string }>(`/api/adm/cursos/${publicado.cursoId}`);
  slug = curso.slug;
  await adm.patch(`/api/adm/cursos/${publicado.cursoId}/status`, { status: 'PUBLICADO' });
  rascunho = `Rascunho ${stamp()}`;
  await adm.post('/api/adm/cursos', { titulo: rascunho, resumo: 'Não publicado.' });
});

/** Recarrega até o site mostrar o que acabou de mudar na API (o parâmetro fura o cache de 60 s do HTML). */
async function aguardarGeracao(page: Page, caminho: string, texto: string | RegExp) {
  await expect(async () => {
    await page.goto(`${caminho}?v=${Date.now()}`);
    await expect(page.getByText(texto).first()).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 30_000 });
}

test('a home e o catálogo mostram o curso publicado, não o rascunho', async ({ page }) => {
  await aguardarGeracao(page, '/cursos/', publicado.titulo);
  await expect(page.getByText(rascunho)).toHaveCount(0);

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Aprenda no seu ritmo' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Criar conta' })).toHaveAttribute(
    'href',
    'http://aluno.localhost:8088/cadastro',
  );
});

test('a busca do catálogo filtra os cursos no navegador', async ({ page }) => {
  await aguardarGeracao(page, '/cursos/', publicado.titulo);
  const busca = page.getByRole('searchbox', { name: 'Buscar curso' });
  await busca.fill(publicado.titulo.toUpperCase());
  await expect(page.getByRole('link', { name: new RegExp(publicado.titulo) })).toBeVisible();
  await busca.fill('nenhum curso tem este nome');
  await expect(page.getByText('Nenhum curso encontrado para essa busca.')).toBeVisible();
  await expect(page.getByRole('link', { name: new RegExp(publicado.titulo) })).toHaveCount(0);
});

test('página do curso com o conteúdo e as meta tags', async ({ page }) => {
  await aguardarGeracao(page, '/cursos/', publicado.titulo);
  await page.getByRole('link', { name: new RegExp(publicado.titulo) }).click();
  await expect(page).toHaveURL(`/cursos/${slug}/`);
  await page.goto(`/cursos/${slug}/?v=${Date.now()}`);
  await expect(page.getByRole('heading', { level: 1, name: publicado.titulo })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Módulo 1' })).toBeVisible();
  await expect(page.getByText('Aula 1')).toBeVisible();

  await expect(page).toHaveTitle(`${publicado.titulo} — Plataforma de Cursos`);
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute(
    'href',
    `http://cursos.localhost:8088/cursos/${slug}/`,
  );
  await expect(page.locator('meta[property="og:title"]')).toHaveAttribute(
    'content',
    publicado.titulo,
  );
  const dados = JSON.parse(
    (await page.locator('script[type="application/ld+json"]').textContent())!,
  );
  expect(dados['@type']).toBe('Course');
});

test('a capa enviada pelo ADM aparece no site', async ({ page }) => {
  const contexto = await criarRequest.newContext();
  const envio = await adm.post<{ chave: string; url: string; contentType: string }>(
    `/api/adm/cursos/${publicado.cursoId}/capa/envio`,
    { contentType: PNG.mimeType, tamanho: PNG.buffer.length },
  );
  await contexto.put(envio.url, {
    data: PNG.buffer,
    headers: { 'Content-Type': envio.contentType },
  });
  await contexto.dispose();
  await adm.put(`/api/adm/cursos/${publicado.cursoId}/capa`, { chave: envio.chave });

  await page.goto(`/cursos/${slug}/`);
  const capa = page.getByRole('img', { name: `Capa do curso ${publicado.titulo}` });
  await expect(async () => {
    await page.goto(`/cursos/${slug}/?v=${Date.now()}`);
    await expect(capa).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 30_000 });
  await expect
    .poll(() => capa.evaluate((img: HTMLImageElement) => img.naturalWidth))
    .toBeGreaterThan(0);
});

test('curso que não existe mostra a página 404 do site', async ({ page }) => {
  const resposta = await page.goto('/cursos/curso-que-nao-existe/');
  expect(resposta!.status()).toBe(404);
  await expect(page.getByRole('heading', { name: 'Página não encontrada' })).toBeVisible();
});

test('rotas da API fora das públicas não passam pelo site', async ({ request }) => {
  expect((await request.post('/api/auth/login', { data: {} })).status()).toBe(404);
  expect((await request.get('/api/aluno/cursos')).status()).toBe(404);
  expect((await request.get('/api/cursos')).status()).toBe(200);
});

test('sitemap e robots', async ({ request }) => {
  await expect(async () => {
    const mapa = await (await request.get('/sitemap.xml')).text();
    expect(mapa).toContain(`<loc>http://cursos.localhost:8088/cursos/${slug}/</loc>`);
  }).toPass({ timeout: 30_000 });
  expect(await (await request.get('/robots.txt')).text()).toContain(
    'Sitemap: http://cursos.localhost:8088/sitemap.xml',
  );
});

test.describe('Validar certificado', () => {
  test('código válido mostra os dados; código errado avisa', async ({ page, request }) => {
    // Aluno conclui o curso e emite o certificado pela API
    const email = await alunoConfirmado(request);
    await adm.post(`/api/adm/turmas/${publicado.turmaId}/matriculas`, {
      email,
      formaPagamento: 'CORTESIA',
      inicio: new Date(Date.now() - 86_400_000).toISOString(),
    });
    const aluno = await clienteAluno(request, email, SENHA_NOVA);
    await aluno.patch(`/api/aluno/aulas/${publicado.aulaId}/progresso`, { vista: true });
    const certificado = await aluno.post<{ codigo: string }>(
      `/api/aluno/turmas/${publicado.turmaId}/certificado`,
      {
        nomeCompleto: 'Pessoa Validada da Silva',
      },
    );

    await page.goto('/certificados/validar/');
    await page.getByRole('button', { name: 'Validar' }).click();
    await expect(page.getByText('Informe o código do certificado.')).toBeVisible();

    await page.getByRole('textbox', { name: 'Código do certificado' }).fill('CODIGO-INEXISTENTE');
    await page.getByRole('button', { name: 'Validar' }).click();
    await expect(page.getByText('Certificado não encontrado.')).toBeVisible();

    await page.getByRole('textbox', { name: 'Código do certificado' }).fill(certificado.codigo);
    await page.getByRole('button', { name: 'Validar' }).click();
    await expect(page.getByText('Certificado válido.')).toBeVisible();
    await expect(page.getByText('Pessoa Validada da Silva')).toBeVisible();
    await expect(page).toHaveURL(`/certificados/validar/?codigo=${certificado.codigo}`);

    // Link direto (impresso no certificado) já valida ao abrir
    await page.goto(`/certificados/validar/?codigo=${certificado.codigo}`);
    await expect(page.getByText('Certificado válido.')).toBeVisible();
  });
});

test('termos de uso publicados aparecem no site', async ({ page }) => {
  await page.goto('/termos/');
  await expect(page.getByRole('heading', { level: 1, name: 'Termos de uso' })).toBeVisible();
  await expect(
    page.getByText(/Versão \d+, publicada em/).or(page.getByText('Em breve.')),
  ).toBeVisible();
});
`````

`web/eslint.config.js`:

`````js
import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'playwright-report', 'test-results'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: { ecmaVersion: 2023, globals: { ...globals.browser, ...globals.node } },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      '@typescript-eslint/no-explicit-any': 'error',
      // Chamadas HTTP só pelo cliente de compartilhado/src/api (token, refresh e erros num lugar só)
      'no-restricted-globals': [
        'error',
        { name: 'fetch', message: 'Use o cliente da API (@compartilhado/api/cliente).' },
      ],
    },
  },
  {
    // O site (renderizado no servidor) e os scripts de apoio rodam no Node: buscam a API direto
    files: ['compartilhado/src/api/**', 'e2e/**', 'site/**', 'scripts/**', '**/*.test.{ts,tsx}'],
    rules: { 'no-restricted-globals': 'off' },
  },
);
`````

`web/package.json`:

`````json
{
  "name": "plataforma-cursos-web",
  "private": true,
  "version": "0.1.0",
  "description": "Frontends da Plataforma de Cursos: área do aluno e ADM (React + @celke/kole-react) e o site público (React com renderização no servidor).",
  "type": "module",
  "scripts": {
    "dev:aluno": "vite --config aluno/vite.config.ts",
    "dev:adm": "vite --config adm/vite.config.ts",
    "dev:site": "react-router dev site",
    "build": "tsc -b && vite build --config aluno/vite.config.ts && vite build --config adm/vite.config.ts && react-router build site",
    "typecheck": "tsc -b",
    "lint": "eslint .",
    "format": "prettier --write .",
    "format:check": "prettier --check .",
    "test": "vitest run",
    "e2e": "playwright test",
    "e2e:headed": "playwright test --headed",
    "e2e:ui": "playwright test --ui",
    "e2e:debug": "playwright test --debug",
    "e2e:report": "playwright show-report",
    "e2e:trace": "playwright show-trace",
    "lighthouse": "node e2e/lighthouse.ts",
    "cursos-exemplo": "node scripts/cursos-exemplo.ts",
    "start:site": "cd site && react-router-serve ../dist/site/server/index.js"
  },
  "dependencies": {
    "@celke/kole-react": "github:celkecursos/kole-react#fc87d5c4409e1e65e8c571321c08d56871d2a4d3",
    "@react-router/node": "^8.5.0",
    "@react-router/serve": "^8.5.0",
    "hls.js": "^1.7.3",
    "isbot": "^5.2.2",
    "lucide-react": "^1.61.0",
    "qrcode": "^1.5.4",
    "react": "^19.3.1",
    "react-dom": "^19.3.1",
    "react-router": "^8.5.0"
  },
  "devDependencies": {
    "@eslint/js": "^10.0.1",
    "@playwright/test": "^1.64.1",
    "@react-router/dev": "^8.5.0",
    "@testing-library/jest-dom": "^7.0.1",
    "@testing-library/react": "^16.3.3",
    "@testing-library/user-event": "^14.6.8",
    "@types/node": "^24.13.3",
    "@types/qrcode": "^1.5.6",
    "@types/react": "^19.2.18",
    "@types/react-dom": "^19.2.7",
    "@vitejs/plugin-react": "^6.1.2",
    "eslint": "^10.13.0",
    "eslint-plugin-react-hooks": "^7.1.1",
    "globals": "^17.13.0",
    "jsdom": "^30.1.3",
    "lighthouse": "^13.5.0",
    "prettier": "^3.9.10",
    "typescript": "~6.0.3",
    "typescript-eslint": "^8.72.0",
    "vite": "^8.3.5",
    "vitest": "^5.0.5"
  },
  "engines": {
    "node": ">=22"
  }
}
`````

`web/tsconfig.json`:

`````json
{
  "compilerOptions": {
    "target": "ES2023",
    "lib": ["ES2023", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "noUncheckedIndexedAccess": true,
    "verbatimModuleSyntax": true,
    "isolatedModules": true,
    "skipLibCheck": true,
    "noEmit": true,
    "allowImportingTsExtensions": true,
    "types": ["vite/client", "node"],
    "paths": {
      "@compartilhado/*": ["./compartilhado/src/*"]
    }
  },
  "include": [
    "aluno",
    "adm",
    "compartilhado",
    "site",
    "scripts",
    "e2e",
    "playwright.config.ts",
    "vitest.config.ts",
    "vite.base.ts"
  ]
}
`````

`web/vitest.config.ts`:

`````ts
import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@compartilhado': fileURLToPath(new URL('./compartilhado/src', import.meta.url)) },
  },
  test: {
    environment: 'jsdom',
    include: [
      '{aluno,adm,compartilhado}/src/**/*.test.{ts,tsx}',
      'site/app/**/*.test.ts',
      'scripts/**/*.test.ts',
    ],
    setupFiles: ['./compartilhado/src/testes/configuracao.ts'],
    css: false,
  },
});
`````

### Arquivos novos

`.gitignore`:

`````text
# Segredos
.env
.env.*
!.env.example

# Java / Maven
target/
*.class
*.log

# Node
node_modules/
dist/
.react-router/
coverage/
playwright-report/
test-results/

# IDE / sistema
.idea/
.vscode/
*.iml
.DS_Store
`````

`web/scripts/cursos-exemplo.test.ts`:

`````ts
import { inflateSync } from 'node:zlib';
import { describe, expect, it } from 'vitest';
import { capa } from './cursos-exemplo.ts';

describe('capa', () => {
  it('gera um PNG RGB com o tamanho pedido e o degradê entre as duas cores', () => {
    const png = capa(['#000000', '#ffffff'], 40, 20);
    expect(png.subarray(0, 8)).toEqual(
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    );
    expect(png.toString('ascii', 12, 16)).toBe('IHDR');
    expect(png.readUInt32BE(16)).toBe(40);
    expect(png.readUInt32BE(20)).toBe(20);
    expect(png[25]).toBe(2);

    const tamanhoIdat = png.readUInt32BE(33);
    expect(png.toString('ascii', 37, 41)).toBe('IDAT');
    const pixels = inflateSync(png.subarray(41, 41 + tamanhoIdat));
    expect(pixels).toHaveLength((40 * 3 + 1) * 20);
    // Canto superior esquerdo fica mais escuro que o inferior direito
    const ultimaLinha = 19 * (40 * 3 + 1);
    expect(pixels[1]!).toBeLessThan(pixels[ultimaLinha + 1 + 39 * 3]!);
  });
});
`````

`web/scripts/cursos-exemplo.ts`:

`````ts
/**
 * Cria cursos de exemplo publicados (com turma ativa, módulos, aulas e capa) pela API do ADM, para ver
 * o site e a área do aluno com conteúdo. Pede o e-mail, a senha e o código do aplicativo autenticador
 * de um administrador. Pode rodar de novo: curso que já existe (mesmo endereço) é pulado.
 *
 *   npm run cursos-exemplo -- https://homolog-adm.celke.uno
 *   npm run cursos-exemplo -- http://adm.localhost:8088   (stack de e2e)
 */
import { stdin, stdout } from 'node:process';
import { createInterface } from 'node:readline/promises';
import { crc32, deflateSync } from 'node:zlib';

interface Exemplo {
  slug: string;
  titulo: string;
  resumo: string;
  descricao: string;
  cores: [string, string];
  cargaHoras: number;
  modulos: { titulo: string; aulas: string[] }[];
}

const EXEMPLOS: Exemplo[] = [
  {
    slug: 'java-do-zero-ao-avancado',
    titulo: 'Java do zero ao avançado',
    resumo: 'Aprenda a programar em Java, da lógica à orientação a objetos, com projetos práticos.',
    descricao:
      'Um curso completo para quem quer começar a programar com uma das linguagens mais usadas no mercado.\n\nVocê sai do primeiro programa até coleções, tratamento de erros e testes, sempre com exercícios para fixar.',
    cores: ['#1f5fe0', '#6a3df0'],
    cargaHoras: 40,
    modulos: [
      {
        titulo: 'Primeiros passos',
        aulas: ['Instalando o JDK e a IDE', 'Seu primeiro programa', 'Variáveis e tipos'],
      },
      {
        titulo: 'Lógica de programação',
        aulas: ['Condicionais', 'Laços de repetição', 'Métodos', 'Exercícios comentados'],
      },
      {
        titulo: 'Orientação a objetos',
        aulas: ['Classes e objetos', 'Herança e interfaces', 'Coleções', 'Projeto final'],
      },
    ],
  },
  {
    slug: 'react-com-typescript-na-pratica',
    titulo: 'React com TypeScript na prática',
    resumo: 'Construa interfaces modernas com componentes, hooks e tipagem do começo ao fim.',
    descricao:
      'Do componente ao projeto publicado: estado, efeitos, formulários, rotas e chamadas à API, com TypeScript em tudo.\n\nIdeal para quem já conhece JavaScript e quer dar o próximo passo.',
    cores: ['#0ea5b7', '#1f5fe0'],
    cargaHoras: 30,
    modulos: [
      {
        titulo: 'Fundamentos',
        aulas: ['Criando o projeto com Vite', 'Componentes e props', 'Estado com useState'],
      },
      {
        titulo: 'Aplicação real',
        aulas: ['Rotas', 'Formulários', 'Consumindo uma API', 'Testes com Vitest'],
      },
      { titulo: 'Publicação', aulas: ['Build de produção', 'Deploy'] },
    ],
  },
  {
    slug: 'excel-para-o-dia-a-dia',
    titulo: 'Excel para o dia a dia',
    resumo: 'Planilhas, fórmulas, gráficos e tabelas dinâmicas para ganhar tempo no trabalho.',
    descricao:
      'Aprenda a organizar dados e automatizar tarefas repetitivas com as funções que mais aparecem no dia a dia.\n\nCada aula traz uma planilha de apoio para você praticar.',
    cores: ['#16a34a', '#0f766e'],
    cargaHoras: 16,
    modulos: [
      { titulo: 'Básico', aulas: ['Conhecendo a planilha', 'Formatação', 'Fórmulas simples'] },
      { titulo: 'Funções', aulas: ['SE e SOMASE', 'PROCX', 'Datas e textos'] },
      { titulo: 'Análise', aulas: ['Gráficos', 'Tabelas dinâmicas', 'Painel de indicadores'] },
    ],
  },
  {
    slug: 'marketing-digital-para-iniciantes',
    titulo: 'Marketing digital para iniciantes',
    resumo: 'Redes sociais, conteúdo e anúncios para atrair clientes pela internet.',
    descricao:
      'Entenda como planejar a presença on-line de um negócio, produzir conteúdo que engaja e medir os resultados.\n\nSem jargão: exemplos reais e um plano de ação ao final.',
    cores: ['#f7931e', '#e11d48'],
    cargaHoras: 20,
    modulos: [
      { titulo: 'Planejamento', aulas: ['Público e persona', 'Posicionamento', 'Metas'] },
      { titulo: 'Conteúdo', aulas: ['Calendário editorial', 'Instagram', 'Vídeos curtos'] },
      { titulo: 'Resultados', aulas: ['Anúncios', 'Métricas', 'Plano de ação'] },
    ],
  },
  {
    slug: 'fotografia-com-o-celular',
    titulo: 'Fotografia com o celular',
    resumo: 'Composição, luz e edição para fotos profissionais usando só o smartphone.',
    descricao:
      'Descubra como tirar fotos melhores com o equipamento que você já tem no bolso.\n\nVocê pratica composição, aproveita a luz natural e aprende a editar no próprio celular.',
    cores: ['#7c3aed', '#db2777'],
    cargaHoras: 12,
    modulos: [
      { titulo: 'O olhar', aulas: ['Regra dos terços', 'Linhas e enquadramento', 'Perspectiva'] },
      { titulo: 'Luz', aulas: ['Luz natural', 'Contraluz', 'Fotos à noite'] },
      { titulo: 'Edição', aulas: ['Ajustes básicos', 'Cor e estilo'] },
    ],
  },
  {
    slug: 'ingles-para-viagens',
    titulo: 'Inglês para viagens',
    resumo: 'Frases e situações para se virar no aeroporto, no hotel e no restaurante.',
    descricao:
      'Um curso direto ao ponto para quem vai viajar e quer se comunicar com confiança.\n\nCada módulo simula uma situação real, com vocabulário, pronúncia e diálogos.',
    cores: ['#0b1a36', '#1f5fe0'],
    cargaHoras: 10,
    modulos: [
      { titulo: 'No aeroporto', aulas: ['Check-in', 'Imigração', 'Perguntando o caminho'] },
      { titulo: 'No hotel', aulas: ['Reserva', 'Pedidos e problemas'] },
      { titulo: 'Passeios', aulas: ['No restaurante', 'Compras', 'Emergências'] },
    ],
  },
];

// ---------- capa: PNG de 1280 × 720 com degradê, sem dependências ----------

function rgb(hex: string): [number, number, number] {
  const n = Number.parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function bloco(tipo: string, dados: Buffer): Buffer {
  const tamanho = Buffer.alloc(4);
  tamanho.writeUInt32BE(dados.length);
  const tipoEDados = Buffer.concat([Buffer.from(tipo, 'ascii'), dados]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(tipoEDados));
  return Buffer.concat([tamanho, tipoEDados, crc]);
}

/** Degradê diagonal entre as duas cores, com dois círculos claros e suaves. */
export function capa([de, ate]: [string, string], largura = 1280, altura = 720): Buffer {
  const a = rgb(de);
  const b = rgb(ate);
  const linhas = Buffer.alloc((largura * 3 + 1) * altura);
  const circulos = [
    { x: largura * 0.82, y: altura * 0.18, r: altura * 0.55, forca: 0.22 },
    { x: largura * 0.12, y: altura * 0.95, r: altura * 0.45, forca: 0.12 },
  ];
  for (let y = 0; y < altura; y++) {
    const inicio = y * (largura * 3 + 1);
    linhas[inicio] = 0;
    for (let x = 0; x < largura; x++) {
      const t = (x / largura) * 0.6 + (y / altura) * 0.4;
      let luz = 0;
      for (const c of circulos) {
        const d = Math.hypot(x - c.x, y - c.y) / c.r;
        if (d < 1) luz += (1 - d) * (1 - d) * c.forca;
      }
      for (let canal = 0; canal < 3; canal++) {
        const base = a[canal]! + (b[canal]! - a[canal]!) * t;
        linhas[inicio + 1 + x * 3 + canal] = Math.min(255, Math.round(base + (255 - base) * luz));
      }
    }
  }
  const cabecalho = Buffer.alloc(13);
  cabecalho.writeUInt32BE(largura, 0);
  cabecalho.writeUInt32BE(altura, 4);
  cabecalho[8] = 8; // bits por canal
  cabecalho[9] = 2; // RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    bloco('IHDR', cabecalho),
    bloco('IDAT', deflateSync(linhas, { level: 9 })),
    bloco('IEND', Buffer.alloc(0)),
  ]);
}

// ---------- API ----------

let linhas: AsyncIterator<string> | undefined;

/** Uma resposta. No terminal, a senha (`oculto`) é lida tecla a tecla, sem eco; fora dele (entrada
 * redirecionada), cada resposta é uma linha. */
async function perguntar(texto: string, oculto = false): Promise<string> {
  if (!stdin.isTTY) {
    stdout.write(`${texto}\n`);
    linhas ??= createInterface({ input: stdin, terminal: false })[Symbol.asyncIterator]();
    return ((await linhas.next()).value ?? '').trim();
  }
  if (!oculto) {
    const rl = createInterface({ input: stdin, output: stdout });
    const resposta = await rl.question(texto);
    rl.close();
    return resposta.trim();
  }
  stdout.write(texto);
  stdin.setRawMode(true);
  stdin.resume();
  return new Promise((resolver) => {
    let valor = '';
    const tecla = (dado: Buffer) => {
      for (const c of dado.toString('utf8')) {
        if (c === '\r' || c === '\n') {
          stdin.setRawMode(false);
          stdin.pause();
          stdin.off('data', tecla);
          stdout.write('\n');
          resolver(valor);
          return;
        }
        if (c === '\u0003') process.exit(130);
        valor = c === '\u007f' ? valor.slice(0, -1) : valor + c;
      }
    };
    stdin.on('data', tecla);
  });
}

class Api {
  private readonly base: string;
  private token = '';

  constructor(base: string) {
    this.base = base;
  }

  async pedir<T>(metodo: string, caminho: string, corpo?: unknown): Promise<T> {
    const resposta = await fetch(this.base + caminho, {
      method: metodo,
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}),
      },
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
    const texto = await resposta.text();
    if (!resposta.ok) {
      const erro = new Error(`${metodo} ${caminho}: ${resposta.status} ${texto.slice(0, 300)}`);
      Object.assign(erro, { status: resposta.status });
      throw erro;
    }
    return (texto ? JSON.parse(texto) : undefined) as T;
  }

  async entrar(email: string, senha: string, codigo: () => Promise<string>): Promise<void> {
    const login = await this.pedir<{
      accessToken?: string;
      desafio?: string;
      segundoFator?: string;
    }>('POST', '/api/auth/login', { email, senha });
    if (login.accessToken) {
      this.token = login.accessToken;
      return;
    }
    if (login.segundoFator === 'CADASTRO') {
      throw new Error(
        'Entre uma vez no ADM pelo navegador para cadastrar o aplicativo autenticador.',
      );
    }
    const sessao = await this.pedir<{ accessToken: string }>('POST', '/api/auth/segundo-fator', {
      desafio: login.desafio,
      codigo: await codigo(),
    });
    this.token = sessao.accessToken;
  }
}

async function enviarCapa(api: Api, cursoId: number, png: Buffer): Promise<void> {
  const envio = await api.pedir<{ chave: string; url: string; contentType: string }>(
    'POST',
    `/api/adm/cursos/${cursoId}/capa/envio`,
    { contentType: 'image/png', tamanho: png.length },
  );
  const resposta = await fetch(envio.url, {
    method: 'PUT',
    headers: { 'Content-Type': envio.contentType },
    body: new Uint8Array(png),
  });
  if (!resposta.ok) throw new Error(`Envio da capa recusado: ${resposta.status}`);
  await api.pedir('PUT', `/api/adm/cursos/${cursoId}/capa`, { chave: envio.chave });
}

async function criar(api: Api, e: Exemplo): Promise<'criado' | 'existente'> {
  let curso: { id: number };
  try {
    curso = await api.pedir<{ id: number }>('POST', '/api/adm/cursos', {
      titulo: e.titulo,
      slug: e.slug,
      resumo: e.resumo,
      descricao: e.descricao,
    });
  } catch (erro) {
    if ((erro as { status?: number }).status === 409) return 'existente';
    throw erro;
  }
  const turma = await api.pedir<{ id: number }>('POST', `/api/adm/cursos/${curso.id}/turmas`, {
    nome: 'Turma de exemplo',
  });
  await api.pedir('PUT', `/api/adm/turmas/${turma.id}/certificado`, {
    percentualConclusao: 100,
    cargaHorariaHoras: e.cargaHoras,
  });
  await api.pedir('PATCH', `/api/adm/turmas/${turma.id}/status`, { status: 'ATIVA' });
  let primeira = true;
  for (const m of e.modulos) {
    const modulo = await api.pedir<{ id: number }>('POST', `/api/adm/turmas/${turma.id}/modulos`, {
      titulo: m.titulo,
    });
    for (const titulo of m.aulas) {
      await api.pedir('POST', `/api/adm/modulos/${modulo.id}/aulas`, {
        titulo,
        descricao: `Aula de exemplo do curso ${e.titulo}.`,
        gratuita: primeira,
      });
      primeira = false;
    }
  }
  await enviarCapa(api, curso.id, capa(e.cores));
  await api.pedir('PATCH', `/api/adm/cursos/${curso.id}/status`, { status: 'PUBLICADO' });
  return 'criado';
}

async function principal(): Promise<void> {
  const base = (process.argv[2] ?? '').replace(/\/$/, '');
  if (!/^https?:\/\//.test(base)) {
    console.error(
      'Uso: npm run cursos-exemplo -- <endereço do ADM, ex.: https://homolog-adm.celke.uno>',
    );
    process.exit(1);
  }
  const api = new Api(base);
  const email = await perguntar('E-mail do administrador: ');
  const senha = await perguntar('Senha: ', true);
  await api.entrar(email, senha, () => perguntar('Código do aplicativo autenticador: '));
  for (const e of EXEMPLOS) {
    const resultado = await criar(api, e);
    console.log(`${resultado === 'criado' ? 'Criado ' : 'Já existe'}  ${e.titulo}`);
  }
  console.log('\nPronto. O site mostra os cursos em até um minuto.');
}

if (import.meta.main) {
  principal().catch((erro: unknown) => {
    console.error(erro instanceof Error ? erro.message : erro);
    process.exit(1);
  });
}
`````

`web/site/app/api.server.ts`:

`````ts
import { data } from 'react-router';
import { API_URL, CACHE_MS } from './config.server.ts';
import type { Certificado, CursoCatalogo, CursoDetalhe, Termo } from './seo.ts';

interface Guardado {
  expiraEm: number;
  valor: Promise<unknown>;
}

const cache = new Map<string, Guardado>();

/**
 * GET na API com cache curto em memória. Leituras simultâneas do mesmo endereço esperam a mesma
 * resposta; um erro não fica guardado. 404 vira a página "não encontrada" do site.
 */
async function buscar<T>(caminho: string): Promise<T> {
  const agora = Date.now();
  const guardado = cache.get(caminho);
  if (guardado && guardado.expiraEm > agora) return guardado.valor as Promise<T>;
  const valor = fetch(API_URL + caminho, {
    headers: { Accept: 'application/json' },
    signal: AbortSignal.timeout(10_000),
  }).then(async (resposta) => {
    if (resposta.status === 404) throw data('Não encontrado', { status: 404 });
    if (!resposta.ok) throw new Error(`GET ${caminho}: ${resposta.status}`);
    return (await resposta.json()) as T;
  });
  cache.set(caminho, { expiraEm: agora + CACHE_MS, valor });
  valor.catch(() => cache.delete(caminho));
  if (cache.size > 500) {
    for (const [chave, item] of cache) if (item.expiraEm <= agora) cache.delete(chave);
  }
  return valor;
}

/** Todos os cursos publicados (o catálogo é paginado, até 100 por página). */
export async function cursos(): Promise<CursoCatalogo[]> {
  const todos: CursoCatalogo[] = [];
  for (let pagina = 0; ; pagina++) {
    const resposta = await buscar<{ itens: CursoCatalogo[]; totalPaginas: number }>(
      `/api/cursos?page=${pagina}&size=100`,
    );
    todos.push(...resposta.itens);
    if (pagina + 1 >= resposta.totalPaginas) return todos;
  }
}

export function curso(slug: string): Promise<CursoDetalhe> {
  return buscar<CursoDetalhe>(`/api/cursos/${encodeURIComponent(slug)}`);
}

export function termosVigentes(): Promise<Termo[]> {
  return buscar<Termo[]>('/api/termos/vigentes');
}

/** Certificado pelo código; vazio quando não existe. Sem cache: a consulta é pontual. */
export async function certificado(codigo: string): Promise<Certificado | null> {
  const resposta = await fetch(`${API_URL}/api/certificados/${encodeURIComponent(codigo)}`, {
    headers: { Accept: 'application/json' },
    signal: AbortSignal.timeout(10_000),
  });
  if (resposta.status === 404) return null;
  if (!resposta.ok) throw new Error(`GET /api/certificados: ${resposta.status}`);
  return (await resposta.json()) as Certificado;
}
`````

`web/site/app/componentes/CartaoCurso.tsx`:

`````tsx
import { Link } from 'react-router';
import type { CursoCatalogo } from '../seo.ts';
import { Icone } from './Icone.tsx';

export function CartaoCurso({ curso }: { curso: CursoCatalogo }) {
  return (
    <li className="s-cartao">
      <Link to={`/cursos/${curso.slug}/`} className="s-cartao-link">
        <span className="s-cartao-midia">
          {curso.capaUrl ? (
            <img
              className="s-cartao-capa"
              src={curso.capaUrl}
              alt=""
              width={640}
              height={360}
              loading="lazy"
            />
          ) : (
            <span className="s-cartao-capa s-sem-capa">
              <Icone nome="play" className="s-sem-capa-icone" />
            </span>
          )}
        </span>
        <span className="s-cartao-corpo">
          <span className="s-cartao-titulo">{curso.titulo}</span>
          <span className="s-cartao-resumo">{curso.resumo}</span>
          <span className="s-cartao-mais">
            Ver curso <Icone nome="seta" className="s-icone-pequeno" />
          </span>
        </span>
      </Link>
    </li>
  );
}

export function GradeCursos({ cursos }: { cursos: CursoCatalogo[] }) {
  if (cursos.length === 0) return <p className="s-vazio">Novos cursos em breve. Volte logo!</p>;
  return (
    <ul className="s-grade" role="list">
      {cursos.map((c) => (
        <CartaoCurso key={c.slug} curso={c} />
      ))}
    </ul>
  );
}
`````

`web/site/app/componentes/Icone.tsx`:

`````tsx
/** Ícones de linha (decorativos), desenhados inline: nenhuma requisição a mais. */
const DESENHOS = {
  play: <path d="M8 5.5v13l11-6.5z" />,
  livro: (
    <>
      <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15H6.5A2.5 2.5 0 0 0 4 20.5z" />
      <path d="M4 20.5A2.5 2.5 0 0 0 6.5 23H20v-5" />
    </>
  ),
  conversa: <path d="M21 12a8 8 0 0 1-11.6 7.1L4 21l1.9-5.4A8 8 0 1 1 21 12z" />,
  certificado: (
    <>
      <circle cx="12" cy="9" r="6" />
      <path d="M8.5 14 7 22l5-3 5 3-1.5-8" />
    </>
  ),
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  seta: <path d="M5 12h14M13 6l6 6-6 6" />,
  camadas: (
    <>
      <path d="m12 3 9 5-9 5-9-5z" />
      <path d="m3 13 9 5 9-5" />
    </>
  ),
  escudo: (
    <>
      <path d="M12 3 4 6v6c0 4.4 3.4 8.3 8 9 4.6-.7 8-4.6 8-9V6z" />
      <path d="m9 12 2 2 4-4" />
    </>
  ),
  busca: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </>
  ),
} as const;

export type NomeIcone = keyof typeof DESENHOS;

export function Icone({ nome, className = 's-icone' }: { nome: NomeIcone; className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {DESENHOS[nome]}
    </svg>
  );
}
`````

`web/site/app/componentes/Marca.tsx`:

`````tsx
import { Link } from 'react-router';
import { NOME } from '../seo.ts';

export function Marca() {
  return (
    <Link className="s-marca" to="/">
      <img src="/icone.svg" alt="" width={32} height={32} />
      <span>{NOME}</span>
    </Link>
  );
}
`````

`web/site/app/config.server.ts`:

`````ts
import type { Contexto } from './seo.ts';

const sem = (url: string) => url.replace(/\/$/, '');

/** API do próprio ambiente pela rede interna (no compose: http://api:8080). */
export const API_URL = sem(process.env['API_URL'] ?? 'http://localhost:8080');

const ALUNO_URL = sem(process.env['FRONT_ALUNO_URL'] ?? 'http://localhost:5173');

/** Endereços e regras do ambiente. Sem SITE_URL, deriva do aluno (aluno.celke.uno → cursos.celke.uno). */
export function contexto(): Contexto {
  return {
    siteUrl: sem(process.env['SITE_URL'] || ALUNO_URL.replace('aluno.', 'cursos.')),
    alunoUrl: ALUNO_URL,
    indexavel: process.env['SITE_INDEXAVEL'] !== 'false',
    ano: new Date().getFullYear(),
  };
}

/** Tempo que uma leitura da API fica em memória (um pico de acessos não vira um pico na API). */
export const CACHE_MS = Number(process.env['SITE_CACHE_SEGUNDOS'] ?? 30) * 1000;
`````

`web/site/app/entry.server.tsx`:

`````tsx
import { randomBytes } from 'node:crypto';
import { PassThrough } from 'node:stream';
import { createReadableStreamFromReadable } from '@react-router/node';
import { isbot } from 'isbot';
import { renderToPipeableStream } from 'react-dom/server';
import { ServerRouter, type EntryContext } from 'react-router';
import { ContextoNonce } from './nonce.ts';

const LIMITE_MS = 10_000;

/** Origem das capas (URLs pré-assinadas do armazenamento), liberada nas imagens. */
const MIDIA = process.env['MIDIA_ORIGEM'] ?? '';

/** Política da página: só o que é do próprio site, e os scripts de hidratação com o nonce desta resposta. */
export function politica(nonce: string): string {
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}'`,
    "style-src 'self'",
    `img-src 'self' data: ${MIDIA}`.trim(),
    "connect-src 'self'",
    "font-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join('; ');
}

/**
 * Renderiza no servidor. Buscadores e robôs recebem a página inteira de uma vez; navegadores começam
 * a receber o HTML em streaming.
 */
export default function handleRequest(
  request: Request,
  status: number,
  headers: Headers,
  contexto: EntryContext,
): Promise<Response> {
  const nonce = randomBytes(16).toString('base64');
  const robo = isbot(request.headers.get('user-agent') ?? '');
  return new Promise((resolver, rejeitar) => {
    let erroDeRender = false;
    const { pipe, abort } = renderToPipeableStream(
      <ContextoNonce.Provider value={nonce}>
        <ServerRouter context={contexto} url={request.url} nonce={nonce} />
      </ContextoNonce.Provider>,
      {
        nonce,
        [robo || contexto.isSpaMode ? 'onAllReady' : 'onShellReady']() {
          const corpo = new PassThrough();
          headers.set('Content-Type', 'text/html; charset=utf-8');
          headers.set('Content-Security-Policy', politica(nonce));
          headers.set('X-Frame-Options', 'DENY');
          resolver(
            new Response(createReadableStreamFromReadable(corpo), {
              headers,
              status: erroDeRender ? 500 : status,
            }),
          );
          pipe(corpo);
        },
        onShellError: rejeitar,
        onError(erro: unknown) {
          erroDeRender = true;
          console.error(erro);
        },
      },
    );
    setTimeout(abort, LIMITE_MS + 1_000);
  });
}

export const streamTimeout = LIMITE_MS;
`````

`web/site/app/estilos/site.css`:

`````css
/* Site público: tokens da @celke/kole-react (tokens.css) com o tema escuro seguindo o sistema do
   visitante. Sem fontes nem imagens externas: tudo do próprio site. */

@media (prefers-color-scheme: dark) {
  :root {
    --bg: #070d1a;
    --surface: #0f1729;
    --surf-2: #141e33;
    --surf-3: #19243c;
    --border: #223152;
    --border-2: #2e3f63;
    --ink: #e8eef8;
    --t2: #b4c0d4;
    --t3: #8a98b0;
    --pri: #4d8dff;
    --pri-bg: #13254a;
    --pri-bd: #24427a;
    --pri-ink: #77a8ff;
    --on-pri: #06101f;
    --navy: #0a1428;
    --ok-fg: #59d98c;
    --ok-bg: #14301f;
    --ok-bd: #235639;
    --err-fg: #f47369;
    --err-bg: #331513;
    --err-bd: #5a2620;
  }
}

:root {
  --s-sombra: 0 1px 2px rgba(11, 26, 54, 0.06), 0 8px 24px rgba(11, 26, 54, 0.08);
  --s-sombra-forte: 0 2px 4px rgba(11, 26, 54, 0.08), 0 18px 40px rgba(11, 26, 54, 0.16);
  --s-raio: 16px;
  --s-heroi: linear-gradient(135deg, #0b1a36 0%, #14306b 55%, #1f5fe0 120%);
}

*,
*::before,
*::after {
  box-sizing: border-box;
}

html {
  -webkit-text-size-adjust: 100%;
  scroll-behavior: smooth;
}

body {
  margin: 0;
  background: var(--bg);
  color: var(--ink);
  font-family: var(--font-ui);
  font-size: var(--fs-corpo);
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}

img {
  max-width: 100%;
  height: auto;
}

a {
  color: var(--pri-ink);
}

:focus-visible {
  outline: 3px solid var(--pri);
  outline-offset: 2px;
}

h1,
h2,
h3 {
  line-height: 1.2;
  margin: 0 0 0.5em;
  letter-spacing: -0.02em;
}

h1 {
  font-size: clamp(2rem, 5vw, 3.25rem);
  font-weight: 800;
}

h2 {
  font-size: clamp(1.4rem, 3vw, 1.9rem);
  font-weight: 750;
}

h3 {
  font-size: 1.1rem;
  font-weight: 700;
}

.s-largura {
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 20px;
}

.s-estreito {
  max-width: 780px;
}

.s-secao {
  padding-top: 64px;
  padding-bottom: 64px;
}

.s-secao-final {
  padding-bottom: 72px;
}

.s-pular {
  position: absolute;
  left: -9999px;
}

.s-pular:focus {
  left: 16px;
  top: 8px;
  z-index: 30;
  background: var(--surface);
  padding: 8px 12px;
  border-radius: 8px;
}

.s-visivel-leitor {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.s-icone {
  width: 24px;
  height: 24px;
  flex: none;
}

.s-icone-pequeno {
  width: 18px;
  height: 18px;
  flex: none;
  vertical-align: -3px;
}

/* Topo fixo, translúcido */
.s-topo {
  position: sticky;
  top: 0;
  z-index: 20;
  background: color-mix(in srgb, var(--surface) 86%, transparent);
  backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid var(--border);
}

.s-topo-linha {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 24px;
  min-height: 68px;
  padding-top: 10px;
  padding-bottom: 10px;
}

.s-marca {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--ink);
  font-weight: 800;
  font-size: 1.1rem;
  letter-spacing: -0.01em;
  text-decoration: none;
}

.s-menu {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 24px;
}

.s-menu a:not(.s-botao) {
  color: var(--t2);
  font-weight: 600;
  text-decoration: none;
}

.s-menu a:not(.s-botao):hover {
  color: var(--pri-ink);
}

/* Botões */
.s-botao {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 11px 22px;
  border-radius: var(--r-pill);
  border: 2px solid var(--pri);
  background: var(--pri);
  color: var(--on-pri);
  font: inherit;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease,
    background 0.15s ease;
}

.s-botao:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--pri) 35%, transparent);
}

.s-botao:disabled {
  opacity: 0.7;
  cursor: progress;
  transform: none;
}

.s-botao-pequeno {
  padding: 7px 18px;
}

.s-botao-grande {
  padding: 14px 28px;
  font-size: 1.05rem;
}

.s-botao-bloco {
  display: flex;
  width: 100%;
}

.s-botao-contorno {
  background: transparent;
  color: var(--pri-ink);
}

.s-botao-contorno:hover {
  background: var(--pri-bg);
  box-shadow: none;
}

/* Sobre o fundo escuro da abertura e da chamada final */
.s-botao-vidro {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.45);
  color: #fff;
}

.s-botao-vidro:hover {
  background: rgba(255, 255, 255, 0.18);
  box-shadow: none;
}

.s-botao-claro {
  background: #fff;
  border-color: #fff;
  color: #0b1a36;
}

.s-acoes {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 28px 0 0;
}

.s-acoes-centro {
  justify-content: center;
}

.s-detalhe {
  color: var(--t3);
  font-size: var(--fs-corpo-2);
}

.s-vazio {
  color: var(--t3);
}

/* Abertura */
.s-heroi {
  position: relative;
  overflow: hidden;
  background: var(--s-heroi);
  color: #fff;
  padding: 88px 0 96px;
}

.s-heroi::before,
.s-heroi::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  filter: blur(10px);
  pointer-events: none;
}

.s-heroi::before {
  width: 520px;
  height: 520px;
  right: -140px;
  top: -200px;
  background: radial-gradient(circle, rgba(77, 141, 255, 0.45), transparent 65%);
}

.s-heroi::after {
  width: 420px;
  height: 420px;
  left: -160px;
  bottom: -220px;
  background: radial-gradient(circle, rgba(247, 147, 30, 0.22), transparent 65%);
}

.s-heroi-grade {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 48px;
  align-items: center;
}

@media (min-width: 920px) {
  .s-heroi-grade {
    grid-template-columns: 1.15fr 1fr;
  }
}

.s-rotulo {
  display: inline-block;
  margin: 0 0 18px;
  padding: 6px 14px;
  border-radius: var(--r-pill);
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.22);
  font-size: var(--fs-corpo-2);
  font-weight: 600;
}

.s-heroi h1 {
  color: #fff;
}

.s-heroi-texto {
  font-size: 1.15rem;
  max-width: 620px;
  color: var(--t2);
}

.s-heroi .s-heroi-texto {
  color: rgba(255, 255, 255, 0.82);
}

.s-heroi-selos {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
  margin: 28px 0 0;
  padding: 0;
  list-style: none;
  color: rgba(255, 255, 255, 0.85);
  font-size: var(--fs-corpo-2);
}

.s-heroi-selos li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.s-heroi-selos svg {
  color: #59d98c;
}

/* Ilustração da abertura: um player com a lista de aulas, só em CSS */
.s-heroi-ilustracao {
  position: relative;
  max-width: 480px;
  justify-self: center;
  width: 100%;
}

@media (max-width: 640px) {
  .s-heroi-ilustracao {
    display: none;
  }
}

.s-tela {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 20px;
  padding: 16px;
  box-shadow: 0 30px 60px rgba(3, 10, 25, 0.45);
  transform: perspective(1200px) rotateY(-8deg) rotateX(4deg);
}

.s-tela-video {
  display: grid;
  place-items: center;
  aspect-ratio: 16 / 9;
  border-radius: 12px;
  background: linear-gradient(135deg, #1f5fe0, #6a3df0);
}

.s-tela-play {
  width: 56px;
  height: 56px;
  padding: 14px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  color: #1f5fe0;
  fill: #1f5fe0;
}

.s-tela-barra {
  height: 6px;
  margin: 14px 0 16px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.18);
}

.s-tela-barra span {
  display: block;
  width: 62%;
  height: 100%;
  border-radius: inherit;
  background: #59d98c;
}

.s-tela-aulas {
  display: grid;
  gap: 10px;
}

.s-tela-aula {
  height: 14px;
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.14);
}

.s-tela-vista {
  background: rgba(89, 217, 140, 0.45);
}

.s-tela-atual {
  background: rgba(255, 255, 255, 0.55);
  width: 82%;
}

.s-tela-selo {
  position: absolute;
  right: -8px;
  bottom: -22px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 12px;
  background: #fff;
  color: #0b1a36;
  font-weight: 700;
  font-size: var(--fs-corpo-2);
  box-shadow: 0 16px 36px rgba(3, 10, 25, 0.35);
}

.s-tela-selo svg {
  color: #f7931e;
}

/* Seções */
.s-titulo-secao {
  margin-bottom: 28px;
}

.s-cabecalho-secao {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px 24px;
}

.s-cabecalho-secao a {
  font-weight: 700;
}

.s-faixa {
  background: var(--surf-2);
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}

/* Vantagens */
.s-vantagens {
  display: grid;
  gap: 20px;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  margin: 0;
  padding: 0;
  list-style: none;
}

.s-vantagens li {
  padding: 26px;
  border-radius: var(--s-raio);
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: var(--s-sombra);
}

.s-vantagens p {
  margin: 0;
  color: var(--t2);
}

.s-vantagem-icone {
  display: inline-grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin-bottom: 16px;
  border-radius: 14px;
  background: var(--pri-bg);
  color: var(--pri-ink);
}

/* Cartões de curso */
.s-grade {
  display: grid;
  gap: 24px;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  margin: 0;
  padding: 0;
  list-style: none;
}

.s-cartao {
  border-radius: var(--s-raio);
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: var(--s-sombra);
  overflow: hidden;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.s-cartao:hover,
.s-cartao:focus-within {
  transform: translateY(-4px);
  box-shadow: var(--s-sombra-forte);
}

.s-cartao-link {
  display: flex;
  flex-direction: column;
  height: 100%;
  color: inherit;
  text-decoration: none;
}

.s-cartao-midia {
  display: block;
  overflow: hidden;
}

.s-cartao-capa {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  transition: transform 0.4s ease;
}

.s-cartao:hover .s-cartao-capa {
  transform: scale(1.04);
}

.s-sem-capa {
  display: grid;
  place-items: center;
  background: var(--s-heroi);
  color: #fff;
}

.s-sem-capa-icone {
  width: 52px;
  height: 52px;
  padding: 12px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  fill: currentColor;
}

.s-cartao-corpo {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  padding: 20px 22px 22px;
}

.s-cartao-titulo {
  font-weight: 750;
  font-size: 1.12rem;
  line-height: 1.3;
}

.s-cartao-resumo {
  color: var(--t2);
  font-size: var(--fs-corpo-2);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.s-cartao-mais {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: auto;
  padding-top: 8px;
  color: var(--pri-ink);
  font-weight: 700;
  font-size: var(--fs-corpo-2);
}

/* Como funciona */
.s-passos {
  display: grid;
  gap: 18px;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: passo;
}

.s-passos li {
  position: relative;
  padding: 58px 22px 22px;
  border-radius: var(--s-raio);
  border: 1px dashed var(--border-2);
  counter-increment: passo;
  color: var(--t2);
}

.s-passos li::before {
  content: counter(passo);
  position: absolute;
  top: 18px;
  left: 22px;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--pri);
  color: var(--on-pri);
  font-weight: 800;
  font-size: 0.95rem;
}

.s-passos strong {
  color: var(--ink);
}

/* Chamada final */
.s-chamada {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 20px 32px;
  padding: 40px;
  border-radius: 24px;
  background: var(--s-heroi);
  color: #fff;
  box-shadow: var(--s-sombra-forte);
}

.s-chamada h2 {
  color: #fff;
  margin-bottom: 4px;
}

.s-chamada p {
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
}

/* Cabeçalho das páginas internas */
.s-cabeca-pagina {
  background: var(--s-heroi);
  color: #fff;
  padding: 56px 0 60px;
}

.s-cabeca-pagina h1 {
  color: #fff;
  font-size: clamp(1.9rem, 4.2vw, 2.8rem);
}

.s-cabeca-pagina .s-heroi-texto {
  color: rgba(255, 255, 255, 0.82);
  margin: 0;
}

.s-trilha {
  margin-bottom: 18px;
  font-size: var(--fs-corpo-2);
  color: rgba(255, 255, 255, 0.7);
}

.s-trilha a {
  color: #fff;
}

.s-fichas {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 24px 0 0;
  padding: 0;
  list-style: none;
}

.s-fichas li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--r-pill);
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.2);
  font-size: var(--fs-corpo-2);
  font-weight: 600;
}

/* Busca do catálogo */
.s-busca {
  position: relative;
  max-width: 520px;
  margin-top: 26px;
}

.s-busca input {
  width: 100%;
  padding: 14px 18px 14px 48px;
  border-radius: var(--r-pill);
  border: 1px solid rgba(255, 255, 255, 0.3);
  background: rgba(255, 255, 255, 0.96);
  color: #0e1726;
  font: inherit;
}

.s-busca input::placeholder {
  color: #55627a;
}

.s-busca-icone {
  position: absolute;
  left: 18px;
  top: 50%;
  width: 20px;
  height: 20px;
  transform: translateY(-50%);
  color: #55627a;
}

/* Página do curso */
.s-curso-grade {
  display: grid;
  gap: 32px;
  padding-top: 40px;
  padding-bottom: 72px;
}

@media (min-width: 920px) {
  .s-curso-grade {
    grid-template-columns: minmax(0, 1fr) 380px;
    grid-template-areas: 'conteudo compra';
    align-items: start;
  }

  .s-compra {
    grid-area: compra;
    position: sticky;
    top: 92px;
    margin-top: -120px;
  }

  .s-curso-conteudo {
    grid-area: conteudo;
  }
}

.s-compra {
  border-radius: 20px;
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: var(--s-sombra-forte);
  overflow: hidden;
}

.s-curso-capa {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.s-curso-capa.s-sem-capa {
  display: grid;
}

.s-compra-corpo {
  padding: 22px;
}

.s-compra-acoes {
  display: grid;
  gap: 10px;
}

.s-inclui {
  display: grid;
  gap: 8px;
  margin: 20px 0 0;
  padding: 18px 0 0;
  border-top: 1px solid var(--border);
  list-style: none;
  color: var(--t2);
  font-size: var(--fs-corpo-2);
}

.s-inclui li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.s-inclui svg {
  color: var(--ok-fg);
  margin-top: 3px;
}

.s-bloco + .s-bloco {
  margin-top: 40px;
}

.s-texto {
  overflow-wrap: anywhere;
  color: var(--t2);
}

.s-modulos {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.s-modulo {
  border-radius: 14px;
  background: var(--surface);
  border: 1px solid var(--border);
  overflow: hidden;
}

.s-modulo-topo {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  background: var(--surf-2);
  border-bottom: 1px solid var(--border);
}

.s-modulo-topo h3 {
  flex: 1;
  margin: 0;
}

.s-modulo-numero {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--pri-bg);
  color: var(--pri-ink);
  font-weight: 800;
  font-size: 0.9rem;
}

.s-modulo-qtd {
  color: var(--t3);
  font-size: var(--fs-corpo-2);
  white-space: nowrap;
}

.s-aulas {
  margin: 0;
  padding: 6px 0;
  list-style: none;
}

.s-aulas li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 20px;
  color: var(--t2);
}

.s-aulas li + li {
  border-top: 1px solid var(--border);
}

.s-aulas svg {
  color: var(--t3);
}

.s-selo {
  margin-left: auto;
  padding: 2px 10px;
  border-radius: var(--r-pill);
  background: var(--ok-bg);
  color: var(--ok-fg);
  font-size: var(--fs-caption);
  font-weight: 700;
  white-space: nowrap;
}

/* Ofertas: preço e compra */
.s-ofertas {
  display: grid;
  gap: 12px;
  margin: 0 0 12px;
  padding: 0;
  list-style: none;
}

.s-oferta {
  display: grid;
  gap: 8px;
  padding: 16px;
  border-radius: 14px;
  background: var(--pri-bg);
  border: 1px solid var(--pri-bd);
}

.s-oferta-titulo {
  color: var(--t2);
  font-weight: 600;
}

.s-oferta-preco {
  font-size: 1.8rem;
  letter-spacing: -0.02em;
}

/* Painéis (validar, termos) */
.s-painel {
  padding: clamp(24px, 5vw, 44px);
  border-radius: 20px;
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: var(--s-sombra);
}

.s-painel h1 {
  font-size: clamp(1.7rem, 4vw, 2.3rem);
}

.s-form {
  display: grid;
  gap: 10px;
  max-width: 460px;
  margin: 24px 0;
}

.s-form label {
  font-weight: 700;
}

.s-form input {
  font: inherit;
  padding: 12px 14px;
  border: 1px solid var(--border-2);
  border-radius: 12px;
  background: var(--surf-2);
  color: var(--ink);
}

.s-form .s-botao {
  justify-self: start;
}

.s-resultado {
  padding: 18px 20px;
  border-radius: 14px;
  border: 1px solid var(--border);
}

.s-resultado p {
  margin: 4px 0 0;
}

.s-resultado-ok {
  background: var(--ok-bg);
  border-color: var(--ok-bd);
  color: var(--ok-fg);
}

.s-resultado-erro {
  background: var(--err-bg);
  border-color: var(--err-bd);
  color: var(--err-fg);
}

.s-resultado dl {
  display: grid;
  gap: 6px;
  margin: 14px 0 0;
}

.s-resultado dl div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.s-resultado dt {
  font-weight: 700;
}

.s-resultado dd {
  margin: 0;
}

/* 404 e erro */
.s-nao-encontrada {
  text-align: center;
  padding-top: 96px;
  padding-bottom: 96px;
}

.s-codigo-erro {
  margin: 0;
  font-size: clamp(4rem, 14vw, 7rem);
  font-weight: 900;
  line-height: 1;
  letter-spacing: -0.04em;
  background: var(--s-heroi);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

/* Rodapé */
.s-rodape {
  background: #0b1a36;
  color: rgba(255, 255, 255, 0.78);
  font-size: var(--fs-corpo-2);
}

.s-rodape a {
  color: rgba(255, 255, 255, 0.86);
  text-decoration: none;
}

.s-rodape a:hover {
  color: #fff;
  text-decoration: underline;
}

.s-rodape .s-marca {
  color: #fff;
}

.s-rodape-grade {
  display: grid;
  gap: 32px;
  padding-top: 56px;
  padding-bottom: 40px;
}

@media (min-width: 760px) {
  .s-rodape-grade {
    grid-template-columns: 2fr 1fr 1fr;
  }
}

.s-rodape-marca p {
  max-width: 360px;
  margin: 14px 0 0;
}

.s-rodape-coluna {
  display: grid;
  gap: 10px;
  align-content: start;
}

.s-rodape-titulo {
  margin: 0 0 4px;
  color: #fff;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  font-size: var(--fs-caption);
}

.s-rodape-base {
  padding-top: 20px;
  padding-bottom: 28px;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.6);
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    transition: none !important;
    scroll-behavior: auto !important;
  }
}
`````

`web/site/app/nonce.ts`:

`````ts
import { createContext, useContext } from 'react';

/** Nonce da Content-Security-Policy desta resposta (só existe no servidor; no navegador fica vazio). */
export const ContextoNonce = createContext<string | undefined>(undefined);

export function useNonce(): string | undefined {
  return useContext(ContextoNonce);
}
`````

`web/site/app/root.tsx`:

`````tsx
import type { ReactNode } from 'react';
import {
  isRouteErrorResponse,
  Link,
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
  useRouteError,
  useRouteLoaderData,
  type LinksFunction,
} from 'react-router';
import tokens from '@celke/kole-react/tokens.css?url';
import estilos from './estilos/site.css?url';
import { Marca } from './componentes/Marca.tsx';
import { contexto } from './config.server.ts';
import { useNonce } from './nonce.ts';
import { NOME, type Contexto } from './seo.ts';

export const links: LinksFunction = () => [
  { rel: 'icon', href: '/favicon.ico', sizes: '32x32' },
  { rel: 'icon', href: '/icone.svg', type: 'image/svg+xml' },
  { rel: 'stylesheet', href: tokens },
  { rel: 'stylesheet', href: estilos },
];

export function loader() {
  return contexto();
}

/** Contexto do ambiente (endereços), disponível em todas as páginas. */
export function useContexto(): Contexto {
  const ctx = useRouteLoaderData<typeof loader>('root');
  return ctx ?? { siteUrl: '', alunoUrl: '', indexavel: false, ano: new Date().getFullYear() };
}

export function Layout({ children }: { children: ReactNode }) {
  const nonce = useNonce();
  const ctx = useContexto();
  return (
    <html lang="pt-BR">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <Meta />
        <Links />
      </head>
      <body>
        <a className="s-pular" href="#conteudo">
          Pular para o conteúdo
        </a>
        <header className="s-topo">
          <div className="s-largura s-topo-linha">
            <Marca />
            <nav aria-label="Principal" className="s-menu">
              <Link to="/cursos/">Cursos</Link>
              <Link to="/certificados/validar/">Validar certificado</Link>
              <a className="s-botao s-botao-pequeno" href={`${ctx.alunoUrl}/entrar`}>
                Entrar
              </a>
            </nav>
          </div>
        </header>
        <main id="conteudo">{children}</main>
        <footer className="s-rodape">
          <div className="s-largura s-rodape-grade">
            <div className="s-rodape-marca">
              <Marca />
              <p>
                Cursos online com aulas em vídeo, materiais de apoio, fórum com o instrutor e
                certificado verificável.
              </p>
            </div>
            <nav aria-label="Plataforma" className="s-rodape-coluna">
              <p className="s-rodape-titulo">Plataforma</p>
              <Link to="/cursos/">Cursos</Link>
              <a href={`${ctx.alunoUrl}/entrar`}>Área do aluno</a>
              <Link to="/certificados/validar/">Validar certificado</Link>
            </nav>
            <nav aria-label="Rodapé" className="s-rodape-coluna">
              <p className="s-rodape-titulo">Legal</p>
              <Link to="/termos/">Termos de uso</Link>
              <Link to="/privacidade/">Privacidade</Link>
            </nav>
          </div>
          <div className="s-largura s-rodape-base">
            © {ctx.ano} {NOME}. Todos os direitos reservados.
          </div>
        </footer>
        <ScrollRestoration nonce={nonce} />
        <Scripts nonce={nonce} />
      </body>
    </html>
  );
}

export default function App() {
  return <Outlet />;
}

/** Página não encontrada (404) ou erro inesperado, dentro do mesmo layout. */
export function ErrorBoundary() {
  const erro = useRouteError();
  const naoEncontrada = isRouteErrorResponse(erro) && erro.status === 404;
  return (
    <div className="s-largura s-secao s-nao-encontrada">
      <title>{`${naoEncontrada ? 'Página não encontrada' : 'Erro'} — ${NOME}`}</title>
      <meta name="robots" content="noindex" />
      <p className="s-codigo-erro" aria-hidden="true">
        {naoEncontrada ? '404' : '500'}
      </p>
      <h1>{naoEncontrada ? 'Página não encontrada' : 'Algo deu errado'}</h1>
      <p>
        {naoEncontrada
          ? 'O endereço não existe ou mudou.'
          : 'Não foi possível carregar esta página agora. Tente de novo em instantes.'}
      </p>
      <p className="s-acoes s-acoes-centro">
        <Link className="s-botao" to="/cursos/">
          Ver os cursos
        </Link>
      </p>
    </div>
  );
}
`````

`web/site/app/rotas/comum.ts`:

`````ts
import type { Contexto } from '../seo.ts';

/** Contexto do ambiente (loader da raiz) dentro do `meta` de uma rota. */
export function contextoDe(
  matches: readonly ({ id: string; data?: unknown } | undefined)[],
): Contexto {
  const raizMatch = matches.find((m) => m?.id === 'root') as
    { loaderData?: unknown; data?: unknown } | undefined;
  const raiz = (raizMatch?.loaderData ?? raizMatch?.data) as Contexto | undefined;
  return raiz ?? { siteUrl: '', alunoUrl: '', indexavel: false, ano: new Date().getFullYear() };
}

/** HTML público sem cookies: pode ficar um minuto em cache no navegador e nos intermediários. */
export function cachePublico() {
  return { 'Cache-Control': 'public, max-age=60, stale-while-revalidate=300' };
}
`````

`web/site/app/rotas/curso.tsx`:

`````tsx
import { Link, useLoaderData, type LoaderFunctionArgs, type MetaFunction } from 'react-router';
import { curso as buscarCurso } from '../api.server.ts';
import { Icone } from '../componentes/Icone.tsx';
import { useContexto } from '../root.tsx';
import {
  dadosCurso,
  jsonEmScript,
  metaTags,
  paragrafos,
  plural,
  preco,
  type Contexto,
  type CursoDetalhe,
} from '../seo.ts';
import { cachePublico, contextoDe } from './comum.ts';

export async function loader({ params }: LoaderFunctionArgs) {
  return { curso: await buscarCurso(params['slug'] ?? '') };
}

export const headers = cachePublico;

export const meta: MetaFunction<typeof loader> = ({ loaderData, matches }) =>
  loaderData
    ? metaTags(contextoDe(matches), {
        caminho: `/cursos/${loaderData.curso.slug}/`,
        titulo: loaderData.curso.titulo,
        descricao: loaderData.curso.resumo,
        imagem: loaderData.curso.capaUrl,
      })
    : [];

/** Preço e "Comprar" de cada oferta; sem oferta, o convite para criar a conta. */
function Compra({ ctx, curso }: { ctx: Contexto; curso: CursoDetalhe }) {
  const ofertas = curso.ofertas ?? [];
  if (ofertas.length === 0) {
    return (
      <div className="s-compra-acoes">
        <a className="s-botao s-botao-grande s-botao-bloco" href={`${ctx.alunoUrl}/cadastro`}>
          Quero fazer este curso
        </a>
        <a className="s-botao s-botao-contorno s-botao-bloco" href={`${ctx.alunoUrl}/entrar`}>
          Já sou aluno
        </a>
      </div>
    );
  }
  return (
    <>
      <ul className="s-ofertas" role="list">
        {ofertas.map((o) => (
          <li key={o.id} className="s-oferta">
            <span className="s-oferta-titulo">{o.titulo}</span>
            <strong className="s-oferta-preco">{preco(o)}</strong>
            <a className="s-botao s-botao-bloco" href={`${ctx.alunoUrl}/comprar/${o.id}`}>
              Comprar
            </a>
          </li>
        ))}
      </ul>
      <p className="s-detalhe">
        Já é aluno? <a href={`${ctx.alunoUrl}/entrar`}>Entre na sua conta</a>.
      </p>
    </>
  );
}

export default function Curso() {
  const { curso } = useLoaderData<typeof loader>();
  const ctx = useContexto();
  const totalAulas = curso.modulos.reduce((total, m) => total + m.aulas.length, 0);
  return (
    <article>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonEmScript(dadosCurso(ctx, curso)) }}
      />
      <header className="s-cabeca-pagina">
        <div className="s-largura">
          <nav aria-label="Trilha" className="s-trilha">
            <Link to="/cursos/">Cursos</Link> <span aria-hidden="true">›</span>{' '}
            <span>{curso.titulo}</span>
          </nav>
          <h1>{curso.titulo}</h1>
          <p className="s-heroi-texto">{curso.resumo}</p>
          <ul className="s-fichas" role="list">
            <li>
              <Icone nome="camadas" className="s-icone-pequeno" />{' '}
              {plural(curso.modulos.length, 'módulo', 'módulos')}
            </li>
            <li>
              <Icone nome="play" className="s-icone-pequeno" />{' '}
              {plural(totalAulas, 'aula', 'aulas')}
            </li>
            <li>
              <Icone nome="certificado" className="s-icone-pequeno" /> Certificado
            </li>
          </ul>
        </div>
      </header>
      <div className="s-largura s-curso-grade">
        <aside className="s-compra" aria-label="Matrícula">
          {curso.capaUrl ? (
            <img
              className="s-curso-capa"
              src={curso.capaUrl}
              alt={`Capa do curso ${curso.titulo}`}
              width={640}
              height={360}
            />
          ) : (
            <span className="s-curso-capa s-sem-capa">
              <Icone nome="play" className="s-sem-capa-icone" />
            </span>
          )}
          <div className="s-compra-corpo">
            <Compra ctx={ctx} curso={curso} />
            <ul className="s-inclui" role="list">
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Aulas em vídeo no seu ritmo
              </li>
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Materiais de apoio para baixar
              </li>
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Fórum de dúvidas com o instrutor
              </li>
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Certificado com código de
                validação
              </li>
            </ul>
          </div>
        </aside>
        <div className="s-curso-conteudo">
          {curso.descricao && (
            <section aria-labelledby="titulo-sobre" className="s-bloco">
              <h2 id="titulo-sobre">Sobre o curso</h2>
              <div className="s-texto">
                {paragrafos(curso.descricao).map((p, i) => (
                  <p key={i}>{p}</p>
                ))}
              </div>
            </section>
          )}
          <section aria-labelledby="titulo-conteudo" className="s-bloco">
            <h2 id="titulo-conteudo">Conteúdo</h2>
            {curso.modulos.length === 0 ? (
              <p className="s-vazio">O conteúdo deste curso está em preparação.</p>
            ) : (
              <ol className="s-modulos">
                {curso.modulos.map((m, i) => (
                  <li key={i} className="s-modulo">
                    <div className="s-modulo-topo">
                      <span className="s-modulo-numero" aria-hidden="true">
                        {String(i + 1).padStart(2, '0')}
                      </span>
                      <h3>{m.titulo}</h3>
                      <span className="s-modulo-qtd">
                        {plural(m.aulas.length, 'aula', 'aulas')}
                      </span>
                    </div>
                    <ol className="s-aulas">
                      {m.aulas.map((a) => (
                        <li key={a.id}>
                          <Icone nome="play" className="s-icone-pequeno" />
                          <span>{a.titulo}</span>
                          {a.gratuita && <span className="s-selo">Aula gratuita</span>}
                        </li>
                      ))}
                    </ol>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      </div>
    </article>
  );
}
`````

`web/site/app/rotas/cursos.tsx`:

`````tsx
import { useState } from 'react';
import { useLoaderData, type MetaFunction } from 'react-router';
import { cursos } from '../api.server.ts';
import { GradeCursos } from '../componentes/CartaoCurso.tsx';
import { Icone } from '../componentes/Icone.tsx';
import { filtrarCursos, metaTags, NOME, plural } from '../seo.ts';
import { cachePublico, contextoDe } from './comum.ts';

export async function loader() {
  return { cursos: await cursos() };
}

export const headers = cachePublico;

export const meta: MetaFunction<typeof loader> = ({ loaderData, matches }) =>
  metaTags(contextoDe(matches), {
    caminho: '/cursos/',
    titulo: 'Cursos',
    descricao: `Conheça os ${loaderData?.cursos.length ?? 0} cursos disponíveis na ${NOME}.`,
  });

/** Catálogo: o HTML já vem com todos os cursos (para os buscadores); a busca filtra no navegador. */
export default function Cursos() {
  const { cursos } = useLoaderData<typeof loader>();
  const [busca, setBusca] = useState('');
  const encontrados = filtrarCursos(cursos, busca);
  return (
    <>
      <section className="s-cabeca-pagina">
        <div className="s-largura">
          <h1>Cursos</h1>
          <p className="s-heroi-texto">
            {plural(cursos.length, 'curso disponível', 'cursos disponíveis')} para você estudar no
            seu ritmo.
          </p>
          {cursos.length > 0 && (
            <div className="s-busca">
              <Icone nome="busca" className="s-busca-icone" />
              <label htmlFor="busca" className="s-visivel-leitor">
                Buscar curso
              </label>
              <input
                id="busca"
                type="search"
                placeholder="Buscar por nome ou assunto"
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
                autoComplete="off"
              />
            </div>
          )}
        </div>
      </section>
      <div className="s-largura s-secao">
        {busca.trim() && (
          <p className="s-detalhe" role="status">
            {encontrados.length === 0
              ? 'Nenhum curso encontrado para essa busca.'
              : plural(encontrados.length, 'curso encontrado', 'cursos encontrados')}
          </p>
        )}
        {(encontrados.length > 0 || !busca.trim()) && <GradeCursos cursos={encontrados} />}
      </div>
    </>
  );
}
`````

`web/site/app/rotas/inicio.tsx`:

`````tsx
import { Link, useLoaderData, type MetaFunction } from 'react-router';
import { cursos } from '../api.server.ts';
import { GradeCursos } from '../componentes/CartaoCurso.tsx';
import { Icone, type NomeIcone } from '../componentes/Icone.tsx';
import { useContexto } from '../root.tsx';
import { metaTags } from '../seo.ts';
import { cachePublico, contextoDe } from './comum.ts';

export async function loader() {
  const todos = await cursos();
  return { destaques: todos.slice(0, 6), total: todos.length };
}

export const headers = cachePublico;

export const meta: MetaFunction = ({ matches }) =>
  metaTags(contextoDe(matches), {
    caminho: '/',
    titulo: 'Aprenda no seu ritmo',
    descricao:
      'Cursos online com aulas em vídeo, materiais de apoio, fórum com o instrutor e certificado.',
  });

const VANTAGENS: { icone: NomeIcone; titulo: string; texto: string }[] = [
  {
    icone: 'play',
    titulo: 'Aulas em vídeo',
    texto: 'Assista quando e onde quiser e continue exatamente de onde parou.',
  },
  {
    icone: 'livro',
    titulo: 'Materiais de apoio',
    texto: 'Arquivos e exercícios de cada aula para baixar e praticar.',
  },
  {
    icone: 'conversa',
    titulo: 'Fórum com o instrutor',
    texto: 'Tire as dúvidas de cada aula com quem preparou o conteúdo.',
  },
  {
    icone: 'certificado',
    titulo: 'Certificado',
    texto: 'Conclua o curso e receba o certificado com código de validação.',
  },
];

export default function Inicio() {
  const { destaques, total } = useLoaderData<typeof loader>();
  const ctx = useContexto();
  return (
    <>
      <section className="s-heroi">
        <div className="s-largura s-heroi-grade">
          <div>
            <p className="s-rotulo">Cursos online com certificado</p>
            <h1>Aprenda no seu ritmo</h1>
            <p className="s-heroi-texto">
              Aulas em vídeo para assistir de qualquer lugar, materiais de apoio, dúvidas
              respondidas pelo instrutor e certificado com código de validação.
            </p>
            <p className="s-acoes">
              <Link className="s-botao s-botao-grande" to="/cursos/">
                Ver os cursos <Icone nome="seta" className="s-icone-pequeno" />
              </Link>
              <a className="s-botao s-botao-vidro s-botao-grande" href={`${ctx.alunoUrl}/cadastro`}>
                Criar conta
              </a>
            </p>
            <ul className="s-heroi-selos" role="list">
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Computador ou celular
              </li>
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Progresso salvo
              </li>
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Certificado verificável
              </li>
            </ul>
          </div>
          <div className="s-heroi-ilustracao" aria-hidden="true">
            <div className="s-tela">
              <div className="s-tela-video">
                <Icone nome="play" className="s-tela-play" />
              </div>
              <div className="s-tela-barra">
                <span />
              </div>
              <div className="s-tela-aulas">
                <span className="s-tela-aula s-tela-vista" />
                <span className="s-tela-aula s-tela-vista" />
                <span className="s-tela-aula s-tela-atual" />
                <span className="s-tela-aula" />
              </div>
            </div>
            <div className="s-tela-selo">
              <Icone nome="certificado" className="s-icone-pequeno" /> Certificado emitido
            </div>
          </div>
        </div>
      </section>

      <section className="s-largura s-secao" aria-labelledby="titulo-vantagens">
        <h2 id="titulo-vantagens" className="s-titulo-secao">
          Tudo o que você precisa para aprender
        </h2>
        <ul className="s-vantagens" role="list">
          {VANTAGENS.map((v) => (
            <li key={v.titulo}>
              <span className="s-vantagem-icone">
                <Icone nome={v.icone} />
              </span>
              <h3>{v.titulo}</h3>
              <p>{v.texto}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="s-faixa" aria-labelledby="titulo-cursos">
        <div className="s-largura s-secao">
          <div className="s-cabecalho-secao">
            <h2 id="titulo-cursos" className="s-titulo-secao">
              Cursos
            </h2>
            {total > destaques.length && <Link to="/cursos/">Ver todos os {total} cursos</Link>}
          </div>
          <GradeCursos cursos={destaques} />
        </div>
      </section>

      <section className="s-largura s-secao" aria-labelledby="titulo-como">
        <h2 id="titulo-como" className="s-titulo-secao">
          Como funciona
        </h2>
        <ol className="s-passos">
          <li>
            <strong>Crie a sua conta</strong> e confirme o e-mail.
          </li>
          <li>
            <strong>Matricule-se</strong> no curso e acesse as aulas pela área do aluno.
          </li>
          <li>
            <strong>Assista no seu tempo:</strong> o progresso fica salvo e você continua de onde
            parou.
          </li>
          <li>
            <strong>Conclua e emita o certificado</strong>, que qualquer pessoa pode validar aqui no
            site.
          </li>
        </ol>
      </section>

      <section className="s-largura s-secao-final" aria-labelledby="titulo-chamada">
        <div className="s-chamada">
          <div>
            <h2 id="titulo-chamada">Pronto para começar?</h2>
            <p>Escolha um curso e comece a estudar hoje mesmo.</p>
          </div>
          <Link className="s-botao s-botao-claro s-botao-grande" to="/cursos/">
            Escolher um curso
          </Link>
        </div>
      </section>
    </>
  );
}
`````

`web/site/app/rotas/nao-encontrada.tsx`:

`````tsx
import { data } from 'react-router';

/** Qualquer endereço sem rota: a página "não encontrada" da raiz, com status 404. */
export function loader() {
  throw data('Não encontrado', { status: 404 });
}

/** Envio para um endereço sem rota (ex.: uma rota privada da API pelo domínio do site): 404 também. */
export function action() {
  throw data('Não encontrado', { status: 404 });
}

export default function NaoEncontrada() {
  return null;
}
`````

`web/site/app/rotas/robots.ts`:

`````ts
import { contexto } from '../config.server.ts';
import { robots } from '../seo.ts';

export function loader() {
  return new Response(robots(contexto()), {
    headers: {
      'Content-Type': 'text/plain; charset=utf-8',
      'Cache-Control': 'public, max-age=3600',
    },
  });
}
`````

`web/site/app/rotas/saude.ts`:

`````ts
/** Health check do container (não depende da API: o site sobe mesmo com ela fora). */
export function loader() {
  return new Response('ok', {
    headers: { 'Content-Type': 'text/plain', 'Cache-Control': 'no-store' },
  });
}
`````

`web/site/app/rotas/sitemap.ts`:

`````ts
import { cursos } from '../api.server.ts';
import { contexto } from '../config.server.ts';
import { sitemap } from '../seo.ts';

export async function loader() {
  return new Response(sitemap(contexto(), await cursos()), {
    headers: {
      'Content-Type': 'application/xml; charset=utf-8',
      'Cache-Control': 'public, max-age=300',
    },
  });
}
`````

`web/site/app/rotas/termo.tsx`:

`````tsx
import { useLoaderData, type LoaderFunctionArgs, type MetaFunction } from 'react-router';
import { termosVigentes } from '../api.server.ts';
import { metaTags, NOME, type Termo } from '../seo.ts';
import { cachePublico, contextoDe } from './comum.ts';

const PAGINAS = {
  termos: { caminho: '/termos/', titulo: 'Termos de uso', tipo: 'TERMOS_USO' },
  privacidade: {
    caminho: '/privacidade/',
    titulo: 'Política de privacidade',
    tipo: 'POLITICA_PRIVACIDADE',
  },
} as const;

function pagina(url: string) {
  return new URL(url).pathname.startsWith('/privacidade') ? PAGINAS.privacidade : PAGINAS.termos;
}

export async function loader({ request }: LoaderFunctionArgs) {
  const p = pagina(request.url);
  const termo: Termo | undefined = (await termosVigentes()).find((t) => t.tipo === p.tipo);
  return { titulo: p.titulo, caminho: p.caminho, termo: termo ?? null };
}

export const headers = cachePublico;

export const meta: MetaFunction<typeof loader> = ({ loaderData, matches }) =>
  loaderData
    ? metaTags(contextoDe(matches), {
        caminho: loaderData.caminho,
        titulo: loaderData.titulo,
        descricao: `${loaderData.titulo} da ${NOME}.`,
      })
    : [];

export default function TermoPagina() {
  const { titulo, termo } = useLoaderData<typeof loader>();
  return (
    <article className="s-largura s-secao s-estreito">
      <div className="s-painel s-texto">
        <h1>{titulo}</h1>
        {termo ? (
          <>
            <p className="s-detalhe">
              Versão {termo.versao}, publicada em{' '}
              {new Date(termo.publicadoEm).toLocaleDateString('pt-BR', {
                timeZone: 'America/Sao_Paulo',
              })}
              .
            </p>
            {/* HTML já filtrado pela API (Markdown com HTML escapado e lista fechada de tags) */}
            <div dangerouslySetInnerHTML={{ __html: termo.conteudoHtml }} />
          </>
        ) : (
          <p className="s-vazio">Em breve.</p>
        )}
      </div>
    </article>
  );
}
`````

`web/site/app/rotas/validar.tsx`:

`````tsx
import {
  Form,
  useLoaderData,
  useNavigation,
  type LoaderFunctionArgs,
  type MetaFunction,
} from 'react-router';
import { certificado } from '../api.server.ts';
import { Icone } from '../componentes/Icone.tsx';
import { metaTags, type Certificado } from '../seo.ts';
import { contextoDe } from './comum.ts';

type Resultado =
  | { estado: 'inicial'; codigo: '' }
  | { estado: 'vazio'; codigo: '' }
  | { estado: 'valido'; codigo: string; certificado: Certificado }
  | { estado: 'nao-encontrado' | 'falha'; codigo: string };

/** O código vem na URL (?codigo=): o link impresso no certificado já abre validado. */
export async function loader({ request }: LoaderFunctionArgs): Promise<Resultado> {
  const parametro = new URL(request.url).searchParams.get('codigo');
  if (parametro === null) return { estado: 'inicial', codigo: '' };
  const codigo = parametro.trim();
  if (!codigo) return { estado: 'vazio', codigo: '' };
  try {
    const encontrado = await certificado(codigo);
    return encontrado
      ? { estado: 'valido', codigo, certificado: encontrado }
      : { estado: 'nao-encontrado', codigo };
  } catch {
    return { estado: 'falha', codigo };
  }
}

export const meta: MetaFunction = ({ matches }) =>
  metaTags(contextoDe(matches), {
    caminho: '/certificados/validar/',
    titulo: 'Validar certificado',
    descricao:
      'Confira se um certificado foi emitido pela plataforma usando o código impresso nele.',
  });

function Detalhes({ c }: { c: Certificado }) {
  const linhas: [string, string][] = [
    ['Aluno', c.nomeCompleto],
    ['Curso', c.cursoTitulo],
    ['Turma', c.turmaNome],
    [
      'Emitido em',
      new Date(c.emitidoEm).toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' }),
    ],
  ];
  if (c.cargaHorariaHoras != null) linhas.push(['Carga horária', `${c.cargaHorariaHoras} h`]);
  return (
    <dl>
      {linhas.map(([rotulo, valor]) => (
        <div key={rotulo}>
          <dt>{rotulo}</dt>
          <dd>{valor}</dd>
        </div>
      ))}
    </dl>
  );
}

function Mensagem({ r }: { r: Resultado }) {
  switch (r.estado) {
    case 'inicial':
      return null;
    case 'vazio':
      return (
        <div className="s-resultado s-resultado-erro">
          <strong>Informe o código do certificado.</strong>
        </div>
      );
    case 'valido':
      return (
        <div className="s-resultado s-resultado-ok">
          <strong>Certificado válido.</strong>
          <Detalhes c={r.certificado} />
        </div>
      );
    case 'nao-encontrado':
      return (
        <div className="s-resultado s-resultado-erro">
          <strong>Certificado não encontrado.</strong>
          <p>Confira o código digitado.</p>
        </div>
      );
    case 'falha':
      return (
        <div className="s-resultado s-resultado-erro">
          <strong>Não foi possível consultar agora.</strong>
          <p>Tente de novo em instantes.</p>
        </div>
      );
  }
}

export default function Validar() {
  const resultado = useLoaderData<typeof loader>();
  const consultando = useNavigation().state !== 'idle';
  return (
    <div className="s-largura s-secao s-estreito">
      <div className="s-painel">
        <span className="s-vantagem-icone">
          <Icone nome="escudo" />
        </span>
        <h1>Validar certificado</h1>
        <p>Informe o código impresso no certificado para conferir os dados dele.</p>
        <Form method="get" action="/certificados/validar/" className="s-form" noValidate>
          <label htmlFor="codigo">Código do certificado</label>
          <input
            id="codigo"
            name="codigo"
            autoComplete="off"
            maxLength={64}
            defaultValue={resultado.codigo}
            key={resultado.codigo}
          />
          <button className="s-botao" type="submit" disabled={consultando}>
            {consultando ? 'Consultando...' : 'Validar'}
          </button>
        </Form>
        <div role="status" aria-live="polite">
          <Mensagem r={resultado} />
        </div>
      </div>
    </div>
  );
}
`````

`web/site/app/routes.ts`:

`````ts
import { index, route, type RouteConfig } from '@react-router/dev/routes';

export default [
  index('rotas/inicio.tsx'),
  route('cursos', 'rotas/cursos.tsx'),
  route('cursos/:slug', 'rotas/curso.tsx'),
  route('certificados/validar', 'rotas/validar.tsx'),
  route('termos', 'rotas/termo.tsx', { id: 'termos' }),
  route('privacidade', 'rotas/termo.tsx', { id: 'privacidade' }),
  route('sitemap.xml', 'rotas/sitemap.ts'),
  route('robots.txt', 'rotas/robots.ts'),
  route('saude', 'rotas/saude.ts'),
  route('*', 'rotas/nao-encontrada.tsx'),
] satisfies RouteConfig;
`````

`web/site/app/seo.test.ts`:

`````ts
import { describe, expect, it } from 'vitest';
import {
  dadosCurso,
  filtrarCursos,
  jsonEmScript,
  metaTags,
  paragrafos,
  plural,
  preco,
  robots,
  sitemap,
  type Contexto,
  type CursoDetalhe,
} from './seo.ts';

const ctx: Contexto = {
  siteUrl: 'https://cursos.celke.uno',
  alunoUrl: 'https://aluno.celke.uno',
  indexavel: true,
  ano: 2026,
};

const curso: CursoDetalhe = {
  slug: 'java-do-zero',
  titulo: 'Java <do zero>',
  resumo: 'Aprenda Java.',
  capaUrl: '/api/cursos/java-do-zero/capa',
  descricao: 'Primeiro parágrafo.\n\nSegundo parágrafo.',
  modulos: [{ titulo: 'Módulo 1', aulas: [{ id: 1, titulo: 'Aula 1', gratuita: true }] }],
  ofertas: [],
};

describe('metaTags', () => {
  it('monta título, canônico, Open Graph e imagem', () => {
    const tags = metaTags(ctx, {
      caminho: '/cursos/java-do-zero/',
      titulo: curso.titulo,
      descricao: curso.resumo,
      imagem: curso.capaUrl,
    });
    expect(tags).toContainEqual({ title: 'Java <do zero> — Plataforma de Cursos' });
    expect(tags).toContainEqual({
      tagName: 'link',
      rel: 'canonical',
      href: 'https://cursos.celke.uno/cursos/java-do-zero/',
    });
    expect(tags).toContainEqual({
      property: 'og:image',
      content: 'https://cursos.celke.uno/api/cursos/java-do-zero/capa',
    });
    expect(tags).toContainEqual({ name: 'twitter:card', content: 'summary_large_image' });
    expect(tags).not.toContainEqual({ name: 'robots', content: 'noindex, nofollow' });
  });

  it('na home o nome vem primeiro; fora dos buscadores leva noindex', () => {
    const tags = metaTags(
      { ...ctx, indexavel: false },
      { caminho: '/', titulo: 'X', descricao: 'Y' },
    );
    expect(tags).toContainEqual({ title: 'Plataforma de Cursos — X' });
    expect(tags).toContainEqual({ name: 'robots', content: 'noindex, nofollow' });
    expect(tags).toContainEqual({ name: 'twitter:card', content: 'summary' });
  });
});

describe('dadosCurso', () => {
  it('Course sem ofertas', () => {
    const dados = dadosCurso(ctx, curso);
    expect(dados['@type']).toBe('Course');
    expect(dados).not.toHaveProperty('offers');
  });

  it('ofertas com preço e link de compra', () => {
    const dados = dadosCurso(ctx, {
      ...curso,
      ofertas: [{ id: 7, titulo: 'À vista', precoCentavos: 19700, moeda: 'BRL' }],
    });
    expect(dados).toMatchObject({
      offers: [
        {
          '@type': 'Offer',
          price: '197.00',
          priceCurrency: 'BRL',
          url: 'https://aluno.celke.uno/comprar/7',
        },
      ],
    });
  });

  it('JSON seguro dentro de <script>', () => {
    expect(jsonEmScript({ t: '</script><b>' })).not.toContain('<');
  });
});

describe('filtrarCursos', () => {
  const cursos = [
    { slug: 'a', titulo: 'Programação em Java', resumo: 'Do zero ao avançado', capaUrl: null },
    { slug: 'b', titulo: 'Excel', resumo: 'Planilhas e gráficos', capaUrl: null },
  ];

  it('sem acento, sem diferenciar maiúsculas, em título e resumo', () => {
    expect(filtrarCursos(cursos, 'PROGRAMACAO').map((c) => c.slug)).toEqual(['a']);
    expect(filtrarCursos(cursos, 'graficos').map((c) => c.slug)).toEqual(['b']);
  });

  it('todas as palavras precisam aparecer; busca vazia devolve tudo', () => {
    expect(filtrarCursos(cursos, 'java planilhas')).toEqual([]);
    expect(filtrarCursos(cursos, '   ')).toHaveLength(2);
  });
});

describe('textos', () => {
  it('preço em reais, plural e parágrafos', () => {
    expect(preco({ precoCentavos: 19700, moeda: 'BRL' })).toBe('R$ 197,00');
    expect(plural(1, 'aula', 'aulas')).toBe('1 aula');
    expect(plural(3, 'aula', 'aulas')).toBe('3 aulas');
    expect(paragrafos(curso.descricao)).toEqual(['Primeiro parágrafo.', 'Segundo parágrafo.']);
    expect(paragrafos('  ')).toEqual([]);
  });
});

describe('sitemap e robots', () => {
  it('sitemap com as páginas e os cursos', () => {
    const mapa = sitemap(ctx, [curso]);
    expect(mapa).toContain('<loc>https://cursos.celke.uno/cursos/java-do-zero/</loc>');
    expect(mapa).toContain('<loc>https://cursos.celke.uno/certificados/validar/</loc>');
  });

  it('robots aponta o sitemap; fora dos buscadores bloqueia tudo', () => {
    expect(robots(ctx)).toContain('Sitemap: https://cursos.celke.uno/sitemap.xml');
    expect(robots({ ...ctx, indexavel: false })).toBe('User-agent: *\nDisallow: /\n');
  });
});
`````

`web/site/app/seo.ts`:

`````ts
/**
 * Regras puras do site público: tipos da API, SEO (meta tags, dados estruturados, sitemap, robots),
 * preço e busca no catálogo. Sem acesso à rede: tudo testável sem servidor.
 */

export const NOME = 'Plataforma de Cursos';

/** Catálogo público da API (/api/cursos), espelhando os records do backend. */
export interface CursoCatalogo {
  slug: string;
  titulo: string;
  resumo: string;
  capaUrl: string | null;
}

/** Opção à venda da turma atual (no meio de pagamento ativo). */
export interface OfertaCatalogo {
  id: number;
  titulo: string;
  precoCentavos: number;
  moeda: string;
}

export interface CursoDetalhe extends CursoCatalogo {
  descricao: string | null;
  modulos: { titulo: string; aulas: { id: number; titulo: string; gratuita: boolean }[] }[];
  /** Vazia: nada à venda agora (o botão leva ao cadastro). */
  ofertas: OfertaCatalogo[];
}

export interface Termo {
  tipo: 'TERMOS_USO' | 'POLITICA_PRIVACIDADE';
  versao: number;
  conteudoHtml: string;
  publicadoEm: string;
}

export interface Certificado {
  nomeCompleto: string;
  cursoTitulo: string;
  turmaNome: string;
  emitidoEm: string;
  cargaHorariaHoras: number | null;
}

/** Endereços e regras do ambiente, vindos do servidor (root loader). */
export interface Contexto {
  /** Endereço público do site, sem barra no fim (ex.: https://cursos.celke.uno). */
  siteUrl: string;
  /** Área do aluno (entrar, criar conta, comprar). */
  alunoUrl: string;
  /** Homologação: fora dos buscadores. */
  indexavel: boolean;
  /** Ano do rodapé. */
  ano: number;
}

export interface Seo {
  caminho: string;
  titulo: string;
  descricao: string;
  imagem?: string | null;
}

/** Meta tags de uma página no formato do `meta` do React Router. */
export function metaTags(ctx: Contexto, seo: Seo) {
  const canonico = ctx.siteUrl + seo.caminho;
  const titulo = seo.caminho === '/' ? `${NOME} — ${seo.titulo}` : `${seo.titulo} — ${NOME}`;
  return [
    { title: titulo },
    { name: 'description', content: seo.descricao },
    ...(ctx.indexavel ? [] : [{ name: 'robots', content: 'noindex, nofollow' }]),
    { tagName: 'link', rel: 'canonical', href: canonico },
    { property: 'og:type', content: 'website' },
    { property: 'og:site_name', content: NOME },
    { property: 'og:locale', content: 'pt_BR' },
    { property: 'og:title', content: seo.titulo },
    { property: 'og:description', content: seo.descricao },
    { property: 'og:url', content: canonico },
    ...(seo.imagem ? [{ property: 'og:image', content: ctx.siteUrl + seo.imagem }] : []),
    { name: 'twitter:card', content: seo.imagem ? 'summary_large_image' : 'summary' },
  ];
}

export function preco(oferta: Pick<OfertaCatalogo, 'precoCentavos' | 'moeda'>): string {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: oferta.moeda }).format(
    oferta.precoCentavos / 100,
  );
}

export function plural(n: number, um: string, varios: string): string {
  return `${n} ${n === 1 ? um : varios}`;
}

/** Schema.org `Course`, com as ofertas quando houver (preço no resultado do Google). */
export function dadosCurso(ctx: Contexto, curso: CursoDetalhe) {
  const ofertas = curso.ofertas ?? [];
  return {
    '@context': 'https://schema.org',
    '@type': 'Course',
    name: curso.titulo,
    description: curso.resumo,
    url: `${ctx.siteUrl}/cursos/${curso.slug}/`,
    provider: { '@type': 'Organization', name: NOME, sameAs: ctx.siteUrl },
    ...(ofertas.length > 0
      ? {
          offers: ofertas.map((o) => ({
            '@type': 'Offer',
            name: o.titulo,
            price: (o.precoCentavos / 100).toFixed(2),
            priceCurrency: o.moeda,
            url: `${ctx.alunoUrl}/comprar/${o.id}`,
          })),
        }
      : {}),
  };
}

/** JSON para `<script type="application/ld+json">`: sem `<` cru, para não fechar a tag. */
export function jsonEmScript(dados: unknown): string {
  return JSON.stringify(dados).replace(/</g, '\\u003c');
}

/** Texto sem acento e em minúsculas, para a busca do catálogo. */
function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase();
}

/** Busca no catálogo por título e resumo, sem diferenciar acento e maiúsculas; todas as palavras. */
export function filtrarCursos(cursos: CursoCatalogo[], busca: string): CursoCatalogo[] {
  const termos = normalizar(busca).split(/\s+/).filter(Boolean);
  if (termos.length === 0) return cursos;
  return cursos.filter((c) => {
    const texto = normalizar(`${c.titulo} ${c.resumo}`);
    return termos.every((t) => texto.includes(t));
  });
}

/** Texto puro em parágrafos (linha em branco separa parágrafos). */
export function paragrafos(texto: string | null | undefined): string[] {
  if (!texto?.trim()) return [];
  return texto
    .trim()
    .split(/\n\s*\n/)
    .map((p) => p.trim());
}

function escaparXml(texto: string): string {
  return texto.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}

export function sitemap(ctx: Contexto, cursos: CursoCatalogo[]): string {
  const caminhos = [
    '/',
    '/cursos/',
    ...cursos.map((c) => `/cursos/${c.slug}/`),
    '/certificados/validar/',
    '/termos/',
    '/privacidade/',
  ];
  const urls = caminhos.map((c) => `  <url><loc>${escaparXml(ctx.siteUrl + c)}</loc></url>`);
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls.join('\n')}\n</urlset>\n`;
}

export function robots(ctx: Contexto): string {
  return ctx.indexavel
    ? `User-agent: *\nAllow: /\n\nSitemap: ${ctx.siteUrl}/sitemap.xml\n`
    : 'User-agent: *\nDisallow: /\n';
}
`````

`web/site/public/icone.svg`:

`````xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><rect width="32" height="32" rx="7" fill="#0b1f4b"/><path d="M12 9.5v13l10-6.5z" fill="#f7931e"/></svg>
`````

`web/site/react-router.config.ts`:

`````ts
import type { Config } from '@react-router/dev/config';

/** Site público com renderização no servidor: cada página chega pronta para os buscadores. */
export default {
  appDirectory: 'app',
  buildDirectory: '../dist/site',
  ssr: true,
} satisfies Config;
`````

`web/site/vite.config.ts`:

`````ts
import { reactRouter } from '@react-router/dev/vite';
import { defineConfig } from 'vite';

/**
 * Site público (cursos.*): React Router em modo framework, renderizado no servidor. No
 * desenvolvimento (`npm run dev:site`), as leituras vão para a API em API_URL (padrão: a do compose
 * local) e as imagens das capas, para `/api` dela.
 */
export default defineConfig({
  plugins: [reactRouter()],
  server: {
    port: 5175,
    strictPort: true,
    proxy: { '/api': process.env['API_URL'] ?? 'http://localhost:8080' },
  },
});
`````

### Dependências do front

Depois de gravar os arquivos, dentro de `web/`, rode `npm install` para instalar as dependências do `package.json` e gerar o `package-lock.json`.

## Antes de executar

Fase 20 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/site-layout
```

## Orientações para o usuário

Esta etapa tem passos que só o usuário faz, fora do código (no navegador, no painel da Hostinger, no DNS, no GitHub ou na VPS). Depois de gravar os arquivos e passar na **Verificação**, **mostre no terminal**, para o usuário, cada bloco abaixo **exatamente como está**, um de cada vez, na ordem. Depois de cada bloco, pare e espere o usuário confirmar que concluiu antes de mostrar o próximo. Quando ele informar um valor (o IP da VPS, por exemplo), use-o nos blocos seguintes no lugar de `<ip-da-vps>`.

### Caddy do site

```text
==> SITE EM REACT — ATUALIZAR O CADDY DEPOIS DO DEPLOY (docs/vps.md, seção 11)

O site passa a ser servido pelo container site, na porta 3000 (o web deixa de servir a 8083).
Depois do deploy desta etapa nos dois ambientes (homologação e produção), copie o Caddyfile de
infra/caddy/ para /opt/plataforma-cursos/caddy/ e, como deploy:

  cd /opt/plataforma-cursos
  docker compose -f caddy/compose.yml --env-file caddy/.env --project-directory caddy up -d --force-recreate

Até lá, cursos.* do ambiente já atualizado responde 502.

Para ver o site com conteúdo, crie cursos de exemplo no ambiente (no seu computador, dentro de web/):

  npm run cursos-exemplo -- https://homolog-adm.celke.uno
```

## Verificação

- No `web/`: format, lint, typecheck, test, build; com a stack de e2e, `npm run e2e` e
  `npm run lighthouse` (todas as páginas nas metas).
- `npm run cursos-exemplo -- http://adm.localhost:8088` com o admin do seed: os seis cursos aparecem em
  `http://cursos.localhost:8088` com capa; rodar de novo pula todos.
- `curl -s http://cursos.localhost:8088/cursos/ | grep '<h1'`: o HTML já vem renderizado.

## Depois de executar

```bash
git add web/ infra/ docs/ .github/ .claude/ .gitignore README.md
git commit -m "feat(site): renderizar o site no servidor com React e renovar o layout"
git push -u origin feature/site-layout
```

Abra o Pull Request de `feature/site-layout` para `homolog`.
