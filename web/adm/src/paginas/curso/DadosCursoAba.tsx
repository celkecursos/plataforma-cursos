import {
  Botao,
  Card,
  CampoSelect,
  Detalhes,
  Imagem,
  Status,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { enviarParaUrl } from '@compartilhado/api/envio.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { CAMINHOS } from '../../caminhos.ts';
import { EscolherArquivo } from '@compartilhado/ui/EscolherArquivo.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import {
  opcoes,
  STATUS_CURSO,
  type Curso,
  type EnvioSimples,
  type StatusCurso,
} from '../../tipos.ts';
import { FormCurso } from '../cursos/FormCurso.tsx';

const CAPA_MAXIMA = 5 * 1024 * 1024;

interface Props {
  curso: Curso;
  aoMudar: (curso: Curso) => void;
}

export function DadosCursoAba({ curso, aoMudar }: Props) {
  const navigate = useNavigate();
  const toast = useToast();
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();
  const [editando, setEditando] = useState(false);
  const [enviandoCapa, setEnviandoCapa] = useState(false);
  const rota = `/api/adm/cursos/${curso.id}`;

  const enviarCapa = async (arquivo: File) => {
    if (!['image/jpeg', 'image/png'].includes(arquivo.type)) {
      toast.err('Formato não aceito', 'Envie uma imagem JPG ou PNG.');
      return;
    }
    if (arquivo.size > CAPA_MAXIMA) {
      toast.err('Imagem grande demais', 'A capa pode ter até 5 MB.');
      return;
    }
    setEnviandoCapa(true);
    const atualizado = await executar(async () => {
      const envio = await api.post<EnvioSimples>(`${rota}/capa/envio`, {
        contentType: arquivo.type,
        tamanho: arquivo.size,
      });
      await enviarParaUrl(envio.url, arquivo, envio.contentType);
      return api.put<Curso>(`${rota}/capa`, { chave: envio.chave });
    }, 'Capa atualizada');
    setEnviandoCapa(false);
    if (atualizado) aoMudar(atualizado);
  };

  return (
    <div className="pc-pilha">
      <Card
        titulo="Dados do curso"
        ferramentas={
          <Botao
            rotulo="Editar"
            icone="pencil"
            aparencia="contorno"
            onClick={() => setEditando(true)}
          />
        }
      >
        <div className="pc-grade-detalhes">
          <Detalhes rotulo="Título" valor={curso.titulo} />
          <Detalhes rotulo="Endereço (slug)" valor={curso.slug} />
          <Detalhes rotulo="Resumo" valor={curso.resumo} />
          <Detalhes rotulo="Atualizado em" valor={dataHora(curso.atualizadoEm)} />
        </div>
        {curso.descricao && <p className="pc-texto-longo">{curso.descricao}</p>}
      </Card>

      <Card titulo="Publicação">
        <div className="pc-linha">
          <Status
            rotulo={STATUS_CURSO[curso.status].rotulo}
            severidade={STATUS_CURSO[curso.status].severidade}
          />
          <CampoSelect<StatusCurso>
            rotulo="Alterar status"
            opcoes={opcoes(STATUS_CURSO)}
            valor={curso.status}
            disabled={executando}
            aoMudar={(status) => {
              if (!status || status === curso.status) return;
              void executar(
                () => api.patch<Curso>(`${rota}/status`, { status }),
                'Status do curso alterado',
              ).then((c) => c && aoMudar(c));
            }}
          />
        </div>
        <p className="pc-dica">Só cursos publicados aparecem no catálogo do site.</p>
      </Card>

      <Card titulo="Capa">
        {curso.capaUrl ? (
          <Imagem src={curso.capaUrl} alt={`Capa de ${curso.titulo}`} className="pc-capa" />
        ) : (
          <p className="pc-dica">Sem capa. Use uma imagem JPG ou PNG de até 5 MB.</p>
        )}
        <div className="pc-linha">
          <EscolherArquivo
            rotulo={curso.capaUrl ? 'Trocar capa' : 'Enviar capa'}
            icone="image"
            aceitar="image/jpeg,image/png"
            carregando={enviandoCapa}
            aoEscolher={(arquivo) => void enviarCapa(arquivo)}
          />
          {curso.capaUrl && (
            <Botao
              rotulo="Remover capa"
              icone="trash-2"
              aparencia="texto"
              severidade="err"
              onClick={async () => {
                if (!(await confirmar.remover({ mensagem: 'A capa do curso será removida.' })))
                  return;
                const ok = await executar(
                  () => api.delete(`${rota}/capa`).then(() => true),
                  'Capa removida',
                );
                if (ok) aoMudar({ ...curso, capaUrl: null });
              }}
            />
          )}
        </div>
      </Card>

      <Card titulo="Excluir curso">
        <p className="pc-dica">
          Só dá para excluir um curso sem turmas. Para tirar do ar sem perder nada, arquive.
        </p>
        <Botao
          rotulo="Excluir curso"
          icone="trash-2"
          severidade="err"
          aparencia="contorno"
          onClick={async () => {
            if (!(await confirmar.remover({ mensagem: `"${curso.titulo}" será excluído.` })))
              return;
            const ok = await executar(() => api.delete(rota).then(() => true), 'Curso excluído');
            if (ok) navigate(CAMINHOS.cursos);
          }}
        />
      </Card>

      {editando && (
        <FormCurso
          aberto
          curso={curso}
          aoFechar={() => setEditando(false)}
          aoSalvar={async (dados) => {
            aoMudar(await api.put<Curso>(rota, dados));
            toast.ok('Curso salvo');
            setEditando(false);
          }}
        />
      )}
    </div>
  );
}
