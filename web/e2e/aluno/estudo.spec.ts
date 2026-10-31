import { readFileSync } from 'node:fs';
import { request as criarRequest, type APIRequestContext, type Page } from '@playwright/test';
import { TXT, VIDEO_3S } from '../support/amostras.ts';
import {
  clienteAluno,
  clienteApi,
  enviarVideo,
  montarCurso,
  type ClienteApi,
  type CursoMontado,
} from '../support/api.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA } from '../support/dados.ts';
import { entrarPelaApi, expect, test } from '../support/sessoes.ts';
import { aceitarTermosSePedir } from '../support/telas.ts';

/**
 * Estudo pelo aluno: um curso com duas aulas (a primeira com vídeo e arquivo de apoio), montado uma
 * vez pela API do ADM. Cada teste usa um aluno novo, matriculado, para o progresso não se misturar.
 */
let contexto: APIRequestContext;
let adm: ClienteApi;
let curso: CursoMontado;
let segundaAula: number;

test.beforeAll(async () => {
  test.setTimeout(180_000);
  contexto = await criarRequest.newContext();
  adm = await clienteApi(contexto);
  curso = await montarCurso(adm);
  segundaAula = (
    await adm.post<{ id: number }>(`/api/adm/modulos/${curso.moduloId}/aulas`, { titulo: 'Aula 2' })
  ).id;
  await enviarVideo(contexto, adm, curso.aulaId, readFileSync(VIDEO_3S));
  const envio = await adm.post<{ chave: string; url: string; contentType: string }>(
    `/api/adm/aulas/${curso.aulaId}/arquivos/envio`,
    { contentType: TXT.mimeType, tamanho: TXT.buffer.length },
  );
  await contexto.put(envio.url, {
    data: TXT.buffer,
    headers: { 'Content-Type': envio.contentType },
  });
  await adm.post(`/api/adm/aulas/${curso.aulaId}/arquivos`, {
    chave: envio.chave,
    titulo: 'Roteiro',
    nomeArquivo: TXT.name,
  });
});

test.afterAll(async () => {
  await contexto.dispose();
});

/** Aluno novo, matriculado na turma e com a sessão aberta na página. */
async function alunoMatriculado(page: Page, request: APIRequestContext) {
  const email = await alunoConfirmado(request);
  // Começa ontem: o relógio da VM do Docker (Windows) às vezes volta alguns segundos
  await adm.post(`/api/adm/turmas/${curso.turmaId}/matriculas`, {
    email,
    formaPagamento: 'CORTESIA',
    inicio: new Date(Date.now() - 86_400_000).toISOString(),
  });
  await entrarPelaApi(page, email, SENHA_NOVA);
  return email;
}

async function abrirAula(page: Page, aulaId = curso.aulaId) {
  await page.goto(`/turmas/${curso.turmaId}/aulas/${aulaId}`);
  await aceitarTermosSePedir(page, 'aula');
}

test('do curso à aula: assiste o vídeo até o fim e o progresso da turma sobe', async ({
  page,
  request,
}) => {
  await alunoMatriculado(page, request);
  await page.goto('/');
  await aceitarTermosSePedir(page);
  await page
    .getByLabel(curso.titulo, { exact: true })
    .getByRole('button', { name: 'Continuar' })
    .click();
  await expect(page.getByText('0 de 2 aulas vistas')).toBeVisible();

  await page.getByRole('link', { name: 'Aula 1' }).click();
  const video = page.getByLabel('Vídeo da aula');
  await expect(video).toBeVisible();
  const concluida = page.waitForResponse(
    (r) =>
      r.url().endsWith(`/aulas/${curso.aulaId}/progresso`) &&
      r.request().method() === 'PATCH' &&
      r.request().postData()!.includes('vista'),
  );
  await video.evaluate((v: HTMLVideoElement) => {
    v.muted = true;
    return v.play();
  });
  expect((await concluida).status()).toBe(200);
  await expect(page.getByText('Aula vista')).toBeVisible();

  await page.getByRole('button', { name: 'Voltar ao curso' }).click();
  await expect(page.getByText('1 de 2 aulas vistas')).toBeVisible();
  await expect(
    page.getByRole('list', { name: 'Aulas de Módulo 1' }).getByText('Vista'),
  ).toBeVisible();
});

test('retoma o vídeo de onde parou', async ({ page, request }) => {
  const email = await alunoMatriculado(page, request);
  const aluno = await clienteAluno(request, email, SENHA_NOVA);
  await aluno.patch(`/api/aluno/aulas/${curso.aulaId}/progresso`, { posicaoSegundos: 1 });

  await abrirAula(page);
  const video = page.getByLabel('Vídeo da aula');
  await expect
    .poll(() => video.evaluate((v: HTMLVideoElement) => v.currentTime))
    .toBeGreaterThanOrEqual(1);
});

test('marca como vista, desmarca e guarda para revisar', async ({ page, request }) => {
  await alunoMatriculado(page, request);
  await abrirAula(page, segundaAula);
  await expect(page.getByText('Esta aula não tem vídeo.')).toBeVisible();

  await page.getByRole('button', { name: 'Marcar como vista' }).click();
  await expect(page.getByText('Aula vista')).toBeVisible();
  await page.getByRole('button', { name: 'Desmarcar como vista' }).click();
  await expect(page.getByRole('button', { name: 'Marcar como vista' })).toBeVisible();

  await page.getByRole('button', { name: 'Revisar depois' }).click();
  await expect(page.getByRole('button', { name: 'Tirar de revisar' })).toBeVisible();
  await page.getByRole('button', { name: 'Voltar ao curso' }).click();
  await expect(
    page.getByRole('list', { name: 'Aulas de Módulo 1' }).getByText('Revisar'),
  ).toBeVisible();
});

test('anterior e próxima levam pelas aulas da turma', async ({ page, request }) => {
  await alunoMatriculado(page, request);
  await abrirAula(page);
  await expect(page.getByRole('button', { name: 'Primeira aula' })).toBeDisabled();
  await page.getByRole('button', { name: 'Próxima: Aula 2' }).click();
  await expect(page.getByRole('heading', { name: 'Aula 2' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Última aula' })).toBeDisabled();
  await page.getByRole('button', { name: 'Anterior: Aula 1' }).click();
  await expect(page.getByRole('heading', { name: 'Aula 1' })).toBeVisible();
});

test('baixa o arquivo de apoio da aula', async ({ page, request }) => {
  await alunoMatriculado(page, request);
  await abrirAula(page);
  const arquivos = page.getByRole('region', { name: 'Arquivos da aula' });
  const link = page.waitForResponse(
    (r) => r.url().includes('/api/aluno/arquivos/') && r.url().endsWith('/download'),
  );
  // O navegador vai direto ao armazenamento pela URL assinada que a API devolveu
  const baixado = page.waitForEvent('download');
  await arquivos.getByRole('button', { name: /^Roteiro/ }).click();
  expect((await link).status()).toBe(200);
  const download = await baixado;
  expect(download.url()).toContain('media.localhost');
  expect(readFileSync(await download.path(), 'utf8')).toContain(
    'Roteiro da aula gerado pelo teste.',
  );
});

test('pergunta no fórum da aula, valida e responde', async ({ page, request }) => {
  await alunoMatriculado(page, request);
  await abrirAula(page);
  const forum = page
    .getByRole('region', { name: 'Dúvidas sobre esta aula' })
    .or(page.locator('body'));
  await forum.getByRole('button', { name: 'Fazer uma pergunta' }).click();
  await page.getByRole('button', { name: 'Publicar pergunta' }).click();
  await expect(page.getByText('Dê um título à pergunta.')).toBeVisible();
  await expect(page.getByText('Descreva a sua dúvida.')).toBeVisible();

  await page.getByRole('textbox', { name: 'Título da pergunta' }).fill('Não entendi o exemplo');
  await page
    .getByRole('textbox', { name: 'Sua dúvida' })
    .fill('Pode explicar o **segundo** passo?');
  await page.getByRole('button', { name: 'Publicar pergunta' }).click();
  await expect(page.getByRole('heading', { name: 'Não entendi o exemplo' })).toBeVisible();
  await expect(page.getByText('segundo')).toBeVisible();
  await expect(page.getByText('Nenhuma resposta ainda')).toBeVisible();

  await page.getByRole('textbox', { name: 'Sua resposta' }).fill('Complementando a pergunta.');
  await page.getByRole('button', { name: 'Publicar resposta' }).click();
  await expect(page.getByText('Respostas (1)')).toBeVisible();

  await page.getByRole('button', { name: 'Ir para a aula' }).click();
  await expect(page.getByRole('heading', { name: 'Aula 1' })).toBeVisible();
  await expect(page.getByText('Não entendi o exemplo')).toBeVisible();
});

test('certificado: bloqueado até concluir, depois emitido, baixado e validado', async ({
  page,
  request,
}) => {
  const email = await alunoMatriculado(page, request);
  await page.goto(`/turmas/${curso.turmaId}?aba=certificado`);
  await aceitarTermosSePedir(page, 'turma');
  await expect(page.getByText(/O certificado é liberado ao ver \d+% das aulas/)).toBeVisible();

  const aluno = await clienteAluno(request, email, SENHA_NOVA);
  for (const aula of [curso.aulaId, segundaAula]) {
    await aluno.patch(`/api/aluno/aulas/${aula}/progresso`, { vista: true });
  }
  await page.reload();
  const nome = page.getByRole('textbox', { name: 'Nome completo no certificado' });
  await nome.fill('');
  await nome.blur();
  await expect(page.getByText('Campo obrigatório')).toBeVisible();
  await page.getByRole('button', { name: 'Emitir certificado' }).click();
  await expect(page.getByText('Informe o nome completo.')).toBeVisible();

  await nome.fill('Aluna Playwright da Silva');
  await page.getByRole('button', { name: 'Emitir certificado' }).click();
  const cartao = page
    .getByRole('region', { name: `Certificado de ${curso.titulo}` })
    .or(page.getByText('Certificado emitido'));
  await expect(cartao.first()).toBeVisible();
  const codigo = await page.getByText('Código de validação').locator('..').innerText();

  const pdf = page.waitForResponse(
    (r) => r.url().includes('/api/aluno/certificados/') && r.url().endsWith('/download'),
  );
  const novaAba = page.waitForEvent('popup');
  await page.getByRole('button', { name: 'Baixar PDF' }).click();
  expect((await pdf).status()).toBe(200);
  await (await novaAba).close();

  await page.goto('/certificados');
  await expect(page.getByText('Aluna Playwright da Silva')).toBeVisible();
  const valor = codigo.replace('Código de validação', '').trim();
  const validacao = await request.get(`/api/certificados/${valor}`);
  expect(validacao.ok()).toBe(true);
  expect((await validacao.json()).nomeCompleto).toBe('Aluna Playwright da Silva');
});

test.describe('Caminhos infelizes', () => {
  test('turma sem matrícula não abre', async ({ comoAluno: page }) => {
    await page.goto(`/turmas/${curso.turmaId}`);
    await aceitarTermosSePedir(page, 'turma');
    await expect(page.getByText('Não foi possível abrir o curso')).toBeVisible();
    await expect(page.getByText(/não tem acesso a esta turma/)).toBeVisible();
  });

  test('aula paga sem matrícula não abre', async ({ comoAluno: page }) => {
    await page.goto(`/turmas/${curso.turmaId}/aulas/${curso.aulaId}`);
    await aceitarTermosSePedir(page, 'aula');
    await expect(page.getByText('Não foi possível abrir a aula')).toBeVisible();
  });

  test('matrícula que ainda não começou fica em "Começa em breve", sem acesso', async ({
    page,
    request,
  }) => {
    const email = await alunoConfirmado(request);
    await adm.post(`/api/adm/turmas/${curso.turmaId}/matriculas`, {
      email,
      formaPagamento: 'CORTESIA',
      inicio: '2099-01-01T03:00:00Z',
    });
    await entrarPelaApi(page, email, SENHA_NOVA);
    await page.goto('/');
    await aceitarTermosSePedir(page);
    const card = page.getByLabel(curso.titulo, { exact: true });
    await expect(card.getByText('Começa em breve')).toBeVisible();
    await expect(card.getByRole('button', { name: 'Continuar' })).toHaveCount(0);
  });

  test('arquiva e desarquiva um curso', async ({ page, request }) => {
    await alunoMatriculado(page, request);
    await page.goto('/');
    await aceitarTermosSePedir(page);
    await page
      .getByLabel(curso.titulo, { exact: true })
      .getByRole('button', { name: 'Arquivar' })
      .click();
    await expect(page.getByText('Você ainda não tem cursos')).toBeVisible();
    await page.getByRole('tab', { name: 'Arquivados' }).click();
    await page
      .getByLabel(curso.titulo, { exact: true })
      .getByRole('button', { name: 'Desarquivar' })
      .click();
    await expect(page.getByText('Nenhum curso aqui')).toBeVisible();
    await page.getByRole('tab', { name: 'Ativos' }).click();
    await expect(page.getByLabel(curso.titulo, { exact: true })).toBeVisible();
  });
});
