import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig, type UserConfig } from 'vite';

const compartilhado = fileURLToPath(new URL('./compartilhado/src', import.meta.url));

/**
 * Configuração comum das duas SPAs (aluno e ADM). No desenvolvimento, `/api` vai para a API
 * (`VITE_API_PROXY`, padrão: a API do compose local em http://localhost:8080) — mesma origem,
 * como em produção atrás do Caddy, então o cookie de refresh funciona sem CORS.
 */
export function configurar(app: 'aluno' | 'adm', porta: number): UserConfig {
  const raiz = fileURLToPath(new URL(`./${app}`, import.meta.url));
  return defineConfig({
    root: raiz,
    plugins: [react()],
    resolve: { alias: { '@compartilhado': compartilhado } },
    server: {
      port: porta,
      strictPort: true,
      proxy: { '/api': process.env['VITE_API_PROXY'] ?? 'http://localhost:8080' },
    },
    build: { outDir: fileURLToPath(new URL(`./dist/${app}`, import.meta.url)), emptyOutDir: true },
  });
}
