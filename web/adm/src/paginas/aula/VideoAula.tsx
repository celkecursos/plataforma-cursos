import {
  Alerta,
  Botao,
  Card,
  Detalhes,
  Imagem,
  Progresso,
  Status,
  useConfirmacao,
} from '@celke/kole-react';
import { useEffect, useRef, useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { enviarEmPartes } from '@compartilhado/api/envio.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { bytes, dataHora, duracao } from '@compartilhado/ui/formato.ts';
import { EscolherArquivo } from '../../componentes/EscolherArquivo.tsx';
import { useAcao } from '../../componentes/useAcao.ts';
import { STATUS_VIDEO, type EnvioVideo, type Video } from '../../tipos.ts';

/** Formatos aceitos pela API (FormatoVideo). */
const TIPOS = ['video/mp4', 'video/quicktime', 'video/x-matroska', 'video/webm'];
const ACEITOS = `${TIPOS.join(',')},.mp4,.mov,.mkv,.webm`;
const INTERVALO_CONSULTA = 3000;

/** O navegador às vezes não informa o tipo (MKV no Windows): deduz pela extensão. */
export function tipoDoVideo(arquivo: File): string | null {
  if (TIPOS.includes(arquivo.type)) return arquivo.type;
  const extensao = arquivo.name.split('.').pop()?.toLowerCase();
  const porExtensao: Record<string, string> = {
    mp4: 'video/mp4',
    mov: 'video/quicktime',
    mkv: 'video/x-matroska',
    webm: 'video/webm',
  };
  return (extensao && porExtensao[extensao]) || null;
}

const emAndamento = (v: Video | null | undefined) =>
  v?.status === 'PENDENTE' || v?.status === 'PROCESSANDO';

/** Vídeo da aula: envio em partes direto ao armazenamento, acompanhamento da conversão e remoção. */
export function VideoAula({ aulaId }: { aulaId: number }) {
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();
  const [enviado, setEnviado] = useState<number | null>(null);
  const [total, setTotal] = useState(0);
  const [erroEnvio, setErroEnvio] = useState<string>();
  const cancelar = useRef<AbortController | null>(null);
  const rota = `/api/adm/aulas/${aulaId}/video`;

  const video = useCarregar(
    () =>
      api.get<Video>(rota).catch((erro: unknown) => {
        if (erro instanceof ErroApi && erro.status === 404) return null;
        throw erro;
      }),
    [rota],
  );
  const v = video.dados;

  // Enquanto o worker converte, consulta o status de tempos em tempos
  useEffect(() => {
    if (!emAndamento(v)) return;
    const relogio = window.setTimeout(video.recarregar, INTERVALO_CONSULTA);
    return () => window.clearTimeout(relogio);
  }, [v, video.recarregar]);

  useEffect(() => () => cancelar.current?.abort(), []);

  const enviar = async (arquivo: File) => {
    const tipo = tipoDoVideo(arquivo);
    setErroEnvio(undefined);
    if (!tipo) {
      setErroEnvio('Envie um vídeo MP4, MOV, MKV ou WebM.');
      return;
    }
    cancelar.current = new AbortController();
    setTotal(arquivo.size);
    setEnviado(0);
    try {
      const envio = await api.post<EnvioVideo>(`${rota}/envio`, {
        nomeArquivo: arquivo.name,
        contentType: tipo,
        tamanho: arquivo.size,
      });
      const partes = await enviarEmPartes(
        arquivo,
        envio.tamanhoParte,
        envio.partes,
        setEnviado,
        cancelar.current.signal,
      );
      video.definir(await api.post<Video>(`${rota}/envio/conclusao`, { partes }));
    } catch (erro) {
      setErroEnvio(erro instanceof DOMException ? 'Envio cancelado.' : mensagemDeErro(erro));
    } finally {
      setEnviado(null);
      cancelar.current = null;
    }
  };

  const enviando = enviado !== null;
  return (
    <Card titulo="Vídeo da aula">
      {video.erro && <Alerta severidade="err" mensagem={video.erro} />}
      {erroEnvio && (
        <Alerta severidade="err" titulo="O vídeo não foi enviado" mensagem={erroEnvio} />
      )}

      {v && (
        <div className="pc-pilha">
          <div className="pc-linha">
            <Status
              rotulo={STATUS_VIDEO[v.status].rotulo}
              severidade={STATUS_VIDEO[v.status].severidade}
            />
            {v.publicado && v.status !== 'PRONTO' && (
              <Status rotulo="O vídeo anterior continua no ar" severidade="info" />
            )}
          </div>
          {emAndamento(v) && (
            <Progresso
              rotulo="Conversão do vídeo"
              valor={50}
              aparencia="listrado"
              animado
              mostrarValor="nenhum"
            />
          )}
          {v.status === 'ERRO' && (
            <Alerta
              severidade="err"
              titulo="A conversão falhou"
              mensagem={v.erro ?? 'Erro desconhecido.'}
            />
          )}
          {v.thumbnailUrl && (
            <Imagem src={v.thumbnailUrl} alt="Quadro do vídeo" className="pc-capa" />
          )}
          <div className="pc-grade-detalhes">
            <Detalhes rotulo="Arquivo enviado" valor={v.nomeOriginal ?? '—'} />
            <Detalhes rotulo="Duração" valor={duracao(v.duracaoSegundos)} />
            <Detalhes
              rotulo="Resolução"
              valor={v.largura && v.altura ? `${v.largura}×${v.altura}` : '—'}
            />
            <Detalhes
              rotulo="Qualidades"
              valor={v.variantes.length ? v.variantes.join(', ') : '—'}
            />
            <Detalhes rotulo="Tamanho original" valor={bytes(v.tamanhoOriginal)} />
            <Detalhes
              rotulo="Tamanho convertido"
              valor={
                v.tamanhoFinal == null
                  ? '—'
                  : `${bytes(v.tamanhoFinal)}${v.reducaoPercentual != null ? ` (−${v.reducaoPercentual.toFixed(0)}%)` : ''}`
              }
            />
            <Detalhes rotulo="Processado em" valor={dataHora(v.processadoEm)} />
          </div>
        </div>
      )}
      {v === null && (
        <p className="pc-dica">Esta aula ainda não tem vídeo. Envie MP4, MOV, MKV ou WebM.</p>
      )}

      {enviando && (
        <div className="pc-pilha">
          <Progresso rotulo="Envio do vídeo" valor={enviado} max={total || 1} mostrarValor="fim" />
          <div>
            <Botao
              rotulo="Cancelar envio"
              aparencia="texto"
              severidade="err"
              onClick={() => cancelar.current?.abort()}
            />
          </div>
        </div>
      )}

      <div className="pc-linha">
        <EscolherArquivo
          rotulo={v ? 'Enviar novo vídeo' : 'Enviar vídeo'}
          icone="upload"
          aceitar={ACEITOS}
          carregando={enviando}
          aoEscolher={(arquivo) => void enviar(arquivo)}
        />
        {v?.status === 'ERRO' && (
          <Botao
            rotulo="Reprocessar"
            icone="refresh-cw"
            aparencia="contorno"
            disabled={executando}
            onClick={async () => {
              const novo = await executar(
                () => api.post<Video>(`${rota}/reprocessamento`),
                'Vídeo de volta à fila',
              );
              if (novo) video.definir(novo);
            }}
          />
        )}
        {v && !emAndamento(v) && (
          <Botao
            rotulo="Remover vídeo"
            icone="trash-2"
            aparencia="texto"
            severidade="err"
            disabled={executando || enviando}
            onClick={async () => {
              if (
                !(await confirmar.remover({
                  mensagem: 'O vídeo e as versões convertidas serão apagados.',
                }))
              )
                return;
              const ok = await executar(() => api.delete(rota).then(() => true), 'Vídeo removido');
              if (ok) video.definir(null);
            }}
          />
        )}
      </div>
    </Card>
  );
}
