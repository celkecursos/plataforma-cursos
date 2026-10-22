import { Alerta, Botao, CampoEmail } from '@celke/kole-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Mensagem } from '@compartilhado/api/tipos.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { erroEmail } from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../caminhos.ts';

/** Pede o link de nova senha. A resposta é a mesma exista ou não a conta (não revela e-mails). */
export function RecuperarSenhaPagina() {
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
        (await api.post<Mensagem>('/api/auth/recuperar-senha', { email: email.trim() })).mensagem,
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
      titulo="Recuperar senha"
      descricao="Informe o e-mail da conta. Enviaremos um link para criar uma nova senha."
      aoEnviar={resposta ? undefined : enviar}
      rodape={<Link to={CAMINHOS.entrar}>Voltar para a entrada</Link>}
    >
      {resposta ? (
        <Alerta severidade="ok" mensagem={resposta} />
      ) : (
        <>
          <CampoEmail
            rotulo="E-mail"
            valor={email}
            aoMudar={setEmail}
            erro={erro}
            obrigatorio
            autoComplete="email"
          />
          <Botao type="submit" rotulo="Enviar link" icone="mail" carregando={enviando} bloco />
        </>
      )}
    </LayoutConta>
  );
}
