import { Aba, Abas, Alerta, Carregando, Status } from '@celke/kole-react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { caminhoCurso } from '../../caminhos.ts';
import { STATUS_TURMA, type Curso, type Turma } from '../../tipos.ts';
import { ConfiguracaoTurmaAba } from './ConfiguracaoTurmaAba.tsx';
import { ConteudoAba } from './ConteudoAba.tsx';
import { ForumAba } from './ForumAba.tsx';
import { MatriculasAba } from './MatriculasAba.tsx';
import { VendaAba } from './VendaAba.tsx';

export interface ContextoTurma {
  curso: Curso;
  turma: Turma;
}

/** Uma turma: conteúdo, matrículas, venda, fórum e configurações. A API não tem GET da turma: vem da lista do curso. */
export function TurmaPagina() {
  const { cursoId, turmaId } = useParams();
  const navigate = useNavigate();
  const [parametros, setParametros] = useSearchParams();
  const contexto = useCarregar(async (): Promise<ContextoTurma> => {
    const [curso, turmas] = await Promise.all([
      api.get<Curso>(`/api/adm/cursos/${cursoId}`),
      api.get<Turma[]>(`/api/adm/cursos/${cursoId}/turmas`),
    ]);
    const turma = turmas.find((t) => String(t.id) === turmaId);
    if (!turma) throw new ErroApi(404, { detail: 'Turma não encontrada neste curso.' });
    return { curso, turma };
  }, [cursoId, turmaId]);

  if (contexto.erro && !contexto.dados) {
    return (
      <div className="pc-pagina">
        <Alerta severidade="err" titulo="Não foi possível abrir a turma" mensagem={contexto.erro} />
      </div>
    );
  }
  if (!contexto.dados) return <Carregando mensagem="Carregando a turma..." />;
  const { curso, turma } = contexto.dados;
  const voltar = () => navigate(`${caminhoCurso(curso.id)}?aba=turmas`);

  return (
    <div className="pc-pagina">
      <Abas
        titulo={turma.nome}
        identidade={curso.titulo}
        aoVoltar={voltar}
        acoes={
          <Status
            rotulo={STATUS_TURMA[turma.status].rotulo}
            severidade={STATUS_TURMA[turma.status].severidade}
          />
        }
        ativa={parametros.get('aba') ?? 'conteudo'}
        aoMudar={(aba) => setParametros({ aba }, { replace: true })}
        rotulo="Seções da turma"
      >
        <Aba valor="conteudo" rotulo="Conteúdo" icone="layers">
          <ConteudoAba curso={curso} turma={turma} />
        </Aba>
        <Aba valor="matriculas" rotulo="Matrículas" icone="users">
          <MatriculasAba turma={turma} />
        </Aba>
        <Aba valor="venda" rotulo="Venda" icone="tag">
          <VendaAba turma={turma} />
        </Aba>
        <Aba valor="forum" rotulo="Fórum" icone="message-square">
          <ForumAba turma={turma} />
        </Aba>
        <Aba valor="configuracao" rotulo="Configurações" icone="settings">
          <ConfiguracaoTurmaAba
            turma={turma}
            aoMudar={(nova) => contexto.definir({ curso, turma: nova })}
            aoExcluir={voltar}
          />
        </Aba>
      </Abas>
    </div>
  );
}
