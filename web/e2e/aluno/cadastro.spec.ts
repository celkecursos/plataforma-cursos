import { expect, test } from '@playwright/test';
import { ALUNO_SEED } from '../support/config.ts';
import { emailNovo, SENHA_NOVA } from '../support/dados.ts';
import { linkDoEmail } from '../support/mailpit.ts';
import { aceitarTermosSePedir, entrarPelaTela } from '../support/telas.ts';

test.describe('Cadastro e confirmação do e-mail', () => {
  test('cadastra, confirma pelo link do e-mail e entra', async ({ page }) => {
    const email = emailNovo();
    await page.goto('/cadastro');
    await page.getByRole('textbox', { name: 'Nome', exact: true }).fill('Aluna do Playwright');
    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(email);
    await page.getByRole('textbox', { name: 'Senha', exact: true }).fill(SENHA_NOVA);
    await page.getByRole('textbox', { name: 'Repita a senha', exact: true }).fill(SENHA_NOVA);
    const cadastro = page.waitForResponse('**/api/auth/cadastro');
    await page.getByRole('button', { name: 'Criar conta' }).click();
    expect((await cadastro).status()).toBe(202);
    await expect(page.getByRole('heading', { name: 'Confira o seu e-mail' })).toBeVisible();
    await expect(page.getByText(email)).toBeVisible();

    // Antes de confirmar, a conta não entra
    await page.goto('/entrar');
    await entrarPelaTela(page, email, SENHA_NOVA);
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/entrar$/);

    await page.goto(await linkDoEmail(email, 'Confirme o seu e-mail'));
    await expect(page.getByRole('heading', { name: 'E-mail confirmado' })).toBeVisible();

    await page.getByRole('link', { name: 'Entrar' }).click();
    await entrarPelaTela(page, email, SENHA_NOVA);
    await aceitarTermosSePedir(page);
    await expect(page.getByText('Você ainda não tem cursos')).toBeVisible();
  });

  test('formulário vazio mostra os erros e não chama a API', async ({ page }) => {
    let chamou = false;
    page.on('request', (req) => {
      if (req.url().includes('/api/auth/cadastro')) chamou = true;
    });
    await page.goto('/cadastro');
    await page.getByRole('button', { name: 'Criar conta' }).click();

    await expect(page.getByText('Informe o seu nome.')).toBeVisible();
    await expect(page.getByText('Informe o e-mail.')).toBeVisible();
    await expect(page.getByText('Informe a senha.')).toBeVisible();
    await expect(page.getByText('Repita a senha.')).toBeVisible();
    expect(chamou).toBe(false);
  });

  test('e-mail inválido, senha curta e confirmação diferente são barrados na tela', async ({
    page,
  }) => {
    await page.goto('/cadastro');
    await page.getByRole('textbox', { name: 'Nome', exact: true }).fill('Aluno');
    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill('sem-arroba');
    await page.getByRole('textbox', { name: 'Senha', exact: true }).fill('curta');
    await page.getByRole('textbox', { name: 'Repita a senha', exact: true }).fill('outra');
    await page.getByRole('button', { name: 'Criar conta' }).click();

    await expect(page.getByText('Informe um e-mail válido.')).toBeVisible();
    await expect(page.getByText('A senha deve ter pelo menos 10 caracteres.')).toBeVisible();
    await expect(page.getByText('As senhas não conferem.')).toBeVisible();
  });

  test('e-mail já cadastrado recebe a mesma resposta (não revela quem tem conta)', async ({
    page,
  }) => {
    await page.goto('/cadastro');
    await page.getByRole('textbox', { name: 'Nome', exact: true }).fill('Outra pessoa');
    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(ALUNO_SEED);
    await page.getByRole('textbox', { name: 'Senha', exact: true }).fill(SENHA_NOVA);
    await page.getByRole('textbox', { name: 'Repita a senha', exact: true }).fill(SENHA_NOVA);
    const cadastro = page.waitForResponse('**/api/auth/cadastro');
    await page.getByRole('button', { name: 'Criar conta' }).click();

    expect((await cadastro).status()).toBe(202);
    await expect(page.getByRole('heading', { name: 'Confira o seu e-mail' })).toBeVisible();

    // A conta existente continua com a senha dela
    await page.goto('/entrar');
    await entrarPelaTela(page, ALUNO_SEED, SENHA_NOVA);
    await expect(page.getByText('E-mail ou senha inválidos')).toBeVisible();
  });

  test('link de confirmação inválido oferece o reenvio', async ({ page }) => {
    await page.goto('/confirmar-email?token=token-que-nao-existe');
    await expect(page.getByRole('heading', { name: 'Não foi possível confirmar' })).toBeVisible();

    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(emailNovo());
    await page.getByRole('button', { name: 'Enviar novo link' }).click();
    // A resposta não revela se a conta existe
    await expect(page.getByRole('button', { name: 'Enviar novo link' })).toBeHidden();
  });

  test('link de confirmação sem token', async ({ page }) => {
    await page.goto('/confirmar-email');
    await expect(page.getByText('Link inválido ou expirado. Peça um novo.')).toBeVisible();
  });
});
