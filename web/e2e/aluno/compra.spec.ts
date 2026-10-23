import { request as criarRequest, type APIRequestContext, type Page } from '@playwright/test';
import { clienteApi, montarCurso, type ClienteApi, type CursoMontado } from '../support/api.ts';
import { SITE_URL } from '../support/config.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA, stamp } from '../support/dados.ts';
import {
  criarOferta,
  hotmartAtiva,
  notificar,
  type HotmartConfigurada,
} from '../support/pagamentos.ts';
import { entrarPelaApi, expect, test } from '../support/sessoes.ts';
import { aceitarTermosSePedir } from '../support/telas.ts';

/**
 * Compra pela Hotmart: o botão "Comprar" do site leva ao checkout (a ida ao pay.hotmart.com é
 * interceptada), a notificação é mandada pelo teste como a Hotmart faria e a página de retorno
 * acompanha o pedido. O curso e a oferta são montados uma vez; cada teste usa um aluno novo.
 */
let contexto: APIRequestContext;
let adm: ClienteApi;
let curso: CursoMontado;
let slug: string;
let hotmart: HotmartConfigurada;
const produto = stamp();

test.beforeAll(async () => {
  contexto = await criarRequest.newContext();
  adm = await clienteApi(contexto);
  curso = await montarCurso(adm);
  slug = (await adm.get<{ slug: string }>(`/api/adm/cursos/${curso.cursoId}`)).slug;
  await adm.patch(`/api/adm/cursos/${curso.cursoId}/status`, { status: 'PUBLICADO' });
  hotmart = await hotmartAtiva(adm);
  await criarOferta(adm, curso.turmaId, produto);
});

test.afterAll(async () => {
  await contexto.dispose();
});

/** Clica em "Comprar" no site e devolve o checkout que iria para a Hotmart. */
async function comprarPeloSite(page: Page, email: string): Promise<URL> {
  let checkout: URL | undefined;
  await page.route('https://pay.hotmart.com/**', async (rota) => {
    checkout = new URL(rota.request().url());
    await rota.fulfill({ contentType: 'text/html', body: '<h1>Checkout da Hotmart</h1>' });
  });
  await expect(async () => {
    await page.goto(`${SITE_URL}/cursos/${slug}/?v=${Date.now()}`);
    await expect(page.getByText('R$ 197,00')).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 30_000 });
  await page.getByRole('link', { name: 'Comprar' }).click();
  // Abrir a página não cria pedido: o aluno confere o curso e o valor e confirma
  await expect(page.getByRole('heading', { name: 'Confirmar a compra' })).toBeVisible();
  await expect(page.getByText('R$ 197,00')).toBeVisible();
  await page.getByRole('button', { name: 'Ir para o pagamento' }).click();
  await expect(page.getByRole('heading', { name: 'Checkout da Hotmart' })).toBeVisible();
  expect(checkout?.pathname).toBe(`/${produto}`);
  expect(checkout?.searchParams.get('off')).toBe('e2e');
  expect(checkout?.searchParams.get('email')).toBe(email);
  expect(checkout?.searchParams.get('xcod')).toHaveLength(22);
  return checkout!;
}

async function alunoLogado(page: Page, request: APIRequestContext): Promise<string> {
  const email = await alunoConfirmado(request);
  await entrarPelaApi(page, email, SENHA_NOVA);
  return email;
}

test('compra aprovada: retorno confirma, o curso aparece; reembolso tira o curso', async ({
  page,
  request,
}) => {
  const email = await alunoLogado(page, request);
  const checkout = await comprarPeloSite(page, email);
  const pedido = checkout.searchParams.get('xcod')!;
  const transacao = `HP${stamp()}`;

  await notificar(request, hotmart, {
    evento: 'PURCHASE_APPROVED',
    produto,
    transacao,
    xcod: pedido,
  });
  await page.goto(`/pagamento/retorno?pedido=${pedido}`);
  await expect(page.getByRole('heading', { name: 'Pagamento confirmado' })).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole('button', { name: 'Ir para o curso' }).click();
  await aceitarTermosSePedir(page, 'turma');
  await expect(page.getByText('Aula 1')).toBeVisible();

  await page.goto('/');
  await aceitarTermosSePedir(page);
  await expect(page.getByText(curso.titulo)).toBeVisible();

  await notificar(request, hotmart, {
    evento: 'PURCHASE_REFUNDED',
    produto,
    transacao,
    xcod: pedido,
    ocorridoEm: Date.now() + 1_000,
  });
  await expect(async () => {
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Meus cursos' })).toBeVisible();
    await expect(page.getByText(curso.titulo)).toHaveCount(0, { timeout: 1_000 });
  }).toPass({ timeout: 30_000 });
});

test('boleto impresso: a página de retorno mostra que aguarda o pagamento', async ({
  page,
  request,
}) => {
  const email = await alunoLogado(page, request);
  const pedido = (await comprarPeloSite(page, email)).searchParams.get('xcod')!;

  await notificar(request, hotmart, {
    evento: 'PURCHASE_BILLET_PRINTED',
    produto,
    transacao: `HP${stamp()}`,
    xcod: pedido,
  });
  await page.goto(`/pagamento/retorno?pedido=${pedido}`);
  await expect(page.getByRole('heading', { name: 'Aguardando pagamento' })).toBeVisible();
  await expect(page.getByText(/você recebe um e-mail/)).toBeVisible();
});

test('sem login, Comprar leva à entrada e volta para a compra depois de entrar', async ({
  page,
  request,
}) => {
  const email = await alunoConfirmado(request);
  const oferta = (await contexto.get(`${SITE_URL}/api/cursos/${slug}`).then((r) => r.json())) as {
    ofertas: { id: number }[];
  };
  await page.goto(`/comprar/${oferta.ofertas[0]!.id}`);
  await expect(page).toHaveURL(/\/entrar\?voltar=%2Fcomprar%2F/);
  await page.route('https://pay.hotmart.com/**', (rota) =>
    rota.fulfill({ contentType: 'text/html', body: '<h1>Checkout da Hotmart</h1>' }),
  );
  await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(email);
  await page.getByRole('textbox', { name: 'Senha', exact: true }).fill(SENHA_NOVA);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.getByRole('button', { name: 'Ir para o pagamento' }).click();
  await expect(page.getByRole('heading', { name: 'Checkout da Hotmart' })).toBeVisible();
});
