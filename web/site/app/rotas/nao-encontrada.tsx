import { data } from 'react-router';

/** Qualquer endereço sem rota: a página "não encontrada" da raiz, com status 404. */
export function loader() {
  throw data('Não encontrado', { status: 404 });
}

/** Envio para um endereço sem rota (ex.: uma rota privada da API pelo domínio do site): 404 também. */
export function action() {
  throw data('Não encontrado', { status: 404 });
}

export default function NaoEncontrada() {
  return null;
}
