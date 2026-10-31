import {
  Alerta,
  Badge,
  Botao,
  CampoTextarea,
  CampoTexto,
  Card,
  Carregando,
  ItemLista,
  Lista,
  Paginacao,
  Status,
  Vazio,
} from '@celke/kole-react';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { CampoImagens, type CampoImagensRef } from '@compartilhado/ui/CampoImagens.tsx';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { LIMITES_FORUM } from '@compartilhado/ui/imagens.ts';
import { erroObrigatorio, semErros } from '@compartilhado/ui/validacao.ts';
import { caminhoTopico } from '../../caminhos.ts';
import type { Topico, TopicoResumo } from '../../tipos.ts';
import { useImagensMensagem } from './useImagensMensagem.ts';

const TAMANHO = 10;

interface Props {
  turmaId: number;
  /** Na página da aula: só os tópicos dela, e a pergunta nova fica ligada a ela. */
  aulaId?: number;
}

/** Tópicos do fórum (fixados primeiro) e o formulário para perguntar. */
export function ForumLista({ turmaId, aulaId }: Props) {
  const navigate = useNavigate();
  const [pagina, setPagina] = useState(1);
  const [perguntando, setPerguntando] = useState(false);
  const topicos = useCarregar(() => {
    const filtro = new URLSearchParams({ page: String(pagina - 1), size: String(TAMANHO) });
    if (aulaId) filtro.set('aulaId', String(aulaId));
    return api.get<Pagina<TopicoResumo>>(`/api/aluno/turmas/${turmaId}/forum?${filtro}`);
  }, [turmaId, aulaId, pagina]);

  return (
    <div className="pc-pilha">
      <div className="pc-linha pc-linha-fim">
        {!perguntando && (
          <Botao
            rotulo="Fazer uma pergunta"
            icone="message-square"
            onClick={() => setPerguntando(true)}
          />
        )}
      </div>
      {perguntando && (
        <NovaPergunta
          turmaId={turmaId}
          aulaId={aulaId}
          aoCancelar={() => setPerguntando(false)}
          aoPublicar={(topico) => navigate(caminhoTopico(turmaId, topico.id))}
        />
      )}
      {topicos.erro && <Alerta severidade="err" mensagem={topicos.erro} />}
      {!topicos.dados && !topicos.erro && <Carregando mensagem="Carregando o fórum..." />}
      {topicos.dados?.itens.length === 0 && (
        <Vazio
          titulo="Nenhuma pergunta ainda"
          descricao={
            aulaId
              ? 'Tem dúvida sobre esta aula? Pergunte ao instrutor.'
              : 'Tem dúvida? Pergunte ao instrutor.'
          }
          icone="message-square"
        />
      )}
      {!!topicos.dados?.itens.length && (
        <>
          <Lista rotulo="Perguntas do fórum">
            {topicos.dados.itens.map((t) => (
              <ItemLista
                key={t.id}
                titulo={t.titulo}
                icone={t.fixado ? 'pin' : 'message-square'}
                onClick={() => navigate(caminhoTopico(turmaId, t.id))}
                fim={
                  <span className="pc-linha">
                    {t.respondidoInstrutor && <Status rotulo="Respondida" severidade="ok" />}
                    <Badge rotulo={`${t.respostas} resp.`} aparencia="suave" />
                  </span>
                }
              >
                {t.autorNome} · {dataHora(t.criadoEm)}
              </ItemLista>
            ))}
          </Lista>
          {topicos.dados.totalPaginas > 1 && (
            <Paginacao
              total={topicos.dados.total}
              pagina={pagina}
              tamanho={TAMANHO}
              aoMudarPagina={setPagina}
            />
          )}
        </>
      )}
    </div>
  );
}

function NovaPergunta({
  turmaId,
  aulaId,
  aoCancelar,
  aoPublicar,
}: Props & { aoCancelar: () => void; aoPublicar: (t: Topico) => void }) {
  const [titulo, setTitulo] = useState('');
  const [corpo, setCorpo] = useState('');
  const [erros, setErros] = useState<{ titulo?: string; corpo?: string }>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const imagens = useImagensMensagem();
  const campoImagens = useRef<CampoImagensRef>(null);

  const publicar = async () => {
    const novos = {
      titulo: erroObrigatorio(titulo, 'Dê um título à pergunta.', 150),
      corpo: erroObrigatorio(corpo, 'Descreva a sua dúvida.', 10_000),
    };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setEnviando(true);
    try {
      const anexos = await imagens.enviar();
      aoPublicar(
        await api.post<Topico>(`/api/aluno/turmas/${turmaId}/forum`, {
          titulo: titulo.trim(),
          corpo: corpo.trim(),
          aulaId: aulaId ?? null,
          anexos,
        }),
      );
      imagens.limpar();
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({ titulo: erro.erroDoCampo('titulo'), corpo: erro.erroDoCampo('corpo') });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <Card titulo={aulaId ? 'Pergunta sobre esta aula' : 'Nova pergunta'}>
      <form
        noValidate
        className="pc-pilha"
        onSubmit={(evento) => {
          evento.preventDefault();
          void publicar();
        }}
      >
        {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
        <CampoTexto
          rotulo="Título da pergunta"
          valor={titulo}
          aoMudar={setTitulo}
          erro={erros.titulo}
          obrigatorio
          maxLength={150}
        />
        <CampoTextarea
          rotulo="Sua dúvida"
          valor={corpo}
          aoMudar={setCorpo}
          erro={erros.corpo}
          obrigatorio
          rows={6}
          maxLength={10_000}
          dica="Aceita Markdown (**negrito**, listas com -, `código`)."
          onPaste={(evento) => campoImagens.current?.colar(evento)}
        />
        <CampoImagens
          ref={campoImagens}
          rotulo="Imagens da pergunta"
          valor={imagens.imagens}
          aoMudar={imagens.setImagens}
          limites={LIMITES_FORUM}
          progresso={imagens.progresso}
          desabilitado={enviando}
        />
        <div className="pc-linha">
          <Botao type="submit" rotulo="Publicar pergunta" icone="send" carregando={enviando} />
          <Botao rotulo="Cancelar" aparencia="texto" onClick={aoCancelar} />
        </div>
      </form>
    </Card>
  );
}
