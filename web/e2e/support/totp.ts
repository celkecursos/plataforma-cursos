import { createHmac } from 'node:crypto';
import { TOTP_SEED } from './config.ts';

const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

function decodificarBase32(texto: string): Buffer {
  const bytes: number[] = [];
  let buffer = 0;
  let bits = 0;
  for (const c of texto.replace(/[\s=]/g, '').toUpperCase()) {
    buffer = (buffer << 5) | BASE32.indexOf(c);
    bits += 5;
    if (bits >= 8) {
      bytes.push((buffer >> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(bytes);
}

/** Código atual do aplicativo autenticador (TOTP, RFC 6238), como o celular calcula. */
export function codigoTotp(segredo = TOTP_SEED): string {
  const passo = Buffer.alloc(8);
  passo.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30_000)));
  const hash = createHmac('sha1', decodificarBase32(segredo)).update(passo).digest();
  const i = hash[hash.length - 1]! & 0x0f;
  const binario = hash.readUInt32BE(i) & 0x7fffffff;
  return String(binario % 1_000_000).padStart(6, '0');
}
