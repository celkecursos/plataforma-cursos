import { Alerta, Botao, CampoEmail, CampoSenha, CampoTexto } from '@celke/kole-react';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Mensagem } from '@compartilhado/api/tipos.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import {
  erroConfirmacao,
  erroEmail,
  erroObrigatorio,
  erroSenhaNova,
  semErros,
} from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../caminhos.ts';

type Erros = Partial<Record<'nome' | 'email' | 'senha' | 'confirmacao', string>>;

/** Cadastro do aluno: a conta só entra depois de confirmar o e-mail (link enviado pela API). */
export function CadastroPagina() {
  const [parametros] = useSearchParams();
  const voltar = parametros.get('voltar');
  const entrar = voltar
    ? `${CAMINHOS.entrar}?voltar=${encodeURIComponent(voltar)}`
    : CAMINHOS.entrar;
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [erros, setErros] = useState<Erros>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const [mensagem, setMensagem] = useState<string>();

  const enviar = async () => {
    const novos: Erros = {
      nome: erroObrigatorio(nome, 'Informe o seu nome.', 120),
      email: erroEmail(email),
      senha: erroSenhaNova(senha),
      confirmacao: erroConfirmacao(senha, confirmacao),
    };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setEnviando(true);
    try {
      const resposta = await api.post<Mensagem>('/api/auth/cadastro', {
        nome: nome.trim(),
        email: email.trim(),
        senha,
      });
      setMensagem(resposta.mensagem);
    } catch (erro) {
      if (erro instanceof ErroApi && erro.erros.length) {
        setErros({
          nome: erro.erroDoCampo('nome'),
          email: erro.erroDoCampo('email'),
          senha: erro.erroDoCampo('senha'),
        });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setEnviando(false);
    }
  };

  if (mensagem) {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Confira o seu e-mail">
        <Alerta severidade="ok" titulo={mensagem}>
          Enviamos um link para <strong>{email.trim()}</strong>. Abra o e-mail e clique no link para
          ativar a conta.
        </Alerta>
        <Link className="pc-link" to={entrar}>
          Depois de confirmar, entre aqui
        </Link>
      </LayoutConta>
    );
  }

  return (
    <LayoutConta
      marca="Plataforma de Cursos"
      titulo="Criar conta"
      descricao="Cadastre-se para acessar seus cursos."
      aoEnviar={enviar}
      rodape={
        <span>
          Já tem conta? <Link to={entrar}>Entrar</Link>
        </span>
      }
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <CampoTexto
        rotulo="Nome"
        valor={nome}
        aoMudar={setNome}
        erro={erros.nome}
        obrigatorio
        autoComplete="name"
      />
      <CampoEmail
        rotulo="E-mail"
        valor={email}
        aoMudar={setEmail}
        erro={erros.email}
        obrigatorio
        autoComplete="email"
      />
      <CampoSenha
        rotulo="Senha"
        valor={senha}
        aoMudar={setSenha}
        erro={erros.senha}
        obrigatorio
        dica="Pelo menos 10 caracteres."
        autoComplete="new-password"
      />
      <CampoSenha
        rotulo="Repita a senha"
        valor={confirmacao}
        aoMudar={setConfirmacao}
        erro={erros.confirmacao}
        obrigatorio
        autoComplete="new-password"
      />
      <Botao type="submit" rotulo="Criar conta" icone="user-plus" carregando={enviando} bloco />
    </LayoutConta>
  );
}
