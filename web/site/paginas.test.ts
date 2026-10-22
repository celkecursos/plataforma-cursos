import { describe, expect, it } from 'vitest';
import { html, jsonEmScript, paragrafos } from './html.ts';
import {
  paginaCurso,
  paginaInicial,
  robots,
  sitemap,
  type Contexto,
  type CursoDetalhe,
} from './paginas.ts';

const ctx: Contexto = {
  siteUrl: 'https://cursos.celke.uno',
  alunoUrl: 'https://aluno.celke.uno',
  indexavel: true,
  ano: 2026,
};

const curso: CursoDetalhe = {
  slug: 'java-do-zero',
  titulo: 'Java <do zero>',
  resumo: 'Aprenda "Java" & Spring',
  descricao: 'Primeiro parágrafo.\n\nSegundo parágrafo.',
  capaUrl: '/api/cursos/java-do-zero/capa',
  modulos: [{ titulo: 'Introdução', aulas: [{ id: 1, titulo: 'Boas-vindas', gratuita: true }] }],
};

describe('html', () => {
  it('escapa o que vem de dados e mantém o que já é HTML', () => {
    expect(html`<p>${'<script>alert(1)</script>'}</p>`.toString()).toBe(
      '<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>',
    );
    const itens = ['a', 'b'].map((x) => html`<li>${x}</li>`);
    expect(
      html`<ul>
        ${itens}
      </ul>`
        .toString()
        .replace(/\s+/g, ''),
    ).toBe('<ul><li>a</li><li>b</li></ul>');
    expect(html`${null}${undefined}${false}`.toString()).toBe('');
  });

  it('parágrafos e JSON seguro dentro de script', () => {
    expect(paragrafos('um\n\ndois').toString()).toBe('<p>um</p><p>dois</p>');
    expect(jsonEmScript({ t: '</script>' }).toString()).toBe('{"t":"\\u003c/script>"}');
  });
});

describe('páginas', () => {
  it('página do curso com SEO: título, descrição, canônico, Open Graph e dados estruturados', () => {
    const pagina = paginaCurso(ctx, curso);
    expect(pagina).toContain('<title>Java &lt;do zero&gt; — Plataforma de Cursos</title>');
    expect(pagina).toContain(
      '<meta name="description" content="Aprenda &quot;Java&quot; &amp; Spring" />',
    );
    expect(pagina).toContain(
      '<link rel="canonical" href="https://cursos.celke.uno/cursos/java-do-zero/" />',
    );
    expect(pagina).toContain('content="https://cursos.celke.uno/api/cursos/java-do-zero/capa"');
    expect(pagina).toContain('"@type":"Course"');
    expect(pagina).toContain('Aula gratuita');
    expect(pagina).toContain('<p>Segundo parágrafo.</p>');
    expect(pagina).not.toContain('noindex');
  });

  it('homologação fica fora dos buscadores', () => {
    const fora = { ...ctx, indexavel: false };
    expect(paginaInicial(fora, [])).toContain('<meta name="robots" content="noindex, nofollow" />');
    expect(robots(fora)).toBe('User-agent: *\nDisallow: /\n');
    expect(robots(ctx)).toContain('Sitemap: https://cursos.celke.uno/sitemap.xml');
  });

  it('home sem cursos e sitemap com cada curso', () => {
    expect(paginaInicial(ctx, [])).toContain('Novos cursos em breve');
    const mapa = sitemap(ctx, [curso]);
    expect(mapa).toContain('<loc>https://cursos.celke.uno/cursos/java-do-zero/</loc>');
    expect(mapa).toContain('<loc>https://cursos.celke.uno/certificados/validar/</loc>');
  });
});
