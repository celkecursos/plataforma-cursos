import { expect, test } from '@playwright/test';
import { ALUNO_SEED, SENHA_SEED } from '../support/config.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { emailNovo } from '../support/dados.ts';
import { aceitarTermosSePedir, entrarPelaTela } from '../support/telas.ts';

test.describe('Entrar (aluno)', () => {
  test('com e-mail e senha certos, abre os cursos do aluno', async ({ page }) => {
    await page.goto('/entrar');
    const login = page.waitForResponse('**/api/auth/login');
    await entrarPelaTela(page, ALUNO_SEED, SENHA_SEED);
    expect((await login).status()).toBe(200);

    await aceitarTermosSePedir(page);
    await expect(page).toHaveURL('/');
    await expect(page.getByText('Aluno do e2e')).toBeVisible();
  });

  test('com senha errada, mostra o erro e continua na entrada', async ({ page, request }) => {
    const email = await alunoConfirmado(request);
    await page.goto('/entrar');
    const login = page.waitForResponse('**/api/auth/login');
    await entrarPelaTela(page, email, 'senha-errada-de-proposito');
    expect((await login).status()).toBe(401);

    await expect(page.getByText('E-mail ou senha inválidos')).toBeVisible();
    await expect(page).toHaveURL(/\/entrar$/);
  });

  test('com e-mail que não existe, a resposta é a mesma da senha errada', async ({ page }) => {
    await page.goto('/entrar');
    await entrarPelaTela(page, emailNovo(), 'qualquer-senha-123');
    await expect(page.getByText('E-mail ou senha inválidos')).toBeVisible();
  });

  test('página privada sem sessão leva à entrada e volta para ela depois do login', async ({
    page,
  }) => {
    await page.goto('/?aba=ativos');
    await expect(page).toHaveURL(/\/entrar\?voltar=/);

    await entrarPelaTela(page, ALUNO_SEED, SENHA_SEED);
    await aceitarTermosSePedir(page);
    await expect(page).toHaveURL('/?aba=ativos');
  });

  test('voltar para outro site é ignorado (sem redirecionamento aberto)', async ({ page }) => {
    await page.goto('/entrar?voltar=//exemplo.com/roubo');
    await entrarPelaTela(page, ALUNO_SEED, SENHA_SEED);
    await aceitarTermosSePedir(page);
    await expect(page).toHaveURL('/');
  });

  test('endereço que não existe mostra a página de não encontrada', async ({ page }) => {
    await page.goto('/pagina-que-nao-existe');
    await expect(page.getByRole('heading', { name: 'Página não encontrada' })).toBeVisible();
  });
});
