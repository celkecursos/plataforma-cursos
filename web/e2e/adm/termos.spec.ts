import { stamp } from '../support/dados.ts';
import { expect, test } from '../support/sessoes.ts';

test.describe('Termos', () => {
  test('texto vazio não é publicado', async ({ comoAdmin: page }) => {
    await page.goto('/termos');
    await page.getByRole('button', { name: 'Publicar versão' }).click();
    await expect(page.getByText('Escreva o texto da nova versão.')).toBeVisible();
  });

  test('publica uma nova versão da política de privacidade', async ({ comoAdmin: page }) => {
    const marca = `Política revisada ${stamp()}`;
    await page.goto('/termos');
    await page.getByRole('tab', { name: 'Política de privacidade' }).click();
    const painel = page.getByRole('tabpanel', { name: 'Política de privacidade' });
    await painel
      .getByRole('textbox', { name: 'Texto da nova versão' })
      .fill(`# ${marca}\n\nTratamos os seus dados com cuidado.`);
    await page.getByRole('button', { name: 'Publicar versão' }).click();
    await page
      .getByRole('dialog', { name: /Publicar nova versão/ })
      .getByRole('button', { name: 'Publicar', exact: true })
      .click();

    await expect(painel.getByRole('heading', { name: marca })).toBeVisible();
    await expect(
      page
        .getByRole('tabpanel', { name: 'Política de privacidade' })
        .getByText(/Versão vigente: \d+/),
    ).toBeVisible();
  });
});
