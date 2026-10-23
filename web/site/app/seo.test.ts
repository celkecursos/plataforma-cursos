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
