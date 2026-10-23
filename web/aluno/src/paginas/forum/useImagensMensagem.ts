import { useCallback, useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { enviarParaUrl } from '@compartilhado/api/envio.ts';
import type { ImagemLocal } from '@compartilhado/ui/imagens.ts';
import type { EnvioSimples } from '../../tipos.ts';

/**
 * Envia as imagens de uma mensagem do fórum, uma por vez, direto ao armazenamento (URL pré-assinada
 * pela API), e devolve as chaves para publicar a pergunta ou a resposta.
 */
export async function enviarImagensForum(
  imagens: readonly ImagemLocal[],
  aoProgredir: (id: string, fracao: number) => void,
): Promise<string[]> {
  const chaves: string[] = [];
  for (const imagem of imagens) {
    const { arquivo } = imagem;
    const envio = await api.post<EnvioSimples>('/api/aluno/forum/anexos/envio', {
      contentType: arquivo.type,
      tamanho: arquivo.size,
    });
    aoProgredir(imagem.id, 0);
    await enviarParaUrl(envio.url, arquivo, envio.contentType, (enviados) =>
      aoProgredir(imagem.id, arquivo.size ? enviados / arquivo.size : 1),
    );
    aoProgredir(imagem.id, 1);
    chaves.push(envio.chave);
  }
  return chaves;
}

/**
 * Imagens escolhidas para a mensagem e o progresso do envio. O envio acontece só ao publicar: a API
 * apaga os envios ao processá-los, então uma tentativa nova sempre envia de novo.
 */
export function useImagensMensagem() {
  const [imagens, setImagens] = useState<ImagemLocal[]>([]);
  const [progresso, setProgresso] = useState<Record<string, number>>();

  const enviar = useCallback(async () => {
    if (imagens.length === 0) return [];
    setProgresso({});
    try {
      return await enviarImagensForum(imagens, (id, fracao) =>
        setProgresso((atual) => ({ ...atual, [id]: fracao })),
      );
    } finally {
      setProgresso(undefined);
    }
  }, [imagens]);

  const limpar = useCallback(() => {
    imagens.forEach((imagem) => URL.revokeObjectURL(imagem.previa));
    setImagens([]);
  }, [imagens]);

  return { imagens, setImagens, progresso, enviar, limpar };
}
