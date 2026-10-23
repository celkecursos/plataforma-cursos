import { Link, useLoaderData, type LoaderFunctionArgs, type MetaFunction } from 'react-router';
import { curso as buscarCurso } from '../api.server.ts';
import { Icone } from '../componentes/Icone.tsx';
import { useContexto } from '../root.tsx';
import {
  dadosCurso,
  jsonEmScript,
  metaTags,
  paragrafos,
  plural,
  preco,
  type Contexto,
  type CursoDetalhe,
} from '../seo.ts';
import { cachePublico, contextoDe } from './comum.ts';

export async function loader({ params }: LoaderFunctionArgs) {
  return { curso: await buscarCurso(params['slug'] ?? '') };
}

export const headers = cachePublico;

export const meta: MetaFunction<typeof loader> = ({ loaderData, matches }) =>
  loaderData
    ? metaTags(contextoDe(matches), {
        caminho: `/cursos/${loaderData.curso.slug}/`,
        titulo: loaderData.curso.titulo,
        descricao: loaderData.curso.resumo,
        imagem: loaderData.curso.capaUrl,
      })
    : [];

/** Preço e "Comprar" de cada oferta; sem oferta, o convite para criar a conta. */
function Compra({ ctx, curso }: { ctx: Contexto; curso: CursoDetalhe }) {
  const ofertas = curso.ofertas ?? [];
  if (ofertas.length === 0) {
    return (
      <div className="s-compra-acoes">
        <a className="s-botao s-botao-grande s-botao-bloco" href={`${ctx.alunoUrl}/cadastro`}>
          Quero fazer este curso
        </a>
        <a className="s-botao s-botao-contorno s-botao-bloco" href={`${ctx.alunoUrl}/entrar`}>
          Já sou aluno
        </a>
      </div>
    );
  }
  return (
    <>
      <ul className="s-ofertas" role="list">
        {ofertas.map((o) => (
          <li key={o.id} className="s-oferta">
            <span className="s-oferta-titulo">{o.titulo}</span>
            <strong className="s-oferta-preco">{preco(o)}</strong>
            <a className="s-botao s-botao-bloco" href={`${ctx.alunoUrl}/comprar/${o.id}`}>
              Comprar
            </a>
          </li>
        ))}
      </ul>
      <p className="s-detalhe">
        Já é aluno? <a href={`${ctx.alunoUrl}/entrar`}>Entre na sua conta</a>.
      </p>
    </>
  );
}

export default function Curso() {
  const { curso } = useLoaderData<typeof loader>();
  const ctx = useContexto();
  const totalAulas = curso.modulos.reduce((total, m) => total + m.aulas.length, 0);
  return (
    <article>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonEmScript(dadosCurso(ctx, curso)) }}
      />
      <header className="s-cabeca-pagina">
        <div className="s-largura">
          <nav aria-label="Trilha" className="s-trilha">
            <Link to="/cursos/">Cursos</Link> <span aria-hidden="true">›</span>{' '}
            <span>{curso.titulo}</span>
          </nav>
          <h1>{curso.titulo}</h1>
          <p className="s-heroi-texto">{curso.resumo}</p>
          <ul className="s-fichas" role="list">
            <li>
              <Icone nome="camadas" className="s-icone-pequeno" />{' '}
              {plural(curso.modulos.length, 'módulo', 'módulos')}
            </li>
            <li>
              <Icone nome="play" className="s-icone-pequeno" />{' '}
              {plural(totalAulas, 'aula', 'aulas')}
            </li>
            <li>
              <Icone nome="certificado" className="s-icone-pequeno" /> Certificado
            </li>
          </ul>
        </div>
      </header>
      <div className="s-largura s-curso-grade">
        <aside className="s-compra" aria-label="Matrícula">
          {curso.capaUrl ? (
            <img
              className="s-curso-capa"
              src={curso.capaUrl}
              alt={`Capa do curso ${curso.titulo}`}
              width={640}
              height={360}
            />
          ) : (
            <span className="s-curso-capa s-sem-capa">
              <Icone nome="play" className="s-sem-capa-icone" />
            </span>
          )}
          <div className="s-compra-corpo">
            <Compra ctx={ctx} curso={curso} />
            <ul className="s-inclui" role="list">
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Aulas em vídeo no seu ritmo
              </li>
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Materiais de apoio para baixar
              </li>
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Fórum de dúvidas com o instrutor
              </li>
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Certificado com código de
                validação
              </li>
            </ul>
          </div>
        </aside>
        <div className="s-curso-conteudo">
          {curso.descricao && (
            <section aria-labelledby="titulo-sobre" className="s-bloco">
              <h2 id="titulo-sobre">Sobre o curso</h2>
              <div className="s-texto">
                {paragrafos(curso.descricao).map((p, i) => (
                  <p key={i}>{p}</p>
                ))}
              </div>
            </section>
          )}
          <section aria-labelledby="titulo-conteudo" className="s-bloco">
            <h2 id="titulo-conteudo">Conteúdo</h2>
            {curso.modulos.length === 0 ? (
              <p className="s-vazio">O conteúdo deste curso está em preparação.</p>
            ) : (
              <ol className="s-modulos">
                {curso.modulos.map((m, i) => (
                  <li key={i} className="s-modulo">
                    <div className="s-modulo-topo">
                      <span className="s-modulo-numero" aria-hidden="true">
                        {String(i + 1).padStart(2, '0')}
                      </span>
                      <h3>{m.titulo}</h3>
                      <span className="s-modulo-qtd">
                        {plural(m.aulas.length, 'aula', 'aulas')}
                      </span>
                    </div>
                    <ol className="s-aulas">
                      {m.aulas.map((a) => (
                        <li key={a.id}>
                          <Icone nome="play" className="s-icone-pequeno" />
                          <span>{a.titulo}</span>
                          {a.gratuita && <span className="s-selo">Aula gratuita</span>}
                        </li>
                      ))}
                    </ol>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      </div>
    </article>
  );
}
