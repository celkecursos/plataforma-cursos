/** Endereços e contas da stack de e2e (valores de infra/e2e.env; nenhum vale em servidor). */
export const ALUNO_URL = process.env['E2E_ALUNO_URL'] ?? 'http://aluno.localhost:8088';
export const ADM_URL = process.env['E2E_ADM_URL'] ?? 'http://adm.localhost:8088';
export const SITE_URL = process.env['E2E_SITE_URL'] ?? 'http://cursos.localhost:8088';
export const MAILPIT_URL = process.env['E2E_MAILPIT_URL'] ?? 'http://localhost:8026';

export const SENHA_SEED = process.env['E2E_SENHA_SEED'] ?? '123456A#b123';
export const ADMIN_SEED = 'admin@celke.com.br';
export const ALUNO_SEED = 'aluno@celke.com.br';

// Trava: os testes criam contas e trocam senhas; só rodam contra a stack local (*.localhost)
for (const url of [ALUNO_URL, ADM_URL, SITE_URL]) {
  if (!/^http:\/\/[a-z]+\.localhost(:\d+)?$/.test(url)) {
    throw new Error(`E2E recusado: ${url} não é da stack local de e2e (*.localhost).`);
  }
}
