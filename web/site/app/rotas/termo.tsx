import { useLoaderData, type LoaderFunctionArgs, type MetaFunction } from 'react-router';
import { termosVigentes } from '../api.server.ts';
import { metaTags, NOME, type Termo } from '../seo.ts';
import { cachePublico, contextoDe } from './comum.ts';

const PAGINAS = {
  termos: { caminho: '/termos/', titulo: 'Termos de uso', tipo: 'TERMOS_USO' },
  privacidade: {
    caminho: '/privacidade/',
    titulo: 'Política de privacidade',
    tipo: 'POLITICA_PRIVACIDADE',
  },
} as const;

function pagina(url: string) {
  return new URL(url).pathname.startsWith('/privacidade') ? PAGINAS.privacidade : PAGINAS.termos;
}

export async function loader({ request }: LoaderFunctionArgs) {
  const p = pagina(request.url);
  const termo: Termo | undefined = (await termosVigentes()).find((t) => t.tipo === p.tipo);
  return { titulo: p.titulo, caminho: p.caminho, termo: termo ?? null };
}

export const headers = cachePublico;

export const meta: MetaFunction<typeof loader> = ({ loaderData, matches }) =>
  loaderData
    ? metaTags(contextoDe(matches), {
        caminho: loaderData.caminho,
        titulo: loaderData.titulo,
        descricao: `${loaderData.titulo} da ${NOME}.`,
      })
    : [];

export default function TermoPagina() {
  const { titulo, termo } = useLoaderData<typeof loader>();
  return (
    <article className="s-largura s-secao s-estreito">
      <div className="s-painel s-texto">
        <h1>{titulo}</h1>
        {termo ? (
          <>
            <p className="s-detalhe">
              Versão {termo.versao}, publicada em{' '}
              {new Date(termo.publicadoEm).toLocaleDateString('pt-BR', {
                timeZone: 'America/Sao_Paulo',
              })}
              .
            </p>
            {/* HTML já filtrado pela API (Markdown com HTML escapado e lista fechada de tags) */}
            <div dangerouslySetInnerHTML={{ __html: termo.conteudoHtml }} />
          </>
        ) : (
          <p className="s-vazio">Em breve.</p>
        )}
      </div>
    </article>
  );
}
