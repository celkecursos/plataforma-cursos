import { defineConfig, devices } from '@playwright/test';
import { ADM_URL, ALUNO_URL } from './e2e/support/config.ts';

/**
 * Testes e2e dos fronts: navegador real contra a stack de e2e (infra/compose.e2e.yml), com os apps
 * servidos pelo container web atrás do Caddy, a API, o worker e o Mailpit de verdade. Nunca contra
 * um servidor: a stack de e2e é descartável (`down -v`) e o seed só existe nela.
 *
 *   docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
 *   npm run e2e
 *
 * Cada teste começa sem sessão; quem precisa de uma usa as fixtures de e2e/support/sessoes.ts.
 */

// Só quando se está assistindo (`--headed`) a janela abre maximizada; fora daí o viewport é fixo
const assistindo = process.argv.includes('--headed');

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // Um worker: os testes escrevem no mesmo banco e gastam o mesmo limite por IP
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: !!process.env['CI'],
  reporter: process.env['CI']
    ? [['github'], ['html', { open: 'never' }], ['list']]
    : [['html', { open: 'never' }], ['list']],
  use: {
    ...devices['Desktop Chrome'],
    // Artefatos só quando falha
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 10_000,
    locale: 'pt-BR',
    ...(assistindo ? { viewport: null } : {}),
    launchOptions: {
      slowMo: Number(process.env['E2E_SLOWMO'] ?? 0),
      args: assistindo ? ['--start-maximized'] : [],
    },
  },
  projects: [
    { name: 'aluno', testDir: './e2e/aluno', use: { baseURL: ALUNO_URL } },
    { name: 'adm', testDir: './e2e/adm', use: { baseURL: ADM_URL } },
  ],
});
