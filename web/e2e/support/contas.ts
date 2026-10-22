import type { APIRequestContext } from '@playwright/test';
import { ALUNO_URL } from './config.ts';
import { emailNovo, SENHA_NOVA } from './dados.ts';
import { linkDoEmail } from './mailpit.ts';

/**
 * Aluno novo já confirmado, criado pela API (o caminho pela tela é coberto em cadastro.spec.ts).
 * Para os testes que mexem na conta (senha errada, nova senha) sem tocar nas contas do seed.
 */
export async function alunoConfirmado(request: APIRequestContext): Promise<string> {
  const email = emailNovo();
  const cadastro = await request.post(`${ALUNO_URL}/api/auth/cadastro`, {
    data: { nome: 'Aluno do Playwright', email, senha: SENHA_NOVA },
  });
  if (!cadastro.ok()) throw new Error(`Cadastro recusado: ${cadastro.status()}`);
  const link = await linkDoEmail(email, 'Confirme o seu e-mail');
  const confirmacao = await request.post(`${ALUNO_URL}/api/auth/confirmar-email`, {
    data: { token: new URL(link).searchParams.get('token') },
  });
  if (!confirmacao.ok()) throw new Error(`Confirmação recusada: ${confirmacao.status()}`);
  return email;
}
