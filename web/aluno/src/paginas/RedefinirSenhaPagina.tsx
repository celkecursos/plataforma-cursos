import { Alerta, Botao, CampoSenha } from '@celke/kole-react';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { useTokenDaUrl } from '@compartilhado/ui/useTokenDaUrl.ts';
import {
  erroConfirmacao,
  erroSenhaNova,
  SENHA_MINIMA_ALUNO,
  semErros,
} from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../caminhos.ts';

/**
 * Destino do link de recuperação: cria a nova senha (e encerra as sessões abertas). Vindo do e-mail da
 * matrícula paga (`origem=pagamento`), é a primeira senha da conta criada pela compra.
 */
export function RedefinirSenhaPagina() {
  const [parametros] = useSearchParams();
  const token = useTokenDaUrl();
  const primeiraSenha = parametros.get('origem') === 'pagamento';
  const [senha, setSenha] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [erros, setErros] = useState<{ senha?: string; confirmacao?: string }>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const [pronto, setPronto] = useState(false);

  if (!token) {
    return (
      <LayoutConta marca="Plataforma de Cursos" titulo="Link inválido">
        <Alerta severidade="err" mensagem="Link inválido ou expirado. Peça um novo." />
        <Link className="pc-link" to={CAMINHOS.recuperarSenha}>
          Pedir um novo link
        </Link>
      </LayoutConta>
    );
  }

  if (pronto) {
    return (
      <LayoutConta
        marca="Plataforma de Cursos"
        titulo={primeiraSenha ? 'Senha criada' : 'Senha alterada'}
      >
        <Alerta
          severidade="ok"
          mensagem={
            primeiraSenha
              ? 'Sua senha foi criada. Entre para acessar o seu curso.'
              : 'Sua nova senha já vale. Por segurança, você saiu de todos os dispositivos.'
          }
        />
        <Link className="pc-link" to={CAMINHOS.entrar}>
          Entrar
        </Link>
      </LayoutConta>
    );
  }

  const enviar = async () => {
    const novos = { senha: erroSenhaNova(senha), confirmacao: erroConfirmacao(senha, confirmacao) };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setEnviando(true);
    try {
      await api.post('/api/auth/redefinir-senha', { token, senha });
      setPronto(true);
    } catch (erro) {
      const doCampo = erro instanceof ErroApi ? erro.erroDoCampo('senha') : undefined;
      if (doCampo) setErros({ senha: doCampo });
      else setErroGeral(mensagemDeErro(erro));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <LayoutConta
      marca="Plataforma de Cursos"
      titulo={primeiraSenha ? 'Crie sua senha' : 'Criar nova senha'}
      descricao={
        primeiraSenha ? 'Sua matrícula está confirmada. Crie a senha para entrar.' : undefined
      }
      aoEnviar={enviar}
      rodape={<Link to={CAMINHOS.recuperarSenha}>Pedir um novo link</Link>}
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <CampoSenha
        rotulo={primeiraSenha ? 'Senha' : 'Nova senha'}
        valor={senha}
        aoMudar={setSenha}
        erro={erros.senha}
        obrigatorio
        dica="Pelo menos 10 caracteres, sem o seu nome ou e-mail e fora das senhas mais usadas."
        forca
        politica={{ minimo: SENHA_MINIMA_ALUNO }}
        autoComplete="new-password"
      />
      <CampoSenha
        rotulo={primeiraSenha ? 'Repita a senha' : 'Repita a nova senha'}
        valor={confirmacao}
        aoMudar={setConfirmacao}
        erro={erros.confirmacao}
        obrigatorio
        autoComplete="new-password"
      />
      <Botao
        type="submit"
        rotulo={primeiraSenha ? 'Criar senha' : 'Salvar nova senha'}
        icone="save"
        carregando={enviando}
        bloco
      />
    </LayoutConta>
  );
}
