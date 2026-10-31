import { Badge, Card, Vazio } from '@celke/kole-react';
import { Link } from 'react-router';
import { caminhoAula } from '../../caminhos.ts';
import type { Progresso, ProgressoTurma, TurmaAluno } from '../../tipos.ts';
import { ArquivosModulo } from './ArquivosModulo.tsx';

/** Módulos e aulas da turma, com o que já foi visto e o que ficou para revisar. */
export function ConteudoTurma({
  turma,
  progresso,
}: {
  turma: TurmaAluno;
  progresso?: ProgressoTurma;
}) {
  const porAula = new Map<number, Progresso>((progresso?.aulas ?? []).map((p) => [p.aulaId, p]));

  if (turma.modulos.length === 0) {
    return (
      <Vazio
        titulo="Conteúdo em preparação"
        descricao="As aulas aparecem aqui assim que forem publicadas."
        icone="layers"
      />
    );
  }

  return (
    <div className="pc-pilha">
      {turma.modulos.map((modulo, indice) => (
        <Card
          key={modulo.id}
          titulo={`${indice + 1}. ${modulo.titulo}`}
          aria-label={`Módulo ${modulo.titulo}`}
        >
          <ol className="pc-lista-aulas" aria-label={`Aulas de ${modulo.titulo}`}>
            {modulo.aulas.map((aula) => {
              const p = porAula.get(aula.id);
              return (
                <li key={aula.id}>
                  <Link className="pc-link" to={caminhoAula(turma.turmaId, aula.id)}>
                    {aula.titulo}
                  </Link>
                  {p?.vista && <Badge cor="ok" icone="check" rotulo="Vista" aparencia="suave" />}
                  {p?.revisar && <Badge cor="warn" rotulo="Revisar" aparencia="suave" />}
                </li>
              );
            })}
          </ol>
          <ArquivosModulo moduloId={modulo.id} />
        </Card>
      ))}
    </div>
  );
}
