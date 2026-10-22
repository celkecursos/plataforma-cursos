import { request as criarRequest } from '@playwright/test';
import { clienteApi, montarCurso } from '../support/api.ts';
import { ADMIN_SEED } from '../support/config.ts';
import { expect, test } from '../support/sessoes.ts';
import { escolher } from '../support/telas.ts';

test('a trilha mostra quem criou o curso, de onde e os campos gravados', async ({
  comoAdmin: page,
}) => {
  const contexto = await criarRequest.newContext();
  const curso = await montarCurso(await clienteApi(contexto));
  await contexto.dispose();

  await page.goto('/auditoria');
  await expect(page.getByRole('heading', { name: 'Auditoria' })).toBeVisible();
  await escolher(page, 'Entidade', 'Curso');
  await page.getByRole('textbox', { name: 'Id' }).fill(String(curso.cursoId));
  const linha = page.getByRole('row').filter({ hasText: `Curso #${curso.cursoId}` });
  await expect(linha).toHaveCount(1);
  await expect(linha.getByText(ADMIN_SEED)).toBeVisible();
  await expect(linha.getByText('POST /api/adm/cursos')).toBeVisible();

  await linha.click();
  const dialogo = page.getByRole('dialog', { name: `Inclusão: Curso #${curso.cursoId}` });
  await expect(dialogo.getByRole('cell', { name: 'titulo', exact: true })).toBeVisible();
  await expect(dialogo.getByRole('cell', { name: curso.titulo })).toBeVisible();
  await expect(dialogo.getByRole('cell', { name: 'RASCUNHO' })).toBeVisible();
});
