import { Aba, Abas, Alerta, Carregando } from '@celke/kole-react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { CAMINHOS } from '../../caminhos.ts';
import type { Curso } from '../../tipos.ts';
import { DadosCursoAba } from './DadosCursoAba.tsx';
import { TurmasAba } from './TurmasAba.tsx';

/** Um curso: dados (com capa e status) e as turmas. A aba ativa fica no endereço (?aba=). */
export function CursoPagina() {
  const { cursoId } = useParams();
  const navigate = useNavigate();
  const [parametros, setParametros] = useSearchParams();
  const curso = useCarregar(() => api.get<Curso>(`/api/adm/cursos/${cursoId}`), [cursoId]);

  if (curso.erro && !curso.dados) {
    return (
      <div className="pc-pagina">
        <Alerta severidade="err" titulo="Não foi possível abrir o curso" mensagem={curso.erro} />
      </div>
    );
  }
  if (!curso.dados) return <Carregando mensagem="Carregando o curso..." />;

  return (
    <div className="pc-pagina">
      <Abas
        titulo={curso.dados.titulo}
        aoVoltar={() => navigate(CAMINHOS.cursos)}
        ativa={parametros.get('aba') ?? 'dados'}
        aoMudar={(aba) => setParametros({ aba }, { replace: true })}
        rotulo="Seções do curso"
      >
        <Aba valor="dados" rotulo="Dados" icone="file-text">
          <DadosCursoAba curso={curso.dados} aoMudar={curso.definir} />
        </Aba>
        <Aba valor="turmas" rotulo="Turmas" icone="users">
          <TurmasAba curso={curso.dados} />
        </Aba>
      </Abas>
    </div>
  );
}
