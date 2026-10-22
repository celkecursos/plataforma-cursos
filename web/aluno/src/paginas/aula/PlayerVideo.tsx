import { Alerta, Carregando } from '@celke/kole-react';
import Hls from 'hls.js';
import { useEffect, useEffectEvent, useRef, useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';

/** De quantos em quantos segundos de vídeo a posição é enviada à API. */
export const INTERVALO_PROGRESSO = 10;

interface Props {
  aulaId: number;
  /** Onde o aluno parou (retoma dali, se não estiver no finzinho). */
  posicaoInicial: number;
  aoProgredir: (segundos: number) => void;
  aoTerminar: () => void;
}

type Estado =
  | { tipo: 'verificando' }
  | { tipo: 'pronto' }
  | { tipo: 'semVideo' }
  | { tipo: 'erro'; mensagem: string };

/** Só as playlists da API levam o token; os segmentos são URLs pré-assinadas do armazenamento. */
export function precisaDeToken(url: string, origem = window.location.origin): boolean {
  const destino = new URL(url, origem);
  return destino.origin === origem && destino.pathname.startsWith('/api/');
}

/** Ponto de retomada: recomeça do zero se faltava pouco para o fim (5 s, ou 10% em vídeo curto). */
export function pontoDeRetomada(posicao: number, duracao: number): number {
  const margem = Math.min(5, duracao * 0.1);
  return posicao > 0 && posicao < duracao - margem ? posicao : 0;
}

/**
 * Player HLS da aula (hls.js). Antes de criar o player, pede a master playlist pela API: confere o
 * acesso (403/404 viram mensagem) e, se o token tinha vencido, o cliente já renova.
 */
export function PlayerVideo({ aulaId, posicaoInicial, aoProgredir, aoTerminar }: Props) {
  const video = useRef<HTMLVideoElement>(null);
  const [estado, setEstado] = useState<Estado>({ tipo: 'verificando' });
  const master = `/api/aulas/${aulaId}/video.m3u8`;

  const progredir = useEffectEvent(aoProgredir);
  const terminar = useEffectEvent(aoTerminar);
  const posicao = useEffectEvent(() => posicaoInicial);

  useEffect(() => {
    let ativo = true;
    api.get<string>(master).then(
      () => ativo && setEstado({ tipo: 'pronto' }),
      (erro: unknown) => {
        if (!ativo) return;
        if (erro instanceof ErroApi && erro.status === 404) setEstado({ tipo: 'semVideo' });
        else setEstado({ tipo: 'erro', mensagem: mensagemDeErro(erro) });
      },
    );
    return () => {
      ativo = false;
    };
  }, [master]);

  useEffect(() => {
    const elemento = video.current;
    if (estado.tipo !== 'pronto' || !elemento) return;
    if (!Hls.isSupported()) {
      setTimeout(() =>
        setEstado({
          tipo: 'erro',
          mensagem: 'Este navegador não reproduz o vídeo. Use um navegador atualizado.',
        }),
      );
      return;
    }
    let renovou = false;
    const hls = new Hls({
      xhrSetup: (xhr, url) => {
        if (precisaDeToken(url) && api.tokenAcesso)
          xhr.setRequestHeader('Authorization', `Bearer ${api.tokenAcesso}`);
      },
    });
    hls.on(Hls.Events.ERROR, (_evento, dados) => {
      const status = dados.response?.code;
      // Token venceu no meio: renova uma vez e recarrega a playlist
      if (status === 401 && !renovou) {
        renovou = true;
        void api.renovar().then((sessao) => sessao && hls.loadSource(master));
        return;
      }
      if (dados.fatal) {
        setEstado({
          tipo: 'erro',
          mensagem: 'Não foi possível reproduzir o vídeo. Recarregue a página.',
        });
        hls.destroy();
      }
    });
    hls.loadSource(master);
    hls.attachMedia(elemento);

    let ultimoEnvio = -1;
    const enviar = () => {
      const segundos = Math.floor(elemento.currentTime);
      if (segundos !== ultimoEnvio) {
        ultimoEnvio = segundos;
        progredir(segundos);
      }
    };
    const aoCarregar = () => {
      elemento.currentTime = pontoDeRetomada(posicao(), elemento.duration);
    };
    const aoAtualizar = () => {
      if (Math.floor(elemento.currentTime) - ultimoEnvio >= INTERVALO_PROGRESSO) enviar();
    };
    const aoTerminarVideo = () => {
      enviar();
      terminar();
    };
    elemento.addEventListener('loadedmetadata', aoCarregar, { once: true });
    elemento.addEventListener('timeupdate', aoAtualizar);
    elemento.addEventListener('pause', enviar);
    elemento.addEventListener('ended', aoTerminarVideo);
    return () => {
      elemento.removeEventListener('timeupdate', aoAtualizar);
      elemento.removeEventListener('pause', enviar);
      elemento.removeEventListener('ended', aoTerminarVideo);
      if (elemento.currentTime > 0) enviar();
      hls.destroy();
    };
  }, [estado.tipo, master]);

  if (estado.tipo === 'semVideo')
    return <Alerta severidade="info" mensagem="Esta aula não tem vídeo." />;
  if (estado.tipo === 'erro')
    return <Alerta severidade="err" titulo="Vídeo indisponível" mensagem={estado.mensagem} />;
  return (
    <div className="pc-player">
      {estado.tipo === 'verificando' && <Carregando mensagem="Carregando o vídeo..." />}
      {/* Sem legendas: os vídeos das aulas ainda não têm faixa de texto */}
      <video
        ref={video}
        controls
        playsInline
        preload="metadata"
        aria-label="Vídeo da aula"
        hidden={estado.tipo !== 'pronto'}
      />
    </div>
  );
}
