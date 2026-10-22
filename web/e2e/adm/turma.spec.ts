import { clienteAluno, clienteApi, montarCurso } from '../support/api.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { emailNovo, SENHA_NOVA, stamp } from '../support/dados.ts';
import { expect, test } from '../support/sessoes.ts';

test.describe('Matrículas', () => {
  test('matricula, altera o período e cancela', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    const email = await alunoConfirmado(request);

    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=matriculas`);
    await page.getByRole('button', { name: 'Matricular aluno' }).click();
    await page.getByRole('textbox', { name: 'E-mail do aluno' }).fill(email);
    await page.getByRole('button', { name: 'Matricular', exact: true }).click();
    await expect(page.getByRole('cell', { name: email })).toBeVisible();

    await page.getByRole('button', { name: 'Alterar período de Aluno do Playwright' }).click();
    await page.getByRole('textbox', { name: 'Fim' }).fill('2099-12-31');
    await page.getByRole('button', { name: 'Salvar período' }).click();
    await expect(page.getByRole('cell', { name: '31/12/2099' })).toBeVisible();

    await page.getByRole('button', { name: 'Cancelar matrícula de Aluno do Playwright' }).click();
    await page
      .getByRole('dialog', { name: 'Cancelar matrícula?' })
      .getByRole('button', { name: 'Cancelar matrícula' })
      .click();
    await expect(page.getByRole('cell', { name: 'Cancelada' })).toBeVisible();
  });

  test('e-mail sem conta é recusado pela API', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=matriculas`);
    await page.getByRole('button', { name: 'Matricular aluno' }).click();
    await page.getByRole('textbox', { name: 'E-mail do aluno' }).fill(emailNovo());
    await page.getByRole('button', { name: 'Matricular', exact: true }).click();
    await expect(page.getByText('Usuário não encontrado.')).toBeVisible();
  });

  test('e-mail inválido e fim antes do início são barrados na tela', async ({
    comoAdmin: page,
    request,
  }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=matriculas`);
    await page.getByRole('button', { name: 'Matricular aluno' }).click();
    await page.getByRole('textbox', { name: 'E-mail do aluno' }).fill('sem-arroba');
    await page.getByRole('textbox', { name: 'Início' }).fill('2026-05-10');
    await page.getByRole('textbox', { name: 'Fim' }).fill('2026-05-01');
    await page.getByRole('button', { name: 'Matricular', exact: true }).click();
    await expect(page.getByText('Informe um e-mail válido.')).toBeVisible();
    await expect(page.getByText('O fim deve ser depois do início.')).toBeVisible();
  });

  test('turma encerrada não recebe matrícula', async ({ comoAdmin: page, request }) => {
    const adm = await clienteApi(request);
    const curso = await montarCurso(adm);
    await adm.patch(`/api/adm/turmas/${curso.turmaId}/status`, { status: 'ENCERRADA' });
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=matriculas`);
    await expect(
      page.getByText('A turma está encerrada e não recebe novas matrículas.'),
    ).toBeVisible();
    await expect(page.getByRole('button', { name: 'Matricular aluno' })).toBeDisabled();
  });
});

test.describe('Fórum', () => {
  test('o admin responde, fixa e oculta um tópico do aluno', async ({
    comoAdmin: page,
    request,
  }) => {
    const adm = await clienteApi(request);
    const curso = await montarCurso(adm);
    const email = await alunoConfirmado(request);
    await adm.post(`/api/adm/turmas/${curso.turmaId}/matriculas`, {
      email,
      formaPagamento: 'CORTESIA',
    });
    const aluno = await clienteAluno(request, email, SENHA_NOVA);
    const titulo = `Dúvida ${stamp()}`;
    await aluno.post(`/api/aluno/turmas/${curso.turmaId}/forum`, {
      titulo,
      corpo: 'Como **funciona**?',
    });

    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=forum`);
    await expect(page.getByRole('cell', { name: 'Aguardando resposta' })).toBeVisible();
    await page.getByRole('cell', { name: titulo }).click();
    const painel = page.getByRole('dialog');
    await expect(painel.getByText('funciona')).toBeVisible();

    await painel.getByRole('button', { name: 'Publicar resposta' }).click();
    await expect(painel.getByText('Escreva a resposta.')).toBeVisible();
    await painel
      .getByRole('textbox', { name: 'Responder como instrutor' })
      .fill('Assim: passo a passo.');
    await painel.getByRole('button', { name: 'Publicar resposta' }).click();
    await expect(painel.getByText('Assim: passo a passo.')).toBeVisible();
    await expect(painel.getByText('Instrutor', { exact: true })).toBeVisible();

    await painel.getByRole('button', { name: 'Fixar' }).click();
    await expect(painel.getByRole('button', { name: 'Desafixar' })).toBeVisible();
    await painel.getByRole('button', { name: 'Ocultar tópico' }).click();
    await expect(painel.getByText('Oculto para os alunos')).toBeVisible();

    // O aluno não vê mais o tópico
    const lista = await aluno.get<{ itens: { titulo: string }[] }>(
      `/api/aluno/turmas/${curso.turmaId}/forum`,
    );
    expect(lista.itens.map((t) => t.titulo)).not.toContain(titulo);
  });

  test('turma sem tópicos', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=forum`);
    await expect(page.getByText('Nenhum tópico')).toBeVisible();
  });
});

test.describe('Configurações da turma', () => {
  test('certificado: percentual fora do limite é barrado e um válido é salvo', async ({
    comoAdmin: page,
    request,
  }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=configuracao`);
    const percentual = page.getByRole('textbox', { name: 'Conclusão mínima (%)' });
    await percentual.fill('150');
    await page.getByRole('button', { name: 'Salvar certificado' }).click();
    await expect(page.getByText('Informe um percentual de 1 a 100.')).toBeVisible();

    await percentual.fill('80');
    await page.getByRole('textbox', { name: 'Carga horária (horas)' }).fill('12');
    await page.getByRole('button', { name: 'Salvar certificado' }).click();
    await expect(page.getByText('Regras do certificado salvas', { exact: true })).toBeVisible();
  });

  test('turma com módulos não pode ser excluída', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=configuracao`);
    await page.getByRole('button', { name: 'Excluir turma' }).click();
    await page
      .getByRole('dialog', { name: 'Remover registro?' })
      .getByRole('button', { name: 'Remover' })
      .click();
    await expect(
      page.getByText('A turma tem módulos. Exclua os módulos ou encerre a turma.'),
    ).toBeVisible();
  });
});
