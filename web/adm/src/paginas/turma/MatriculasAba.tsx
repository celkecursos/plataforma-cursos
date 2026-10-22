import {
  AcaoLinha,
  Alerta,
  Botao,
  CampoData,
  CampoEmail,
  CampoSelect,
  CampoTexto,
  Form,
  FormSecao,
  Status,
  Tabela,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { data, diaLocal, fimDoDia, inicioDoDia } from '@compartilhado/ui/formato.ts';
import { erroEmail } from '@compartilhado/ui/validacao.ts';
import { useAcao } from '../../componentes/useAcao.ts';
import {
  FORMAS_PAGAMENTO,
  opcoes,
  SITUACAO_MATRICULA,
  type FormaPagamento,
  type Matricula,
  type Turma,
} from '../../tipos.ts';

const TAMANHO = 20;

export function MatriculasAba({ turma }: { turma: Turma }) {
  const confirmar = useConfirmacao();
  const { executar } = useAcao();
  const [pagina, setPagina] = useState(1);
  const [dialogo, setDialogo] = useState<{ matricula?: Matricula } | null>(null);
  const matriculas = useCarregar(
    () =>
      api.get<Pagina<Matricula>>(
        `/api/adm/turmas/${turma.id}/matriculas?page=${pagina - 1}&size=${TAMANHO}`,
      ),
    [turma.id, pagina],
  );

  return (
    <div className="pc-pilha">
      {turma.status === 'ENCERRADA' && (
        <Alerta
          severidade="warn"
          mensagem="A turma está encerrada e não recebe novas matrículas."
        />
      )}
      <div className="pc-linha pc-linha-fim">
        <Botao
          rotulo="Matricular aluno"
          icone="user-plus"
          disabled={turma.status === 'ENCERRADA'}
          onClick={() => setDialogo({})}
        />
      </div>
      <Tabela<Matricula>
        rotulo="Matrículas"
        colunas={[
          { campo: 'nome', rotulo: 'Aluno', destaque: true },
          { campo: 'email', rotulo: 'E-mail' },
          { campo: 'inicio', rotulo: 'Início', formato: (v) => data(v as string) },
          { campo: 'fim', rotulo: 'Fim', formato: (v) => (v ? data(v as string) : 'Sem fim') },
          {
            campo: 'situacao',
            rotulo: 'Situação',
            render: (m) => (
              <Status
                rotulo={SITUACAO_MATRICULA[m.situacao].rotulo}
                severidade={SITUACAO_MATRICULA[m.situacao].severidade}
              />
            ),
          },
          {
            campo: 'formaPagamento',
            rotulo: 'Forma',
            formato: (v) => FORMAS_PAGAMENTO[v as FormaPagamento],
          },
        ]}
        registros={matriculas.dados?.itens ?? []}
        carregando={matriculas.carregando && !matriculas.dados}
        atualizando={matriculas.carregando && !!matriculas.dados}
        erro={matriculas.erro}
        aoRecarregar={matriculas.recarregar}
        vazioTitulo="Nenhuma matrícula"
        vazioDescricao="Matricule um aluno pelo e-mail da conta dele."
        vazioIcone="users"
        paginacao={{
          total: matriculas.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
        acoes={(m) =>
          m.status === 'ATIVA' && (
            <>
              <AcaoLinha
                icone="calendar"
                rotulo={`Alterar período de ${m.nome}`}
                onClick={() => setDialogo({ matricula: m })}
              />
              <AcaoLinha
                icone="x"
                severidade="err"
                rotulo={`Cancelar matrícula de ${m.nome}`}
                onClick={async () => {
                  const sim = await confirmar({
                    titulo: 'Cancelar matrícula?',
                    mensagem: `${m.nome} perde o acesso à turma na hora.`,
                    severidade: 'err',
                    rotuloAceitar: 'Cancelar matrícula',
                    rotuloRecusar: 'Voltar',
                  });
                  if (!sim) return;
                  const ok = await executar(
                    () => api.post(`/api/adm/matriculas/${m.id}/cancelamento`).then(() => true),
                    'Matrícula cancelada',
                  );
                  if (ok) matriculas.recarregar();
                }}
              />
            </>
          )
        }
      />
      {dialogo && (
        <FormMatricula
          turma={turma}
          matricula={dialogo.matricula}
          aoFechar={() => setDialogo(null)}
          aoSalvar={() => {
            setDialogo(null);
            matriculas.recarregar();
          }}
        />
      )}
    </div>
  );
}

interface PropsForm {
  turma: Turma;
  /** Com matrícula, altera só o período. */
  matricula?: Matricula;
  aoFechar: () => void;
  aoSalvar: () => void;
}

type Erros = Partial<Record<'email' | 'inicio' | 'fim' | 'formaPagamento' | 'transacao', string>>;

function FormMatricula({ turma, matricula, aoFechar, aoSalvar }: PropsForm) {
  const toast = useToast();
  const [email, setEmail] = useState('');
  const [inicio, setInicio] = useState(diaLocal(matricula?.inicio));
  const [fim, setFim] = useState(diaLocal(matricula?.fim));
  const [forma, setForma] = useState<FormaPagamento | null>('MANUAL');
  const [transacao, setTransacao] = useState('');
  const [erros, setErros] = useState<Erros>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const novos: Erros = {
      email: matricula ? undefined : erroEmail(email),
      inicio: matricula && !inicio ? 'Informe o início.' : undefined,
      fim: inicio && fim && fim < inicio ? 'O fim deve ser depois do início.' : undefined,
      formaPagamento: !matricula && !forma ? 'Escolha a forma.' : undefined,
    };
    setErros(novos);
    setErroGeral(undefined);
    if (Object.values(novos).some(Boolean)) return;
    setSalvando(true);
    try {
      const periodo = {
        inicio: inicio ? inicioDoDia(inicio) : null,
        fim: fim ? fimDoDia(fim) : null,
      };
      if (matricula) {
        await api.put(`/api/adm/matriculas/${matricula.id}/periodo`, periodo);
        toast.ok('Período alterado', matricula.nome);
      } else {
        const nova = await api.post<Matricula>(`/api/adm/turmas/${turma.id}/matriculas`, {
          email: email.trim(),
          ...periodo,
          formaPagamento: forma,
          transacao: transacao.trim() || null,
        });
        toast.ok('Aluno matriculado', nova.nome);
      }
      aoSalvar();
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({
          email: erro.erroDoCampo('email'),
          inicio: erro.erroDoCampo('inicio'),
          fim: erro.erroDoCampo('fim'),
          transacao: erro.erroDoCampo('transacao'),
        });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Form
      arranjo="modal"
      aberto
      titulo={matricula ? `Período de ${matricula.nome}` : 'Matricular aluno'}
      rotuloSalvar={matricula ? 'Salvar período' : 'Matricular'}
      aoSalvar={() => void salvar()}
      aoCancelar={aoFechar}
      salvando={salvando}
      tamanho="medio"
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <FormSecao legenda={matricula ? 'Período' : 'Aluno e período'} colunas={2}>
        {!matricula && (
          <CampoEmail
            rotulo="E-mail do aluno"
            valor={email}
            aoMudar={setEmail}
            erro={erros.email}
            obrigatorio
            dica="O aluno precisa ter conta na plataforma."
            className="lp-col-full"
          />
        )}
        <CampoData
          rotulo="Início"
          valor={inicio}
          aoMudar={setInicio}
          erro={erros.inicio}
          obrigatorio={!!matricula}
          dica={matricula ? undefined : 'Em branco, começa agora.'}
        />
        <CampoData
          rotulo="Fim"
          valor={fim}
          aoMudar={setFim}
          erro={erros.fim}
          dica="Em branco, sem prazo."
        />
        {!matricula && (
          <>
            <CampoSelect<FormaPagamento>
              rotulo="Forma"
              opcoes={opcoes(FORMAS_PAGAMENTO)}
              valor={forma}
              aoMudar={setForma}
              erro={erros.formaPagamento}
              obrigatorio
            />
            <CampoTexto
              rotulo="Transação"
              valor={transacao}
              aoMudar={setTransacao}
              erro={erros.transacao}
              maxLength={100}
              dica="Código do pagamento, se houver."
            />
          </>
        )}
      </FormSecao>
    </Form>
  );
}
