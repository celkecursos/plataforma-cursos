/** Endereços e contas da stack de e2e (valores de infra/e2e.env; nenhum vale em servidor). */
export const ALUNO_URL = process.env['E2E_ALUNO_URL'] ?? 'http://aluno.localhost:8088';
export const ADM_URL = process.env['E2E_ADM_URL'] ?? 'http://adm.localhost:8088';
export const MAILPIT_URL = process.env['E2E_MAILPIT_URL'] ?? 'http://localhost:8026';

export const SENHA_SEED = process.env['E2E_SENHA_SEED'] ?? 'senha-do-seed-e2e';
export const ADMIN_SEED = 'admin.e2e@teste.celke.uno';
export const ALUNO_SEED = 'aluno.e2e@teste.celke.uno';

// Trava: os testes criam contas e trocam senhas; só rodam contra a stack local (*.localhost)
for (const url of [ALUNO_URL, ADM_URL]) {
  if (!/^http:\/\/[a-z]+\.localhost(:\d+)?$/.test(url)) {
    throw new Error(`E2E recusado: ${url} não é da stack local de e2e (*.localhost).`);
  }
}
