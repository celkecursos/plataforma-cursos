import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ImagemLocal } from '@compartilhado/ui/imagens.ts';
import { enviarImagensForum } from './paginas/forum/useImagensMensagem.ts';

const { post, enviarParaUrl } = vi.hoisted(() => ({ post: vi.fn(), enviarParaUrl: vi.fn() }));
vi.mock('@compartilhado/api/cliente.ts', () => ({ api: { post } }));
vi.mock('@compartilhado/api/envio.ts', () => ({ enviarParaUrl }));

function imagem(id: string, nome: string, tipo: string, tamanho: number): ImagemLocal {
  const arquivo = new File(['x'], nome, { type: tipo });
  Object.defineProperty(arquivo, 'size', { value: tamanho });
  return { id, arquivo, previa: `blob:${nome}` };
}

afterEach(() => vi.clearAllMocks());

describe('envio das imagens do fórum', () => {
  it('pede a URL, envia direto ao armazenamento e devolve as chaves na ordem', async () => {
    post
      .mockResolvedValueOnce({
        chave: 'uploads/forum/7/a',
        url: 'https://midia/a',
        contentType: 'image/png',
      })
      .mockResolvedValueOnce({
        chave: 'uploads/forum/7/b',
        url: 'https://midia/b',
        contentType: 'image/jpeg',
      });
    enviarParaUrl.mockImplementation(
      async (_url, _arquivo, _tipo, aoProgredir: (n: number) => void) => {
        aoProgredir(50);
        return '"etag"';
      },
    );
    const progresso: [string, number][] = [];

    const chaves = await enviarImagensForum(
      [imagem('1', 'a.png', 'image/png', 100), imagem('2', 'b.jpg', 'image/jpeg', 200)],
      (id, fracao) => progresso.push([id, fracao]),
    );

    expect(chaves).toEqual(['uploads/forum/7/a', 'uploads/forum/7/b']);
    expect(post).toHaveBeenNthCalledWith(1, '/api/aluno/forum/anexos/envio', {
      contentType: 'image/png',
      tamanho: 100,
    });
    // O PUT leva o Content-Type que a API assinou
    expect(enviarParaUrl.mock.calls[1]!.slice(0, 3)).toEqual([
      'https://midia/b',
      expect.any(File),
      'image/jpeg',
    ]);
    expect(progresso).toEqual([
      ['1', 0],
      ['1', 0.5],
      ['1', 1],
      ['2', 0],
      ['2', 0.25],
      ['2', 1],
    ]);
  });

  it('se uma imagem falha, o erro sobe e as seguintes não são enviadas', async () => {
    post.mockResolvedValue({ chave: 'k', url: 'https://midia/k', contentType: 'image/png' });
    enviarParaUrl.mockRejectedValueOnce(new Error('armazenamento recusou'));

    await expect(
      enviarImagensForum(
        [imagem('1', 'a.png', 'image/png', 10), imagem('2', 'b.png', 'image/png', 10)],
        () => undefined,
      ),
    ).rejects.toThrow('armazenamento recusou');
    expect(post).toHaveBeenCalledTimes(1);
  });

  it('sem imagens, não chama a API', async () => {
    expect(await enviarImagensForum([], () => undefined)).toEqual([]);
    expect(post).not.toHaveBeenCalled();
  });
});
