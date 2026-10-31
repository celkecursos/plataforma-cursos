/**
 * Cria cursos de exemplo publicados (com turma ativa, módulos, aulas e capa) pela API do ADM, para ver
 * o site e a área do aluno com conteúdo. Pede o e-mail, a senha e o código do aplicativo autenticador
 * de um administrador. Pode rodar de novo: curso que já existe (mesmo endereço) é pulado.
 *
 *   npm run cursos-exemplo -- https://homolog-adm.celke.uno
 *   npm run cursos-exemplo -- http://adm.localhost:8088   (stack de e2e)
 */
import { stdin, stdout } from 'node:process';
import { createInterface } from 'node:readline/promises';
import { crc32, deflateSync } from 'node:zlib';

interface Exemplo {
  slug: string;
  titulo: string;
  resumo: string;
  descricao: string;
  cores: [string, string];
  cargaHoras: number;
  modulos: { titulo: string; aulas: string[] }[];
}

const EXEMPLOS: Exemplo[] = [
  {
    slug: 'java-do-zero-ao-avancado',
    titulo: 'Java do zero ao avançado',
    resumo: 'Aprenda a programar em Java, da lógica à orientação a objetos, com projetos práticos.',
    descricao:
      'Um curso completo para quem quer começar a programar com uma das linguagens mais usadas no mercado.\n\nVocê sai do primeiro programa até coleções, tratamento de erros e testes, sempre com exercícios para fixar.',
    cores: ['#1f5fe0', '#6a3df0'],
    cargaHoras: 40,
    modulos: [
      {
        titulo: 'Primeiros passos',
        aulas: ['Instalando o JDK e a IDE', 'Seu primeiro programa', 'Variáveis e tipos'],
      },
      {
        titulo: 'Lógica de programação',
        aulas: ['Condicionais', 'Laços de repetição', 'Métodos', 'Exercícios comentados'],
      },
      {
        titulo: 'Orientação a objetos',
        aulas: ['Classes e objetos', 'Herança e interfaces', 'Coleções', 'Projeto final'],
      },
    ],
  },
  {
    slug: 'react-com-typescript-na-pratica',
    titulo: 'React com TypeScript na prática',
    resumo: 'Construa interfaces modernas com componentes, hooks e tipagem do começo ao fim.',
    descricao:
      'Do componente ao projeto publicado: estado, efeitos, formulários, rotas e chamadas à API, com TypeScript em tudo.\n\nIdeal para quem já conhece JavaScript e quer dar o próximo passo.',
    cores: ['#0ea5b7', '#1f5fe0'],
    cargaHoras: 30,
    modulos: [
      {
        titulo: 'Fundamentos',
        aulas: ['Criando o projeto com Vite', 'Componentes e props', 'Estado com useState'],
      },
      {
        titulo: 'Aplicação real',
        aulas: ['Rotas', 'Formulários', 'Consumindo uma API', 'Testes com Vitest'],
      },
      { titulo: 'Publicação', aulas: ['Build de produção', 'Deploy'] },
    ],
  },
  {
    slug: 'excel-para-o-dia-a-dia',
    titulo: 'Excel para o dia a dia',
    resumo: 'Planilhas, fórmulas, gráficos e tabelas dinâmicas para ganhar tempo no trabalho.',
    descricao:
      'Aprenda a organizar dados e automatizar tarefas repetitivas com as funções que mais aparecem no dia a dia.\n\nCada aula traz uma planilha de apoio para você praticar.',
    cores: ['#16a34a', '#0f766e'],
    cargaHoras: 16,
    modulos: [
      { titulo: 'Básico', aulas: ['Conhecendo a planilha', 'Formatação', 'Fórmulas simples'] },
      { titulo: 'Funções', aulas: ['SE e SOMASE', 'PROCX', 'Datas e textos'] },
      { titulo: 'Análise', aulas: ['Gráficos', 'Tabelas dinâmicas', 'Painel de indicadores'] },
    ],
  },
  {
    slug: 'marketing-digital-para-iniciantes',
    titulo: 'Marketing digital para iniciantes',
    resumo: 'Redes sociais, conteúdo e anúncios para atrair clientes pela internet.',
    descricao:
      'Entenda como planejar a presença on-line de um negócio, produzir conteúdo que engaja e medir os resultados.\n\nSem jargão: exemplos reais e um plano de ação ao final.',
    cores: ['#f7931e', '#e11d48'],
    cargaHoras: 20,
    modulos: [
      { titulo: 'Planejamento', aulas: ['Público e persona', 'Posicionamento', 'Metas'] },
      { titulo: 'Conteúdo', aulas: ['Calendário editorial', 'Instagram', 'Vídeos curtos'] },
      { titulo: 'Resultados', aulas: ['Anúncios', 'Métricas', 'Plano de ação'] },
    ],
  },
  {
    slug: 'fotografia-com-o-celular',
    titulo: 'Fotografia com o celular',
    resumo: 'Composição, luz e edição para fotos profissionais usando só o smartphone.',
    descricao:
      'Descubra como tirar fotos melhores com o equipamento que você já tem no bolso.\n\nVocê pratica composição, aproveita a luz natural e aprende a editar no próprio celular.',
    cores: ['#7c3aed', '#db2777'],
    cargaHoras: 12,
    modulos: [
      { titulo: 'O olhar', aulas: ['Regra dos terços', 'Linhas e enquadramento', 'Perspectiva'] },
      { titulo: 'Luz', aulas: ['Luz natural', 'Contraluz', 'Fotos à noite'] },
      { titulo: 'Edição', aulas: ['Ajustes básicos', 'Cor e estilo'] },
    ],
  },
  {
    slug: 'ingles-para-viagens',
    titulo: 'Inglês para viagens',
    resumo: 'Frases e situações para se virar no aeroporto, no hotel e no restaurante.',
    descricao:
      'Um curso direto ao ponto para quem vai viajar e quer se comunicar com confiança.\n\nCada módulo simula uma situação real, com vocabulário, pronúncia e diálogos.',
    cores: ['#0b1a36', '#1f5fe0'],
    cargaHoras: 10,
    modulos: [
      { titulo: 'No aeroporto', aulas: ['Check-in', 'Imigração', 'Perguntando o caminho'] },
      { titulo: 'No hotel', aulas: ['Reserva', 'Pedidos e problemas'] },
      { titulo: 'Passeios', aulas: ['No restaurante', 'Compras', 'Emergências'] },
    ],
  },
];

// ---------- capa: PNG de 1280 × 720 com degradê, sem dependências ----------

function rgb(hex: string): [number, number, number] {
  const n = Number.parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function bloco(tipo: string, dados: Buffer): Buffer {
  const tamanho = Buffer.alloc(4);
  tamanho.writeUInt32BE(dados.length);
  const tipoEDados = Buffer.concat([Buffer.from(tipo, 'ascii'), dados]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(tipoEDados));
  return Buffer.concat([tamanho, tipoEDados, crc]);
}

/** Degradê diagonal entre as duas cores, com dois círculos claros e suaves. */
export function capa([de, ate]: [string, string], largura = 1280, altura = 720): Buffer {
  const a = rgb(de);
  const b = rgb(ate);
  const linhas = Buffer.alloc((largura * 3 + 1) * altura);
  const circulos = [
    { x: largura * 0.82, y: altura * 0.18, r: altura * 0.55, forca: 0.22 },
    { x: largura * 0.12, y: altura * 0.95, r: altura * 0.45, forca: 0.12 },
  ];
  for (let y = 0; y < altura; y++) {
    const inicio = y * (largura * 3 + 1);
    linhas[inicio] = 0;
    for (let x = 0; x < largura; x++) {
      const t = (x / largura) * 0.6 + (y / altura) * 0.4;
      let luz = 0;
      for (const c of circulos) {
        const d = Math.hypot(x - c.x, y - c.y) / c.r;
        if (d < 1) luz += (1 - d) * (1 - d) * c.forca;
      }
      for (let canal = 0; canal < 3; canal++) {
        const base = a[canal]! + (b[canal]! - a[canal]!) * t;
        linhas[inicio + 1 + x * 3 + canal] = Math.min(255, Math.round(base + (255 - base) * luz));
      }
    }
  }
  const cabecalho = Buffer.alloc(13);
  cabecalho.writeUInt32BE(largura, 0);
  cabecalho.writeUInt32BE(altura, 4);
  cabecalho[8] = 8; // bits por canal
  cabecalho[9] = 2; // RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    bloco('IHDR', cabecalho),
    bloco('IDAT', deflateSync(linhas, { level: 9 })),
    bloco('IEND', Buffer.alloc(0)),
  ]);
}

// ---------- API ----------

let linhas: AsyncIterator<string> | undefined;

/** Uma resposta. No terminal, a senha (`oculto`) é lida tecla a tecla, sem eco; fora dele (entrada
 * redirecionada), cada resposta é uma linha. */
async function perguntar(texto: string, oculto = false): Promise<string> {
  if (!stdin.isTTY) {
    stdout.write(`${texto}\n`);
    linhas ??= createInterface({ input: stdin, terminal: false })[Symbol.asyncIterator]();
    return ((await linhas.next()).value ?? '').trim();
  }
  if (!oculto) {
    const rl = createInterface({ input: stdin, output: stdout });
    const resposta = await rl.question(texto);
    rl.close();
    return resposta.trim();
  }
  stdout.write(texto);
  stdin.setRawMode(true);
  stdin.resume();
  return new Promise((resolver) => {
    let valor = '';
    const tecla = (dado: Buffer) => {
      for (const c of dado.toString('utf8')) {
        if (c === '\r' || c === '\n') {
          stdin.setRawMode(false);
          stdin.pause();
          stdin.off('data', tecla);
          stdout.write('\n');
          resolver(valor);
          return;
        }
        if (c === '\u0003') process.exit(130);
        valor = c === '\u007f' ? valor.slice(0, -1) : valor + c;
      }
    };
    stdin.on('data', tecla);
  });
}

class Api {
  private readonly base: string;
  private token = '';

  constructor(base: string) {
    this.base = base;
  }

  async pedir<T>(metodo: string, caminho: string, corpo?: unknown): Promise<T> {
    const resposta = await fetch(this.base + caminho, {
      method: metodo,
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}),
      },
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
    const texto = await resposta.text();
    if (!resposta.ok) {
      const erro = new Error(`${metodo} ${caminho}: ${resposta.status} ${texto.slice(0, 300)}`);
      Object.assign(erro, { status: resposta.status });
      throw erro;
    }
    return (texto ? JSON.parse(texto) : undefined) as T;
  }

  async entrar(email: string, senha: string, codigo: () => Promise<string>): Promise<void> {
    const login = await this.pedir<{
      accessToken?: string;
      desafio?: string;
      segundoFator?: string;
    }>('POST', '/api/auth/login', { email, senha });
    if (login.accessToken) {
      this.token = login.accessToken;
      return;
    }
    if (login.segundoFator === 'CADASTRO') {
      throw new Error(
        'Entre uma vez no ADM pelo navegador para cadastrar o aplicativo autenticador.',
      );
    }
    const sessao = await this.pedir<{ accessToken: string }>('POST', '/api/auth/segundo-fator', {
      desafio: login.desafio,
      codigo: await codigo(),
    });
    this.token = sessao.accessToken;
  }
}

async function enviarCapa(api: Api, cursoId: number, png: Buffer): Promise<void> {
  const envio = await api.pedir<{ chave: string; url: string; contentType: string }>(
    'POST',
    `/api/adm/cursos/${cursoId}/capa/envio`,
    { contentType: 'image/png', tamanho: png.length },
  );
  const resposta = await fetch(envio.url, {
    method: 'PUT',
    headers: { 'Content-Type': envio.contentType },
    body: new Uint8Array(png),
  });
  if (!resposta.ok) throw new Error(`Envio da capa recusado: ${resposta.status}`);
  await api.pedir('PUT', `/api/adm/cursos/${cursoId}/capa`, { chave: envio.chave });
}

async function criar(api: Api, e: Exemplo): Promise<'criado' | 'existente'> {
  let curso: { id: number };
  try {
    curso = await api.pedir<{ id: number }>('POST', '/api/adm/cursos', {
      titulo: e.titulo,
      slug: e.slug,
      resumo: e.resumo,
      descricao: e.descricao,
    });
  } catch (erro) {
    if ((erro as { status?: number }).status === 409) return 'existente';
    throw erro;
  }
  const turma = await api.pedir<{ id: number }>('POST', `/api/adm/cursos/${curso.id}/turmas`, {
    nome: 'Turma de exemplo',
  });
  await api.pedir('PUT', `/api/adm/turmas/${turma.id}/certificado`, {
    percentualConclusao: 100,
    cargaHorariaHoras: e.cargaHoras,
  });
  await api.pedir('PATCH', `/api/adm/turmas/${turma.id}/status`, { status: 'ATIVA' });
  let primeira = true;
  for (const m of e.modulos) {
    const modulo = await api.pedir<{ id: number }>('POST', `/api/adm/turmas/${turma.id}/modulos`, {
      titulo: m.titulo,
    });
    for (const titulo of m.aulas) {
      await api.pedir('POST', `/api/adm/modulos/${modulo.id}/aulas`, {
        titulo,
        descricao: `Aula de exemplo do curso ${e.titulo}.`,
        gratuita: primeira,
      });
      primeira = false;
    }
  }
  await enviarCapa(api, curso.id, capa(e.cores));
  await api.pedir('PATCH', `/api/adm/cursos/${curso.id}/status`, { status: 'PUBLICADO' });
  return 'criado';
}

async function principal(): Promise<void> {
  const base = (process.argv[2] ?? '').replace(/\/$/, '');
  if (!/^https?:\/\//.test(base)) {
    console.error(
      'Uso: npm run cursos-exemplo -- <endereço do ADM, ex.: https://homolog-adm.celke.uno>',
    );
    process.exit(1);
  }
  const api = new Api(base);
  const email = await perguntar('E-mail do administrador: ');
  const senha = await perguntar('Senha: ', true);
  await api.entrar(email, senha, () => perguntar('Código do aplicativo autenticador: '));
  for (const e of EXEMPLOS) {
    const resultado = await criar(api, e);
    console.log(`${resultado === 'criado' ? 'Criado ' : 'Já existe'}  ${e.titulo}`);
  }
  console.log('\nPronto. O site mostra os cursos em até um minuto.');
}

if (import.meta.main) {
  principal().catch((erro: unknown) => {
    console.error(erro instanceof Error ? erro.message : erro);
    process.exit(1);
  });
}
