import { request as criarRequest, type APIRequestContext, type Page } from '@playwright/test';
import { PNG } from '../support/amostras.ts';
import { clienteApi, montarCurso, type ClienteApi, type CursoMontado } from '../support/api.ts';
import { ADM_URL, ADMIN_SEED } from '../support/config.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA, stamp } from '../support/dados.ts';
import { entrarPelaApi, expect, test } from '../support/sessoes.ts';
import { aceitarTermosSePedir } from '../support/telas.ts';

/**
 * Fórum de dúvidas com imagens: o aluno anexa (escolhendo, arrastando ou colando um print), a imagem
 * vai direto ao armazenamento e a API a regrava sem metadados; aluno e moderação veem as imagens.
 */
let contexto: APIRequestContext;
let adm: ClienteApi;
let curso: CursoMontado;

test.beforeAll(async () => {
  contexto = await criarRequest.newContext();
  adm = await clienteApi(contexto);
  curso = await montarCurso(adm);
});

test.afterAll(async () => {
  await contexto.dispose();
});

const png = (nome: string) => ({ ...PNG, name: nome });

async function alunoNoForum(page: Page, request: APIRequestContext) {
  const email = await alunoConfirmado(request);
  // Começa ontem: o relógio da VM do Docker (Windows) às vezes volta alguns segundos
  await adm.post(`/api/adm/turmas/${curso.turmaId}/matriculas`, {
    email,
    formaPagamento: 'CORTESIA',
    inicio: new Date(Date.now() - 86_400_000).toISOString(),
  });
  await entrarPelaApi(page, email, SENHA_NOVA);
  await page.goto(`/turmas/${curso.turmaId}?aba=forum`);
  await aceitarTermosSePedir(page, 'turma');
  await page.getByRole('button', { name: 'Fazer uma pergunta' }).click();
}

/** Cola um print (imagem na área de transferência) no elemento. */
async function colarImagem(page: Page, seletor: string, nome: string) {
  await page.locator(seletor).evaluate(
    (alvo, { base64, nome }) => {
      const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
      const dados = new DataTransfer();
      dados.items.add(new File([bytes], nome, { type: 'image/png' }));
      alvo.dispatchEvent(
        new ClipboardEvent('paste', { clipboardData: dados, bubbles: true, cancelable: true }),
      );
    },
    { base64: PNG.buffer.toString('base64'), nome },
  );
}

async function imagensCarregadas(page: Page, lista: string) {
  const imagens = page.getByRole('list', { name: lista }).getByRole('img');
  await expect(imagens.first()).toBeVisible();
  await expect
    .poll(() =>
      imagens.evaluateAll((todas) => todas.every((i) => (i as HTMLImageElement).naturalWidth > 0)),
    )
    .toBe(true);
  return imagens;
}

test('pergunta com imagens, resposta com print colado e a moderação vê as imagens', async ({
  page,
  request,
  browser,
}) => {
  await alunoNoForum(page, request);
  const titulo = `Erro ao compilar ${stamp()}`;
  await page.getByRole('textbox', { name: 'Título da pergunta' }).fill(titulo);
  await page.getByRole('textbox', { name: 'Sua dúvida' }).fill('Segue o **print** do erro.');
  await page
    .getByLabel('Anexar imagens da pergunta')
    .setInputFiles([png('erro-1.png'), png('erro-2.png')]);
  await expect(page.getByRole('img', { name: 'Prévia de erro-1.png' })).toBeVisible();
  await expect(page.getByRole('img', { name: 'Prévia de erro-2.png' })).toBeVisible();

  const envios = page.waitForResponse('**/api/aluno/forum/anexos/envio');
  await page.getByRole('button', { name: 'Publicar pergunta' }).click();
  expect((await envios).status()).toBe(200);
  await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

  // As imagens publicadas carregam do armazenamento e abrem ampliadas
  const daPergunta = await imagensCarregadas(page, `Imagens de ${'Aluno do Playwright'}`);
  await expect(daPergunta).toHaveCount(2);
  await daPergunta.first().click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');

  // Resposta com um print colado direto no texto
  await page.getByRole('textbox', { name: 'Sua resposta' }).fill('Mais um print, agora colado.');
  await colarImagem(page, 'textarea', 'print.png');
  await expect(page.getByRole('img', { name: 'Prévia de print.png' })).toBeVisible();
  await page.getByRole('button', { name: 'Publicar resposta' }).click();
  await expect(page.getByText('Respostas (1)')).toBeVisible();
  const daResposta = page
    .getByRole('article', { name: 'Resposta de Aluno do Playwright' })
    .getByRole('img');
  await expect(daResposta).toHaveCount(1);
  // O formulário volta vazio
  await expect(page.getByRole('list', { name: 'Imagens anexadas' })).toHaveCount(0);

  // A moderação do ADM vê as imagens da pergunta e da resposta
  const contextoAdm = await browser.newContext({ baseURL: ADM_URL });
  const moderacao = await contextoAdm.newPage();
  await entrarPelaApi(moderacao, ADMIN_SEED);
  await moderacao.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=forum`);
  await moderacao.getByRole('cell', { name: titulo }).click();
  const painel = moderacao.getByRole('dialog');
  await expect(
    painel.getByRole('list', { name: 'Imagens de Aluno do Playwright' }).first().getByRole('img'),
  ).toHaveCount(2);
  await expect(
    painel.getByRole('article', { name: 'Resposta de Aluno do Playwright' }).getByRole('img'),
  ).toHaveCount(1);
  await contextoAdm.close();
});

test('recusa GIF e o que passa de 3 imagens, e permite remover', async ({ page, request }) => {
  await alunoNoForum(page, request);
  const campo = page.getByLabel('Anexar imagens da pergunta');

  await campo.setInputFiles({ ...PNG, name: 'animado.gif', mimeType: 'image/gif' });
  await expect(page.getByText('animado.gif: envie imagens JPG ou PNG.')).toBeVisible();

  await campo.setInputFiles([png('1.png'), png('2.png'), png('3.png'), png('4.png')]);
  await expect(page.getByText('4.png: anexe no máximo 3 imagens por mensagem.')).toBeVisible();
  await expect(
    page.getByRole('list', { name: 'Imagens anexadas' }).getByRole('listitem'),
  ).toHaveCount(3);
  await expect(page.getByRole('button', { name: 'Anexar imagem' })).toBeDisabled();

  await page.getByRole('button', { name: 'Remover 2.png' }).click();
  await expect(
    page.getByRole('list', { name: 'Imagens anexadas' }).getByRole('listitem'),
  ).toHaveCount(2);
  await expect(page.getByRole('button', { name: 'Anexar imagem' })).toBeEnabled();
});

test('imagem falsa é recusada pela API; o texto fica e dá para publicar sem ela', async ({
  page,
  request,
}) => {
  await alunoNoForum(page, request);
  const titulo = `Pergunta com imagem falsa ${stamp()}`;
  await page.getByRole('textbox', { name: 'Título da pergunta' }).fill(titulo);
  await page.getByRole('textbox', { name: 'Sua dúvida' }).fill('O arquivo diz ser PNG, mas não é.');
  await page.getByLabel('Anexar imagens da pergunta').setInputFiles({
    name: 'falso.png',
    mimeType: 'image/png',
    buffer: Buffer.from('não sou uma imagem'),
  });

  const publicacao = page.waitForResponse(
    (r) =>
      r.url().endsWith(`/api/aluno/turmas/${curso.turmaId}/forum`) &&
      r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Publicar pergunta' }).click();
  expect((await publicacao).status()).toBe(400);
  await expect(page.getByRole('alert').first()).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Título da pergunta' })).toHaveValue(titulo);

  await page.getByRole('button', { name: 'Remover falso.png' }).click();
  await page.getByRole('button', { name: 'Publicar pergunta' }).click();
  await expect(page.getByRole('heading', { name: titulo })).toBeVisible();
});
