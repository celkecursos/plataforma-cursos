import { describe, expect, it } from 'vitest';
import { mudancas } from './paginas/auditoria/AuditoriaPagina.tsx';

describe('campos da auditoria', () => {
  it('alteração: antes e depois lado a lado', () => {
    expect(
      mudancas('{"titulo": "Antigo", "ativo": true}', '{"titulo": "Novo", "ativo": false}'),
    ).toEqual([
      { campo: 'titulo', antes: 'Antigo', depois: 'Novo' },
      { campo: 'ativo', antes: 'true', depois: 'false' },
    ]);
  });

  it('inclusão e exclusão: o lado que falta vira travessão; nulo vira "vazio"', () => {
    expect(mudancas(null, '{"fim": null, "papeis": ["ALUNO"]}')).toEqual([
      { campo: 'fim', antes: '—', depois: 'vazio' },
      { campo: 'papeis', antes: '—', depois: '["ALUNO"]' },
    ]);
    expect(mudancas('{"senhaHash": "***"}', null)).toEqual([
      { campo: 'senhaHash', antes: '***', depois: '—' },
    ]);
  });

  it('JSON inválido não quebra a tela', () => {
    expect(mudancas('quebrado', null)).toEqual([]);
  });
});
