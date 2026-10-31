import { afterEach, describe, expect, it, vi } from 'vitest';
import { enviarEmPartes, enviarParaUrl } from './envio.ts';
import { ErroApi } from './erros.ts';

/** XHR falso: guarda cada envio e responde com o status e o ETag configurados. */
class XhrFalso {
  static enviados: { url: string; corpo: Blob; cabecalhos: Record<string, string> }[] = [];
  static status = 200;
  url = '';
  status = 0;
  cabecalhos: Record<string, string> = {};
  upload: { onprogress?: (e: { loaded: number }) => void } = {};
  onload?: () => void;
  onerror?: () => void;
  onabort?: () => void;

  open(_metodo: string, url: string) {
    this.url = url;
  }
  setRequestHeader(nome: string, valor: string) {
    this.cabecalhos[nome] = valor;
  }
  getResponseHeader(nome: string) {
    return nome === 'ETag' ? `"etag-${XhrFalso.enviados.length}"` : null;
  }
  abort() {
    this.onabort?.();
  }
  send(corpo: Blob) {
    XhrFalso.enviados.push({ url: this.url, corpo, cabecalhos: this.cabecalhos });
    this.upload.onprogress?.({ loaded: corpo.size });
    this.status = XhrFalso.status;
    this.onload?.();
  }
}

afterEach(() => {
  XhrFalso.enviados = [];
  XhrFalso.status = 200;
  vi.unstubAllGlobals();
});

describe('envio direto ao armazenamento', () => {
  it('manda o Content-Type informado e devolve o ETag', async () => {
    vi.stubGlobal('XMLHttpRequest', XhrFalso);
    const etag = await enviarParaUrl('https://midia/x', new Blob(['abc']), 'image/png');
    expect(etag).toBe('"etag-1"');
    expect(XhrFalso.enviados[0]!.cabecalhos).toEqual({ 'Content-Type': 'image/png' });
  });

  it('status de erro vira ErroApi', async () => {
    vi.stubGlobal('XMLHttpRequest', XhrFalso);
    XhrFalso.status = 403;
    await expect(enviarParaUrl('https://midia/x', new Blob(['a']), null)).rejects.toBeInstanceOf(
      ErroApi,
    );
  });

  it('divide o arquivo pelas partes, em ordem, e soma o progresso', async () => {
    vi.stubGlobal('XMLHttpRequest', XhrFalso);
    const progresso: number[] = [];
    const partes = await enviarEmPartes(
      new Blob(['0123456789']),
      4,
      [
        { numero: 3, url: 'u3' },
        { numero: 1, url: 'u1' },
        { numero: 2, url: 'u2' },
      ],
      (n) => progresso.push(n),
    );

    expect(XhrFalso.enviados.map((e) => [e.url, e.corpo.size])).toEqual([
      ['u1', 4],
      ['u2', 4],
      ['u3', 2],
    ]);
    expect(partes).toEqual([
      { numero: 1, etag: '"etag-1"' },
      { numero: 2, etag: '"etag-2"' },
      { numero: 3, etag: '"etag-3"' },
    ]);
    expect(progresso).toEqual([4, 8, 10, 10]);
    // Parte de vídeo vai sem Content-Type: a URL assinada não o inclui
    expect(XhrFalso.enviados[0]!.cabecalhos).toEqual({});
  });
});
