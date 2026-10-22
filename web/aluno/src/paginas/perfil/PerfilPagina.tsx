import {
  Alerta,
  Avatar,
  Botao,
  CabecalhoPagina,
  Card,
  CampoEmail,
  CampoSenha,
  CampoTexto,
  Carregando,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { enviarParaUrl } from '@compartilhado/api/envio.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { EscolherArquivo } from '@compartilhado/ui/EscolherArquivo.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import {
  erroConfirmacao,
  erroObrigatorio,
  erroSenhaNova,
  semErros,
} from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../../caminhos.ts';
import type { EnvioSimples, Perfil } from '../../tipos.ts';

const FOTO_MAXIMA = 5 * 1024 * 1024;

/** Dados do aluno: nome, foto e troca de senha. */
export function PerfilPagina() {
  const perfil = useCarregar(() => api.get<Perfil>('/api/perfil'), []);
  if (perfil.erro && !perfil.dados) return <Alerta severidade="err" mensagem={perfil.erro} />;
  if (!perfil.dados) return <Carregando mensagem="Carregando o perfil..." />;
  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Meu perfil" icone="user" />
      <Dados perfil={perfil.dados} aoMudar={perfil.definir} />
      <Foto perfil={perfil.dados} aoMudar={perfil.definir} />
      <TrocarSenha />
    </div>
  );
}

function Dados({ perfil, aoMudar }: { perfil: Perfil; aoMudar: (p: Perfil) => void }) {
  const toast = useToast();
  const [nome, setNome] = useState(perfil.nome);
  const [erro, setErro] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const problema = erroObrigatorio(nome, 'Informe o seu nome.', 120);
    setErro(problema);
    if (problema) return;
    setSalvando(true);
    try {
      aoMudar(await api.put<Perfil>('/api/perfil', { nome: nome.trim() }));
      toast.ok('Nome atualizado');
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo('nome')) || mensagemDeErro(falha));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Card titulo="Dados">
      <form
        noValidate
        className="pc-grade-detalhes"
        onSubmit={(evento) => {
          evento.preventDefault();
          void salvar();
        }}
      >
        <CampoTexto
          rotulo="Nome"
          valor={nome}
          aoMudar={setNome}
          erro={erro}
          obrigatorio
          maxLength={120}
          autoComplete="name"
        />
        <CampoEmail
          rotulo="E-mail"
          valor={perfil.email}
          disabled
          dica="O e-mail da conta não muda."
        />
        <div>
          <Botao type="submit" rotulo="Salvar nome" icone="save" carregando={salvando} />
        </div>
      </form>
    </Card>
  );
}

function Foto({ perfil, aoMudar }: { perfil: Perfil; aoMudar: (p: Perfil) => void }) {
  const toast = useToast();
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();

  const enviar = async (arquivo: File) => {
    if (!['image/jpeg', 'image/png'].includes(arquivo.type)) {
      toast.err('Formato não aceito', 'Envie uma foto JPG ou PNG.');
      return;
    }
    if (arquivo.size > FOTO_MAXIMA) {
      toast.err('Foto grande demais', 'A foto pode ter até 5 MB.');
      return;
    }
    const novo = await executar(async () => {
      const envio = await api.post<EnvioSimples>('/api/perfil/foto/envio', {
        contentType: arquivo.type,
        tamanho: arquivo.size,
      });
      await enviarParaUrl(envio.url, arquivo, envio.contentType);
      return api.put<Perfil>('/api/perfil/foto', { chave: envio.chave });
    }, 'Foto atualizada');
    if (novo) aoMudar(novo);
  };

  return (
    <Card titulo="Foto">
      <div className="pc-linha">
        <Avatar nome={perfil.nome} foto={perfil.fotoUrl ?? undefined} tamanho="4rem" />
        <EscolherArquivo
          rotulo={perfil.fotoUrl ? 'Trocar foto' : 'Enviar foto'}
          icone="image"
          aparencia="contorno"
          aceitar="image/jpeg,image/png"
          carregando={executando}
          aoEscolher={(arquivo) => void enviar(arquivo)}
        />
        {perfil.fotoUrl && (
          <Botao
            rotulo="Remover foto"
            aparencia="texto"
            severidade="err"
            onClick={async () => {
              if (!(await confirmar.remover({ mensagem: 'A sua foto será removida.' }))) return;
              const ok = await executar(
                () => api.delete('/api/perfil/foto').then(() => true),
                'Foto removida',
              );
              if (ok) aoMudar({ ...perfil, fotoUrl: null });
            }}
          />
        )}
      </div>
      <p className="pc-dica">JPG ou PNG até 5 MB. A foto é reduzida para 400 × 400.</p>
    </Card>
  );
}

type ErrosSenha = Partial<Record<'atual' | 'nova' | 'confirmacao', string>>;

function TrocarSenha() {
  const [atual, setAtual] = useState('');
  const [nova, setNova] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [erros, setErros] = useState<ErrosSenha>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const novos: ErrosSenha = {
      atual: atual ? undefined : 'Informe a senha atual.',
      nova: erroSenhaNova(nova),
      confirmacao: erroConfirmacao(nova, confirmacao),
    };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setSalvando(true);
    try {
      await api.put('/api/perfil/senha', { senhaAtual: atual, novaSenha: nova });
      // A API encerrou todas as sessões (o cookie de refresh já foi limpo): recarrega na entrada,
      // o que também descarta o token desta aba
      window.location.replace(`${CAMINHOS.entrar}?senha=alterada`);
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({ atual: erro.erroDoCampo('senhaAtual'), nova: erro.erroDoCampo('novaSenha') });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Card titulo="Trocar senha">
      <form
        noValidate
        className="pc-pilha pc-formulario-estreito"
        onSubmit={(evento) => {
          evento.preventDefault();
          void salvar();
        }}
      >
        {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
        <CampoSenha
          rotulo="Senha atual"
          valor={atual}
          aoMudar={setAtual}
          erro={erros.atual}
          obrigatorio
          autoComplete="current-password"
        />
        <CampoSenha
          rotulo="Nova senha"
          valor={nova}
          aoMudar={setNova}
          erro={erros.nova}
          obrigatorio
          dica="Pelo menos 10 caracteres."
          autoComplete="new-password"
        />
        <CampoSenha
          rotulo="Repita a nova senha"
          valor={confirmacao}
          aoMudar={setConfirmacao}
          erro={erros.confirmacao}
          obrigatorio
          autoComplete="new-password"
        />
        <Alerta
          severidade="info"
          mensagem="Ao trocar a senha, você sai de todos os dispositivos e entra de novo."
        />
        <div>
          <Botao type="submit" rotulo="Trocar senha" icone="key" carregando={salvando} />
        </div>
      </form>
    </Card>
  );
}
