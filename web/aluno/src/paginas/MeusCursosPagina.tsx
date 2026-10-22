import { Alerta, CabecalhoPagina, Card, Carregando, Status, Vazio } from '@celke/kole-react';
import { useEffect, useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';

export interface MeuCurso {
  matriculaId: number;
  turmaId: number;
  cursoTitulo: string;
  cursoSlug: string;
  turmaNome: string;
  capaUrl: string | null;
  inicio: string;
  fim: string | null;
  situacao: 'AGUARDANDO_INICIO' | 'VIGENTE' | 'VENCIDA' | 'CANCELADA';
  arquivada: boolean;
}

/** Cursos do aluno (aba de ativos). */
export function MeusCursosPagina() {
  const [cursos, setCursos] = useState<MeuCurso[] | null>(null);
  const [erro, setErro] = useState<string>();

  useEffect(() => {
    api
      .get<MeuCurso[]>('/api/aluno/cursos')
      .then(setCursos)
      .catch((falha: unknown) => setErro(mensagemDeErro(falha)));
  }, []);

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Meus cursos" icone="graduation-cap" />
      {erro && (
        <Alerta severidade="err" titulo="Não foi possível carregar seus cursos" mensagem={erro} />
      )}
      {!erro && cursos === null && <Carregando mensagem="Carregando seus cursos..." />}
      {cursos?.length === 0 && (
        <Vazio
          titulo="Você ainda não tem cursos"
          descricao="Quando a sua matrícula for confirmada, o curso aparece aqui."
          icone="book-open"
        />
      )}
      {!!cursos?.length && (
        <div className="pc-grade-cards">
          {cursos.map((curso) => (
            <Card key={curso.matriculaId} titulo={curso.cursoTitulo} subtitulo={curso.turmaNome}>
              <Status
                rotulo={curso.situacao === 'VIGENTE' ? 'Em andamento' : 'Começa em breve'}
                severidade={curso.situacao === 'VIGENTE' ? 'ok' : 'info'}
              />
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
