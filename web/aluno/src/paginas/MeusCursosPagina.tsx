import {
  Aba,
  Abas,
  Alerta,
  Botao,
  CabecalhoPagina,
  Card,
  Carregando,
  Imagem,
  Status,
  Vazio,
} from '@celke/kole-react';
import { useNavigate, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { data } from '@compartilhado/ui/formato.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { caminhoTurma } from '../caminhos.ts';
import type { FiltroMeusCursos, MeuCurso } from '../tipos.ts';

const ABAS: { valor: FiltroMeusCursos; rotulo: string; vazio: string }[] = [
  {
    valor: 'ATIVOS',
    rotulo: 'Ativos',
    vazio: 'Quando a sua matrícula for confirmada, o curso aparece aqui.',
  },
  {
    valor: 'ARQUIVADOS',
    rotulo: 'Arquivados',
    vazio: 'Cursos que você arquivar ficam aqui; o acesso não muda.',
  },
  {
    valor: 'EXPIRADOS',
    rotulo: 'Expirados',
    vazio: 'Cursos com o período de acesso encerrado aparecem aqui.',
  },
];

/** Cursos do aluno, por aba. A aba fica no endereço (?aba=). */
export function MeusCursosPagina() {
  const [parametros, setParametros] = useSearchParams();
  const aba = (parametros.get('aba') as FiltroMeusCursos | null) ?? 'ATIVOS';

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Meus cursos" icone="graduation-cap" />
      <Abas
        ativa={aba}
        aoMudar={(a) => setParametros({ aba: a }, { replace: true })}
        rotulo="Situação dos cursos"
      >
        {ABAS.map((a) => (
          <Aba key={a.valor} valor={a.valor} rotulo={a.rotulo}>
            {a.valor === aba && <ListaCursos filtro={a.valor} vazio={a.vazio} />}
          </Aba>
        ))}
      </Abas>
    </div>
  );
}

function ListaCursos({ filtro, vazio }: { filtro: FiltroMeusCursos; vazio: string }) {
  const navigate = useNavigate();
  const { executar, executando } = useAcao();
  const cursos = useCarregar(
    () => api.get<MeuCurso[]>(`/api/aluno/cursos?situacao=${filtro}`),
    [filtro],
  );

  if (cursos.erro && !cursos.dados) {
    return (
      <Alerta
        severidade="err"
        titulo="Não foi possível carregar seus cursos"
        mensagem={cursos.erro}
      />
    );
  }
  if (!cursos.dados) return <Carregando mensagem="Carregando seus cursos..." />;
  if (cursos.dados.length === 0) {
    return (
      <Vazio
        titulo={filtro === 'ATIVOS' ? 'Você ainda não tem cursos' : 'Nenhum curso aqui'}
        descricao={vazio}
        icone="book-open"
      />
    );
  }

  return (
    <div className="pc-grade-cards">
      {cursos.dados.map((curso) => {
        const vigente = curso.situacao === 'VIGENTE';
        const aguardando = curso.situacao === 'AGUARDANDO_INICIO';
        return (
          <Card
            key={curso.matriculaId}
            titulo={curso.cursoTitulo}
            subtitulo={curso.turmaNome}
            aria-label={curso.cursoTitulo}
            midia={
              curso.capaUrl ? <Imagem src={curso.capaUrl} alt="" proporcao="16 / 9" /> : undefined
            }
            rodape={
              <div className="pc-linha">
                {vigente && (
                  <Botao
                    rotulo="Continuar"
                    icone="circle-play"
                    tamanho="pequeno"
                    onClick={() => navigate(caminhoTurma(curso.turmaId))}
                  />
                )}
                {filtro !== 'EXPIRADOS' && (
                  <Botao
                    rotulo={curso.arquivada ? 'Desarquivar' : 'Arquivar'}
                    aparencia="texto"
                    tamanho="pequeno"
                    disabled={executando}
                    onClick={async () => {
                      const rota = `/api/aluno/matriculas/${curso.matriculaId}/arquivamento`;
                      const ok = await executar(
                        () => (curso.arquivada ? api.delete(rota) : api.put(rota)).then(() => true),
                        curso.arquivada ? 'Curso de volta aos ativos' : 'Curso arquivado',
                      );
                      if (ok) cursos.recarregar();
                    }}
                  />
                )}
              </div>
            }
          >
            <div className="pc-linha">
              <Status
                rotulo={vigente ? 'Em andamento' : aguardando ? 'Começa em breve' : 'Encerrado'}
                severidade={vigente ? 'ok' : aguardando ? 'info' : 'neutral'}
              />
              <span className="pc-dica">
                {aguardando
                  ? `Começa em ${data(curso.inicio)}`
                  : curso.fim
                    ? `Acesso até ${data(curso.fim)}`
                    : 'Acesso sem prazo'}
              </span>
            </div>
          </Card>
        );
      })}
    </div>
  );
}
