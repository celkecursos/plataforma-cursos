import { Botao, type BotaoProps } from '@celke/kole-react';
import { useId, useRef } from 'react';

interface Props extends Pick<
  BotaoProps,
  'rotulo' | 'icone' | 'carregando' | 'aparencia' | 'severidade'
> {
  /** Tipos aceitos (atributo `accept`). */
  aceitar: string;
  aoEscolher: (arquivo: File) => void;
}

/** Botão que abre a escolha de arquivo do sistema. O input fica oculto, mas com rótulo acessível. */
export function EscolherArquivo({
  aceitar,
  aoEscolher,
  rotulo = 'Escolher arquivo',
  ...botao
}: Props) {
  const entrada = useRef<HTMLInputElement>(null);
  const id = useId();
  return (
    <>
      <input
        ref={entrada}
        id={id}
        type="file"
        accept={aceitar}
        aria-label={rotulo}
        hidden
        onChange={(evento) => {
          const arquivo = evento.target.files?.[0];
          evento.target.value = '';
          if (arquivo) aoEscolher(arquivo);
        }}
      />
      <Botao rotulo={rotulo} {...botao} onClick={() => entrada.current?.click()} />
    </>
  );
}
