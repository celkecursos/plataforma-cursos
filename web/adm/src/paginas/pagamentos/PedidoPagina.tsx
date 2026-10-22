import { Alerta, Card, Carregando, Detalhes, Status, Tabela } from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora, moeda } from '@compartilhado/ui/formato.ts';
import { CAMINHOS } from '../../caminhos.ts';
import {
  FORMAS_PAGAMENTO,
  STATUS_EVENTO,
  STATUS_PEDIDO,
  type EventoPagamento,
  type PedidoDetalhe,
} from '../../tipos.ts';
import { DialogoEvento } from './DialogoEvento.tsx';

/** Um pedido: dados, linha do tempo e as notificações recebidas. */
export function PedidoPagina() {
  const { pedidoId } = useParams();
  const navigate = useNavigate();
  const [evento, setEvento] = useState<number | null>(null);
  const detalhe = useCarregar(
    () => api.get<PedidoDetalhe>(`/api/adm/pagamentos/pedidos/${pedidoId}`),
    [pedidoId],
  );

  if (detalhe.erro && !detalhe.dados) {
    return (
      <div className="pc-pagina">
        <Alerta severidade="err" titulo="Não foi possível abrir o pedido" mensagem={detalhe.erro} />
      </div>
    );
  }
  if (!detalhe.dados) return <Carregando mensagem="Carregando o pedido..." />;
  const { pedido, historico, eventos } = detalhe.dados;

  return (
    <div className="pc-pagina">
      <Card
        titulo={`Pedido ${pedido.codigo}`}
        subtitulo={pedido.email}
        ferramentas={
          <Status
            rotulo={STATUS_PEDIDO[pedido.status].rotulo}
            severidade={STATUS_PEDIDO[pedido.status].severidade}
          />
        }
        rodape={
          <button type="button" className="pc-link" onClick={() => navigate(CAMINHOS.pedidos)}>
            Voltar para os pedidos
          </button>
        }
      >
        <div className="pc-grade-detalhes">
          <Detalhes rotulo="Comprador" valor={pedido.nome ?? pedido.email} />
          <Detalhes rotulo="Oferta" valor={pedido.ofertaTitulo} />
          <Detalhes rotulo="Valor" valor={moeda(pedido.valorCentavos, pedido.moeda ?? 'BRL')} />
          <Detalhes
            rotulo="Forma"
            valor={pedido.formaPagamento ? FORMAS_PAGAMENTO[pedido.formaPagamento] : null}
          />
          <Detalhes rotulo="Transação" valor={pedido.transacaoExterna} />
          <Detalhes
            rotulo="Origem"
            valor={pedido.origem === 'EXTERNO' ? 'Direto no meio de pagamento' : 'Plataforma'}
          />
          <Detalhes
            rotulo="Matrícula"
            valor={pedido.matriculaId ? `#${pedido.matriculaId}` : null}
          />
          <Detalhes rotulo="Criado em" valor={dataHora(pedido.criadoEm)} />
        </div>
      </Card>

      <Card titulo="Linha do tempo">
        <ol className="pc-linha-tempo">
          {historico.map((h) => (
            <li key={h.id}>
              <strong>
                {h.de ? `${STATUS_PEDIDO[h.de].rotulo} → ` : ''}
                {STATUS_PEDIDO[h.para].rotulo}
              </strong>
              <span className="pc-dica">
                {dataHora(h.em)}
                {h.motivo ? ` · ${h.motivo}` : ''}
              </span>
            </li>
          ))}
        </ol>
      </Card>

      <Card titulo="Notificações recebidas" corpoSemEspaco>
        <Tabela<EventoPagamento>
          rotulo="Notificações do pedido"
          colunas={[
            { campo: 'tipoOriginal', rotulo: 'Evento', destaque: true },
            {
              campo: 'status',
              rotulo: 'Situação',
              render: (e) => (
                <Status
                  rotulo={STATUS_EVENTO[e.status].rotulo}
                  severidade={STATUS_EVENTO[e.status].severidade}
                />
              ),
            },
            { campo: 'erro', rotulo: 'Motivo' },
            { campo: 'recebidoEm', rotulo: 'Recebido em', formato: (v) => dataHora(v as string) },
          ]}
          registros={eventos}
          vazioTitulo="Nenhuma notificação"
          vazioDescricao="O meio de pagamento ainda não avisou nada sobre este pedido."
          vazioIcone="inbox"
          aoClicarLinha={(e) => setEvento(e.id)}
        />
      </Card>
      {evento !== null && (
        <DialogoEvento
          eventoId={evento}
          aoFechar={() => setEvento(null)}
          aoReprocessar={() => {
            setEvento(null);
            detalhe.recarregar();
          }}
        />
      )}
    </div>
  );
}
