import { Alerta, Botao, Carregando } from '@celke/kole-react';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { CAMINHOS, caminhoTurma } from '../../caminhos.ts';
import type { SituacaoPedido, StatusPedido } from '../../tipos.ts';

export const INTERVALO_MS = 3_000;
export const LIMITE_MS = 120_000;

export type Resultado = 'confirmado' | 'aguardando' | 'recusado';

/** O que a página mostra para cada status do pedido. */
export function resultado(status: StatusPedido): Resultado {
  switch (status) {
    case 'APROVADO':
    case 'CONCLUIDO':
      return 'confirmado';
    case 'CRIADO':
    case 'AGUARDANDO_PAGAMENTO':
      return 'aguardando';
    default:
      return 'recusado';
  }
}

/**
 * Página de obrigado do meio de pagamento (`?pedido=<código>` ou `?xcod=<código>`): consulta o pedido a cada 3 segundos
 * por até 2 minutos, até a notificação chegar.
 */
export function RetornoPagamentoPagina() {
  const [parametros] = useSearchParams();
  // `xcod`: o código do pedido como a Hotmart o repassa à página de obrigado
  const codigo = parametros.get('pedido') ?? parametros.get('xcod');
  const navigate = useNavigate();
  const [situacao, setSituacao] = useState<SituacaoPedido>();
  const [erro, setErro] = useState<string>();
  const [esgotou, setEsgotou] = useState(false);

  useEffect(() => {
    if (!codigo) return;
    let ativo = true;
    const inicio = Date.now();
    let espera: ReturnType<typeof setTimeout>;
    const consultar = async () => {
      try {
        const atual = await api.get<SituacaoPedido>(
          `/api/pagamentos/pedidos/${encodeURIComponent(codigo)}`,
        );
        if (!ativo) return;
        setSituacao(atual);
        if (resultado(atual.status) !== 'aguardando') return;
      } catch (falha) {
        if (ativo) setErro(mensagemDeErro(falha));
        return;
      }
      if (Date.now() - inicio >= LIMITE_MS) {
        setEsgotou(true);
        return;
      }
      espera = setTimeout(() => void consultar(), INTERVALO_MS);
    };
    void consultar();
    return () => {
      ativo = false;
      clearTimeout(espera);
    };
  }, [codigo]);

  if (!codigo || erro) {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Pedido não encontrado">
        <Alerta severidade="err" mensagem={erro ?? 'O link não tem o código do pedido.'} />
        <Link className="pc-link" to={CAMINHOS.inicio}>
          Ir para Meus cursos
        </Link>
      </LayoutConta>
    );
  }
  if (!situacao) {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Conferindo o pagamento">
        <Carregando mensagem="Consultando o seu pedido..." />
      </LayoutConta>
    );
  }

  const estado = resultado(situacao.status);
  if (estado === 'confirmado') {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Pagamento confirmado">
        <Alerta
          severidade="ok"
          mensagem={
            situacao.cursoTitulo
              ? `Sua matrícula em ${situacao.cursoTitulo} está ativa.`
              : 'Sua matrícula está ativa. Confira o e-mail de confirmação.'
          }
        />
        <Botao
          rotulo={situacao.turmaId ? 'Ir para o curso' : 'Ir para Meus cursos'}
          icone="graduation-cap"
          bloco
          onClick={() =>
            navigate(situacao.turmaId ? caminhoTurma(situacao.turmaId) : CAMINHOS.inicio)
          }
        />
      </LayoutConta>
    );
  }
  if (estado === 'aguardando') {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Aguardando pagamento">
        <Alerta
          severidade="info"
          mensagem="Assim que o pagamento for confirmado (boleto ou PIX podem levar um tempo), você recebe um e-mail e o curso aparece em Meus cursos."
        />
        {!esgotou && <Carregando mensagem="Conferindo de novo em instantes..." />}
        <Link className="pc-link" to={CAMINHOS.inicio}>
          Ir para Meus cursos
        </Link>
      </LayoutConta>
    );
  }
  return (
    <LayoutConta marca="Plataforma de Cursos" titulo="Pagamento não aprovado">
      <Alerta
        severidade="err"
        mensagem="O pagamento não foi aprovado ou foi cancelado. Tente de novo pelo site do curso."
      />
      <Link className="pc-link" to={CAMINHOS.inicio}>
        Ir para Meus cursos
      </Link>
    </LayoutConta>
  );
}
