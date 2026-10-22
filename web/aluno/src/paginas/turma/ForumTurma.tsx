import type { TurmaAluno } from '../../tipos.ts';
import { ForumLista } from '../forum/ForumLista.tsx';

export function ForumTurma({ turma }: { turma: TurmaAluno }) {
  return <ForumLista turmaId={turma.turmaId} />;
}
