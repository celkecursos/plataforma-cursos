import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@compartilhado': fileURLToPath(new URL('./compartilhado/src', import.meta.url)) },
  },
  test: {
    environment: 'jsdom',
    include: ['{aluno,adm,compartilhado}/src/**/*.test.{ts,tsx}'],
    setupFiles: ['./compartilhado/src/testes/configuracao.ts'],
    css: false,
  },
});
