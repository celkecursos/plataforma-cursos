import type { Contexto } from '../seo.ts';

/** Contexto do ambiente (loader da raiz) dentro do `meta` de uma rota. */
export function contextoDe(
  matches: readonly ({ id: string; data?: unknown } | undefined)[],
): Contexto {
  const raizMatch = matches.find((m) => m?.id === 'root') as
    { loaderData?: unknown; data?: unknown } | undefined;
  const raiz = (raizMatch?.loaderData ?? raizMatch?.data) as Contexto | undefined;
  return raiz ?? { siteUrl: '', alunoUrl: '', indexavel: false, ano: new Date().getFullYear() };
}

/** HTML público sem cookies: pode ficar um minuto em cache no navegador e nos intermediários. */
export function cachePublico() {
  return { 'Cache-Control': 'public, max-age=60, stale-while-revalidate=300' };
}
