import { expect, test } from '@playwright/test';
import { alunoConfirmado } from '../support/contas.ts';
import { emailNovo, SENHA_NOVA } from '../support/dados.ts';
import { linkDoEmail } from '../support/mailpit.ts';
import { aceitarTermosSePedir, entrarPelaTela } from '../support/telas.ts';

test.describe('Recuperação de senha', () => {
  test('pede o link, cria a nova senha e entra com ela', async ({ page, request }) => {
    const email = await alunoConfirmado(request);
    const novaSenha = `${SENHA_NOVA}-nova`;

    await page.goto('/entrar');
    await page.getByRole('link', { name: 'Esqueci minha senha' }).click();
    await expect(page.getByRole('heading', { name: 'Recuperar senha' })).toBeVisible();
    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(email);
    await page.getByRole('button', { name: 'Enviar link' }).click();
    await expect(page.getByRole('button', { name: 'Enviar link' })).toBeHidden();

    await page.goto(await linkDoEmail(email, 'Crie uma nova senha'));
    await page.getByRole('textbox', { name: 'Nova senha', exact: true }).fill(novaSenha);
    await page.getByRole('textbox', { name: 'Repita a nova senha', exact: true }).fill(novaSenha);
    await page.getByRole('button', { name: 'Salvar nova senha' }).click();
    await expect(page.getByRole('heading', { name: 'Senha alterada' })).toBeVisible();

    // A senha antiga deixou de valer
    await page.getByRole('link', { name: 'Entrar' }).click();
    await entrarPelaTela(page, email, SENHA_NOVA);
    await expect(page.getByText('E-mail ou senha inválidos')).toBeVisible();

    await entrarPelaTela(page, email, novaSenha);
    await aceitarTermosSePedir(page);
  });

  test('e-mail sem conta recebe a mesma resposta (não revela quem é cliente)', async ({ page }) => {
    await page.goto('/recuperar-senha');
    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(emailNovo());
    const resposta = page.waitForResponse('**/api/auth/recuperar-senha');
    await page.getByRole('button', { name: 'Enviar link' }).click();
    expect((await resposta).status()).toBe(202);
  });

  test('e-mail inválido é barrado na tela', async ({ page }) => {
    await page.goto('/recuperar-senha');
    await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill('invalido');
    await page.getByRole('button', { name: 'Enviar link' }).click();
    await expect(page.getByText('Informe um e-mail válido.')).toBeVisible();
  });

  test('link sem token', async ({ page }) => {
    await page.goto('/redefinir-senha');
    await expect(page.getByRole('heading', { name: 'Link inválido' })).toBeVisible();
    await page.getByRole('link', { name: 'Pedir um novo link' }).click();
    await expect(page).toHaveURL(/\/recuperar-senha$/);
  });

  test('token inválido é recusado pela API', async ({ page }) => {
    await page.goto('/redefinir-senha?token=token-que-nao-existe');
    await page.getByRole('textbox', { name: 'Nova senha', exact: true }).fill(SENHA_NOVA);
    await page.getByRole('textbox', { name: 'Repita a nova senha', exact: true }).fill(SENHA_NOVA);
    const resposta = page.waitForResponse('**/api/auth/redefinir-senha');
    await page.getByRole('button', { name: 'Salvar nova senha' }).click();

    expect((await resposta).status()).toBe(400);
    await expect(page.getByRole('heading', { name: 'Criar nova senha' })).toBeVisible();
    await expect(page.getByRole('alert').first()).toBeVisible();
  });

  test('confirmação diferente é barrada na tela', async ({ page }) => {
    await page.goto('/redefinir-senha?token=qualquer');
    await page.getByRole('textbox', { name: 'Nova senha', exact: true }).fill(SENHA_NOVA);
    await page
      .getByRole('textbox', { name: 'Repita a nova senha', exact: true })
      .fill('outra-senha-qualquer');
    await page.getByRole('button', { name: 'Salvar nova senha' }).click();
    await expect(page.getByText('As senhas não conferem.')).toBeVisible();
  });
});
