import { describe, expect, it } from 'vitest';
import {
  erroConfirmacaoExclusao,
  nomeArquivoDados,
  urlPoliticaPrivacidade,
} from './privacidade.ts';

describe('erroConfirmacaoExclusao', () => {
  it('só aceita a palavra EXCLUIR, em maiúsculas', () => {
    expect(erroConfirmacaoExclusao('EXCLUIR')).toBeUndefined();
    expect(erroConfirmacaoExclusao(' EXCLUIR ')).toBeUndefined();
    expect(erroConfirmacaoExclusao('excluir')).toBe('Digite EXCLUIR para confirmar.');
    expect(erroConfirmacaoExclusao('')).toBe('Digite EXCLUIR para confirmar.');
  });
});

describe('nomeArquivoDados', () => {
  it('leva a data local com zeros à esquerda', () => {
    expect(nomeArquivoDados(new Date(2026, 0, 5, 23, 59))).toBe('meus-dados-2026-01-05.json');
  });
});

describe('urlPoliticaPrivacidade', () => {
  it('troca o subdomínio do aluno pelo do site', () => {
    expect(urlPoliticaPrivacidade({ protocol: 'https:', host: 'aluno.celke.uno' })).toBe(
      'https://cursos.celke.uno/privacidade/',
    );
    expect(urlPoliticaPrivacidade({ protocol: 'http:', host: 'aluno.localhost:8088' })).toBe(
      'http://cursos.localhost:8088/privacidade/',
    );
  });
});
