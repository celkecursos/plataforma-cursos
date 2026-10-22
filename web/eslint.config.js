import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'playwright-report', 'test-results'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: { ecmaVersion: 2023, globals: { ...globals.browser, ...globals.node } },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      '@typescript-eslint/no-explicit-any': 'error',
      // Chamadas HTTP só pelo cliente de compartilhado/src/api (token, refresh e erros num lugar só)
      'no-restricted-globals': [
        'error',
        { name: 'fetch', message: 'Use o cliente da API (@compartilhado/api/cliente).' },
      ],
    },
  },
  {
    // O gerador do site roda no Node, fora do app: busca a API direto
    files: ['compartilhado/src/api/**', 'e2e/**', 'site/**', '**/*.test.{ts,tsx}'],
    rules: { 'no-restricted-globals': 'off' },
  },
  {
    // Script do site que roda no navegador (validar certificado)
    files: ['site/assets/**/*.js'],
    extends: [js.configs.recommended],
    languageOptions: { ecmaVersion: 2023, sourceType: 'script', globals: globals.browser },
  },
);
