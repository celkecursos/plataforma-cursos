import { ADMIN_SEED, ALUNO_SEED, SENHA_SEED } from '../support/config.ts';
import { emailNovo } from '../support/dados.ts';
import { expect, test } from '../support/sessoes.ts';
import { confirmarSegundoFator, entrarPelaTela } from '../support/telas.ts';

test.describe('ADM', () => {
  test('com a sessão do admin, abre o painel com o menu da administração', async ({
    comoAdmin: page,
  }) => {
    await page.goto('/');
    await expect(page.getByRole('link', { name: 'Painel' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Usuário: Admin do e2e' })).toContainText(
      'Administrador',
    );
  });

  test('o admin entra pela tela, com o código do aplicativo, e volta para onde ia', async ({
    page,
  }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/entrar\?voltar=/);
    await entrarPelaTela(page, ADMIN_SEED, SENHA_SEED);
    await confirmarSegundoFator(page);
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('link', { name: 'Painel' })).toBeVisible();
  });

  test('código errado do aplicativo mostra o erro e não entra', async ({ page }) => {
    await page.goto('/entrar');
    await entrarPelaTela(page, ADMIN_SEED, SENHA_SEED);
    await confirmarSegundoFator(page, '000000');
    await expect(page.getByText('Código inválido.', { exact: false })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Verificação em duas etapas' })).toBeVisible();
  });

  test('um aluno é barrado na entrada e a sessão dele é encerrada', async ({ page }) => {
    await page.goto('/entrar');
    const sair = page.waitForResponse('**/api/auth/logout');
    await entrarPelaTela(page, ALUNO_SEED, SENHA_SEED);
    await sair;

    await expect(page.getByText('Acesso restrito a administradores.')).toBeVisible();
    await page.goto('/');
    await expect(page).toHaveURL(/\/entrar\?voltar=/);
  });

  test('um aluno com sessão aberta vê o acesso restrito, não o painel', async ({
    comoAluno: page,
  }) => {
    await page.goto('/');
    await expect(
      page.getByRole('heading', { name: 'Acesso restrito a administradores' }),
    ).toBeVisible();
    await expect(page.getByRole('link', { name: 'Painel' })).toBeHidden();

    await page.getByRole('button', { name: 'Entrar com outra conta' }).click();
    await expect(page).toHaveURL(/\/entrar(\?|$)/);
  });

  test('senha errada mostra o erro', async ({ page }) => {
    await page.goto('/entrar');
    await entrarPelaTela(page, emailNovo(), 'senha-errada-123');
    await expect(page.getByText('E-mail ou senha inválidos')).toBeVisible();
  });

  test('a API do ADM recusa o aluno mesmo sem passar pela tela', async ({ request }) => {
    const login = await request.post('/api/auth/login', {
      data: { email: ALUNO_SEED, senha: SENHA_SEED },
    });
    const { accessToken } = (await login.json()) as { accessToken: string };
    const resposta = await request.get('/api/adm/cursos', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(resposta.status()).toBe(403);
  });
});
