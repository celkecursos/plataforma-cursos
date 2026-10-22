import {
  Alerta,
  CampoSelect,
  CampoSenha,
  CampoTexto,
  Form,
  FormSecao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { erroObrigatorio } from '@compartilhado/ui/validacao.ts';
import type { CampoConfiguracao, Gateway, TiposGateway } from '../../tipos.ts';

interface Props {
  tipos: TiposGateway;
  /** Sem gateway, cria um novo. */
  gateway?: Gateway;
  aoFechar: () => void;
  aoSalvar: () => void;
}

/**
 * Formulário montado a partir dos campos que o meio de pagamento declara. Campo secreto vem em
 * branco (a API nunca o devolve): em branco, o valor atual continua valendo.
 */
export function FormGateway({ tipos, gateway, aoFechar, aoSalvar }: Props) {
  const toast = useToast();
  const [tipo, setTipo] = useState<string | null>(gateway?.tipo ?? tipos.tipos[0]?.tipo ?? null);
  const [nome, setNome] = useState(gateway?.nome ?? tipos.tipos[0]?.nome ?? '');
  const [valores, setValores] = useState<Record<string, string>>(gateway?.valores ?? {});
  const [erros, setErros] = useState<Record<string, string | undefined>>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);
  const campos = tipos.tipos.find((t) => t.tipo === tipo)?.campos ?? [];

  const mudar = (chave: string, valor: string) =>
    setValores((atuais) => ({ ...atuais, [chave]: valor }));

  const salvar = async () => {
    const novos: Record<string, string | undefined> = {
      nome: erroObrigatorio(nome, 'Informe o nome.', 120),
      tipo: tipo ? undefined : 'Escolha o meio de pagamento.',
    };
    for (const campo of campos)
      novos[campo.chave] = erroCampo(campo, valores[campo.chave], gateway);
    setErros(novos);
    setErroGeral(undefined);
    if (Object.values(novos).some(Boolean)) return;
    setSalvando(true);
    try {
      const corpo = { nome: nome.trim(), valores: limpos(valores) };
      if (gateway) {
        await api.put(`/api/adm/pagamentos/gateways/${gateway.id}`, corpo);
        toast.ok('Meio de pagamento alterado', nome.trim());
      } else {
        await api.post('/api/adm/pagamentos/gateways', { ...corpo, tipo });
        toast.ok('Meio de pagamento cadastrado', 'Teste as credenciais e ative.');
      }
      aoSalvar();
    } catch (erro) {
      if (erro instanceof ErroApi) {
        const daApi: Record<string, string | undefined> = { nome: erro.erroDoCampo('nome') };
        for (const campo of campos) daApi[campo.chave] = erro.erroDoCampo(`valores.${campo.chave}`);
        setErros(daApi);
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
      titulo={gateway ? `Editar ${gateway.nome}` : 'Novo meio de pagamento'}
      rotuloSalvar="Salvar"
      aoSalvar={() => void salvar()}
      aoCancelar={aoFechar}
      salvando={salvando}
      tamanho="medio"
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <FormSecao legenda="Identificação" colunas={2}>
        <CampoSelect<string>
          rotulo="Meio de pagamento"
          opcoes={tipos.tipos.map((t) => ({ valor: t.tipo, rotulo: t.nome }))}
          valor={tipo}
          aoMudar={setTipo}
          erro={erros['tipo']}
          disabled={!!gateway}
          obrigatorio
        />
        <CampoTexto
          rotulo="Nome"
          valor={nome}
          aoMudar={setNome}
          erro={erros['nome']}
          maxLength={120}
          obrigatorio
        />
      </FormSecao>
      <FormSecao legenda="Configuração" colunas={1}>
        {campos.map((campo) =>
          campo.tipo === 'SELECAO' ? (
            <CampoSelect<string>
              key={campo.chave}
              rotulo={campo.rotulo}
              opcoes={campo.opcoes.map((o) => ({ valor: o, rotulo: o }))}
              valor={valores[campo.chave] ?? campo.padrao}
              aoMudar={(v) => mudar(campo.chave, v ?? '')}
              erro={erros[campo.chave]}
              dica={campo.dica ?? undefined}
              obrigatorio={campo.obrigatorio}
            />
          ) : campo.secreto ? (
            <CampoSenha
              key={campo.chave}
              rotulo={campo.rotulo}
              valor={valores[campo.chave] ?? ''}
              aoMudar={(v) => mudar(campo.chave, v)}
              erro={erros[campo.chave]}
              dica={dicaSegredo(campo, gateway)}
              obrigatorio={campo.obrigatorio && !gateway?.segredos[campo.chave]?.configurado}
              autoComplete="off"
            />
          ) : (
            <CampoTexto
              key={campo.chave}
              rotulo={campo.rotulo}
              valor={valores[campo.chave] ?? ''}
              aoMudar={(v) => mudar(campo.chave, v)}
              erro={erros[campo.chave]}
              dica={campo.dica ?? (campo.padrao ? `Em branco: ${campo.padrao}` : undefined)}
              placeholder={campo.padrao ?? undefined}
              maxLength={500}
              obrigatorio={campo.obrigatorio && !campo.padrao}
            />
          ),
        )}
      </FormSecao>
    </Form>
  );
}

function dicaSegredo(campo: CampoConfiguracao, gateway?: Gateway): string | undefined {
  const atual = gateway?.segredos[campo.chave];
  if (atual?.configurado) {
    return `Configurado ${atual.final4 ? `••••${atual.final4}` : ''}. Em branco, continua o atual.`;
  }
  return campo.dica ?? undefined;
}

/** Erro do campo antes de ir à API (a API confere de novo). */
export function erroCampo(
  campo: CampoConfiguracao,
  valor: string | undefined,
  gateway?: Gateway,
): string | undefined {
  const texto = valor?.trim() ?? '';
  if (!texto) {
    const jaTem = campo.secreto && gateway?.segredos[campo.chave]?.configurado;
    return campo.obrigatorio && !campo.padrao && !jaTem ? `Informe ${campo.rotulo}.` : undefined;
  }
  if (texto.length > 500) return 'Use no máximo 500 caracteres.';
  if (campo.tipo === 'URL' && !/^https?:\/\/[^\s/]+/.test(texto)) {
    return 'Informe um endereço começando com https://.';
  }
  return undefined;
}

function limpos(valores: Record<string, string>): Record<string, string> {
  return Object.fromEntries(Object.entries(valores).map(([chave, valor]) => [chave, valor.trim()]));
}
