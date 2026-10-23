import {
  Form,
  useLoaderData,
  useNavigation,
  type LoaderFunctionArgs,
  type MetaFunction,
} from 'react-router';
import { certificado } from '../api.server.ts';
import { Icone } from '../componentes/Icone.tsx';
import { metaTags, type Certificado } from '../seo.ts';
import { contextoDe } from './comum.ts';

type Resultado =
  | { estado: 'inicial'; codigo: '' }
  | { estado: 'vazio'; codigo: '' }
  | { estado: 'valido'; codigo: string; certificado: Certificado }
  | { estado: 'nao-encontrado' | 'falha'; codigo: string };

/** O código vem na URL (?codigo=): o link impresso no certificado já abre validado. */
export async function loader({ request }: LoaderFunctionArgs): Promise<Resultado> {
  const parametro = new URL(request.url).searchParams.get('codigo');
  if (parametro === null) return { estado: 'inicial', codigo: '' };
  const codigo = parametro.trim();
  if (!codigo) return { estado: 'vazio', codigo: '' };
  try {
    const encontrado = await certificado(codigo);
    return encontrado
      ? { estado: 'valido', codigo, certificado: encontrado }
      : { estado: 'nao-encontrado', codigo };
  } catch {
    return { estado: 'falha', codigo };
  }
}

export const meta: MetaFunction = ({ matches }) =>
  metaTags(contextoDe(matches), {
    caminho: '/certificados/validar/',
    titulo: 'Validar certificado',
    descricao:
      'Confira se um certificado foi emitido pela plataforma usando o código impresso nele.',
  });

function Detalhes({ c }: { c: Certificado }) {
  const linhas: [string, string][] = [
    ['Aluno', c.nomeCompleto],
    ['Curso', c.cursoTitulo],
    ['Turma', c.turmaNome],
    [
      'Emitido em',
      new Date(c.emitidoEm).toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' }),
    ],
  ];
  if (c.cargaHorariaHoras != null) linhas.push(['Carga horária', `${c.cargaHorariaHoras} h`]);
  return (
    <dl>
      {linhas.map(([rotulo, valor]) => (
        <div key={rotulo}>
          <dt>{rotulo}</dt>
          <dd>{valor}</dd>
        </div>
      ))}
    </dl>
  );
}

function Mensagem({ r }: { r: Resultado }) {
  switch (r.estado) {
    case 'inicial':
      return null;
    case 'vazio':
      return (
        <div className="s-resultado s-resultado-erro">
          <strong>Informe o código do certificado.</strong>
        </div>
      );
    case 'valido':
      return (
        <div className="s-resultado s-resultado-ok">
          <strong>Certificado válido.</strong>
          <Detalhes c={r.certificado} />
        </div>
      );
    case 'nao-encontrado':
      return (
        <div className="s-resultado s-resultado-erro">
          <strong>Certificado não encontrado.</strong>
          <p>Confira o código digitado.</p>
        </div>
      );
    case 'falha':
      return (
        <div className="s-resultado s-resultado-erro">
          <strong>Não foi possível consultar agora.</strong>
          <p>Tente de novo em instantes.</p>
        </div>
      );
  }
}

export default function Validar() {
  const resultado = useLoaderData<typeof loader>();
  const consultando = useNavigation().state !== 'idle';
  return (
    <div className="s-largura s-secao s-estreito">
      <div className="s-painel">
        <span className="s-vantagem-icone">
          <Icone nome="escudo" />
        </span>
        <h1>Validar certificado</h1>
        <p>Informe o código impresso no certificado para conferir os dados dele.</p>
        <Form method="get" action="/certificados/validar/" className="s-form" noValidate>
          <label htmlFor="codigo">Código do certificado</label>
          <input
            id="codigo"
            name="codigo"
            autoComplete="off"
            maxLength={64}
            defaultValue={resultado.codigo}
            key={resultado.codigo}
          />
          <button className="s-botao" type="submit" disabled={consultando}>
            {consultando ? 'Consultando...' : 'Validar'}
          </button>
        </Form>
        <div role="status" aria-live="polite">
          <Mensagem r={resultado} />
        </div>
      </div>
    </div>
  );
}
