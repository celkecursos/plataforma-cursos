/**
 * Meta de Lighthouse do site público, verificada no CI contra a stack de e2e (perfil desktop).
 * Falha se alguma página ficar abaixo da nota mínima de alguma categoria.
 *
 *   npm run lighthouse
 */
import { chromium } from '@playwright/test';
import lighthouse from 'lighthouse';

const SITE = process.env['E2E_SITE_URL'] ?? 'http://cursos.localhost:8088';
const PORTA = 9333;

/** Notas mínimas (0 a 100). */
export const METAS: Record<string, number> = {
  performance: 90,
  accessibility: 95,
  'best-practices': 95,
  seo: 90,
};

async function paginas(): Promise<string[]> {
  const resposta = await fetch(`${SITE}/api/cursos?size=1`);
  const { itens } = (await resposta.json()) as { itens: { slug: string }[] };
  return [
    '/',
    '/cursos/',
    ...itens.map((c) => `/cursos/${c.slug}/`),
    '/certificados/validar/',
    '/termos/',
  ];
}

const navegador = await chromium.launch({ args: [`--remote-debugging-port=${PORTA}`] });
const falhas: string[] = [];
try {
  for (const caminho of await paginas()) {
    const resultado = await lighthouse(SITE + caminho, {
      port: PORTA,
      output: 'json',
      logLevel: 'error',
      onlyCategories: Object.keys(METAS),
      formFactor: 'desktop',
      screenEmulation: {
        mobile: false,
        width: 1350,
        height: 940,
        deviceScaleFactor: 1,
        disabled: false,
      },
      throttlingMethod: 'provided',
    });
    if (!resultado) throw new Error(`Lighthouse não rodou em ${caminho}`);
    const notas = Object.entries(METAS).map(([categoria, minimo]) => {
      const nota = Math.round((resultado.lhr.categories[categoria]?.score ?? 0) * 100);
      if (nota < minimo) {
        const auditorias = Object.values(resultado.lhr.audits)
          .filter((a) => a.score !== null && a.score < 1 && a.scoreDisplayMode !== 'informative')
          .map((a) => `      - ${a.title}`)
          .slice(0, 8);
        falhas.push(`${caminho} ${categoria}: ${nota} < ${minimo}\n${auditorias.join('\n')}`);
      }
      return `${categoria} ${nota}`;
    });
    console.log(`${caminho.padEnd(40)} ${notas.join(' · ')}`);
  }
} finally {
  await navegador.close();
}

if (falhas.length) {
  console.error(`\nAbaixo da meta:\n${falhas.join('\n')}`);
  process.exit(1);
}
console.log('\nTodas as páginas atingiram a meta.');
