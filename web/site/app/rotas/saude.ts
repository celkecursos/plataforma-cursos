/** Health check do container (não depende da API: o site sobe mesmo com ela fora). */
export function loader() {
  return new Response('ok', {
    headers: { 'Content-Type': 'text/plain', 'Cache-Control': 'no-store' },
  });
}
