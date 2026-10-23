import { data } from 'react-router';
import { API_URL, CACHE_MS } from './config.server.ts';
import type { Certificado, CursoCatalogo, CursoDetalhe, Termo } from './seo.ts';

interface Guardado {
  expiraEm: number;
  valor: Promise<unknown>;
}

const cache = new Map<string, Guardado>();

/**
 * GET na API com cache curto em memória. Leituras simultâneas do mesmo endereço esperam a mesma
 * resposta; um erro não fica guardado. 404 vira a página "não encontrada" do site.
 */
async function buscar<T>(caminho: string): Promise<T> {
  const agora = Date.now();
  const guardado = cache.get(caminho);
  if (guardado && guardado.expiraEm > agora) return guardado.valor as Promise<T>;
  const valor = fetch(API_URL + caminho, {
    headers: { Accept: 'application/json' },
    signal: AbortSignal.timeout(10_000),
  }).then(async (resposta) => {
    if (resposta.status === 404) throw data('Não encontrado', { status: 404 });
    if (!resposta.ok) throw new Error(`GET ${caminho}: ${resposta.status}`);
    return (await resposta.json()) as T;
  });
  cache.set(caminho, { expiraEm: agora + CACHE_MS, valor });
  valor.catch(() => cache.delete(caminho));
  if (cache.size > 500) {
    for (const [chave, item] of cache) if (item.expiraEm <= agora) cache.delete(chave);
  }
  return valor;
}

/** Todos os cursos publicados (o catálogo é paginado, até 100 por página). */
export async function cursos(): Promise<CursoCatalogo[]> {
  const todos: CursoCatalogo[] = [];
  for (let pagina = 0; ; pagina++) {
    const resposta = await buscar<{ itens: CursoCatalogo[]; totalPaginas: number }>(
      `/api/cursos?page=${pagina}&size=100`,
    );
    todos.push(...resposta.itens);
    if (pagina + 1 >= resposta.totalPaginas) return todos;
  }
}

export function curso(slug: string): Promise<CursoDetalhe> {
  return buscar<CursoDetalhe>(`/api/cursos/${encodeURIComponent(slug)}`);
}

export function termosVigentes(): Promise<Termo[]> {
  return buscar<Termo[]>('/api/termos/vigentes');
}

/** Certificado pelo código; vazio quando não existe. Sem cache: a consulta é pontual. */
export async function certificado(codigo: string): Promise<Certificado | null> {
  const resposta = await fetch(`${API_URL}/api/certificados/${encodeURIComponent(codigo)}`, {
    headers: { Accept: 'application/json' },
    signal: AbortSignal.timeout(10_000),
  });
  if (resposta.status === 404) return null;
  if (!resposta.ok) throw new Error(`GET /api/certificados: ${resposta.status}`);
  return (await resposta.json()) as Certificado;
}
