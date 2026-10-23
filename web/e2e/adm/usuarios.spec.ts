import { ALUNO_URL } from '../support/config.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA } from '../support/dados.ts';
import { expect, test } from '../support/sessoes.ts';

test('o admin bloqueia um aluno, que não entra mais, e desbloqueia', async ({
  comoAdmin: page,
  request,
}) => {
  const email = await alunoConfirmado(request);
  const entrar = () =>
    request.post(`${ALUNO_URL}/api/auth/login`, { data: { email, senha: SENHA_NOVA } });

  await page.goto('/usuarios');
  await expect(page.getByRole('heading', { name: 'Usuários' })).toBeVisible();
  await page.getByRole('textbox', { name: 'Nome ou e-mail' }).fill(email);
  const linha = page.getByRole('row').filter({ hasText: email });
  await expect(linha).toHaveCount(1);
  await expect(linha.getByText('Ativo')).toBeVisible();

  await linha.getByRole('button', { name: /^Bloquear / }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Bloquear' }).click();
  await expect(page.getByText('Conta bloqueada', { exact: true })).toBeVisible();
  await expect(linha.getByText('Bloqueado')).toBeVisible();
  expect((await entrar()).status()).toBe(401);

  await linha.getByRole('button', { name: /^Desbloquear / }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Desbloquear' }).click();
  await expect(linha.getByText('Ativo')).toBeVisible();
  expect((await entrar()).status()).toBe(200);
});

test('o admin não tem as ações na própria linha', async ({ comoAdmin: page }) => {
  await page.goto('/usuarios');
  await page.getByRole('textbox', { name: 'Nome ou e-mail' }).fill('admin@celke.com.br');
  const linha = page.getByRole('row').filter({ hasText: 'admin@celke.com.br' });
  await expect(linha).toHaveCount(1);
  await expect(linha.getByRole('button', { name: /^Bloquear / })).toHaveCount(0);
});
