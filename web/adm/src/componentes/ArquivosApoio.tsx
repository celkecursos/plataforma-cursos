import {
  AcaoLinha,
  Alerta,
  CampoTexto,
  Lista,
  ItemLista,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { enviarParaUrl } from '@compartilhado/api/envio.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { bytes } from '@compartilhado/ui/formato.ts';
import type { ArquivoApoio, EnvioSimples } from '../tipos.ts';
import { EscolherArquivo } from '@compartilhado/ui/EscolherArquivo.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';

/** Formatos aceitos pela API (FormatoArquivo). */
const ACEITOS = 'application/pdf,application/zip,text/plain,.pdf,.zip,.txt';
const TIPOS = ['application/pdf', 'application/zip', 'application/x-zip-compressed', 'text/plain'];

interface Props {
  dono: 'aulas' | 'modulos';
  donoId: number;
  titulo: string;
}

/** Arquivos de apoio de uma aula ou de um módulo: lista, envio direto ao armazenamento e exclusão. */
export function ArquivosApoio({ dono, donoId, titulo }: Props) {
  const toast = useToast();
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();
  const [nome, setNome] = useState('');
  const rota = `/api/adm/${dono}/${donoId}/arquivos`;
  const arquivos = useCarregar(() => api.get<ArquivoApoio[]>(rota), [rota]);

  const enviar = async (arquivo: File) => {
    // O Windows manda zip como x-zip-compressed; a API só conhece application/zip
    const tipo = arquivo.type === 'application/x-zip-compressed' ? 'application/zip' : arquivo.type;
    if (!TIPOS.includes(arquivo.type)) {
      toast.err('Formato não aceito', 'Envie PDF, ZIP ou TXT.');
      return;
    }
    const novo = await executar(async () => {
      const envio = await api.post<EnvioSimples>(`${rota}/envio`, {
        contentType: tipo,
        tamanho: arquivo.size,
      });
      await enviarParaUrl(envio.url, arquivo, envio.contentType);
      return api.post<ArquivoApoio>(rota, {
        chave: envio.chave,
        titulo: nome.trim() || arquivo.name.replace(/\.[^.]+$/, ''),
        nomeArquivo: arquivo.name,
      });
    }, 'Arquivo enviado');
    if (novo) {
      setNome('');
      arquivos.recarregar();
    }
  };

  return (
    <section className="pc-arquivos" aria-label={titulo}>
      <h3 className="pc-subtitulo">{titulo}</h3>
      {arquivos.erro && <Alerta severidade="err" mensagem={arquivos.erro} />}
      {!!arquivos.dados?.length && (
        <Lista rotulo={titulo}>
          {arquivos.dados.map((a) => (
            <ItemLista
              key={a.id}
              icone="paperclip"
              titulo={a.titulo}
              fim={
                <AcaoLinha
                  icone="trash-2"
                  severidade="err"
                  rotulo={`Excluir ${a.titulo}`}
                  onClick={async () => {
                    if (!(await confirmar.remover({ mensagem: `"${a.titulo}" será removido.` })))
                      return;
                    const ok = await executar(
                      () => api.delete(`/api/adm/arquivos/${a.id}`).then(() => true),
                      'Arquivo removido',
                    );
                    if (ok) arquivos.recarregar();
                  }}
                />
              }
            >
              {a.nomeArquivo} · {bytes(a.tamanho)}
            </ItemLista>
          ))}
        </Lista>
      )}
      {arquivos.dados?.length === 0 && <p className="pc-dica">Nenhum arquivo.</p>}
      <div className="pc-linha">
        <CampoTexto
          rotulo="Título do arquivo"
          valor={nome}
          aoMudar={setNome}
          maxLength={150}
          dica="Em branco, usa o nome do arquivo."
        />
        <EscolherArquivo
          rotulo="Enviar arquivo"
          icone="upload"
          aparencia="contorno"
          aceitar={ACEITOS}
          carregando={executando}
          aoEscolher={(arquivo) => void enviar(arquivo)}
        />
      </div>
    </section>
  );
}
