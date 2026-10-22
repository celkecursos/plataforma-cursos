import type { Severidade } from '@celke/kole-react';

/** Respostas da API de gestão (/api/adm), espelhando os records do backend. */

export type StatusCurso = 'RASCUNHO' | 'PUBLICADO' | 'ARQUIVADO';
export type StatusTurma = 'PREPARACAO' | 'ATIVA' | 'ENCERRADA';
export type StatusConteudo = 'ATIVO' | 'INATIVO';
export type StatusVideo = 'AGUARDANDO_ENVIO' | 'PENDENTE' | 'PROCESSANDO' | 'PRONTO' | 'ERRO';
export type SituacaoMatricula = 'AGUARDANDO_INICIO' | 'VIGENTE' | 'VENCIDA' | 'CANCELADA';
export type FormaPagamento = 'MANUAL' | 'CORTESIA' | 'PIX' | 'CARTAO' | 'BOLETO' | 'OUTRO';
export type TipoTermo = 'TERMOS_USO' | 'POLITICA_PRIVACIDADE';

export interface Curso {
  id: number;
  titulo: string;
  slug: string;
  resumo: string;
  descricao: string | null;
  status: StatusCurso;
  capaUrl: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface Turma {
  id: number;
  cursoId: number;
  nome: string;
  status: StatusTurma;
  percentualConclusao: number;
  cargaHorariaHoras: number | null;
}

export interface Modulo {
  id: number;
  turmaId: number;
  titulo: string;
  ordem: number;
  status: StatusConteudo;
}

export interface Aula {
  id: number;
  moduloId: number;
  titulo: string;
  descricao: string | null;
  ordem: number;
  gratuita: boolean;
  status: StatusConteudo;
}

export interface Video {
  aulaId: number;
  status: StatusVideo;
  publicado: boolean;
  nomeOriginal: string | null;
  tamanhoOriginal: number | null;
  tamanhoFinal: number | null;
  reducaoPercentual: number | null;
  duracaoSegundos: number | null;
  largura: number | null;
  altura: number | null;
  variantes: string[];
  segundosProcessamentoPorMinuto: number | null;
  thumbnailUrl: string | null;
  erro: string | null;
  processadoEm: string | null;
  atualizadoEm: string;
}

export interface EnvioVideo {
  tamanhoParte: number;
  partes: { numero: number; url: string }[];
  expiraEm: string;
}

/** Envio de um arquivo só (capa, arquivo de apoio): PUT na url com o Content-Type informado. */
export interface EnvioSimples {
  chave: string;
  url: string;
  contentType: string;
  expiraEm: string;
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

export interface Matricula {
  id: number;
  turmaId: number;
  usuarioId: number;
  nome: string;
  email: string;
  inicio: string;
  fim: string | null;
  status: 'ATIVA' | 'CANCELADA';
  situacao: SituacaoMatricula;
  formaPagamento: FormaPagamento;
  transacao: string | null;
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
  oculto: boolean | null;
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
  oculto: boolean | null;
  criadoEm: string;
  anexos: Anexo[];
}

export interface Topico extends Omit<TopicoResumo, 'respostas'> {
  corpoHtml: string;
  anexos: Anexo[];
  respostas: Resposta[];
}

export interface Termo {
  id: number;
  tipo: TipoTermo;
  versao: number;
  conteudoHtml: string;
  publicadoEm: string;
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
export type StatusEvento = 'RECEBIDO' | 'PROCESSADO' | 'IGNORADO' | 'ERRO';

/** Campo da configuração de um meio de pagamento (o formulário é montado a partir daqui). */
export interface CampoConfiguracao {
  chave: string;
  rotulo: string;
  tipo: 'TEXTO' | 'URL' | 'SELECAO';
  secreto: boolean;
  obrigatorio: boolean;
  dica: string | null;
  padrao: string | null;
  opcoes: string[];
}

export interface TipoGateway {
  tipo: string;
  nome: string;
  campos: CampoConfiguracao[];
  capacidades: string[];
}

export interface TiposGateway {
  cifraDisponivel: boolean;
  tipos: TipoGateway[];
}

export interface SegredoResumo {
  configurado: boolean;
  final4: string | null;
}

export interface Gateway {
  id: number;
  tipo: string;
  nomeTipo: string;
  nome: string;
  ativo: boolean;
  valores: Record<string, string>;
  segredos: Record<string, SegredoResumo>;
  segredosLegiveis: boolean;
  webhookUrl: string;
  criadoEm: string;
  atualizadoEm: string;
}

export interface ResultadoTeste {
  ok: boolean;
  mensagem: string;
}

export interface Oferta {
  id: number;
  turmaId: number;
  gatewayId: number;
  gatewayNome: string | null;
  gatewayAtivo: boolean;
  produtoExterno: string;
  ofertaExterna: string;
  codigoCheckout: string | null;
  titulo: string;
  precoCentavos: number;
  moeda: string;
  diasAcesso: number | null;
  ativa: boolean;
  criadoEm: string;
}

export interface Pedido {
  id: number;
  codigo: string;
  status: StatusPedido;
  origem: 'CHECKOUT' | 'EXTERNO';
  email: string;
  nome: string | null;
  usuarioId: number | null;
  gatewayId: number;
  ofertaId: number;
  ofertaTitulo: string | null;
  turmaId: number | null;
  transacaoExterna: string | null;
  valorCentavos: number | null;
  moeda: string | null;
  formaPagamento: FormaPagamento | null;
  matriculaId: number | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface EventoPagamento {
  id: number;
  gatewayId: number;
  idExterno: string;
  tipoOriginal: string;
  status: StatusEvento;
  recebidoEm: string;
  ocorridoEm: string | null;
  transacao: string | null;
  erro: string | null;
  pedidoId: number | null;
  processadoEm: string | null;
}

export interface HistoricoPedido {
  id: number;
  de: StatusPedido | null;
  para: StatusPedido;
  eventoId: number | null;
  motivo: string | null;
  em: string;
}

export interface PedidoDetalhe {
  pedido: Pedido;
  historico: HistoricoPedido[];
  eventos: EventoPagamento[];
}

export interface EventoDetalhe {
  evento: EventoPagamento;
  payload: string | null;
}

export type OperacaoAuditoria = 'INCLUSAO' | 'ALTERACAO' | 'EXCLUSAO';

/** Uma linha da trilha de auditoria; `antes` e `depois` são JSON com os campos. */
export interface RegistroAuditoria {
  id: number;
  em: string;
  operacao: OperacaoAuditoria;
  entidade: string;
  entidadeId: string | null;
  usuarioId: number | null;
  usuarioNome: string | null;
  usuarioEmail: string | null;
  origem: string;
  ip: string | null;
  userAgent: string | null;
  requestId: string | null;
  antes: string | null;
  depois: string | null;
}

interface Rotulo {
  rotulo: string;
  severidade: Severidade;
}

export const STATUS_CURSO: Record<StatusCurso, Rotulo> = {
  RASCUNHO: { rotulo: 'Rascunho', severidade: 'neutral' },
  PUBLICADO: { rotulo: 'Publicado', severidade: 'ok' },
  ARQUIVADO: { rotulo: 'Arquivado', severidade: 'warn' },
};

export const STATUS_TURMA: Record<StatusTurma, Rotulo> = {
  PREPARACAO: { rotulo: 'Em preparação', severidade: 'neutral' },
  ATIVA: { rotulo: 'Ativa', severidade: 'ok' },
  ENCERRADA: { rotulo: 'Encerrada', severidade: 'warn' },
};

export const STATUS_CONTEUDO: Record<StatusConteudo, Rotulo> = {
  ATIVO: { rotulo: 'Ativo', severidade: 'ok' },
  INATIVO: { rotulo: 'Inativo', severidade: 'neutral' },
};

export const STATUS_VIDEO: Record<StatusVideo, Rotulo> = {
  AGUARDANDO_ENVIO: { rotulo: 'Aguardando envio', severidade: 'neutral' },
  PENDENTE: { rotulo: 'Na fila', severidade: 'info' },
  PROCESSANDO: { rotulo: 'Processando', severidade: 'info' },
  PRONTO: { rotulo: 'Pronto', severidade: 'ok' },
  ERRO: { rotulo: 'Erro', severidade: 'err' },
};

export const SITUACAO_MATRICULA: Record<SituacaoMatricula, Rotulo> = {
  AGUARDANDO_INICIO: { rotulo: 'Aguardando início', severidade: 'info' },
  VIGENTE: { rotulo: 'Vigente', severidade: 'ok' },
  VENCIDA: { rotulo: 'Vencida', severidade: 'warn' },
  CANCELADA: { rotulo: 'Cancelada', severidade: 'err' },
};

export const FORMAS_PAGAMENTO: Record<FormaPagamento, string> = {
  MANUAL: 'Manual',
  CORTESIA: 'Cortesia',
  PIX: 'Pix',
  CARTAO: 'Cartão',
  BOLETO: 'Boleto',
  OUTRO: 'Outro',
};

export const STATUS_PEDIDO: Record<StatusPedido, Rotulo> = {
  CRIADO: { rotulo: 'Criado', severidade: 'neutral' },
  AGUARDANDO_PAGAMENTO: { rotulo: 'Aguardando pagamento', severidade: 'info' },
  APROVADO: { rotulo: 'Aprovado', severidade: 'ok' },
  CONCLUIDO: { rotulo: 'Concluído', severidade: 'ok' },
  EM_DISPUTA: { rotulo: 'Em disputa', severidade: 'warn' },
  ATRASADO: { rotulo: 'Atrasado', severidade: 'warn' },
  REEMBOLSADO: { rotulo: 'Reembolsado', severidade: 'err' },
  CHARGEBACK: { rotulo: 'Chargeback', severidade: 'err' },
  CANCELADO: { rotulo: 'Cancelado', severidade: 'err' },
  EXPIRADO: { rotulo: 'Expirado', severidade: 'neutral' },
};

export const STATUS_EVENTO: Record<StatusEvento, Rotulo> = {
  RECEBIDO: { rotulo: 'Na fila', severidade: 'info' },
  PROCESSADO: { rotulo: 'Processado', severidade: 'ok' },
  IGNORADO: { rotulo: 'Ignorado', severidade: 'warn' },
  ERRO: { rotulo: 'Erro', severidade: 'err' },
};

export const OPERACAO_AUDITORIA: Record<OperacaoAuditoria, Rotulo> = {
  INCLUSAO: { rotulo: 'Inclusão', severidade: 'ok' },
  ALTERACAO: { rotulo: 'Alteração', severidade: 'info' },
  EXCLUSAO: { rotulo: 'Exclusão', severidade: 'err' },
};

export const TIPOS_TERMO: Record<TipoTermo, string> = {
  TERMOS_USO: 'Termos de uso',
  POLITICA_PRIVACIDADE: 'Política de privacidade',
};

export function opcoes<V extends string>(mapa: Record<V, Rotulo | string>) {
  return (Object.keys(mapa) as V[]).map((valor) => {
    const item = mapa[valor];
    return { valor, rotulo: typeof item === 'string' ? item : item.rotulo };
  });
}
