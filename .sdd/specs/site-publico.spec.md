---
feature: site-publico
versao: 1.1
status: aprovada
autor: pm-spec-architect
---

# Site público

## 1. Resumo Executivo

O site `cursos.celke.uno` apresenta a plataforma e o catálogo aos visitantes e aos buscadores: cada
curso publicado no painel aparece no site sozinho, com página própria, dados para os buscadores e boa
nota de desempenho e acessibilidade.

Na versão 1.1, as páginas passam a ser montadas no servidor a cada requisição, com um layout novo,
busca no catálogo e tema escuro.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Visitante | Navegar, ver os cursos, validar certificados, ler termos e privacidade |
| Buscador | Indexar a produção; a homologação fica fora |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE oferecer as páginas início, catálogo, curso, validar certificado, termos,
  privacidade e "não encontrada".
- **RF-02** — REMOVIDO (substituído pelo RF-07).
- **RF-03** — O sistema DEVE publicar título, descrição, endereço canônico, dados de compartilhamento e
  dados estruturados de curso em cada página.
- **RF-04** — O sistema DEVE publicar o mapa do site e as regras para buscadores, mantendo a homologação
  (`homolog-cursos.celke.uno`) fora dos buscadores.
- **RF-05** — O sistema DEVE atingir no Lighthouse (desktop) desempenho ≥ 90, acessibilidade ≥ 95, boas
  práticas ≥ 95 e SEO ≥ 90 em todas as páginas.
- **RF-06** — O sistema DEVE usar os tokens visuais da biblioteca de componentes da Celke.
- **RF-07** — O sistema DEVE montar cada página no servidor a cada requisição, com o HTML completo para
  os buscadores, e torná-la interativa no navegador.
- **RF-08** — O sistema DEVE guardar as leituras da API em cache curto (30 segundos) e responder 404 de
  verdade para curso inexistente.
- **RF-09** — O sistema DEVE aplicar uma política de conteúdo com valor único por requisição para os
  scripts.
- **RF-10** — O sistema DEVE apresentar topo fixo, abertura com ilustração, vantagens, cursos em
  destaque, como funciona, página do curso com coluna de compra fixa e conteúdo por módulo, rodapé e
  tema escuro.
- **RF-11** — O sistema DEVE buscar no catálogo direto no navegador, sem diferenciar acentos e
  maiúsculas.
- **RF-12** — O sistema DEVE usar o mesmo ícone da biblioteca de componentes nos três fronts.

## 4. Modelo de Domínio

Usa os cursos publicados (spec `catalogo-de-cursos`), a validação de certificado (spec `certificado`) e
os termos vigentes (spec `perfil-e-termos`).

## 5. Estados e Fluxos

- **Publicação:** curso publicado no painel → em até 30 segundos a página está no ar e no mapa.

## 6. Contratos de Interface

| Página | Caminho |
|---|---|
| Início | `/` |
| Catálogo | `/cursos` |
| Curso | `/cursos/<slug>` |
| Validar certificado | `/certificados/validar` |
| Termos / privacidade | `/termos`, `/privacidade` |
| Mapa do site / buscadores | `/sitemap.xml`, `/robots.txt` |

Endereço canônico: `https://cursos.celke.uno`.

## 7. Casos de Borda

- Curso despublicado: sai do site quando o cache curto vence.
- Endereço de curso inexistente: 404 de verdade, não uma página vazia.

## 8. Premissas Validadas

- A mesma imagem serve homologação e produção; o ambiente define os endereços.

## 9. Riscos

- **Conteúdo desatualizado:** cache de no máximo 30 segundos.
- **Script injetado:** política de conteúdo com valor único por requisição.
