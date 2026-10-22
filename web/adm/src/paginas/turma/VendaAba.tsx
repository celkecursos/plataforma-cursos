import {
  AcaoLinha,
  Alerta,
  Botao,
  CampoInteiro,
  CampoMoeda,
  CampoTexto,
  Form,
  FormSecao,
  Status,
  Tabela,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { moeda } from '@compartilhado/ui/formato.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { erroObrigatorio } from '@compartilhado/ui/validacao.ts';
import type { Oferta, Turma } from '../../tipos.ts';

/** Ofertas da turma: o que se vende no meio de pagamento (produto e oferta de lá) e o acesso que dá. */
export function VendaAba({ turma }: { turma: Turma }) {
  const { executar } = useAcao();
  const [dialogo, setDialogo] = useState<{ oferta?: Oferta } | null>(null);
  const ofertas = useCarregar(
    () => api.get<Oferta[]>(`/api/adm/turmas/${turma.id}/ofertas`),
    [turma.id],
  );

  return (
    <div className="pc-pilha">
      {turma.status !== 'ATIVA' && (
        <Alerta
          severidade="info"
          mensagem="O botão Comprar só aparece no site para a turma ativa de um curso publicado."
        />
      )}
      <div className="pc-linha pc-linha-fim">
        <Botao rotulo="Nova oferta" icone="plus" onClick={() => setDialogo({})} />
      </div>
      <Tabela<Oferta>
        rotulo="Ofertas"
        colunas={[
          { campo: 'titulo', rotulo: 'Oferta', destaque: true },
          {
            campo: 'precoCentavos',
            rotulo: 'Preço',
            formato: (v, o) => moeda(v as number, o.moeda),
          },
          {
            campo: 'diasAcesso',
            rotulo: 'Acesso',
            formato: (v) => (v ? `${v as number} dias` : 'Sem prazo'),
          },
          {
            campo: 'produtoExterno',
            rotulo: 'Produto / oferta',
            formato: (v, o) => `${v as string} / ${o.ofertaExterna}`,
          },
          { campo: 'gatewayNome', rotulo: 'Meio de pagamento' },
          {
            campo: 'ativa',
            rotulo: 'Situação',
            render: (o) => (
              <Status
                rotulo={o.ativa ? (o.gatewayAtivo ? 'À venda' : 'Meio inativo') : 'Inativa'}
                severidade={o.ativa && o.gatewayAtivo ? 'ok' : 'neutral'}
              />
            ),
          },
        ]}
        registros={ofertas.dados ?? []}
        carregando={ofertas.carregando && !ofertas.dados}
        erro={ofertas.erro}
        aoRecarregar={ofertas.recarregar}
        vazioTitulo="Nenhuma oferta"
        vazioDescricao="Cadastre o produto e a oferta do meio de pagamento para vender esta turma."
        vazioIcone="tag"
        acoes={(o) => (
          <>
            <AcaoLinha
              icone="pencil"
              rotulo={`Editar ${o.titulo}`}
              onClick={() => setDialogo({ oferta: o })}
            />
            <AcaoLinha
              icone={o.ativa ? 'power-off' : 'power'}
              rotulo={o.ativa ? `Desativar ${o.titulo}` : `Ativar ${o.titulo}`}
              onClick={async () => {
                const ok = await executar(
                  () =>
                    api
                      .patch(`/api/adm/ofertas/${o.id}/status`, { ativa: !o.ativa })
                      .then(() => true),
                  o.ativa ? 'Oferta desativada' : 'Oferta ativada',
                );
                if (ok) ofertas.recarregar();
              }}
            />
          </>
        )}
      />
      {dialogo && (
        <FormOferta
          turma={turma}
          oferta={dialogo.oferta}
          aoFechar={() => setDialogo(null)}
          aoSalvar={() => {
            setDialogo(null);
            ofertas.recarregar();
          }}
        />
      )}
    </div>
  );
}

type Campos =
  'produtoExterno' | 'ofertaExterna' | 'codigoCheckout' | 'titulo' | 'precoCentavos' | 'diasAcesso';

interface PropsForm {
  turma: Turma;
  oferta?: Oferta;
  aoFechar: () => void;
  aoSalvar: () => void;
}

function FormOferta({ turma, oferta, aoFechar, aoSalvar }: PropsForm) {
  const toast = useToast();
  const [titulo, setTitulo] = useState(oferta?.titulo ?? '');
  const [produto, setProduto] = useState(oferta?.produtoExterno ?? '');
  const [codigoOferta, setCodigoOferta] = useState(oferta?.ofertaExterna ?? '');
  const [codigoCheckout, setCodigoCheckout] = useState(oferta?.codigoCheckout ?? '');
  const [preco, setPreco] = useState<number | null>(oferta ? oferta.precoCentavos / 100 : null);
  const [dias, setDias] = useState<number | null>(oferta?.diasAcesso ?? null);
  const [erros, setErros] = useState<Partial<Record<Campos, string>>>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const novos: Partial<Record<Campos, string>> = {
      titulo: erroObrigatorio(titulo, 'Informe o título.', 120),
      produtoExterno: erroObrigatorio(produto, 'Informe o id do produto.', 100),
      ofertaExterna: erroObrigatorio(codigoOferta, 'Informe o código da oferta.', 100),
      precoCentavos: preco == null ? 'Informe o preço.' : undefined,
      diasAcesso: dias != null && (dias < 1 || dias > 36_500) ? 'De 1 a 36.500 dias.' : undefined,
    };
    setErros(novos);
    setErroGeral(undefined);
    if (Object.values(novos).some(Boolean)) return;
    setSalvando(true);
    try {
      const corpo = {
        titulo: titulo.trim(),
        produtoExterno: produto.trim(),
        ofertaExterna: codigoOferta.trim(),
        codigoCheckout: codigoCheckout.trim() || null,
        precoCentavos: Math.round((preco ?? 0) * 100),
        moeda: oferta?.moeda ?? 'BRL',
        diasAcesso: dias,
      };
      if (oferta) {
        await api.put(`/api/adm/ofertas/${oferta.id}`, corpo);
        toast.ok('Oferta alterada', corpo.titulo);
      } else {
        await api.post(`/api/adm/turmas/${turma.id}/ofertas`, corpo);
        toast.ok('Oferta cadastrada', corpo.titulo);
      }
      aoSalvar();
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({
          titulo: erro.erroDoCampo('titulo'),
          produtoExterno: erro.erroDoCampo('produtoExterno'),
          ofertaExterna: erro.erroDoCampo('ofertaExterna'),
          codigoCheckout: erro.erroDoCampo('codigoCheckout'),
          precoCentavos: erro.erroDoCampo('precoCentavos'),
          diasAcesso: erro.erroDoCampo('diasAcesso'),
        });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Form
      arranjo="modal"
      aberto
      titulo={oferta ? `Editar ${oferta.titulo}` : 'Nova oferta'}
      rotuloSalvar="Salvar oferta"
      aoSalvar={() => void salvar()}
      aoCancelar={aoFechar}
      salvando={salvando}
      tamanho="medio"
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <FormSecao legenda="Oferta" colunas={2}>
        <CampoTexto
          rotulo="Título"
          valor={titulo}
          aoMudar={setTitulo}
          erro={erros.titulo}
          maxLength={120}
          obrigatorio
          dica="Aparece no site, no botão de compra."
          className="lp-col-full"
        />
        <CampoMoeda
          rotulo="Preço"
          valor={preco}
          aoMudar={setPreco}
          erro={erros.precoCentavos}
          obrigatorio
        />
        <CampoInteiro
          rotulo="Dias de acesso"
          valor={dias}
          aoMudar={setDias}
          erro={erros.diasAcesso}
          dica="Em branco, sem prazo."
        />
      </FormSecao>
      <FormSecao legenda="No meio de pagamento" colunas={2}>
        <CampoTexto
          rotulo="Id do produto"
          valor={produto}
          aoMudar={setProduto}
          erro={erros.produtoExterno}
          maxLength={100}
          obrigatorio
          dica="Hotmart: o id numérico do produto (vem nas notificações)."
        />
        <CampoTexto
          rotulo="Código da oferta"
          valor={codigoOferta}
          aoMudar={setCodigoOferta}
          erro={erros.ofertaExterna}
          maxLength={100}
          obrigatorio
          dica="Hotmart: o valor de off= no link da oferta."
        />
        <CampoTexto
          rotulo="Código do checkout"
          valor={codigoCheckout}
          aoMudar={setCodigoCheckout}
          erro={erros.codigoCheckout}
          maxLength={100}
          dica="Hotmart: o trecho depois de pay.hotmart.com/ no link. Em branco, usa o id do produto."
          className="lp-col-full"
        />
      </FormSecao>
    </Form>
  );
}
