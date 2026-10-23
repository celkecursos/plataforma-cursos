import { useState } from 'react';
import { useLoaderData, type MetaFunction } from 'react-router';
import { cursos } from '../api.server.ts';
import { GradeCursos } from '../componentes/CartaoCurso.tsx';
import { Icone } from '../componentes/Icone.tsx';
import { filtrarCursos, metaTags, NOME, plural } from '../seo.ts';
import { cachePublico, contextoDe } from './comum.ts';

export async function loader() {
  return { cursos: await cursos() };
}

export const headers = cachePublico;

export const meta: MetaFunction<typeof loader> = ({ loaderData, matches }) =>
  metaTags(contextoDe(matches), {
    caminho: '/cursos/',
    titulo: 'Cursos',
    descricao: `Conheça os ${loaderData?.cursos.length ?? 0} cursos disponíveis na ${NOME}.`,
  });

/** Catálogo: o HTML já vem com todos os cursos (para os buscadores); a busca filtra no navegador. */
export default function Cursos() {
  const { cursos } = useLoaderData<typeof loader>();
  const [busca, setBusca] = useState('');
  const encontrados = filtrarCursos(cursos, busca);
  return (
    <>
      <section className="s-cabeca-pagina">
        <div className="s-largura">
          <h1>Cursos</h1>
          <p className="s-heroi-texto">
            {plural(cursos.length, 'curso disponível', 'cursos disponíveis')} para você estudar no
            seu ritmo.
          </p>
          {cursos.length > 0 && (
            <div className="s-busca">
              <Icone nome="busca" className="s-busca-icone" />
              <label htmlFor="busca" className="s-visivel-leitor">
                Buscar curso
              </label>
              <input
                id="busca"
                type="search"
                placeholder="Buscar por nome ou assunto"
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
                autoComplete="off"
              />
            </div>
          )}
        </div>
      </section>
      <div className="s-largura s-secao">
        {busca.trim() && (
          <p className="s-detalhe" role="status">
            {encontrados.length === 0
              ? 'Nenhum curso encontrado para essa busca.'
              : plural(encontrados.length, 'curso encontrado', 'cursos encontrados')}
          </p>
        )}
        {(encontrados.length > 0 || !busca.trim()) && <GradeCursos cursos={encontrados} />}
      </div>
    </>
  );
}
