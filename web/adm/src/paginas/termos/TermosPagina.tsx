import {
  Aba,
  Abas,
  Alerta,
  Botao,
  CabecalhoPagina,
  Card,
  CampoTextarea,
  Carregando,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { TIPOS_TERMO, type Termo, type TipoTermo } from '../../tipos.ts';

/** Termos de uso e política de privacidade: histórico de versões e publicação de uma nova. */
export function TermosPagina() {
  const [parametros, setParametros] = useSearchParams();
  const tipo = (parametros.get('tipo') as TipoTermo | null) ?? 'TERMOS_USO';

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Termos" icone="file-text">
        Ao publicar uma versão nova, todos os usuários precisam aceitá-la para continuar usando a
        área do aluno.
      </CabecalhoPagina>
      <Abas
        ativa={tipo}
        aoMudar={(t) => setParametros({ tipo: t }, { replace: true })}
        rotulo="Tipo de termo"
      >
        {(Object.keys(TIPOS_TERMO) as TipoTermo[]).map((t) => (
          <Aba key={t} valor={t} rotulo={TIPOS_TERMO[t]}>
            <VersoesTermo tipo={t} />
          </Aba>
        ))}
      </Abas>
    </div>
  );
}

function VersoesTermo({ tipo }: { tipo: TipoTermo }) {
  const toast = useToast();
  const confirmar = useConfirmacao();
  const [conteudo, setConteudo] = useState('');
  const [erro, setErro] = useState<string>();
  const [publicando, setPublicando] = useState(false);
  const versoes = useCarregar(() => api.get<Termo[]>(`/api/adm/termos?tipo=${tipo}`), [tipo]);

  const publicar = async () => {
    if (!conteudo.trim()) {
      setErro('Escreva o texto da nova versão.');
      return;
    }
    setErro(undefined);
    const sim = await confirmar({
      titulo: `Publicar nova versão de ${TIPOS_TERMO[tipo]}?`,
      mensagem: 'Todos os usuários vão precisar aceitar o texto novo para continuar.',
      rotuloAceitar: 'Publicar',
    });
    if (!sim) return;
    setPublicando(true);
    try {
      const termo = await api.post<Termo>('/api/adm/termos', { tipo, conteudo: conteudo.trim() });
      toast.ok('Versão publicada', `${TIPOS_TERMO[tipo]} — versão ${termo.versao}`);
      setConteudo('');
      versoes.recarregar();
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo('conteudo')) || mensagemDeErro(falha));
    } finally {
      setPublicando(false);
    }
  };

  const [atual, ...anteriores] = versoes.dados ?? [];
  return (
    <div className="pc-pilha">
      {versoes.erro && <Alerta severidade="err" mensagem={versoes.erro} />}
      {versoes.carregando && !versoes.dados && <Carregando mensagem="Carregando as versões..." />}
      {versoes.dados && !atual && (
        <Alerta severidade="info" mensagem="Nenhuma versão publicada ainda." />
      )}
      {atual && (
        <Card
          titulo={`Versão vigente: ${atual.versao}`}
          subtitulo={`Publicada em ${dataHora(atual.publicadoEm)}`}
        >
          <div
            className="pc-texto-longo"
            dangerouslySetInnerHTML={{ __html: atual.conteudoHtml }}
          />
        </Card>
      )}
      {anteriores.length > 0 && (
        <Card titulo={`Versões anteriores (${anteriores.length})`} recolhivel recolhidoInicial>
          <div className="pc-pilha">
            {anteriores.map((t) => (
              <Card key={t.id} titulo={`Versão ${t.versao}`} subtitulo={dataHora(t.publicadoEm)}>
                <div
                  className="pc-texto-longo"
                  dangerouslySetInnerHTML={{ __html: t.conteudoHtml }}
                />
              </Card>
            ))}
          </div>
        </Card>
      )}
      <Card titulo="Nova versão">
        <form
          noValidate
          className="pc-pilha"
          onSubmit={(evento) => {
            evento.preventDefault();
            void publicar();
          }}
        >
          <CampoTextarea
            rotulo="Texto da nova versão"
            valor={conteudo}
            aoMudar={setConteudo}
            erro={erro}
            rows={12}
            maxLength={100_000}
            dica="Aceita Markdown (títulos com #, listas com -, **negrito**)."
          />
          <div>
            <Botao type="submit" rotulo="Publicar versão" icone="send" carregando={publicando} />
          </div>
        </form>
      </Card>
    </div>
  );
}
