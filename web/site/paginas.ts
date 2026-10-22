import { confiavel, html, jsonEmScript, paragrafos, type Html } from './html.ts';

/** Dados do catálogo público da API (/api/cursos), espelhando os records do backend. */
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

export interface Contexto {
  /** Endereço público do site, sem barra no fim (ex.: https://cursos.celke.uno). */
  siteUrl: string;
  /** Área do aluno (entrar, criar conta). */
  alunoUrl: string;
  /** Homologação: fora dos buscadores. */
  indexavel: boolean;
  /** Ano do rodapé. */
  ano: number;
}

const NOME = 'Plataforma de Cursos';

interface Pagina {
  caminho: string;
  titulo: string;
  descricao: string;
  corpo: Html;
  imagem?: string | null;
  dadosEstruturados?: unknown;
}

function layout(ctx: Contexto, p: Pagina): string {
  const canonico = ctx.siteUrl + p.caminho;
  const titulo = p.caminho === '/' ? `${NOME} — ${p.titulo}` : `${p.titulo} — ${NOME}`;
  return `<!doctype html>\n${html`<html lang="pt-BR">
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1" />
      <title>${titulo}</title>
      <meta name="description" content="${p.descricao}" />
      ${ctx.indexavel ? '' : html`<meta name="robots" content="noindex, nofollow" />`}
      <link rel="canonical" href="${canonico}" />
      <meta property="og:type" content="website" />
      <meta property="og:site_name" content="${NOME}" />
      <meta property="og:locale" content="pt_BR" />
      <meta property="og:title" content="${p.titulo}" />
      <meta property="og:description" content="${p.descricao}" />
      <meta property="og:url" content="${canonico}" />
      ${p.imagem ? html`<meta property="og:image" content="${ctx.siteUrl + p.imagem}" />` : ''}
      <meta name="twitter:card" content="${p.imagem ? 'summary_large_image' : 'summary'}" />
      <link rel="icon" href="/assets/icone.svg" type="image/svg+xml" />
      <link rel="stylesheet" href="/assets/tokens.css" />
      <link rel="stylesheet" href="/assets/site.css" />
      ${
        p.dadosEstruturados
          ? html`<script type="application/ld+json">
              ${jsonEmScript(p.dadosEstruturados)}
            </script>`
          : ''
      }
    </head>
    <body>
      <a class="s-pular" href="#conteudo">Pular para o conteúdo</a>
      <header class="s-topo">
        <div class="s-largura s-topo-linha">
          <a class="s-marca" href="/">${NOME}</a>
          <nav aria-label="Principal" class="s-menu">
            <a href="/cursos/">Cursos</a>
            <a href="/certificados/validar/">Validar certificado</a>
            <a class="s-botao s-botao-claro" href="${ctx.alunoUrl}/entrar">Entrar</a>
          </nav>
        </div>
      </header>
      <main id="conteudo">${p.corpo}</main>
      <footer class="s-rodape">
        <div class="s-largura s-rodape-linha">
          <span>© ${ctx.ano} ${NOME}</span>
          <nav aria-label="Rodapé" class="s-menu">
            <a href="/termos/">Termos de uso</a>
            <a href="/privacidade/">Privacidade</a>
            <a href="/certificados/validar/">Validar certificado</a>
          </nav>
        </div>
      </footer>
    </body>
  </html>`}\n`;
}

function cartaoCurso(curso: CursoCatalogo): Html {
  return html`<li class="s-cartao">
    <a href="/cursos/${curso.slug}/" class="s-cartao-link">
      ${
        curso.capaUrl
          ? html`<img
              class="s-cartao-capa"
              src="${curso.capaUrl}"
              alt=""
              width="640"
              height="360"
              loading="lazy"
            />`
          : html`<span class="s-cartao-capa s-cartao-sem-capa" aria-hidden="true"></span>`
      }
      <span class="s-cartao-corpo">
        <span class="s-cartao-titulo">${curso.titulo}</span>
        <span class="s-cartao-resumo">${curso.resumo}</span>
      </span>
    </a>
  </li>`;
}

function listaCursos(cursos: CursoCatalogo[]): Html {
  if (cursos.length === 0) {
    return html`<p class="s-vazio">Novos cursos em breve. Volte logo!</p>`;
  }
  return html`<ul class="s-grade" role="list">
    ${cursos.map(cartaoCurso)}
  </ul>`;
}

export function paginaInicial(ctx: Contexto, cursos: CursoCatalogo[]): string {
  return layout(ctx, {
    caminho: '/',
    titulo: 'Aprenda no seu ritmo',
    descricao:
      'Cursos online com aulas em vídeo, materiais de apoio, fórum com o instrutor e certificado.',
    corpo: html`<section class="s-heroi">
        <div class="s-largura">
          <h1>Aprenda no seu ritmo</h1>
          <p class="s-heroi-texto">
            Aulas em vídeo para assistir de qualquer lugar, materiais de apoio, dúvidas respondidas
            pelo instrutor e certificado com código de validação.
          </p>
          <p class="s-acoes">
            <a class="s-botao" href="/cursos/">Ver os cursos</a>
            <a class="s-botao s-botao-contorno" href="${ctx.alunoUrl}/cadastro">Criar conta</a>
          </p>
        </div>
      </section>
      <section class="s-largura s-secao" aria-labelledby="titulo-cursos">
        <h2 id="titulo-cursos">Cursos</h2>
        ${listaCursos(cursos.slice(0, 6))}
        ${cursos.length > 6 ? html`<p><a href="/cursos/">Ver todos os ${cursos.length} cursos</a></p>` : ''}
      </section>
      <section class="s-largura s-secao" aria-labelledby="titulo-como">
        <h2 id="titulo-como">Como funciona</h2>
        <ol class="s-passos">
          <li><strong>Crie a sua conta</strong> e confirme o e-mail.</li>
          <li><strong>Matricule-se</strong> no curso e acesse as aulas pela área do aluno.</li>
          <li>
            <strong>Assista no seu tempo:</strong> o progresso fica salvo e você continua de onde
            parou.
          </li>
          <li>
            <strong>Conclua e emita o certificado</strong>, que qualquer pessoa pode validar aqui no
            site.
          </li>
        </ol>
      </section>`,
  });
}

export function paginaCatalogo(ctx: Contexto, cursos: CursoCatalogo[]): string {
  return layout(ctx, {
    caminho: '/cursos/',
    titulo: 'Cursos',
    descricao: `Conheça os ${cursos.length} cursos disponíveis na ${NOME}.`,
    corpo: html`<div class="s-largura s-secao">
      <h1>Cursos</h1>
      ${listaCursos(cursos)}
    </div>`,
  });
}

export function preco(oferta: OfertaCatalogo): string {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: oferta.moeda }).format(
    oferta.precoCentavos / 100,
  );
}

/** Preço e "Comprar" de cada oferta; sem oferta, o convite para criar a conta. */
function acoesCurso(ctx: Contexto, curso: CursoDetalhe): Html {
  const ofertas = curso.ofertas ?? [];
  if (ofertas.length === 0) {
    return html`<p class="s-acoes">
      <a class="s-botao" href="${ctx.alunoUrl}/cadastro">Quero fazer este curso</a>
      <a class="s-botao s-botao-contorno" href="${ctx.alunoUrl}/entrar">Já sou aluno</a>
    </p>`;
  }
  return html`<ul class="s-ofertas" role="list">
      ${ofertas.map(
        (o) =>
          html`<li class="s-oferta">
            <span class="s-oferta-titulo">${o.titulo}</span>
            <strong class="s-oferta-preco">${preco(o)}</strong>
            <a class="s-botao" href="${ctx.alunoUrl}/comprar/${o.id}">Comprar</a>
          </li>`,
      )}
    </ul>
    <p class="s-detalhe">Já é aluno? <a href="${ctx.alunoUrl}/entrar">Entre na sua conta</a>.</p>`;
}

export function paginaCurso(ctx: Contexto, curso: CursoDetalhe): string {
  const totalAulas = curso.modulos.reduce((total, m) => total + m.aulas.length, 0);
  const caminho = `/cursos/${curso.slug}/`;
  return layout(ctx, {
    caminho,
    titulo: curso.titulo,
    descricao: curso.resumo,
    imagem: curso.capaUrl,
    dadosEstruturados: {
      '@context': 'https://schema.org',
      '@type': 'Course',
      name: curso.titulo,
      description: curso.resumo,
      url: ctx.siteUrl + caminho,
      provider: { '@type': 'Organization', name: NOME, sameAs: ctx.siteUrl },
      ...((curso.ofertas ?? []).length > 0
        ? {
            offers: curso.ofertas.map((o) => ({
              '@type': 'Offer',
              name: o.titulo,
              price: (o.precoCentavos / 100).toFixed(2),
              priceCurrency: o.moeda,
              url: `${ctx.alunoUrl}/comprar/${o.id}`,
            })),
          }
        : {}),
    },
    corpo: html`<article class="s-largura s-secao">
      <nav aria-label="Trilha" class="s-trilha">
        <a href="/cursos/">Cursos</a> › <span>${curso.titulo}</span>
      </nav>
      <div class="s-curso-topo">
        <div>
          <h1>${curso.titulo}</h1>
          <p class="s-heroi-texto">${curso.resumo}</p>
          <p class="s-detalhe">${curso.modulos.length} módulos · ${totalAulas} aulas</p>
          ${acoesCurso(ctx, curso)}
        </div>
        ${curso.capaUrl ? html`<img class="s-curso-capa" src="${curso.capaUrl}" alt="Capa do curso ${curso.titulo}" width="640" height="360" />` : ''}
      </div>
      ${
        curso.descricao
          ? html`<section aria-labelledby="titulo-sobre">
              <h2 id="titulo-sobre">Sobre o curso</h2>
              ${paragrafos(curso.descricao)}
            </section>`
          : ''
      }
      <section aria-labelledby="titulo-conteudo">
        <h2 id="titulo-conteudo">Conteúdo</h2>
        ${
          curso.modulos.length === 0
            ? html`<p class="s-vazio">O conteúdo deste curso está em preparação.</p>`
            : html`<ol class="s-modulos">
                ${curso.modulos.map(
                  (m) =>
                    html`<li>
                      <h3>${m.titulo}</h3>
                      <ol class="s-aulas">
                        ${m.aulas.map((a) => html`<li>${a.titulo}${a.gratuita ? html` <span class="s-selo">Aula gratuita</span>` : ''}</li>`)}
                      </ol>
                    </li>`,
                )}
              </ol>`
        }
      </section>
    </article>`,
  });
}

export function paginaValidar(ctx: Contexto): string {
  return layout(ctx, {
    caminho: '/certificados/validar/',
    titulo: 'Validar certificado',
    descricao:
      'Confira se um certificado foi emitido pela plataforma usando o código impresso nele.',
    corpo: html`<div class="s-largura s-secao s-estreito">
      <h1>Validar certificado</h1>
      <p>Informe o código impresso no certificado para conferir os dados dele.</p>
      <form id="validar" class="s-form" novalidate>
        <label for="codigo">Código do certificado</label>
        <input id="codigo" name="codigo" autocomplete="off" required maxlength="64" />
        <button class="s-botao" type="submit">Validar</button>
      </form>
      <div id="resultado" role="status" aria-live="polite"></div>
      <script src="/assets/validar.js" defer></script>
    </div>`,
  });
}

export function paginaTermo(
  ctx: Contexto,
  termo: Termo | undefined,
  caminho: '/termos/' | '/privacidade/',
): string {
  const titulo = caminho === '/termos/' ? 'Termos de uso' : 'Política de privacidade';
  return layout(ctx, {
    caminho,
    titulo,
    descricao: `${titulo} da ${NOME}.`,
    corpo: html`<article class="s-largura s-secao s-estreito s-texto">
      <h1>${titulo}</h1>
      ${
        termo
          ? html`<p class="s-detalhe">
                Versão ${termo.versao}, publicada em
                ${new Date(termo.publicadoEm).toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' })}.
              </p>
              ${confiavel(termo.conteudoHtml)}`
          : html`<p class="s-vazio">Em breve.</p>`
      }
    </article>`,
  });
}

export function paginaNaoEncontrada(ctx: Contexto): string {
  return layout(ctx, {
    caminho: '/404.html',
    titulo: 'Página não encontrada',
    descricao: 'O endereço não existe ou mudou.',
    corpo: html`<div class="s-largura s-secao">
      <h1>Página não encontrada</h1>
      <p>O endereço não existe ou mudou. <a href="/cursos/">Veja os cursos</a>.</p>
    </div>`,
  });
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
  return `<?xml version="1.0" encoding="UTF-8"?>\n${html`<urlset
    xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"
  >
    ${caminhos.map((c) => html` <url><loc>${ctx.siteUrl + c}</loc></url> `)}</urlset
  >`}\n`;
}

export function robots(ctx: Contexto): string {
  return ctx.indexavel
    ? `User-agent: *\nAllow: /\n\nSitemap: ${ctx.siteUrl}/sitemap.xml\n`
    : 'User-agent: *\nDisallow: /\n';
}
