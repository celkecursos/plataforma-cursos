import { describe, expect, it } from 'vitest';
import {
  erroConfirmacao,
  erroEmail,
  erroObrigatorio,
  erroSenhaNova,
  semErros,
} from './validacao.ts';
import { destinoAposLogin } from '../sessao/RotaPrivada.tsx';

describe('validação de formulários', () => {
  it.each([
    ['', 'Informe o e-mail.'],
    ['ana', 'Informe um e-mail válido.'],
    ['ana@celke', 'Informe um e-mail válido.'],
    [' ana@celke.uno ', undefined],
  ])('e-mail "%s" → %s', (valor, esperado) => {
    expect(erroEmail(valor)).toBe(esperado);
  });

  it.each([
    ['', 'Informe a senha.'],
    ['123456789', 'A senha deve ter pelo menos 10 caracteres.'],
    ['1234567890', undefined],
    ['x'.repeat(129), 'A senha deve ter no máximo 128 caracteres.'],
  ])('senha de %s caracteres', (valor, esperado) => {
    expect(erroSenhaNova(valor)).toBe(esperado);
  });

  it('confirmação precisa ser igual', () => {
    expect(erroConfirmacao('abc', '')).toBe('Repita a senha.');
    expect(erroConfirmacao('abc', 'abd')).toBe('As senhas não conferem.');
    expect(erroConfirmacao('abc', 'abc')).toBeUndefined();
  });

  it('obrigatório com tamanho máximo', () => {
    expect(erroObrigatorio('  ', 'Informe o nome.')).toBe('Informe o nome.');
    expect(erroObrigatorio('abcd', 'x', 3)).toBe('Use no máximo 3 caracteres.');
    expect(semErros({ a: undefined, b: '' })).toBe(true);
    expect(semErros({ a: 'erro' })).toBe(false);
  });

  it.each([
    [null, '/'],
    ['/turmas/5', '/turmas/5'],
    ['https://mal.example', '/'],
    ['//mal.example', '/'],
    ['javascript:alert(1)', '/'],
  ])('destino após o login para "%s" → %s (sem redirecionamento aberto)', (voltar, esperado) => {
    expect(destinoAposLogin(voltar)).toBe(esperado);
  });
});
