import { cursos } from '../api.server.ts';
import { contexto } from '../config.server.ts';
import { sitemap } from '../seo.ts';

export async function loader() {
  return new Response(sitemap(contexto(), await cursos()), {
    headers: {
      'Content-Type': 'application/xml; charset=utf-8',
      'Cache-Control': 'public, max-age=300',
    },
  });
}
