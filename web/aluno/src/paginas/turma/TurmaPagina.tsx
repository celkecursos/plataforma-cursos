import { Aba, Abas, Alerta, Carregando, Progresso } from '@celke/kole-react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { CAMINHOS } from '../../caminhos.ts';
import type { ProgressoTurma, TurmaAluno } from '../../tipos.ts';
import { CertificadoAba } from './CertificadoAba.tsx';
import { ConteudoTurma } from './ConteudoTurma.tsx';
import { ForumTurma } from './ForumTurma.tsx';

/** A turma do aluno: conteúdo com o progresso, fórum e certificado. A aba fica no endereço (?aba=). */
export function TurmaPagina() {
  const { turmaId } = useParams();
  const navigate = useNavigate();
  const [parametros, setParametros] = useSearchParams();
  const turma = useCarregar(() => api.get<TurmaAluno>(`/api/aluno/turmas/${turmaId}`), [turmaId]);
  const progresso = useCarregar(
    () => api.get<ProgressoTurma>(`/api/aluno/turmas/${turmaId}/progresso`),
    [turmaId],
  );

  if (turma.erro && !turma.dados) {
    return (
      <div className="pc-pagina">
        <Alerta severidade="err" titulo="Não foi possível abrir o curso" mensagem={turma.erro} />
      </div>
    );
  }
  if (!turma.dados) return <Carregando mensagem="Carregando o curso..." />;
  const t = turma.dados;
  const p = progresso.dados;

  return (
    <div className="pc-pagina">
      <Abas
        titulo={t.cursoTitulo}
        identidade={t.turmaNome}
        aoVoltar={() => navigate(CAMINHOS.inicio)}
        ativa={parametros.get('aba') ?? 'conteudo'}
        aoMudar={(aba) => setParametros({ aba }, { replace: true })}
        rotulo="Seções do curso"
      >
        <Aba valor="conteudo" rotulo="Conteúdo" icone="layers">
          <div className="pc-pilha">
            {p && (
              <div className="pc-progresso-turma">
                <Progresso
                  rotulo="Progresso no curso"
                  valor={p.percentual}
                  mostrarValor="fim"
                  cor={p.percentual === 100 ? 'ok' : 'pri'}
                />
                <span className="pc-dica">
                  {p.aulasVistas} de {p.totalAulas} aulas vistas
                </span>
              </div>
            )}
            <ConteudoTurma turma={t} progresso={p} />
          </div>
        </Aba>
        <Aba valor="forum" rotulo="Fórum" icone="message-square">
          <ForumTurma turma={t} />
        </Aba>
        <Aba valor="certificado" rotulo="Certificado" icone="award">
          <CertificadoAba turmaId={t.turmaId} />
        </Aba>
      </Abas>
    </div>
  );
}
