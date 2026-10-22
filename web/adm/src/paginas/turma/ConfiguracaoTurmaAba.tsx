import { Alerta, Botao, Card, CampoInteiro, CampoSelect, useConfirmacao } from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi } from '@compartilhado/api/erros.ts';
import { useAcao } from '../../componentes/useAcao.ts';
import { opcoes, STATUS_TURMA, type StatusTurma, type Turma } from '../../tipos.ts';

interface Props {
  turma: Turma;
  aoMudar: (turma: Turma) => void;
  aoExcluir: () => void;
}

export function ConfiguracaoTurmaAba({ turma, aoMudar, aoExcluir }: Props) {
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();
  const [percentual, setPercentual] = useState<number | null>(turma.percentualConclusao);
  const [carga, setCarga] = useState<number | null>(turma.cargaHorariaHoras);
  const [erros, setErros] = useState<{ percentual?: string; carga?: string }>({});
  const rota = `/api/adm/turmas/${turma.id}`;

  const salvarCertificado = async () => {
    const novos = {
      percentual:
        percentual == null || percentual < 1 || percentual > 100
          ? 'Informe um percentual de 1 a 100.'
          : undefined,
      carga: carga != null && (carga < 1 || carga > 10_000) ? 'De 1 a 10.000 horas.' : undefined,
    };
    setErros(novos);
    if (novos.percentual || novos.carga) return;
    const nova = await executar(
      () =>
        api
          .put<Turma>(`${rota}/certificado`, {
            percentualConclusao: percentual,
            cargaHorariaHoras: carga,
          })
          .catch((erro: unknown) => {
            if (erro instanceof ErroApi) {
              setErros({
                percentual: erro.erroDoCampo('percentualConclusao'),
                carga: erro.erroDoCampo('cargaHorariaHoras'),
              });
            }
            throw erro;
          }),
      'Regras do certificado salvas',
    );
    if (nova) aoMudar(nova);
  };

  return (
    <div className="pc-pilha">
      <Card titulo="Status da turma">
        <CampoSelect<StatusTurma>
          rotulo="Status"
          opcoes={opcoes(STATUS_TURMA)}
          valor={turma.status}
          disabled={executando}
          aoMudar={(status) => {
            if (!status || status === turma.status) return;
            void executar(
              () => api.patch<Turma>(`${rota}/status`, { status }),
              'Status da turma alterado',
            ).then((t) => t && aoMudar(t));
          }}
        />
        <p className="pc-dica">
          O catálogo mostra o conteúdo da turma ativa mais recente do curso. Turma encerrada
          continua acessível para quem tem matrícula vigente.
        </p>
      </Card>

      <Card titulo="Certificado">
        <form
          noValidate
          className="pc-grade-detalhes"
          onSubmit={(evento) => {
            evento.preventDefault();
            void salvarCertificado();
          }}
        >
          <CampoInteiro
            rotulo="Conclusão mínima (%)"
            valor={percentual}
            aoMudar={setPercentual}
            erro={erros.percentual}
            obrigatorio
          />
          <CampoInteiro
            rotulo="Carga horária (horas)"
            valor={carga}
            aoMudar={setCarga}
            erro={erros.carga}
            dica="Em branco, o certificado sai sem carga horária."
          />
          <div>
            <Botao type="submit" rotulo="Salvar certificado" icone="save" carregando={executando} />
          </div>
        </form>
      </Card>

      <Card titulo="Excluir turma">
        <Alerta
          severidade="warn"
          mensagem="Só dá para excluir uma turma sem matrículas e sem conteúdo. Para encerrar, mude o status."
        />
        <Botao
          rotulo="Excluir turma"
          icone="trash-2"
          severidade="err"
          aparencia="contorno"
          onClick={async () => {
            if (!(await confirmar.remover({ mensagem: `A turma "${turma.nome}" será excluída.` })))
              return;
            const ok = await executar(() => api.delete(rota).then(() => true), 'Turma excluída');
            if (ok) aoExcluir();
          }}
        />
      </Card>
    </div>
  );
}
