import { escolher } from '../support/telas.ts';
import { clienteApi, montarCurso } from '../support/api.ts';
import { PDF_FALSO, PNG } from '../support/amostras.ts';
import { stamp } from '../support/dados.ts';
import { expect, test } from '../support/sessoes.ts';

test.describe('Cursos', () => {
  test('formulário vazio mostra os erros e não chama a API', async ({ comoAdmin: page }) => {
    let chamou = false;
    page.on('request', (r) => {
      if (r.url().endsWith('/api/adm/cursos') && r.method() === 'POST') chamou = true;
    });
    await page.goto('/cursos');
    await page.getByRole('button', { name: 'Novo curso' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Salvar' }).click();

    await expect(page.getByText('Informe o título.')).toBeVisible();
    await expect(page.getByText('Informe o resumo.')).toBeVisible();
    expect(chamou).toBe(false);
  });

  test('slug inválido é barrado na tela e slug repetido é recusado pela API', async ({
    comoAdmin: page,
    request,
  }) => {
    const adm = await clienteApi(request);
    const existente = await adm.post<{ slug: string }>('/api/adm/cursos', {
      titulo: `Curso ${stamp()}`,
      resumo: 'Já existe.',
    });

    await page.goto('/cursos');
    await page.getByRole('button', { name: 'Novo curso' }).click();
    const form = page.getByRole('dialog');
    await form.getByRole('textbox', { name: 'Título', exact: true }).fill('Outro curso');
    await form.getByRole('textbox', { name: 'Resumo', exact: true }).fill('Resumo.');
    await form.getByRole('textbox', { name: 'Endereço (slug)' }).fill('Com Espaço');
    await form.getByRole('button', { name: 'Salvar' }).click();
    await expect(form.getByText('Use só letras minúsculas, números e hífens.')).toBeVisible();

    await form.getByRole('textbox', { name: 'Endereço (slug)' }).fill(existente.slug);
    const resposta = page.waitForResponse(
      (r) => r.url().endsWith('/api/adm/cursos') && r.request().method() === 'POST',
    );
    await form.getByRole('button', { name: 'Salvar' }).click();
    expect((await resposta).status()).toBe(409);
    await expect(
      form.getByText(`Já existe um curso com o slug "${existente.slug}".`),
    ).toBeVisible();
  });

  test('capa em formato errado não é enviada', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}`);
    await page
      .getByLabel('Enviar capa')
      .setInputFiles({ ...PNG, name: 'capa.gif', mimeType: 'image/gif' });
    await expect(page.getByText('Envie uma imagem JPG ou PNG.')).toBeVisible();
  });

  test('curso com turmas não pode ser excluído', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}`);
    await page.getByRole('button', { name: 'Excluir curso' }).click();
    await page
      .getByRole('dialog', { name: 'Remover registro?' })
      .getByRole('button', { name: 'Remover' })
      .click();
    await expect(
      page.getByText('O curso tem turmas. Exclua as turmas vazias ou arquive o curso.'),
    ).toBeVisible();
    await expect(page.getByRole('heading', { name: curso.titulo })).toBeVisible();
  });

  test('a lista filtra pelo status', async ({ comoAdmin: page, request }) => {
    const adm = await clienteApi(request);
    const titulo = `Arquivado ${stamp()}`;
    const curso = await adm.post<{ id: number }>('/api/adm/cursos', { titulo, resumo: 'r' });
    await adm.patch(`/api/adm/cursos/${curso.id}/status`, { status: 'ARQUIVADO' });

    await page.goto('/cursos');
    await escolher(page, 'Status', 'Arquivado');
    await expect(page.getByRole('cell', { name: titulo })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Rascunho' })).toHaveCount(0);
  });

  test('curso que não existe mostra o erro', async ({ comoAdmin: page }) => {
    await page.goto('/cursos/999999999');
    await expect(page.getByText('Não foi possível abrir o curso')).toBeVisible();
  });
});

test.describe('Conteúdo da turma', () => {
  test('reordena módulos e aulas', async ({ comoAdmin: page, request }) => {
    const adm = await clienteApi(request);
    const curso = await montarCurso(adm);
    await adm.post(`/api/adm/turmas/${curso.turmaId}/modulos`, { titulo: 'Módulo 2' });
    await adm.post(`/api/adm/modulos/${curso.moduloId}/aulas`, { titulo: 'Aula 2' });

    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}`);
    await expect(page.getByText('1. Módulo 1')).toBeVisible();
    await page.getByRole('button', { name: 'Descer Módulo 1' }).click();
    await expect(page.getByText('1. Módulo 2')).toBeVisible();
    await expect(page.getByText('2. Módulo 1')).toBeVisible();

    const aulas = page.getByRole('list', { name: 'Aulas de Módulo 1' });
    await expect(aulas.getByRole('listitem').first()).toContainText('Aula 1');
    await page.getByRole('button', { name: 'Subir Aula 2' }).click();
    await expect(aulas.getByRole('listitem').first()).toContainText('Aula 2');
  });

  test('módulo com aulas não pode ser excluído', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}`);
    await page.getByRole('button', { name: 'Excluir Módulo 1' }).click();
    await page
      .getByRole('dialog', { name: 'Remover registro?' })
      .getByRole('button', { name: 'Remover' })
      .click();
    await expect(
      page.getByText('O módulo tem aulas. Exclua as aulas ou inative o módulo.'),
    ).toBeVisible();
  });

  test('inativa e ativa um módulo', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}`);
    const modulo = page.getByLabel('Módulo Módulo 1');
    await page.getByRole('button', { name: 'Inativar Módulo 1' }).click();
    await expect(modulo.getByText('Inativo', { exact: true }).first()).toBeVisible();
    await page.getByRole('button', { name: 'Ativar Módulo 1' }).click();
    await expect(page.getByRole('button', { name: 'Inativar Módulo 1' })).toBeVisible();
  });

  test('título vazio no novo módulo é barrado', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}`);
    await page.getByRole('button', { name: 'Novo módulo' }).click();
    await page.getByRole('button', { name: 'Criar módulo' }).click();
    await expect(page.getByText('Informe título do módulo.')).toBeVisible();
  });
});

test.describe('Aula', () => {
  test('arquivo que não é vídeo é recusado na tela', async ({ comoAdmin: page, request }) => {
    const c = await montarCurso(await clienteApi(request));
    await page.goto(
      `/cursos/${c.cursoId}/turmas/${c.turmaId}/modulos/${c.moduloId}/aulas/${c.aulaId}`,
    );
    await expect(page.getByText('Esta aula ainda não tem vídeo.')).toBeVisible();
    await page.getByLabel('Enviar vídeo').setInputFiles(PDF_FALSO);
    await expect(page.getByText('Envie um vídeo MP4, MOV, MKV ou WebM.')).toBeVisible();
  });

  test('vídeo falso (extensão certa, conteúdo errado) é recusado pela API', async ({
    comoAdmin: page,
    request,
  }) => {
    const c = await montarCurso(await clienteApi(request));
    await page.goto(
      `/cursos/${c.cursoId}/turmas/${c.turmaId}/modulos/${c.moduloId}/aulas/${c.aulaId}`,
    );
    await page
      .getByLabel('Enviar vídeo')
      .setInputFiles({ ...PDF_FALSO, name: 'aula.mp4', mimeType: 'video/mp4' });
    await expect(page.getByText('O vídeo não foi enviado')).toBeVisible({ timeout: 30_000 });
  });

  test('edita os dados da aula e valida o título', async ({ comoAdmin: page, request }) => {
    const c = await montarCurso(await clienteApi(request));
    await page.goto(
      `/cursos/${c.cursoId}/turmas/${c.turmaId}/modulos/${c.moduloId}/aulas/${c.aulaId}`,
    );
    const titulo = page.getByRole('textbox', { name: 'Título', exact: true });
    await titulo.fill('');
    await titulo.blur();
    // Ao sair do campo vazio, a biblioteca avisa; o botão desce com o aviso
    await expect(page.getByText('Campo obrigatório')).toBeVisible();
    await page.getByRole('button', { name: 'Salvar aula' }).click();
    await expect(page.getByText('Informe o título.')).toBeVisible();

    await titulo.fill('Aula renomeada');
    await page.getByRole('checkbox', { name: /Aula gratuita/ }).check();
    await page.getByRole('button', { name: 'Salvar aula' }).click();
    await expect(page.getByRole('heading', { name: 'Aula renomeada' })).toBeVisible();
  });

  test('arquivo de apoio em formato não aceito', async ({ comoAdmin: page, request }) => {
    const c = await montarCurso(await clienteApi(request));
    await page.goto(
      `/cursos/${c.cursoId}/turmas/${c.turmaId}/modulos/${c.moduloId}/aulas/${c.aulaId}`,
    );
    const arquivos = page.getByRole('region', { name: 'Arquivos de apoio da aula' });
    await arquivos.getByLabel('Enviar arquivo').setInputFiles(PNG);
    await expect(page.getByText('Envie PDF, ZIP ou TXT.')).toBeVisible();
  });
});
