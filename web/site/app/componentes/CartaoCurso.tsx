import { Link } from 'react-router';
import type { CursoCatalogo } from '../seo.ts';
import { Icone } from './Icone.tsx';

export function CartaoCurso({ curso }: { curso: CursoCatalogo }) {
  return (
    <li className="s-cartao">
      <Link to={`/cursos/${curso.slug}/`} className="s-cartao-link">
        <span className="s-cartao-midia">
          {curso.capaUrl ? (
            <img
              className="s-cartao-capa"
              src={curso.capaUrl}
              alt=""
              width={640}
              height={360}
              loading="lazy"
            />
          ) : (
            <span className="s-cartao-capa s-sem-capa">
              <Icone nome="play" className="s-sem-capa-icone" />
            </span>
          )}
        </span>
        <span className="s-cartao-corpo">
          <span className="s-cartao-titulo">{curso.titulo}</span>
          <span className="s-cartao-resumo">{curso.resumo}</span>
          <span className="s-cartao-mais">
            Ver curso <Icone nome="seta" className="s-icone-pequeno" />
          </span>
        </span>
      </Link>
    </li>
  );
}

export function GradeCursos({ cursos }: { cursos: CursoCatalogo[] }) {
  if (cursos.length === 0) return <p className="s-vazio">Novos cursos em breve. Volte logo!</p>;
  return (
    <ul className="s-grade" role="list">
      {cursos.map((c) => (
        <CartaoCurso key={c.slug} curso={c} />
      ))}
    </ul>
  );
}
