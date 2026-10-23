import {
  Alerta,
  Badge,
  Botao,
  CabecalhoPagina,
  Card,
  CampoTextarea,
  Carregando,
} from '@celke/kole-react';
import { useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { CampoImagens, type CampoImagensRef } from '@compartilhado/ui/CampoImagens.tsx';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { GaleriaImagens } from '@compartilhado/ui/GaleriaImagens.tsx';
import { LIMITES_FORUM } from '@compartilhado/ui/imagens.ts';
import { caminhoAula, caminhoTurma } from '../../caminhos.ts';
import type { Topico } from '../../tipos.ts';
import { useImagensMensagem } from './useImagensMensagem.ts';

/** Uma pergunta do fórum com as respostas; o aluno também pode responder. */
export function TopicoPagina() {
  const { turmaId, topicoId } = useParams();
  const navigate = useNavigate();
  const [resposta, setResposta] = useState('');
  const [erro, setErro] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const imagens = useImagensMensagem();
  const campoImagens = useRef<CampoImagensRef>(null);
  const topico = useCarregar(
    () => api.get<Topico>(`/api/aluno/forum/topicos/${topicoId}`),
    [topicoId],
  );

  const responder = async () => {
    if (!resposta.trim()) {
      setErro('Escreva a resposta.');
      return;
    }
    setErro(undefined);
    setEnviando(true);
    try {
      const anexos = await imagens.enviar();
      topico.definir(
        await api.post<Topico>(`/api/aluno/forum/topicos/${topicoId}/respostas`, {
          corpo: resposta.trim(),
          anexos,
        }),
      );
      setResposta('');
      imagens.limpar();
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo('corpo')) || mensagemDeErro(falha));
    } finally {
      setEnviando(false);
    }
  };

  if (topico.erro && !topico.dados) {
    return (
      <div className="pc-pagina">
        <Alerta
          severidade="err"
          titulo="Não foi possível abrir a pergunta"
          mensagem={topico.erro}
        />
      </div>
    );
  }
  if (!topico.dados) return <Carregando mensagem="Carregando a pergunta..." />;
  const t = topico.dados;

  return (
    <div className="pc-pagina">
      <CabecalhoPagina
        titulo={t.titulo}
        icone="message-square"
        acoes={
          <>
            {t.aulaId && (
              <Botao
                rotulo="Ir para a aula"
                icone="circle-play"
                aparencia="contorno"
                onClick={() => navigate(caminhoAula(turmaId!, t.aulaId!))}
              />
            )}
            <Botao
              rotulo="Voltar ao fórum"
              icone="chevron-left"
              aparencia="texto"
              onClick={() => navigate(`${caminhoTurma(turmaId!)}?aba=forum`)}
            />
          </>
        }
      >
        {t.autorNome} · {dataHora(t.criadoEm)}
      </CabecalhoPagina>
      <Card>
        {/* HTML já filtrado pela API (Markdown → HTML seguro) */}
        <div className="pc-texto-longo" dangerouslySetInnerHTML={{ __html: t.corpoHtml }} />
        <GaleriaImagens imagens={t.anexos} de={t.autorNome} />
      </Card>

      <h2 className="pc-subtitulo">
        {t.respostas.length === 0 ? 'Nenhuma resposta ainda' : `Respostas (${t.respostas.length})`}
      </h2>
      {t.respostas.map((r) => (
        <article key={r.id} className="pc-resposta" aria-label={`Resposta de ${r.autorNome}`}>
          <header className="pc-linha">
            <strong>{r.autorNome}</strong>
            {r.instrutor && <Badge cor="pri" rotulo="Instrutor" aparencia="suave" />}
            <span className="pc-dica">{dataHora(r.criadoEm)}</span>
          </header>
          <div dangerouslySetInnerHTML={{ __html: r.corpoHtml }} />
          <GaleriaImagens imagens={r.anexos} de={r.autorNome} />
        </article>
      ))}

      <Card titulo="Responder">
        <form
          noValidate
          className="pc-pilha"
          onSubmit={(evento) => {
            evento.preventDefault();
            void responder();
          }}
        >
          <CampoTextarea
            rotulo="Sua resposta"
            valor={resposta}
            aoMudar={setResposta}
            erro={erro}
            rows={4}
            maxLength={10_000}
            onPaste={(evento) => campoImagens.current?.colar(evento)}
          />
          <CampoImagens
            ref={campoImagens}
            rotulo="Imagens da resposta"
            valor={imagens.imagens}
            aoMudar={imagens.setImagens}
            limites={LIMITES_FORUM}
            progresso={imagens.progresso}
            desabilitado={enviando}
          />
          <div>
            <Botao type="submit" rotulo="Publicar resposta" icone="send" carregando={enviando} />
          </div>
        </form>
      </Card>
    </div>
  );
}
