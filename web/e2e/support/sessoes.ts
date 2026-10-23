import { test as base, type Page } from '@playwright/test';
import { ADMIN_SEED, ALUNO_SEED, SENHA_SEED } from './config.ts';
import { codigoTotp } from './totp.ts';

/**
 * Sessão nova por teste. O refresh token é rotativo e um token reutilizado revoga a família inteira,
 * então uma sessão gravada (storageState) só serviria para um teste. O login vai pela API do próprio
 * contexto do navegador, que guarda o cookie HttpOnly; ao abrir a página, o app renova e entra. O
 * admin do seed conclui a segunda etapa com o código do aplicativo autenticador.
 */
export async function entrarPelaApi(page: Page, email: string, senha = SENHA_SEED): Promise<void> {
  const resposta = await page.request.post('/api/auth/login', {
    data: { email, senha },
  });
  if (!resposta.ok()) throw new Error(`Login de ${email} recusado: ${resposta.status()}`);
  const { desafio } = (await resposta.json()) as { desafio?: string };
  if (!desafio) return;
  const segundaEtapa = await page.request.post('/api/auth/segundo-fator', {
    data: { desafio, codigo: codigoTotp() },
  });
  if (!segundaEtapa.ok())
    throw new Error(`Segundo fator de ${email} recusado: ${segundaEtapa.status()}`);
}

export const test = base.extend<{ comoAluno: Page; comoAdmin: Page }>({
  comoAluno: async ({ page }, usar) => {
    await entrarPelaApi(page, ALUNO_SEED);
    await usar(page);
  },
  comoAdmin: async ({ page }, usar) => {
    await entrarPelaApi(page, ADMIN_SEED);
    await usar(page);
  },
});

export { expect } from '@playwright/test';
