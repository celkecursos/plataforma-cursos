import { readFile } from 'node:fs/promises';
import { ALUNO_URL } from '../support/config.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA } from '../support/dados.ts';
import { aguardarEmail } from '../support/mailpit.ts';
import { entrarPelaApi, expect, test } from '../support/sessoes.ts';
import { aceitarTermosSePedir } from '../support/telas.ts';

test.describe('Privacidade', () => {
  test('baixa a cópia dos dados em JSON', async ({ page, request }) => {
    const email = await alunoConfirmado(request);
    await entrarPelaApi(page, email, SENHA_NOVA);
    await page.goto('/perfil');
    await aceitarTermosSePedir(page, 'turma');

    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Baixar meus dados' }).click();
    const arquivo = await download;
    expect(arquivo.suggestedFilename()).toMatch(/^meus-dados-\d{4}-\d{2}-\d{2}\.json$/);
    const dados = JSON.parse(await readFile(await arquivo.path(), 'utf-8')) as {
      conta: { email: string };
      entradas: unknown[];
    };
    expect(dados.conta.email).toBe(email);
    expect(dados.entradas.length).toBeGreaterThan(0);
  });

  test('exclui a conta com a senha e a confirmação; ela não entra mais', async ({
    page,
    request,
  }) => {
    const email = await alunoConfirmado(request);
    await entrarPelaApi(page, email, SENHA_NOVA);
    await page.goto('/perfil');
    await aceitarTermosSePedir(page, 'turma');

    await page.getByRole('button', { name: 'Excluir minha conta' }).click();
    const dialogo = page.getByRole('dialog');
    await dialogo.getByRole('button', { name: 'Excluir a conta' }).click();
    await expect(dialogo.getByText('Informe a sua senha.')).toBeVisible();
    await expect(dialogo.getByText('Digite EXCLUIR para confirmar.')).toBeVisible();

    await dialogo.getByRole('textbox', { name: 'Sua senha' }).fill(SENHA_NOVA);
    await dialogo.getByRole('textbox', { name: 'Digite EXCLUIR para confirmar' }).fill('EXCLUIR');
    await dialogo.getByRole('button', { name: 'Excluir a conta' }).click();

    await expect(page).toHaveURL(/\/entrar\?conta=excluida/);
    await expect(page.getByText('Sua conta foi excluída.', { exact: false })).toBeVisible();
    expect(await aguardarEmail(email, 'Sua conta foi excluída')).toBeTruthy();
    const login = await request.post(`${ALUNO_URL}/api/auth/login`, {
      data: { email, senha: SENHA_NOVA },
    });
    expect(login.status()).toBe(401);
  });
});
