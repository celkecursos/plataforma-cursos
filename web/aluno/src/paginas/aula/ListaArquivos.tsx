import { ItemLista, Lista } from '@celke/kole-react';
import { api } from '@compartilhado/api/cliente.ts';
import { bytes } from '@compartilhado/ui/formato.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import type { ArquivoApoio, Download } from '../../tipos.ts';

/**
 * Arquivos de apoio para baixar. O link de download é pedido na hora do clique (a API confere o
 * acesso e devolve uma URL que vale 5 minutos).
 */
export function ListaArquivos({ titulo, arquivos }: { titulo: string; arquivos: ArquivoApoio[] }) {
  const { executar } = useAcao();
  return (
    <section className="pc-arquivos" aria-label={titulo}>
      <h3 className="pc-subtitulo">{titulo}</h3>
      <Lista rotulo={titulo}>
        {arquivos.map((a) => (
          <ItemLista
            key={a.id}
            icone="download"
            titulo={a.titulo}
            onClick={async () => {
              const download = await executar(() =>
                api.get<Download>(`/api/aluno/arquivos/${a.id}/download`),
              );
              if (download) window.location.assign(download.url);
            }}
          >
            {a.nomeArquivo} · {bytes(a.tamanho)}
          </ItemLista>
        ))}
      </Lista>
    </section>
  );
}
