import type { Contexto } from './seo.ts';

const sem = (url: string) => url.replace(/\/$/, '');

/** API do próprio ambiente pela rede interna (no compose: http://api:8080). */
export const API_URL = sem(process.env['API_URL'] ?? 'http://localhost:8080');

const ALUNO_URL = sem(process.env['FRONT_ALUNO_URL'] ?? 'http://localhost:5173');

/** Endereços e regras do ambiente. Sem SITE_URL, deriva do aluno (aluno.celke.uno → cursos.celke.uno). */
export function contexto(): Contexto {
  return {
    siteUrl: sem(process.env['SITE_URL'] || ALUNO_URL.replace('aluno.', 'cursos.')),
    alunoUrl: ALUNO_URL,
    indexavel: process.env['SITE_INDEXAVEL'] !== 'false',
    ano: new Date().getFullYear(),
  };
}

/** Tempo que uma leitura da API fica em memória (um pico de acessos não vira um pico na API). */
export const CACHE_MS = Number(process.env['SITE_CACHE_SEGUNDOS'] ?? 30) * 1000;
