/** Imagem escolhida pelo usuário e ainda não enviada (o arquivo fica só no navegador). */
export interface ImagemLocal {
  /** Identificador desta escolha (duas imagens com o mesmo nome continuam distintas). */
  id: string;
  arquivo: File;
  /** Endereço de pré-visualização (`URL.createObjectURL`); liberado ao remover. */
  previa: string;
}

export interface LimitesImagens {
  /** Tipos aceitos (MIME). */
  tipos: readonly string[];
  /** Tamanho máximo de cada imagem, em bytes. */
  tamanhoMaximo: number;
  /** Quantas imagens cabem na mensagem. */
  quantidadeMaxima: number;
}

/** Regras da API do fórum: JPEG ou PNG, até 5 MB, até 3 por mensagem. */
export const LIMITES_FORUM: LimitesImagens = {
  tipos: ['image/jpeg', 'image/png'],
  tamanhoMaximo: 5 * 1024 * 1024,
  quantidadeMaxima: 3,
};

export interface Triagem {
  aceitas: File[];
  /** Uma mensagem por motivo, pronta para mostrar. */
  recusas: string[];
}

/**
 * Separa o que pode entrar do que não pode (tipo, tamanho, quantidade). Vale para escolha, arrastar
 * e colar: o `accept` do seletor não filtra o que chega por arrastar ou colar.
 */
export function triarImagens(
  jaEscolhidas: number,
  novas: readonly File[],
  limites: LimitesImagens,
): Triagem {
  const aceitas: File[] = [];
  const tipoErrado: string[] = [];
  const grandes: string[] = [];
  const sobrando: string[] = [];
  for (const arquivo of novas) {
    const nome = arquivo.name || 'imagem colada';
    if (!limites.tipos.includes(arquivo.type)) tipoErrado.push(nome);
    else if (arquivo.size > limites.tamanhoMaximo) grandes.push(nome);
    else if (jaEscolhidas + aceitas.length >= limites.quantidadeMaxima) sobrando.push(nome);
    else aceitas.push(arquivo);
  }
  const recusas: string[] = [];
  const mb = Math.round(limites.tamanhoMaximo / (1024 * 1024));
  if (tipoErrado.length) recusas.push(`${lista(tipoErrado)}: envie imagens JPG ou PNG.`);
  if (grandes.length) recusas.push(`${lista(grandes)}: cada imagem pode ter até ${mb} MB.`);
  if (sobrando.length) {
    recusas.push(
      `${lista(sobrando)}: anexe no máximo ${limites.quantidadeMaxima} imagens por mensagem.`,
    );
  }
  return { aceitas, recusas };
}

function lista(nomes: string[]): string {
  if (nomes.length === 1) return nomes[0]!;
  return `${nomes.slice(0, -1).join(', ')} e ${nomes.at(-1)}`;
}

/** Arquivos de imagem de um evento de colar (Ctrl+V de um print). */
export function imagensColadas(dados: DataTransfer | null): File[] {
  if (!dados) return [];
  return Array.from(dados.items)
    .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
    .map((item) => item.getAsFile())
    .filter((arquivo): arquivo is File => arquivo !== null);
}
