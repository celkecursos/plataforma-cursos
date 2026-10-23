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
