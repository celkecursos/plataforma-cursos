import { AcaoLinha, Botao, Progresso } from '@celke/kole-react';
import {
  forwardRef,
  useEffect,
  useId,
  useImperativeHandle,
  useRef,
  useState,
  type ClipboardEvent,
  type DragEvent,
} from 'react';
import { bytes } from './formato.ts';
import { imagensColadas, triarImagens, type ImagemLocal, type LimitesImagens } from './imagens.ts';

export interface CampoImagensRef {
  /** Para o formulário repassar o que foi colado em outro campo (ex.: no texto da mensagem). */
  colar: (evento: ClipboardEvent) => void;
}

interface Props {
  rotulo: string;
  valor: ImagemLocal[];
  aoMudar: (imagens: ImagemLocal[]) => void;
  limites: LimitesImagens;
  /** Durante o envio: fração enviada (0 a 1) de cada imagem, pelo `id`. */
  progresso?: Record<string, number>;
  /** Erro vindo de fora (a API recusou a imagem). */
  erro?: string;
  desabilitado?: boolean;
}

let sequencia = 0;
const novoId = () =>
  typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `img-${Date.now()}-${++sequencia}`;

/**
 * Imagens anexadas a uma mensagem: escolher, arrastar ou colar (Ctrl+V de um print). Confere tipo,
 * tamanho e quantidade em todos os caminhos; o envio fica com quem usa (cada API tem o seu).
 */
export const CampoImagens = forwardRef<CampoImagensRef, Props>(function CampoImagens(
  { rotulo, valor, aoMudar, limites, progresso, erro, desabilitado = false },
  ref,
) {
  const entrada = useRef<HTMLInputElement>(null);
  const idEntrada = useId();
  const [recusas, setRecusas] = useState<string[]>([]);
  const [sobre, setSobre] = useState(false);
  // As prévias vivas, para liberar ao desmontar
  const previas = useRef(new Set<string>());
  const cheio = valor.length >= limites.quantidadeMaxima;

  useEffect(() => {
    const vivas = previas.current;
    return () => vivas.forEach((url) => URL.revokeObjectURL(url));
  }, []);

  const adicionar = (arquivos: File[]) => {
    if (desabilitado || arquivos.length === 0) return;
    const { aceitas, recusas: motivos } = triarImagens(valor.length, arquivos, limites);
    setRecusas(motivos);
    if (aceitas.length === 0) return;
    const novas = aceitas.map((arquivo) => {
      const previa = URL.createObjectURL(arquivo);
      previas.current.add(previa);
      return { id: novoId(), arquivo, previa };
    });
    aoMudar([...valor, ...novas]);
  };

  const colar = (evento: ClipboardEvent) => {
    const imagens = imagensColadas(evento.clipboardData);
    if (imagens.length === 0) return;
    // Só impede o colar padrão quando há imagem: texto continua indo para o campo de texto
    evento.preventDefault();
    adicionar(imagens);
  };

  useImperativeHandle(ref, () => ({ colar }));

  const remover = (imagem: ImagemLocal) => {
    URL.revokeObjectURL(imagem.previa);
    previas.current.delete(imagem.previa);
    setRecusas([]);
    aoMudar(valor.filter((i) => i.id !== imagem.id));
  };

  const soltar = (evento: DragEvent) => {
    evento.preventDefault();
    setSobre(false);
    adicionar(Array.from(evento.dataTransfer.files));
  };

  const mensagens = [erro, ...recusas].filter(Boolean);
  const mb = Math.round(limites.tamanhoMaximo / (1024 * 1024));

  return (
    <div className="pc-imagens">
      <div
        role="group"
        aria-label={rotulo}
        className={`pc-imagens-zona${sobre ? ' pc-imagens-zona-sobre' : ''}`}
        onDragOver={(evento) => {
          evento.preventDefault();
          if (!desabilitado) setSobre(true);
        }}
        onDragLeave={() => setSobre(false)}
        onDrop={soltar}
        onPaste={colar}
      >
        <input
          ref={entrada}
          id={idEntrada}
          type="file"
          accept={limites.tipos.join(',')}
          multiple
          hidden
          aria-label={`Anexar ${rotulo.toLowerCase()}`}
          disabled={desabilitado || cheio}
          onChange={(evento) => {
            const arquivos = Array.from(evento.target.files ?? []);
            evento.target.value = '';
            adicionar(arquivos);
          }}
        />
        <Botao
          rotulo="Anexar imagem"
          icone="image"
          aparencia="contorno"
          tamanho="pequeno"
          disabled={desabilitado || cheio}
          onClick={() => entrada.current?.click()}
        />
        <span className="pc-dica">
          {cheio
            ? `Limite de ${limites.quantidadeMaxima} imagens atingido.`
            : `Ou arraste e solte, ou cole um print (Ctrl+V). JPG ou PNG, até ${mb} MB, no máximo ${limites.quantidadeMaxima}.`}
        </span>
      </div>

      {mensagens.length > 0 && (
        <div role="alert" className="pc-imagens-erros">
          {mensagens.map((mensagem) => (
            <p key={mensagem}>{mensagem}</p>
          ))}
        </div>
      )}

      {valor.length > 0 && (
        <ul className="pc-imagens-lista" aria-label="Imagens anexadas">
          {valor.map((imagem) => {
            const enviado = progresso?.[imagem.id];
            return (
              <li key={imagem.id} className="pc-imagens-item">
                <img
                  src={imagem.previa}
                  alt={`Prévia de ${imagem.arquivo.name || 'imagem colada'}`}
                />
                <span className="pc-imagens-texto">
                  <span className="pc-imagens-nome">{imagem.arquivo.name || 'imagem colada'}</span>
                  <span className="pc-dica">{bytes(imagem.arquivo.size)}</span>
                  {enviado !== undefined && (
                    <Progresso
                      rotulo={`Envio de ${imagem.arquivo.name || 'imagem colada'}`}
                      valor={Math.round(enviado * 100)}
                      altura="xs"
                    />
                  )}
                </span>
                {!desabilitado && (
                  <AcaoLinha
                    icone="x"
                    severidade="err"
                    rotulo={`Remover ${imagem.arquivo.name || 'imagem colada'}`}
                    onClick={() => remover(imagem)}
                  />
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
});
