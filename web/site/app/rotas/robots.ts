import { contexto } from '../config.server.ts';
import { robots } from '../seo.ts';

export function loader() {
  return new Response(robots(contexto()), {
    headers: {
      'Content-Type': 'text/plain; charset=utf-8',
      'Cache-Control': 'public, max-age=3600',
    },
  });
}
