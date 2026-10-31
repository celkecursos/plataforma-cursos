import { inflateSync } from 'node:zlib';
import { describe, expect, it } from 'vitest';
import { capa } from './cursos-exemplo.ts';

describe('capa', () => {
  it('gera um PNG RGB com o tamanho pedido e o degradê entre as duas cores', () => {
    const png = capa(['#000000', '#ffffff'], 40, 20);
    expect(png.subarray(0, 8)).toEqual(
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    );
    expect(png.toString('ascii', 12, 16)).toBe('IHDR');
    expect(png.readUInt32BE(16)).toBe(40);
    expect(png.readUInt32BE(20)).toBe(20);
    expect(png[25]).toBe(2);

    const tamanhoIdat = png.readUInt32BE(33);
    expect(png.toString('ascii', 37, 41)).toBe('IDAT');
    const pixels = inflateSync(png.subarray(41, 41 + tamanhoIdat));
    expect(pixels).toHaveLength((40 * 3 + 1) * 20);
    // Canto superior esquerdo fica mais escuro que o inferior direito
    const ultimaLinha = 19 * (40 * 3 + 1);
    expect(pixels[1]!).toBeLessThan(pixels[ultimaLinha + 1 + 39 * 3]!);
  });
});
