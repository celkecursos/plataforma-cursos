/** Erro de campo devolvido pela API na validação (lista `erros` do ProblemDetail). */
export interface ErroCampo {
  campo: string;
  mensagem: string;
}

/** Termo vigente que o usuário ainda precisa aceitar (403 com `termosPendentes`). */
export interface TermoPendente {
  id: number;
  tipo: 'TERMOS_USO' | 'POLITICA_PRIVACIDADE';
  versao: number;
}

interface ProblemDetail {
  status?: number;
  title?: string;
  detail?: string;
  erros?: ErroCampo[];
  termosPendentes?: TermoPendente[];
}

/**
 * Resposta de erro da API (ProblemDetail, RFC 9457). A `message` é o `detail`, já em português e
 * pronto para o usuário.
 */
export class ErroApi extends Error {
  readonly status: number;
  readonly erros: ErroCampo[];
  readonly termosPendentes: TermoPendente[];
  /** Segundos do cabeçalho `Retry-After` (limite de tentativas), quando houver. */
  readonly tentarEm: number | null;

  constructor(status: number, corpo: ProblemDetail | null, tentarEm: number | null = null) {
    super(corpo?.detail ?? mensagemPadrao(status));
    this.name = 'ErroApi';
    this.status = status;
    this.erros = corpo?.erros ?? [];
    this.termosPendentes = corpo?.termosPendentes ?? [];
    this.tentarEm = tentarEm;
  }

  /** Mensagem do campo informado, se a API apontou erro nele. */
  erroDoCampo(campo: string): string | undefined {
    return this.erros.find((e) => e.campo === campo)?.mensagem;
  }
}

function mensagemPadrao(status: number): string {
  if (status === 0) return 'Sem conexão com o servidor. Verifique a internet e tente de novo.';
  if (status >= 500) return 'O servidor não conseguiu responder agora. Tente de novo em instantes.';
  return 'Não foi possível concluir a operação.';
}

/** Texto para mostrar ao usuário a partir de qualquer erro. */
export function mensagemDeErro(erro: unknown): string {
  if (erro instanceof ErroApi) return erro.message;
  return 'Ocorreu um erro inesperado. Tente de novo.';
}
