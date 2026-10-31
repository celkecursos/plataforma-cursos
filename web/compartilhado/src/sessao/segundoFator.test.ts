import { describe, expect, it } from 'vitest';
import {
  erroCodigo,
  erroCodigoRecuperacao,
  limparCodigo,
  segredoEmGrupos,
} from './segundoFator.ts';

describe('erroCodigo', () => {
  it('aceita 6 dígitos, com ou sem o espaço que o aplicativo mostra', () => {
    expect(erroCodigo('123456')).toBeUndefined();
    expect(erroCodigo('123 456')).toBeUndefined();
    expect(limparCodigo(' 123 456 ')).toBe('123456');
  });

  it('recusa vazio, letras e tamanho errado', () => {
    expect(erroCodigo('')).toBe('Informe o código de 6 dígitos do aplicativo.');
    expect(erroCodigo('12345')).toBe('O código tem 6 dígitos.');
    expect(erroCodigo('12a456')).toBe('O código tem 6 dígitos.');
  });
});

describe('erroCodigoRecuperacao', () => {
  it('aceita 10 letras e números, com ou sem hífen', () => {
    expect(erroCodigoRecuperacao('abcde-fghjk')).toBeUndefined();
    expect(erroCodigoRecuperacao('ABCDEFGHJK')).toBeUndefined();
  });

  it('recusa vazio e tamanho errado', () => {
    expect(erroCodigoRecuperacao(' ')).toBe('Informe um dos códigos de recuperação.');
    expect(erroCodigoRecuperacao('abcde')).toBe('O código de recuperação tem 10 letras e números.');
  });
});

describe('segredoEmGrupos', () => {
  it('separa em grupos de 4', () => {
    expect(segredoEmGrupos('JBSWY3DPEHPK3PXP')).toBe('JBSW Y3DP EHPK 3PXP');
    expect(segredoEmGrupos('ABCDE')).toBe('ABCD E');
  });
});
