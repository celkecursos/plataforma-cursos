/** Respostas da API da área do aluno (/api/aluno, /api/perfil), espelhando os records do backend. */

export type SituacaoMatricula = 'AGUARDANDO_INICIO' | 'VIGENTE' | 'VENCIDA' | 'CANCELADA';
export type FiltroMeusCursos = 'ATIVOS' | 'ARQUIVADOS' | 'EXPIRADOS';

export interface MeuCurso {
  matriculaId: number;
  turmaId: number;
  cursoTitulo: string;
  cursoSlug: string;
  turmaNome: string;
  capaUrl: string | null;
  inicio: string;
  fim: string | null;
  situacao: SituacaoMatricula;
  arquivada: boolean;
}

export interface AulaResumo {
  id: number;
  titulo: string;
  gratuita: boolean;
}

export interface ModuloAluno {
  id: number;
  titulo: string;
  aulas: AulaResumo[];
}

export interface TurmaAluno {
  matriculaId: number;
  turmaId: number;
  turmaNome: string;
  cursoTitulo: string;
  cursoSlug: string;
  inicio: string;
  fim: string | null;
  modulos: ModuloAluno[];
}

export interface AulaAluno {
  id: number;
  turmaId: number;
  titulo: string;
  descricao: string | null;
  gratuita: boolean;
}

export interface Progresso {
  aulaId: number;
  posicaoSegundos: number;
  vista: boolean;
  vistaEm: string | null;
  revisar: boolean;
}

export interface ProgressoTurma {
  turmaId: number;
  totalAulas: number;
  aulasVistas: number;
  percentual: number;
  aulas: Progresso[];
}

export interface ArquivoApoio {
  id: number;
  aulaId: number | null;
  moduloId: number | null;
  titulo: string;
  nomeArquivo: string;
  contentType: string;
  tamanho: number;
  criadoEm: string;
}

export interface Download {
  url: string;
  expiraEm: string;
}

export interface Certificado {
  id: number;
  turmaId: number;
  codigo: string;
  nomeCompleto: string;
  cursoTitulo: string;
  turmaNome: string;
  cargaHorariaHoras: number | null;
  emitidoEm: string;
}

export interface SituacaoCertificado {
  percentualVisto: number;
  percentualExigido: number;
  concluiu: boolean;
  certificado: Certificado | null;
}

export interface TopicoResumo {
  id: number;
  turmaId: number;
  aulaId: number | null;
  titulo: string;
  autorNome: string;
  fixado: boolean;
  respondidoInstrutor: boolean;
  respostas: number;
  criadoEm: string;
}

export interface Anexo {
  id: number;
  url: string;
}

export interface Resposta {
  id: number;
  corpoHtml: string;
  autorNome: string;
  instrutor: boolean;
  criadoEm: string;
  anexos: Anexo[];
}

export interface Topico extends Omit<TopicoResumo, 'respostas'> {
  corpoHtml: string;
  anexos: Anexo[];
  respostas: Resposta[];
}

export interface Perfil {
  id: number;
  nome: string;
  email: string;
  papeis: string[];
  fotoUrl: string | null;
}

export interface EnvioSimples {
  chave: string;
  url: string;
  contentType: string;
  expiraEm: string;
}

/** Aula com o módulo e a posição dela na turma, para anterior/próxima. */
export interface AulaNaTurma {
  aula: AulaResumo;
  modulo: ModuloAluno;
  anterior: AulaResumo | null;
  proxima: AulaResumo | null;
}

export function localizarAula(turma: TurmaAluno, aulaId: number): AulaNaTurma | null {
  const todas = turma.modulos.flatMap((modulo) => modulo.aulas.map((aula) => ({ aula, modulo })));
  const indice = todas.findIndex((item) => item.aula.id === aulaId);
  if (indice < 0) return null;
  const { aula, modulo } = todas[indice]!;
  return {
    aula,
    modulo,
    anterior: todas[indice - 1]?.aula ?? null,
    proxima: todas[indice + 1]?.aula ?? null,
  };
}

export type StatusPedido =
  | 'CRIADO'
  | 'AGUARDANDO_PAGAMENTO'
  | 'APROVADO'
  | 'CONCLUIDO'
  | 'EM_DISPUTA'
  | 'ATRASADO'
  | 'REEMBOLSADO'
  | 'CHARGEBACK'
  | 'CANCELADO'
  | 'EXPIRADO';

/** Resposta do checkout: ir para a página do meio de pagamento ou montar o formulário embutido. */
/** Oferta à venda mostrada antes de abrir o pedido (`ResumoOferta`). */
export interface ResumoOferta {
  id: number;
  titulo: string;
  precoCentavos: number;
  moeda: string;
  curso: string;
  turma: string;
  meioPagamento: string;
}

export interface Checkout {
  pedido: string;
  tipo: 'REDIRECIONAMENTO' | 'EMBUTIDO';
  url: string | null;
  dados: Record<string, unknown> | null;
}

/** Situação do pedido na página de retorno; curso e turma só para o dono logado. */
export interface SituacaoPedido {
  codigo: string;
  status: StatusPedido;
  turmaId: number | null;
  turmaNome: string | null;
  cursoTitulo: string | null;
  cursoSlug: string | null;
}
