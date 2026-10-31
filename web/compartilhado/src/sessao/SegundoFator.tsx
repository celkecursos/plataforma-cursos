import { Alerta, Botao, CampoTexto } from '@celke/kole-react';
import QRCode from 'qrcode';
import { useEffect, useState } from 'react';
import { api } from '../api/cliente.ts';
import { mensagemDeErro } from '../api/erros.ts';
import type { CadastroSegundoFator, DesafioSegundoFator, Sessao, Usuario } from '../api/tipos.ts';
import { LayoutConta } from '../ui/LayoutConta.tsx';
import {
  erroCodigo,
  erroCodigoRecuperacao,
  limparCodigo,
  segredoEmGrupos,
} from './segundoFator.ts';

interface SegundoFatorProps {
  marca: string;
  desafio: DesafioSegundoFator;
  /** Sessão aberta: o componente já a entregou ao cliente da API. */
  aoConcluir: (usuario: Usuario) => void;
  /** Volta para o e-mail e a senha (desafio vencido ou "Voltar"). */
  aoVoltar: () => void;
}

/**
 * Segunda etapa do login do administrador. No primeiro acesso, mostra o QR code (e o segredo para
 * digitar) do aplicativo autenticador; depois, pede o código de 6 dígitos ou um código de
 * recuperação. Na ativação, mostra os códigos de recuperação uma única vez antes de entrar.
 */
export function SegundoFator({ marca, desafio, aoConcluir, aoVoltar }: SegundoFatorProps) {
  const cadastro = desafio.segundoFator === 'CADASTRO';
  const [dados, setDados] = useState<CadastroSegundoFator>();
  const [qr, setQr] = useState<string>();
  const [comRecuperacao, setComRecuperacao] = useState(false);
  const [codigo, setCodigo] = useState('');
  const [erroCampo, setErroCampo] = useState<string>();
  const [erroGeral, setErroGeral] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const [sessao, setSessao] = useState<Sessao>();

  useEffect(() => {
    if (!cadastro) return;
    let ativo = true;
    api
      .post<CadastroSegundoFator>(
        '/api/auth/segundo-fator/cadastro',
        { desafio: desafio.desafio },
        { semRenovar: true },
      )
      .then(async (resposta) => {
        const imagem = await QRCode.toDataURL(resposta.uri, { margin: 1, width: 200 });
        if (!ativo) return;
        setDados(resposta);
        setQr(imagem);
      })
      .catch((erro: unknown) => ativo && setErroGeral(mensagemDeErro(erro)));
    return () => {
      ativo = false;
    };
  }, [cadastro, desafio.desafio]);

  if (sessao?.codigosRecuperacao?.length) {
    return (
      <LayoutConta marca={marca} titulo="Guarde os códigos de recuperação">
        <Alerta
          severidade="warn"
          mensagem="Sem o celular, cada código abaixo permite entrar uma vez. Eles não aparecem de novo: guarde num lugar seguro."
        />
        <ul className="pc-codigos-recuperacao" aria-label="Códigos de recuperação">
          {sessao.codigosRecuperacao.map((c) => (
            <li key={c}>
              <code>{c}</code>
            </li>
          ))}
        </ul>
        <Botao
          rotulo="Guardei os códigos, continuar"
          icone="arrow-right"
          bloco
          onClick={() => {
            api.definirSessao(sessao);
            aoConcluir(sessao.usuario);
          }}
        />
      </LayoutConta>
    );
  }

  const enviar = async () => {
    const erro = comRecuperacao ? erroCodigoRecuperacao(codigo) : erroCodigo(codigo);
    setErroCampo(erro);
    setErroGeral(undefined);
    if (erro) return;
    setEnviando(true);
    try {
      const corpo = comRecuperacao
        ? { desafio: desafio.desafio, codigoRecuperacao: codigo.trim() }
        : { desafio: desafio.desafio, codigo: limparCodigo(codigo) };
      const resposta = await api.post<Sessao>('/api/auth/segundo-fator', corpo, {
        semRenovar: true,
      });
      if (resposta.codigosRecuperacao?.length) {
        setSessao(resposta);
        return;
      }
      api.definirSessao(resposta);
      aoConcluir(resposta.usuario);
    } catch (erro) {
      setErroGeral(mensagemDeErro(erro));
      setCodigo('');
    } finally {
      setEnviando(false);
    }
  };

  return (
    <LayoutConta
      marca={marca}
      titulo={cadastro ? 'Ative a verificação em duas etapas' : 'Verificação em duas etapas'}
      descricao={
        cadastro
          ? 'Leia o QR code com um aplicativo autenticador (Google Authenticator, Microsoft Authenticator, 1Password…) e informe o código que ele mostrar.'
          : comRecuperacao
            ? 'Informe um dos códigos de recuperação que você guardou.'
            : 'Informe o código de 6 dígitos do aplicativo autenticador.'
      }
      aoEnviar={enviar}
      rodape={
        <>
          {!cadastro && (
            <button
              type="button"
              className="pc-link"
              onClick={() => {
                setComRecuperacao(!comRecuperacao);
                setCodigo('');
                setErroCampo(undefined);
              }}
            >
              {comRecuperacao
                ? 'Usar o código do aplicativo'
                : 'Sem o celular? Use um código de recuperação'}
            </button>
          )}
          <button type="button" className="pc-link" onClick={aoVoltar}>
            Voltar
          </button>
        </>
      }
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      {cadastro && !qr && !erroGeral && <p role="status">Gerando o QR code…</p>}
      {cadastro && dados && qr && (
        <div className="pc-segundo-fator-qr">
          <img src={qr} alt="QR code para o aplicativo autenticador" width={200} height={200} />
          <p>
            Não consegue ler? Digite a chave no aplicativo:{' '}
            <code aria-label="Chave do aplicativo autenticador">
              {segredoEmGrupos(dados.segredo)}
            </code>
          </p>
        </div>
      )}
      <CampoTexto
        rotulo={comRecuperacao ? 'Código de recuperação' : 'Código do aplicativo'}
        valor={codigo}
        aoMudar={setCodigo}
        erro={erroCampo}
        obrigatorio
        autoComplete="one-time-code"
        inputMode={comRecuperacao ? 'text' : 'numeric'}
        autoFocus
      />
      <Botao type="submit" rotulo="Confirmar" icone="shield-check" carregando={enviando} bloco />
    </LayoutConta>
  );
}
