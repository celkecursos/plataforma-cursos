import type { Config } from '@react-router/dev/config';

/** Site público com renderização no servidor: cada página chega pronta para os buscadores. */
export default {
  appDirectory: 'app',
  buildDirectory: '../dist/site',
  ssr: true,
} satisfies Config;
