import { DialogoArquivo, type Anexo } from '@celke/kole-react';
import { useState } from 'react';

interface Props {
  /** Imagens já publicadas (URL temporária devolvida pela API). */
  imagens: readonly { id: number; url: string }[];
  /** Quem enviou, para o texto alternativo (ex.: "Imagem 1 de Ana"). */
  de: string;
}

/** Miniaturas das imagens de uma mensagem; clicar abre o visualizador em tela cheia. */
export function GaleriaImagens({ imagens, de }: Props) {
  const [aberta, setAberta] = useState<number | null>(null);
  if (imagens.length === 0) return null;
  const arquivos: Anexo[] = imagens.map((imagem, i) => ({
    nome: `Imagem ${i + 1} de ${de}`,
    tamanho: 0,
    tipo: 'image/jpeg',
    url: imagem.url,
  }));

  return (
    <>
      <ul className="pc-galeria" aria-label={`Imagens de ${de}`}>
        {arquivos.map((arquivo, i) => (
          <li key={imagens[i]!.id}>
            <button type="button" onClick={() => setAberta(i)} title={`Ampliar ${arquivo.nome}`}>
              <img src={arquivo.url} alt={arquivo.nome} loading="lazy" />
            </button>
          </li>
        ))}
      </ul>
      <DialogoArquivo
        aberto={aberta !== null}
        aoFechar={() => setAberta(null)}
        arquivos={arquivos}
        indice={aberta ?? 0}
        aoMudarIndice={setAberta}
      />
    </>
  );
}
