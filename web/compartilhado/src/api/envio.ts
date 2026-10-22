import { ErroApi } from './erros.ts';

/** Progresso de um envio, em bytes já enviados. */
export type AoProgredir = (enviados: number) => void;

export interface ParteEnviada {
  numero: number;
  etag: string;
}

/**
 * PUT do corpo direto no Garage, numa URL pré-assinada pela API (o arquivo não passa pela API).
 * XHR em vez de fetch: só ele informa o progresso do envio. Devolve o ETag da resposta (o bucket o
 * expõe por CORS).
 */
export function enviarParaUrl(
  url: string,
  corpo: Blob,
  contentType: string | null,
  aoProgredir?: AoProgredir,
  sinal?: AbortSignal,
): Promise<string> {
  return new Promise((resolver, rejeitar) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    if (contentType) xhr.setRequestHeader('Content-Type', contentType);
    xhr.upload.onprogress = (evento) => aoProgredir?.(evento.loaded);
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolver(xhr.getResponseHeader('ETag') ?? '');
      } else {
        rejeitar(
          new ErroApi(xhr.status, { detail: 'O armazenamento recusou o arquivo. Tente de novo.' }),
        );
      }
    };
    xhr.onerror = () => rejeitar(new ErroApi(0, null));
    xhr.onabort = () => rejeitar(new DOMException('Envio cancelado.', 'AbortError'));
    sinal?.addEventListener('abort', () => xhr.abort(), { once: true });
    xhr.send(corpo);
  });
}

/**
 * Envio em partes (vídeo): cada parte vai para a sua URL, em ordem, e o progresso soma o que já
 * foi enviado. Devolve número e ETag de cada parte para a API concluir o envio.
 */
export async function enviarEmPartes(
  arquivo: Blob,
  tamanhoParte: number,
  urls: { numero: number; url: string }[],
  aoProgredir?: AoProgredir,
  sinal?: AbortSignal,
): Promise<ParteEnviada[]> {
  const enviadas: ParteEnviada[] = [];
  for (const { numero, url } of [...urls].sort((a, b) => a.numero - b.numero)) {
    const inicio = (numero - 1) * tamanhoParte;
    const parte = arquivo.slice(inicio, Math.min(arquivo.size, inicio + tamanhoParte));
    const etag = await enviarParaUrl(url, parte, null, (n) => aoProgredir?.(inicio + n), sinal);
    enviadas.push({ numero, etag });
  }
  aoProgredir?.(arquivo.size);
  return enviadas;
}
