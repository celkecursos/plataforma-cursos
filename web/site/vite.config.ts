import { reactRouter } from '@react-router/dev/vite';
import { defineConfig } from 'vite';

/**
 * Site público (cursos.*): React Router em modo framework, renderizado no servidor. No
 * desenvolvimento (`npm run dev:site`), as leituras vão para a API em API_URL (padrão: a do compose
 * local) e as imagens das capas, para `/api` dela.
 */
export default defineConfig({
  plugins: [reactRouter()],
  server: {
    port: 5175,
    strictPort: true,
    proxy: { '/api': process.env['API_URL'] ?? 'http://localhost:8080' },
  },
});
