import { CabecalhoPagina, CampoSelect, Status, Tabela } from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { opcoes, STATUS_EVENTO, type EventoPagamento, type StatusEvento } from '../../tipos.ts';
import { DialogoEvento } from './DialogoEvento.tsx';

const TAMANHO = 20;

/** Notificações recebidas dos meios de pagamento; as com erro ou ignoradas podem ser reprocessadas. */
export function EventosPagina() {
  const [pagina, setPagina] = useState(1);
  const [status, setStatus] = useState<StatusEvento | null>(null);
  const [aberto, setAberto] = useState<number | null>(null);

  const eventos = useCarregar(() => {
    const filtro = new URLSearchParams({ page: String(pagina - 1), size: String(TAMANHO) });
    if (status) filtro.set('status', status);
    return api.get<Pagina<EventoPagamento>>(`/api/adm/pagamentos/eventos?${filtro}`);
  }, [pagina, status]);

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Eventos recebidos" icone="inbox">
        Cada notificação do meio de pagamento fica registrada. Evento ignorado (oferta ainda não
        cadastrada, por exemplo) pode ser reprocessado depois de corrigir.
      </CabecalhoPagina>
      <div className="pc-filtros">
        <CampoSelect
          rotulo="Situação"
          variante="inline"
          placeholder="Todas"
          botaoLimpar
          opcoes={opcoes(STATUS_EVENTO)}
          valor={status}
          aoMudar={(valor) => {
            setStatus(valor);
            setPagina(1);
          }}
        />
      </div>
      <Tabela<EventoPagamento>
        rotulo="Eventos recebidos"
        colunas={[
          { campo: 'tipoOriginal', rotulo: 'Evento', destaque: true },
          { campo: 'transacao', rotulo: 'Transação' },
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
        registros={eventos.dados?.itens ?? []}
        carregando={eventos.carregando && !eventos.dados}
        atualizando={eventos.carregando && !!eventos.dados}
        erro={eventos.erro}
        aoRecarregar={eventos.recarregar}
        vazioTitulo="Nenhum evento"
        vazioDescricao="Use “Enviar teste” na Hotmart para conferir a configuração."
        vazioIcone="inbox"
        aoClicarLinha={(e) => setAberto(e.id)}
        paginacao={{
          total: eventos.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
      />
      {aberto !== null && (
        <DialogoEvento
          eventoId={aberto}
          aoFechar={() => setAberto(null)}
          aoReprocessar={() => {
            setAberto(null);
            eventos.recarregar();
          }}
        />
      )}
    </div>
  );
}
