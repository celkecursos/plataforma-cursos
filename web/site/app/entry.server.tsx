import { randomBytes } from 'node:crypto';
import { PassThrough } from 'node:stream';
import { createReadableStreamFromReadable } from '@react-router/node';
import { isbot } from 'isbot';
import { renderToPipeableStream } from 'react-dom/server';
import { ServerRouter, type EntryContext } from 'react-router';
import { ContextoNonce } from './nonce.ts';

const LIMITE_MS = 10_000;

/** Origem das capas (URLs pré-assinadas do armazenamento), liberada nas imagens. */
const MIDIA = process.env['MIDIA_ORIGEM'] ?? '';

/** Política da página: só o que é do próprio site, e os scripts de hidratação com o nonce desta resposta. */
export function politica(nonce: string): string {
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}'`,
    "style-src 'self'",
    `img-src 'self' data: ${MIDIA}`.trim(),
    "connect-src 'self'",
    "font-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join('; ');
}

/**
 * Renderiza no servidor. Buscadores e robôs recebem a página inteira de uma vez; navegadores começam
 * a receber o HTML em streaming.
 */
export default function handleRequest(
  request: Request,
  status: number,
  headers: Headers,
  contexto: EntryContext,
): Promise<Response> {
  const nonce = randomBytes(16).toString('base64');
  const robo = isbot(request.headers.get('user-agent') ?? '');
  return new Promise((resolver, rejeitar) => {
    let erroDeRender = false;
    const { pipe, abort } = renderToPipeableStream(
      <ContextoNonce.Provider value={nonce}>
        <ServerRouter context={contexto} url={request.url} nonce={nonce} />
      </ContextoNonce.Provider>,
      {
        nonce,
        [robo || contexto.isSpaMode ? 'onAllReady' : 'onShellReady']() {
          const corpo = new PassThrough();
          headers.set('Content-Type', 'text/html; charset=utf-8');
          headers.set('Content-Security-Policy', politica(nonce));
          headers.set('X-Frame-Options', 'DENY');
          resolver(
            new Response(createReadableStreamFromReadable(corpo), {
              headers,
              status: erroDeRender ? 500 : status,
            }),
          );
          pipe(corpo);
        },
        onShellError: rejeitar,
        onError(erro: unknown) {
          erroDeRender = true;
          console.error(erro);
        },
      },
    );
    setTimeout(abort, LIMITE_MS + 1_000);
  });
}

export const streamTimeout = LIMITE_MS;
