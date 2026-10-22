import { expect, type Page } from '@playwright/test';

/** Preenche e envia o TelaLogin do kole-react. */
export async function entrarPelaTela(page: Page, email: string, senha: string): Promise<void> {
  await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(email);
  await page.getByRole('textbox', { name: 'Senha', exact: true }).fill(senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
}

/**
 * Se houver termos vigentes sem aceite (outra jornada pode ter publicado), aceita. Sem destino,
 * espera a página "Meus cursos"; com destino (outra página da área logada), espera a moldura e o
 * fim da consulta dos termos.
 */
export async function aceitarTermosSePedir(page: Page, destino?: 'aula' | 'turma'): Promise<void> {
  const termos = page.getByRole('heading', { name: 'Termos atualizados' });
  const pronto = destino
    ? page.getByRole('button', { name: /^Usuário: / })
    : page.getByRole('heading', { name: 'Meus cursos' });
  await expect(termos.or(pronto).first()).toBeVisible();
  if (destino) await expect(page.getByText('Carregando...', { exact: true })).toBeHidden();
  if (await termos.isVisible()) {
    await page.getByRole('checkbox', { name: 'Li e concordo com os termos acima' }).check();
    await page.getByRole('button', { name: 'Aceitar e continuar' }).click();
    await expect(termos).toBeHidden();
  }
}

/**
 * Escolhe uma opção num CampoSelect da @celke/kole-react pelo rótulo visível. O rótulo é um
 * <label for> apontando para o combobox (um div), o que não dá nome acessível a ele.
 */
export async function escolher(page: Page, rotulo: string, opcao: string): Promise<void> {
  const label = page.locator('label').filter({ hasText: new RegExp(`^${rotulo}[:*]*$`) });
  const id = await label.first().getAttribute('for');
  await page.locator(`[id="${id}"]`).click();
  await page.getByRole('option', { name: opcao, exact: true }).click();
}
