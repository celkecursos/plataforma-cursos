import { describe, expect, it } from 'vitest';
import { semToken } from './useTokenDaUrl.ts';

describe('semToken', () => {
  it('tira só o token e mantém os outros parâmetros', () => {
    expect(semToken(new URLSearchParams('token=abc&origem=pagamento')).toString()).toBe(
      'origem=pagamento',
    );
  });

  it('sem token, não muda nada', () => {
    expect(semToken(new URLSearchParams('origem=pagamento')).toString()).toBe('origem=pagamento');
    expect(semToken(new URLSearchParams('')).toString()).toBe('');
  });
});
