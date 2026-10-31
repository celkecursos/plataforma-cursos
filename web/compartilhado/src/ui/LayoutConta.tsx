import type { FormEvent, ReactNode } from 'react';

interface LayoutContaProps {
  marca: string;
  titulo: string;
  descricao?: string;
  /** Envio do formulário (o `preventDefault` já é feito aqui). Sem ele, o conteúdo vai sem `<form>`. */
  aoEnviar?: () => void;
  rodape?: ReactNode;
  children: ReactNode;
}

/**
 * Cartão centralizado das telas fora da área logada (cadastro, confirmação de e-mail, recuperação de
 * senha). O título é o `<h1>` da página.
 */
export function LayoutConta({
  marca,
  titulo,
  descricao,
  aoEnviar,
  rodape,
  children,
}: LayoutContaProps) {
  const enviar = (evento: FormEvent) => {
    evento.preventDefault();
    aoEnviar?.();
  };
  return (
    <main className="pc-conta">
      <section className="pc-conta-cartao" aria-labelledby="pc-conta-titulo">
        <p className="pc-conta-marca">{marca}</p>
        <h1 id="pc-conta-titulo" className="pc-conta-titulo">
          {titulo}
        </h1>
        {descricao && <p className="pc-conta-descricao">{descricao}</p>}
        {aoEnviar ? (
          <form className="pc-conta-form" onSubmit={enviar} noValidate>
            {children}
          </form>
        ) : (
          <div className="pc-conta-form">{children}</div>
        )}
        {rodape && <div className="pc-conta-rodape">{rodape}</div>}
      </section>
    </main>
  );
}
