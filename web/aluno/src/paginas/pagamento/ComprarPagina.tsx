import { Alerta, Carregando } from '@celke/kole-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { CAMINHOS } from '../../caminhos.ts';
import type { Checkout } from '../../tipos.ts';

/** Para onde levar o aluno no checkout (redirecionamento separado para os testes). */
export const navegador = {
  ir: (url: string) => window.location.assign(url),
};

/**
 * Destino do botão "Comprar" do site (exige login: sem sessão, volta para cá depois de entrar ou
 * criar a conta). Abre o pedido e leva ao checkout do meio de pagamento.
 */
export function ComprarPagina() {
  const { ofertaId } = useParams();
  const [erro, setErro] = useState<string>();
  const [embutido, setEmbutido] = useState<Checkout | null>(null);
  // Um pedido por visita, mesmo com o StrictMode montando duas vezes
  const pedido = useRef(false);

  useEffect(() => {
    if (pedido.current) return;
    pedido.current = true;
    api
      .post<Checkout>('/api/pagamentos/checkout', { ofertaId: Number(ofertaId) })
      .then((checkout) => {
        if (checkout.tipo === 'REDIRECIONAMENTO' && checkout.url) navegador.ir(checkout.url);
        else setEmbutido(checkout);
      })
      .catch((falha: unknown) => setErro(mensagemDeErro(falha)));
  }, [ofertaId]);

  if (erro) {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Não foi possível iniciar a compra">
        <Alerta severidade="err" mensagem={erro} />
        <Link className="pc-link" to={CAMINHOS.inicio}>
          Ir para Meus cursos
        </Link>
      </LayoutConta>
    );
  }
  if (embutido) {
    // Meio de pagamento com formulário próprio: os dados vão para o componente dele
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Pagamento">
        <Alerta
          severidade="info"
          mensagem="Este meio de pagamento usa um formulário próprio, ainda não disponível nesta versão."
        />
      </LayoutConta>
    );
  }
  return (
    <LayoutConta marca="Plataforma de Cursos" titulo="Indo para o pagamento">
      <Carregando mensagem="Preparando o seu pedido..." />
    </LayoutConta>
  );
}
