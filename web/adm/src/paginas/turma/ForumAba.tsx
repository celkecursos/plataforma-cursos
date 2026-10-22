import {
  Alerta,
  Badge,
  Botao,
  CampoTextarea,
  Carregando,
  Dialogo,
  Status,
  Tabela,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { useAcao } from '../../componentes/useAcao.ts';
import type { Topico, TopicoResumo, Turma } from '../../tipos.ts';

const TAMANHO = 20;

/** Moderação do fórum da turma: o admin vê também o que está oculto. */
export function ForumAba({ turma }: { turma: Turma }) {
  const [pagina, setPagina] = useState(1);
  const [aberto, setAberto] = useState<number | null>(null);
  const topicos = useCarregar(
    () =>
      api.get<Pagina<TopicoResumo>>(
        `/api/adm/turmas/${turma.id}/forum?page=${pagina - 1}&size=${TAMANHO}`,
      ),
    [turma.id, pagina],
  );

  return (
    <div className="pc-pilha">
      <Tabela<TopicoResumo>
        rotulo="Tópicos do fórum"
        colunas={[
          {
            campo: 'titulo',
            rotulo: 'Tópico',
            destaque: true,
            render: (t) => (
              <span className="pc-linha">
                {t.fixado && <Badge cor="pri" icone="pin" rotulo="Fixado" aparencia="suave" />}
                {t.titulo}
              </span>
            ),
          },
          { campo: 'autorNome', rotulo: 'Autor' },
          { campo: 'respostas', rotulo: 'Respostas', alinhamento: 'direita' },
          {
            campo: 'respondidoInstrutor',
            rotulo: 'Situação',
            render: (t) =>
              t.oculto ? (
                <Status rotulo="Oculto" severidade="neutral" />
              ) : t.respondidoInstrutor ? (
                <Status rotulo="Respondido" severidade="ok" />
              ) : (
                <Status rotulo="Aguardando resposta" severidade="warn" />
              ),
          },
          { campo: 'criadoEm', rotulo: 'Criado em', formato: (v) => dataHora(v as string) },
        ]}
        registros={topicos.dados?.itens ?? []}
        carregando={topicos.carregando && !topicos.dados}
        atualizando={topicos.carregando && !!topicos.dados}
        erro={topicos.erro}
        aoRecarregar={topicos.recarregar}
        vazioTitulo="Nenhum tópico"
        vazioDescricao="As dúvidas dos alunos aparecem aqui."
        vazioIcone="message-square"
        aoClicarLinha={(t) => setAberto(t.id)}
        paginacao={{
          total: topicos.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
      />
      {aberto != null && (
        <PainelTopico
          topicoId={aberto}
          aoFechar={() => setAberto(null)}
          aoMudar={topicos.recarregar}
        />
      )}
    </div>
  );
}

function PainelTopico({
  topicoId,
  aoFechar,
  aoMudar,
}: {
  topicoId: number;
  aoFechar: () => void;
  aoMudar: () => void;
}) {
  const toast = useToast();
  const { executar, executando } = useAcao();
  const [resposta, setResposta] = useState('');
  const [erro, setErro] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const topico = useCarregar(
    () => api.get<Topico>(`/api/adm/forum/topicos/${topicoId}`),
    [topicoId],
  );

  const alterar = async (acao: () => Promise<Topico>, sucesso: string) => {
    const novo = await executar(acao, sucesso);
    if (novo) {
      topico.definir(novo);
      aoMudar();
    }
  };

  const responder = async () => {
    if (!resposta.trim()) {
      setErro('Escreva a resposta.');
      return;
    }
    setEnviando(true);
    setErro(undefined);
    try {
      topico.definir(
        await api.post<Topico>(`/api/adm/forum/topicos/${topicoId}/respostas`, {
          corpo: resposta.trim(),
        }),
      );
      setResposta('');
      toast.ok('Resposta publicada');
      aoMudar();
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo('corpo')) || mensagemDeErro(falha));
    } finally {
      setEnviando(false);
    }
  };

  const t = topico.dados;
  return (
    <Dialogo
      aberto
      aparencia="lateral"
      tamanho="grande"
      titulo={t?.titulo ?? 'Tópico'}
      subtitulo={t ? `${t.autorNome} · ${dataHora(t.criadoEm)}` : undefined}
      aoFechar={aoFechar}
    >
      {topico.erro && <Alerta severidade="err" mensagem={topico.erro} />}
      {!t && !topico.erro && <Carregando mensagem="Carregando o tópico..." />}
      {t && (
        <div className="pc-pilha">
          <div className="pc-linha">
            {t.oculto && <Status rotulo="Oculto para os alunos" severidade="neutral" />}
            <Botao
              rotulo={t.fixado ? 'Desafixar' : 'Fixar'}
              icone={t.fixado ? 'pin-off' : 'pin'}
              aparencia="contorno"
              tamanho="pequeno"
              disabled={executando}
              onClick={() =>
                void alterar(
                  () => api.patch<Topico>(`/api/adm/forum/topicos/${t.id}`, { fixado: !t.fixado }),
                  t.fixado ? 'Tópico desafixado' : 'Tópico fixado',
                )
              }
            />
            <Botao
              rotulo={t.oculto ? 'Mostrar tópico' : 'Ocultar tópico'}
              icone={t.oculto ? 'eye' : 'eye-off'}
              aparencia="contorno"
              tamanho="pequeno"
              severidade={t.oculto ? 'pri' : 'err'}
              disabled={executando}
              onClick={() =>
                void alterar(
                  () => api.patch<Topico>(`/api/adm/forum/topicos/${t.id}`, { oculto: !t.oculto }),
                  t.oculto ? 'Tópico visível de novo' : 'Tópico ocultado',
                )
              }
            />
          </div>
          {/* HTML já filtrado pela API (Markdown → HTML seguro) */}
          <div className="pc-texto-longo" dangerouslySetInnerHTML={{ __html: t.corpoHtml }} />

          <h3 className="pc-subtitulo">Respostas ({t.respostas.length})</h3>
          {t.respostas.map((r) => (
            <article
              key={r.id}
              className={`pc-resposta${r.oculto ? ' pc-resposta-oculta' : ''}`}
              aria-label={`Resposta de ${r.autorNome}`}
            >
              <header className="pc-linha">
                <strong>{r.autorNome}</strong>
                {r.instrutor && <Badge cor="pri" rotulo="Instrutor" aparencia="suave" />}
                {r.oculto && <Status rotulo="Oculta" severidade="neutral" />}
                <span className="pc-dica">{dataHora(r.criadoEm)}</span>
                <Botao
                  rotulo={r.oculto ? 'Mostrar' : 'Ocultar'}
                  aparencia="texto"
                  tamanho="pequeno"
                  disabled={executando}
                  onClick={() =>
                    void alterar(
                      () =>
                        api.patch<Topico>(`/api/adm/forum/respostas/${r.id}`, {
                          oculto: !r.oculto,
                        }),
                      r.oculto ? 'Resposta visível de novo' : 'Resposta ocultada',
                    )
                  }
                />
              </header>
              <div dangerouslySetInnerHTML={{ __html: r.corpoHtml }} />
            </article>
          ))}

          <form
            noValidate
            className="pc-pilha"
            onSubmit={(evento) => {
              evento.preventDefault();
              void responder();
            }}
          >
            <CampoTextarea
              rotulo="Responder como instrutor"
              valor={resposta}
              aoMudar={setResposta}
              erro={erro}
              rows={5}
              maxLength={10_000}
              dica="Aceita Markdown."
            />
            <div>
              <Botao type="submit" rotulo="Publicar resposta" icone="send" carregando={enviando} />
            </div>
          </form>
        </div>
      )}
    </Dialogo>
  );
}
