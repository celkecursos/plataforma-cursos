/**
 * Gerador do site público (cursos.celke.uno): busca o catálogo na API do próprio ambiente e grava
 * uma página HTML por rota. Roda no container `site` ao subir e de novo a cada SITE_INTERVALO
 * segundos; o Caddy do container `web` serve a versão mais recente.
 *
 * Cada geração vai para uma pasta nova em versoes/ e só então o link `atual` passa a apontar para
 * ela (troca atômica): o Caddy nunca serve um site pela metade. Se a API falhar, a versão anterior
 * continua no ar e a geração é tentada de novo em pouco tempo.
 *
 *   node site/gerar.ts            (laço)
 *   node site/gerar.ts --uma-vez  (uma geração e sai)
 */
import { cp, mkdir, readdir, rename, rm, symlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import {
  paginaCatalogo,
  paginaCurso,
  paginaInicial,
  paginaNaoEncontrada,
  paginaTermo,
  paginaValidar,
  robots,
  sitemap,
  type Contexto,
  type CursoCatalogo,
  type CursoDetalhe,
  type Termo,
} from './paginas.ts';

const API_URL = (process.env['API_URL'] ?? 'http://localhost:8080').replace(/\/$/, '');
const ALUNO_URL = (process.env['FRONT_ALUNO_URL'] ?? 'http://localhost:5173').replace(/\/$/, '');
/** Sem SITE_URL, deriva do endereço do aluno (aluno.celke.uno → cursos.celke.uno). */
const SITE_URL = (process.env['SITE_URL'] || ALUNO_URL.replace('aluno.', 'cursos.')).replace(
  /\/$/,
  '',
);
const DESTINO = process.env['SITE_DESTINO'] ?? '/site';
const ASSETS = process.env['SITE_ASSETS'] ?? path.join(import.meta.dirname, 'assets');
const INTERVALO = Number(process.env['SITE_INTERVALO'] ?? 300) * 1000;
const NOVA_TENTATIVA = 30_000;
const VERSOES_GUARDADAS = 3;

async function buscar<T>(caminho: string): Promise<T> {
  const resposta = await fetch(API_URL + caminho, { headers: { Accept: 'application/json' } });
  if (!resposta.ok) throw new Error(`GET ${caminho}: ${resposta.status}`);
  return (await resposta.json()) as T;
}

/** Todos os cursos publicados (o catálogo é paginado, até 100 por página). */
async function todosOsCursos(): Promise<CursoCatalogo[]> {
  const cursos: CursoCatalogo[] = [];
  for (let pagina = 0; ; pagina++) {
    const resposta = await buscar<{ itens: CursoCatalogo[]; totalPaginas: number }>(
      `/api/cursos?page=${pagina}&size=100`,
    );
    cursos.push(...resposta.itens);
    if (pagina + 1 >= resposta.totalPaginas) return cursos;
  }
}

async function gravar(raiz: string, caminho: string, conteudo: string): Promise<void> {
  const arquivo = path.join(raiz, caminho.endsWith('/') ? `${caminho}index.html` : caminho);
  await mkdir(path.dirname(arquivo), { recursive: true });
  await writeFile(arquivo, conteudo);
}

export async function gerar(): Promise<number> {
  const ctx: Contexto = {
    siteUrl: SITE_URL,
    alunoUrl: ALUNO_URL,
    indexavel: process.env['SITE_INDEXAVEL'] !== 'false',
    ano: new Date().getFullYear(),
  };
  const [cursos, termos] = await Promise.all([
    todosOsCursos(),
    buscar<Termo[]>('/api/termos/vigentes'),
  ]);
  const detalhes = await Promise.all(
    cursos.map((c) => buscar<CursoDetalhe>(`/api/cursos/${c.slug}`)),
  );

  const versao = `v${Date.now()}`;
  const raiz = path.join(DESTINO, 'versoes', versao);
  await cp(ASSETS, path.join(raiz, 'assets'), { recursive: true });
  await gravar(raiz, '/', paginaInicial(ctx, cursos));
  await gravar(raiz, '/cursos/', paginaCatalogo(ctx, cursos));
  for (const curso of detalhes)
    await gravar(raiz, `/cursos/${curso.slug}/`, paginaCurso(ctx, curso));
  await gravar(raiz, '/certificados/validar/', paginaValidar(ctx));
  await gravar(
    raiz,
    '/termos/',
    paginaTermo(
      ctx,
      termos.find((t) => t.tipo === 'TERMOS_USO'),
      '/termos/',
    ),
  );
  await gravar(
    raiz,
    '/privacidade/',
    paginaTermo(
      ctx,
      termos.find((t) => t.tipo === 'POLITICA_PRIVACIDADE'),
      '/privacidade/',
    ),
  );
  await gravar(raiz, '/404.html', paginaNaoEncontrada(ctx));
  await gravar(raiz, '/sitemap.xml', sitemap(ctx, cursos));
  await gravar(raiz, '/robots.txt', robots(ctx));

  // Troca atômica: link temporário renomeado por cima do `atual`
  const temporario = path.join(DESTINO, `atual.${versao}`);
  await symlink(path.join('versoes', versao), temporario);
  await rename(temporario, path.join(DESTINO, 'atual'));

  const antigas = (await readdir(path.join(DESTINO, 'versoes')))
    .sort()
    .slice(0, -VERSOES_GUARDADAS);
  await Promise.all(
    antigas.map((v) => rm(path.join(DESTINO, 'versoes', v), { recursive: true, force: true })),
  );
  return cursos.length;
}

function registrar(mensagem: string): void {
  console.log(
    JSON.stringify({ '@timestamp': new Date().toISOString(), servico: 'site', mensagem }),
  );
}

async function laco(): Promise<void> {
  for (;;) {
    let espera = INTERVALO;
    try {
      const total = await gerar();
      registrar(`site gerado com ${total} cursos`);
    } catch (erro) {
      registrar(`falha ao gerar o site (a versão anterior continua no ar): ${String(erro)}`);
      espera = Math.min(INTERVALO, NOVA_TENTATIVA);
    }
    await new Promise((fim) => setTimeout(fim, espera));
  }
}

if (import.meta.main) {
  if (process.argv.includes('--uma-vez')) {
    registrar(`site gerado com ${await gerar()} cursos`);
  } else {
    process.on('SIGTERM', () => process.exit(0));
    await laco();
  }
}
