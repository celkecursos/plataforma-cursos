/** HTML montado com escape automático: só o que já é `Html` entra sem escapar. */
export class Html {
  readonly valor: string;
  constructor(valor: string) {
    this.valor = valor;
  }
  toString(): string {
    return this.valor;
  }
}

const ENTIDADES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

export function escapar(texto: string): string {
  return texto.replace(/[&<>"']/g, (c) => ENTIDADES[c]!);
}

type Valor = Html | string | number | boolean | null | undefined | Valor[];

function parte(valor: Valor): string {
  if (valor === null || valor === undefined || valor === false || valor === true) return '';
  if (Array.isArray(valor)) return valor.map(parte).join('');
  if (valor instanceof Html) return valor.valor;
  return escapar(String(valor));
}

export function html(textos: TemplateStringsArray, ...valores: Valor[]): Html {
  let saida = textos[0]!;
  valores.forEach((valor, i) => {
    saida += parte(valor) + textos[i + 1]!;
  });
  return new Html(saida);
}

/** HTML que já vem filtrado (termos convertidos pela API). */
export function confiavel(texto: string): Html {
  return new Html(texto);
}

/** Texto puro em parágrafos (linha em branco separa parágrafos). */
export function paragrafos(texto: string | null | undefined): Html {
  if (!texto?.trim()) return new Html('');
  return html`${texto
    .trim()
    .split(/\n\s*\n/)
    .map((p) => html`<p>${p.trim()}</p>`)}`;
}

/** JSON dentro de <script>: sem `<` cru, para não fechar a tag. */
export function jsonEmScript(dados: unknown): Html {
  return new Html(JSON.stringify(dados).replace(/</g, '\\u003c'));
}
