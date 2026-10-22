import { Alerta, Botao, Carregando, CampoEmail } from '@celke/kole-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Mensagem } from '@compartilhado/api/tipos.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { erroEmail } from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../caminhos.ts';

type Estado = { tipo: 'confirmando' } | { tipo: 'confirmado' } | { tipo: 'erro'; mensagem: string };

/** Destino do link enviado no cadastro: confirma o e-mail e, se o link venceu, permite pedir outro. */
export function ConfirmarEmailPagina() {
  const [parametros] = useSearchParams();
  const token = parametros.get('token');
  const [estado, setEstado] = useState<Estado>(
    token
      ? { tipo: 'confirmando' }
      : { tipo: 'erro', mensagem: 'Link inválido ou expirado. Peça um novo.' },
  );
  // O link é de uso único: confirmar só uma vez, mesmo com o StrictMode montando duas vezes
  const enviado = useRef(false);

  useEffect(() => {
    if (!token || enviado.current) return;
    enviado.current = true;
    api
      .post('/api/auth/confirmar-email', { token })
      .then(() => setEstado({ tipo: 'confirmado' }))
      .catch((erro: unknown) => setEstado({ tipo: 'erro', mensagem: mensagemDeErro(erro) }));
  }, [token]);

  if (estado.tipo === 'confirmando') {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Confirmando o seu e-mail">
        <Carregando mensagem="Confirmando..." />
      </LayoutConta>
    );
  }
  if (estado.tipo === 'confirmado') {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="E-mail confirmado">
        <Alerta severidade="ok" mensagem="Sua conta está ativa. Agora é só entrar." />
        <Link className="pc-link" to={CAMINHOS.entrar}>
          Entrar
        </Link>
      </LayoutConta>
    );
  }
  return <ReenviarConfirmacao mensagem={estado.mensagem} />;
}

function ReenviarConfirmacao({ mensagem }: { mensagem: string }) {
  const [email, setEmail] = useState('');
  const [erro, setErro] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const [resposta, setResposta] = useState<string>();

  const enviar = async () => {
    const problema = erroEmail(email);
    setErro(problema);
    if (problema) return;
    setEnviando(true);
    try {
      setResposta(
        (await api.post<Mensagem>('/api/auth/reenviar-confirmacao', { email: email.trim() }))
          .mensagem,
      );
    } catch (falha) {
      setErro(mensagemDeErro(falha));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <LayoutConta
      marca="Plataforma de Cursos"
      titulo="Não foi possível confirmar"
      aoEnviar={resposta ? undefined : enviar}
      rodape={<Link to={CAMINHOS.entrar}>Voltar para a entrada</Link>}
    >
      <Alerta severidade="err" mensagem={mensagem} />
      {resposta ? (
        <Alerta severidade="ok" mensagem={resposta} />
      ) : (
        <>
          <CampoEmail rotulo="E-mail" valor={email} aoMudar={setEmail} erro={erro} obrigatorio />
          <Botao type="submit" rotulo="Enviar novo link" icone="mail" carregando={enviando} bloco />
        </>
      )}
    </LayoutConta>
  );
}
