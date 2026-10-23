import { Alerta, Botao, Carregando } from '@celke/kole-react';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { moeda } from '@compartilhado/ui/formato.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { CAMINHOS } from '../../caminhos.ts';
import type { Checkout, ResumoOferta } from '../../tipos.ts';

/** Para onde levar o aluno no checkout (redirecionamento separado para os testes). */
export const navegador = {
  ir: (url: string) => window.location.assign(url),
};

/** Só sai da plataforma para um checkout em HTTPS (o backend já limita os domínios do meio). */
export function checkoutSeguro(url: string | null): url is string {
  if (!url) return false;
  try {
    return new URL(url).protocol === 'https:';
  } catch {
    return false;
  }
}

/**
 * Destino do botão "Comprar" do site (exige login: sem sessão, volta para cá depois de entrar ou
 * criar a conta). Mostra o que vai ser comprado e só abre o pedido quando o aluno confirma: abrir a
 * página (um link enviado por alguém, um pré-carregamento do navegador) não cria pedido.
 */
export function ComprarPagina() {
  const { ofertaId } = useParams();
  const [oferta, setOferta] = useState<ResumoOferta>();
  const [erro, setErro] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const [embutido, setEmbutido] = useState<Checkout | null>(null);

  useEffect(() => {
    let ativo = true;
    api
      .get<ResumoOferta>(`/api/pagamentos/ofertas/${Number(ofertaId)}`)
      .then((resposta) => ativo && setOferta(resposta))
      .catch((falha: unknown) => ativo && setErro(mensagemDeErro(falha)));
    return () => {
      ativo = false;
    };
  }, [ofertaId]);

  const comprar = async () => {
    setEnviando(true);
    setErro(undefined);
    try {
      const checkout = await api.post<Checkout>('/api/pagamentos/checkout', {
        ofertaId: Number(ofertaId),
      });
      if (checkout.tipo === 'REDIRECIONAMENTO') {
        if (!checkoutSeguro(checkout.url)) {
          setErro('O endereço de pagamento recebido não é seguro. Avise o suporte.');
          setEnviando(false);
          return;
        }
        navegador.ir(checkout.url);
        return;
      }
      setEmbutido(checkout);
      setEnviando(false);
    } catch (falha) {
      setErro(mensagemDeErro(falha));
      setEnviando(false);
    }
  };

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
  if (!oferta) {
    return erro ? (
      <LayoutConta marca="Plataforma de Cursos" titulo="Não foi possível iniciar a compra">
        <Alerta severidade="err" mensagem={erro} />
        <Link className="pc-link" to={CAMINHOS.inicio}>
          Ir para Meus cursos
        </Link>
      </LayoutConta>
    ) : (
      <LayoutConta marca="Plataforma de Cursos" titulo="Confirmar a compra">
        <Carregando mensagem="Carregando a oferta..." />
      </LayoutConta>
    );
  }
  return (
    <LayoutConta
      marca="Plataforma de Cursos"
      titulo="Confirmar a compra"
      aoEnviar={() => void comprar()}
      rodape={<Link to={CAMINHOS.inicio}>Cancelar</Link>}
    >
      {erro && <Alerta severidade="err" mensagem={erro} />}
      <dl className="pc-resumo-compra">
        <dt>Curso</dt>
        <dd>{oferta.curso}</dd>
        <dt>Turma</dt>
        <dd>{oferta.turma}</dd>
        <dt>Oferta</dt>
        <dd>{oferta.titulo}</dd>
        <dt>Valor</dt>
        <dd>{moeda(oferta.precoCentavos, oferta.moeda)}</dd>
      </dl>
      <p className="pc-conta-descricao">
        O pagamento é feito na página do meio de pagamento; a matrícula sai assim que ele for
        confirmado.
      </p>
      <Botao
        type="submit"
        rotulo="Ir para o pagamento"
        icone="credit-card"
        carregando={enviando}
        bloco
      />
    </LayoutConta>
  );
}
