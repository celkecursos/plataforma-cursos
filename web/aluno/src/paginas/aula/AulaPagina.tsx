import {
  Alerta,
  Badge,
  Botao,
  CabecalhoPagina,
  Card,
  Carregando,
  useToast,
} from '@celke/kole-react';
import { useCallback } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { caminhoAula, caminhoTurma } from '../../caminhos.ts';
import {
  localizarAula,
  type ArquivoApoio,
  type AulaAluno,
  type Progresso,
  type TurmaAluno,
} from '../../tipos.ts';
import { ForumLista } from '../forum/ForumLista.tsx';
import { ListaArquivos } from './ListaArquivos.tsx';
import { PlayerVideo } from './PlayerVideo.tsx';

/** A aula: vídeo com retomada e progresso, vista/revisar, anterior/próxima, arquivos e fórum da aula. */
export function AulaPagina() {
  const { turmaId, aulaId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const aula = useCarregar(() => api.get<AulaAluno>(`/api/aluno/aulas/${aulaId}`), [aulaId]);
  const turma = useCarregar(() => api.get<TurmaAluno>(`/api/aluno/turmas/${turmaId}`), [turmaId]);
  const progresso = useCarregar(
    () => api.get<Progresso>(`/api/aluno/aulas/${aulaId}/progresso`),
    [aulaId],
  );
  const arquivos = useCarregar(
    () => api.get<ArquivoApoio[]>(`/api/aluno/aulas/${aulaId}/arquivos`),
    [aulaId],
  );

  const { definir } = progresso;
  const atualizar = useCallback(
    (campos: Partial<Pick<Progresso, 'posicaoSegundos' | 'vista' | 'revisar'>>, aviso?: string) =>
      api
        .patch<Progresso>(`/api/aluno/aulas/${aulaId}/progresso`, campos)
        .then((novo) => {
          definir(novo);
          if (aviso) toast.ok(aviso);
        })
        .catch((erro: unknown) => {
          // A posição é enviada sozinha de tempos em tempos: só avisa falha de ação do aluno
          if (aviso) toast.err('Não foi possível salvar', mensagemDeErro(erro));
        }),
    [aulaId, definir, toast],
  );

  if (aula.erro && !aula.dados) {
    return (
      <div className="pc-pagina">
        <Alerta severidade="err" titulo="Não foi possível abrir a aula" mensagem={aula.erro} />
        <div>
          <Botao
            rotulo="Voltar ao curso"
            aparencia="texto"
            onClick={() => navigate(caminhoTurma(turmaId!))}
          />
        </div>
      </div>
    );
  }
  if (!aula.dados || !progresso.dados) return <Carregando mensagem="Carregando a aula..." />;
  const a = aula.dados;
  const p = progresso.dados;
  const lugar = turma.dados ? localizarAula(turma.dados, a.id) : null;

  return (
    <div className="pc-pagina">
      <CabecalhoPagina
        titulo={a.titulo}
        icone="circle-play"
        categoria={lugar ? `${turma.dados!.cursoTitulo} · ${lugar.modulo.titulo}` : undefined}
        acoes={
          <Botao
            rotulo="Voltar ao curso"
            icone="chevron-left"
            aparencia="texto"
            onClick={() => navigate(caminhoTurma(a.turmaId))}
          />
        }
      />
      <PlayerVideo
        key={a.id}
        aulaId={a.id}
        posicaoInicial={p.posicaoSegundos}
        aoProgredir={(segundos) => void atualizar({ posicaoSegundos: segundos })}
        aoTerminar={() => !p.vista && void atualizar({ vista: true }, 'Aula concluída')}
      />

      <div className="pc-linha">
        {p.vista ? (
          <>
            <Badge cor="ok" icone="check" rotulo="Aula vista" aparencia="suave" />
            <Botao
              rotulo="Desmarcar como vista"
              aparencia="texto"
              tamanho="pequeno"
              onClick={() => void atualizar({ vista: false }, 'Aula desmarcada')}
            />
          </>
        ) : (
          <Botao
            rotulo="Marcar como vista"
            icone="check"
            onClick={() => void atualizar({ vista: true }, 'Aula concluída')}
          />
        )}
        <Botao
          rotulo={p.revisar ? 'Tirar de revisar' : 'Revisar depois'}
          icone="bookmark"
          aparencia="contorno"
          pressionado={p.revisar}
          onClick={() =>
            void atualizar(
              { revisar: !p.revisar },
              p.revisar ? 'Tirada da revisão' : 'Marcada para revisar',
            )
          }
        />
      </div>

      {lugar && (
        <nav className="pc-linha pc-navegacao-aulas" aria-label="Navegação entre aulas">
          <Botao
            rotulo={lugar.anterior ? `Anterior: ${lugar.anterior.titulo}` : 'Primeira aula'}
            icone="chevron-left"
            aparencia="texto"
            disabled={!lugar.anterior}
            onClick={() => lugar.anterior && navigate(caminhoAula(a.turmaId, lugar.anterior.id))}
          />
          <Botao
            rotulo={lugar.proxima ? `Próxima: ${lugar.proxima.titulo}` : 'Última aula'}
            icone="chevron-right"
            posicaoIcone="direita"
            disabled={!lugar.proxima}
            onClick={() => lugar.proxima && navigate(caminhoAula(a.turmaId, lugar.proxima.id))}
          />
        </nav>
      )}

      {a.descricao && (
        <Card titulo="Sobre a aula">
          <p className="pc-texto-longo">{a.descricao}</p>
        </Card>
      )}
      {!!arquivos.dados?.length && (
        <Card>
          <ListaArquivos titulo="Arquivos da aula" arquivos={arquivos.dados} />
        </Card>
      )}
      {turma.dados && (
        <Card titulo="Dúvidas sobre esta aula">
          <ForumLista turmaId={a.turmaId} aulaId={a.id} />
        </Card>
      )}
    </div>
  );
}
