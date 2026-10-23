import { expect, test } from '../support/sessoes.ts';
import { aceitarTermosSePedir } from '../support/telas.ts';

test.describe('Sessão do aluno', () => {
  test('ao abrir e ao recarregar, a sessão volta pelo cookie de refresh', async ({
    comoAluno: page,
  }) => {
    const renovar = page.waitForResponse('**/api/auth/refresh');
    await page.goto('/');
    expect((await renovar).status()).toBe(200);
    await aceitarTermosSePedir(page);

    await page.reload();
    await expect(page.getByRole('heading', { name: 'Meus cursos' })).toBeVisible();
    await expect(page.getByText('Aluno do e2e')).toBeVisible();
  });

  test('sem matrícula, a lista de cursos fica vazia', async ({ comoAluno: page }) => {
    await page.goto('/');
    await aceitarTermosSePedir(page);
    await expect(page.getByText('Você ainda não tem cursos')).toBeVisible();
  });

  test('sair encerra a sessão: voltar à área do aluno pede login', async ({ comoAluno: page }) => {
    await page.goto('/');
    await aceitarTermosSePedir(page);
    await page.getByRole('button', { name: /Aluno do e2e/ }).click();
    const sair = page.waitForResponse('**/api/auth/logout');
    await page
      .getByRole('group', { name: 'Menu do usuário' })
      .getByRole('button', { name: 'Sair' })
      .click();
    expect((await sair).status()).toBe(204);
    await expect(page).toHaveURL(/\/entrar(\?|$)/);

    await page.goto('/');
    await expect(page).toHaveURL(/\/entrar\?voltar=/);
  });

  test('cookie de refresh já usado não abre sessão (e derruba a família)', async ({
    comoAluno: page,
  }) => {
    const [cookie] = await page.context().cookies();
    await page.goto('/');
    await aceitarTermosSePedir(page);

    // Outro navegador com o cookie antigo (já trocado pelo app): recusado
    const outro = await page.context().browser()!.newContext({ baseURL: page.url() });
    await outro.addCookies([cookie!]);
    const pagina = await outro.newPage();
    await pagina.goto('/');
    await expect(pagina).toHaveURL(/\/entrar\?voltar=/);
    await outro.close();

    // A sessão original também caiu: recarregar pede login
    await page.reload();
    await expect(page).toHaveURL(/\/entrar\?voltar=/);
  });
});
