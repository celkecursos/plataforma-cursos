import { describe, expect, it } from 'vitest';
import { moeda } from '@compartilhado/ui/formato.ts';
import { formatarJson } from './paginas/pagamentos/DialogoEvento.tsx';
import { erroCampo } from './paginas/pagamentos/FormGateway.tsx';
import type { CampoConfiguracao, Gateway } from './tipos.ts';

const campo = (parcial: Partial<CampoConfiguracao>): CampoConfiguracao => ({
  chave: 'hottok',
  rotulo: 'Hottok',
  tipo: 'TEXTO',
  secreto: true,
  obrigatorio: true,
  dica: null,
  padrao: null,
  opcoes: [],
  ...parcial,
});

const gatewayCom = (configurado: boolean) =>
  ({
    segredos: { hottok: { configurado, final4: configurado ? '1234' : null } },
  }) as unknown as Gateway;

describe('formulário do meio de pagamento', () => {
  it('obrigatório em branco: erro, exceto segredo já configurado ou campo com padrão', () => {
    expect(erroCampo(campo({}), '  ')).toBe('Informe Hottok.');
    expect(erroCampo(campo({}), '', gatewayCom(true))).toBeUndefined();
    expect(erroCampo(campo({}), '', gatewayCom(false))).toBe('Informe Hottok.');
    expect(erroCampo(campo({ secreto: false, padrao: 'x' }), '')).toBeUndefined();
    expect(erroCampo(campo({ obrigatorio: false }), undefined)).toBeUndefined();
  });

  it('endereço precisa começar com http(s) e o valor tem no máximo 500 caracteres', () => {
    const url = campo({ tipo: 'URL', secreto: false });
    expect(erroCampo(url, 'pay.hotmart.com')).toBe('Informe um endereço começando com https://.');
    expect(erroCampo(url, 'https://pay.hotmart.com')).toBeUndefined();
    expect(erroCampo(campo({}), 'a'.repeat(501))).toBe('Use no máximo 500 caracteres.');
  });
});

describe('corpo do evento', () => {
  it('JSON indentado; texto (corpo que não era JSON) fica como veio', () => {
    expect(formatarJson('{"a":{"b":1}}')).toBe('{\n  "a": {\n    "b": 1\n  }\n}');
    expect(formatarJson('"não é json"')).toBe('não é json');
    expect(formatarJson('quebrado {')).toBe('quebrado {');
  });
});

describe('moeda', () => {
  it('centavos na moeda informada; vazio vira travessão', () => {
    expect(moeda(19_700)).toBe('R$ 197,00');
    expect(moeda(5, 'USD')).toBe('US$ 0,05');
    expect(moeda(null)).toBe('—');
  });
});
