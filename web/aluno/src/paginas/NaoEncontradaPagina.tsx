import { Link } from 'react-router';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { CAMINHOS } from '../caminhos.ts';

export function NaoEncontradaPagina() {
  return (
    <LayoutConta
      marca="Plataforma de Cursos"
      titulo="Página não encontrada"
      descricao="O endereço não existe ou mudou."
    >
      <Link className="pc-link" to={CAMINHOS.inicio}>
        Ir para meus cursos
      </Link>
    </LayoutConta>
  );
}
