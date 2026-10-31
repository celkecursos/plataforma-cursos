import { Alerta, Botao, CabecalhoPagina, Card, Carregando, CampoMarcacao } from '@celke/kole-react';
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';

export interface Termo {
  id: number;
  tipo: 'TERMOS_USO' | 'POLITICA_PRIVACIDADE';
  versao: number;
  conteudoHtml: string;
  publicadoEm: string;
}

const NOMES: Record<Termo['tipo'], string> = {
  TERMOS_USO: 'Termos de uso',
  POLITICA_PRIVACIDADE: 'Política de privacidade',
};

type Estado =
  | { tipo: 'carregando' }
  | { tipo: 'pendentes'; termos: Termo[] }
  | { tipo: 'livre' }
  | { tipo: 'erro'; mensagem: string };

/**
 * A API bloqueia a área do aluno enquanto houver termo vigente sem aceite (primeiro acesso e cada
 * versão nova). Aqui o aluno lê e aceita antes de seguir. O HTML dos termos já vem filtrado pela API.
 */
export function GuardaTermos({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<Estado>({ tipo: 'carregando' });
  const [concordo, setConcordo] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string>();

  const carregar = useCallback(() => {
    api
      .get<Termo[]>('/api/termos/pendentes')
      .then((termos) =>
        setEstado(termos.length ? { tipo: 'pendentes', termos } : { tipo: 'livre' }),
      )
      .catch((falha: unknown) => setEstado({ tipo: 'erro', mensagem: mensagemDeErro(falha) }));
  }, []);

  useEffect(carregar, [carregar]);

  if (estado.tipo === 'carregando') return <Carregando mensagem="Carregando..." />;
  if (estado.tipo === 'erro')
    return (
      <Alerta severidade="err" titulo="Não foi possível carregar" mensagem={estado.mensagem} />
    );
  if (estado.tipo === 'livre') return <>{children}</>;

  const aceitar = async () => {
    if (!concordo) {
      setErro('Marque que leu e concorda para continuar.');
      return;
    }
    setEnviando(true);
    setErro(undefined);
    try {
      for (const termo of estado.termos) {
        await api.post(`/api/termos/${termo.id}/aceite`);
      }
      setEstado({ tipo: 'livre' });
    } catch (falha) {
      setErro(mensagemDeErro(falha));
      carregar();
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Termos atualizados" icone="file-text" />
      <Alerta
        severidade="info"
        mensagem="Para continuar usando a plataforma, leia e aceite os termos abaixo."
      />
      {estado.termos.map((termo) => (
        <Card key={termo.id} titulo={`${NOMES[termo.tipo]} (versão ${termo.versao})`}>
          <div dangerouslySetInnerHTML={{ __html: termo.conteudoHtml }} />
        </Card>
      ))}
      <CampoMarcacao
        rotulo="Li e concordo com os termos acima"
        valor={concordo}
        aoMudar={setConcordo}
        erro={erro}
      />
      <div>
        <Botao
          rotulo="Aceitar e continuar"
          icone="check"
          carregando={enviando}
          onClick={() => void aceitar()}
        />
      </div>
    </div>
  );
}
