import { escolher } from '../support/telas.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { stamp } from '../support/dados.ts';
import { PNG, TXT, VIDEO_3S } from '../support/amostras.ts';
import { expect, test } from '../support/sessoes.ts';

test('admin cria um curso completo e envia o vídeo da aula até ficar pronto', async ({
  comoAdmin: page,
  request,
}) => {
  // A conversão do vídeo pelo worker leva alguns segundos
  test.slow();
  const titulo = `Curso Playwright ${stamp()}`;
  const aluno = await alunoConfirmado(request);

  await page.goto('/cursos');
  await page.getByRole('button', { name: 'Novo curso' }).click();
  const formCurso = page.getByRole('dialog');
  await formCurso.getByRole('textbox', { name: 'Título', exact: true }).fill(titulo);
  await formCurso
    .getByRole('textbox', { name: 'Resumo', exact: true })
    .fill('Curso criado pela jornada do ADM.');
  const criar = page.waitForResponse(
    (r) => r.url().endsWith('/api/adm/cursos') && r.request().method() === 'POST',
  );
  await formCurso.getByRole('button', { name: 'Salvar' }).click();
  expect((await criar).status()).toBe(201);
  await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

  // Capa: o navegador envia direto ao armazenamento pela URL pré-assinada
  await page.getByLabel('Enviar capa').setInputFiles(PNG);
  const capa = page.getByRole('img', { name: `Capa de ${titulo}` });
  await expect(capa).toBeVisible();
  await expect
    .poll(() => capa.evaluate((img: HTMLImageElement) => img.naturalWidth))
    .toBeGreaterThan(0);

  // Turma, módulo e aula
  await page.getByRole('tab', { name: 'Turmas' }).click();
  await page.getByRole('button', { name: 'Nova turma' }).click();
  await page.getByRole('textbox', { name: 'Nome da turma' }).fill('Turma de outubro');
  await page.getByRole('button', { name: 'Criar turma' }).click();
  await expect(page.getByRole('heading', { name: 'Turma de outubro' })).toBeVisible();

  await page.getByRole('button', { name: 'Novo módulo' }).click();
  await page.getByRole('textbox', { name: 'Título do módulo' }).fill('Primeiros passos');
  await page.getByRole('button', { name: 'Criar módulo' }).click();
  await expect(page.getByText('1. Primeiros passos')).toBeVisible();

  await page.getByRole('button', { name: 'Nova aula' }).click();
  await page.getByRole('textbox', { name: 'Título da aula' }).fill('Boas-vindas');
  await page.getByRole('button', { name: 'Criar aula' }).click();
  await expect(page.getByRole('heading', { name: 'Boas-vindas' })).toBeVisible();

  // Vídeo em partes e conversão
  await page.getByLabel('Enviar vídeo').setInputFiles(VIDEO_3S);
  await expect(page.getByText('Na fila').or(page.getByText('Processando'))).toBeVisible();
  await expect(page.getByText('Pronto', { exact: true })).toBeVisible({ timeout: 150_000 });
  await expect(page.getByRole('img', { name: 'Quadro do vídeo' })).toBeVisible();
  await expect(page.getByText('0:03')).toBeVisible();

  // Arquivo de apoio da aula
  const arquivosAula = page.getByRole('region', { name: 'Arquivos de apoio da aula' });
  await arquivosAula.getByRole('textbox', { name: 'Título do arquivo' }).fill('Roteiro');
  await arquivosAula.getByLabel('Enviar arquivo').setInputFiles(TXT);
  await expect(arquivosAula.getByText('roteiro.txt')).toBeVisible();

  // Matrícula de um aluno
  await page.getByRole('button', { name: 'Voltar à turma' }).click();
  await page.getByRole('tab', { name: 'Matrículas' }).click();
  await page.getByRole('button', { name: 'Matricular aluno' }).click();
  await page.getByRole('textbox', { name: 'E-mail do aluno' }).fill(aluno);
  await page.getByRole('button', { name: 'Matricular', exact: true }).click();
  await expect(page.getByRole('cell', { name: aluno })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Vigente' })).toBeVisible();

  // Publica: turma ativa e curso publicado
  await page.getByRole('tab', { name: 'Configurações' }).click();
  await escolher(page, 'Status', 'Ativa');
  await expect(page.getByText('Status da turma alterado', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Voltar' }).click();
  await page.getByRole('tab', { name: 'Dados' }).click();
  await escolher(page, 'Alterar status', 'Publicado');
  await expect(page.getByText('Status do curso alterado', { exact: true })).toBeVisible();

  // O curso aparece no catálogo público
  const catalogo = await page.request.get('/api/cursos');
  expect(JSON.stringify(await catalogo.json())).toContain(titulo);
});
