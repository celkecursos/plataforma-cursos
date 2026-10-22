import {
  Alerta,
  Botao,
  CabecalhoPagina,
  Card,
  CampoMarcacao,
  CampoTextarea,
  CampoTexto,
  Carregando,
  Status,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { erroObrigatorio } from '@compartilhado/ui/validacao.ts';
import { caminhoTurma } from '../../caminhos.ts';
import { ArquivosApoio } from '../../componentes/ArquivosApoio.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { STATUS_CONTEUDO, type Aula } from '../../tipos.ts';
import { VideoAula } from './VideoAula.tsx';

/** Uma aula: dados, vídeo e arquivos de apoio. A API não tem GET da aula: vem da lista do módulo. */
export function AulaPagina() {
  const { cursoId, turmaId, moduloId, aulaId } = useParams();
  const navigate = useNavigate();
  const aula = useCarregar(async () => {
    const aulas = await api.get<Aula[]>(`/api/adm/modulos/${moduloId}/aulas`);
    const encontrada = aulas.find((a) => String(a.id) === aulaId);
    if (!encontrada) throw new ErroApi(404, { detail: 'Aula não encontrada neste módulo.' });
    return encontrada;
  }, [moduloId, aulaId]);
  const voltar = () => navigate(`${caminhoTurma(cursoId!, turmaId!)}?aba=conteudo`);

  if (aula.erro && !aula.dados) {
    return (
      <div className="pc-pagina">
        <Alerta severidade="err" titulo="Não foi possível abrir a aula" mensagem={aula.erro} />
      </div>
    );
  }
  if (!aula.dados) return <Carregando mensagem="Carregando a aula..." />;
  const a = aula.dados;

  return (
    <div className="pc-pagina">
      <CabecalhoPagina
        titulo={a.titulo}
        icone="circle-play"
        acoes={
          <>
            <Status
              rotulo={STATUS_CONTEUDO[a.status].rotulo}
              severidade={STATUS_CONTEUDO[a.status].severidade}
            />
            <Botao
              rotulo="Voltar à turma"
              icone="chevron-left"
              aparencia="texto"
              onClick={voltar}
            />
          </>
        }
      />
      <div className="pc-pilha">
        <DadosAula key={a.id} aula={a} aoMudar={aula.definir} aoExcluir={voltar} />
        <VideoAula aulaId={a.id} />
        <Card titulo="Arquivos de apoio">
          <ArquivosApoio dono="aulas" donoId={a.id} titulo="Arquivos de apoio da aula" />
        </Card>
      </div>
    </div>
  );
}

function DadosAula({
  aula,
  aoMudar,
  aoExcluir,
}: {
  aula: Aula;
  aoMudar: (a: Aula) => void;
  aoExcluir: () => void;
}) {
  const toast = useToast();
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();
  const [titulo, setTitulo] = useState(aula.titulo);
  const [descricao, setDescricao] = useState(aula.descricao ?? '');
  const [gratuita, setGratuita] = useState(aula.gratuita);
  const [erros, setErros] = useState<{ titulo?: string; descricao?: string }>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);
  const rota = `/api/adm/aulas/${aula.id}`;

  const salvar = async () => {
    const problema = erroObrigatorio(titulo, 'Informe o título.', 150);
    setErros({ titulo: problema });
    setErroGeral(undefined);
    if (problema) return;
    setSalvando(true);
    try {
      aoMudar(
        await api.put<Aula>(rota, {
          titulo: titulo.trim(),
          descricao: descricao.trim() || null,
          gratuita,
        }),
      );
      toast.ok('Aula salva');
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({ titulo: erro.erroDoCampo('titulo'), descricao: erro.erroDoCampo('descricao') });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Card
      titulo="Dados da aula"
      ferramentas={
        <div className="pc-linha">
          <Botao
            rotulo={aula.status === 'ATIVO' ? 'Inativar aula' : 'Ativar aula'}
            icone={aula.status === 'ATIVO' ? 'eye-off' : 'eye'}
            aparencia="contorno"
            tamanho="pequeno"
            disabled={executando}
            onClick={async () => {
              const nova = await executar(
                () =>
                  api.patch<Aula>(`${rota}/status`, {
                    status: aula.status === 'ATIVO' ? 'INATIVO' : 'ATIVO',
                  }),
                aula.status === 'ATIVO' ? 'Aula inativada' : 'Aula ativada',
              );
              if (nova) aoMudar(nova);
            }}
          />
          <Botao
            rotulo="Excluir aula"
            icone="trash-2"
            aparencia="texto"
            severidade="err"
            tamanho="pequeno"
            disabled={executando}
            onClick={async () => {
              if (
                !(await confirmar.remover({ mensagem: `A aula "${aula.titulo}" será excluída.` }))
              )
                return;
              const ok = await executar(() => api.delete(rota).then(() => true), 'Aula excluída');
              if (ok) aoExcluir();
            }}
          />
        </div>
      }
    >
      <form
        noValidate
        className="pc-pilha"
        onSubmit={(evento) => {
          evento.preventDefault();
          void salvar();
        }}
      >
        {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
        <CampoTexto
          rotulo="Título"
          valor={titulo}
          aoMudar={setTitulo}
          erro={erros.titulo}
          obrigatorio
          maxLength={150}
        />
        <CampoTextarea
          rotulo="Descrição"
          valor={descricao}
          aoMudar={setDescricao}
          erro={erros.descricao}
          rows={5}
          maxLength={20_000}
        />
        <CampoMarcacao
          rotulo="Aula gratuita (aparece aberta no catálogo, sem matrícula)"
          valor={gratuita}
          aoMudar={setGratuita}
        />
        <div>
          <Botao type="submit" rotulo="Salvar aula" icone="save" carregando={salvando} />
        </div>
      </form>
    </Card>
  );
}
