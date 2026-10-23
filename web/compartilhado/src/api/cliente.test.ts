import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ClienteApi } from './cliente.ts';
import { ErroApi } from './erros.ts';
import type { Sessao } from './tipos.ts';

function sessao(token: string): Sessao {
  return {
    accessToken: token,
    tipo: 'Bearer',
    expiraEm: '2026-01-01T00:10:00Z',
    usuario: { id: 1, nome: 'Ana', email: 'ana@celke.uno', papeis: ['ALUNO'] },
  };
}

function json(status: number, corpo: unknown, cabecalhos: Record<string, string> = {}) {
  return new Response(JSON.stringify(corpo), {
    status,
    headers: { 'Content-Type': 'application/json', ...cabecalhos },
  });
}

describe('ClienteApi', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it('manda o token no cabeçalho e o corpo como JSON', async () => {
    const api = new ClienteApi();
    api.definirSessao(sessao('t1'));
    fetchMock.mockResolvedValueOnce(json(201, { id: 9 }));

    await expect(api.post('/api/x', { nome: 'a' })).resolves.toEqual({ id: 9 });

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe('/api/x');
    expect(init?.method).toBe('POST');
    expect((init?.headers as Record<string, string>)['Authorization']).toBe('Bearer t1');
    expect(init?.body).toBe('{"nome":"a"}');
  });

  it('no 401 renova a sessão uma vez e repete a requisição com o token novo', async () => {
    const api = new ClienteApi();
    api.definirSessao(sessao('velho'));
    fetchMock
      .mockResolvedValueOnce(json(401, { detail: 'Token expirado.' }))
      .mockResolvedValueOnce(json(200, sessao('novo')))
      .mockResolvedValueOnce(json(200, { ok: true }));

    await expect(api.get('/api/aluno/cursos')).resolves.toEqual({ ok: true });

    expect(fetchMock.mock.calls[1]![0]).toBe('/api/auth/refresh');
    const repetida = fetchMock.mock.calls[2]![1]?.headers as Record<string, string>;
    expect(repetida['Authorization']).toBe('Bearer novo');
  });

  it('dois 401 ao mesmo tempo esperam a mesma renovação (o refresh token não é reutilizado)', async () => {
    const api = new ClienteApi();
    api.definirSessao(sessao('velho'));
    let renovacoes = 0;
    fetchMock.mockImplementation(async (url, init) => {
      if (url === '/api/auth/refresh') {
        renovacoes++;
        return json(200, sessao('novo'));
      }
      const auth = (init?.headers as Record<string, string>)['Authorization'];
      return auth === 'Bearer novo' ? json(200, { url }) : json(401, {});
    });

    await Promise.all([api.get('/api/a'), api.get('/api/b'), api.get('/api/c')]);

    expect(renovacoes).toBe(1);
  });

  it('renovação recusada encerra a sessão e devolve o erro original', async () => {
    const api = new ClienteApi();
    const ouvinte = vi.fn();
    api.definirSessao(sessao('velho'));
    api.aoMudarSessao(ouvinte);
    fetchMock
      .mockResolvedValueOnce(json(401, { detail: 'Token expirado.' }))
      .mockResolvedValueOnce(json(401, { detail: 'Sessão inválida.' }));

    await expect(api.get('/api/aluno/cursos')).rejects.toMatchObject({ status: 401 });
    expect(ouvinte).toHaveBeenCalledWith(null);
    expect(api.autenticado).toBe(false);
  });

  it('sem sessão, o 401 não tenta renovar', async () => {
    const api = new ClienteApi();
    fetchMock.mockResolvedValueOnce(json(401, { detail: 'Autenticação necessária.' }));

    await expect(api.get('/api/aluno/cursos')).rejects.toBeInstanceOf(ErroApi);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('erro da API vira ErroApi com o detail, os campos e o Retry-After', async () => {
    const api = new ClienteApi();
    fetchMock.mockResolvedValueOnce(
      json(
        400,
        { detail: 'Dados inválidos.', erros: [{ campo: 'email', mensagem: 'inválido' }] },
        { 'Retry-After': '30' },
      ),
    );

    const erro = await api.post('/api/auth/cadastro', {}).catch((e: unknown) => e);

    expect(erro).toBeInstanceOf(ErroApi);
    expect((erro as ErroApi).message).toBe('Dados inválidos.');
    expect((erro as ErroApi).erroDoCampo('email')).toBe('inválido');
    expect((erro as ErroApi).tentarEm).toBe(30);
  });

  it('falha de rede vira ErroApi com status 0 e mensagem de conexão', async () => {
    const api = new ClienteApi();
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));

    const erro = (await api.get('/api/x').catch((e: unknown) => e)) as ErroApi;

    expect(erro.status).toBe(0);
    expect(erro.message).toContain('Sem conexão');
  });

  it('204 devolve undefined', async () => {
    const api = new ClienteApi();
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await expect(api.delete('/api/x')).resolves.toBeUndefined();
  });
});
