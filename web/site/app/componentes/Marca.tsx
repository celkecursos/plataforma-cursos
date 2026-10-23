import { Link } from 'react-router';
import { NOME } from '../seo.ts';

export function Marca() {
  return (
    <Link className="s-marca" to="/">
      <img src="/icone.svg" alt="" width={32} height={32} />
      <span>{NOME}</span>
    </Link>
  );
}
