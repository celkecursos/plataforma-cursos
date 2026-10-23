import { Alerta, Botao, Carregando, Detalhes, Dialogo, Status } from '@celke/kole-react';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { STATUS_EVENTO, type EventoDetalhe } from '../../tipos.ts';

interface Props {
  eventoId: number;
  aoFechar: () => void;
  aoReprocessar: () => void;
}

/** Uma notificação recebida: dados, motivo, corpo (formatado) e o reprocessamento. */
export function DialogoEvento({ eventoId, aoFechar, aoReprocessar }: Props) {
  const { executar, executando } = useAcao();
  const detalhe = useCarregar(
    () => api.get<EventoDetalhe>(`/api/adm/pagamentos/eventos/${eventoId}`),
    [eventoId],
  );
  const evento = detalhe.dados?.evento;
  const podeReprocessar = evento && (evento.status === 'ERRO' || evento.status === 'IGNORADO');

  return (
    <Dialogo
      aberto
      titulo={evento ? evento.tipoOriginal : 'Notificação'}
      subtitulo={evento?.idExterno}
      tamanho="grande"
      aoFechar={aoFechar}
      rodape={
        podeReprocessar && (
          <Botao
            rotulo="Reprocessar"
            icone="rotate-ccw"
            carregando={executando}
            onClick={async () => {
              const ok = await executar(
                () =>
                  api
                    .post(`/api/adm/pagamentos/eventos/${eventoId}/reprocessamento`)
                    .then(() => true),
                'Evento na fila para reprocessar',
              );
              if (ok) aoReprocessar();
            }}
          />
        )
      }
    >
      {detalhe.erro && <Alerta severidade="err" mensagem={detalhe.erro} />}
      {!detalhe.dados && !detalhe.erro && <Carregando mensagem="Carregando a notificação..." />}
      {evento && (
        <div className="pc-pilha">
          <div className="pc-grade-detalhes">
            <Detalhes rotulo="Situação">
              <Status
                rotulo={STATUS_EVENTO[evento.status].rotulo}
                severidade={STATUS_EVENTO[evento.status].severidade}
              />
            </Detalhes>
            <Detalhes rotulo="Transação" valor={evento.transacao} />
            <Detalhes rotulo="Ocorrido em" valor={dataHora(evento.ocorridoEm)} />
            <Detalhes rotulo="Recebido em" valor={dataHora(evento.recebidoEm)} />
          </div>
          {evento.erro && (
            <Alerta severidade={evento.status === 'ERRO' ? 'err' : 'warn'} mensagem={evento.erro} />
          )}
          <Detalhes rotulo="Corpo recebido (documento e telefone mascarados)">
            {detalhe.dados?.payload ? (
              <pre className="pc-json">{formatarJson(detalhe.dados.payload)}</pre>
            ) : (
              <p className="pc-dica">O corpo já foi apagado pela retenção.</p>
            )}
          </Detalhes>
        </div>
      )}
    </Dialogo>
  );
}

/** JSON indentado; texto que não é JSON fica como veio. */
export function formatarJson(texto: string): string {
  try {
    const valor: unknown = JSON.parse(texto);
    return typeof valor === 'string' ? valor : JSON.stringify(valor, null, 2);
  } catch {
    return texto;
  }
}
