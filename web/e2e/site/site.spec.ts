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
 * Site público gerado pelo container site a partir da API (na stack de e2e, a cada 3 s). Um curso
 * publicado pelo ADM aparece sozinho; rascunho nunca aparece.
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

/** Recarrega até o gerador incluir o que acabou de mudar na API (o parâmetro fura o cache de 60 s do HTML). */
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
