import { CabecalhoPagina, CampoSelect, CampoTexto, Status, Tabela } from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora, moeda } from '@compartilhado/ui/formato.ts';
import { caminhoPedido } from '../../caminhos.ts';
import { opcoes, STATUS_PEDIDO, type Pedido, type StatusPedido } from '../../tipos.ts';

const TAMANHO = 20;

/** Pedidos de todos os meios de pagamento, com filtro por status e por e-mail. */
export function PedidosPagina() {
  const navigate = useNavigate();
  const [pagina, setPagina] = useState(1);
  const [status, setStatus] = useState<StatusPedido | null>(null);
  const [email, setEmail] = useState('');
  const filtroEmail = email.trim();

  const pedidos = useCarregar(() => {
    const filtro = new URLSearchParams({ page: String(pagina - 1), size: String(TAMANHO) });
    if (status) filtro.set('status', status);
    if (filtroEmail) filtro.set('email', filtroEmail);
    return api.get<Pagina<Pedido>>(`/api/adm/pagamentos/pedidos?${filtro}`);
  }, [pagina, status, filtroEmail]);

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Pedidos" icone="receipt">
        Compras feitas pela plataforma e vendas que chegaram direto do meio de pagamento.
      </CabecalhoPagina>
      <div className="pc-filtros">
        <CampoSelect
          rotulo="Status"
          variante="inline"
          placeholder="Todos"
          botaoLimpar
          opcoes={opcoes(STATUS_PEDIDO)}
          valor={status}
          aoMudar={(valor) => {
            setStatus(valor);
            setPagina(1);
          }}
        />
        <CampoTexto
          rotulo="E-mail"
          valor={email}
          aoMudar={(valor) => {
            setEmail(valor);
            setPagina(1);
          }}
          placeholder="Parte do e-mail"
        />
      </div>
      <Tabela<Pedido>
        rotulo="Pedidos"
        colunas={[
          { campo: 'email', rotulo: 'Comprador', destaque: true },
          { campo: 'ofertaTitulo', rotulo: 'Oferta' },
          {
            campo: 'valorCentavos',
            rotulo: 'Valor',
            formato: (v, p) => moeda(v as number | null, p.moeda ?? 'BRL'),
          },
          {
            campo: 'status',
            rotulo: 'Status',
            render: (p) => (
              <Status
                rotulo={STATUS_PEDIDO[p.status].rotulo}
                severidade={STATUS_PEDIDO[p.status].severidade}
              />
            ),
          },
          {
            campo: 'origem',
            rotulo: 'Origem',
            formato: (v) => (v === 'EXTERNO' ? 'Direto no meio de pagamento' : 'Plataforma'),
          },
          { campo: 'criadoEm', rotulo: 'Criado em', formato: (v) => dataHora(v as string) },
        ]}
        registros={pedidos.dados?.itens ?? []}
        carregando={pedidos.carregando && !pedidos.dados}
        atualizando={pedidos.carregando && !!pedidos.dados}
        erro={pedidos.erro}
        aoRecarregar={pedidos.recarregar}
        vazioTitulo="Nenhum pedido"
        vazioDescricao="Os pedidos aparecem aqui assim que alguém clica em Comprar."
        vazioIcone="receipt"
        aoClicarLinha={(p) => navigate(caminhoPedido(p.id))}
        paginacao={{
          total: pedidos.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
      />
    </div>
  );
}
