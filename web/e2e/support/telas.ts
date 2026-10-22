import { expect, type Page } from '@playwright/test';

/** Preenche e envia o TelaLogin do kole-react. */
export async function entrarPelaTela(page: Page, email: string, senha: string): Promise<void> {
  await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(email);
  await page.getByRole('textbox', { name: 'Senha', exact: true }).fill(senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
}

/** Se houver termos vigentes sem aceite (outra jornada pode ter publicado), aceita. */
export async function aceitarTermosSePedir(page: Page): Promise<void> {
  const termos = page.getByRole('heading', { name: 'Termos atualizados' });
  const cursos = page.getByRole('heading', { name: 'Meus cursos' });
  await expect(termos.or(cursos)).toBeVisible();
  if (await termos.isVisible()) {
    await page.getByRole('checkbox', { name: 'Li e concordo com os termos acima' }).check();
    await page.getByRole('button', { name: 'Aceitar e continuar' }).click();
    await expect(cursos).toBeVisible();
  }
}
