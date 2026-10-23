import {
  Alerta,
  Botao,
  CabecalhoPagina,
  Card,
  Carregando,
  Detalhes,
  Status,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import type { Gateway, ResultadoTeste, TiposGateway } from '../../tipos.ts';
import { FormGateway } from './FormGateway.tsx';

/** Meios de pagamento: configuração, teste das credenciais, ativação e a URL do webhook. */
export function MeiosPagamentoPagina() {
  const toast = useToast();
  const { executar, executando } = useAcao();
  const [dialogo, setDialogo] = useState<{ gateway?: Gateway } | null>(null);
  const [testes, setTestes] = useState<Record<number, ResultadoTeste>>({});
  const tipos = useCarregar(() => api.get<TiposGateway>('/api/adm/pagamentos/tipos'), []);
  const gateways = useCarregar(() => api.get<Gateway[]>('/api/adm/pagamentos/gateways'), []);

  const testar = async (gateway: Gateway) => {
    const resultado = await executar(() =>
      api.post<ResultadoTeste>(`/api/adm/pagamentos/gateways/${gateway.id}/teste`),
    );
    if (resultado) setTestes((atuais) => ({ ...atuais, [gateway.id]: resultado }));
  };

  const alternar = async (gateway: Gateway) => {
    const rota = `/api/adm/pagamentos/gateways/${gateway.id}/ativacao`;
    const ok = await executar(
      () => (gateway.ativo ? api.delete(rota) : api.post(rota)).then(() => true),
      gateway.ativo ? 'Meio de pagamento desativado' : 'Meio de pagamento ativado',
    );
    if (ok) gateways.recarregar();
  };

  const copiar = async (url: string) => {
    try {
      await navigator.clipboard.writeText(url);
      toast.ok('Endereço copiado');
    } catch {
      toast.err('Não foi possível copiar', 'Selecione o endereço e copie à mão.');
    }
  };

  if ((tipos.erro && !tipos.dados) || (gateways.erro && !gateways.dados)) {
    return (
      <div className="pc-pagina">
        <Alerta
          severidade="err"
          titulo="Não foi possível carregar"
          mensagem={tipos.erro ?? gateways.erro}
        />
      </div>
    );
  }
  if (!tipos.dados || !gateways.dados) {
    return <Carregando mensagem="Carregando os meios de pagamento..." />;
  }

  return (
    <div className="pc-pagina">
      <CabecalhoPagina
        titulo="Meios de pagamento"
        icone="credit-card"
        acoes={
          <Botao rotulo="Novo meio de pagamento" icone="plus" onClick={() => setDialogo({})} />
        }
      >
        O meio ativo recebe os novos checkouts. Um meio desativado continua recebendo as
        notificações das compras antigas.
      </CabecalhoPagina>
      {!tipos.dados.cifraDisponivel && (
        <Alerta
          severidade="warn"
          titulo="Credenciais não podem ser gravadas"
          mensagem="Defina a PAGAMENTOS_CHAVE no .env do ambiente (openssl rand -base64 32) e reinicie a API."
        />
      )}
      {gateways.dados.length === 0 && (
        <Alerta
          severidade="info"
          mensagem="Nenhum meio de pagamento configurado. Cadastre a Hotmart para vender os cursos."
        />
      )}
      {gateways.dados.map((gateway) => {
        const teste = testes[gateway.id];
        return (
          <Card
            key={gateway.id}
            titulo={gateway.nome}
            subtitulo={`${gateway.nomeTipo} · alterado em ${dataHora(gateway.atualizadoEm)}`}
            borda={gateway.ativo ? 'ok' : undefined}
            ferramentas={
              <Status
                rotulo={gateway.ativo ? 'Ativo' : 'Inativo'}
                severidade={gateway.ativo ? 'ok' : 'neutral'}
              />
            }
          >
            <div className="pc-pilha">
              {!gateway.segredosLegiveis && (
                <Alerta
                  severidade="err"
                  mensagem="As credenciais gravadas não puderam ser lidas (a PAGAMENTOS_CHAVE mudou ou falta). Informe-as de novo."
                />
              )}
              <div className="pc-grade-detalhes">
                {Object.entries(gateway.valores).map(([chave, valor]) => (
                  <Detalhes
                    key={chave}
                    rotulo={rotuloCampo(tipos.dados!, gateway, chave)}
                    valor={valor}
                  />
                ))}
                {Object.entries(gateway.segredos).map(([chave, segredo]) => (
                  <Detalhes
                    key={chave}
                    rotulo={rotuloCampo(tipos.dados!, gateway, chave)}
                    valor={
                      segredo.configurado
                        ? `configurado ${segredo.final4 ? `••••${segredo.final4}` : ''}`
                        : 'não configurado'
                    }
                  />
                ))}
              </div>
              <Detalhes rotulo="Endereço do webhook">
                <div className="pc-linha">
                  <code className="pc-codigo">{gateway.webhookUrl}</code>
                  <Botao
                    rotulo="Copiar"
                    icone="copy"
                    aparencia="contorno"
                    tamanho="pequeno"
                    onClick={() => void copiar(gateway.webhookUrl)}
                  />
                </div>
              </Detalhes>
              {gateway.tipo === 'HOTMART' && <PassoAPassoHotmart />}
              {teste && (
                <Alerta
                  severidade={teste.ok ? 'ok' : 'err'}
                  titulo={teste.ok ? 'Credenciais conferidas' : 'Credenciais recusadas'}
                  mensagem={teste.mensagem}
                />
              )}
              <div className="pc-linha">
                <Botao
                  rotulo="Editar"
                  icone="pencil"
                  aparencia="contorno"
                  onClick={() => setDialogo({ gateway })}
                />
                <Botao
                  rotulo="Testar credenciais"
                  icone="shield-check"
                  aparencia="contorno"
                  carregando={executando}
                  onClick={() => void testar(gateway)}
                />
                <Botao
                  rotulo={gateway.ativo ? 'Desativar' : 'Ativar'}
                  icone={gateway.ativo ? 'power-off' : 'power'}
                  severidade={gateway.ativo ? 'err' : undefined}
                  aparencia={gateway.ativo ? 'contorno' : undefined}
                  onClick={() => void alternar(gateway)}
                />
              </div>
            </div>
          </Card>
        );
      })}
      {dialogo && (
        <FormGateway
          tipos={tipos.dados}
          gateway={dialogo.gateway}
          aoFechar={() => setDialogo(null)}
          aoSalvar={() => {
            setDialogo(null);
            gateways.recarregar();
          }}
        />
      )}
    </div>
  );
}

function rotuloCampo(tipos: TiposGateway, gateway: Gateway, chave: string): string {
  const tipo = tipos.tipos.find((t) => t.tipo === gateway.tipo);
  return tipo?.campos.find((c) => c.chave === chave)?.rotulo ?? chave;
}

/** Onde cada coisa fica na Hotmart (webhook 2.0.0). */
function PassoAPassoHotmart() {
  return (
    <Card titulo="Como configurar na Hotmart" recolhivel recolhidoInicial>
      <ol className="pc-passos">
        <li>
          Em <strong>Ferramentas → Webhook</strong>, crie uma configuração na{' '}
          <strong>versão 2.0.0</strong> com o endereço acima.
        </li>
        <li>
          Marque os eventos de compra: aprovada, completa, cancelada, reembolsada, chargeback,
          boleto impresso, pedido de reembolso (protesto), atrasada e expirada.
        </li>
        <li>Copie o hottok da configuração para o campo Hottok daqui.</li>
        <li>
          Em cada produto, aponte a página de obrigado para{' '}
          <code>{'<área do aluno>'}/pagamento/retorno</code>.
        </li>
        <li>
          Para a reconciliação, crie as credenciais em <strong>Ferramentas → Credenciais</strong> e
          informe o Client ID, o Client Secret e o Basic.
        </li>
        <li>Use “Enviar teste” na Hotmart e confira em Pagamentos → Eventos recebidos.</li>
      </ol>
    </Card>
  );
}
