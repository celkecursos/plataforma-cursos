import { PNG } from '../support/amostras.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA } from '../support/dados.ts';
import { entrarPelaApi, expect, test } from '../support/sessoes.ts';
import { aceitarTermosSePedir, entrarPelaTela } from '../support/telas.ts';

test.describe('Meu perfil', () => {
  test('troca o nome e a foto', async ({ page, request }) => {
    const email = await alunoConfirmado(request);
    await entrarPelaApi(page, email, SENHA_NOVA);
    await page.goto('/perfil');
    await aceitarTermosSePedir(page, 'turma');
    await expect(page.getByRole('textbox', { name: 'E-mail' })).toHaveValue(email);

    const nome = page.getByRole('textbox', { name: 'Nome', exact: true });
    await nome.fill('Nome Trocado no Perfil');
    await page.getByRole('button', { name: 'Salvar nome' }).click();
    await expect(page.getByText('Nome atualizado', { exact: true })).toBeVisible();

    await page.getByLabel('Enviar foto').setInputFiles(PNG);
    await expect(page.getByText('Foto atualizada', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Remover foto' })).toBeVisible();
  });

  test('foto em formato errado não é enviada', async ({ page, request }) => {
    const email = await alunoConfirmado(request);
    await entrarPelaApi(page, email, SENHA_NOVA);
    await page.goto('/perfil');
    await aceitarTermosSePedir(page, 'turma');
    await page
      .getByLabel('Enviar foto')
      .setInputFiles({ ...PNG, name: 'foto.gif', mimeType: 'image/gif' });
    await expect(page.getByText('Envie uma foto JPG ou PNG.')).toBeVisible();
  });

  test('troca a senha: a atual errada é recusada; com a certa, entra de novo com a nova', async ({
    page,
    request,
  }) => {
    const email = await alunoConfirmado(request);
    await entrarPelaApi(page, email, SENHA_NOVA);
    await page.goto('/perfil');
    await aceitarTermosSePedir(page, 'turma');

    await page.getByRole('button', { name: 'Trocar senha' }).click();
    await expect(page.getByText('Informe a senha atual.')).toBeVisible();

    await page.getByRole('textbox', { name: 'Senha atual' }).fill('senha-atual-errada');
    await page
      .getByRole('textbox', { name: 'Nova senha', exact: true })
      .fill('outra-senha-forte-1');
    await page.getByRole('textbox', { name: 'Repita a nova senha' }).fill('outra-senha-forte-1');
    const recusada = page.waitForResponse('**/api/perfil/senha');
    await page.getByRole('button', { name: 'Trocar senha' }).click();
    expect((await recusada).ok()).toBe(false);
    await expect(page.getByRole('alert').first()).toBeVisible();

    await page.getByRole('textbox', { name: 'Senha atual' }).fill(SENHA_NOVA);
    await page.getByRole('button', { name: 'Trocar senha' }).click();
    await expect(page).toHaveURL(/\/entrar\?senha=alterada/);
    await expect(page.getByText('Senha alterada. Entre com a nova senha.')).toBeVisible();

    await entrarPelaTela(page, email, 'outra-senha-forte-1');
    await expect(
      page
        .getByRole('heading', { name: 'Meus cursos' })
        .or(page.getByRole('heading', { name: 'Termos atualizados' })),
    ).toBeVisible();
  });
});
