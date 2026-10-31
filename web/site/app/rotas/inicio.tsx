import { Link, useLoaderData, type MetaFunction } from 'react-router';
import { cursos } from '../api.server.ts';
import { GradeCursos } from '../componentes/CartaoCurso.tsx';
import { Icone, type NomeIcone } from '../componentes/Icone.tsx';
import { useContexto } from '../root.tsx';
import { metaTags } from '../seo.ts';
import { cachePublico, contextoDe } from './comum.ts';

export async function loader() {
  const todos = await cursos();
  return { destaques: todos.slice(0, 6), total: todos.length };
}

export const headers = cachePublico;

export const meta: MetaFunction = ({ matches }) =>
  metaTags(contextoDe(matches), {
    caminho: '/',
    titulo: 'Aprenda no seu ritmo',
    descricao:
      'Cursos online com aulas em vídeo, materiais de apoio, fórum com o instrutor e certificado.',
  });

const VANTAGENS: { icone: NomeIcone; titulo: string; texto: string }[] = [
  {
    icone: 'play',
    titulo: 'Aulas em vídeo',
    texto: 'Assista quando e onde quiser e continue exatamente de onde parou.',
  },
  {
    icone: 'livro',
    titulo: 'Materiais de apoio',
    texto: 'Arquivos e exercícios de cada aula para baixar e praticar.',
  },
  {
    icone: 'conversa',
    titulo: 'Fórum com o instrutor',
    texto: 'Tire as dúvidas de cada aula com quem preparou o conteúdo.',
  },
  {
    icone: 'certificado',
    titulo: 'Certificado',
    texto: 'Conclua o curso e receba o certificado com código de validação.',
  },
];

export default function Inicio() {
  const { destaques, total } = useLoaderData<typeof loader>();
  const ctx = useContexto();
  return (
    <>
      <section className="s-heroi">
        <div className="s-largura s-heroi-grade">
          <div>
            <p className="s-rotulo">Cursos online com certificado</p>
            <h1>Aprenda no seu ritmo</h1>
            <p className="s-heroi-texto">
              Aulas em vídeo para assistir de qualquer lugar, materiais de apoio, dúvidas
              respondidas pelo instrutor e certificado com código de validação.
            </p>
            <p className="s-acoes">
              <Link className="s-botao s-botao-grande" to="/cursos/">
                Ver os cursos <Icone nome="seta" className="s-icone-pequeno" />
              </Link>
              <a className="s-botao s-botao-vidro s-botao-grande" href={`${ctx.alunoUrl}/cadastro`}>
                Criar conta
              </a>
            </p>
            <ul className="s-heroi-selos" role="list">
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Computador ou celular
              </li>
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Progresso salvo
              </li>
              <li>
                <Icone nome="check" className="s-icone-pequeno" /> Certificado verificável
              </li>
            </ul>
          </div>
          <div className="s-heroi-ilustracao" aria-hidden="true">
            <div className="s-tela">
              <div className="s-tela-video">
                <Icone nome="play" className="s-tela-play" />
              </div>
              <div className="s-tela-barra">
                <span />
              </div>
              <div className="s-tela-aulas">
                <span className="s-tela-aula s-tela-vista" />
                <span className="s-tela-aula s-tela-vista" />
                <span className="s-tela-aula s-tela-atual" />
                <span className="s-tela-aula" />
              </div>
            </div>
            <div className="s-tela-selo">
              <Icone nome="certificado" className="s-icone-pequeno" /> Certificado emitido
            </div>
          </div>
        </div>
      </section>

      <section className="s-largura s-secao" aria-labelledby="titulo-vantagens">
        <h2 id="titulo-vantagens" className="s-titulo-secao">
          Tudo o que você precisa para aprender
        </h2>
        <ul className="s-vantagens" role="list">
          {VANTAGENS.map((v) => (
            <li key={v.titulo}>
              <span className="s-vantagem-icone">
                <Icone nome={v.icone} />
              </span>
              <h3>{v.titulo}</h3>
              <p>{v.texto}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="s-faixa" aria-labelledby="titulo-cursos">
        <div className="s-largura s-secao">
          <div className="s-cabecalho-secao">
            <h2 id="titulo-cursos" className="s-titulo-secao">
              Cursos
            </h2>
            {total > destaques.length && <Link to="/cursos/">Ver todos os {total} cursos</Link>}
          </div>
          <GradeCursos cursos={destaques} />
        </div>
      </section>

      <section className="s-largura s-secao" aria-labelledby="titulo-como">
        <h2 id="titulo-como" className="s-titulo-secao">
          Como funciona
        </h2>
        <ol className="s-passos">
          <li>
            <strong>Crie a sua conta</strong> e confirme o e-mail.
          </li>
          <li>
            <strong>Matricule-se</strong> no curso e acesse as aulas pela área do aluno.
          </li>
          <li>
            <strong>Assista no seu tempo:</strong> o progresso fica salvo e você continua de onde
            parou.
          </li>
          <li>
            <strong>Conclua e emita o certificado</strong>, que qualquer pessoa pode validar aqui no
            site.
          </li>
        </ol>
      </section>

      <section className="s-largura s-secao-final" aria-labelledby="titulo-chamada">
        <div className="s-chamada">
          <div>
            <h2 id="titulo-chamada">Pronto para começar?</h2>
            <p>Escolha um curso e comece a estudar hoje mesmo.</p>
          </div>
          <Link className="s-botao s-botao-claro s-botao-grande" to="/cursos/">
            Escolher um curso
          </Link>
        </div>
      </section>
    </>
  );
}
