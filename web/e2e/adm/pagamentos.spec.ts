import { request as criarRequest, type APIRequestContext } from '@playwright/test';
import { clienteApi, montarCurso, type ClienteApi } from '../support/api.ts';
import { emailNovo, stamp } from '../support/dados.ts';
import { criarOferta, hotmartAtiva, notificar } from '../support/pagamentos.ts';
import { expect, test } from '../support/sessoes.ts';
import { escolher } from '../support/telas.ts';

let contexto: APIRequestContext;
let adm: ClienteApi;

test.beforeAll(async () => {
  contexto = await criarRequest.newContext();
  adm = await clienteApi(contexto);
});

test.afterAll(async () => {
  await contexto.dispose();
});

test.describe('Meios de pagamento', () => {
  test('cadastra a Hotmart com validação, testa credencial errada, ativa e vende uma turma', async ({
    comoAdmin: page,
  }) => {
    const nome = `Hotmart ${stamp()}`;
    await page.goto('/pagamentos');
    await page.getByRole('button', { name: 'Novo meio de pagamento' }).click();
    await page.getByRole('textbox', { name: 'Nome' }).fill(nome);
    await page.getByRole('button', { name: 'Salvar', exact: true }).click();
    await expect(page.getByText('Informe Hottok.')).toBeVisible();

    await page.getByLabel('Hottok').fill('hottok-da-tela-123');
    await page.getByRole('textbox', { name: 'Client ID' }).fill('id-errado');
    await page.getByLabel('Client Secret').fill('segredo-errado');
    await page.getByLabel('Basic').fill('basic-errado');
    await page.getByRole('button', { name: 'Salvar', exact: true }).click();
    const cartao = page.locator('.lp-card').filter({ hasText: nome });
    await expect(cartao.getByText('configurado ••••-123')).toBeVisible();
    await expect(page.getByText('hottok-da-tela-123')).toHaveCount(0);
    await expect(cartao.getByText(/\/api\/pagamentos\/webhooks\//)).toBeVisible();

    await cartao.getByRole('button', { name: 'Testar credenciais' }).click();
    await expect(cartao.getByText('Credenciais recusadas')).toBeVisible();

    await cartao.getByRole('button', { name: 'Ativar' }).click();
    await expect(cartao.getByText('Ativo', { exact: true })).toBeVisible();

    // Aba "Venda" da turma
    const curso = await montarCurso(adm);
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=venda`);
    await page.getByRole('button', { name: 'Nova oferta' }).click();
    await page.getByRole('button', { name: 'Salvar oferta' }).click();
    await expect(page.getByText('Informe o id do produto.')).toBeVisible();
    await page.getByRole('textbox', { name: 'Título' }).fill('À vista');
    await page.getByRole('textbox', { name: 'Preço' }).fill('19700');
    await page.getByRole('textbox', { name: 'Id do produto' }).fill(stamp());
    await page.getByRole('textbox', { name: 'Código da oferta' }).fill('e2e');
    await page.getByRole('button', { name: 'Salvar oferta' }).click();
    await expect(page.getByRole('cell', { name: 'R$ 197,00' })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'À venda' })).toBeVisible();
  });

  test('evento de oferta não cadastrada é ignorado e reprocessado depois; o pedido aparece no painel', async ({
    comoAdmin: page,
    request,
  }) => {
    const hotmart = await hotmartAtiva(adm);
    const curso = await montarCurso(adm);
    const produto = stamp();
    const email = emailNovo();
    await notificar(request, hotmart, {
      evento: 'PURCHASE_APPROVED',
      produto,
      transacao: `HP${stamp()}`,
      email,
    });

    await page.goto('/pagamentos/eventos');
    await escolher(page, 'Situação', 'Ignorado');
    const linha = page.getByRole('row').filter({ hasText: `produto ${produto}` });
    await expect(async () => {
      await page.reload();
      await escolher(page, 'Situação', 'Ignorado');
      await expect(linha).toBeVisible({ timeout: 2_000 });
    }).toPass({ timeout: 30_000 });

    await criarOferta(adm, curso.turmaId, produto);
    await linha.click();
    const dialogo = page.getByRole('dialog', { name: 'PURCHASE_APPROVED' });
    await expect(dialogo.getByText('***09')).toBeVisible();
    await dialogo.getByRole('button', { name: 'Reprocessar' }).click();
    await expect(page.getByText('Evento na fila para reprocessar', { exact: true })).toBeVisible();

    await page.goto('/pagamentos/pedidos');
    await page.getByRole('textbox', { name: 'E-mail' }).fill(email);
    const pedido = page.getByRole('row').filter({ hasText: email });
    await expect(async () => {
      await page.reload();
      await page.getByRole('textbox', { name: 'E-mail' }).fill(email);
      await expect(pedido.getByText('Aprovado')).toBeVisible({ timeout: 2_000 });
    }).toPass({ timeout: 30_000 });
    await pedido.click();
    await expect(page.getByText('Venda sem checkout da plataforma')).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Processado' })).toBeVisible();
  });
});
