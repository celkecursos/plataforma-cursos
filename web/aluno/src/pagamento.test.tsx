import { act, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  INTERVALO_MS,
  LIMITE_MS,
  resultado,
  RetornoPagamentoPagina,
} from './paginas/pagamento/RetornoPagamentoPagina.tsx';
import type { StatusPedido } from './tipos.ts';

describe('resultado do pedido na página de retorno', () => {
  it.each<[StatusPedido, string]>([
    ['APROVADO', 'confirmado'],
    ['CONCLUIDO', 'confirmado'],
    ['CRIADO', 'aguardando'],
    ['AGUARDANDO_PAGAMENTO', 'aguardando'],
    ['CANCELADO', 'recusado'],
    ['EXPIRADO', 'recusado'],
    ['REEMBOLSADO', 'recusado'],
    ['CHARGEBACK', 'recusado'],
  ])('%s → %s', (status, esperado) => {
    expect(resultado(status)).toBe(esperado);
  });
});

function json(corpo: unknown) {
  return new Response(JSON.stringify(corpo), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function pedido(status: StatusPedido) {
  return {
    codigo: 'abc',
    status,
    turmaId: null,
    turmaNome: null,
    cursoTitulo: null,
    cursoSlug: null,
  };
}

function abrir(caminho: string) {
  const roteador = createMemoryRouter(
    [{ path: '/pagamento/retorno', element: <RetornoPagamentoPagina /> }],
    { initialEntries: [caminho] },
  );
  render(<RouterProvider router={roteador} />);
}

describe('página de retorno', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('consulta a cada 3 s até o pagamento ser confirmado', async () => {
    fetchMock
      .mockResolvedValueOnce(json(pedido('CRIADO')))
      .mockResolvedValueOnce(json(pedido('APROVADO')));

    abrir('/pagamento/retorno?pedido=abc');

    expect(await screen.findByText('Aguardando pagamento')).toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(INTERVALO_MS));
    expect(await screen.findByText('Pagamento confirmado')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0]![0]).toBe('/api/pagamentos/pedidos/abc');
  });

  it('para de consultar depois de 2 minutos', async () => {
    fetchMock.mockImplementation(async () => json(pedido('AGUARDANDO_PAGAMENTO')));

    abrir('/pagamento/retorno?pedido=abc');
    await screen.findByText('Aguardando pagamento');
    await act(() => vi.advanceTimersByTimeAsync(LIMITE_MS + INTERVALO_MS * 2));
    const chamadas = fetchMock.mock.calls.length;
    await act(() => vi.advanceTimersByTimeAsync(INTERVALO_MS * 5));

    expect(fetchMock.mock.calls.length).toBe(chamadas);
    expect(chamadas).toBeLessThanOrEqual(LIMITE_MS / INTERVALO_MS + 2);
  });

  it('pagamento recusado e link sem pedido', async () => {
    fetchMock.mockResolvedValueOnce(json(pedido('CANCELADO')));
    abrir('/pagamento/retorno?pedido=abc');
    expect(await screen.findByText('Pagamento não aprovado')).toBeInTheDocument();
  });

  it('link sem o código do pedido', () => {
    abrir('/pagamento/retorno');
    expect(screen.getByText('O link não tem o código do pedido.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('código do pedido na página de retorno', () => {
  const fetchMock = vi.fn<typeof fetch>();

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it('aceita o xcod como a Hotmart repassa', async () => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockResolvedValueOnce(json(pedido('APROVADO')));
    abrir('/pagamento/retorno?xcod=abc');
    expect(await screen.findByText('Pagamento confirmado')).toBeInTheDocument();
    expect(fetchMock.mock.calls[0]![0]).toBe('/api/pagamentos/pedidos/abc');
  });
});
